/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{filter, toSeq, Finder}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** `shown by { <url> }` was emitted by nothing: `ShownBy` appeared NOWHERE in the prettify package.
  *
  * It is a `RiddlValue` living in a Group's or Epic's `contents`, and `Pass.processValue` skipped
  * it deliberately — its comment claimed `ShownBy` is "read by the definition that holds them",
  * which was true of every visitor except the one that has to write source back out. So the
  * construct parsed, validated, and round-tripped through BAST correctly while prettify silently
  * deleted it.
  *
  * The fix gives it a `doShownBy` visitor hook, matching how the other non-`Definition` values
  * (`Enumerator`, `Requires`, `Returns`) already reach a visitor. Emitting it from the holder's
  * `open*` instead would have relocated it to the top of the body, breaking sibling order.
  *
  * Reported by riddl-models (`task/2026-08-14-prettify-emitter-drops-method-and-shown-by.md`).
  */
class ShownByRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    Pass
      .runThesePasses(
        PassInput(root),
        Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
          PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
        }
      )
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  /** `shown by` sits BETWEEN two definitions so the round trip has to preserve its position, not
    * merely its presence.
    */
  private val src =
    """domain Dom is {
      |  application context ui is {
      |    type Amount is Integer
      |    result Total is { sum: Integer }
      |    page picker is {
      |      input amount enters type Dom.ui.Amount
      |      shown by { https://ossum.tech/x }
      |      output total presents result Dom.ui.Total
      |    }
      |  }
      |}
      |""".stripMargin

  private def groupOf(root: Root): Group =
    Finder(root).recursiveFindByType[Group].headOption.getOrElse(fail("no group was parsed"))

  "a `shown by` in a group body" should {

    "be parsed into the group's contents" in { (_: TestData) =>
      val group = groupOf(parse(src, "shownBy"))
      val shown = group.contents.filter[ShownBy]
      shown.size mustBe 1
      shown.head.urls.map(_.toExternalForm).head must include("ossum.tech/x")
    }

    "survive a prettify round trip still inside the group" in { (_: TestData) =>
      val pretty = prettify(parse(src, "shownBy"))
      withClue(s"prettified output was:\n$pretty") {
        pretty must include("shown by")

        val again = groupOf(parse(pretty, "regen"))
        val shown = again.contents.filter[ShownBy]
        shown.size mustBe 1
        shown.head.urls.map(_.toExternalForm).head must include("ossum.tech/x")
      }
    }

    "keep its position between the group's input and output" in { (_: TestData) =>
      val pretty = prettify(parse(src, "shownBy"))
      val again = groupOf(parse(pretty, "regen"))
      val kinds = again.contents.toSeq.map {
        case _: Input   => "input"
        case _: ShownBy => "shownBy"
        case _: Output  => "output"
        case other      => other.getClass.getSimpleName
      }
      withClue(s"prettified output was:\n$pretty") {
        kinds mustBe Seq("input", "shownBy", "output")
      }
    }
  }

  /** `Epic` is the OTHER holder of a `ShownBy` (`EpicParser:170`). The report only exercised a
    * group, but the fix is in the shared visitor dispatch, so the epic case must be pinned too --
    * otherwise a later change could restore the loss for epics alone and every test would stay
    * green.
    */
  private val epicSrc =
    """domain Dom is {
      |  user Author is "human writer"
      |  epic WritingABook is {
      |    user Dom.Author wants to "edit on screen" so that "he can revise content more easily"
      |    shown by { http://example.com:80/path/to/WritingABook }
      |    case perfection is {
      |      user Dom.Author wants "to open a document" so that "it can be edited" ???
      |    }
      |  } with { briefly "e" }
      |} with { briefly "d" }
      |""".stripMargin

  "a `shown by` in an epic body" should {

    "survive a prettify round trip" in { (_: TestData) =>
      val pretty = prettify(parse(epicSrc, "epicShownBy"))
      withClue(s"prettified output was:\n$pretty") {
        val epic = Finder(parse(pretty, "regen"))
          .recursiveFindByType[Epic]
          .headOption
          .getOrElse(fail("no epic came back"))
        epic.shownBy.size mustBe 1
        epic.shownBy.head.urls.map(_.toExternalForm).head must
          be("http://example.com:80/path/to/WritingABook")
      }
    }
  }
}
