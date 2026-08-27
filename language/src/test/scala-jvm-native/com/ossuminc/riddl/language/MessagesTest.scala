/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.parsing.RiddlParserInput.*
import com.ossuminc.riddl.utils.{AbstractTestingBasis, CommonOptions, StringLogger, URL, pc}

import scala.io.AnsiColor.*

class MessagesTest extends AbstractTestingBasis {

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
    "have CompletenessWarning with correct queries" in {
      CompletenessWarning.isSevereError mustBe false
      CompletenessWarning.isError mustBe false
      CompletenessWarning.isWarning mustBe true
      CompletenessWarning.isInfo mustBe false
      CompletenessWarning.isActionable mustBe true
      CompletenessWarning.isIgnorable mustBe false
      CompletenessWarning.isCompleteness mustBe true
      CompletenessWarning.isUsage mustBe false
      CompletenessWarning.isStyle mustBe false
      CompletenessWarning.isMissing mustBe false
      CompletenessWarning.toString mustBe "Completeness"
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
    "have Deprecation with correct queries (A9)" in {
      Deprecation.isSevereError mustBe false
      Deprecation.isError mustBe false
      Deprecation.isWarning mustBe true
      Deprecation.isInfo mustBe false
      Deprecation.isActionable mustBe false
      Deprecation.isIgnorable mustBe true
      Deprecation.isUsage mustBe false
      Deprecation.isStyle mustBe false
      Deprecation.isMissing mustBe false
      Deprecation.isDeprecation mustBe true
      Deprecation.toString mustBe "Deprecation"
    }
    "have Severities from lowest to highest" in {
      (Info.severity < StyleWarning.severity) mustBe true
      (StyleWarning.severity < MissingWarning.severity) mustBe true
      (MissingWarning.severity < UsageWarning.severity) mustBe true
      (UsageWarning.severity < CompletenessWarning.severity) mustBe true
      (CompletenessWarning.severity < Warning.severity) mustBe true
      (Warning.severity < Error.severity) mustBe true
      (Error.severity < SevereError.severity) mustBe true
    }
    "have KindOfMessage that supports comparison" in {
      (Info < StyleWarning &&
        StyleWarning < MissingWarning &&
        MissingWarning < UsageWarning &&
        UsageWarning < CompletenessWarning &&
        CompletenessWarning < Warning &&
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
  private val dep = Message(At.empty, "deprecated thing", Deprecation)

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
    "filter for Deprecations (A9)" in {
      List(i, dep, w).justDeprecations mustBe Seq(dep)
    }
    "log with retained order" in {
      pc.withLogger(StringLogger()) { slog =>
        Messages.logMessages(mix)
        val content = slog.toString
        val expected =
          s"""$BLUE$BOLD[info] empty(1:1->1):$RESET
            |${BLUE}info$RESET
            |$GREEN$BOLD[style] empty(1:1->1):$RESET
            |${GREEN}style$RESET
            |$GREEN$BOLD[missing] empty(1:1->1):$RESET
            |${GREEN}missing$RESET
            |$GREEN$BOLD[usage] empty(1:1->1):$RESET
            |${GREEN}usage$RESET
            |$YELLOW$BOLD[warning] empty(1:1->1):$RESET
            |${YELLOW}warning$RESET
            |$RED$BOLD[error] empty(1:1->1):$RESET
            |${RED}error$RESET
            |$RED_B$BLACK$BOLD[severe] empty(1:1->1):$RESET
            |$RED_B${BLACK}severe$RESET
            |""".stripMargin
        info(s"Comparing expected:\n$expected\nwith actual:\n$content\n")
        content mustBe expected
      }
    }
    "log grouped by message kind" in {
      pc.withLogger(StringLogger()) { _ =>
        pc.withOptions(CommonOptions(groupMessagesByKind = true)) { _ =>
          Messages.logMessages(mix)
          val content = pc.log.toString
          val expected =
            s"""$RED_B$BLACK$BOLD[severe] Severe Message Count: 1$RESET
              |$RED_B$BLACK$BOLD[severe] empty(1:1->1):$RESET
              |$RED_B${BLACK}severe$RESET
              |$RED$BOLD[error] Error Message Count: 1$RESET
              |$RED$BOLD[error] empty(1:1->1):$RESET
              |${RED}error$RESET
              |$GREEN$BOLD[usage] Usage Message Count: 1$RESET
              |$GREEN$BOLD[usage] empty(1:1->1):$RESET
              |${GREEN}usage$RESET
              |$GREEN$BOLD[missing] Missing Message Count: 1$RESET
              |$GREEN$BOLD[missing] empty(1:1->1):$RESET
              |${GREEN}missing$RESET
              |$GREEN$BOLD[style] Style Message Count: 1$RESET
              |$GREEN$BOLD[style] empty(1:1->1):$RESET
              |${GREEN}style$RESET
              |$BLUE$BOLD[info] Info Message Count: 1$RESET
              |$BLUE$BOLD[info] empty(1:1->1):$RESET
              |${BLUE}info$RESET
              |""".stripMargin
          content mustBe expected
        }
      }
    }

    "has inquiry methods" in {
      val mix_formatted = mix.format
      mix_formatted.length must be(150)
      mix.isOnlyWarnings must be(false)
      mix.isOnlyIgnorable must be(false)
      mix.hasErrors must be(true)
      mix.hasWarnings must be(true)
    }

    "format a correct string for empty location" in {
      val rpi = RiddlParserInput.empty
      val msg = Message(At(1, 2, rpi), "the_message", Warning)
      val content = msg.format
      val expected = "empty(1:2->3):\nthe_message"
      content mustBe expected
    }

    "format to locate output for non-empty location" in {
      val rip: RiddlParserInput = RiddlParserInput("TEST INPUT", URL.empty, "test")
      val at = At(1, 2, rip)
      val msg = Message(at, "the_message", Warning)
      val content = msg.format
      BOLD
      val expected =
        s"""empty(1:2->3):
          |the_message:
          |T${BOLD}E${RESET}ST INPUT""".stripMargin
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
    val acc: Accumulator = Accumulator()
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
      // `ruleId` is REQUIRED, so these pass None explicitly. That is the point of removing the
      // default: a diagnostic cannot be added without deciding which rule it belongs to, and a
      // test exercising the plumbing has to say out loud that it has no rule in mind.
      acc.addStyle(At.empty, "style", ruleId = None)
      acc.addMissing(At.empty, "missing", ruleId = None)
      acc.addUsage(At.empty, "usage", ruleId = None)
      acc.addWarning(At.empty, "warning", ruleId = None)
      acc.addError(At.empty, "error", ruleId = None)
      acc.addSevere(At.empty, "severe", ruleId = None)
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

    // A9 regressions: logging a Deprecation message must not throw. A non-exhaustive
    // `KindOfMessage` match in logMessage/logMessagesByGroup previously crashed only via
    // Commands.runMain (caught -> exit 8), not through direct Messages inspection.
    "log a Deprecation message (retained order) without crashing (A9 regression)" in {
      pc.withLogger(StringLogger()) { slog =>
        Messages.logMessages(List(i, dep, w))
        slog.toString must include("deprecated thing")
      }
    }
    "log a Deprecation message (grouped by kind) without crashing (A9 regression)" in {
      pc.withLogger(StringLogger()) { _ =>
        pc.withOptions(CommonOptions(groupMessagesByKind = true)) { _ =>
          Messages.logMessages(List(i, dep, w))
          pc.log.toString must include("deprecated thing")
        }
      }
    }
  }

  /** Reid's 2026-08-27 ruling: a CONFORMING model is error-free, but a GENERABLE one has "no
    * warnings except Style warnings". The two bars are different questions and the predicates
    * deliberately disagree — `isActionable` draws its line at CompletenessWarning, which lets
    * Missing and Usage through, and those are precisely the two the ruling says must NOT pass:
    * unused items put cruft in the model, and missing things cannot be generated at all.
    *
    * Every kind is asserted, not a sample, so adding a KindOfMessage without deciding which side
    * of the bar it falls on reddens here rather than defaulting silently.
    */
  "isGenerable" should {
    "admit Style and everything below it" in {
      Tip.isGenerable mustBe true
      Info.isGenerable mustBe true
      StyleWarning.isGenerable mustBe true
    }
    "reject Missing and Usage — the two the ruling turns on" in {
      MissingWarning.isGenerable mustBe false
      UsageWarning.isGenerable mustBe false
    }
    "reject everything above them" in {
      Deprecation.isGenerable mustBe false
      CompletenessWarning.isGenerable mustBe false
      Warning.isGenerable mustBe false
      Error.isGenerable mustBe false
      SevereError.isGenerable mustBe false
    }
    "disagree with isActionable exactly where the ruling says it should" in {
      // Missing and Usage are NOT actionable but ARE generation blockers. If these two ever
      // agree, one of the predicates has been quietly re-pointed at the other's question.
      MissingWarning.isActionable mustBe false
      MissingWarning.isGenerable mustBe false
      UsageWarning.isActionable mustBe false
      UsageWarning.isGenerable mustBe false
    }
  }
}
