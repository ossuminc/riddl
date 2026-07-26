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

/** A54/A45/A57: RIDDL is reflective — `put`/`return` and the value expressions (constructor,
  * get-value, value-ref) must emit (prettify) and re-parse to the same shape.
  */
class ValueRoundTripTest extends AbstractValidatingTest {

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
    """domain d is {
      |  context Calc is {
      |    type Sum is record { total: Integer }
      |    function Add is {
      |      returns record Sum
      |      return record Sum(total = "the total")
      |    }
      |  }
      |  application context UI is {
      |    type Greeting is record { text: String }
      |    command Refresh is { ??? }
      |    group Main is {
      |      form Entry acquires type Greeting
      |      output Panel presents type Greeting
      |    }
      |    handler Screen is {
      |      on command Refresh {
      |        put get from input Entry to output Panel
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "value expressions" should {
    "round-trip put/return and their values through prettify" in { (td: TestData) =>
      val pretty = prettify(parse(src, "src"))
      pretty must include("return record Sum(total = \"the total\")")
      pretty must include("put get from input Entry to output Panel")

      val regen = parse(pretty, "regen")
      val ret = Finder(regen)
        .recursiveFindByType[ReturnStatement]
        .headOption
        .getOrElse(fail("return statement lost"))
      ret.value match
        case c: Constructor =>
          c.ref.isInstanceOf[RecordRef] mustBe true
          c.args.size mustBe 1
          c.args.head.name.map(_.value) mustBe Some("total")
        case other => fail(s"expected a Constructor return value, got $other")

      val put = Finder(regen)
        .recursiveFindByType[PutStatement]
        .headOption
        .getOrElse(fail("put statement lost"))
      put.output.pathId.value mustBe Seq("Panel")
      put.value match
        case gv: GetValue =>
          gv.source match
            case ir: InputRef => ir.pathId.value mustBe Seq("Entry")
            case other        => fail(s"expected InputRef source, got $other")
        case other => fail(s"expected a GetValue put value, got $other")
    }
  }
}
