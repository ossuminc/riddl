/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.Messages.DeprecationCode
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{AbstractTestingBasis, URL, pc}

/** Deprecations must carry a machine-readable identity, not just prose.
  *
  * A migration tool groups deprecations, counts them, and tells the user which ones a mechanical
  * fixer will resolve. Doing that off message TEXT means a regex that breaks silently the first
  * time someone rewords a message — so these tests assert on the CODE, and deliberately do not
  * assert on wording, which stays free to change.
  */
class DeprecationCodeTest extends AbstractTestingBasis {

  private def messagesIn(text: String, origin: String): Seq[Messages.Message] =
    val rpi = RiddlParserInput(text, URL.fromCwdPath(origin), "test")
    TopLevelParser.parseInputWithMessages(rpi) match
      case Left(errs)       => fail(errs.format)
      case Right((_, msgs)) => msgs.toSeq

  private def deprecationsIn(text: String): Seq[Messages.Message] =
    messagesIn(text, "dep.riddl").filter(_.isDeprecation)

  /** A minimal valid model whose handler body is the one construct under test.
    *
    * Deliberately parses cleanly apart from the deprecation: a parse ERROR would abort before the
    * deprecation was collected, which is what the first version of this test did.
    */
  private def modelWith(statement: String): String =
    s"""domain D is {
       |  context C is {
       |    command PlaceOrder yields event OrderPlaced is { id: Integer }
       |    event OrderPlaced is { id: Integer }
       |    entity E is {
       |      record Fields is { id: Integer }
       |      state Current of record E.Fields
       |      handler H is {
       |        on command PlaceOrder { $statement }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  // The `reply-to-yield` case that lived here is GONE with its deprecation. `reply` was a
  // deprecated synonym for `yield` until 2.0; it is now its own statement, required for a query's
  // result, and `reply event` is an ERROR rather than a deprecation. A code naming a deprecation
  // that can never fire is the vestigial shape this repo keeps removing.

  "a deprecated `prompt` statement" should {
    "carry the prompt-statement code" in {
      val deps = deprecationsIn(modelWith("""prompt "do a thing" """))
      deps.flatMap(_.deprecationCode) must contain(DeprecationCode.DoStatement)
    }
  }

  "a type-first aggregate declaration" should {

    /** `type X is command { … }` produces the SAME AST as `command X is { … }`, and PrettifyPass
      * already emits the kind-first form for both — so the old spelling never round-trips back to
      * itself. It is also strictly less expressive: `yields` exists only on the kind-first rule.
      */
    "carry the type-first-aggregate code and be auto-fixable" in {
      val deps = deprecationsIn(
        """domain D is {
          |  context C is {
          |    type Pay is command { amount: Integer }
          |  }
          |}
          |""".stripMargin
      )
      val m = deps
        .find(_.deprecationCode.contains(DeprecationCode.TypeFirstAggregate))
        .getOrElse(
          fail(s"no type-first-aggregate deprecation; got:\n${deps.map(_.format).mkString("\n")}")
        )
      m.autoFixable mustBe true
    }

    "say nothing for the kind-first spelling" in {
      // The other half of the contract. Without this, a deprecation that fired on EVERY aggregate
      // would pass the case above and nobody would notice until the corpus lit up.
      val deps = deprecationsIn(
        """domain D is {
          |  context C is {
          |    command Pay is { amount: Integer }
          |  }
          |}
          |""".stripMargin
      )
      deps.flatMap(_.deprecationCode) mustNot contain(DeprecationCode.TypeFirstAggregate)
    }

    "say nothing for a plain type whose expression is not an aggregate use case" in {
      // Scope guard: `type` itself is not deprecated, only the aggregate-use-case spelling of it.
      val deps = deprecationsIn(
        """domain D is {
          |  context C is {
          |    type Address is { street: String }
          |    type Name is String
          |  }
          |}
          |""".stripMargin
      )
      deps.flatMap(_.deprecationCode) mustNot contain(DeprecationCode.TypeFirstAggregate)
    }
  }

  "the code registry" should {
    "list every code it defines" in {
      DeprecationCode.all must contain(DeprecationCode.StateIsRecord)
      DeprecationCode.all.distinct.size mustBe DeprecationCode.all.size
    }

    "use stable kebab-case identifiers" in {
      // These strings are API: a consumer's migration report keys off them, so they must not
      // drift into prose or change shape.
      DeprecationCode.all.foreach { c =>
        c must fullyMatch regex "[a-z][a-z0-9-]*"
      }
    }
  }

  "non-deprecation messages" should {
    "carry no deprecation code" in {
      messagesIn("domain D is { ??? }", "ok.riddl").filterNot(_.isDeprecation).foreach { m =>
        m.deprecationCode mustBe None
        m.autoFixable mustBe false
      }
    }
  }
}
