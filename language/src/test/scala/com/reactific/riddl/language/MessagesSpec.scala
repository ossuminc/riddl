/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.utils.{Logger, StringLogger}

class MessagesSpec extends AnyWordSpec with Matchers {

  "MessageKinds" should {
    "MissingWarning must have correct queries" in {
      MissingWarning.isSevereError mustBe false
      MissingWarning.isError mustBe false
      MissingWarning.isWarning mustBe true
      MissingWarning.isInfo mustBe false
      MissingWarning.isActionable mustBe false
      MissingWarning.isIgnorable mustBe true
      MissingWarning.toString mustBe "Missing"
    }
    "StyleWarning must have correct queries" in {
      StyleWarning.isSevereError mustBe false
      StyleWarning.isError mustBe false
      StyleWarning.isWarning mustBe true
      StyleWarning.isInfo mustBe false
      StyleWarning.isActionable mustBe false
      StyleWarning.isIgnorable mustBe true
      StyleWarning.toString mustBe "Style"
    }
    "Warning must have correct queries" in {
      Warning.isSevereError mustBe false
      Warning.isError mustBe false
      Warning.isWarning mustBe true
      Warning.isInfo mustBe false
      Warning.isActionable mustBe true
      Warning.isIgnorable mustBe false
      Warning.toString mustBe "Warning"
    }
    "Error must have correct queries" in {
      Error.isSevereError mustBe false
      Error.isError mustBe true
      Error.isWarning mustBe false
      Error.isInfo mustBe false
      Error.isActionable mustBe true
      Error.isIgnorable mustBe false
      Error.toString mustBe "Error"
    }
    "SevereError must have correct queries" in {
      SevereError.isSevereError mustBe true
      SevereError.isError mustBe true
      SevereError.isWarning mustBe false
      SevereError.isInfo mustBe false
      SevereError.isActionable mustBe true
      SevereError.isIgnorable mustBe false
      SevereError.toString mustBe "Severe"
    }
    "Severities from lowest to highest" in {
      Info.severity mustBe 0
      StyleWarning.severity mustBe 1
      MissingWarning.severity mustBe 2
      Warning.severity mustBe 3
      Error.severity mustBe 4
      SevereError.severity mustBe 5
    }
    "KindOfMessage supports comparison" in {
      (Info < StyleWarning &&
        StyleWarning < MissingWarning &&
        MissingWarning < Warning &&
        Warning < Error &&
        Error < SevereError) mustBe true
    }
  }

  val i = Messages.info("nada")
  val w = Messages.warning("nada")
  val e = Messages.error("nada")
  val s = Messages.severe("nada")

  "Message" should {
    "know their kind" in {
      i.isInfo mustBe true
      w.isWarning mustBe true
      e.isError mustBe true
      s.isSevere mustBe true
    }
  }

  val mix: Messages = List(s, e, i, w)

  "Messages" should {
    "filter for Warnings" in {
      mix.justWarnings.head mustBe w
    }
    "filter for Errors" in {
      mix.justErrors.size mustBe 2
    }
    "filter for MissingWarnings" in {
      mix.justMissing mustBe(empty)
    }
    "log with retained order" in {
      val commonOptions = CommonOptions(groupMessagesByKind = false)
      val log = StringLogger()
      Messages.logMessages(mix, log, commonOptions)
      val content = log.toString()
      content mustBe
        """[severe] Severe: empty(1:1): nada
          |[error] Error: empty(1:1): nada
          |[info] Info: empty(1:1): nada
          |[warning] Warning: empty(1:1): nada
          |""".stripMargin
    }
    "log grouped by message kind" in {
      val commonOptions = CommonOptions(groupMessagesByKind = true)
        val log:Logger = StringLogger()
        Messages.logMessages(mix, log, commonOptions)
        val content = log.toString()
        content mustBe """[info] Warnings: 1
                         |[warning] Warning: empty(1:1): nada
                         |[info] Errors: 2
                         |[error] Error: empty(1:1): nada
                         |[error] Info: empty(1:1): nada
                         |[info] Severe Errors: 1
                         |[severe] Severe: empty(1:1): nada
                         |""".stripMargin
      }
      "format should produce a correct string" in {
        val msg =
          Message(At(1, 2, RiddlParserInput.empty), "the_message", Warning)
        msg.format mustBe s"Warning: empty(1:2): the_message"
      }
      "be ordered based on location" in {
        val v1 = Message(At(1, 2, "the_source"), "the_message", Warning)
        val v2 = Message(At(2, 3, "the_source"), "the_message", Warning)
        v1 < v2 mustBe true
        v1 == v2 mustBe false
      }
  }
}
