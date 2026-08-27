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

/** A SUBDOMAIN is a domain: it owns its own `error-sink`.
  *
  * 2.0.0-rc.8 counted sinks with a recursive find that crossed nested `Domain` boundaries, so the
  * two error-sink checks contradicted each other and a nested model could satisfy neither: the
  * missing check named `Sub1` as a domain wanting its own sink, while the uniqueness check folded
  * `Sub1`'s sink into the root's count and called it a duplicate. riddl-models hit this on
  * `reactive-bbq` and reported it as unsatisfiable, which it was.
  *
  * The resolution has both checks agree on what a domain is, and lets an ANCESTOR's sink satisfy a
  * subdomain so that a root may declare one destination for its whole tree.
  */
class NestedDomainErrorSinkTest extends AbstractValidatingTest {

  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) =>
          captured = msgs
          succeed
      }
    }
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse, so any absence proves nothing:\n${m.format}")
      case None    => captured
  end messagesFor

  private def clue(msgs: Messages): String = msgs.map(_.message).mkString("\n")
  private def duplicates(msgs: Messages): Messages =
    msgs.filter(_.message.contains("second 'error-sink'"))
  private def missing(msgs: Messages): Messages =
    msgs.filter(_.message.contains("declares no 'error-sink'"))

  /** An error-sink inlet, wrapped in a context so it has somewhere to live. */
  private def sinkContext(n: String): String =
    s"""    context C$n is {
       |      processor R$n as sink is {
       |        inlet ErrorSink is record Riddl.GeneratorError with { option error-sink }
       |        handler H is { on other { do "record" } } with { briefly "h" }
       |      } with { briefly "r" }
       |    } with { briefly "c" }""".stripMargin

  private def plainContext(n: String): String =
    s"""    context C$n is {
       |      type T$n is String with { briefly "t" }
       |    } with { briefly "c" }""".stripMargin

  private def model(root: String, sub1: String, sub2: String): String =
    s"""domain Root is {
       |$root
       |  domain Sub1 is {
       |$sub1
       |  } with { briefly "s1" }
       |  domain Sub2 is {
       |$sub2
       |  } with { briefly "s2" }
       |} with { briefly "root" }
       |""".stripMargin

  "a sink in EACH subdomain" should {
    "be legal -- a subdomain is a domain, so these are not duplicates (the rc.8 bug)" in {
      (td: TestData) =>
        val msgs = messagesFor(model(plainContext("R"), sinkContext("1"), sinkContext("2")), td)
        withClue(s"messages were: ${clue(msgs)}") { duplicates(msgs) mustBe empty }
    }
  }

  "a single sink in the ROOT domain" should {
    "satisfy its subdomains -- one destination may serve a whole tree" in { (td: TestData) =>
      val msgs = messagesFor(model(sinkContext("R"), plainContext("1"), plainContext("2")), td)
      withClue(s"messages were: ${clue(msgs)}") {
        missing(msgs) mustBe empty
        duplicates(msgs) mustBe empty
      }
    }
  }

  "the two checks together" should {
    "be SATISFIABLE for a nested model -- neither arrangement above can be made to fail" in {
      (td: TestData) =>
        // The point of the report: rc.8 had no arrangement that passed both. Assert it directly,
        // over the arrangements a modeller would actually try.
        val arrangements = Seq(
          "sink in root only" -> model(sinkContext("R"), plainContext("1"), plainContext("2")),
          "sink in root and in each subdomain" ->
            model(sinkContext("R"), sinkContext("1"), sinkContext("2")),
          // Root is a pure container here: no processors of its own, so nothing of its own can
          // fail and it is not asked for a sink.
          "sinks only in the subdomains, root a pure container" ->
            model("", sinkContext("1"), sinkContext("2"))
        )
        arrangements.foreach { case (name, src) =>
          val msgs = messagesFor(src, td)
          withClue(s"[$name] messages were: ${clue(msgs)}") {
            duplicates(msgs) mustBe empty
            missing(msgs) mustBe empty
          }
        }
    }
  }

  "two sinks in the SAME subdomain" should {
    "still be an error -- the rule is unchanged, only its scope is" in { (td: TestData) =>
      val twoInOne =
        s"""${sinkContext("1")}
           |${sinkContext("1b")}""".stripMargin
      val msgs = messagesFor(model(plainContext("R"), twoInOne, plainContext("2")), td)
      withClue(s"messages were: ${clue(msgs)}") {
        val dupes = duplicates(msgs)
        dupes must not be empty
        dupes.head.isError mustBe true
        // ...and it is attributed to the SUBDOMAIN that has two, not to the root.
        dupes.head.message must include("Sub1")
      }
    }
  }

  "leaf subdomains with no sink anywhere in the ancestry" should {
    "each be warned about, and the GROUPING domain not be" in { (td: TestData) =>
      val msgs = messagesFor(model(plainContext("R"), plainContext("1"), plainContext("2")), td)
      withClue(s"messages were: ${clue(msgs)}") {
        // Sub1 and Sub2 are where the work is. Root groups them, so asking it as well would
        // double-report the same subtree.
        missing(msgs).size mustBe 2
        missing(msgs).exists(_.message.contains("Sub1")) mustBe true
        missing(msgs).exists(_.message.contains("Sub2")) mustBe true
        missing(msgs).exists(_.message.contains("'Root'")) mustBe false
      }
    }
  }

  "a domain that CONTAINS subdomains" should {
    "not be asked for a sink even when it has processors of its own" in { (td: TestData) =>
      // A grouping domain is for scoping and for sharing types; the things that can fail live in
      // its leaves, which are asked individually.
      val msgs = messagesFor(model(plainContext("R"), sinkContext("1"), sinkContext("2")), td)
      withClue(s"messages were: ${clue(msgs)}") {
        missing(msgs).filter(_.message.contains("'Root'")) mustBe empty
      }
    }
  }

  "a LEAF domain with no processors of its own" should {
    "not be asked either -- it has nowhere to put an inlet and nothing that can fail" in {
      (td: TestData) =>
        val src =
          """domain Solo is {
            |  type T is String with { briefly "t" }
            |} with { briefly "solo" }
            |""".stripMargin
        val msgs = messagesFor(src, td)
        withClue(s"messages were: ${clue(msgs)}") { missing(msgs) mustBe empty }
    }

    "be asked as soon as it HAS a processor -- the exemption must not swallow real cases" in {
      (td: TestData) =>
        val src =
          s"""domain Solo is {
             |${plainContext("S")}
             |} with { briefly "solo" }
             |""".stripMargin
        val msgs = messagesFor(src, td)
        withClue(s"messages were: ${clue(msgs)}") { missing(msgs) must not be empty }
    }
  }
}
