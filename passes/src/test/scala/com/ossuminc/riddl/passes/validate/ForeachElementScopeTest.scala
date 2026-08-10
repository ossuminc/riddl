/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** `foreach x in <collection>` binds `x` over the loop BODY.
  *
  * Reported by ossum.tech 2026-08-09: the binding existed only for the loop header's own check, so
  * every body that dereferenced the element was an Error and `foreach` admitted only bodies that
  * IGNORED what they iterated -- which is the one thing iteration is not for.
  *
  * Two properties are easy to get half-right and are pinned separately below:
  *
  *   - the element's TYPE is carried, not just its name. Binding the name alone would let
  *     `line.nosuch` resolve as readily as `line.sku`, which is the last-component-matching defect
  *     A54 removed from `ValueRef` resolution generally. Reintroducing it here would be no better
  *     for being local.
  *   - the binding is scoped to the BODY. It leaves scope at the closing brace, so a reference
  *     after the loop stays an Error.
  */
class ForeachElementScopeTest extends AbstractValidatingTest {

  private def model(body: String): String =
    s"""domain D is {
       |  author A is { name is "A" email is "a@b.c" }
       |  context C is {
       |    record Line is { sku is String }
       |    record Order is { entries is many Line, ref is String }
       |    record St is { lines is many Line, byId is mapping from Integer to Line, note is String }
       |    command Cmd is { order is Order, note is String }
       |    event Shipped is { sku is String }
       |    outlet Out is event Shipped
       |    entity E is {
       |      state S of record St is {
       |        handler H is {
       |          on command Cmd { $body }
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def errorsFor(body: String, td: TestData): Messages =
    var captured: Messages = List.empty
    pc.withOptions(CommonOptions(showStyleWarnings = false, showWarnings = false)) { _ =>
      parseAndValidateDomain(RiddlParserInput(model(body), td), shouldFailOnErrors = false) {
        case (_, _, msgs) =>
          captured = msgs
          succeed
      }
    }
    // Half these cases assert the ABSENCE of an error, which a fixture that never parsed satisfies
    // for free. Refuse to report on one.
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse, so any absence proves nothing:\n${m.format}")
      case None    => captured.filter(_.isError)
  end errorsFor

  private def clue(msgs: Messages): String = msgs.map(_.message).mkString("\n")

  "Foreach element scope" should {

    // The task's acceptance criterion is explicitly that the fix not be narrow to one statement
    // kind, so the element is dereferenced from three different ones.

    "resolve the element in a send" in { (td: TestData) =>
      val errors =
        errorsFor("foreach line in field lines { send event Shipped(sku = line.sku) to outlet Out }", td)
      errors mustBe empty
    }

    "resolve the element in a set" in { (td: TestData) =>
      val errors = errorsFor("foreach line in field lines { set field St.note to line.sku }", td)
      errors mustBe empty
    }

    "resolve the element inside a nested when" in { (td: TestData) =>
      val errors = errorsFor(
        "foreach line in field lines { when line.sku then set field St.note to line.sku end }",
        td
      )
      errors mustBe empty
    }

    "resolve the element referenced bare" in { (td: TestData) =>
      val errors = errorsFor("foreach line in field lines { set field St.note to line }", td)
      errors mustBe empty
    }

    "reject a member the element's type does not have" in { (td: TestData) =>
      val errors = errorsFor("foreach line in field lines { set field St.note to line.nosuch }", td)
      withClue(clue(errors)) {
        errors.exists(_.message.contains("line.nosuch")) mustBe true
      }
    }

    // A dotted collection path. This was rejected until 2026-08-09, not by decision but by an
    // allow-list that tested the resolved field for identity against the DIRECT fields of the state
    // record, handled message and function input. `lines` belongs to `Order`, so no path through a
    // nested record could satisfy it. Cardinality is the whole question: the path resolves and
    // lands on a collection, so it iterates.

    "iterate a dotted path into the handled message" in { (td: TestData) =>
      val errors = errorsFor(
        "foreach line in field order.entries { send event Shipped(sku = line.sku) to outlet Out }",
        td
      )
      errors mustBe empty
    }

    "reject a dotted path landing on a scalar" in { (td: TestData) =>
      val errors = errorsFor("""foreach x in field order.ref { do "x" }""", td)
      withClue(clue(errors)) {
        errors.exists(_.message.contains("is not a collection type")) mustBe true
      }
    }

    // A mapping has no single element type, so it is DESTRUCTURED: `foreach k, v in m` binds the
    // key to the mapping's `from` and the value to its `to`. Before that form existed a mapping
    // bound one name to `Anything`, and `e.whatever` passed unchecked -- which is the hole these
    // four cases exist to keep closed.

    "type the key and the value of a destructured mapping" in { (td: TestData) =>
      val errors =
        errorsFor("foreach k, v in field byId { set field St.note to v.sku }", td)
      errors mustBe empty
    }

    "reject a member the mapping's value type does not have" in { (td: TestData) =>
      val errors =
        errorsFor("foreach k, v in field byId { set field St.note to v.nosuch }", td)
      withClue(clue(errors)) {
        errors.exists(_.message.contains("v.nosuch")) mustBe true
      }
    }

    "require two names over a mapping" in { (td: TestData) =>
      val errors = errorsFor("""foreach e in field byId { do "x" }""", td)
      withClue(clue(errors)) {
        errors.exists(_.message.contains("needs two names")) mustBe true
      }
    }

    "reject a second name over a non-mapping" in { (td: TestData) =>
      val errors = errorsFor("""foreach a, b in field lines { do "x" }""", td)
      withClue(clue(errors)) {
        errors.exists(_.message.contains("second name only over a mapping")) mustBe true
      }
    }

    "not leak the element past the loop body" in { (td: TestData) =>
      val errors =
        errorsFor("""foreach line in field lines { do "nothing" } set field St.note to line.sku""", td)
      withClue(clue(errors)) {
        errors.exists(_.message.contains("line.sku")) mustBe true
      }
    }
  }
}
