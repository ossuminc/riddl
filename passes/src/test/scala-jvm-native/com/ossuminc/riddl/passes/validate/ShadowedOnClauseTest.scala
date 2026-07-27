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

/** A21: within a single handler, two `on <message>` clauses that handle the same message make the
  * later clause unreachable. The later clause should draw a StyleWarning.
  */
class ShadowedOnClauseTest extends AbstractValidatingTest {

  "Shadowed on-clause (A21)" should {

    "emit a StyleWarning on the later of two on-clauses handling the same message" in {
      (td: TestData) =>
        val input =
          """domain d is {
            |  context c is {
            |    command DoIt is { f: Integer }
            |    entity e is {
            |      handler h is {
            |        on command DoIt { ??? }
            |        on command DoIt { ??? }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
        val rpi = RiddlParserInput(input, td)
        parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
          val shadows = msgs.filter(m =>
            m.kind == Messages.StyleWarning && m.message.contains("shadows an earlier clause")
          )
          shadows.size mustBe 1
          shadows.head.message must include("unreachable")
        }
    }

    "not warn when a handler has two on-clauses for distinct messages" in { (td: TestData) =>
      val input =
        """domain d is {
          |  context c is {
          |    command DoIt is { f: Integer }
          |    command DoThat is { g: Integer }
          |    entity e is {
          |      handler h is {
          |        on command DoIt { ??? }
          |        on command DoThat { ??? }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val rpi = RiddlParserInput(input, td)
      parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        msgs.filter(_.message.contains("shadows an earlier clause")) mustBe empty
      }
    }
  }
}
