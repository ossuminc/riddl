/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Domain
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import com.ossuminc.riddl.utils.CommonOptions
import org.scalatest.TestData

/** Unit Tests For Repository */
class RepositoryTest extends AbstractValidatingTest {

  "RepositoryTest" should {
    "handle a basic definition" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is {
          |  context bar is {
          |    source itin is { outlet hereyougo is bar.fubar.Reply }
          |    repository fubar is {
          |      query GetOne is { how: String }
          |      result Reply is { that: String }
          |      command AddThis is { what: String }
          |      handler Only is {
          |        on command AddThis {
          |          do "add 'what' to the list"
          |        }
          |        on query GetOne {
          |          send result fubar.Reply(that = "the reply") to outlet hereyougo
          |        }
          |      }
          |     }
          |  }
          |}
          |""".stripMargin,
        td
      )
      val options = CommonOptions.noWarnings.copy(showMissingWarnings = false)
      pc.withOptions(options) { _ =>
        parseAndValidateDomain(input) {
          case (domain: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
            domain mustNot be(empty)
            domain.contexts.headOption match {
              case Some(context) =>
                context.repositories mustNot be(empty)
                // info(msgs.format)
                val errors = msgs.justErrors
                errors.size mustBe 0
                msgs.isOnlyWarnings
                succeed
              case _ =>
                fail("Did not parse a context!")
            }
        }
      }
    }

    "allow a repository at domain scope that synthesizes across contexts" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain d is {
          |  context a is { event AEvent is { x: String } }
          |  context b is { event BEvent is { y: String } }
          |  repository synth is {
          |    handler h is {
          |      on event a.AEvent { do "record from a" }
          |      on event b.BEvent { do "record from b" }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (domain: Domain, _, msgs) =>
        domain.repositories mustNot be(empty) // repository is at domain scope
        // Reaches two contexts, so domain scope is justified: no demote error.
        msgs.justErrors.exists(
          _.message.contains("domain scope but its handlers only reach")
        ) mustBe false
      }
    }

    "warn that a context repository crossing context bounds belongs at domain scope" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain d is {
            |  context a is { event AEvent is { x: String } }
            |  context b is {
            |    event BEvent is { y: String }
            |    repository r is {
            |      handler h is {
            |        on event a.AEvent { do "from a" }
            |        on event BEvent { do "from b" }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin,
          td
        )
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, msgs) =>
          val cw = msgs.filter(_.kind == Messages.CompletenessWarning)
          cw.exists(_.message.contains("cross context boundaries")) mustBe true
        }
    }

    "error when a domain-scoped repository reaches only one context" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain d is {
          |  context a is { event AEvent is { x: String } }
          |  repository r is {
          |    handler h is {
          |      on event a.AEvent { do "only from a" }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, msgs) =>
        msgs.justErrors.exists(
          _.message.contains("domain scope but its handlers only reach")
        ) mustBe true
      }
    }
  }
}
