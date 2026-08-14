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

/** `on init` and `on term` become invocable, so they need parameters.
  *
  * `on term`'s leading parameter is REQUIRED to be Id(this processor): it is invoked from
  * outside, so the caller must say which instance. `on init` has no such parameter -- there is
  * no instance yet, and the identity is minted by initiating.
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

    "REJECT a missing leading id parameter" in { (td: TestData) =>
      val text = diagnostics(
        entityWith("""on term(why: String) is { do "end" }"""), "term-no-id"
      ).justErrors.map(_.message).mkString("\n")
      text must include("first parameter")
      text must include("Id(")
    }

    "REJECT an Id of a DIFFERENT processor that merely shares the last path segment" in {
      (td: TestData) =>
        // The name-matching version accepted this with NO diagnostic. Reid overruled exactly this
        // pattern for task 6's tell addressing (`isAddressFieldFor` compares by `eq` against a
        // refMap lookup); one task adopted the ruling and the other did not, in the same feature,
        // over the same construct. Both paths end in `Order`, so only resolved identity can tell
        // them apart -- and getting it wrong means `on term` accepts an id that cannot name an
        // instance of the processor being terminated.
        val src =
          """domain Dom is {
            |  context Other is {
            |    entity Order is { ??? } with { briefly "foreign" }
            |  } with { briefly "o" }
            |  context Ctx is {
            |    record R is { total: Integer } with { briefly "r" }
            |    entity Order is {
            |      state S of record R is {
            |        handler H is {
            |          on term(oid: Id(entity Dom.Other.Order), why: String) is { do "end" }
            |        } with { briefly "h" }
            |      } with { briefly "s" }
            |    } with { briefly "e" }
            |  } with { briefly "c" }
            |} with { briefly "d" }
            |""".stripMargin
        val text = diagnostics(src, "term-foreign-id").justErrors.map(_.message).mkString("\n")
        text must include("first parameter")
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
