/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Finder}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A value's type ascription is a type NAME, carried as `typeRef` (BACKLOG [1.26], Reid's Q5).
  *
  * `EmptyValue` and `PromptValue` gained a trailing `typeRef`; the old `typeEx` is deprecated but
  * still populated -- mirrored for a name, and the only record of a retired EXPRESSION ascription,
  * which validation reports. This pins the classification at parse time and that it survives every
  * reflective surface that does not go through JSON (prettify -> re-parse, and BAST), for a name
  * and for an expression on both nodes, plus the unascribed form.
  */
class ValueAscriptionReflectionTest extends AbstractValidatingTest {

  private val src =
    """domain D is {
      |  context C is {
      |    type MaybeNote is String?
      |    command Go is { why: String }
      |    entity E is {
      |      handler H is {
      |        on command Go is {
      |          let emptyName = empty MaybeNote
      |          let emptyRecord = empty record MaybeNote
      |          let emptyExpr = empty String?
      |          let promptName = prompt("x") as MaybeNote
      |          let promptExpr = prompt("x") as String*
      |          let promptBare = prompt("x")
          let promptPredef = prompt("x") as Real
          let promptParams = prompt("x") as String(1,30)
      |          do "done"
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def let(root: Branch[?], name: String): Value =
    Finder(root)
      .recursiveFindByType[LetStatement]
      .find(_.identifier.value == name)
      .getOrElse(fail(s"no let named '$name'"))
      .expression

  /** What must survive: which form the ascription takes, and what it says. */
  private def shape(v: Value): (Option[(String, String)], Option[String]) = v match
    case ev: EmptyValue =>
      (ev.typeRef.map(tr => tr.keyword -> tr.pathId.format), ev.expressionAscription.map(_.format))
    case pv: PromptValue =>
      (pv.typeRef.map(tr => tr.keyword -> tr.pathId.format), pv.expressionAscription.map(_.format))
    case other => fail(s"not an ascribable value: $other")

  private val expected: Map[String, (Option[(String, String)], Option[String])] = Map(
    "emptyName" -> (Some("type" -> "MaybeNote"), None),
    "emptyRecord" -> (Some("record" -> "MaybeNote"), None),
    "emptyExpr" -> (None, Some("Optional")),
    "promptName" -> (Some("type" -> "MaybeNote"), None),
    "promptExpr" -> (None, Some("ZeroOrMore")),
    "promptBare" -> (None, None),
    // A bare predefined type is spelled as a name and is one; a parameterized one is not.
    "promptPredef" -> (Some("type" -> "Real"), None),
    "promptParams" -> (None, Some("String(1,30)"))
  )

  private def assertShapes(root: Branch[?], surface: String): Unit =
    expected.foreach { case (name, (ref, expr)) =>
      val (gotRef, gotExpr) = shape(let(root, name))
      withClue(s"$surface, let $name: ") {
        gotRef mustBe ref
        // An expression is identified by its node kind, not its text: the cardinality wrapper is
        // what makes it an expression rather than a name.
        gotExpr.isDefined mustBe expr.isDefined
      }
    }

  "parsing" should {
    "fill `typeRef` for a name and leave it empty for an expression" in { (td: TestData) =>
      val root = parse(src, td.name)
      assertShapes(root, "parse")
      let(root, "emptyExpr").asInstanceOf[EmptyValue].expressionAscription.get mustBe an[Optional]
      let(root, "promptExpr").asInstanceOf[PromptValue].expressionAscription.get mustBe
        a[ZeroOrMore]
    }

    "mirror a name into the deprecated `typeEx`, so existing readers see no change" in {
      (td: TestData) =>
        val ev = let(parse(src, td.name), "emptyName").asInstanceOf[EmptyValue]
        ev.ascribedType mustBe Some(
          AliasedTypeExpression(ev.typeRef.get.loc, "type", ev.typeRef.get.pathId)
        )
    }
  }

  "prettify" should {
    "re-parse to the same ascriptions" in { (td: TestData) =>
      val pretty = Pass
        .runThesePasses(
          PassInput(parse(src, td.name)),
          Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
            PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
          }
        )
        .outputs
        .outputOf[PrettifyOutput](PrettifyPass.name)
        .getOrElse(fail("PrettifyPass produced no output"))
        .state
        .filesAsString
      pretty must include("empty MaybeNote")
      pretty must include("""prompt("x") as MaybeNote""")
      assertShapes(parse(pretty, "regen"), "prettify")
    }

    "emit a `typeRef` built through the API, with no mirrored `typeEx`" in { (td: TestData) =>
      val tr = TypeRef(At.empty, "type", PathIdentifier(At.empty, Seq("MaybeNote")))
      EmptyValue(At.empty, typeRef = Some(tr)).format mustBe "empty type MaybeNote"
      EmptyValue(At.empty, typeRef = Some(tr)).ascribedType must not be empty
    }
  }

  "BAST" should {
    "round-trip both forms on both nodes" in { (td: TestData) =>
      val bytes = Pass
        .runThesePasses(PassInput(parse(src, td.name)), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("BASTWriterPass produced no output"))
        .bytes
      BASTReader(bytes).read() match
        case Right(decoded) => assertShapes(decoded, "BAST")
        case Left(msgs)     => fail(s"BAST round trip failed:\n${msgs.format}")
    }
  }
}
