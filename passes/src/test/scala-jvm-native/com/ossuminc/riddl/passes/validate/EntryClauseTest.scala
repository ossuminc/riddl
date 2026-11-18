/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, Messages, RuleId, toSeq}
import com.ossuminc.riddl.language.Messages.Message
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.passes.{BASTOutput, BASTWriterPass, Pass, PassInput, PassesOutput, Riddl}
import com.ossuminc.riddl.utils.{CommonOptions, PathUtils, pc}

import org.scalatest.TestData

import java.nio.file.{Files, Path}

/** `on entry` (2.8.0, Reid 2026-11-17): a state's entry action, run when a live `morph` moves the
  * entity into that state. Pins placement, the no-morph rule, `morph S -> S`, the event-sourced
  * consequence, and all three reflective surfaces (prettify, BAST; JSON is swept by the fixture
  * suites over `language/input`).
  */
class EntryClauseTest extends AbstractValidatingTest {

  private val fixture: String =
    Files.readString(Path.of("language/input/entity-creation.riddl"))

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin), true) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def messages(src: String, td: TestData): Seq[Message] =
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default) { _ =>
      out = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Right(result) => result.messages
        case Left(msgs)    => msgs
    }
    out

  private def of(msgs: Seq[Message], rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))

  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    Pass.runThesePasses(PassInput(root), creators).outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  /** The state holding each `on entry`, by name. */
  private def entryStates(root: Root): Seq[String] =
    Finder(root).recursiveFindByType[State].filter { s =>
      s.handlers.flatMap(_.clauses).exists(_.isInstanceOf[OnEntryClause])
    }.map(_.id.value)

  "The entity-creation fixture" should {
    "validate with no creation or entry findings" in { (td: TestData) =>
      val msgs = messages(fixture, td)
      withClue(msgs.map(_.format).mkString("\n")) {
        msgs.filter(_.kind == Messages.Error) mustBe empty
        for r <- Seq(RuleId.EntryMisplaced, RuleId.EntryMorphs, RuleId.MorphToCurrentState,
            RuleId.InitNoMorph, RuleId.InitInState, RuleId.MorphSingleState) do
          withClue(r.toString) { of(msgs, r) mustBe empty }
      }
    }

    "keep `on entry` in the same state through a prettify round trip" in { (td: TestData) =>
      val root = parse(fixture, "src")
      entryStates(root) mustBe Seq("Submitted")
      val pretty = prettify(root)
      pretty must include("on entry is {")
      entryStates(parse(pretty, "regen")) mustBe Seq("Submitted")
    }

    "keep `on entry` through a BAST round trip" in { (td: TestData) =>
      val bytes = Pass
        .runThesePasses(PassInput(parse(fixture, "src")), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("BASTWriterPass produced no output"))
        .bytes
      BASTReader(bytes).read() match
        case Left(msgs) => fail(s"BAST round trip failed:\n${msgs.format}")
        case Right(module) =>
          val entries = Finder(module).recursiveFindByType[OnEntryClause]
          entries.size mustBe 1
          entries.head.contents.toSeq.collect { case s: SendStatement => s }.size mustBe 1
          // and the nodes after it were not misread
          Finder(module).recursiveFindByType[MorphStatement].size mustBe 4
    }
  }

  private def oneState(stateBody: String, entityLevel: String = ""): String =
    s"""domain D is { context C is {
       |  record R is { n: Integer }
       |  command Go is { x: Integer }
       |  entity E is {
       |    handler Life is {
       |      on init is { morph entity C.E to state C.E.A with record C.R(n = 0) }
       |      $entityLevel
       |    }
       |    state A of record C.R is { $stateBody }
       |    state B of record C.R is { handler HB is { on command C.Go is { do "b" } } }
       |  }
       |} }
       |""".stripMargin

  "An `on entry` clause" should {
    "be an Error in an entity-level handler" in { (td: TestData) =>
      val errs = of(messages(oneState("handler HA is { on command C.Go is { do \"a\" } }",
        entityLevel = "on entry is { do \"nowhere\" }"), td), RuleId.EntryMisplaced)
      errs.size mustBe 1
      errs.head.message must include("no state for it to enter")
    }

    "be an Error in a non-initial handler of its state" in { (td: TestData) =>
      val body = "handler HA is { on command C.Go is { become entity C.E to handler C.E.A.HA2 } } " +
        "handler HA2 is { on entry is { do \"too late\" } }"
      val errs = of(messages(oneState(body), td), RuleId.EntryMisplaced)
      errs.size mustBe 1
      errs.head.message must include("non-initial handler")
    }

    "not morph" in { (td: TestData) =>
      val body = "handler HA is { on entry is { " +
        "morph entity C.E to state C.E.B with record C.R(n = 1) } }"
      of(messages(oneState(body), td), RuleId.EntryMorphs).size mustBe 1
    }

    "allow one per handler only" in { (td: TestData) =>
      val body = "handler HA is { on entry is { do \"one\" } on entry is { do \"two\" } }"
      messages(oneState(body), td).exists(m =>
        m.kind == Messages.Error && m.message.contains("more than one 'on entry'")
      ) mustBe true
    }

    "be a parse error outside an entity" in { (td: TestData) =>
      val src =
        """domain D is { context C is {
          |  handler H is { on entry is { do "x" } }
          |} }
          |""".stripMargin
      TopLevelParser.parseInput(RiddlParserInput(src, td)).isLeft mustBe true
    }

    "not mutate state in an event-sourced entity, since it does not run on replay" in {
      (td: TestData) =>
        val src = oneState("handler HA is { on entry is { set field C.E.A.n to 2 } }")
          .replace("entity E is", "event-sourced entity E is")
        of(messages(src, td), RuleId.EventSourcedMutationScope).size mustBe 1
    }
  }

  "A morph into the current state" should {
    "warn that it changes nothing and runs no entry" in { (td: TestData) =>
      val body = "handler HA is { on command C.Go is { " +
        "morph entity C.E to state C.E.A with record C.R(n = 1) } }"
      val warns = of(messages(oneState(body), td), RuleId.MorphToCurrentState)
      warns.size mustBe 1
      warns.head.kind mustBe Messages.Warning
    }

    "not warn for a morph into a different state (negative control)" in { (td: TestData) =>
      val body = "handler HA is { on command C.Go is { " +
        "morph entity C.E to state C.E.B with record C.R(n = 1) } }"
      of(messages(oneState(body), td), RuleId.MorphToCurrentState) mustBe empty
    }
  }
}
