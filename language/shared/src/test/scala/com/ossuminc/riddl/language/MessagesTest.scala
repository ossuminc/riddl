/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.parsing.RiddlParserInput.*
import com.ossuminc.riddl.utils.{Logger, StringLogger, TestingBasis, URL}

class MessagesTest extends TestingBasis {

  "MessageKinds" must {
    "have MissingWarning with correct queries" in {
      MissingWarning.isSevereError mustBe false
      MissingWarning.isError mustBe false
      MissingWarning.isWarning mustBe true
      MissingWarning.isInfo mustBe false
      MissingWarning.isActionable mustBe false
      MissingWarning.isIgnorable mustBe true
      MissingWarning.isUsage mustBe false
      MissingWarning.isStyle mustBe false
      MissingWarning.isMissing mustBe true
      MissingWarning.toString mustBe "Missing"
    }
    "have StyleWarning with correct queries" in {
      StyleWarning.isSevereError mustBe false
      StyleWarning.isError mustBe false
      StyleWarning.isWarning mustBe true
      StyleWarning.isInfo mustBe false
      StyleWarning.isActionable mustBe false
      StyleWarning.isIgnorable mustBe true
      StyleWarning.isUsage mustBe false
      StyleWarning.isStyle mustBe true
      StyleWarning.isMissing mustBe false
      StyleWarning.toString mustBe "Style"
    }
    "have UsageWarning with correct queries" in {
      UsageWarning.isSevereError mustBe false
      UsageWarning.isError mustBe false
      UsageWarning.isWarning mustBe true
      UsageWarning.isInfo mustBe false
      UsageWarning.isActionable mustBe false
      UsageWarning.isIgnorable mustBe true
      UsageWarning.isUsage mustBe true
      UsageWarning.isStyle mustBe false
      UsageWarning.isMissing mustBe false
      UsageWarning.toString mustBe "Usage"
    }
    "have Warning with correct queries" in {
      Warning.isSevereError mustBe false
      Warning.isError mustBe false
      Warning.isWarning mustBe true
      Warning.isInfo mustBe false
      Warning.isActionable mustBe true
      Warning.isIgnorable mustBe false
      Warning.isUsage mustBe false
      Warning.isStyle mustBe false
      Warning.isMissing mustBe false
      Warning.toString mustBe "Warning"
    }
    "have Error with correct queries" in {
      Error.isSevereError mustBe false
      Error.isError mustBe true
      Error.isWarning mustBe false
      Error.isInfo mustBe false
      Error.isActionable mustBe true
      Error.isIgnorable mustBe false
      Error.isUsage mustBe false
      Error.isStyle mustBe false
      Error.isMissing mustBe false
      Error.toString mustBe "Error"
    }
    "have SevereError with correct queries" in {
      SevereError.isSevereError mustBe true
      SevereError.isError mustBe true
      SevereError.isWarning mustBe false
      SevereError.isInfo mustBe false
      SevereError.isActionable mustBe true
      SevereError.isIgnorable mustBe false
      SevereError.isUsage mustBe false
      SevereError.isStyle mustBe false
      SevereError.isMissing mustBe false
      SevereError.toString mustBe "Severe"
    }
    "have Severities from lowest to highest" in {
      Info.severity mustBe 0
      StyleWarning.severity mustBe 1
      MissingWarning.severity mustBe 2
      UsageWarning.severity mustBe 3
      Warning.severity mustBe 4
      Error.severity mustBe 5
      SevereError.severity mustBe 6
    }
    "have KindOfMessage that supports comparison" in {
      (Info < StyleWarning &&
        StyleWarning < MissingWarning &&
        MissingWarning < UsageWarning &&
        UsageWarning < Warning &&
        Warning < Error &&
        Error < SevereError) mustBe true
    }
  }

  private val i = Messages.info("info")
  private val sty = Messages.style("style")
  private val m = Messages.missing("missing")
  private val u = Messages.usage("usage")
  private val w = Messages.warning("warning")
  private val e = Messages.error("error")
  private val s = Messages.severe("severe")

  "Message" should {
    "know their kind" in {
      i.isInfo mustBe true
      sty.isStyle mustBe true
      m.isMissing mustBe true
      u.isUsage mustBe true
      w.isWarning mustBe true
      e.isError mustBe true
      s.isSevere mustBe true
    }
  }

  val mix: Messages = List(i, sty, m, u, w, e, s)

  "Messages" should {
    "filter for Warnings" in {
      mix.justWarnings mustBe Seq(sty, m, u, w)
    }
    "filter for Errors" in {
      mix.justErrors mustBe Seq(e, s)
    }
    "filter for StyleWarnings" in {
      mix.justStyle mustBe Seq(sty)
    }
    "filter for MissingWarnings" in {
      mix.justMissing mustBe Seq(m)
    }
    "filter for UsageWarnings" in {
      mix.justUsage mustBe Seq(u)
    }
    "filter for InfoWarnings" in {
      mix.justInfo mustBe Seq(i)
    }
    "log with retained order" in {
      val commonOptions = CommonOptions()
      val log: Logger = StringLogger()
      Messages.logMessages(mix, log, commonOptions)
      val content = log.toString
      val expected = """[34m[1m[info] empty(1:1)info[0m
                       |[32m[1m[style] empty(1:1)style[0m
                       |[32m[1m[missing] empty(1:1)missing[0m
                       |[32m[1m[usage] empty(1:1)usage[0m
                       |[33m[1m[warning] empty(1:1)warning[0m
                       |[31m[1m[error] empty(1:1)error[0m
                       |[41m[30m[1m[severe] empty(1:1)severe[0m
                       |""".stripMargin
      content mustBe expected
    }
    "log grouped by message kind" in {
      val commonOptions = CommonOptions(groupMessagesByKind = true)
      val log: Logger = StringLogger()
      Messages.logMessages(mix, log, commonOptions)
      val content = log.toString
      val expected =
        """[41m[30m[1m[severe] Severe Message Count: 1[0m
          |[41m[30m[1m[severe] empty(1:1)severe[0m
          |[31m[1m[error] Error Message Count: 1[0m
          |[31m[1m[error] empty(1:1)error[0m
          |[32m[1m[usage] Usage Message Count: 1[0m
          |[32m[1m[usage] empty(1:1)usage[0m
          |[32m[1m[missing] Missing Message Count: 1[0m
          |[32m[1m[missing] empty(1:1)missing[0m
          |[32m[1m[style] Style Message Count: 1[0m
          |[32m[1m[style] empty(1:1)style[0m
          |[34m[1m[info] Info Message Count: 1[0m
          |[34m[1m[info] empty(1:1)info[0m
          |""".stripMargin
      content mustBe expected
    }

    "has inquiry methods" in {
      val mix_formatted = mix.format
      mix_formatted.length must be(115)
      mix.isOnlyWarnings must be(false)
      mix.isOnlyIgnorable must be(false)
      mix.hasErrors must be(true)
      mix.hasWarnings must be(true)
    }

    "format a correct string for empty location" in {
      val msg =
        Message(At(1, 2, RiddlParserInput.empty), "the_message", Warning)
      val content = msg.format
      val expected = "empty(1:2)the_message"
      content mustBe expected
    }

    "format to locate output for non-empty location" in {
      val rip: RiddlParserInput = RiddlParserInput("test", URL.empty, "test")
      val at = At(1, 2, rip)
      val msg = Message(at, "the_message", Warning)
      val content = msg.format
      val expected =
        """empty(1:2):
          |the_message:
          |test
          | ^""".stripMargin
      content mustBe expected
    }

    "be ordered based on location" in {
      val rip: RiddlParserInput = RiddlParserInput("test", "")
      val v1 = Message(At(1, 2, rip), "the_message", Warning)
      val v2 = Message(At(2, 3, rip), "the_message", Warning)
      v1 < v2 mustBe true
      v1 == v2 mustBe false
    }
  }

  "Accumulator" must {
    val acc: Accumulator = Accumulator(CommonOptions())
    "has an empty companion" in {
      Accumulator.empty must be(empty)
    }
    "have basic inquiry methods" in {
      acc.isEmpty must be(true)
      acc.nonEmpty must be(false)
      acc.size must be(0)
      acc.toMessages must be(empty)
    }
    "have message add methods" in {
      acc.add(Messages.info("info", At.empty))
      acc.addStyle(At.empty, "style")
      acc.addMissing(At.empty, "missing")
      acc.addUsage(At.empty, "usage")
      acc.addWarning(At.empty, "warning")
      acc.addError(At.empty, "error")
      acc.addSevere(At.empty, "severe")
      val msgs = acc.toMessages
      msgs.justErrors.size must be(2)
      msgs.justInfo.size must be(1)
      msgs.justStyle.size must be(1)
      msgs.justUsage.size must be(1)
      msgs.justWarnings.size must be(4)
      msgs.justErrors.head.message must be("error")
      msgs.justInfo.head.message must be("info")
      msgs.justStyle.head.message must be("style")
      msgs.justMissing.head.message must be("missing")
      msgs.justUsage.head.message must be("usage")
      msgs.justErrors.head.message must be("error")
    }
  }
}
