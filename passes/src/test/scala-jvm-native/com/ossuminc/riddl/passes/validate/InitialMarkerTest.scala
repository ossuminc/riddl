/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, Messages}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** Tests for the explicit `initial` marker on States and Handlers (#14): the default (first-declared
  * is initial), explicit marking, and the "at most one initial" validation errors.
  */
class InitialMarkerTest extends AbstractValidatingTest {

  private def entity(src: String, td: TestData)(check: (Entity, Messages.Messages) => org.scalatest.Assertion) =
    parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
      case (domain, _, messages) => check(Finder(domain).recursiveFindByType[Entity].head, messages)
    }

  "Initial marker" should {

    "default the first-declared state and first-declared handler to initial" in { (td: TestData) =>
      entity(
        """domain d is { context c is { entity e is {
          |  type Data is { x: Integer }
          |  state First of type d.c.e.Data is {
          |    handler H1 is { on other is { prompt "a" } }
          |    handler H2 is { on other is { prompt "b" } }
          |  }
          |  state Second of type d.c.e.Data is {
          |    handler H3 is { on other is { prompt "c" } }
          |  }
          |}}}""".stripMargin,
        td
      ) { (e, _) =>
        e.states.find(_.id.value == "First").get.isInitial mustBe true
        e.states.find(_.id.value == "Second").get.isInitial mustBe false
        val first = e.states.find(_.id.value == "First").get
        first.handlers.find(_.id.value == "H1").get.isInitial mustBe true
        first.handlers.find(_.id.value == "H2").get.isInitial mustBe false
        // first handler of the OTHER state is also its initial
        e.states.find(_.id.value == "Second").get.handlers.head.isInitial mustBe true
      }
    }

    "honor an explicit `initial` on a non-first state (no defaulting)" in { (td: TestData) =>
      entity(
        """domain d is { context c is { entity e is {
          |  type Data is { x: Integer }
          |  state First of type d.c.e.Data is { handler H is { on other is { prompt "a" } } }
          |  initial state Second of type d.c.e.Data is { handler H2 is { on other is { prompt "b" } } }
          |}}}""".stripMargin,
        td
      ) { (e, _) =>
        e.states.find(_.id.value == "First").get.isInitial mustBe false
        e.states.find(_.id.value == "Second").get.isInitial mustBe true
      }
    }

    "error when more than one state is marked initial" in { (td: TestData) =>
      entity(
        """domain d is { context c is { entity e is {
          |  type Data is { x: Integer }
          |  initial state First of type d.c.e.Data is { handler H is { on other is { prompt "a" } } }
          |  initial state Second of type d.c.e.Data is { handler H2 is { on other is { prompt "b" } } }
          |}}}""".stripMargin,
        td
      ) { (_, messages) =>
        messages.justErrors.exists(_.message.contains("states 'initial'")) mustBe true
      }
    }

    "error when more than one handler in a state is marked initial" in { (td: TestData) =>
      entity(
        """domain d is { context c is { entity e is {
          |  type Data is { x: Integer }
          |  state Only of type d.c.e.Data is {
          |    initial handler H1 is { on other is { prompt "a" } }
          |    initial handler H2 is { on other is { prompt "b" } }
          |  }
          |}}}""".stripMargin,
        td
      ) { (_, messages) =>
        messages.justErrors.exists(_.message.contains("handlers 'initial'")) mustBe true
      }
    }
  }
}
