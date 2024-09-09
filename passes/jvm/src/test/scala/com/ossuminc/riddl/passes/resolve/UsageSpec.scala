/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.parsing.ParsingTest
import com.ossuminc.riddl.passes.{PassesResult, Riddl}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.TestData

class UsageSpec extends ParsingTest {

  "Usage" should {
    "correctly associated definitions" in { (td: TestData) =>
      import com.ossuminc.riddl.language.Messages.Messages
      val input = RiddlParserInput(
        """
          |domain D is {
          |  type T is Number
          |  context C is {
          |    command DoIt is { ref: Id(C.E), f1: C.T }
          |    type T is D.T
          |    entity E is {
          |      record SFields is {
          |        f2: D.T,
          |        f3: C.T
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
      Riddl.parseAndValidate(input, CommonOptions()) match {
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
          val domain = model.domains.head
          val D_T = domain.types.find(_.id.value == "T").get
          val context = domain.contexts.head
          val DoIt = context.types.find(_.id.value == "DoIt").get
          val ref = DoIt.typEx.asInstanceOf[AggregateUseCaseTypeExpression].fields.find(_.id.value == "ref").get
          val f1 = DoIt.typEx.asInstanceOf[AggregateUseCaseTypeExpression].fields.find(_.id.value == "f1").get
          val C_T = context.types.find(_.id.value == "T").get
          val entityE = context.entities.head
          val SFields = entityE.types.head
          val f2 = SFields.typEx.asInstanceOf[AggregateUseCaseTypeExpression].fields.find(_.id.value == "f2").get
          val f3 = SFields.typEx.asInstanceOf[AggregateUseCaseTypeExpression].fields.find(_.id.value == "f3").get
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
        case Left(messages) =>
          fail(messages.format)
        case Right(result) =>
          info(result.messages.format)
          result.messages.hasErrors must be(false)
          val warnings = result.messages.justUsage
          warnings.size mustBe 2
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
      Riddl.parseAndValidate(input, options, shouldFailOnError = false) match {
        case Left(messages) => fail(messages.format)
        case Right(result) =>
          result.messages.isOnlyIgnorable mustBe true
          val warnings = result.messages.justWarnings
          warnings.size mustBe 2
          val warnMessage = warnings.last.format
          warnMessage must include("Type 'Bar' is unused")
      }
    }
  }
}
