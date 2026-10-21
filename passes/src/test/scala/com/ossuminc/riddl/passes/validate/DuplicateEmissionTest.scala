/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** riddl-generator's "incognito duplicate" (Reid, 2026-10-16): one handler clause putting the
  * same value onto the same outlet twice. Each transmission gets its own message id, so the copies
  * are indistinguishable downstream. A non-blocking warning, plus its companion: a `tell` whose
  * target is reachable from more than one of the sender's outlets names no single channel.
  *
  * The fixture is reactive-bbq's shape reduced: a `split` with two outlets, one wired to an entity
  * and one to a sink.
  */
class DuplicateEmissionTest extends AbstractValidatingTest {

  private def model(clause: String, extraWiring: String = ""): String =
    s"""domain D is {
       |  context C is {
       |    event Placed is { id: String } with { briefly "p" }
       |    entity Order is {
       |      inlet OrderIn is event C.Placed with { briefly "i" }
       |      handler OH is { on event C.Placed is { do "record it" } } with { briefly "h" }
       |    } with { briefly "o" }
       |    sink Board is {
       |      inlet BoardIn is event C.Placed with { briefly "i" }
       |      handler BH is { on event C.Placed is { do "show it" } } with { briefly "h" }
       |    } with { briefly "b" }
       |    streamlet Fanout as split is {
       |      inlet FanIn is event C.Placed with { briefly "i" }
       |      outlet ToOrder is event C.Placed with { briefly "o" }
       |      outlet ToBoard is event C.Placed with { briefly "o" }
       |      handler FH is {
       |        on placed: event C.Placed is {
       |$clause
       |        }
       |      } with { briefly "h" }
       |    } with { briefly "f" }
       |    connector A is from outlet C.Fanout.ToOrder to inlet C.Order.OrderIn
       |    connector B is from outlet C.Fanout.ToBoard to inlet C.Board.BoardIn
       |$extraWiring
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse:\n${m.format}")
      case None    => captured

  private def ruled(src: String, origin: String, rule: RuleId): Messages =
    diagnostics(src, origin).filter(_.ruleId.contains(rule))

  private def dups(src: String, origin: String): Messages =
    ruled(src, origin, RuleId.DuplicateSendSameOutlet)

  "the same value onto the same outlet twice" should {
    "warn for two sends to one outlet" in { (td: TestData) =>
      val found = dups(
        model("""          send placed to outlet C.Fanout.ToOrder
                |          send placed to outlet C.Fanout.ToOrder""".stripMargin),
        td.name
      )
      withClue(found.map(_.format).mkString("\n")) {
        found must have size 1
        found.head.message must include("ToOrder")
        found.head.message must include("different message ids")
      }
    }

    "warn for a send plus a tell whose only route is that outlet (reactive-bbq's shape)" in {
      (td: TestData) =>
        val found = dups(
          model("""          send placed to outlet C.Fanout.ToBoard
                  |          send placed to outlet C.Fanout.ToOrder
                  |          tell placed to entity C.Order""".stripMargin),
          td.name
        )
        withClue(found.map(_.format).mkString("\n")) {
          found must have size 1
          found.head.message must include("'tell'")
          found.head.message must include("ToOrder")
        }
    }

    "warn for two tells that resolve to the same outlet" in { (td: TestData) =>
      dups(
        model("""          tell placed to entity C.Order
                |          tell placed to entity C.Order""".stripMargin),
        td.name
      ) must have size 1
    }

    "be non-blocking: a generator delivers both copies" in { (td: TestData) =>
      val found = dups(
        model("""          send placed to outlet C.Fanout.ToOrder
                |          send placed to outlet C.Fanout.ToOrder""".stripMargin),
        td.name
      )
      found must not be empty
      found.forall(_.isGenerable) mustBe true
    }
  }

  "emissions that do not duplicate" should {
    "draw nothing for two DIFFERENT values to one outlet" in { (td: TestData) =>
      dups(
        model("""          send placed to outlet C.Fanout.ToOrder
                |          send event C.Placed(id = "other") to outlet C.Fanout.ToOrder""".stripMargin),
        td.name
      ) mustBe empty
    }

    "draw nothing for a send and a tell on DIFFERENT outlets" in { (td: TestData) =>
      dups(
        model("""          send placed to outlet C.Fanout.ToBoard
                |          tell placed to entity C.Order""".stripMargin),
        td.name
      ) mustBe empty
    }

    "draw nothing across the two branches of a `when`, which never both run" in {
      (td: TestData) =>
        dups(
          model("""          when placed.id == "x" then
                  |            send placed to outlet C.Fanout.ToOrder
                  |          else
                  |            send placed to outlet C.Fanout.ToOrder
                  |          end""".stripMargin),
          td.name
        ) mustBe empty
    }
  }

  /** Board becomes a flow wired back to the order. Whether that second path is a ROUTE for the
    * tell depends on whether Board passes the event on, which `boardClause` decides.
    */
  private def withBackWire(clause: String, boardClause: String): String =
    model(clause, """    connector Back is from outlet C.Board.BoardOut to inlet C.Order.OrderIn""")
      .replace(
        """      inlet BoardIn is event C.Placed with { briefly "i" }""",
        """      inlet BoardIn is event C.Placed with { briefly "i" }
          |      outlet BoardOut is event C.Placed with { briefly "o" }""".stripMargin
      )
      .replace("""on event C.Placed is { do "show it" }""", boardClause)
      .replace("sink Board is", "flow Board is")

  "a tell whose target is reachable from two of the sender's outlets" should {
    "be reported as ambiguous when both paths CARRY the message, and take no part in duplicates" in {
      (td: TestData) =>
        val src = withBackWire(
          """          send placed to outlet C.Fanout.ToOrder
            |          tell placed to entity C.Order""".stripMargin,
          """on p: event C.Placed is { send p to outlet C.Board.BoardOut }"""
        )
        val ambiguous = ruled(src, td.name, RuleId.TellRouteAmbiguous)
        withClue(diagnostics(src, td.name).map(_.format).mkString("\n")) {
          ambiguous must have size 1
          ambiguous.head.message must include("'ToOrder'")
          ambiguous.head.message must include("'ToBoard'")
          dups(src, td.name) mustBe empty
        }
    }

    "not be ambiguous when the second path only EXISTS: the processor on it consumes the message" in {
      (td: TestData) =>
        val src = withBackWire(
          """          send placed to outlet C.Fanout.ToOrder
            |          tell placed to entity C.Order""".stripMargin,
          """on event C.Placed is { do "show it" }"""
        )
        withClue(diagnostics(src, td.name).map(_.format).mkString("\n")) {
          ruled(src, td.name, RuleId.TellRouteAmbiguous) mustBe empty
          dups(src, td.name) must have size 1
        }
    }
  }

  "a tell routed through a processor that passes the message on (reactive-bbq's two hops)" should {
    "resolve to the outlet that starts that chain, and count against a send on it" in {
      (td: TestData) =>
        val src = model(
          """          send placed to outlet C.Fanout.ToOrder
            |          tell placed to entity C.Order""".stripMargin
        ).replace(
          "    connector A is from outlet C.Fanout.ToOrder to inlet C.Order.OrderIn",
          """    streamlet Relay as flow is {
            |      inlet RelayIn is event C.Placed with { briefly "i" }
            |      outlet RelayOut is event C.Placed with { briefly "o" }
            |      handler RH is {
            |        on p: event C.Placed is { send p to outlet C.Relay.RelayOut }
            |      } with { briefly "h" }
            |    } with { briefly "r" }
            |    connector A is from outlet C.Fanout.ToOrder to inlet C.Relay.RelayIn
            |    connector A2 is from outlet C.Relay.RelayOut to inlet C.Order.OrderIn""".stripMargin
        )
        withClue(diagnostics(src, td.name).map(_.format).mkString("\n")) {
          val found = dups(src, td.name)
          found must have size 1
          found.head.message must include("ToOrder")
        }
    }
  }
}
