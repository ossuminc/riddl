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

/** Tests for the explicit `initial` marker on States and Handlers (#14): the default
  * (first-declared is initial), explicit marking, and the "at most one initial" validation errors.
  */
class InitialMarkerTest extends AbstractValidatingTest {

  private def entity(src: String, td: TestData)(
    check: (Entity, Messages.Messages) => org.scalatest.Assertion
  ) =
    parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
      case (domain, _, messages) => check(Finder(domain).recursiveFindByType[Entity].head, messages)
    }

  "Initial marker" should {

    "default the first-declared state and first-declared handler to initial" in { (td: TestData) =>
      entity(
        """domain d is { context c is { entity e is {
          |  type Data is { x: Integer }
          |  state First of record d.c.e.Data is {
          |    handler H1 is { on other is { do "a" } }
          |    handler H2 is { on other is { do "b" } }
          |  }
          |  state Second of record d.c.e.Data is {
          |    handler H3 is { on other is { do "c" } }
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
          |  state First of record d.c.e.Data is { handler H is { on other is { do "a" } } }
          |  initial state Second of record d.c.e.Data is { handler H2 is { on other is { do "b" } } }
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
          |  initial state First of record d.c.e.Data is { handler H is { on other is { do "a" } } }
          |  initial state Second of record d.c.e.Data is { handler H2 is { on other is { do "b" } } }
          |}}}""".stripMargin,
        td
      ) { (_, messages) =>
        messages.justErrors.exists(_.message.contains("states 'initial'")) mustBe true
      }
    }

    "default the first entity-scope handler to initial when the entity has exactly one state" in {
      (td: TestData) =>
        entity(
          """domain d is { context c is { entity e is {
            |  type Data is { x: Integer }
            |  state Only of record d.c.e.Data is { handler S is { on other is { do "s" } } }
            |  handler E1 is { on other is { do "a" } }
            |  handler E2 is { on other is { do "b" } }
            |}}}""".stripMargin,
          td
        ) { (e, _) =>
          e.handlers.find(_.id.value == "E1").get.isInitial mustBe true
          e.handlers.find(_.id.value == "E2").get.isInitial mustBe false
        }
    }

    "NOT default any entity-scope handler when the entity has multiple states" in {
      (td: TestData) =>
        // With >1 state the entity-scope handlers are common parts merged into each state's
        // handler set, so none of them is the entity's initial handler.
        entity(
          """domain d is { context c is { entity e is {
          |  type Data is { x: Integer }
          |  state First of record d.c.e.Data is { handler S1 is { on other is { do "a" } } }
          |  state Second of record d.c.e.Data is { handler S2 is { on other is { do "b" } } }
          |  handler E1 is { on other is { do "c" } }
          |  handler E2 is { on other is { do "d" } }
          |}}}""".stripMargin,
          td
        ) { (e, _) =>
          e.handlers.forall(_.isInitial == false) mustBe true
        }
    }

    // Reported by ossum.tech 2026-08-04. The duplicate check was guarded on
    // `entity.states.sizeIs <= 1`, so ADDING a second state made the error disappear. The guard
    // came from the DEFAULTING rule above, where single-state is right, but defaulting and
    // duplicate-detection are different rules and only the first is about state count. Under
    // §17.2 an ambiguous entity-scope `initial` is worse with several states, not better: it picks
    // the live behavior for every state that does not define its own.
    "error on duplicate entity-scope initial handlers even with MULTIPLE states" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain d is { context c is { entity e is {
          |  type Data is { x: Integer }
          |  initial handler E1 is { on other is { do "c" } }
          |  initial handler E2 is { on other is { do "d" } }
          |  initial state First of record d.c.e.Data
          |  state Second of record d.c.e.Data
          |}}}""".stripMargin,
          td
        )
        parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
          msgs.justErrors.exists(_.message.contains("marks 2 handlers 'initial'")) mustBe true
        }
    }

    "error when more than one handler in a state is marked initial" in { (td: TestData) =>
      entity(
        """domain d is { context c is { entity e is {
          |  type Data is { x: Integer }
          |  state Only of record d.c.e.Data is {
          |    initial handler H1 is { on other is { do "a" } }
          |    initial handler H2 is { on other is { do "b" } }
          |  }
          |}}}""".stripMargin,
        td
      ) { (_, messages) =>
        messages.justErrors.exists(_.message.contains("handlers 'initial'")) mustBe true
      }
    }
  }
}
