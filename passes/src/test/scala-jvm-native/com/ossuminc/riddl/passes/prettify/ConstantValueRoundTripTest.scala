/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** The numeric-literals plan (Task 4) widened `Constant.value` from a bare `LiteralString` to
  * `ConstantValue = LiteralString | NumericLiteral | BooleanLiteral | PromptValue`, AND changed a
  * `constant`'s separator from `is` to `:` (prettify's preferred spelling — both still parse). A
  * whole-branch review found no round-trip test covering all four value kinds together, nor one
  * pinning the separator convergence — this closes that gap, following the template of
  * `InitiateRoundTripTest` / `RepositoryDomainScopeRoundTripTest`.
  *
  * Each case asserts the value survives as the RIGHT NODE TYPE after parse -> prettify(flatten) ->
  * re-parse, not merely as equivalent text — the same standard `NumericLiteralJsonRoundTripTest`
  * and the BAST round-trip suites hold every other surface to.
  */
class ConstantValueRoundTripTest extends AbstractValidatingTest {

  private val src =
    """domain D is {
      |  context C is {
      |    constant Str is String = "hello"
      |    constant Num is Integer = 42
      |    constant Flag is Boolean = true
      |    constant Hint is Real = prompt("give a hint")
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin)) match
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

  private def constantsOf(root: Root): Map[String, Constant] =
    val ctx = AST.getContexts(AST.getTopLevelDomains(root).head).head
    ctx.constants.map(c => c.id.value -> c).toMap

  "a constant" should {

    "round-trip all four value kinds as the same node type, and converge `is` to `:`" in {
      (td: TestData) =>
        val original = constantsOf(parse(src, "src"))

        val pretty = prettify(parse(src, "src"))
        // The separator convergence: prettify always emits `:`, regardless of the `is` the author
        // wrote (`RiddlFileEmitter.emitConstant` hardcodes it).
        pretty must include("constant Str:")
        pretty must include("constant Num:")
        pretty must include("constant Flag:")
        pretty must include("constant Hint:")
        pretty must not include "constant Str is"
        pretty must not include "constant Num is"
        pretty must not include "constant Flag is"
        pretty must not include "constant Hint is"

        val regen = constantsOf(parse(pretty, "regen"))

        regen.keySet mustBe original.keySet

        regen("Str").value match
          case ls: LiteralString => ls.s mustBe "hello"
          case other             => fail(s"expected a LiteralString, got $other")

        regen("Num").value match
          case nl: NumericLiteral => nl.text mustBe "42"
          case other              => fail(s"expected a NumericLiteral, got $other")

        regen("Flag").value match
          case bl: BooleanLiteral => bl.value mustBe true
          case other              => fail(s"expected a BooleanLiteral, got $other")

        regen("Hint").value match
          case pv: PromptValue => pv.prompt.s mustBe "give a hint"
          case other           => fail(s"expected a PromptValue, got $other")
    }

    "converge a quoted numeric literal to a bare NumericLiteral after one prettify pass" in {
      (td: TestData) =>
        // The `QuotedConstantLiteral` deprecation: the parser CONSUMES the quoted spelling into a
        // `NumericLiteral` at parse time, so a single prettify pass drops the quotes for good --
        // there is no lingering old-shaped node for a second pass to still be fixing.
        val quotedSrc =
          """domain D is {
            |  context C is {
            |    constant N is Natural = "10"
            |  } with { briefly "c" }
            |} with { briefly "d" }
            |""".stripMargin
        val pretty = prettify(parse(quotedSrc, "quoted-src"))
        pretty must include("constant N: Natural = 10")
        pretty must not include "\"10\""

        val regen = constantsOf(parse(pretty, "quoted-regen"))
        regen("N").value match
          case nl: NumericLiteral => nl.text mustBe "10"
          case other              => fail(s"expected a NumericLiteral, got $other")
    }
  }
}
