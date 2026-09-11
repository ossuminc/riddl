/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A handler may declare AT MOST ONE of each SPECIAL clause kind (Reid, 2026-09-11).
  *
  * Quiescence had this rule since 2026-09-07 and the other five did not, so `on other` twice --
  * two catch-alls with no rule for which one runs -- validated clean. `handler-clause-shadowed`
  * cannot see these: it collects `OnMessageLikeClause` only, because a special clause names no
  * message to key on.
  */
class DuplicateSpecialClauseTest extends AbstractValidatingTest {

  private def entityWith(clauses: String): String =
    s"""domain d is {
       |  context c is {
       |    command DoIt is { f: Integer }
       |    entity e is {
       |      handler h is {
       |$clauses
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private val ruleText = "declares more than one"

  "A duplicate special on-clause" should {

    "be an Error for two 'on other' clauses" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        entityWith("        on other { ??? }\n        on other { ??? }"),
        td
      )
      parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        val dups = msgs.filter(m => m.kind == Messages.Error && m.message.contains(ruleText))
        dups.size mustBe 1
        dups.head.message must include("'on other'")
        dups.head.message must include("one residual-message policy")
      }
    }

    "be an Error for two 'on init' clauses" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        entityWith("        on init { ??? }\n        on init { ??? }"),
        td
      )
      parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        val dups = msgs.filter(m => m.kind == Messages.Error && m.message.contains(ruleText))
        dups.size mustBe 1
        dups.head.message must include("'on init'")
      }
    }

    "be an Error for two 'on term' clauses" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        entityWith("        on term { ??? }\n        on term { ??? }"),
        td
      )
      parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        val dups = msgs.filter(m => m.kind == Messages.Error && m.message.contains(ruleText))
        dups.size mustBe 1
        dups.head.message must include("'on term'")
      }
    }

    "report EVERY duplicate beyond the first, not just one" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        entityWith("        on other { ??? }\n        on other { ??? }\n        on other { ??? }"),
        td
      )
      parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        msgs.count(m => m.kind == Messages.Error && m.message.contains(ruleText)) mustBe 2
      }
    }

    // The negative control. Without it, a check that fired on EVERYTHING would still pass every
    // case above -- the vacuous-green shape this repo keeps recording.
    "be silent when each special clause appears exactly once" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        entityWith(
          "        on init { ??? }\n" +
            "        on command DoIt { ??? }\n" +
            "        on term { ??? }\n" +
            "        on other { ??? }"
        ),
        td
      )
      parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        msgs.filter(_.message.contains(ruleText)) mustBe empty
      }
    }

    // Quiescence keeps its OWN rule id and message: the rule is the same but the reason is not
    // (a second idle clock, not a second residual-message policy), so it must not be folded in.
    "leave 'on quiescence' to handler-quiescence-duplicate" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        entityWith(
          "        on quiescence \"PT5M\" { ??? }\n        on quiescence \"PT9M\" { ??? }"
        ),
        td
      )
      parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        val idle = msgs.filter(_.message.contains("one idle clock"))
        idle.size mustBe 1
        msgs.filter(_.message.contains("residual-message policy")) mustBe empty
      }
    }
  }
}
