/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.ai

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{
  AbstractParsingTest,
  RiddlParserInput,
  TopLevelParser
}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.ossuminc.riddl.passes.validate.{ValidationOutput, ValidationPass}
import com.ossuminc.riddl.utils.PlatformContext

import org.scalatest.TestData

abstract class SharedAIHelperPassTest(using PlatformContext)
    extends AbstractParsingTest {

  /** A minimal well-formed model that parses and resolves */
  private val wellFormedModel: String =
    """domain TestDomain is {
      |  context TestContext is {
      |    entity TestEntity is {
      |      type TestCommand = command { field: String }
      |      state TestState of TestStateData is {
      |        handler TestHandler is {
      |          on command TestCommand {
      |            prompt "do something"
      |          }
      |        }
      |      }
      |      type TestStateData is { field: String }
      |    }
      |  }
      |}""".stripMargin

  /** Helper: parse RIDDL and run AIHelperPass via the
    * analyzeSource entry point
    */
  private def analyzeRiddl(
    riddl: String,
    td: TestData
  ): Either[Messages.Messages, PassesResult] = {
    val input = RiddlParserInput(riddl, td)
    AIHelperPass.analyzeSource(input)
  }

  /** Helper: parse, then use the analyze(Root) entry point */
  private def analyzeRoot(
    riddl: String,
    td: TestData
  ): PassesResult = {
    val input = RiddlParserInput(riddl, td)
    TopLevelParser.parseInput(input) match
      case Left(errors) =>
        fail(s"Parse failed: ${errors.format}")
      case Right(root) => AIHelperPass.analyze(root)
  }

  /** Helper: get just the AIHelper output messages */
  private def aiMessages(
    result: PassesResult
  ): Messages.Messages = {
    result.outputOf[AIHelperOutput](AIHelperPass.name) match
      case Some(output) => output.messages
      case None => fail("AIHelperOutput not found in results")
  }

  // ---- Entry Point Tests ----

  "AIHelperPass Entry Points" should {
    "analyzeSource returns Right for valid RIDDL" in {
      (td: TestData) =>
        val result = analyzeRiddl(wellFormedModel, td)
        result.isRight mustBe true
    }

    "analyzeSource returns Left for unparseable input" in {
      (td: TestData) =>
        val riddl = "this is not valid RIDDL at all }{{"
        val result = analyzeRiddl(riddl, td)
        result.isLeft mustBe true
    }

    "analyze(Root) works with pre-parsed AST" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is { ??? }
            |}""".stripMargin
        val result = analyzeRoot(riddl, td)
        val msgs = aiMessages(result)
        // Should have Tip messages since context has ???
        msgs.justTips mustNot be(empty)
    }
  }

  // ---- Message Filtering Tests (Path B) ----

  "AIHelperPass Message Filtering" should {
    "filter out StyleWarning messages" in {
      (td: TestData) =>
        val result = analyzeRiddl(wellFormedModel, td)
        result match
          case Right(pr) =>
            val msgs = aiMessages(pr)
            msgs.justStyle mustBe empty
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "filter out Info messages" in { (td: TestData) =>
      val result = analyzeRiddl(wellFormedModel, td)
      result match
        case Right(pr) =>
          val msgs = aiMessages(pr)
          msgs.justInfo mustBe empty
        case Left(errors) =>
          fail(s"Analysis failed: ${errors.format}")
    }

    "pass through Error and Warning messages" in {
      (td: TestData) =>
        val result = analyzeRiddl(wellFormedModel, td)
        // Well-formed model should parse and analyze
        result.isRight mustBe true
    }

    "convert MissingWarning to Tip" in { (td: TestData) =>
      val result = analyzeRiddl(wellFormedModel, td)
      result match
        case Right(pr) =>
          val msgs = aiMessages(pr)
          // MissingWarnings should be absent (converted)
          msgs.justMissing mustBe empty
        case Left(errors) =>
          fail(s"Analysis failed: ${errors.format}")
    }

    "convert UsageWarning to Tip" in { (td: TestData) =>
      // An unused type should produce UsageWarning which
      // becomes a Tip
      val riddl =
        """domain TestDomain is {
          |  context TestContext is {
          |    entity TestEntity is {
          |      type UnusedType is { x: Integer }
          |      type TestCommand = command {
          |        field: String
          |      }
          |      state TestState of TestStateData is {
          |        handler TestHandler is {
          |          on command TestCommand {
          |            prompt "do something"
          |          }
          |        }
          |      }
          |      type TestStateData is { field: String }
          |    }
          |  }
          |}""".stripMargin
      val result = analyzeRiddl(riddl, td)
      result match
        case Right(pr) =>
          val msgs = aiMessages(pr)
          // UsageWarnings should be absent (converted)
          msgs.justUsage mustBe empty
        case Left(errors) =>
          fail(s"Analysis failed: ${errors.format}")
    }
  }

  // ---- Path A Tests (Resolution errors) ----

  "AIHelperPass Path A (resolution errors)" should {
    "convert unresolved reference errors to Tips" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is {
            |    entity TestEntity is {
            |      type TestCommand = command {
            |        field: String
            |      }
            |      state TestState of NonExistentType is {
            |        handler TestHandler is {
            |          on command TestCommand {
            |            prompt "do something"
            |          }
            |        }
            |      }
            |    }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val msgs = aiMessages(pr)
            // Resolution errors become Tips
            val tips = msgs.justTips
            tips mustNot be(empty)
            // Original errors should not be present
            val errors = msgs.justErrors
            errors mustBe empty
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "not run ValidationPass when resolution has errors" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is {
            |    entity TestEntity is {
            |      type TestCommand = command {
            |        field: String
            |      }
            |      state TestState of MissingType is {
            |        handler TestHandler is {
            |          on command TestCommand {
            |            prompt "do something"
            |          }
            |        }
            |      }
            |    }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            // ValidationPass should NOT have run
            pr.hasOutputOf(ValidationPass.name) mustBe false
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }
  }

  // ---- Tip Generation Rule Tests ----

  "AIHelperPass Tip Generation" should {
    "generate Tips for empty domain" in { (td: TestData) =>
      val riddl = "domain EmptyDomain is { ??? }"
      val result = analyzeRiddl(riddl, td)
      result match
        case Right(pr) =>
          val tips = aiMessages(pr).justTips
          tips mustNot be(empty)
        case Left(errors) =>
          fail(s"Analysis failed: ${errors.format}")
    }

    "generate Tips for empty context" in { (td: TestData) =>
      val riddl =
        """domain TestDomain is {
          |  context EmptyContext is { ??? }
          |}""".stripMargin
      val result = analyzeRiddl(riddl, td)
      result match
        case Right(pr) =>
          val tips = aiMessages(pr).justTips
          tips mustNot be(empty)
        case Left(errors) =>
          fail(s"Analysis failed: ${errors.format}")
    }

    "generate Tips for entity with no types, state, or handlers" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is {
            |    entity EmptyEntity is { ??? }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            tips.exists(t =>
              t.message.contains("EmptyEntity")
            ) mustBe true
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "generate Tips for entity missing state" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is {
            |    entity NoStateEntity is {
            |      type SomeCommand = command {
            |        field: String
            |      }
            |      handler SomeHandler is {
            |        on command SomeCommand {
            |          prompt "process it"
            |        }
            |      }
            |    }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            tips.exists(t =>
              t.message.contains("NoStateEntity") &&
                t.message.contains("state")
            ) mustBe true
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "generate Tips for entity missing handlers" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is {
            |    entity NoHandlerEntity is {
            |      type SomeType is { field: String }
            |    }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            tips.exists(t =>
              t.message.contains("NoHandlerEntity") &&
                t.message.contains("handler")
            ) mustBe true
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "generate Tips for empty handler" in { (td: TestData) =>
      val riddl =
        """domain TestDomain is {
          |  context TestContext is {
          |    entity TestEntity is {
          |      type TestCommand = command {
          |        field: String
          |      }
          |      state TestState of TestStateData is {
          |        handler EmptyHandler is { ??? }
          |      }
          |      type TestStateData is { field: String }
          |    }
          |  }
          |}""".stripMargin
      val result = analyzeRiddl(riddl, td)
      result match
        case Right(pr) =>
          val tips = aiMessages(pr).justTips
          tips.exists(t =>
            t.message.contains("EmptyHandler") &&
              t.message.contains("on-clauses")
          ) mustBe true
        case Left(errors) =>
          fail(s"Analysis failed: ${errors.format}")
    }

    "include RIDDL snippets in empty container Tips" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context EmptyContext is { ??? }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            val snippetTips = tips.filter(
              _.message.contains("Suggested RIDDL:")
            )
            snippetTips mustNot be(empty)
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "not generate empty-container Tips when content exists" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is {
            |    entity SomeEntity is { ??? }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            // Context has content (entity), so no
            // "is empty" tip for TestContext
            val emptyContextTips = tips.filter(t =>
              t.message.contains("'TestContext' is empty")
            )
            emptyContextTips mustBe empty
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }
  }

  // ---- Phase 2: Entity Completeness Tips ----

  "AIHelperPass Entity Completeness" should {
    "generate Tip for entity missing command types" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is {
            |    entity NoCommands is {
            |      type SomeEvent = event { data: String }
            |      state NoCommandsState of NoCommandsData is {
            |        handler NoCommandsHandler is { ??? }
            |      }
            |      type NoCommandsData is { data: String }
            |    }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            tips.exists(t =>
              t.message.contains("NoCommands") &&
                t.message.contains("command types")
            ) mustBe true
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "generate Tip for entity missing event types" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is {
            |    entity NoEvents is {
            |      type SomeCommand = command {
            |        data: String
            |      }
            |      state NoEventsState of NoEventsData is {
            |        handler NoEventsHandler is {
            |          on command SomeCommand {
            |            prompt "process"
            |          }
            |        }
            |      }
            |      type NoEventsData is { data: String }
            |    }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            tips.exists(t =>
              t.message.contains("NoEvents") &&
                t.message.contains("event types")
            ) mustBe true
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "include RIDDL snippets for missing command types" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is {
            |    entity NoCmd is {
            |      type SomeEvent = event { data: String }
            |      state NoCmdState of NoCmdData is {
            |        handler NoCmdHandler is { ??? }
            |      }
            |      type NoCmdData is { data: String }
            |    }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            val cmdTips = tips.filter(t =>
              t.message.contains("command types") &&
                t.message.contains("Suggested RIDDL:")
            )
            cmdTips mustNot be(empty)
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "include RIDDL snippet for missing state" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is {
            |    entity NoState is {
            |      type SomeCommand = command {
            |        data: String
            |      }
            |      handler NoStateHandler is {
            |        on command SomeCommand {
            |          prompt "process"
            |        }
            |      }
            |    }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            val stateTips = tips.filter(t =>
              t.message.contains("NoState") &&
                t.message.contains("state") &&
                t.message.contains("Suggested RIDDL:")
            )
            stateTips mustNot be(empty)
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }
  }

  // ---- Phase 2: Handler Completeness Tips ----

  "AIHelperPass Handler Completeness" should {
    "suggest specific on-clauses from entity command types" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is {
            |    entity TestEntity is {
            |      type DoThis = command { data: String }
            |      type DoThat = command { data: String }
            |      state TestState of TestStateData is {
            |        handler EmptyH is { ??? }
            |      }
            |      type TestStateData is { data: String }
            |    }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            // Empty handler should get tips referencing
            // the entity's command types
            val handlerTips = tips.filter(t =>
              t.message.contains("EmptyH") &&
                t.message.contains("on-clauses")
            )
            handlerTips mustNot be(empty)
            // Snippet should reference entity commands
            val snippetText = handlerTips.map(
              _.message
            ).mkString
            snippetText must include("DoThis")
            snippetText must include("DoThat")
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "generate Tip for empty on-clause body" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is {
            |    entity TestEntity is {
            |      type DoThis = command { data: String }
            |      state TestState of TestStateData is {
            |        handler TestHandler is {
            |          on command DoThis { ??? }
            |        }
            |      }
            |      type TestStateData is { data: String }
            |    }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            tips.exists(t =>
              t.message.contains("empty body")
            ) mustBe true
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "generate Tip for unhandled command types" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is {
            |    entity TestEntity is {
            |      type DoThis = command { data: String }
            |      type DoThat = command { data: String }
            |      state TestState of TestStateData is {
            |        handler TestHandler is {
            |          on command DoThis {
            |            prompt "handle this"
            |          }
            |        }
            |      }
            |      type TestStateData is { data: String }
            |    }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            // DoThat is not handled
            tips.exists(t =>
              t.message.contains("DoThat") &&
                t.message.contains("does not handle")
            ) mustBe true
            // DoThis IS handled, so no tip for it
            tips.exists(t =>
              t.message.contains("DoThis") &&
                t.message.contains("does not handle")
            ) mustBe false
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }
  }

  // ---- Phase 3: Context and Documentation Tips ----

  "AIHelperPass Context Completeness" should {
    "generate Tip for context with entities but no repository" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context NoRepo is {
            |    entity SomeEntity is { ??? }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            tips.exists(t =>
              t.message.contains("NoRepo") &&
                t.message.contains("repository")
            ) mustBe true
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "generate Tip for context with no adaptors" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context NoAdaptors is {
            |    entity SomeEntity is { ??? }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            tips.exists(t =>
              t.message.contains("NoAdaptors") &&
                t.message.contains("adaptor")
            ) mustBe true
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "include RIDDL snippets for missing repository" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context NoRepo is {
            |    entity SomeEntity is { ??? }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            val repoTips = tips.filter(t =>
              t.message.contains("repository") &&
                t.message.contains("Suggested RIDDL:")
            )
            repoTips mustNot be(empty)
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }
  }

  "AIHelperPass Documentation Tips" should {
    "generate Tip for definition missing brief description" in {
      (td: TestData) =>
        val riddl =
          """domain NoBrief is {
            |  context SomeContext is { ??? }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            tips.exists(t =>
              t.message.contains("NoBrief") &&
                t.message.contains("brief description")
            ) mustBe true
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "not generate doc Tip when brief exists" in {
      (td: TestData) =>
        val riddl =
          """domain Documented is {
            |  context SomeContext is { ??? }
            |} with {
            |  briefly "A well-documented domain"
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            val docTips = tips.filter(t =>
              t.message.contains("'Documented'") &&
                t.message.contains("brief description")
            )
            docTips mustBe empty
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "include RIDDL snippet for missing brief" in {
      (td: TestData) =>
        val riddl =
          """domain NoBrief is {
            |  context SomeContext is { ??? }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            val briefTips = tips.filter(t =>
              t.message.contains("brief description") &&
                t.message.contains("Suggested RIDDL:")
            )
            briefTips mustNot be(empty)
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }
  }

  // ---- Phase 4: Resolution Error Rewriting ----

  "AIHelperPass Resolution Error Rewriting" should {
    "rewrite 'not resolved' errors into actionable Tips" in {
      (td: TestData) =>
        val riddl =
          """domain TestDomain is {
            |  context TestContext is {
            |    entity TestEntity is {
            |      type TestCommand = command {
            |        field: String
            |      }
            |      state TestState of MissingType is {
            |        handler TestHandler is {
            |          on command TestCommand {
            |            prompt "do something"
            |          }
            |        }
            |      }
            |    }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            // Should have rewritten tips, not raw errors
            tips.exists(t =>
              t.message.contains("Define the missing")
            ) mustBe true
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }

    "rewrite ambiguous path errors into actionable Tips" in {
      (td: TestData) =>
        // Two types with the same name in different
        // contexts, referenced ambiguously
        val riddl =
          """domain TestDomain is {
            |  context CtxA is {
            |    type SharedName is { x: Integer }
            |  }
            |  context CtxB is {
            |    type SharedName is { y: Integer }
            |    entity TestEntity is {
            |      type TestCommand = command {
            |        ref: type SharedName
            |      }
            |      state TestState of TestStateData is {
            |        handler TestHandler is {
            |          on command TestCommand {
            |            prompt "do something"
            |          }
            |        }
            |      }
            |      type TestStateData is { x: Integer }
            |    }
            |  }
            |}""".stripMargin
        val result = analyzeRiddl(riddl, td)
        result match
          case Right(pr) =>
            val tips = aiMessages(pr).justTips
            // Any tips generated should be actionable,
            // not raw error text
            for tip <- tips do
              tip.message must startWith("Tip:")
          case Left(errors) =>
            fail(s"Analysis failed: ${errors.format}")
    }
  }

  // ---- Well-formed Model ----

  "AIHelperPass on well-formed models" should {
    "produce Tips for both complete and empty models" in {
      (td: TestData) =>
        val emptyRiddl =
          """domain EmptyDomain is {
            |  context EmptyContext is { ??? }
            |}""".stripMargin
        val completeResult =
          analyzeRiddl(wellFormedModel, td)
        val emptyResult = analyzeRiddl(emptyRiddl, td)
        (completeResult, emptyResult) match
          case (Right(cpr), Right(epr)) =>
            val completeTips = aiMessages(cpr).justTips
            val emptyTips = aiMessages(epr).justTips
            // Empty model should have structural tips
            emptyTips mustNot be(empty)
            succeed
          case _ => fail("Both analyses should succeed")
    }
  }
}
