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

/** A comment — or a type — in a saga body.
  *
  * Reported by riddl-generator: a `//` comment between two saga steps was a PARSE ERROR whose
  * message ("Expected one of function | include | inlet | outlet | step | with") never mentioned
  * comments. Comments inside a step's `is { … }` block were always fine; only the saga body itself
  * rejected them, which pushed riddlg's `saga` pattern template to carry its explanation outside
  * the saga — the worst place for it.
  *
  * It was a rule that disagreed with its own AST, not a deliberate restriction. `OccursInSaga` is
  * `OccursInVitalDefinition | SagaStep` (AST.scala:931) and `OccursInVitalDefinition` is
  * `Type | Comment`, so both were always legal saga contents. `SagaParser.sagaDefinitions` was
  * simply the one container in this family that did not lead with `vitalDefinitionContents` —
  * DomainParser:38, FunctionParser:35, EpicParser:170 and ProcessorParser:69 all do. Hence `type`
  * was rejected in a saga body too, which the original report did not know.
  *
  * RIDDL is reflective, so these check the whole contract, not just that it parses: the comment
  * must land in the saga's CONTENTS, survive parse → prettify → re-parse still inside the saga, and
  * not be quietly relocated to metadata.
  */
class SagaCommentRoundTripTest extends AbstractValidatingTest {

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

  /** Comments in the three positions a saga body allows: opening it, between two steps, and closing
    * it. The middle one is exactly what riddl-generator reported.
    */
  private val src =
    """domain Dom is {
      |  context Ctx is {
      |    saga Checkout is {
      |      // opens the body
      |      step One is { ??? } reverted by { ??? }
      |      // compensation runs completed steps in reverse order
      |      step Two is { ??? } reverted by { ??? }
      |      // closes the body
      |    }
      |  }
      |}
      |""".stripMargin

  private def sagaOf(root: Root): Saga =
    Finder(root).recursiveFindByType[Saga].headOption.getOrElse(fail("no saga was parsed"))

  "a comment in a saga body" should {

    "be parsed into the saga's contents, not its metadata" in { (_: TestData) =>
      val saga = sagaOf(parse(src, "sagaComments"))
      val comments = saga.contents.filter[Comment]
      comments.size mustBe 3
      comments.map(_.format).mkString(" ") must include("compensation runs completed steps")
      saga.metadata.filter[Comment] mustBe empty
    }

    "survive a prettify round trip still inside the saga" in { (_: TestData) =>
      val pretty = prettify(parse(src, "sagaComments"))
      pretty must include("compensation runs completed steps")

      val again = sagaOf(parse(pretty, "regen"))
      val comments = again.contents.filter[Comment]
      withClue(s"prettified output was:\n$pretty") {
        comments.size mustBe 3
        again.metadata.filter[Comment] mustBe empty
      }
    }

    "keep the saga's steps alongside the comments" in { (_: TestData) =>
      val again = sagaOf(parse(prettify(parse(src, "sagaComments")), "regen"))
      again.contents.filter[SagaStep].size mustBe 2
    }
  }

  "a type in a saga body" should {

    /** Not in the original report, but the same hole: `OccursInVitalDefinition` is `Type | Comment`
      * and the rule omitted both.
      */
    "be parsed and survive a round trip" in { (_: TestData) =>
      val withType =
        """domain Dom is {
          |  context Ctx is {
          |    saga Checkout is {
          |      type Money is String
          |      step One is { ??? } reverted by { ??? }
          |      step Two is { ??? } reverted by { ??? }
          |    }
          |  }
          |}
          |""".stripMargin

      val saga = sagaOf(parse(withType, "sagaType"))
      saga.contents.filter[Type].size mustBe 1

      val again = sagaOf(parse(prettify(parse(withType, "sagaType")), "regen"))
      again.contents.filter[Type].size mustBe 1
      again.contents.filter[SagaStep].size mustBe 2
    }
  }
}
