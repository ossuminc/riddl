/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.CompletenessWarning
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{CommonOptions, pc, ec}

import org.scalatest.TestData

class CompletenessTest extends AbstractValidatingTest {

  private def completenessWarnings(msgs: Messages.Messages): Messages.Messages =
    msgs.filter(_.isCompleteness)

  "CompletenessWarning" should {
    "warn when entity state has no on-init clause" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Cmd is command { data: String }
          |    type Evt is event { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on command D.C.Cmd {
          |          send event D.C.Evt to outlet D.C.Events.out
          |        }
          |        on query D.C.GetData {
          |          prompt "return data"
          |        }
          |      }
          |    }
          |    type GetData is query { id: String }
          |    source Events is { outlet out is type Evt }
          |    sink Incoming is { inlet in is type Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains("no 'on init' clause")) mustBe true
      }
    }

    "warn when entity state has on-init but no set statement" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Cmd is command { data: String }
          |    type Evt is event { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on init {
          |          prompt "should set state here"
          |        }
          |        on command D.C.Cmd {
          |          send event D.C.Evt to outlet D.C.Events.out
          |        }
          |        on query D.C.GetData {
          |          prompt "return data"
          |        }
          |      }
          |    }
          |    type GetData is query { id: String }
          |    source Events is { outlet out is type Evt }
          |    sink Incoming is { inlet in is type Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains("no 'set' statement")) mustBe true
      }
    }

    "not warn when entity state has proper on-init with set" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Cmd is command { data: String }
          |    type Evt is event { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on init {
          |          set field E.Fields.data to "default"
          |        }
          |        on command D.C.Cmd {
          |          send event D.C.Evt to outlet D.C.Events.out
          |        }
          |        on query D.C.GetData {
          |          prompt "return data"
          |        }
          |      }
          |    }
          |    type GetData is query { id: String }
          |    source Events is { outlet out is type Evt }
          |    sink Incoming is { inlet in is type Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains("on init")) mustBe false
      }
    }

    "warn when command handler does not send event" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Cmd is command { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd {
          |          set field E.Fields.data to "updated"
          |        }
          |        on query D.C.GetData {
          |          prompt "return data"
          |        }
          |      }
          |    }
          |    type GetData is query { id: String }
          |    sink Incoming is { inlet in is type Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains(
            "should result in sending an event"
          )) mustBe true
      }
    }

    "warn when query handler does not send result" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Qry is query { id: String }
          |    type Evt is event { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on query D.C.Qry {
          |          set field E.Fields.data to "looked up"
          |        }
          |      }
          |    }
          |    source Events is { outlet out is type Evt }
          |    sink Incoming is { inlet in is type Qry }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains(
            "should result in a reply or sending a result"
          )) mustBe true
      }
    }

    "warn when projector does not reference a repository" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Evt is event { data: String }
          |    projector P is {
          |      type ProjRecord is record { data: String }
          |      handler H is {
          |        on event D.C.Evt {
          |          prompt "handle event"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains(
            "does not reference any repository to persist its projection"
          )) mustBe true
      }
    }

    "warn when projector does not tell to a repository" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Evt is event { data: String }
          |    repository Repo is {
          |      schema S is flat of records as type D.C.RecType
          |      handler RH is {
          |        on command D.C.Store {
          |          prompt "store it"
          |        }
          |      }
          |    }
          |    type Store is command { data: String }
          |    type RecType is record { data: String }
          |    projector P is {
          |      type ProjRecord is record { data: String }
          |      updates repository Repo
          |      handler H is {
          |        on event D.C.Evt {
          |          prompt "handle event but never tell repo"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains(
            "does not persist its projection"
          )) mustBe true
      }
    }

    "warn when handler has no executable statements (empty)" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Evt is event { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler Empty is {
          |        on event D.C.Evt { ??? }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains(
            "has no executable statements"
          )) mustBe true
      }
    }

    "warn when handler has only prompt statements" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Evt is event { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler PromptOnly is {
          |        on event D.C.Evt {
          |          prompt "do something"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains(
            "contains only prompt statements"
          )) mustBe true
      }
    }

    "warn when entity has no query handler" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Cmd is command { data: String }
          |    type Evt is event { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd {
          |          send event D.C.Evt to outlet D.C.Events.out
          |        }
          |      }
          |    }
          |    source Events is { outlet out is type Evt }
          |    sink Incoming is { inlet in is type Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains(
            "no 'on query' clause"
          )) mustBe true
      }
    }

    "warn when entity has no outlet streamlet for events" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Cmd is command { data: String }
          |    type GetData is query { id: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd {
          |          prompt "handle command"
          |        }
          |        on query D.C.GetData {
          |          prompt "return data"
          |        }
          |      }
          |    }
          |    sink Incoming is { inlet in is type Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains(
            "no outlet streamlet to publish events on"
          )) mustBe true
      }
    }

    "warn when context with entities has no Sink" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Cmd is command { data: String }
          |    type Evt is event { data: String }
          |    type GetData is query { id: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd {
          |          send event D.C.Evt to outlet D.C.Events.out
          |        }
          |        on query D.C.GetData {
          |          prompt "return data"
          |        }
          |      }
          |    }
          |    source Events is { outlet out is type Evt }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains(
            "no Sink streamlet to receive and dispatch"
          )) mustBe true
      }
    }

    "warn when unconnected inlet or outlet exists" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type T is String
          |    source Src is { outlet out is type T }
          |    sink Snk is { inlet in is type T }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains("is not connected")) mustBe true
      }
    }

    "warn when entity has no Id type" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Cmd is command { data: String }
          |    type Evt is event { data: String }
          |    type GetData is query { id: String }
          |    type DataResult is result { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd {
          |          send event D.C.Evt to outlet D.C.Events.out
          |        }
          |        on query D.C.GetData {
          |          send result D.C.DataResult to outlet D.C.Events.out
          |        }
          |      }
          |    }
          |    source Events is { outlet out is type Evt }
          |    sink Incoming is { inlet in is type Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains("does not define an Id type")) mustBe true
      }
    }

    "warn when event-sourced entity command handler does not emit event" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type EId is Id(C.E)
          |    type Cmd is command { data: String }
          |    type Evt is event { data: String }
          |    type GetData is query { id: String }
          |    type DataResult is result { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd {
          |          set field E.Fields.data to "updated"
          |        }
          |        on query D.C.GetData {
          |          send result D.C.DataResult to outlet D.C.Events.out
          |        }
          |      }
          |    } with { option event-sourced }
          |    source Events is { outlet out is type Evt }
          |    sink Incoming is { inlet in is type Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains("event-sourced")) mustBe true
      }
    }

    "warn when saga step has no tell command" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Evt is event { data: String }
          |    saga S is {
          |      requires { data: String }
          |      returns { result: String }
          |      step One is {
          |        prompt "do something"
          |      } reverted by {
          |        prompt "undo something"
          |      }
          |      step Two is {
          |        prompt "do more"
          |      } reverted by {
          |        prompt "undo more"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains("no 'tell command'")) mustBe true
      }
    }

    "warn when on-other clause is empty" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Evt is event { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on event D.C.Evt {
          |          set field E.Fields.data to "updated"
          |        }
          |        on other is { ??? }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains("on other")) mustBe true
      }
    }

    "warn when flow streamlet handler does not send to outlet" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type T is String
          |    flow Transform is {
          |      inlet in is type T
          |      outlet out is type T
          |      handler H is {
          |        on event D.C.SomeEvent {
          |          prompt "transform but forget to send"
          |        }
          |      }
          |    }
          |    type SomeEvent is event { data: String }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains("do not send any messages to its outlets")) mustBe true
      }
    }

    "warn when source streamlet has no on-init or on-other" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type T is String
          |    source Src is {
          |      outlet out is type T
          |      handler H is {
          |        on event D.C.SomeEvent {
          |          prompt "handle event"
          |        }
          |      }
          |    }
          |    type SomeEvent is event { data: String }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains("no 'on init' or 'on other'")) mustBe true
      }
    }

    "warn when event type is not produced by any handler" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type EId is Id(C.E)
          |    type Cmd is command { data: String }
          |    type Evt is event { data: String }
          |    type OrphanEvent is event { info: String }
          |    type GetData is query { id: String }
          |    type DataResult is result { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd {
          |          send event D.C.Evt to outlet D.C.Events.out
          |        }
          |        on query D.C.GetData {
          |          send result D.C.DataResult to outlet D.C.Events.out
          |        }
          |      }
          |    }
          |    source Events is { outlet out is type Evt }
          |    sink Incoming is { inlet in is type Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(m => m.message.contains("OrphanEvent") && m.message.contains("no handler produces it")) mustBe true
      }
    }

    "warn when context has queries but no results" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type GetData is query { id: String }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains("defines queries but no result types")) mustBe true
      }
    }

    "warn when context has results but no queries" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type DataResult is result { data: String }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains("defines results but no query types")) mustBe true
      }
    }

    "produce no completeness warnings for a well-formed model" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type EId is Id(C.E)
          |    type Cmd is command { data: String }
          |    type Evt is event { data: String }
          |    type GetData is query { id: String }
          |    type DataResult is result { data: String }
          |
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on init {
          |          set field E.Fields.data to "default"
          |        }
          |        on command D.C.Cmd {
          |          send event D.C.Evt to outlet D.C.Events.out
          |        }
          |        on query D.C.GetData {
          |          reply result D.C.DataResult
          |        }
          |      }
          |    }
          |
          |    source Events is { outlet out is type Evt }
          |
          |    sink Incoming is {
          |      inlet in is type Cmd
          |      handler Dispatch is {
          |        on command D.C.Cmd {
          |          tell command D.C.Cmd to entity D.C.E
          |        }
          |      }
          |    }
          |
          |    connector CmdPipe {
          |      from outlet C.Events.out to inlet C.Incoming.in
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          if cw.nonEmpty then {
            info(s"Unexpected completeness warnings:\n${cw.map(_.format).mkString("\n")}")
          }
          cw mustBe empty
      }
    }

    "accept reply statement in query handlers" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type EId is Id(C.E)
          |    type GetData is query { id: String }
          |    type DataResult is result { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on query D.C.GetData {
          |          reply result D.C.DataResult
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          val cw = completenessWarnings(msgs)
          cw.exists(_.message.contains("reply or sending a result")) mustBe false
      }
    }

    "accept require with invariant reference" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type EId is Id(C.E)
          |    type Cmd is command { amount: Number }
          |    type Evt is event { amount: Number }
          |    entity E is {
          |      record Fields is { balance: Number }
          |      invariant BalanceNonNegative is "balance >= 0"
          |      state Main of E.Fields
          |      handler H is {
          |        on init { set field E.Fields.balance to "0" }
          |        on command D.C.Cmd {
          |          require invariant BalanceNonNegative
          |          send event D.C.Evt to outlet D.C.Events.out
          |        }
          |      }
          |    }
          |    source Events is { outlet out is type Evt }
          |    sink Incoming is { inlet in is type Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          msgs.hasErrors mustBe false
          // The invariant should be referenced, no usage warning
          msgs.filter(_.isUsage).exists(
            _.message.contains("BalanceNonNegative")
          ) mustBe false
      }
    }

    "warn when invariant is not referenced by require" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Evt is event { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      invariant DataNotEmpty is "data.nonEmpty"
          |      state Main of E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on event D.C.Evt {
          |          set field E.Fields.data to "updated"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
          (_, _, msgs) =>
            val usageWarnings = msgs.filter(_.isUsage)
            usageWarnings.exists(
              _.message.contains("DataNotEmpty")
            ) mustBe true
        }
      }
    }

    "accept auto-id option on entities" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type EId is Id(C.E)
          |    type Evt is event { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on event D.C.Evt {
          |          set field E.Fields.data to "updated"
          |        }
          |      }
          |    } with { option auto-id }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
        (_, _, msgs) =>
          // auto-id should be accepted without "not recognized" warning
          msgs.exists(m =>
            m.message.contains("auto-id") && m.message.contains("not a recognized")
          ) mustBe false
      }
    }

    "reject auto-id option on non-entity definitions" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type T is String
          |  } with { option auto-id }
          |}
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidate(input.data, "test", shouldFailOnErrors = false) {
          (_, _, msgs) =>
            msgs.exists(m =>
              m.message.contains("auto-id") && m.message.contains("not typically used")
            ) mustBe true
        }
      }
    }
  }
}
