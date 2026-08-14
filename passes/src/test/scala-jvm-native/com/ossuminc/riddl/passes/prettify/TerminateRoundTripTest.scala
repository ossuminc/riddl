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

/** Task 5 (processor-instance identity): `terminate` is a new [[com.ossuminc.riddl.language.AST.Statement]],
  * so RIDDL's reflectivity mandate requires a prettify round trip -- parse -> prettify(flatten=true)
  * -> re-parse -- proving it survives at the SAME place, following `InitiateRoundTripTest`'s
  * template (Task 4's sibling feature). Runs on JVM AND Native, unlike a plain `scalajvm` test --
  * put here rather than under `passes/src/test/scalajvm/` for that reason. The BAST round trip is
  * legitimately JVM-only (BAST I/O has no Native-friendly harness in this test suite) and stays in
  * `TerminateBASTRoundTripTest.scala`.
  *
  * Unlike `initiate` (a VALUE, typically wrapped in a `let`, which `Finder.recursiveFindByType`
  * cannot see through), `terminate` is a bare STATEMENT sitting directly in an on-clause's
  * `contents`, which `Finder` DOES descend into (it walks every `Container[?]`) -- so this test
  * uses `Finder` directly rather than a manual tree walk.
  */
class TerminateRoundTripTest extends AbstractValidatingTest {

  private val src =
    """domain Dom is {
      |  context Ctx is {
      |    record R is { total: String } with { briefly "r" }
      |    entity Order is {
      |      state S of record R is {
      |        handler H is {
      |          on init { do "start" }
      |          on term(oid: Id(entity Order)) { do "end" }
      |        } with { briefly "h" }
      |      } with { briefly "s" }
      |    } with { briefly "e" }
      |    entity Caller is {
      |      state CS of record R is {
      |        handler CH is {
      |          on init {
      |            let oid = initiate entity Order
      |            terminate entity Order(oid)
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

  private def terminateIn(root: Root): TerminateStatement =
    Finder(root).recursiveFindByType[TerminateStatement].headOption
      .getOrElse(fail("no TerminateStatement found"))

  "terminate" should {
    "round-trip through prettify (parse -> prettify -> re-parse)" in { (td: TestData) =>
      val original = parse(src, "src")
      val originalTerm = terminateIn(original)

      val pretty = prettify(original)
      pretty must include("terminate")

      val regen = parse(pretty, "regen")
      val regenTerm = terminateIn(regen)

      regenTerm.processor.pathId.format mustBe originalTerm.processor.pathId.format
      regenTerm.args.size mustBe originalTerm.args.size
      regenTerm.args.map(_.value.format) mustBe originalTerm.args.map(_.value.format)
    }

    // The bare `terminate P` form RETURNED on 2026-08-14. It had been removed because it could
    // never validate -- `on term`'s leading `Id(...)` parameter was required, so a no-argument
    // `terminate` could not satisfy the arity check -- and dropping that requirement (`self.id`
    // already names the instance) left nothing holding the asymmetry with `initiate` up.
    //
    // What this pins is the reflectivity consequence either way: whatever `format` emits for an
    // empty argument list must be readable by the parser that produced it. It now emits the BARE
    // form, mirroring `Initiate.format`.
    "emit the BARE form for an empty argument list, and re-parse it" in {
      (td: TestData) =>
        val emptySrc =
          """domain Dom is {
            |  context Ctx is {
            |    entity Widget is {
            |      handler H is {
            |        on init { do "start" }
            |        on term { do "end" }
            |      } with { briefly "h" }
            |    } with { briefly "e" }
            |    entity Caller is {
            |      handler CH is {
            |        on init { terminate entity Widget }
            |      } with { briefly "ch" }
            |    } with { briefly "ce" }
            |  } with { briefly "c" }
            |} with { briefly "d" }
            |""".stripMargin
        val pretty = prettify(parse(emptySrc, "empty-args"))
        pretty must include("terminate entity Widget")
        pretty must not include "terminate entity Widget()"
        // The proof that matters: the emitted text is readable by the parser that produced it.
        terminateIn(parse(pretty, "empty-args-regen")).args mustBe empty
    }

    "still accept the explicit empty parens, normalizing them to the bare form" in {
      (td: TestData) =>
        // `terminate P()` keeps parsing -- the grammar is not the place to encode arity, and
        // models written while the parens were mandatory must not break. Prettify converges on
        // ONE spelling, which is what makes the round trip idempotent.
        val emptyParens =
          """domain Dom is {
            |  context Ctx is {
            |    entity Widget is {
            |      handler H is {
            |        on init { do "start" }
            |        on term { do "end" }
            |      } with { briefly "h" }
            |    } with { briefly "e" }
            |    entity Caller is {
            |      handler CH is {
            |        on init { terminate entity Widget() }
            |      } with { briefly "ch" }
            |    } with { briefly "ce" }
            |  } with { briefly "c" }
            |} with { briefly "d" }
            |""".stripMargin
        terminateIn(parse(emptyParens, "empty-parens")).args mustBe empty
        prettify(parse(emptyParens, "empty-parens2")) must not include "terminate entity Widget()"
    }
  }
}
