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

/** Task 4 (processor-instance identity): `initiate` is a new [[com.ossuminc.riddl.language.AST.Value]],
  * so RIDDL's reflectivity mandate requires a prettify round trip -- parse -> prettify(flatten=true)
  * -> re-parse -- proving it survives at the SAME place, following the template of
  * `RepositoryDomainScopeRoundTripTest` / `IdentifierQuotingRoundTripTest` /
  * `LifecycleParametersRoundTripTest` (this module's own precedent for Task 3's sibling feature).
  * Runs on JVM AND Native, unlike a plain `scalajvm` test -- put here rather than under
  * `passes/src/test/scalajvm/` for that reason. The BAST round trip is legitimately JVM-only (BAST
  * I/O has no Native-friendly harness in this test suite) and stays in
  * `InitiateBASTRoundTripTest.scala`.
  *
  * `Finder.recursiveFindByType` does NOT descend into a `LetStatement`'s `expression` field (its
  * `consider` walk only descends `Container`/`When`/`Match`/`Foreach`/`SagaStep`), so this test walks
  * the tree directly to the `Initiate` node instead of relying on it -- same reasoning as
  * `InitiateFileTest`.
  */
class InitiateRoundTripTest extends AbstractValidatingTest {

  private val src =
    """domain Dom is {
      |  context Ctx is {
      |    record R is { total: String } with { briefly "r" }
      |    entity Order is {
      |      state S of record R is {
      |        handler H is {
      |          on init(total: String) { do "start" }
      |        } with { briefly "h" }
      |      } with { briefly "s" }
      |    } with { briefly "e" }
      |    entity Caller is {
      |      state CS of record R is {
      |        handler CH is {
      |          on init { let oid = initiate entity Order("5") }
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

  /** Walk down to the `Caller` entity's `on init` clause and pull out its single `let`'s
    * `Initiate` expression -- mirrors `InitiateFileTest`'s direct walk, for the same reason
    * (Finder does not descend into LetStatement).
    */
  private def initiateIn(root: Root): Initiate =
    val domain =
      root.contents.toSeq.collectFirst { case d: Domain => d }.getOrElse(fail("no domain"))
    val context =
      domain.contents.toSeq.collectFirst { case c: Context => c }.getOrElse(fail("no context"))
    val caller = context.contents.toSeq
      .collectFirst { case e: Entity if e.id.value == "Caller" => e }
      .getOrElse(fail("no Caller entity"))
    val state = caller.contents.toSeq.collectFirst { case s: State => s }.getOrElse(fail("no state"))
    val handler =
      state.contents.toSeq.collectFirst { case h: Handler => h }.getOrElse(fail("no handler"))
    val onInit = handler.clauses
      .collectFirst { case oic: OnInitializationClause => oic }
      .getOrElse(fail("no on-init clause"))
    val let = onInit.contents.toSeq
      .collectFirst { case ls: LetStatement => ls }
      .getOrElse(fail("no let statement"))
    let.expression match
      case init: Initiate => init
      case other           => fail(s"expected an Initiate, got $other")

  "initiate" should {
    "round-trip through prettify (parse -> prettify -> re-parse)" in { (td: TestData) =>
      val original = parse(src, "src")
      val originalInit = initiateIn(original)

      val pretty = prettify(original)
      pretty must include("initiate")

      val regen = parse(pretty, "regen")
      val regenInit = initiateIn(regen)

      regenInit.processor.pathId.format mustBe originalInit.processor.pathId.format
      regenInit.args.size mustBe originalInit.args.size
      regenInit.args.map(_.value.format) mustBe originalInit.args.map(_.value.format)
    }

    "keep the bare (no-parens) form parenthesis-free after a round trip" in { (td: TestData) =>
      val bareSrc =
        """domain Dom is {
          |  context Ctx is {
          |    entity Widget is {
          |      handler H is {
          |        on init { do "start" }
          |      } with { briefly "h" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      handler CH is {
          |        on init { let oid = initiate entity Widget }
          |      } with { briefly "ch" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val pretty = prettify(parse(bareSrc, "bare"))
      pretty must include("initiate entity Widget")
      pretty must not include "initiate entity Widget("
    }
  }
}
