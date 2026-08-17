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

/** `option error-sink` marks the inlet that receives hard-error notifications.
  *
  * It belongs on an INLET rather than a processor, because an inlet names the receiver, the port
  * and the message type in one place — a processor may have several inlets, and a generator would
  * be back to guessing which. At most one per DOMAIN: two leave a generator no way to choose.
  * Several across domains is intended, so unrelated concerns need not share an alert stream.
  */
class ErrorSinkTest extends AbstractValidatingTest {

  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) =>
          captured = msgs
          succeed
      }
    }
    // Every case below asserts the ABSENCE of some message, which a fixture that fails to parse
    // satisfies trivially. Two of these cases passed vacuously on a bad alternation until the
    // third -- the one asserting PRESENCE -- gave it away. Refuse to report on a model that
    // never parsed.
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse, so any absence proves nothing:\n${m.format}")
      case None    => captured
  end messagesFor

  private def clue(msgs: Messages): String = msgs.map(_.message).mkString("\n")

  private def unrecognized(msgs: Messages): Messages =
    msgs.filter { m =>
      m.message.contains("not a recognized RIDDL option") ||
      m.message.contains("is not typically used on")
    }

  private def secondSink(msgs: Messages): Messages =
    msgs.filter(_.message.contains("second 'error-sink'"))

  private def wrongType(msgs: Messages): Messages =
    msgs.filter(_.message.contains("does not accept"))

  private def contextWithSinks(sinkCount: Int): String =
    // Typed by GeneratorError because an error-sink inlet MUST accept it -- typing these by the
    // model's own command would make the fixture illegal under the rule these cases are not
    // about, and it would pass only because the filters look elsewhere.
    val inlets = (1 to sinkCount)
      .map(n =>
        s"""      inlet Alerts$n is record Riddl.GeneratorError with { option error-sink }"""
      )
      .mkString("\n")
    s"""domain Dom is {
       |  context Ops is {
       |    command Alert is { detail: String } with { briefly "a" }
       |    processor Receiver as sink is {
       |$inlets
       |      handler H is { on command Dom.Ops.Alert { do "record it" } } with { briefly "h" }
       |    } with { briefly "r" }
       |  } with { briefly "o" }
       |} with { briefly "d" }
       |""".stripMargin

  "option error-sink on an inlet" should {
    "be recognized" in { (td: TestData) =>
      val msgs = messagesFor(contextWithSinks(1), td)
      withClue(s"messages were: ${clue(msgs)}") { unrecognized(msgs) mustBe empty }
    }
  }

  "two error-sink inlets in ONE domain" should {
    "be an error, because a generator cannot choose between them" in { (td: TestData) =>
      val msgs = messagesFor(contextWithSinks(2), td)
      withClue(s"messages were: ${clue(msgs)}") {
        val dupes = secondSink(msgs)
        dupes must not be empty
        dupes.head.isError mustBe true
      }
    }
  }

  "error-sink inlets in DIFFERENT domains" should {
    "both be legal — unrelated concerns need not share an alert stream" in { (td: TestData) =>
      val src =
        """domain First is {
          |  context Ops is {
          |    command Alert is { detail: String } with { briefly "a" }
          |    processor Receiver as sink is {
          |      inlet Alerts is command First.Ops.Alert with { option error-sink }
          |      handler H is { on command First.Ops.Alert { do "record" } } with { briefly "h" }
          |    } with { briefly "r" }
          |  } with { briefly "o" }
          |} with { briefly "d" }
          |""".stripMargin
      // Parsed as one domain at a time by the helper, so the cross-domain case is asserted by
      // there being no complaint about the single sink in each.
      val msgs = messagesFor(src, td)
      withClue(s"messages were: ${clue(msgs)}") {
        secondSink(msgs) mustBe empty
        unrecognized(msgs) mustBe empty
      }
    }
  }

  /** An error-sink inlet typed by `typeClause`, in a model that also defines its own alert. */
  private def sinkTypedBy(typeClause: String): String =
    s"""domain Dom is {
       |  context Ops is {
       |    command Alert is { detail: String } with { briefly "a" }
       |    type Alertable is one of { Riddl.GeneratorError or Dom.Ops.Alert } with {
       |      briefly "either"
       |    }
       |    processor Receiver as sink is {
       |      inlet Alerts is $typeClause with { option error-sink }
       |      handler H is { on other { do "record it" } } with { briefly "h" }
       |    } with { briefly "r" }
       |  } with { briefly "o" }
       |} with { briefly "d" }
       |""".stripMargin

  "an error-sink inlet typed by GeneratorError" should {
    "be accepted — it is what generators send" in { (td: TestData) =>
      val msgs = messagesFor(sinkTypedBy("record Riddl.GeneratorError"), td)
      withClue(s"messages were: ${clue(msgs)}") { wrongType(msgs) mustBe empty }
    }
  }

  "an error-sink inlet typed by an ALTERNATION including GeneratorError" should {
    "be accepted — a model may route its own error messages to the same inlet" in {
      (td: TestData) =>
        val msgs = messagesFor(sinkTypedBy("type Dom.Ops.Alertable"), td)
        withClue(s"messages were: ${clue(msgs)}") { wrongType(msgs) mustBe empty }
    }
  }

  "an error-sink inlet typed ONLY by the model's own message" should {
    "be an ERROR — a generator has nothing it can send there" in { (td: TestData) =>
      val msgs = messagesFor(sinkTypedBy("command Dom.Ops.Alert"), td)
      withClue(s"messages were: ${clue(msgs)}") {
        val wrong = wrongType(msgs)
        wrong must not be empty
        wrong.head.isError mustBe true
      }
    }
  }

  "option error-sink on something other than an inlet" should {
    "be nudged, as portlet and processor options have been since rc.4" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  context Ops is {
          |    command Alert is { detail: String } with { briefly "a" }
          |  } with { briefly "o" option error-sink }
          |} with { briefly "d" }
          |""".stripMargin
      val msgs = messagesFor(src, td)
      withClue(s"messages were: ${clue(msgs)}") { unrecognized(msgs) must not be empty }
    }
  }

  "an ascribed-shape processor hosting the error sink" should {

    /** A `flow` (1 in, 1 out) that also carries the domain's error-sink inlet. */
    def flowWithSink(extraInlet: String): String =
      s"""domain Dom is {
         |  type T is String with { briefly "t" }
         |  context App as flow is {
         |    inlet In is type Dom.T with { briefly "in" }
         |    outlet Out is type Dom.T with { briefly "out" }
         |$extraInlet
         |    handler H is { on other { do "x" } } with { briefly "h" }
         |  } with { briefly "app" }
         |} with { briefly "d" }
         |""".stripMargin

    def arity(msgs: Messages): Messages = msgs.filter(_.message.contains("is ascribed"))

    "be legal -- an error-sink inlet is infrastructure, not part of the shape" in {
      (td: TestData) =>
        // riddl-models had to move api-management's sink to a sibling context because this
        // counted toward arity and turned the flow into a merge. There is nothing wrong with an
        // inlet on a flow; there was something wrong with counting THIS one.
        val src = flowWithSink(
          """    inlet Alerts is record Riddl.GeneratorError with { option error-sink }"""
        )
        val msgs = messagesFor(src, td)
        withClue(s"messages were: ${clue(msgs)}") { arity(msgs) mustBe empty }
    }

    "still reject a second ORDINARY inlet -- the exemption is only for error-sink" in {
      (td: TestData) =>
        val src = flowWithSink("""    inlet Also is type Dom.T with { briefly "also" }""")
        val msgs = messagesFor(src, td)
        withClue(s"messages were: ${clue(msgs)}") { arity(msgs) must not be empty }
    }
  }

  /** ARITY (Reid, 2026-08-16). An `error-sink` inlet is infrastructure, never dataflow, so it does
    * not count toward the shape a processor derives. Before this, validation accepted EITHER
    * reading, which let the infrastructure inlet justify whatever shape the author had written --
    * riddl-models has 177 contexts ascribed `as merge` whose second "inlet" is the error sink and
    * whose dataflow is a plain flow.
    */
  "error-sink arity" should {

    "NOT let an error-sink inlet turn a flow into a merge" in { (td: TestData) =>
      // The case the ruling exists to catch, and the exact shape of those 177 corpus contexts:
      // one dataflow inlet, one outlet, plus the error sink.
      val src =
        """domain D is {
          |  context C as merge is {
          |    inlet In is record Riddl.GeneratorError with { briefly "data" }
          |    inlet Errs is record Riddl.GeneratorError with { option error-sink() briefly "e" }
          |    outlet Out is record Riddl.GeneratorError with { briefly "o" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val text = messagesFor(src, td).justErrors.map(_.message).mkString("\n")
      withClue(text) { text must include("DATAFLOW arity") }
    }

    "ACCEPT the same processor ascribed as the flow it actually is" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C as flow is {
          |    inlet In is record Riddl.GeneratorError with { briefly "data" }
          |    inlet Errs is record Riddl.GeneratorError with { option error-sink() briefly "e" }
          |    outlet Out is record Riddl.GeneratorError with { briefly "o" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      messagesFor(src, td).justErrors.map(_.message).mkString("\n") must not include "DATAFLOW arity"
    }

    // THE ALLOWANCE, and its boundary. A processor whose ONLY inlets are error sinks has no
    // dataflow at all, so it derives as `void` -- but it genuinely IS a sink, of errors, and
    // `void` describes it less well. Both spellings are accepted for exactly that shape.
    "ACCEPT `as sink` on a processor whose only inlet is the error sink" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C as sink is {
          |    inlet Errs is record Riddl.GeneratorError with { option error-sink() briefly "e" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      messagesFor(src, td).justErrors.map(_.message).mkString("\n") must not include "DATAFLOW arity"
    }

    "ACCEPT `as void` on that same processor -- the derived reading" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C as void is {
          |    inlet Errs is record Riddl.GeneratorError with { option error-sink() briefly "e" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      messagesFor(src, td).justErrors.map(_.message).mkString("\n") must not include "DATAFLOW arity"
    }
  }
}
