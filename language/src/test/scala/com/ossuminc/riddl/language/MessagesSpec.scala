/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{Logger,StringLogger}

class MessagesSpec extends AnyWordSpec with Matchers {

  "MessageKinds" should {
    "MissingWarning must have correct queries" in {
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
    "StyleWarning must have correct queries" in {
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
    "UsageWarning must have correct queries" in {
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
    "Warning must have correct queries" in {
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
    "Error must have correct queries" in {
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
    "SevereError must have correct queries" in {
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
    "Severities from lowest to highest" in {
      Info.severity mustBe 0
      StyleWarning.severity mustBe 1
      MissingWarning.severity mustBe 2
      UsageWarning.severity mustBe 3
      Warning.severity mustBe 4
      Error.severity mustBe 5
      SevereError.severity mustBe 6
    }
    "KindOfMessage supports comparison" in {
      (Info < StyleWarning &&
        StyleWarning < MissingWarning &&
        MissingWarning < UsageWarning &&
        UsageWarning < Warning &&
        Warning < Error &&
        Error < SevereError) mustBe true
    }
  }

  val i = Messages.info("info")
  val sty = Messages.style("style")
  val m = Messages.missing("missing")
  val u = Messages.usage("usage")
  val w = Messages.warning("warning")
  val e = Messages.error("error")
  val s = Messages.severe("severe")

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
      mix.justWarnings mustBe (Seq(sty, m, u, w))
    }
    "filter for Errors" in {
      mix.justErrors mustBe (Seq(e, s))
    }
    "filter for StyleWarnings" in {
      mix.justStyle mustBe (Seq(sty))
    }
    "filter for MissingWarnings" in {
      mix.justMissing mustBe (Seq(m))
    }
    "filter for UsageWarnings" in {
      mix.justUsage mustBe (Seq(u))
    }
    "log with retained order" in {
      val commonOptions = CommonOptions(groupMessagesByKind = false)
      val log = StringLogger()
      Messages.logMessages(mix, log, commonOptions)
      val content = log.toString()
      val expected = """[34m[1m[info] Info: empty(1:1):[0m
                       |[34minfo[0m
                       |[33m[1m[warning] Style: empty(1:1):[0m
                       |[33mstyle[0m
                       |[33m[1m[warning] Missing: empty(1:1):[0m
                       |[33mmissing[0m
                       |[33m[1m[warning] Usage: empty(1:1):[0m
                       |[33musage[0m
                       |[33m[1m[warning] Warning: empty(1:1):[0m
                       |[33mwarning[0m
                       |[31m[1m[error] Error: empty(1:1):[0m
                       |[31merror[0m
                       |[41m[30m[1m[severe] Severe: empty(1:1):[0m
                       |[41m[30msevere[0m
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
          |[41m[30m[1m[severe] Severe: empty(1:1):[0m
          |[41m[30msevere[0m
          |[31m[1m[error] Error Message Count: 1[0m
          |[31m[1m[error] Error: empty(1:1):[0m
          |[31merror[0m
          |[33m[1m[warning] Usage Message Count: 1[0m
          |[33m[1m[warning] Usage: empty(1:1):[0m
          |[33musage[0m
          |[33m[1m[warning] Missing Message Count: 1[0m
          |[33m[1m[warning] Missing: empty(1:1):[0m
          |[33mmissing[0m
          |[33m[1m[warning] Style Message Count: 1[0m
          |[33m[1m[warning] Style: empty(1:1):[0m
          |[33mstyle[0m
          |[34m[1m[info] Info Message Count: 1[0m
          |[34m[1m[info] Info: empty(1:1):[0m
          |[34minfo[0m
          |""".stripMargin
      content mustBe expected
    }

    "format should produce a correct string" in {
      val msg =
        Message(At(1, 2, RiddlParserInput.empty), "the_message", Warning)
      val content = msg.format
      val expected = """Warning: empty(1:2):
                       |the_message""".stripMargin
      content mustBe expected
    }

    "be ordered based on location" in {
      val v1 = Message(At(1, 2, "the_source"), "the_message", Warning)
      val v2 = Message(At(2, 3, "the_source"), "the_message", Warning)
      v1 < v2 mustBe true
      v1 == v2 mustBe false
    }
  }
}
