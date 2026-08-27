/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A37: tests for the context-intention rules enforced by [[ValidationPass]] and
  * [[StreamingValidation]].
  */
class ContextIntentionTest extends AbstractValidatingTest {

  "Context intention (A37)" should {

    "error when a service context does not have a flow shape (rule 1)" in { (td: TestData) =>
      val input =
        """domain d is {
          |  service context s is { ??? }
          |}
          |""".stripMargin
      parseAndValidate(input, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          "Service context 's' must have a flow shape"
        )
      }
    }

    "accept a service context ascribed as flow (rule 1 positive)" in { (td: TestData) =>
      val input =
        """domain d is {
          |  service context s as flow is { ??? }
          |}
          |""".stripMargin
      parseAndValidate(input, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        msgs.filter(m =>
          m.kind == Error && m.message.contains("must have a flow shape")
        ) mustBe empty
      }
    }

    "error when a gateway context does not have a merge shape (rule 2)" in { (td: TestData) =>
      val input =
        """domain d is {
          |  gateway context g is { ??? }
          |}
          |""".stripMargin
      parseAndValidate(input, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          "Gateway context 'g' must have a merge shape"
        )
      }
    }

    "error when a non-application context contains a UI group (rule 3)" in { (td: TestData) =>
      val input =
        """domain d is {
          |  gateway context g as merge is {
          |    group grp is { ??? }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(input, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          "Only application-intended contexts may contain UI groups"
        )
      }
    }

    // A41: a context with NO declared intention is no longer grandfathered — UI groups require an
    // explicit 'application' intention.
    "error when an intention-less context contains a UI group (rule 3, A41)" in { (td: TestData) =>
      val input =
        """domain d is {
          |  context c is {
          |    group grp is { ??? }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(input, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          "Only application-intended contexts may contain UI groups"
        )
      }
    }

    "accept an application context that contains a UI group (rule 3 positive)" in {
      (td: TestData) =>
        val input =
          """domain d is {
          |  application context a is {
          |    group grp is { ??? }
          |  }
          |}
          |""".stripMargin
        parseAndValidate(input, td.name, shouldFailOnErrors = false) {
          case (_, _, msgs: Messages) =>
            msgs.filter(m =>
              m.kind == Error && m.message.contains(
                "Only application-intended contexts may contain UI groups"
              )
            ) mustBe empty
        }
    }

    "error when a connector touches an external context but is not persistent (rule 4)" in {
      (td: TestData) =>
        val input =
          """domain d is {
            |  type T is Integer
            |  external context ext is { outlet out is type d.T }
            |  context b is { sink snk is { inlet in is type d.T } }
            |  connector c is { from outlet d.ext.out to inlet d.b.snk.in }
            |}
            |""".stripMargin
        parseAndValidate(input, td.name, shouldFailOnErrors = false) {
          case (_, _, msgs: Messages) =>
            assertValidationMessage(
              msgs,
              Error,
              "touches external context 'ext' and must be 'persistent'"
            )
        }
    }

    "accept a persistent connector touching an external context (rule 4 positive)" in {
      (td: TestData) =>
        val input =
          """domain d is {
            |  type T is Integer
            |  external context ext is { outlet out is type d.T }
            |  context b is { sink snk is { inlet in is type d.T } }
            |  connector c is { from outlet d.ext.out to inlet d.b.snk.in } with { option persistent }
            |}
            |""".stripMargin
        parseAndValidate(input, td.name, shouldFailOnErrors = false) {
          case (_, _, msgs: Messages) =>
            msgs.filter(m =>
              m.kind == Error && m.message.contains("must be 'persistent'")
            ) mustBe empty
        }
    }

    "advise an adaptor between an external context and a non-adaptor processor (rule 5)" in {
      (td: TestData) =>
        val input =
          """domain d is {
            |  type T is Integer
            |  external context ext is { outlet out is type d.T }
            |  context b is { sink snk is { inlet in is type d.T } }
            |  connector c is { from outlet d.ext.out to inlet d.b.snk.in } with { option persistent }
            |}
            |""".stripMargin
        parseAndValidate(input, td.name, shouldFailOnErrors = false) {
          case (_, _, msgs: Messages) =>
            assertValidationMessage(
              msgs,
              StyleWarning,
              "Consider an adaptor between external context 'ext'"
            )
        }
    }
  }
}
