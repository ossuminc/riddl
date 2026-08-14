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
  *
  * Lives in the SHARED test source set (it was `scala-jvm-native`, so JS never exercised it) —
  * this is a pure validation rule with no platform surface, and the check it covers is now the
  * one that decides a user-facing kind name.
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

  /** The same model with the `Id(...)` in a type-ALIAS position rather than a field position. */
  private def aliasModel(idType: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    repository Inventory is { ??? } with { briefly "r" }
       |    entity Order is {
       |      state S of record R is {
       |        handler H is { on other is { ??? } }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |    type Key is $idType with { briefly "alias" }
       |    record R is { key: Key } with { briefly "rec" }
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
      // The kind is named with the RIDDL KEYWORD, not a JVM class name: `getClass.getSimpleName`
      // worked only by the accident that all six Processor class names lowercase to their keyword,
      // and `Definition.kind` cannot be used either (a Streamlet overrides it to its SHAPE).
      text must include("repository")
    }
  }

  /** The check above was verified in FIELD position only, and passed there for the wrong reason:
    * the refMap key's parent for a field's `Id(…)` is the owning `Type`, which happens to be
    * validation's `parents.head` too. Everywhere else the parents differ and the lookup MISSED,
    * with `.foreach` turning the miss into "skip the check". riddl-models holds 232
    * `type X is Id(…)` aliases against 7 field-position uses, so the check was silent in ~97% of
    * real usage — in the position the keyword check was introduced to serve.
    */
  "the Id keyword check" should {
    "fire in a type-ALIAS position" in { (td: TestData) =>
      val text = diagnostics(aliasModel("Id(entity Inventory)"), "alias-mismatch")
        .justErrors.map(_.message).mkString("\n")
      text must include("declared as 'entity'")
      text must include("repository")
    }

    "accept a matching keyword in a type-ALIAS position" in { (td: TestData) =>
      diagnostics(aliasModel("Id(repository Inventory)"), "alias-ok").justErrors mustBe empty
    }

    "fire on an `on init`/`on term` PARAMETER" in { (td: TestData) =>
      // A parameter's type expression reached no `checkTypeExpression` call at all, so it skipped
      // this check AND every cardinality/pattern/range check a field of the same type gets.
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    repository Inventory is { ??? } with { briefly "r" }
          |    record R is { total: Integer } with { briefly "rec" }
          |    entity Order is {
          |      state S of record R is {
          |        handler H is {
          |          on init(k: Id(entity Inventory)) is { do "start" }
          |        } with { briefly "h" }
          |      } with { briefly "s" }
          |    } with { briefly "e" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val text = diagnostics(src, "param-mismatch").justErrors.map(_.message).mkString("\n")
      text must include("declared as 'entity'")
      text must include("repository")
    }

    "reach a parameter's other type checks too" in { (td: TestData) =>
      // Not about `Id(...)`: proves `checkTypeExpression` runs on a parameter at all. A Decimal
      // with a zero whole part is an ordinary Error a Field of the same type would draw; a
      // parameter drew nothing. (Chosen over a malformed regex because it is pure Scala and so
      // behaves identically on JVM, JS and Native -- this suite is in the shared source set.)
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    record R is { total: Integer } with { briefly "rec" }
          |    entity Order is {
          |      state S of record R is {
          |        handler H is {
          |          on init(k: Decimal(0,2)) is { do "start" }
          |        } with { briefly "h" }
          |      } with { briefly "s" }
          |    } with { briefly "e" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val text = diagnostics(src, "param-decimal").justErrors.map(_.message).mkString("\n")
      text must include("whole number part")
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
