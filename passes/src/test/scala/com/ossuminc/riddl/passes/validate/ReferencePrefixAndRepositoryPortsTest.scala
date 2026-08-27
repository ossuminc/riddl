/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** Two rules from riddl-models (Reid, 2026-08-24).
  *
  *   1. **A reference's prefix must name what the target was DECLARED as.** *"I required that
  *      kind-of-thing prefix for all references SPECIFICALLY to avoid ambiguity and to aid
  *      comprehension of the model when read. Using `type` undoes that requirement."*
  *   2. **A repository inlet may not carry an event.** A repository is changed by commands and read
  *      by queries; an event routed into storage skips the decision about what it should change.
  *
  * **Rule 1 is keyed off the DECLARATION, never off what the reference carries**, and that is the
  * whole reason it is safe. An alternation declared `type XEvent is one of { ... }` IS a type even
  * though every member is an event, so `is type XEvent` stays correct -- 230 such references in
  * reactive-bbq alone would otherwise redden wrongly. The negative case below pins that.
  */
class ReferencePrefixAndRepositoryPortsTest extends AbstractValidatingTest {

  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) => captured = msgs; succeed
      }
    }
    captured

  private def errs(msgs: Messages, frag: String): Messages =
    msgs.filter(m => m.isError && m.message.contains(frag))

  private def model(body: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Persist is { p: String(1,9) }
       |    query Ask is { q: String(1,9) }
       |    result View is { v: String(1,9) }
       |    event Happened is { h: String(1,9) }
       |    event Also is { a: String(1,9) }
       |    record Plain is { r: String(1,9) }
       |    type Evs is one of { Ctx.Happened or Ctx.Also }
       |$body
       |  }
       |}
       |""".stripMargin

  private val PrefixErr = "but this reference names it as a"
  private val EventErr = "never by events"

  "a reference's prefix" should {
    "be an Error when it names a command as a type" in { (td: TestData) =>
      val msgs = messagesFor(
        model("""    repository R is {
                |      inlet In is type Ctx.Persist
                |      handler H is { on other is { do "x" } }
                |    }""".stripMargin),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        val hit = errs(msgs, PrefixErr)
        hit must not be empty
        hit.head.message must include("declared a command")
      }
    }

    "be an Error when it names a result as a type" in { (td: TestData) =>
      val msgs = messagesFor(
        model("""    repository R is {
                |      inlet In is command Ctx.Persist
                |      outlet Out is type Ctx.View
                |      handler H is { on other is { do "x" } }
                |    }""".stripMargin),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        errs(msgs, "declared a result") must not be empty
      }
    }

    "be an Error for a BARE reference too, since an omitted prefix means `type`" in {
      (td: TestData) =>
        // Reid's ruling (a). `TypeRef.keyword` defaults to "type", so the AST cannot tell an
        // omitted prefix from a written one -- and the prefix was required precisely to remove
        // this ambiguity, so the bare form is held to the same standard.
        val msgs = messagesFor(
          model("""    repository R is {
                  |      inlet In is Ctx.Persist
                  |      handler H is { on other is { do "x" } }
                  |    }""".stripMargin),
          td
        )
        withClue(msgs.map(_.message).mkString("\n")) {
          errs(msgs, PrefixErr) must not be empty
        }
    }

    "draw nothing when the prefix tells the truth" in { (td: TestData) =>
      val msgs = messagesFor(
        model("""    repository R is {
                |      inlet In is command Ctx.Persist
                |      inlet Q is query Ctx.Ask
                |      outlet Out is result Ctx.View
                |      handler H is { on other is { do "x" } }
                |    }""".stripMargin),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, PrefixErr) mustBe empty }
    }

    "draw nothing for `is type` naming a real alternation, however event-ish its members" in {
      (td: TestData) =>
        // The case that makes the declaration-keyed rule necessary rather than merely tidier.
        val msgs = messagesFor(
          model("""    streamlet S is sink {
                  |      inlet In is type Ctx.Evs
                  |      handler H is { on other is { do "x" } }
                  |    }""".stripMargin),
          td
        )
        withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, PrefixErr) mustBe empty }
    }

    /** The scope BOUNDARY, pinned deliberately so it is visible rather than merely absent.
      *
      * The rule currently covers `TypeRef` — portlet types, invariant `requires`, a function's
      * `requires`/`returns`. A FIELD's type and a type ALIAS are `AliasedTypeExpression`, a
      * different node that carries its own `keyword` and does NOT route through `checkRef`, so
      * `two: Ctx.Cmd` naming a command draws nothing today.
      *
      * That is a smaller scope than the requesting task asked for ("a field's type ... applying it
      * everywhere at once is the honest reading"). It is left OPEN rather than assumed, because the
      * cost differs by a lot: reactive-bbq alone has 283 portlet references and **542** fields with
      * aliased type references, so extending it roughly triples the migration in one model. If this
      * test starts failing because the scope was widened, that is the intended direction — update
      * it rather than narrowing the check back.
      */
    "leave a BARE field type alone -- a prefix is never demanded there" in { (td: TestData) =>
      // [1.10], ruled 2026-08-26: check a prefix that is WRITTEN; never demand one. A field's type
      // admits only a type, so a prefix there removes no ambiguity -- unlike a portlet's type or a
      // function's `requires`, where several kinds are legal and the prefix disambiguates.
      // Demanding it would have cost 542 sites in reactive-bbq alone for no reader benefit.
      val msgs = messagesFor(model("""    record Holder is { two: Ctx.Persist }"""), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, PrefixErr) mustBe empty }
    }

    "flag a field type whose WRITTEN prefix lies" in { (td: TestData) =>
      // The other half of the ruling. A prefix that lies is worse than one that is absent, because
      // a reader believes it.
      val msgs = messagesFor(model("""    record Holder is { two: record Ctx.Persist }"""), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, PrefixErr) must not be empty }
    }

    "accept a field type whose written prefix is true" in { (td: TestData) =>
      val msgs = messagesFor(model("""    record Holder is { two: command Ctx.Persist }"""), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, PrefixErr) mustBe empty }
    }

    "never report on the `type` keyword, which an omitted prefix is indistinguishable from" in {
      (td: TestData) =>
        // A hard limit, not a choice: `TypeParser` builds AliasedTypeExpression(loc, "type", pid)
        // when nothing is written, so the AST cannot tell `Ctx.Persist` from `type Ctx.Persist`.
        // Reporting on "type" would fire on every bare field type in the corpus -- precisely the
        // demand this ruling declined.
        val msgs = messagesFor(model("""    record Holder is { two: type Ctx.Persist }"""), td)
        withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, PrefixErr) mustBe empty }
    }
  }

  "a repository inlet" should {
    "be an Error when it carries an event directly" in { (td: TestData) =>
      val msgs = messagesFor(
        model("""    repository R is {
                |      inlet In is event Ctx.Happened
                |      handler H is { on other is { do "x" } }
                |    }""".stripMargin),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        val hit = errs(msgs, EventErr)
        hit must not be empty
        hit.head.message must include("Happened")
      }
    }

    "be an Error for every event member reached THROUGH an alternation" in { (td: TestData) =>
      // This is the vector riddl-models' check-repository-ports.py existed for: the inlet says
      // `is type XEvent` and nothing looked at what XEvent was made of, so an `on other` clause
      // made it validate clean.
      val msgs = messagesFor(
        model("""    repository R is {
                |      inlet In is type Ctx.Evs
                |      handler H is { on other is { do "x" } }
                |    }""".stripMargin),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        val hit = errs(msgs, EventErr)
        hit.size mustBe 2 // named per offending member, not once for the whole type
        hit.map(_.message).mkString must include("Happened")
        hit.map(_.message).mkString must include("Also")
      }
    }

    "draw nothing for command and query inlets" in { (td: TestData) =>
      val msgs = messagesFor(
        model("""    repository R is {
                |      inlet In is command Ctx.Persist
                |      inlet Q is query Ctx.Ask
                |      handler H is { on other is { do "x" } }
                |    }""".stripMargin),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, EventErr) mustBe empty }
    }

    "draw nothing for an event inlet on something that is NOT a repository" in { (td: TestData) =>
      // The rule is about repositories specifically. A streamlet consuming events is ordinary.
      val msgs = messagesFor(
        model("""    streamlet S is sink {
                |      inlet In is event Ctx.Happened
                |      handler H is { on other is { do "x" } }
                |    }""".stripMargin),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, EventErr) mustBe empty }
    }
  }
}
