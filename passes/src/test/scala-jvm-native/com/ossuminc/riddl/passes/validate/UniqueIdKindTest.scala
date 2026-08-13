/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** `Id(P)` names any Processor, and the optional kind keyword must tell the truth.
  *
  * The keyword form is CANONICAL, not deprecated (Reid, 2026-08-13): keyword-name
  * disambiguation is a RIDDL-wide idiom, and `Order` alone could name a context, a message
  * or an entity. Keeping it earns the check below — a keyword that contradicts the
  * resolved kind is a lie a reader would believe.
  */
class UniqueIdKindTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def model(idType: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    repository Inventory is { ??? } with { briefly "r" }
       |    entity Order is {
       |      state S of record R is {
       |        handler H is { on other is { ??? } }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |    record R is { key: $idType } with { briefly "rec" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  /** Run the prettifier (flatten) over a Root and return the rendered source. */
  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    val result = Pass.runThesePasses(PassInput(root), creators)
    result.outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  /** parse -> prettify -> re-parse -> prettify, so the two prettified strings can be compared
    * for a reflective round trip.
    */
  private def prettifyTwice(src: String, origin: String): (String, String) =
    val first = prettify(parse(src, origin))
    val second = prettify(parse(first, s"$origin-regen"))
    (first, second)

  "Id(P)" should {

    "accept a repository, not only an entity" in { (td: TestData) =>
      // Before this change TypeValidation had checkPathRef[Entity], so this was
      // "Path 'Inventory' was not resolved" -- an Entity-shaped question asked of a repository.
      diagnostics(model("Id(repository Inventory)"), "id-repo").justErrors mustBe empty
    }

    "accept the bare form" in { (td: TestData) =>
      diagnostics(model("Id(Order)"), "id-bare").justErrors mustBe empty
    }

    "accept a matching keyword" in { (td: TestData) =>
      diagnostics(model("Id(entity Order)"), "id-entity").justErrors mustBe empty
    }

    "REJECT a keyword that contradicts the resolved kind" in { (td: TestData) =>
      // THE case that justifies keeping the keyword. `Id(entity Inventory)` reads as a
      // promise about Inventory that is false.
      val text = diagnostics(model("Id(entity Inventory)"), "id-mismatch")
        .justErrors.map(_.message).mkString("\n")
      text must include("declared as 'entity'")
      text must include("Repository")
    }
  }

  "the Id keyword" should {
    "survive a prettify round trip" in { (td: TestData) =>
      // Reflectivity: anything that parses must be emitted, and re-parsing must recover it.
      // Without this, `Id(repository Inventory)` silently prettifies to `Id(Inventory)`.
      val src = model("Id(repository Inventory)")
      val (first, second) = prettifyTwice(src, "id-roundtrip")
      first mustBe second
      first must include("Id(repository Inventory)")
    }
  }
}
