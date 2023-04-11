/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.passes.PassesResult
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UsageSpec extends AnyWordSpec with Matchers {

  "Usage" should {
    "correctly associated definitions" in {
      val input = """
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
                    |      state S of E.SFields is {
                    |        handler H is {
                    |          on command di:C.DoIt {
                    |            then set S.f2 to @di.f1
                    |          }
                    |        }
                    |      }
                    |    }
                    |  }
                    |}
                    |""".stripMargin
      Riddl.parseAndValidate(RiddlParserInput(input), CommonOptions()) match {
        case Left(messages) => fail(messages.format)
        case Right(result: PassesResult) =>
          val usage = result.usage
          // ensure usedBy and uses are reflective
          usage.verifyReflective must be(true)

          // print it out for debugging
          info(
            "Uses:\n" + result.usage.usesAsString
          )
          info(
            "Used By:\n" + result.usage.usedByAsString
          )

          // Now let's make sure we get the right results, first extract
          // all the definitions
          val model = result.root
          val domain = model.domains.head
          val D_T = domain.types.find(_.id.value == "T").get
          val context = domain.contexts.head
          val DoIt = context.types.find(_.id.value == "DoIt").get.asInstanceOf[Type]
          val ref = DoIt.typ.asInstanceOf[AggregateUseCaseTypeExpression].fields.find(_.id.value == "ref").get
          val f1 = DoIt.typ.asInstanceOf[AggregateUseCaseTypeExpression].fields.find(_.id.value == "f1").get
          val C_T = context.types.find(_.id.value == "T").get
          val entityE = context.entities.head
          val SFields = entityE.types.head
          val f2 = SFields.typ.asInstanceOf[AggregateUseCaseTypeExpression].fields.find(_.id.value == "f2").get
          val f3 = SFields.typ.asInstanceOf[AggregateUseCaseTypeExpression].fields.find(_.id.value == "f3").get
          val S = entityE.states.head

          // now validate the containment hierarchy
          usage.uses(ref, entityE) must be(true)
          usage.uses(f1, C_T) must be(true)
          usage.uses(C_T, D_T) must be(true)
          usage.uses(S, SFields) must be(true)
          usage.uses(f2, D_T) must be(true)
          usage.uses(f3, C_T) must be(true)
      }
    }
    "unused entities generate a warning" in {
      val input = """domain foo {
                    |  context Bar is {
                    |    command ACommand is { ??? } described as "AC"
                    |    entity fooBar is {
                    |      record fields is { field: Number }
                    |      state AState of fooBar.fields {
                    |        handler fooBarHandlerForAState is {
                    |          on command ACommand {
                    |            ???
                    |          } described as "inconsequential"
                    |        } described as "inconsequential"
                    |      } described as "inconsequential"
                    |    } described as "unused"
                    |  } described as "inconsequential"
                    |} described as "inconsequential"
                    |""".stripMargin
      Riddl.parseAndValidate(RiddlParserInput(input), shouldFailOnError = false) match {
        case Left(messages) => fail(messages.format)
        case Right(result) =>
          info(result.messages.format)
          result.messages.hasErrors mustBe (false)
          val warnings = result.messages.justUsage
          warnings.size mustBe (2)
          val warnMessage = warnings.head.format
          warnMessage must include("Entity 'fooBar' is unused")
      }

    }
    "unused types generate a warning" in {
      val input = """domain foo {
                    |  type Bar = Number described as "consequential"
                    |} described as "inconsequential"
                    |""".stripMargin
      val options = CommonOptions(showStyleWarnings = false, showMissingWarnings = false)
      Riddl.parseAndValidate(RiddlParserInput(input), options, shouldFailOnError = false) match {
        case Left(messages) => fail(messages.format)
        case Right(result) =>
          result.messages.isOnlyIgnorable mustBe (true)
          val warnings = result.messages.justWarnings
          warnings.size mustBe (2)
          val warnMessage = warnings.head.format
          warnMessage must include("Type 'Bar' is unused")
      }
    }
  }
}
