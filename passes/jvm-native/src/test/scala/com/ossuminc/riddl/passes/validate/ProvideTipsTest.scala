/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{CommonOptions, pc, ec}

import org.scalatest.{Assertion, TestData}

/** Tests for the CommonOptions.provideTips mechanism: every validation/
  * resolution message can carry a remediation `suggestion`, which is retained
  * (and rendered by Message.format) only when provideTips is enabled.
  */
class ProvideTipsTest extends AbstractValidatingTest {

  /** A model that reliably emits at least one warning carrying a suggestion
    * (the "should have a description" MissingWarning on each definition).
    */
  private val describedModel: String =
    """domain D is {
      |  context C is {
      |    type Foo is { x: String }
      |  }
      |}
      |""".stripMargin

  /** A model whose entity defines no command types, exercising one of the
    * advisory completeness checks that only fire when provideTips is on.
    */
  private val entityModel: String =
    """domain D is {
      |  context C is {
      |    entity E is {
      |      record F is { x: String }
      |      state S of E.F is { handler H is { ??? } }
      |    }
      |  }
      |}
      |""".stripMargin

  private def withProvideTips(on: Boolean)(body: => Assertion): Assertion =
    pc.withOptions[Assertion](CommonOptions.default.copy(provideTips = on)) { _ =>
      body
    }

  "provideTips" should {

    "strip remediation suggestions from messages by default" in { (td: TestData) =>
      withProvideTips(on = false) {
        parseAndValidate(describedModel, "test", shouldFailOnErrors = false) {
          (_, _, msgs) =>
            msgs mustNot be(empty)
            msgs.forall(_.suggestion.isEmpty) mustBe true
            msgs.exists(_.format.contains("Suggestion:")) mustBe false
        }
      }
    }

    "attach remediation suggestions to messages when enabled" in { (td: TestData) =>
      withProvideTips(on = true) {
        parseAndValidate(describedModel, "test", shouldFailOnErrors = false) {
          (_, _, msgs) =>
            msgs.exists(_.suggestion.nonEmpty) mustBe true
            // The missing-description warning carries a documentation suggestion
            msgs.exists(m =>
              m.message.contains("should have a description") &&
                m.suggestion.contains("Add documentation")
            ) mustBe true
            // The suggestion is rendered by Message.format
            msgs.exists(_.format.contains("Suggestion:")) mustBe true
        }
      }
    }

    "emit advisory entity-completeness checks only when enabled" in { (td: TestData) =>
      def hasNoCommandTypesWarning(msgs: Messages.Messages): Boolean =
        msgs.exists(_.message.contains("defines no command types"))

      // Off by default: the advisory check does not fire
      withProvideTips(on = false) {
        parseAndValidate(entityModel, "test", shouldFailOnErrors = false) {
          (_, _, msgs) => hasNoCommandTypesWarning(msgs) mustBe false
        }
      }
      // On: the advisory completeness warning fires and carries a suggestion
      withProvideTips(on = true) {
        parseAndValidate(entityModel, "test", shouldFailOnErrors = false) {
          (_, _, msgs) =>
            val cmdWarn = msgs.find(_.message.contains("defines no command types"))
            cmdWarn mustBe defined
            cmdWarn.get.suggestion.contains("type ECommand = command") mustBe true
        }
      }
    }
  }
}
