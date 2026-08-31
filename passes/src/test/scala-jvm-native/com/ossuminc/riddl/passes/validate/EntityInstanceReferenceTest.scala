/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** One concept, five spellings, now ONE AST node.
  *
  * Reid's ruling, 2026-08-31: `reference to entity X`, `Id(entity X)` and `Id(X)` are all types
  * that reference an INSTANCE of entity X, and the value of such a field is what you address a
  * message to. RIDDL spelled that across two unrelated nodes which had made opposite decisions —
  * `UniqueId` kept the disambiguating keyword, `EntityReferenceTypeExpression` discarded it — so a
  * generator had no single way to ask "is this a reference to an instance of E?".
  *
  * The fix is that the parser produces `UniqueId` for all five spellings. That matters beyond
  * tidiness: every addressing question riddlc asks (`isAddressFieldFor`, `isIdForEntity`,
  * `checkTerminate`) is keyed on `UniqueId`, so a `reference to entity E` field was NOT usable as
  * a `tell` address despite denoting exactly that. Unifying the node fixes all of them at once.
  *
  * `Id` is NOT deprecated — it is the permanent canonical form. Only the `reference` spelling is,
  * and it keeps parsing forever under the 3.0 compatibility rule.
  */
class EntityInstanceReferenceTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def spellings(t: String): String =
    s"""domain D is {
       |  context C is {
       |    record R is { n: Integer }
       |    entity E is {
       |      state S of record D.C.R is {
       |        handler H is { on command D.C.Go is { do "x" } }
       |      }
       |    }
       |    command Go is { target: $t }
       |  }
       |}""".stripMargin

  private val allFive =
    Seq("Id(D.C.E)", "Id(entity D.C.E)", "reference to entity D.C.E", "reference to D.C.E",
      "reference D.C.E")

  "all five spellings" should {

    "parse and validate" in { (td: TestData) =>
      allFive.foreach { t =>
        withClue(s"spelling '$t' produced errors\n") {
          diagnostics(spellings(t), td.name).justErrors mustBe empty
        }
      }
    }

    /** The `reference` forms are deprecated; `Id` is NOT, and asserting the negative is what
      * stops a later change from deprecating the canonical form by accident.
      *
      * Uses `parseInputWithMessages`, NOT `parseAndValidate`, because a deprecation is emitted at
      * PARSE time and travels a different channel — `parseAndValidate` discards it, so an
      * assertion through that helper reports zero and blames the code. CLAUDE.md names this trap;
      * this suite hit it anyway on the first run.
      */
    "deprecate the `reference` forms and NOT the `Id` forms" in { (td: TestData) =>
      def deprecations(t: String): Int =
        TopLevelParser.parseInputWithMessages(RiddlParserInput(spellings(t), td)) match
          case Left(messages) => fail(messages.format)
          case Right((_, parseMessages)) =>
            parseMessages.count(_.message.contains("'reference to' is deprecated"))
      deprecations("Id(D.C.E)") mustBe 0
      deprecations("Id(entity D.C.E)") mustBe 0
      deprecations("reference to entity D.C.E") mustBe 1
      deprecations("reference to D.C.E") mustBe 1
      deprecations("reference D.C.E") mustBe 1
    }
  }

  /** The payoff. `isAddressFieldFor` matches `UniqueId`, so before this change a message field
    * typed `reference to entity E` did not count as an address and the entity was reported as
    * having no way to say WHICH instance a message was for.
    */
  "a `reference to` field" should {
    "satisfy instance addressing exactly as Id() does" in { (td: TestData) =>
      def unaddressed(t: String): Seq[String] =
        diagnostics(spellings(t), td.name)
          .filter(m => m.message.contains("carries no field typed") && m.message.contains("'E'"))
          .map(_.message)
      allFive.foreach { t =>
        withClue(s"spelling '$t' left Entity 'E' unaddressable\n") {
          unaddressed(t) mustBe empty
        }
      }
    }
  }
}
