/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** What ENDS a stream chain, and that a chain may not loop (Reid's rulings, 2026-09-04).
  *
  * `stream-source-reaches-no-sink` used to ask whether a Source reaches a processor whose effective
  * SHAPE is `sink` — zero outlets. A6 (a sender must own the outlet it sends on) made that
  * unsatisfiable for any terminal processor that also records what it received: the moment an
  * event log owns an outlet to its repository its arity is a flow, and the sources draining into it
  * are reported as reaching nothing. 42 findings across the corpus, none fixable by wiring.
  *
  * The ruling mirrors the 2026-08-14 chain-HEAD ruling (`75a791682`): a chain TAIL is defined by
  * what a processor DOES, not by its shape. A tail has an inlet, handles every message type its
  * inlets admit (alternation members expanded, `on other` counting), and no clause handling type T
  * sends, tells or forwards a message of THAT type onward. Sending a different type — a `Persist`
  * command to a repository, say — is a write, not a continuation: the arriving message has been
  * consumed. A processor with no handlers at all is opaque and gets the benefit of the doubt: a
  * tail if it has no outlets, pass-through otherwise.
  *
  * And a stream graph may not contain a cycle: connectors carrying one message type that form a
  * loop let a message circulate forever, which is an Error. A request/response pair — a command
  * one way, an event back — is two chains, not a loop.
  */
class StreamTailTest extends AbstractValidatingTest {

  private def model(processors: String, connectors: String): String =
    s"""domain D is {
       |  event Evt is { x: Integer } with { briefly "e" }
       |  event Other is { y: Integer } with { briefly "o" }
       |  command Persist is { x: Integer } with { briefly "p" }
       |  command Cmd yields event D.Evt is { x: Integer } with { briefly "c" }
       |  type EvtOrOther is one of { D.Evt or D.Other } with { briefly "alt" }
       |  context C is {
       |    streamlet Src as source is {
       |      outlet o is event D.Evt with { briefly "o" }
       |    } with { briefly "src" }
       |$processors
       |$connectors
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
      captured = messages
      succeed
    }
    captured

  private def noPath(msgs: Messages): Seq[Message] =
    msgs.filter(_.message.contains("no downstream path"))

  private def cycles(msgs: Messages): Seq[Message] =
    msgs.filter(_.ruleId.contains(RuleId.GraphCycle))

  "a chain tail" should {

    "be a processor that consumes what arrives and writes a DIFFERENT message onward" in {
      (td: TestData) =>
        // The corpus's shape: an event log receives Evt and sends a Persist COMMAND to its
        // repository, which answers queries on an outlet of its own (`as merge`). Under A6 the log
        // owns its outlet, so NOTHING here is Sink-shaped — and the log is still the end of Evt's
        // chain, because Evt itself goes no further. This is the case the old rule got wrong.
        val msgs = diagnostics(
          model(
            """    streamlet Log as flow is {
              |      inlet i is event D.Evt with { briefly "i" }
              |      outlet o is command D.Persist with { briefly "o" }
              |      handler h is { on event D.Evt { send command D.Persist to outlet o } } with { briefly "h" }
              |    } with { briefly "log" }
              |    repository Store is {
              |      inlet i is command D.Persist with { briefly "i" }
              |      outlet r is event D.Other with { briefly "query responses leave here" }
              |      handler h is { on command D.Persist { do "store it" } } with { briefly "h" }
              |    } with { briefly "store" }""".stripMargin,
            """    connector c1 is { from outlet C.Src.o to inlet C.Log.i } with { briefly "c" }
              |    connector c2 is { from outlet C.Log.o to inlet C.Store.i } with { briefly "c" }""".stripMargin
          ),
          "tail-log"
        )
        noPath(msgs) mustBe empty
    }

    "NOT be a processor that sends the SAME message type onward" in { (td: TestData) =>
      // A relay handles Evt and sends Evt on; Evt's chain continues and, with nothing after the
      // relay, reaches no tail.
      val msgs = diagnostics(
        model(
          """    streamlet Relay as flow is {
            |      inlet i is event D.Evt with { briefly "i" }
            |      outlet o is event D.Evt with { briefly "o" }
            |      handler h is { on event D.Evt { send event D.Evt to outlet o } } with { briefly "h" }
            |    } with { briefly "relay" }""".stripMargin,
          """    connector c1 is { from outlet C.Src.o to inlet C.Relay.i } with { briefly "c" }""".stripMargin
        ),
        "tail-relay"
      )
      noPath(msgs).map(_.message) must contain(
        "Source 'Src' is a source but has no downstream path to any sink"
      )
    }

    "NOT be a processor that forwards the handled message" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    streamlet CmdSrc as source is {
            |      outlet o is command D.Cmd with { briefly "o" }
            |    } with { briefly "src" }
            |    streamlet Relay as flow is {
            |      inlet i is command D.Cmd with { briefly "i" }
            |      outlet o is command D.Cmd with { briefly "o" }
            |      handler h is { on command D.Cmd { forward command D.Cmd to outlet o } } with { briefly "h" }
            |    } with { briefly "relay" }""".stripMargin,
          """    connector c1 is { from outlet C.CmdSrc.o to inlet C.Relay.i } with { briefly "c" }""".stripMargin
        ),
        "tail-forward"
      )
      noPath(msgs).map(_.message) must contain(
        "Source 'CmdSrc' is a source but has no downstream path to any sink"
      )
    }

    "be a processor whose `on other` clause consumes everything" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    streamlet Drain as flow is {
            |      inlet i is event D.Evt with { briefly "i" }
            |      outlet o is command D.Persist with { briefly "o" }
            |      handler h is { on other { do "drain it" } } with { briefly "h" }
            |    } with { briefly "drain" }""".stripMargin,
          """    connector c1 is { from outlet C.Src.o to inlet C.Drain.i } with { briefly "c" }""".stripMargin
        ),
        "tail-on-other"
      )
      noPath(msgs) mustBe empty
    }

    "require EVERY member of an alternation inlet to be handled" in { (td: TestData) =>
      def withClauses(clauses: String): String =
        model(
          s"""    streamlet AltSrc as source is {
             |      outlet o is type D.EvtOrOther with { briefly "o" }
             |    } with { briefly "src" }
             |    streamlet Both as flow is {
             |      inlet i is type D.EvtOrOther with { briefly "i" }
             |      outlet o is command D.Persist with { briefly "o" }
             |      handler h is { $clauses } with { briefly "h" }
             |    } with { briefly "both" }""".stripMargin,
          """    connector c1 is { from outlet C.AltSrc.o to inlet C.Both.i } with { briefly "c" }""".stripMargin
        )
      val onlyOne = diagnostics(withClauses("on event D.Evt { do \"one\" }"), "alt-one")
      noPath(onlyOne).map(_.message) must contain(
        "Source 'AltSrc' is a source but has no downstream path to any sink"
      )
      val both = diagnostics(
        withClauses("on event D.Evt { do \"one\" } on event D.Other { do \"two\" }"),
        "alt-both"
      )
      noPath(both) mustBe empty
    }

    "give a handler-less processor the benefit of the doubt: a tail only if it has no outlets" in {
      (td: TestData) =>
        // Ports-only sink: nothing says it passes anything on, and it cannot. A tail.
        val bareSink = diagnostics(
          model(
            """    streamlet Bare as sink is {
              |      inlet i is event D.Evt with { briefly "i" }
              |    } with { briefly "bare" }""".stripMargin,
            """    connector c1 is { from outlet C.Src.o to inlet C.Bare.i } with { briefly "c" }""".stripMargin
          ),
          "bare-sink"
        )
        noPath(bareSink) mustBe empty
        // Ports-only flow: opaque, assumed to pass through; its outlet leads nowhere, so no tail.
        val bareFlow = diagnostics(
          model(
            """    streamlet Pass as flow is {
              |      inlet i is event D.Evt with { briefly "i" }
              |      outlet o is event D.Evt with { briefly "o" }
              |    } with { briefly "pass" }""".stripMargin,
            """    connector c1 is { from outlet C.Src.o to inlet C.Pass.i } with { briefly "c" }""".stripMargin
          ),
          "bare-flow"
        )
        noPath(bareFlow).map(_.message) must contain(
          "Source 'Src' is a source but has no downstream path to any sink"
        )
    }
  }

  "a stream graph" should {

    "report an Error when connectors of one message type form a cycle" in { (td: TestData) =>
      val relay = (name: String) =>
        s"""    streamlet $name as flow is {
           |      inlet i is event D.Evt with { briefly "i" }
           |      outlet o is event D.Evt with { briefly "o" }
           |      handler h is { on event D.Evt { send event D.Evt to outlet o } } with { briefly "h" }
           |    } with { briefly "$name" }""".stripMargin
      val msgs = diagnostics(
        model(
          relay("A") + "\n" + relay("B"),
          """    connector c1 is { from outlet C.Src.o to inlet C.A.i } with { briefly "c" }
            |    connector c2 is { from outlet C.A.o to inlet C.B.i } with { briefly "c" }
            |    connector c3 is { from outlet C.B.o to inlet C.A.i } with { briefly "c" }""".stripMargin
        ),
        "cycle"
      )
      val found = cycles(msgs)
      found must not be empty
      found.head.kind mustBe Messages.Error
      found.head.message must include("cycle")
      found.head.message must include("'A'")
      found.head.message must include("'B'")
    }

    "report a self-loop as a cycle" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    streamlet Loop as flow is {
            |      inlet i is event D.Evt with { briefly "i" }
            |      outlet o is event D.Evt with { briefly "o" }
            |      handler h is { on event D.Evt { send event D.Evt to outlet o } } with { briefly "h" }
            |    } with { briefly "loop" }""".stripMargin,
          """    connector c1 is { from outlet C.Src.o to inlet C.Loop.i } with { briefly "c" }
            |    connector c2 is { from outlet C.Loop.o to inlet C.Loop.i } with { briefly "c" }""".stripMargin
        ),
        "self-loop"
      )
      cycles(msgs) must not be empty
    }

    "NOT call a request/response pair a cycle — the two directions carry different types" in {
      (td: TestData) =>
        // A sends Cmd to B; B answers with Evt back to A. Each type's chain has a start and a
        // finish; nothing circulates.
        val msgs = diagnostics(
          model(
            """    streamlet A as flow is {
              |      inlet i is event D.Evt with { briefly "i" }
              |      outlet o is command D.Cmd with { briefly "o" }
              |      handler h is { on event D.Evt { do "note it" } } with { briefly "h" }
              |    } with { briefly "a" }
              |    streamlet B as flow is {
              |      inlet i is command D.Cmd with { briefly "i" }
              |      outlet o is event D.Evt with { briefly "o" }
              |      handler h is { on command D.Cmd { do "act" } } with { briefly "h" }
              |    } with { briefly "b" }""".stripMargin,
            """    connector c1 is { from outlet C.Src.o to inlet C.A.i } with { briefly "c" }
              |    connector c2 is { from outlet C.A.o to inlet C.B.i } with { briefly "c" }
              |    connector c3 is { from outlet C.B.o to inlet C.A.i } with { briefly "c" }""".stripMargin
          ),
          "request-response"
        )
        cycles(msgs) mustBe empty
    }
  }
}
