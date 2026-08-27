/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** UI modality and container checks, ruled 2026-08-26 (ossum.tech's report).
  *
  * RIDDL's UI vocabulary shipped as parseable aliases with no validation behind it: a `haptic` that
  * `shows`, a `menu` a user cannot choose from, and a `popup` nothing opens all validated clean.
  */
class UIModalityTest extends AbstractValidatingTest {

  private def model(body: String): String =
    s"""domain D is {
       |  author Ossum is { name = "Ossum" email = "o@o.com" }
       |  user Shopper is "a customer"
       |  application context App is {
       |    record Sound is { note: String(1,50) }
       |    record Panel is { note: String(1,50) }
       |    command Tap is { note: String(1,50) }
       |$body
       |  } with { briefly "a" described as "a" }
       |} with { briefly "d" described as "d" }
       |""".stripMargin

  private def msgs(body: String, td: TestData): Seq[String] =
    var out = Seq.empty[String]
    parseAndValidateAggregate(RiddlParserInput(model(body), td)) { result =>
      out = result.messages.toSeq.map(m => m.ruleCode.getOrElse("") + ": " + m.message)
      succeed
    }
    out

  private def has(ms: Seq[String], rule: String): Boolean = ms.exists(_.startsWith(rule))

  "a presentation verb" should {

    "style-warn when it contradicts its output's kind" in { (td: TestData) =>
      val ms = msgs("""    page Screen is { haptic Buzz shows record Sound }""", td)
      withClue(ms.mkString("\n")) { has(ms, "app-verb-modality-mismatch") mustBe true }
    }

    "stay silent for a verb that implies NO modality" in { (td: TestData) =>
      // `presents` and `emits` are broad by meaning -- a system may present through any channel --
      // so mapping them would invent a rule the language never stated.
      val ms = msgs("""    page Screen is { haptic Buzz presents record Sound }""", td)
      withClue(ms.mkString("\n")) { has(ms, "app-verb-modality-mismatch") mustBe false }
    }

    "stay silent for a verb whose modality does not exist as an output" in { (td: TestData) =>
      // `diffuses` implies scent and there is NO scent output kind, so it cannot contradict one.
      // Pinning this stops someone "completing" the map by inventing a modality for it.
      val ms = msgs("""    page Screen is { sound Chime diffuses record Sound }""", td)
      withClue(ms.mkString("\n")) { has(ms, "app-verb-modality-mismatch") mustBe false }
    }

    "stay silent when the verb suits the output" in { (td: TestData) =>
      val ms = msgs("""    page Screen is { haptic Buzz vibrates record Sound }""", td)
      withClue(ms.mkString("\n")) { has(ms, "app-verb-modality-mismatch") mustBe false }
    }

    "treat `plays` as suiting an animation as well as a sound" in { (td: TestData) =>
      // Both "play". A map that made `plays` purely auditory would flag every animation.
      val ms = msgs("""    page Screen is { animation Spin plays record Panel }""", td)
      withClue(ms.mkString("\n")) { has(ms, "app-verb-modality-mismatch") mustBe false }
    }
  }

  "a menu" should {

    "be flagged when it offers nothing to choose" in { (td: TestData) =>
      val ms = msgs(
        """    page Screen is { menu Empty is { document Filler shows record Panel } }""",
        td
      )
      withClue(ms.mkString("\n")) { has(ms, "app-menu-has-no-choice") mustBe true }
    }

    "be accepted when it holds an input" in { (td: TestData) =>
      val ms = msgs(
        """    page Screen is { menu Real is { button Pick acquires command App.Tap } }""",
        td
      )
      withClue(ms.mkString("\n")) { has(ms, "app-menu-has-no-choice") mustBe false }
    }

    "be accepted when it holds a submenu" in { (td: TestData) =>
      val ms = msgs(
        """    page Screen is { menu Outer is { menu Inner is { button P acquires command App.Tap } } }""",
        td
      )
      withClue(ms.mkString("\n")) { has(ms, "app-menu-has-no-choice") mustBe false }
    }

    "not be reported when it is a `???` stub" in { (td: TestData) =>
      // `checkContents` already says a container should have content; saying it twice about one
      // omission is double-reporting.
      val ms = msgs("""    page Screen is { menu Stub is { ??? } }""", td)
      withClue(ms.mkString("\n")) { has(ms, "app-menu-has-no-choice") mustBe false }
    }
  }

  "a popup" should {

    "be flagged when nothing can open it" in { (td: TestData) =>
      val ms = msgs("""    popup Orphan is { document Note shows record Panel }""", td)
      withClue(ms.mkString("\n")) { has(ms, "app-group-unreachable") mustBe true }
    }

    "be accepted when a group contains it" in { (td: TestData) =>
      val ms = msgs(
        """    popup Reached is { document Note shows record Panel }
          |    page Screen is { contains theDialog as popup App.Reached }""".stripMargin,
        td
      )
      withClue(ms.mkString("\n")) { has(ms, "app-group-unreachable") mustBe false }
    }

    "leave a `page` alone, which is a destination rather than a response" in { (td: TestData) =>
      // Warning on a page would demand epics a model is not obliged to have.
      val ms = msgs("""    page Standalone is { document Note shows record Panel }""", td)
      withClue(ms.mkString("\n")) { has(ms, "app-group-unreachable") mustBe false }
    }
  }
}
