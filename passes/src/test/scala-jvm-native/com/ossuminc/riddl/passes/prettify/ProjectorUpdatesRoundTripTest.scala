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

/** A projector's `updates repository <path>` clause must survive prettify.
  *
  * It is semantic content, not formatting: without it a projector no longer names the repository
  * that persists its projection, and validation of the prettified output reports the projector as
  * incomplete. Prettify was dropping it from every projector — 237 clauses across 189 files of
  * riddl-models — which made the corpus impossible to canonicalise and blocked `.bast`
  * regeneration.
  *
  * The clause parses to a [[RepositoryRef]] in the projector's contents. A `RepositoryRef` is a
  * Reference rather than a Definition, so the prettify visitor never saw it — the same shape as the
  * comments that used to be dropped, and as the schema type references that do not count as usage.
  * Anything in `contents` that is not a Definition has to be emitted by hand.
  */
class ProjectorUpdatesRoundTripTest extends AbstractValidatingTest {

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
    """domain D is {
      |  context C is {
      |    type Datum is { id: String }
      |    repository Store is {
      |      schema Data is relational of datum as type D.C.Datum
      |      handler H is { ??? }
      |    }
      |    projector Board is {
      |      updates repository D.C.Store
      |      handler PH is { ??? }
      |    }
      |  }
      |}
      |""".stripMargin

  private def projectorOf(root: Root): Projector =
    Finder(root).recursiveFindByType[Projector].headOption.getOrElse(fail("no projector parsed"))

  "a projector's `updates repository` clause" should {

    "parse into the projector's contents" in { (_: TestData) =>
      projectorOf(parse(src, "updates")).contents.filter[RepositoryRef].size mustBe 1
    }

    "be emitted by prettify" in { (_: TestData) =>
      val pretty = prettify(parse(src, "updates"))
      withClue(s"prettified output was:\n$pretty") {
        pretty must include("updates repository")
      }
    }

    "survive a prettify round trip still naming the same repository" in { (_: TestData) =>
      val pretty = prettify(parse(src, "updates"))
      val again = projectorOf(parse(pretty, "regen"))
      val refs = again.contents.filter[RepositoryRef]
      withClue(s"prettified output was:\n$pretty") {
        refs.size mustBe 1
        refs.head.pathId.format must include("Store")
      }
    }
  }
}
