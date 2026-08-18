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

/** `foreach k, v in <mapping>` destructures an entry into its key and its value.
  *
  * RIDDL is reflective, so the second name must survive prettify → re-parse. It is exactly the kind
  * of thing that gets dropped silently: the emitter has a working single-name path, and a
  * `valueElement` it never consults still produces output that parses and validates — it just means
  * something else. A57's binding shipped with that defect for one commit for the same reason, which
  * is why this test exists at the same time as the feature rather than after it.
  *
  * The single-name form is re-asserted here too. Widening an emitter is a good way to break what it
  * already did.
  */
class ForeachDestructuringRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

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

  private val src =
    """domain d is {
      |  context c is {
      |    record Line is { sku: String }
      |    record St is { byId: mapping from Integer to Line, lines: many Line, note: String }
      |    command Cmd is { note: String }
      |    entity E is {
      |      state S of record d.c.St is {
      |        handler h is {
      |          on command d.c.Cmd is {
      |            foreach k, v in field byId { set field St.note to v.sku }
      |            foreach line in field lines { set field St.note to line.sku }
      |          }
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  /** Every foreach, as (element, valueElement), in source order. */
  private def loops(root: Root): Seq[(String, Option[String])] =
    Finder(root).recursiveFindByType[Statement].collect { case fs: ForeachStatement =>
      fs.element.value -> fs.valueElement.map(_.value)
    }

  "Foreach destructuring" should {

    "survive a prettify round trip with both names intact" in { (td: TestData) =>
      val root1 = parse(src, "src")
      loops(root1) mustBe Seq("k" -> Some("v"), "line" -> None)

      val pretty = prettify(root1)
      pretty must include("foreach k, v in field byId")
      // The single-name form must not acquire a comma on the way out.
      pretty must include("foreach line in field lines")
      pretty must not include ("foreach line,")

      val root2 = parse(pretty, "regen")
      // The whole point: the value name is still there, still attached to the same loop, and the
      // single-name loop has not grown one.
      loops(root2) mustBe Seq("k" -> Some("v"), "line" -> None)

      // And it is still a fixed point -- a second pass must not perturb it further.
      prettify(root2) mustBe pretty
    }
  }
}
