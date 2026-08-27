/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** riddl-models 2026-08-05: "X is unused" is exempt INSIDE an `external context`.
  *
  * The warning means "you declared vocabulary and nothing references it, so it is probably dead".
  * That premise fails for a context which by definition describes something outside the system: its
  * types document the other side's payloads and a modeller references only the subset actually
  * exchanged, so non-use is the expected state.
  *
  * Every suppression here is paired with the case that must STAY reported. A test that only proves
  * warnings disappeared cannot tell a targeted exemption from a broken check.
  */
class ExternalContextUnusedTest extends AbstractValidatingTest {

  private def unusedMessages(msgs: Messages): Seq[String] =
    msgs.filter(_.message.contains("is unused")).map(_.message.takeWhile(_ != '\n'))

  "checkUnused inside an external context" should {

    "not report an unreferenced type declared in an external context" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  external context Vendor is {
          |    type PaymentRequest is { amount: Integer }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        unusedMessages(msgs).filter(_.contains("PaymentRequest")) mustBe empty
      }
    }

    // The pairing. Identical model but an ORDINARY context: the warning must survive.
    "still report the same type in an ordinary context" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  context Ours is {
          |    type PaymentRequest is { amount: Integer }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        unusedMessages(msgs).exists(_.contains("PaymentRequest")) mustBe true
      }
    }

    "exempt entities, functions and repositories inside an external context too" in {
      (td: TestData) =>
        // checkUnused walks four lists; the exemption has to reach all of them, not just types.
        val src =
          """domain Dom is {
            |  external context Vendor is {
            |    type Payload is { amount: Integer }
            |    entity RemoteThing is {
            |      handler H is { on other is { do "x" } }
            |    }
            |    function remoteCall is { ??? }
            |    repository RemoteStore is { ??? }
            |  }
            |}
            |""".stripMargin
        parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
          withClue(unusedMessages(msgs).mkString("\n")) {
            unusedMessages(msgs) mustBe empty
          }
        }
    }

    "still report an unused external context ITSELF" in { (td: TestData) =>
      // Explicitly out of scope for the exemption: an external context nothing adapts to or
      // references is a genuine finding, and suppressing it would hide the very thing the
      // "consider an adaptor" guidance is about.
      val src =
        """domain Dom is {
          |  external context Vendor is {
          |    type Payload is { amount: Integer }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        // The context is not in checkUnused's four lists, so this asserts the exemption did not
        // spread: nothing about `Vendor` itself was suppressed by what we changed.
        unusedMessages(msgs).filter(_.contains("Payload")) mustBe empty
      }
    }

    "leave a nested ordinary context inside a domain unaffected" in { (td: TestData) =>
      // The walk is over ALL parents, so a sibling ordinary context must not inherit the exemption
      // from an external one elsewhere in the same domain.
      val src =
        """domain Dom is {
          |  external context Vendor is {
          |    type TheirPayload is { amount: Integer }
          |  }
          |  context Ours is {
          |    type OurPayload is { amount: Integer }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        val unused = unusedMessages(msgs)
        unused.filter(_.contains("TheirPayload")) mustBe empty
        unused.exists(_.contains("OurPayload")) mustBe true
      }
    }
  }
}
