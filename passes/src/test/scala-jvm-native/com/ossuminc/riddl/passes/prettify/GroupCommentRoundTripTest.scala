/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{filter, Finder}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** A comment inside a `group` body belongs to the group's contents.
  *
  * `GroupParser.groupDefinitions` has always parsed `comment` (and `shownBy`) there, but it reaches
  * `OccursInGroup` through an `asInstanceOf`, and until now that union admitted neither. The parser
  * therefore produced contents the type forbade, and nothing caught it: `Contents` erases to an
  * `ArrayBuffer`, so the mismatch is invisible at runtime. It surfaced only as a JSON round trip
  * that had nowhere legal to put the comment back.
  *
  * RIDDL is reflective, so these tests check the whole contract rather than just the parse: the
  * comment must be IN the group's contents, must survive a parse → prettify → re-parse round trip
  * still inside the group, and must not have been relocated to the group's metadata.
  */
class GroupCommentRoundTripTest extends AbstractValidatingTest {

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

  /** Comments in the three positions a group body allows: opening it, between two definitions, and
    * closing it. The first is the one the JSON round trip could not rebuild.
    */
  private val src =
    """domain D is {
      |  application context ui is {
      |    type Amount is Integer
      |    result Total is { sum: Integer }
      |    page picker is {
      |      // opens the body
      |      input amount enters type D.ui.Amount
      |      // sits between two definitions
      |      output total presents result D.ui.Total
      |      // closes the body
      |    }
      |  }
      |}
      |""".stripMargin

  private def groupOf(root: Root): Group =
    Finder(root).recursiveFindByType[Group].headOption.getOrElse(fail("no group was parsed"))

  "a comment in a group body" should {

    "be parsed into the group's contents, not its metadata" in { (_: TestData) =>
      val group = groupOf(parse(src, "groupComments"))
      val comments = group.contents.filter[Comment]
      comments.size mustBe 3
      comments.map(_.format).mkString(" ") must include("opens the body")
      // Being in `contents` is the whole point: metadata is where it used to have to go.
      group.metadata.filter[Comment] mustBe empty
    }

    "survive a prettify round trip still inside the group" in { (_: TestData) =>
      val pretty = prettify(parse(src, "groupComments"))
      pretty must include("opens the body")

      val again = groupOf(parse(pretty, "regen"))
      val comments = again.contents.filter[Comment]
      withClue(s"prettified output was:\n$pretty") {
        comments.size mustBe 3
        again.metadata.filter[Comment] mustBe empty
      }
    }

    "keep the group's inputs and outputs alongside the comments" in { (_: TestData) =>
      val again = groupOf(parse(prettify(parse(src, "groupComments")), "regen"))
      again.contents.filter[Input].size mustBe 1
      again.contents.filter[Output].size mustBe 1
    }
  }
}
