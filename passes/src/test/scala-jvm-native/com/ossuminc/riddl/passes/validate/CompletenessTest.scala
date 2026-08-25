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

  /** Completeness 4b, parameterised by streamlet shape. A sink is the boundary that carries
    * messages out of the stream and into entities, so asking it to dispatch is fair. A split, merge
    * or flow exists to route between ports; a `tell` there would dispatch into an entity IN
    * ADDITION to fanning out, duplicating what the downstream contexts do.
    */
  private def streamletModel(shape: String, body: String): String =
    s"""domain D is {
       |  context C is {
       |    event Evt is { data: String }
       |    entity E is {
       |      record Fields is { data: String }
       |      state Main of record E.Fields is {
       |        handler EH is { on event D.C.Evt { set field Main.data to "x" } }
       |      }
       |    }
       |    processor P as $shape is {
       |      inlet In is event D.C.Evt
       |      outlet Out is event D.C.Evt
       |      handler PH is {
       |        on event D.C.Evt { $body }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private val noDispatch = "does not dispatch to any entity via 'tell'"

  "Completeness 4b (streamlet dispatch)" should {

    "not fire for a split, whose job is routing between ports" in { (td: TestData) =>
      val input = RiddlParserInput(streamletModel("split", "send event D.C.Evt to outlet Out"), td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        val hits = completenessWarnings(msgs).filter(_.message.contains(noDispatch))
        if hits.nonEmpty then
          info(s"Routing streamlet was asked to dispatch:\n${hits.map(_.format).mkString("\n")}")
        hits mustBe empty
      }
    }

    "not fire for a flow" in { (td: TestData) =>
      val input = RiddlParserInput(streamletModel("flow", "send event D.C.Evt to outlet Out"), td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        completenessWarnings(msgs).filter(_.message.contains(noDispatch)) mustBe empty
      }
    }

    /* The two cases this suite lacked, and their absence is why an rc.16 regression reached two
     * downstream repos. Every case here builds from `processor P as $shape` — a DECLARED
     * streamlet — so the arity-derived-Sink path was never exercised. A Repository or Projector
     * with one inlet and no outlets DERIVES the shape `Sink`, and once [4.1] widened
     * `WithStreamlets.streamlets` to every port-bearing processor, both were asked to dispatch to
     * an entity: 25 false positives in riddl-examples, 3 in riddl-models.
     *
     * They are written as separate literal models rather than through `streamletModel`, precisely
     * because that helper can only produce declared streamlets — routing them through it would
     * reproduce the blind spot.
     *
     * **Each model also contains a DECLARED sink that does dispatch, and it is load-bearing.**
     * The block is gated on the context having a declared streamlet, so without one the gate
     * alone suppresses the check and the test passes no matter what the loop does — verified by
     * reverting the fix and watching these still pass. The sink makes the gate open so the loop
     * is the thing under test, and it must stay silent itself. */
    "not fire for a repository, whose boundary is into STORAGE, not into entities" in {
      (td: TestData) =>
        val model =
          """domain D is {
            |  context C is {
            |    event Evt is { id: String }
            |    entity E is {
            |      record Fields is { id: String }
            |      initial state Main of record D.C.E.Fields
            |      handler EH is { on init { do "init" } }
            |      outlet Announced is event D.C.Evt
            |    }
            |    sink Intake is {
            |      inlet Requests is event D.C.Evt
            |      handler IntakeH is {
            |        on event D.C.Evt { tell event D.C.Evt(id = "x") to entity D.C.E }
            |      }
            |    }
            |    repository Store is {
            |      inlet Recorded is event D.C.Evt
            |      handler StoreH is {
            |        on event D.C.Evt { do "store the identifier carried by the event" }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
        parseAndValidateInput(RiddlParserInput(model, td), shouldFailOnErrors = false) {
          (_, _, msgs) =>
            val hits = completenessWarnings(msgs).filter(_.message.contains(noDispatch))
            if hits.nonEmpty then
              info(s"Repository was asked to dispatch:\n${hits.map(_.format).mkString("\n")}")
            hits mustBe empty
        }
    }

    "not fire for a projector, which updates a projection rather than dispatching" in {
      (td: TestData) =>
        val model =
          """domain D is {
            |  context C is {
            |    event Evt is { id: String }
            |    entity E is {
            |      record Fields is { id: String }
            |      initial state Main of record D.C.E.Fields
            |      handler EH is { on init { do "init" } }
            |      outlet Announced is event D.C.Evt
            |    }
            |    sink Intake is {
            |      inlet Requests is event D.C.Evt
            |      handler IntakeH is {
            |        on event D.C.Evt { tell event D.C.Evt(id = "x") to entity D.C.E }
            |      }
            |    }
            |    projector View is {
            |      inlet Seen is event D.C.Evt
            |      record Fields is { id: String }
            |      handler ViewH is { on event D.C.Evt { do "update the projection" } }
            |    }
            |  }
            |}
            |""".stripMargin
        parseAndValidateInput(RiddlParserInput(model, td), shouldFailOnErrors = false) {
          (_, _, msgs) =>
            val hits = completenessWarnings(msgs).filter(_.message.contains(noDispatch))
            if hits.nonEmpty then
              info(s"Projector was asked to dispatch:\n${hits.map(_.format).mkString("\n")}")
            hits mustBe empty
        }
    }

    "still fire for a sink that handles messages but never dispatches" in { (td: TestData) =>
      val input = RiddlParserInput(streamletModel("sink", "do \"nothing useful\""), td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        completenessWarnings(msgs).exists(_.message.contains(noDispatch)) mustBe true
      }
    }

    "not fire for a sink that does dispatch" in { (td: TestData) =>
      val input =
        RiddlParserInput(streamletModel("sink", "tell event D.C.Evt to entity D.C.E"), td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        completenessWarnings(msgs).filter(_.message.contains(noDispatch)) mustBe empty
      }
    }
  }

  "CompletenessWarning" should {
    "warn when entity state has no on-init clause" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    event Evt is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on command D.C.Cmd {
          |          send event D.C.Evt to outlet D.C.Events.out
          |        }
          |        on query D.C.GetData {
          |          do "return data"
          |        }
          |      }
          |    }
          |    query GetData is { id: String }
          |    source Events is { outlet out is event Evt }
          |    sink Incoming is { inlet in is command Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(_.message.contains("no 'on init' clause")) mustBe true
      }
    }

    "warn when entity state has on-init but no set statement" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    event Evt is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on init {
          |          do "should set state here"
          |        }
          |        on command D.C.Cmd {
          |          send event D.C.Evt to outlet D.C.Events.out
          |        }
          |        on query D.C.GetData {
          |          do "return data"
          |        }
          |      }
          |    }
          |    query GetData is { id: String }
          |    source Events is { outlet out is event Evt }
          |    sink Incoming is { inlet in is command Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(_.message.contains("no 'set' statement")) mustBe true
      }
    }

    "not warn when entity state has proper on-init with set" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    event Evt is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on init {
          |          set field E.Fields.data to "default"
          |        }
          |        on command D.C.Cmd {
          |          send event D.C.Evt to outlet D.C.Events.out
          |        }
          |        on query D.C.GetData {
          |          do "return data"
          |        }
          |      }
          |    }
          |    query GetData is { id: String }
          |    source Events is { outlet out is event Evt }
          |    sink Incoming is { inlet in is command Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(_.message.contains("on init")) mustBe false
      }
    }

    "warn when command handler does not send event" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd {
          |          set field E.Fields.data to "updated"
          |        }
          |        on query D.C.GetData {
          |          do "return data"
          |        }
          |      }
          |    }
          |    query GetData is { id: String }
          |    sink Incoming is { inlet in is command Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(
          _.message.contains(
            "should result in sending an event"
          )
        ) mustBe true
      }
    }

    "not warn when command handler yields an event" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    event Evt is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd {
          |          yield event D.C.Evt
          |        }
          |        on query D.C.GetData {
          |          do "return data"
          |        }
          |      }
          |    }
          |    query GetData is { id: String }
          |    source Events is { outlet out is event Evt }
          |    sink Incoming is { inlet in is command Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(
          _.message.contains(
            "should result in sending an event"
          )
        ) mustBe false
      }
    }

    "warn when query handler does not send result" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    query Qry is { id: String }
          |    event Evt is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on query D.C.Qry {
          |          set field E.Fields.data to "looked up"
          |        }
          |      }
          |    }
          |    source Events is { outlet out is event Evt }
          |    sink Incoming is { inlet in is type Qry }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(
          _.message.contains(
            "should result in a reply or sending a result"
          )
        ) mustBe true
      }
    }

    "warn when projector does not reference a repository" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    event Evt is { data: String }
          |    projector P is {
          |      record ProjRecord is { data: String }
          |      handler H is {
          |        on event D.C.Evt {
          |          do "handle event"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(
          _.message.contains(
            "does not reference any repository to persist its projection"
          )
        ) mustBe true
      }
    }

    "warn when projector does not tell to a repository" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    event Evt is { data: String }
          |    repository Repo is {
          |      schema S is flat of records as type D.C.RecType
          |      handler RH is {
          |        on command D.C.Store {
          |          do "store it"
          |        }
          |      }
          |    }
          |    command Store is { data: String }
          |    record RecType is { data: String }
          |    projector P is {
          |      record ProjRecord is { data: String }
          |      updates repository Repo
          |      handler H is {
          |        on event D.C.Evt {
          |          do "handle event but never tell repo"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(
          _.message.contains(
            "does not persist its projection"
          )
        ) mustBe true
      }
    }

    "warn when handler has no executable statements (empty)" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    event Evt is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler Empty is {
          |        on event D.C.Evt { ??? }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(
          _.message.contains(
            "has no executable statements"
          )
        ) mustBe true
      }
    }

    "warn when handler has only prompt statements" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    event Evt is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler PromptOnly is {
          |        on event D.C.Evt {
          |          do "do something"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(
          _.message.contains(
            "contains only 'do' statements"
          )
        ) mustBe true
      }
    }

    "warn when entity has no query handler" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    event Evt is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd {
          |          send event D.C.Evt to outlet D.C.Events.out
          |        }
          |      }
          |    }
          |    source Events is { outlet out is event Evt }
          |    sink Incoming is { inlet in is command Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(
          _.message.contains(
            "no 'on query' clause"
          )
        ) mustBe true
      }
    }

    // ---- 2026-08-18 ruling (Reid): a processor receives ONLY through its OWN inlet ----
    //
    // "inlets are needed to receive, outlets to transmit/publish." A sibling's port does not
    // deliver to an entity, and neither does its context's: getting a message out of a context is
    // entity outlet -> connector -> context inlet -> handler -> context outlet, whose first step is
    // the entity's own outlet. `tell` is the same operation as `send` for this purpose -- a
    // generator may lower it more efficiently, but only while keeping RIDDL's semantics.
    //
    // These checks replace two context-scoped ones that asked whether ANYTHING in the context had a
    // port. Each is gated on the entity actually doing the thing, so a passive or stubbed entity is
    // not told to add ports it never uses.

    "warn when an entity handles messages but declares no inlet" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd { do "handle command" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        completenessWarnings(msgs).exists(
          _.message.contains("declares no inlet to receive them on")
        ) mustBe true
      }
    }

    "not warn about an inlet when the entity declares its own" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    entity E is {
          |      inlet in is type D.C.Cmd
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd { do "handle command" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        completenessWarnings(msgs).exists(
          _.message.contains("declares no inlet to receive them on")
        ) mustBe false
      }
    }

    "not warn about an inlet when a sibling or the context declares one" in { (td: TestData) =>
      // The whole point of the ruling: neither of these delivers to E, so E is still warned about.
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    inlet cin is type D.C.Cmd
          |    sink Other is { inlet oin is type D.C.Cmd }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd { do "handle command" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        completenessWarnings(msgs).exists(
          _.message.contains("declares no inlet to receive them on")
        ) mustBe true
      }
    }

    "warn when an entity emits messages but declares no outlet" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    event Evt is { data: String }
          |    entity E is {
          |      inlet in is type D.C.Cmd
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd { yield event D.C.Evt }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        completenessWarnings(msgs).exists(
          _.message.contains("declares no outlet to transmit them on")
        ) mustBe true
      }
    }

    "not warn about an outlet when the entity declares its own" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    event Evt is { data: String }
          |    entity E is {
          |      inlet in is type D.C.Cmd
          |      outlet out is type D.C.Evt
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd { yield event D.C.Evt }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        completenessWarnings(msgs).exists(
          _.message.contains("declares no outlet to transmit them on")
        ) mustBe false
      }
    }

    "not warn about an outlet when the entity emits nothing" in { (td: TestData) =>
      // Gated on doing the thing: an entity that publishes nothing needs no outlet.
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    entity E is {
          |      inlet in is type D.C.Cmd
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on init { set field E.Fields.data to "x" }
          |        on command D.C.Cmd { do "handle command" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        completenessWarnings(msgs).exists(
          _.message.contains("declares no outlet to transmit them on")
        ) mustBe false
      }
    }

    "not warn about ports for a stubbed entity" in { (td: TestData) =>
      // The standing `???` rule: a stub earns at most a Missing warning, never a structural one.
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    entity E is { ??? }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(_.message.contains("declares no inlet to receive them on")) mustBe false
        cw.exists(_.message.contains("declares no outlet to transmit them on")) mustBe false
      }
    }

    // ---- A queried repository that declares no index (Reid, 2026-08-18, riddlg's request) ----
    //
    // Reading a store by a value it does not index is a sequential scan by construction. The check
    // deliberately does not name a FIELD, because the model does not say which one: all 406
    // repository `on query` bodies in the corpus are prose, and taking the query TYPE's fields as
    // the operands instead maps to a stored field only 6% of the time. See the check's scaladoc.

    "warn when a repository answers queries but declares no index" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    result R is { data: String }
          |    query GetThing replies result D.C.R is { id: String }
          |    repository Store is {
          |      record Stored is { id: String, data: String }
          |      schema S is relational of things as type Stored
          |      handler H is {
          |        on query D.C.GetThing is { do "read the thing" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        completenessWarnings(msgs).exists(_.message.contains("answers queries but its schema declares no index")) mustBe true
      }
    }

    "not warn when the repository's schema declares an index" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    result R is { data: String }
          |    query GetThing replies result D.C.R is { id: String }
          |    repository Store is {
          |      record Stored is { id: String, data: String }
          |      schema S is relational of things as type Stored
          |        index on field Stored.id
          |      handler H is {
          |        on query D.C.GetThing is { do "read the thing" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        completenessWarnings(msgs).exists(_.message.contains("answers queries but its schema declares no index")) mustBe false
      }
    }

    "not warn about indices for a write-only repository" in { (td: TestData) =>
      // Answers no queries, so nothing reads it by value and no index is implied.
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Put is { id: String }
          |    repository Store is {
          |      record Stored is { id: String, data: String }
          |      schema S is relational of things as type Stored
          |      handler H is {
          |        on command D.C.Put is { do "write the thing" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        completenessWarnings(msgs).exists(_.message.contains("answers queries but its schema declares no index")) mustBe false
      }
    }

    "not warn about indices for a stubbed repository" in { (td: TestData) =>
      // The standing `???` rule: a stub earns at most a Missing warning about its body.
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    repository Store is { ??? }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        completenessWarnings(msgs).exists(_.message.contains("answers queries but its schema declares no index")) mustBe false
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
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(_.message.contains("is not connected")) mustBe true
      }
    }

    "warn when entity has no Id type" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    event Evt is { data: String }
          |    query GetData is { id: String }
          |    result DataResult is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
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
          |    source Events is { outlet out is event Evt }
          |    sink Incoming is { inlet in is command Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(_.message.contains("does not define an Id type")) mustBe true
      }
    }

    /** The Id-type check matches by RESOLVED IDENTITY, not by the path's last segment.
      *
      * Two entities named `Order` in different contexts, and exactly ONE `Id` type, pointing at
      * `Two.Order`. Under the old last-segment name match both entities were considered to have an
      * identity type and NEITHER warned; only `Two.Order` actually has one, so `One.Order` must.
      *
      * This mattered more once `Id(P)` widened from Entity to any Processor on this branch: an
      * `Id(repository Foo)` whose last segment happens to match an entity name was being counted as
      * that entity's identity type. Reid overruled name matching twice for the same class of bug
      * (`isAddressFieldFor`, and the `on term` leading parameter); this site predated both and
      * survived the sweep they prompted.
      */
    "attribute an Id type by identity, not by its path's last segment" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context One is {
          |    entity Order is {
          |      record F is { a: String }
          |      state S of record Order.F
          |      handler H is { on other is { do "x" } }
          |    }
          |  }
          |  context Two is {
          |    type OrderId is Id(entity D.Two.Order)
          |    entity Order is {
          |      record F is { a: String }
          |      state S of record Order.F
          |      handler H is { on other is { do "x" } }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val noIdWarnings = completenessWarnings(msgs)
          .map(_.message)
          .filter(_.contains("does not define an Id type"))
        // Exactly one -- `One.Order`. Asserting the COUNT rather than "nonEmpty" is what makes the
        // name-matching behaviour fail here: it silenced both, giving zero.
        noIdWarnings.size mustBe 1
      }
    }

    "not warn when entity Id type is defined in containing context" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context FrontOfHouse is {
          |    type ReservationId is Id(FrontOfHouse.Reservation)
          |    event Evt is { data: String }
          |    entity Reservation is {
          |      record Fields is { data: String }
          |      state Main of record Reservation.Fields
          |      handler H is {
          |        on init { set field Reservation.Fields.data to "x" }
          |        on event D.FrontOfHouse.Evt {
          |          set field Reservation.Fields.data to "updated"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        val idWarnings = cw.filter(m => m.message.contains("Id type") || m.message.contains("Id("))
        if idWarnings.nonEmpty then {
          info(s"Unexpected Id warnings:\n${idWarnings.map(_.format).mkString("\n")}")
        }
        idWarnings mustBe empty
      }
    }

    "not warn when entity Id type is in an included file within the context" in { (td: TestData) =>
      val path = java.nio.file.Path.of("passes/input/id-in-include/main.riddl").toAbsolutePath
      val data = java.nio.file.Files.readString(path)
      val url = com.ossuminc.riddl.utils.URL.fromFullPath(path.toString)
      val rpi = RiddlParserInput(data, url, td.name)
      parseAndValidateInput(rpi, shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        val idWarnings = cw.filter(m => m.message.contains("Id type") || m.message.contains("Id("))
        if idWarnings.nonEmpty then {
          info(s"Unexpected Id warnings:\n${idWarnings.map(_.format).mkString("\n")}")
        }
        idWarnings mustBe empty
      }
    }

    "warn when entity Id type is defined inside entity body" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    event Evt is { data: String }
          |    entity E is {
          |      type EId is Id(E)
          |      record Fields is { data: String }
          |      state Main of record E.Fields
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
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(_.message.contains("move it to the containing context")) mustBe true
      }
    }

    "warn when entity Id type is defined at domain level" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  type EId is Id(C.E)
          |  context C is {
          |    event Evt is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
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
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(_.message.contains("outside the containing context")) mustBe true
      }
    }

    // The old check here ("event-sourced but this command handler does not emit an event") was
    // DELETED in 2.0. It looked for `send`/`tell` of an event and did not recognise `yield`, so it
    // fired on exactly the models the new event-sourcing rules bless. Those rules -- every handled
    // command declares `yields`, every yielded event has an `on event` clause, state changes only
    // while handling one's own event -- subsume it, as Errors rather than warnings, and live in
    // EventSourcedEntityTest.

    "warn when saga step has no tell command" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    event Evt is { data: String }
          |    saga S is {
          |      requires { data: String }
          |      returns { result: String }
          |      step One is {
          |        do "do something"
          |      } reverted by {
          |        do "undo something"
          |      }
          |      step Two is {
          |        do "do more"
          |      } reverted by {
          |        do "undo more"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(_.message.contains("no 'tell command'")) mustBe true
      }
    }

    "warn when on-other clause is empty" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    event Evt is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
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
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
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
          |          do "transform but forget to send"
          |        }
          |      }
          |    }
          |    event SomeEvent is { data: String }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
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
          |          do "handle event"
          |        }
          |      }
          |    }
          |    event SomeEvent is { data: String }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(_.message.contains("no 'on init' or 'on other'")) mustBe true
      }
    }

    "warn when event type is not produced by any handler" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type EId is Id(C.E)
          |    command Cmd is { data: String }
          |    event Evt is { data: String }
          |    event OrphanEvent is { info: String }
          |    query GetData is { id: String }
          |    result DataResult is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
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
          |    source Events is { outlet out is event Evt }
          |    sink Incoming is { inlet in is command Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(m =>
          m.message.contains("OrphanEvent") && m.message.contains("nothing in the model emits it")
        ) mustBe true
      }
    }

    "warn when context has queries but no results" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    query GetData is { id: String }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(_.message.contains("defines queries but no result types")) mustBe true
      }
    }

    "warn when context has results but no queries" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    result DataResult is { data: String }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(_.message.contains("defines results but no query types")) mustBe true
      }
    }

    /** Message-value-source Task 4 moved this fixture, deliberately rather than by filtering the
      * new warning out: a bare `send event D.C.Evt` says nothing about where the event's `data`
      * comes from, so under the new rule this model is no longer well-formed and the test's own
      * premise would be false. It now shows BOTH sanctioned spellings — the constructor form for
      * `send`/`reply`, and A56's value form (`on cmd: command …` then `tell cmd`) for the `tell`,
      * which is also why `Cmd` carries a `target: Id(C.E)` field for the addressing check to find.
      */
    "produce no completeness warnings for a well-formed model" in { (td: TestData) =>
      // Rebuilt 2026-08-18 for the ruling that a processor receives only through its OWN inlet and
      // publishes only through its OWN outlet. The previous version was "well-formed" only under
      // the checks as they stood: a sibling `sink` told the entity, and the entity sent to a
      // `source`'s outlet -- both disallowed now.
      //
      // Deliberately INTRA-context and free of boundary ports. Inside one context anything may
      // talk to anything, and a connector may drive the entity's own inlet directly. Boundary
      // ports are omitted because a single-context model has nothing on the other side of them,
      // so they would (correctly) report as unconnected.
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type EId is Id(C.E)
          |    command Cmd is { target: Id(C.E), data: String }
          |    event Evt is { data: String }
          |    result DataResult is { data: String }
          |    query GetData replies result D.C.DataResult is { id: String }
          |
          |    source Ingress is { outlet out is type Cmd }
          |
          |    entity E is {
          |      inlet ein is type Cmd
          |      outlet eout is type Evt
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on init {
          |          set field E.Fields.data to "default"
          |        }
          |        on command D.C.Cmd {
          |          send event D.C.Evt(data = "changed") to outlet D.C.E.eout
          |        }
          |        on query D.C.GetData {
          |          reply result D.C.DataResult(data = "answer")
          |        }
          |      }
          |    }
          |
          |    sink Egress is {
          |      inlet in is type Evt
          |      // `on other` is what makes this sink DISCARD deliberately rather than merely fail to
          |      // say what arriving means. `sink` describes ARITY (no outlets), never behaviour --
          |      // `Riddl.BottomlessPit`, the canonical discard, states it exactly this way. Without
          |      // the clause, `checkInletsAreReceived` correctly reports the inlet as unreceived.
          |      handler Discard is { on other is { do "discard the event" } }
          |    }
          |
          |    repository Store is { ??? }
          |
          |    connector Inbound {
          |      from outlet C.Ingress.out to inlet C.E.ein
          |    }
          |    connector Outbound {
          |      from outlet C.E.eout to inlet C.Egress.in
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        if cw.nonEmpty then {
          info(s"Unexpected completeness warnings:\n${cw.map(_.format).mkString("\n")}")
        }
        cw mustBe empty
      }
    }

    "accept a `reply` statement as satisfying a query handler" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type EId is Id(C.E)
          |    result DataResult is { data: String }
          |    query GetData replies result D.C.DataResult is { id: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
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
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        val cw = completenessWarnings(msgs)
        cw.exists(_.message.contains("reply or sending a result")) mustBe false
      }
    }

    "accept require with invariant reference" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type EId is Id(C.E)
          |    command Cmd is { amount: Number }
          |    event Evt is { amount: Number }
          |    entity E is {
          |      record Fields is { balance: Number }
          |      invariant BalanceNonNegative is "balance >= 0"
          |      state Main of record E.Fields
          |      handler H is {
          |        on init { set field E.Fields.balance to "0" }
          |        on command D.C.Cmd {
          |          require invariant BalanceNonNegative
          |          send event D.C.Evt(amount = "the amount") to outlet D.C.Events.out
          |        }
          |      }
          |    }
          |    source Events is { outlet out is event Evt }
          |    sink Incoming is { inlet in is command Cmd }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        msgs.hasErrors mustBe false
        // The invariant should be referenced, no usage warning
        msgs
          .filter(_.isUsage)
          .exists(
            _.message.contains("BalanceNonNegative")
          ) mustBe false
      }
    }

    // Rewritten 2026-08-04. This case used to assert that an entity invariant no `require` named
    // draws a usage warning. Under the new semantics that invariant is applied IMPLICITLY to every
    // clause of its entity (§15.2), so NOT being named is the norm and warning about it would be
    // wrong. Only the one form that cannot be implicit — `requires <type>`, whose value ambient
    // scope cannot supply — is inert when nothing invokes it, so that is what is asserted now.
    "not warn about an entity invariant, which applies implicitly" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    event Evt is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      invariant DataNotEmpty is "data.nonEmpty"
          |      state Main of record E.Fields
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
        parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
          msgs.filter(_.isUsage).exists(_.message.contains("DataNotEmpty")) mustBe false
        }
      }
    }

    "warn when a `requires <type>` invariant is never applied" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    event Evt is { data: String }
          |    record Limits is { ceiling: Integer, used: Integer }
          |    entity E is {
          |      record Fields is { data: String }
          |      invariant UnderLimit requires record D.C.Limits is used <= ceiling
          |      state Main of record E.Fields
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
        parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
          msgs.filter(_.isUsage).exists(_.message.contains("UnderLimit")) mustBe true
        }
      }
    }

    "accept auto-id option on entities" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type EId is Id(C.E)
          |    event Evt is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
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
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        // auto-id should be accepted without "not recognized" warning
        msgs.exists(m =>
          m.message.contains("auto-id") && m.message.contains("not a recognized")
        ) mustBe false
      }
    }

    "accept protocol option on contexts" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type T is String
          |  } with { option protocol("kafka") }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        // protocol should be accepted without "not recognized" or
        // "not typically used" style warnings
        msgs.exists(m =>
          m.message.contains("protocol") &&
            (m.message.contains("not a recognized") ||
              m.message.contains("not typically used"))
        ) mustBe false
      }
    }

    // INVERTED, deliberately. `compensate` was registered as a saga option and is now
    // deregistered: `SagaParser` requires `reverted by` on every step, so a saga without
    // compensation cannot be written and the option declared nothing. Asserting the warning
    // IS raised keeps the removal honest -- a silent re-registration would otherwise go
    // unnoticed, and one generator already mistook the option for a switch and emitted a
    // coordinator that abandoned completed steps on failure.
    "warn that compensate is not a recognized option on sagas" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    saga S is {
          |      step One is {
          |        do "do something"
          |      } reverted by {
          |        do "undo something"
          |      }
          |      step Two is {
          |        do "do more"
          |      } reverted by {
          |        do "undo more"
          |      }
          |    } with { option compensate }
          |  }
          |}
          |""".stripMargin,
        td
      )
      // Style warnings are pinned ON: the "not a recognized RIDDL option" message is a
      // StyleWarning, which Messages.Accumulator DROPS when showStyleWarnings is off, and
      // `pc.options` is global state other suites mutate. The original assertion here was
      // `mustBe false` and so passed either way; asserting PRESENCE exposes the dependency.
      pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
        parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
          msgs.exists(m =>
            m.message.contains("compensate") &&
              (m.message.contains("not a recognized") ||
                m.message.contains("not typically used"))
          ) mustBe true
        }
      }
    }

    "accept parallel option on sagas" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    saga S is {
          |      step One is {
          |        do "do something"
          |      } reverted by {
          |        do "undo something"
          |      }
          |      step Two is {
          |        do "do more"
          |      } reverted by {
          |        do "undo more"
          |      }
          |    } with { option parallel }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        // A11: parallel is a Saga option; accepted without "not recognized" or
        // "not typically used" style warnings
        msgs.exists(m =>
          m.message.contains("parallel") &&
            (m.message.contains("not a recognized") ||
              m.message.contains("not typically used"))
        ) mustBe false
      }
    }

    "nudge parallel option on non-saga definitions" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type T is String
          |  } with { option parallel }
          |}
          |""".stripMargin,
        td
      )
      // Pin the options: the "not typically used" nudge is a StyleWarning, and ambient
      // options vary across a full sequential run (cf. the async nudge test below).
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
          // Recognized (so no "not a recognized"), but drawn on a Context it should
          // trigger the parent-kind "not typically used" nudge
          msgs.exists(m =>
            m.message.contains("parallel") && m.message.contains("not a recognized")
          ) mustBe false
          msgs.exists(m =>
            m.message.contains("parallel") && m.message.contains("not typically used")
          ) mustBe true
        }
      }
    }

    "accept protocol option on any processor (streamlet, entity)" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Evt is String
          |    source Events is { outlet out is event Evt } with { option protocol("kafka") }
          |    entity E is {
          |      handler h is { ??? }
          |    } with { option protocol("amqp") }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        // protocol on a streamlet (parent-kind = shape name "Source") and on an
        // entity should be accepted without "not recognized" or "not typically
        // used" style warnings
        msgs.exists(m =>
          m.message.contains("protocol") &&
            (m.message.contains("not a recognized") ||
              m.message.contains("not typically used"))
        ) mustBe false
      }
    }

    "accept event_catalog_version option on domains, contexts and messages" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command PlaceOrder is { id: String } with {
          |      option event_catalog_version("2.1.0")
          |    }
          |  } with { option event_catalog_version("1.4.0") }
          |} with { option event_catalog_version("3.0.0") }
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        // event_catalog_version should be accepted on all three without
        // "not recognized" or "not typically used" style warnings
        msgs.exists(m =>
          m.message.contains("event_catalog_version") &&
            (m.message.contains("not a recognized") ||
              m.message.contains("not typically used"))
        ) mustBe false
      }
    }

    "accept message_envelope at every scope, and resolve the predefined Envelope" in {
      (td: TestData) =>
        // Scope-inherited like sql_dialect: declared on a domain or context it covers all the
        // messaging within, so it must be accepted at every level rather than pinned to one.
        // The context-level declaration names `Riddl.Envelope`, which also proves the predefined
        // record is REACHABLE by path from a user model without an import.
        val input = RiddlParserInput(
          """domain D is {
            |  context C is {
            |    command DoIt is { a: Integer }
            |    entity Order is {
            |      handler H is { on command D.C.DoIt is { do "handle" } }
            |    } with {
            |      option message_envelope("Riddl.Envelope")
            |    }
            |  } with { option message_envelope("Riddl.Envelope") }
            |} with { option message_envelope("Riddl.Envelope") }
            |""".stripMargin,
          td
        )
        parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
          msgs.exists(m =>
            m.message.contains("message_envelope") &&
              (m.message.contains("not a recognized") ||
                m.message.contains("not typically used"))
          ) mustBe false
        }
    }

    "accept sql_dialect and sql_table options on entities and their parents" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    entity Order is {
          |      type OrderId is Id(Order)
          |    } with {
          |      option sql_dialect("mysql")
          |      option sql_table("orders")
          |    }
          |  } with { option sql_dialect("postgres") }
          |} with { option sql_dialect("ansi") }
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        // both options should be accepted without "not recognized" or
        // "not typically used" style warnings
        msgs.exists(m =>
          (m.message.contains("sql_dialect") ||
            m.message.contains("sql_table")) &&
            (m.message.contains("not a recognized") ||
              m.message.contains("not typically used"))
        ) mustBe false
      }
    }

    "accept backstage options on domains, contexts and entities" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    entity E is {
          |      type Id is Id(E)
          |    } with {
          |      option backstage_owner("sales-team")
          |      option backstage_lifecycle("production")
          |      option backstage_type("service")
          |    }
          |  } with { option backstage_owner("platform-team") }
          |} with { option backstage_lifecycle("experimental") }
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        // all three should be accepted without "not recognized" or
        // "not typically used" style warnings
        msgs.exists(m =>
          m.message.contains("backstage_") &&
            (m.message.contains("not a recognized") ||
              m.message.contains("not typically used"))
        ) mustBe false
      }
    }

    "accept confluence options on domains" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type T is String
          |  }
          |} with {
          |  option confluence_space("DOCS")
          |  option confluence_parent("Systems")
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        msgs.exists(m =>
          m.message.contains("confluence_") &&
            (m.message.contains("not a recognized") ||
              m.message.contains("not typically used"))
        ) mustBe false
      }
    }

    "flag confluence options on non-domain definitions" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type T is String
          |  } with { option confluence_space("DOCS") }
          |}
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
          // recognized, but the generator only reads it from a domain
          msgs.exists(m =>
            m.message.contains("confluence_space") &&
              m.message.contains("not typically used")
          ) mustBe true
        }
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
        parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
          msgs.exists(m =>
            m.message.contains("auto-id") && m.message.contains("not typically used")
          ) mustBe true
        }
      }
    }

    "accept async option on portlets (inlets and outlets)" in { (td: TestData) =>
      // `async` is a PORTLET option: it marks an Inlet or Outlet as a codegen
      // async boundary (anti-fusion). It must draw neither "not a recognized"
      // nor "not typically used" when placed on an outlet or an inlet.
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Forecast is any of { Rainy, Cloudy, Sunny }
          |    source GetForecast is {
          |      outlet Out is type Forecast with { option async }
          |    }
          |    sink UseForecast is {
          |      inlet In is type Forecast with { option async }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        msgs.exists(m =>
          m.message.contains("async") &&
            (m.message.contains("not a recognized") ||
              m.message.contains("not typically used"))
        ) mustBe false
      }
    }

    "accept unordered option on connectors and inlets" in { (td: TestData) =>
      // `unordered` (A33) is the complement of `ordered`: it is a delivery-
      // ordering property of a Connector or an Inlet. It must draw neither
      // "not a recognized" nor "not typically used" on either parent.
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Evt is any of { A, B }
          |    source Events is { outlet out is event Evt }
          |    sink Incoming is {
          |      inlet in is type Evt with { option unordered }
          |    }
          |    connector Pipe is {
          |      from outlet C.Events.out to inlet C.Incoming.in
          |    } with { option unordered }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
        msgs.exists(m =>
          m.message.contains("unordered") &&
            (m.message.contains("not a recognized") ||
              m.message.contains("not typically used"))
        ) mustBe false
      }
    }

    "nudge async option on non-portlet definitions" in { (td: TestData) =>
      // On a streamlet or a context, `async` is recognized (no "not a
      // recognized" warning) but draws the parent-kind "not typically used"
      // nudge because its valid parents are Inlet / Outlet only.
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Forecast is any of { Rainy, Cloudy, Sunny }
          |    source GetForecast is {
          |      outlet Out is type Forecast
          |    } with { option async }
          |  } with { option async }
          |}
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidate(input.data, "test", shouldFailOnErrors = false) { (_, _, msgs) =>
          msgs.exists(m =>
            m.message.contains("async") && m.message.contains("not a recognized")
          ) mustBe false
          msgs.exists(m =>
            m.message.contains("async") && m.message.contains("not typically used")
          ) mustBe true
        }
      }
    }
  }
}
