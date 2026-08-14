/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** `on init` and `on term` become invocable, so they MAY take parameters.
  *
  * Neither clause requires any. `on term`'s leading parameter was REQUIRED to be Id(this
  * processor) until 2026-08-14, on the reasoning that the caller must say which instance --
  * reversed by Reid because `self.id` is already live for the whole clause, so the requirement
  * only forced the author to restate what the language supplies. `on init` never had one: there
  * is no instance yet, and the identity is minted by initiating.
  *
  * Removing it also restored the bare `terminate P` form, whose absence had been justified
  * SOLELY by the requirement -- see `TerminateRoundTripTest`.
  */
class LifecycleParametersTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  /** The state record deliberately declares `total`/`tier` and the parameter cases below use
    * `seed`/`buyer`. A parameter whose name COLLIDES with a state field resolves through the state
    * even when the parameter scope does not exist at all -- which is how the original fixture
    * (`on init(total: Integer)` beside `record Fields is { total: Integer }`) made a declare-only
    * feature look complete.
    */
  private def entityWith(clauses: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    record R is { total: Integer, tier: Integer } with { briefly "r" }
       |    record Customer is { name: String, tier: Integer } with { briefly "cust" }
       |    entity Order is {
       |      state S of record R is {
       |        handler H is { $clauses } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "on init parameters" should {
    "parse and validate" in { (td: TestData) =>
      diagnostics(
        entityWith("""on init(total: Integer) is { do "start" }"""), "init-params"
      ).justErrors mustBe empty
    }

    "remain optional" in { (td: TestData) =>
      diagnostics(entityWith("""on init is { do "start" }"""), "init-none")
        .justErrors mustBe empty
    }

    "REJECT a parameter naming an undefined type" in { (td: TestData) =>
      // THE case proving parameters are TRAVERSED. They are held in a FIELD, not in
      // `contents`, and Pass.traverse's generic Branch arm walks contents ONLY -- so without
      // its own traverse case this model validates clean while naming a type that need not
      // exist. Same shape as Correlation.timeoutStatements.
      val text = diagnostics(
        entityWith("""on init(x: Nonexistent) is { do "start" }"""), "init-bad-type"
      ).justErrors.map(_.message).mkString("\n")
      text must include("Nonexistent")
    }
  }

  "on term parameters" should {
    "accept a leading Id of the enclosing processor" in { (td: TestData) =>
      diagnostics(
        entityWith("""on term(oid: Id(entity Order), why: String) is { do "end" }"""),
        "term-ok"
      ).justErrors mustBe empty
    }

    "ACCEPT a clause with NO parameters at all" in { (td: TestData) =>
      // Reid, 2026-08-14, reversing the leading-Id requirement: the id of the instance being
      // terminated is ALREADY available as `self.id`, which stays live to the very end of the
      // clause, so requiring it as a parameter demands that the author restate what the language
      // already supplies. Argumentless `on term` is expected to be the COMMON form.
      diagnostics(
        entityWith("""on term is { do "end" }"""), "term-no-params"
      ).justErrors mustBe empty
    }

    "ACCEPT parameters that are NOT a leading id" in { (td: TestData) =>
      // The requirement is gone, not relaxed to "if present it must be an Id": a termination
      // reason is a perfectly ordinary thing to pass, and nothing about it needs to be an id.
      diagnostics(
        entityWith("""on term(why: String) is { do "end" }"""), "term-non-id-param"
      ).justErrors mustBe empty
    }

    "make `self.id` readable in the clause body" in { (td: TestData) =>
      // This is the PREMISE the removal rests on, so it is pinned rather than assumed. If `self`
      // did not resolve here there would be no way to obtain the id at all, and dropping the
      // parameter would have removed the only means of naming the instance being terminated.
      // `enclosingProcessorOf` terminates at Function/Saga only, so an `on term` inside a State
      // still finds the Entity.
      //
      // Asserts NO errors rather than "no error mentioning self". The weaker form passed even
      // with the fix reverted -- the clause failed for a DIFFERENT reason (the missing leading
      // parameter), so the assertion never spoke to `self` at all. A test that passes in both
      // states measures nothing, which the revert proof is what caught.
      diagnostics(
        entityWith("""on term is { let who = self.id }"""), "term-self-id"
      ).justErrors mustBe empty
    }
  }

  /** Finding #1 of the final whole-branch review: the feature was DECLARE-ONLY. Parameters parsed,
    * resolved, prettified and round-tripped, but reading one from the clause body was an Error --
    * `valueScopeField` enumerates entity state, the handled message and a function's `requires`
    * input, and an on-init/on-term parameter is none of those. It is the whole point of the
    * feature, and it is what `initiate`/`terminate` argument passing is FOR.
    */
  "an on-clause parameter" should {
    "be READABLE from the clause body" in { (td: TestData) =>
      diagnostics(
        entityWith("""on init(seed: Integer) is { set field S.total to seed }"""),
        "param-read"
      ).justErrors mustBe empty
    }

    "support a `param.field` walk through its type" in { (td: TestData) =>
      diagnostics(
        entityWith("""on init(buyer: Customer) is { set field S.tier to buyer.tier }"""),
        "param-walk"
      ).justErrors mustBe empty
    }

    "be readable from an `on term` body too" in { (td: TestData) =>
      diagnostics(
        entityWith(
          """on term(oid: Id(entity Order), why: Integer) is { set field S.total to why }"""
        ),
        "term-param-read"
      ).justErrors mustBe empty
    }

    "be reachable from a NESTED statement body" in { (td: TestData) =>
      // `checkStatementScopes` threads the scope through when/match/foreach recursion, so depth
      // must not lose it -- the same reachability property `checkInitiate`/`checkTerminate` needed.
      diagnostics(
        entityWith(
          """on init(seed: Integer) is { when true then { set field S.total to seed } end }"""
        ),
        "param-nested"
      ).justErrors mustBe empty
    }

    "NOT make every name resolve" in { (td: TestData) =>
      // The counter-example. A scope that accepted anything would pass every case above while
      // being no scope at all.
      val text = diagnostics(
        entityWith("""on init(seed: Integer) is { set field S.total to nosuchname }"""),
        "param-negative"
      ).justErrors.map(_.message).mkString("\n")
      text must include("nosuchname")
    }

    "NOT walk a field its type does not have" in { (td: TestData) =>
      val text = diagnostics(
        entityWith("""on init(buyer: Customer) is { set field S.tier to buyer.nosuchfield }"""),
        "param-walk-negative"
      ).justErrors.map(_.message).mkString("\n")
      text must include("buyer.nosuchfield")
    }

    "be SHADOWED by a `let` of the same name declared in the body" in { (td: TestData) =>
      // Lexical order: the `let` is the inner binding. `valueRefTypeExpr` consults the parameter
      // scope BEFORE the `let` scope, so this is expressed by dropping the name when the `let` is
      // reached rather than by consultation order.
      diagnostics(
        entityWith(
          """on init(seed: Integer) is { let seed = "x" set field S.total to seed }"""
        ),
        "param-shadowed"
      ).justErrors mustBe empty
    }
  }
}
