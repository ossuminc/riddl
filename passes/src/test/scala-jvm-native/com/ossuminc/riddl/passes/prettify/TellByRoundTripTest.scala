/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** Task 6 (processor-instance identity): `tell ... by <field>` adds an optional trailing
  * [[com.ossuminc.riddl.language.AST.Identifier]] to [[com.ossuminc.riddl.language.AST.TellStatement]],
  * so RIDDL's reflectivity mandate requires a prettify round trip -- parse -> prettify(flatten=true)
  * -> re-parse -- proving `by` survives at the SAME place. Follows `TerminateRoundTripTest`'s
  * template (Task 5's sibling feature). Runs on JVM AND Native, unlike a plain `scalajvm` test.
  */
class TellByRoundTripTest extends AbstractValidatingTest {

  private val src =
    """domain Dom is {
      |  context Ctx is {
      |    command Ship is { fromOrder: Id(entity Order), toOrder: Id(entity Order) } with { briefly "s" }
      |    record R is { total: String } with { briefly "r" }
      |    entity Order is {
      |      state S of record R is {
      |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
      |      } with { briefly "s" }
      |    } with { briefly "e" }
      |    entity Caller is {
      |      state CS of record R is {
      |        handler CH is {
      |          on init {
      |            tell command Ship(fromOrder = f, toOrder = t) to entity Order by toOrder
      |          }
      |        } with { briefly "ch" }
      |      } with { briefly "cs" }
      |    } with { briefly "ce" }
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

  private def tellIn(root: Root): TellStatement =
    Finder(root).recursiveFindByType[TellStatement].headOption
      .getOrElse(fail("no TellStatement found"))

  "tell ... by" should {
    "round-trip through prettify (parse -> prettify -> re-parse)" in { (td: TestData) =>
      val original = parse(src, "src")
      val originalTell = tellIn(original)
      originalTell.by.map(_.value) mustBe Some("toOrder")

      val pretty = prettify(original)
      pretty must include("by toOrder")

      val regen = parse(pretty, "regen")
      val regenTell = tellIn(regen)

      regenTell.processorRef.pathId.format mustBe originalTell.processorRef.pathId.format
      regenTell.by.map(_.value) mustBe originalTell.by.map(_.value)
    }

    "keep the bare (no-'by') form 'by'-free after a round trip" in { (td: TestData) =>
      val bareSrc =
        """domain Dom is {
          |  context Ctx is {
          |    command Ship is { orderId: Id(entity Order) } with { briefly "s" }
          |    record R is { total: String } with { briefly "r" }
          |    entity Order is {
          |      state S of record R is {
          |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
          |      } with { briefly "s" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on init { tell command Ship(orderId = o) to entity Order }
          |        } with { briefly "ch" }
          |      } with { briefly "cs" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val pretty = prettify(parse(bareSrc, "bare"))
      pretty must include("tell command Ship(orderId = o) to entity Order")
      pretty must not include "by"
    }
  }
}
