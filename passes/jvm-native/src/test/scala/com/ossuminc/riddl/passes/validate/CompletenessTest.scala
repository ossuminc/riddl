/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.CompletenessWarning
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{pc, ec}

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
            "should result in sending a result"
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

    "produce no completeness warnings for a well-formed model" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
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
          |          send result D.C.DataResult to outlet D.C.Events.out
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
  }
}
