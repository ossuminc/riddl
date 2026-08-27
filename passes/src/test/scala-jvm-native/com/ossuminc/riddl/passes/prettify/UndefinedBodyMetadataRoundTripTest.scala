/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** A `???`-bodied definition keeps its metadata through prettify.
  *
  * Reported by riddl-examples against `2.0.0-rc.9-48-fdc5c171`: a `page X is { ??? } with { …
  * described by { … } }` came back as `page X is { ??? }`, so a prettified corpus lost its prose
  * and only said so as "missing description" warnings on the next validate.
  *
  * The report framed it as a GROUP bug, by analogy with the earlier body-less STATE fix. It is
  * neither: `RiddlFileEmitter.closeDef` guarded the metadata emission on the same condition as the
  * closing brace, and 13 containers share that method. So this asserts the shape across several
  * container kinds rather than just the one that was reported -- the report's own third acceptance
  * criterion, "audit the other `???`-admitting containers".
  */
class UndefinedBodyMetadataRoundTripTest extends AbstractValidatingTest {

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

  "a ???-bodied definition" should {

    "keep its metadata through prettify — the reported group case" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    page ProductDetails is { ??? } with {
          |      briefly as "Page showing product information"
          |      described as "Displays detailed information about a product."
          |    }
          |  }
          |}
          |""".stripMargin
      val pretty = prettify(parse(src, "group-meta"))
      pretty must include("Page showing product information")
      pretty must include("Displays detailed information about a product.")

      // And it must survive a re-parse in the same place, not merely appear in the text.
      val group = Finder(parse(pretty, "regen"))
        .recursiveFindByType[Group]
        .headOption
        .getOrElse(fail("group did not survive the round trip"))
      group.metadata.toSeq must not be empty
    }

    "keep it for the other containers sharing closeDef" in { (td: TestData) =>
      // One representative per shape: a processor, a vital definition, and an entity.
      val src =
        """domain d is {
          |  context c is { ??? } with { briefly as "the context brief" }
          |  saga s is { ??? } with { briefly as "the saga brief" }
          |} with { briefly as "the domain brief" }
          |""".stripMargin
      val pretty = prettify(parse(src, "others"))
      pretty must include("the context brief")
      pretty must include("the saga brief")
      pretty must include("the domain brief")

      val root = parse(pretty, "others-regen")
      val domain = root.domains.headOption.getOrElse(fail("domain lost"))
      domain.metadata.toSeq must not be empty
      domain.contexts.headOption.getOrElse(fail("context lost")).metadata.toSeq must not be empty
    }

    "still emit the ??? itself, not just the metadata" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is { ??? } with { briefly as "b" }
          |}
          |""".stripMargin
      val pretty = prettify(parse(src, "marker"))
      pretty must include("???")
      // `{ ??? } with {` on one line — the metadata attaches to the self-closed body rather than
      // starting a stray line of its own.
      pretty must include("{ ??? } with {")
    }
  }
}
