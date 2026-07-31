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


  "a deprecated `reply` statement" should {
    "carry the reply-to-yield code and be auto-fixable" in {
      val deps = deprecationsIn(modelWith("reply event OrderPlaced"))
      val m = deps
        .find(_.deprecationCode.contains(DeprecationCode.ReplyToYield))
        .getOrElse(fail(s"no reply-to-yield code; got ${deps.map(_.deprecationCode)}"))
      // prettify rewrites `reply` to `yield` with no decision to make, so a migration UI may
      // promise this one will be fixed for the user.
      m.autoFixable mustBe true
    }
  }

  "a deprecated `prompt` statement" should {
    "carry the prompt-statement code" in {
      val deps = deprecationsIn(modelWith("""prompt "do a thing" """))
      deps.flatMap(_.deprecationCode) must contain(DeprecationCode.PromptStatement)
    }
  }

  "the code registry" should {
    "list every code it defines" in {
      DeprecationCode.all must contain(DeprecationCode.ReplyToYield)
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
