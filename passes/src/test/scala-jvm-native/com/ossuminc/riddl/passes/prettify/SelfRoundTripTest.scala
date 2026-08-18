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

/** Task 2 (processor-instance identity): `self` was the ONE construct of the six this branch adds
  * with no round-trip test on any surface -- `initiate`, `terminate`, `by` and the lifecycle
  * parameters each got prettify AND BAST tests, and the `Id` keyword got a BAST one. The final
  * whole-branch review verified by hand that prettify and BAST both handle `self` correctly; this
  * test and its BAST sibling exist because nothing PINNED that, and this repo's history is that
  * unpinned reflectivity is what regresses.
  *
  * `self` is a VALUE, so it is reached inside a `let`'s `expression` field -- which
  * `Finder.recursiveFindByType` does not descend into (same reason `InitiateRoundTripTest` walks
  * the tree by hand). Both spellings are covered: the bare `self` (whose type is the synthesized
  * Aggregation) and the field form `self.id`, since the field is an `Option` the emitter could drop
  * without any other test noticing.
  */
class SelfRoundTripTest extends AbstractValidatingTest {

  private val src =
    """domain Dom is {
      |  context Ctx is {
      |    command Go is { why: String } with { briefly "c" }
      |    record R is { total: Integer } with { briefly "r" }
      |    entity Order is {
      |      state S of record R is {
      |        handler H is {
      |          on command Go {
      |            let me = self
      |            let mine = self.id
      |            let v = self.version
      |          }
      |        } with { briefly "h" }
      |      } with { briefly "s" }
      |    } with { briefly "e" }
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

  /** The `SelfValue`s bound by the on-clause's `let`s, in source order. */
  private def selvesIn(root: Root): Seq[SelfValue] =
    val domain =
      root.contents.toSeq.collectFirst { case d: Domain => d }.getOrElse(fail("no domain"))
    val context =
      domain.contents.toSeq.collectFirst { case c: Context => c }.getOrElse(fail("no context"))
    val entity = context.contents.toSeq
      .collectFirst { case e: Entity => e }
      .getOrElse(fail("no entity"))
    val state =
      entity.contents.toSeq.collectFirst { case s: State => s }.getOrElse(fail("no state"))
    val handler =
      state.contents.toSeq.collectFirst { case h: Handler => h }.getOrElse(fail("no handler"))
    val clause = handler.clauses.headOption.getOrElse(fail("no on-clause"))
    clause.contents.toSeq.collect { case ls: LetStatement => ls }.collect {
      case ls if ls.expression.isInstanceOf[SelfValue] => ls.expression.asInstanceOf[SelfValue]
    }

  "self" should {
    "round-trip through prettify (parse -> prettify -> re-parse)" in { (td: TestData) =>
      val original = parse(src, "src")
      val originalSelves = selvesIn(original)
      originalSelves.map(_.field.map(_.value)) mustBe Seq(None, Some("id"), Some("version"))

      val pretty = prettify(original)
      pretty must include("self")
      pretty must include("self.id")
      pretty must include("self.version")

      val regen = parse(pretty, "regen")
      selvesIn(regen).map(_.field.map(_.value)) mustBe originalSelves.map(_.field.map(_.value))
    }

    "be a fixed point of the prettifier" in { (td: TestData) =>
      val first = prettify(parse(src, "fp"))
      val second = prettify(parse(first, "fp-regen"))
      first mustBe second
    }
  }
}
