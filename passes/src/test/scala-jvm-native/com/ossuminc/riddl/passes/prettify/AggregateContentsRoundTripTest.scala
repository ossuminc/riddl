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

/** An aggregate's contents are `Field | Method | Comment`, but the emitter rendered only the
  * fields.
  *
  * `RiddlFileEmitter.emitAggregation` called `emitFields(aggregation.fields)`, so a `Method` and a
  * `Comment` in a record body were both dropped on the floor. `RiddlFileEmitter.emitMethod` was
  * fully written and had ZERO callers anywhere in the repo — the capability existed and was never
  * invoked.
  *
  * The loss is SILENT, which is what makes it dangerous: the output still parses, still validates,
  * and simply contains less than the author wrote. `sbt riddlcPrettify` reformats riddl-models in
  * place, so every `method` in the corpus was one prettify run from deletion.
  *
  * Reported by riddl-models (`task/2026-08-14-prettify-emitter-drops-method-and-shown-by.md`),
  * which caught it only because a model built to exercise rarely-used constructs was prettified
  * before being committed. The comment case was NOT in that report and was found by this sweep.
  *
  * Sibling ORDER is asserted, not just membership: reflectivity means exact AST recovery, so a
  * fix that emitted all fields and then all methods would still be wrong.
  */
class AggregateContentsRoundTripTest extends AbstractValidatingTest {

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

  private val src =
    """domain Dom is {
      |  record Rec is {
      |    // opens the body
      |    aaa: Integer
      |    mmm(): Integer
      |    // closes the body
      |  }
      |}
      |""".stripMargin

  private def recordOf(root: Root): AggregateTypeExpression =
    Finder(root)
      .recursiveFindByType[Type]
      .map(_.typEx)
      .collectFirst { case ate: AggregateTypeExpression => ate }
      .getOrElse(fail("no aggregate type was parsed"))

  "an aggregate's methods and comments" should {

    "be parsed into the aggregate's contents" in { (_: TestData) =>
      val rec = recordOf(parse(src, "aggContents"))
      rec.fields.size mustBe 1
      rec.methods.size mustBe 1
      rec.contents.filter[Comment].size mustBe 2
    }

    "survive a prettify round trip" in { (_: TestData) =>
      val pretty = prettify(parse(src, "aggContents"))
      val rec = recordOf(parse(pretty, "regen"))
      withClue(s"prettified output was:\n$pretty") {
        rec.fields.size mustBe 1
        rec.methods.size mustBe 1
        rec.methods.head.id.value mustBe "mmm"
        rec.contents.filter[Comment].size mustBe 2
      }
    }

    "keep the authored sibling order across the round trip" in { (_: TestData) =>
      val pretty = prettify(parse(src, "aggContents"))
      val rec = recordOf(parse(pretty, "regen"))
      val kinds = rec.contents.toSeq.map {
        case _: Field   => "field"
        case _: Method  => "method"
        case _: Comment => "comment"
      }
      withClue(s"prettified output was:\n$pretty") {
        kinds mustBe Seq("comment", "field", "method", "comment")
      }
    }
  }
}
