/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** RIDDL is reflective: A9's named-type `requires`/`returns` (a `TypeRef`) and the deprecated
  * inline `Aggregation` must both emit (prettify) and re-parse to the same shape.
  */
class RequiresReturnsRoundTripTest extends AbstractValidatingTest {

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
    """domain d is { context c is {
      |  record Args is { a: Integer }
      |  function f is { requires record Args returns record Args ??? }
      |  function g is { requires { b: Boolean } returns { r: Integer } ??? }
      |}}
      |""".stripMargin

  "requires/returns" should {
    "round-trip a named type ref and a deprecated inline aggregation through prettify" in {
      (td: TestData) =>
        val pretty = prettify(parse(src, "src"))
        pretty must include("requires record Args")
        pretty must include("returns  record Args")

        val funcs = Finder(parse(pretty, "regen")).recursiveFindByType[Function]
        val f = funcs.find(_.id.value == "f").get
        f.input.get mustBe a[TypeRef]
        val tr = f.input.get.asInstanceOf[TypeRef]
        tr.keyword mustBe "record"
        tr.pathId.format mustBe "Args"

        val g = funcs.find(_.id.value == "g").get
        g.input.get mustBe a[Aggregation]
        g.input.get.asInstanceOf[Aggregation].fields.head.id.value mustBe "b"
    }
  }
}
