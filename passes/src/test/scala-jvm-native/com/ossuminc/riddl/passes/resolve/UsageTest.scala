/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.parsing.ParsingTest
import com.ossuminc.riddl.passes.{PassesResult, Riddl}
import com.ossuminc.riddl.utils.CommonOptions
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

class UsageTest extends ParsingTest {

  "Usage" should {
    "correctly associated definitions" in { (td: TestData) =>
      val input = RiddlParserInput(
        """
          |domain D is {
          |  type T is Number
          |  context C is {
          |    command DoIt is { ref: Id(C.E), f1: C.DT }
          |    type DT is D.T
          |    entity E is {
          |      record SFields is {
          |        f2: D.T,
          |        f3: C.DT
          |      }
          |      state S of E.SFields
          |      handler H is {
          |        on command C.DoIt from di: context C{
          |          set field S.f2 to "field di.f1"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      Riddl.parseAndValidate(input) match {
        case Left(messages: Messages) => fail(messages.format)
        case Right(result: PassesResult) =>
          val usage = result.usage
          // ensure usedBy and uses are reflective
          usage.verifyReflective must be(true)

          // print it out for debugging
          info("Uses:\n" + result.usage.usesAsString)
          info("Used By:\n" + result.usage.usedByAsString)

          // Now let's make sure we get the right results, first extract
          // all the definitions
          val model = result.root
          val domain = model.contents.filter[Domain].head
          val D_T = domain.types.find(_.id.value == "T").get
          val context = domain.contexts.head
          val DoIt = context.types.find(_.id.value == "DoIt").get
          val ref = DoIt.typEx
            .asInstanceOf[AggregateUseCaseTypeExpression]
            .fields
            .find(_.id.value == "ref")
            .get
          val f1 = DoIt.typEx
            .asInstanceOf[AggregateUseCaseTypeExpression]
            .fields
            .find(_.id.value == "f1")
            .get
          val C_T = context.types.find(_.id.value == "DT").get
          val entityE = context.entities.head
          val SFields = entityE.types.head
          val f2 = SFields.typEx
            .asInstanceOf[AggregateUseCaseTypeExpression]
            .fields
            .find(_.id.value == "f2")
            .get
          val f3 = SFields.typEx
            .asInstanceOf[AggregateUseCaseTypeExpression]
            .fields
            .find(_.id.value == "f3")
            .get
          val S = entityE.states.head
          val omc = entityE.handlers.head.clauses.head

          // now validate the containment hierarchy
          usage.uses(f1, C_T) must be(true)
          usage.uses(C_T, D_T) must be(true)
          usage.uses(f2, D_T) must be(true)
          usage.uses(f3, C_T) must be(true)
          usage.uses(omc, DoIt) must be(true)
          usage.uses(S, SFields) must be(true)
          usage.uses(ref, entityE) must be(true)
      }
    }
    "unused entities generate a warning" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo {
          |  context Bar is {
          |    command ACommand is { ??? } with { described as "AC" }
          |    entity fooBar is {
          |      record fields is { field: Number }
          |      state AState of fooBar.fields
          |      handler fooBarHandlerForAState is {
          |        on command ACommand {
          |          ???
          |        } with { described as "inconsequential" }
          |      } with { described as "inconsequential"  }
          |    } with { described as "unused" }
          |  } with { described as "inconsequential" }
          |} with { described as "inconsequential" }
          |""".stripMargin,
        td
      )
      Riddl.parseAndValidate(input, shouldFailOnError = false) match {
        case Left(messages: Messages) =>
          fail(messages.format)
        case Right(result) =>
          info(result.messages.format)
          result.messages.hasErrors must be(false)
          val warnings = result.messages.justUsage
          warnings.size mustBe 1
          val warnMessage = warnings.last.format
          warnMessage must include("Entity 'fooBar' is unused")
      }

    }
    "unused types generate a warning" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo {
          |  type Bar = Number with { described as "consequential" }
          |
          |} with { described as "inconsequential"}
          |""".stripMargin,
        td
      )
      val options = CommonOptions(showStyleWarnings = false, showMissingWarnings = false)
      Riddl.parseAndValidate(input, shouldFailOnError = false) match {
        case Left(messages) => fail(messages.format)
        case Right(result) =>
          result.messages.isOnlyIgnorable mustBe true
          val warnings = result.messages.justWarnings
          warnings.size mustBe 1
          val warnMessage = warnings.last.format
          warnMessage must include("Type 'Bar' is unused")
      }
    }
    // Path-identifier usage tracking and the "only used in path"
    // CompletenessWarning. See plan: validated-discovering-rivest.md
    "record referenced only via path identifier emits CompletenessWarning, not 'is unused'" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain D is {
            |  context C is {
            |    record R is { f: Number }
            |    command DoIt is { ??? }
            |    entity E is {
            |      record EState is { x: Number }
            |      state S of E.EState
            |      handler H is {
            |        on command DoIt {
            |          set field R.f to "1"
            |        } with { described as "uses R only via path" }
            |      } with { described as "h" }
            |    } with { described as "e" }
            |  } with { described as "c" }
            |} with { described as "d" }
            |""".stripMargin,
          td
        )
        Riddl.parseAndValidate(input, shouldFailOnError = false) match {
          case Left(messages) => fail(messages.format)
          case Right(result) =>
            val model = result.root
            val R = model.contents
              .filter[Domain]
              .head
              .contexts
              .head
              .types
              .find(_.id.value == "R")
              .get

            // No "is unused" for R
            result.messages.justUsage.exists(_.format.contains("'R' is unused")) mustBe false

            // CompletenessWarning naming R, with the new message
            val completenessAboutR = result.messages.filter(m =>
              m.isCompleteness && m.format.contains("'R'") &&
                m.format.contains("only referenced in path identifiers")
            )
            completenessAboutR.size mustBe 1

            // Usage map reflects the path-only state
            result.usage.isUsedInPath(R) mustBe true
            result.usage.getUsers(R) mustBe empty
        }
    }
    "record used as the type of a field emits no path-only warning" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    record R is { f: Number }
          |    command DoIt is { ??? }
          |    entity E is {
          |      record EState is { r: R }
          |      state S of E.EState
          |      handler H is {
          |        on command DoIt { ??? }
          |      } with { described as "h" }
          |    } with { described as "e" }
          |  } with { described as "c" }
          |} with { described as "d" }
          |""".stripMargin,
        td
      )
      Riddl.parseAndValidate(input, shouldFailOnError = false) match {
        case Left(messages) => fail(messages.format)
        case Right(result) =>
          val model = result.root
          val R = model.contents
            .filter[Domain]
            .head
            .contexts
            .head
            .types
            .find(_.id.value == "R")
            .get

          // No "only referenced in path identifiers" for R
          result.messages.exists(m =>
            m.isCompleteness && m.format.contains("'R'") &&
              m.format.contains("only referenced in path identifiers")
          ) mustBe false

          // No "is unused" for R
          result.messages.justUsage.exists(_.format.contains("'R' is unused")) mustBe false

          // R is directly used (by the field `r: R`)
          result.usage.getUsers(R).nonEmpty mustBe true
      }
    }
    "record used both as a field's type and via path emits no path-only warning" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain D is {
            |  context C is {
            |    record R is { f: Number }
            |    command DoIt is { ??? }
            |    entity E is {
            |      record EState is { r: R }
            |      state S of E.EState
            |      handler H is {
            |        on command DoIt {
            |          set field R.f to "1"
            |        } with { described as "path use" }
            |      } with { described as "h" }
            |    } with { described as "e" }
            |  } with { described as "c" }
            |} with { described as "d" }
            |""".stripMargin,
          td
        )
        Riddl.parseAndValidate(input, shouldFailOnError = false) match {
          case Left(messages) => fail(messages.format)
          case Right(result) =>
            val model = result.root
            val R = model.contents
              .filter[Domain]
              .head
              .contexts
              .head
              .types
              .find(_.id.value == "R")
              .get

            // No "only referenced in path identifiers" warning for R
            result.messages.exists(m =>
              m.isCompleteness && m.format.contains("'R'") &&
                m.format.contains("only referenced in path identifiers")
            ) mustBe false

            // Both direct and path usage are recorded
            result.usage.getUsers(R).nonEmpty mustBe true
            result.usage.isUsedInPath(R) mustBe true
        }
    }
    "path-only completeness warning is scoped to Types, not Entities" in { (td: TestData) =>
      // E is referenced via path in `tell command E.DoIt to entity Other`,
      // making E an intermediate path component. It has no other direct
      // usage as a type (Entities never are typed-into-fields anyway), so
      // the path-only warning would fire if it were not Types-scoped.
      val input = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    entity E is {
          |      record EState is { x: Number }
          |      state S of E.EState
          |      command DoIt is { ??? }
          |      handler H is {
          |        on command DoIt { ??? }
          |      } with { described as "h" }
          |    } with { described as "e" }
          |    entity Other is {
          |      record OState is { y: Number }
          |      state S2 of Other.OState
          |      handler H2 is {
          |        on command E.DoIt {
          |          ???
          |        } with { described as "uses E via path" }
          |      } with { described as "h2" }
          |    } with { described as "other" }
          |  } with { described as "c" }
          |} with { described as "d" }
          |""".stripMargin,
        td
      )
      Riddl.parseAndValidate(input, shouldFailOnError = false) match {
        case Left(messages) => fail(messages.format)
        case Right(result)  =>
          // No "only referenced in path identifiers" warning for E (or any
          // non-Type definition).
          result.messages.exists(m =>
            m.isCompleteness && m.format.contains("'E'") &&
              m.format.contains("only referenced in path identifiers")
          ) mustBe false
      }
    }
    "is unused still fires for a type with no references of any kind" in { (td: TestData) =>
      // Regression check: existing semantics preserved when there are
      // neither direct nor path-identifier references.
      val input = RiddlParserInput(
        """domain D is {
          |  type Orphan = Number with { described as "untouched" }
          |} with { described as "d" }
          |""".stripMargin,
        td
      )
      Riddl.parseAndValidate(input, shouldFailOnError = false) match {
        case Left(messages) => fail(messages.format)
        case Right(result) =>
          val unused = result.messages.justUsage.filter(_.format.contains("'Orphan' is unused"))
          unused.size mustBe 1
          // And no spurious completeness warning, since it's not path-used.
          result.messages.exists(m =>
            m.isCompleteness && m.format.contains("'Orphan'") &&
              m.format.contains("only referenced in path identifiers")
          ) mustBe false
      }
    }
  }
}
