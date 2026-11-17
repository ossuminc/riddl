/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Message
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, Riddl}
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** 2.8.0 (Reid, 2026-11-17): `on init` is CREATION. It belongs to the entity, runs once, and
  * `morph`s into the state the entity starts in; there is no initial state. For an event-sourced
  * entity the `on init` invocation is the first journal entry and replay re-runs it, so it may
  * mutate and may not read the clock (riddl-models' `on-init-is-the-creation-event` task).
  */
class EntityCreationTest extends AbstractValidatingTest {

  private def messages(src: String, td: TestData): Seq[Message] =
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default) { _ =>
      out = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Right(result) => result.messages
        case Left(msgs)    => msgs
    }
    out

  private def of(msgs: Seq[Message], rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    Pass.runThesePasses(PassInput(root), creators).outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  /** riddl-models' acceptance snippet, reduced: an event-sourced entity created into `Draft`. */
  private def tableOrder(initBody: String, eventSourced: Boolean = true): String =
    s"""domain D is { context C is {
       |  type TableNumber is Integer
       |  record TableOrderData is { tableOrderId: Id(C.TableOrder), tableNumber: TableNumber,
       |    serverId: String, createdAt: TimeStamp }
       |  ${if eventSourced then "event-sourced " else ""}entity TableOrder is {
       |    handler Lifecycle is {
       |      on init(atTable: TableNumber, byServer: String, at: TimeStamp) is {
       |        $initBody
       |      }
       |    }
       |    state Draft of record C.TableOrderData
       |    state Submitted of record C.TableOrderData
       |  }
       |} }
       |""".stripMargin

  private val morphToDraft =
    "morph entity C.TableOrder to state C.TableOrder.Draft with record C.TableOrderData(" +
      "tableOrderId = self.id, tableNumber = atTable, serverId = byServer, createdAt = at)"

  "An event-sourced entity's on init" should {
    "may morph: the invocation is the first journal entry (the acceptance snippet)" in {
      (td: TestData) =>
        val msgs = messages(tableOrder(morphToDraft), td)
        of(msgs, RuleId.EventSourcedMutationScope) mustBe empty
        of(msgs, RuleId.EventSourcedFoldNondeterministic) mustBe empty
        of(msgs, RuleId.InitNoMorph) mustBe empty
        of(msgs, RuleId.MorphSingleState) mustBe empty
    }

    "not read system.now, since replay re-runs it" in { (td: TestData) =>
      val body = morphToDraft.replace("createdAt = at", "createdAt = system.now")
      val errs = of(messages(tableOrder(body), td), RuleId.EventSourcedFoldNondeterministic)
      errs.size mustBe 1
      errs.head.kind mustBe Messages.Error
      errs.head.message must include("runs again on every replay")
    }

    "still keep a mutation out of a command clause (R3 negative control)" in { (td: TestData) =>
      val src = tableOrder(morphToDraft).replace(
        "    handler Lifecycle is {\n",
        "    handler Lifecycle is {\n      on command C.Go is { " +
          "morph entity C.TableOrder to state C.TableOrder.Submitted with record C.TableOrderData(" +
          "tableOrderId = self.id, tableNumber = 1, serverId = \"s\", createdAt = system.now) }\n"
      ).replace("  type TableNumber is Integer\n", "  type TableNumber is Integer\n  command Go is { x: Integer }\n")
      of(messages(src, td), RuleId.EventSourcedMutationScope).size mustBe 1
    }
  }

  "Creation" should {
    "deprecate an entity with states whose on init does not morph" in { (td: TestData) =>
      val deps = of(messages(tableOrder("do \"nothing\"", eventSourced = false), td), RuleId.InitNoMorph)
      deps.size mustBe 1
      deps.head.kind mustBe Messages.Deprecation
      deps.head.message must include("State 'Draft'")
      deps.head.message must include("declared first")
    }

    "exempt a stateless entity" in { (td: TestData) =>
      val src =
        """domain D is { context C is {
          |  entity E is { handler H is { on init is { do "start" } } }
          |} }
          |""".stripMargin
      of(messages(src, td), RuleId.InitNoMorph) mustBe empty
    }

    "deprecate an on init inside a state, noting a non-initial one never runs" in { (td: TestData) =>
      val src =
        """domain D is { context C is {
          |  record R is { n: Integer }
          |  entity E is {
          |    state A of record C.R is { handler HA is { on init is { set field E.A.n to 1 } } }
          |    state B of record C.R is { handler HB is { on init is { set field E.B.n to 2 } } }
          |  }
          |} }
          |""".stripMargin
      val deps = of(messages(src, td), RuleId.InitInState)
      deps.size mustBe 2
      deps.exists(_.message.contains("never runs")) mustBe true
    }

    "deprecate a written `initial state` at parse time" in { (td: TestData) =>
      val src =
        """domain D is { context C is {
          |  record R is { n: Integer }
          |  entity E is { state A of record C.R  initial state B of record C.R }
          |} }
          |""".stripMargin
      TopLevelParser.parseInputWithMessages(RiddlParserInput(src, td)) match
        case Left(msgs) => fail(msgs.format)
        case Right((_, msgs)) =>
          msgs.filter(_.ruleId.contains(RuleId.InitialStateMarker)).size mustBe 1
    }
  }

  "The initial-state default" should {
    "not apply when on init morphs, and round-trip with no `initial` anywhere" in { (td: TestData) =>
      val root = parse(tableOrder(morphToDraft), "src")
      Finder(root).recursiveFindByType[State].exists(_.isInitial) mustBe false
      val pretty = prettify(root)
      pretty must not include ("initial state")
      Finder(parse(pretty, "regen")).recursiveFindByType[State].exists(_.isInitial) mustBe false
    }

    "still apply to an old-style model, and prettify must not write the redundant marker" in {
      (td: TestData) =>
        val root = parse(tableOrder("do \"nothing\"", eventSourced = false), "src")
        Finder(root).recursiveFindByType[State].filter(_.isInitial).map(_.id.value) mustBe Seq("Draft")
        val pretty = prettify(root)
        pretty must not include ("initial state")
        val again = Finder(parse(pretty, "regen")).recursiveFindByType[State]
        again.filter(_.isInitial).map(_.id.value) mustBe Seq("Draft")
    }

    "keep an explicit `initial` on a non-first state through a round trip" in { (td: TestData) =>
      val src =
        """domain D is { context C is {
          |  record R is { n: Integer }
          |  entity E is { state A of record C.R  initial state B of record C.R }
          |} }
          |""".stripMargin
      val pretty = prettify(parse(src, "src"))
      pretty must include("initial state B")
      Finder(parse(pretty, "regen")).recursiveFindByType[State].filter(_.isInitial).map(_.id.value) mustBe
        Seq("B")
    }
  }
}
