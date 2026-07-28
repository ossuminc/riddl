/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

/** Validation tests for the consolidated option registry.
  *
  * These lock down the outcome that the registry consolidation exists to guarantee: an option that
  * RIDDL publishes as valid for a definition kind must not draw a "not a recognized RIDDL option"
  * style warning when used there.
  */
trait SharedOptionValidationTest(using PlatformContext) extends AbstractValidatingTest {

  private def unrecognizedOptionMessages(messages: Messages.Messages): Seq[String] =
    messages.map(_.message).filter(_.contains("is not a recognized RIDDL option"))

  private def misplacedOptionMessages(messages: Messages.Messages): Seq[String] =
    messages.map(_.message).filter(_.contains("is not typically used on"))

  "Option validation" should {
    "recognize every entity option RIDDL publishes for an Entity" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Opts is {
          |  context Ctx is {
          |    entity Persisted is { ??? } with {
          |      option event-sourced
          |      option value
          |      option consistent
          |      option available
          |      option message-queue
          |      option transient
          |      option aggregate
          |      option finite-state-machine
          |      option css("fill:white")
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      simpleParseAndValidate(input) match {
        case Left(messages) => fail(messages.format)
        case Right(result) =>
          unrecognizedOptionMessages(result.messages) mustBe empty
          misplacedOptionMessages(result.messages) mustBe empty
      }
    }

    "accept 'option transient' on a Repository as well as an Entity" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Opts is {
          |  context Ctx is {
          |    entity Ephemeral is { ??? } with { option transient }
          |    repository Cache is { ??? } with { option transient }
          |  }
          |}
          |""".stripMargin,
        td
      )
      simpleParseAndValidate(input) match {
        case Left(messages) => fail(messages.format)
        case Right(result) =>
          unrecognizedOptionMessages(result.messages) mustBe empty
          misplacedOptionMessages(result.messages) mustBe empty
      }
    }

    "recognize 'option css' anywhere, because it has no valid-parent restriction" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain Opts is { ??? } with { option css("fill:white") }
            |""".stripMargin,
          td
        )
        simpleParseAndValidate(input) match {
          case Left(messages) => fail(messages.format)
          case Right(result) =>
            unrecognizedOptionMessages(result.messages) mustBe empty
            misplacedOptionMessages(result.messages) mustBe empty
        }
    }

    "recognize 'option sync' on an Epic" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Opts is {
          |  user Bob is "a person"
          |  epic Story is {
          |    user Opts.Bob wants "to tell a story" so that "the story gets told"
          |    ???
          |  } with { option sync }
          |}
          |""".stripMargin,
        td
      )
      simpleParseAndValidate(input) match {
        case Left(messages) => fail(messages.format)
        case Right(result) =>
          unrecognizedOptionMessages(result.messages) mustBe empty
          misplacedOptionMessages(result.messages) mustBe empty
      }
    }

    "recognize 'option protocol' on a streamlet, whose kind is its SHAPE name" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain Opts is {
            |  context Ctx is {
            |    command Go()
            |    source Emitter is {
            |      outlet Out is command Go
            |    } with { option protocol("kafka") }
            |    sink Absorber is {
            |      inlet In is command Go
            |    } with { option protocol("kafka") }
            |  }
            |}
            |""".stripMargin,
          td
        )
        simpleParseAndValidate(input) match {
          case Left(messages) => fail(messages.format)
          case Right(result) =>
            unrecognizedOptionMessages(result.messages) mustBe empty
            misplacedOptionMessages(result.messages) mustBe empty
        }
    }

    "still flag a genuinely unknown option" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Opts is { ??? } with { option flibbertigibbet }
          |""".stripMargin,
        td
      )
      simpleParseAndValidate(input) match {
        case Left(messages) => fail(messages.format)
        case Right(result) =>
          unrecognizedOptionMessages(result.messages).size mustBe 1
      }
    }
  }
}
