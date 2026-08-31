/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{AbstractTestingBasis, CommonOptions, pc}

/** `Message.format` reads `noANSIMessages` at CALL time, so WHERE it is called decides the result.
  *
  * This is the mechanism behind ossum.ai's 2026-08-27 report that
  * `RiddlAPI.validateString(..., noANSIMessages = true)` returned raw escapes, and that passing
  * `true` and `false` produced byte-identical output. The parameter was not ignored — it was
  * applied to the wrong window. `RiddlLib.validateString` scopes it around the parse and pass run
  * and returns Message OBJECTS; nothing is rendered until the facade calls `format`, by which time
  * the scope has closed and the ambient default (`false`) applies.
  *
  * The fix renders inside the scope. This suite pins the property that makes that fix necessary
  * and correct: identical messages, formatted under different options, must differ.
  *
  * **What this does NOT cover**: `RiddlAPI` is Scala.js-only and `riddlLib` has no JS test tree,
  * so the facade's own wrapping is not directly asserted here. Said plainly rather than implied —
  * a suite that looks like it covers the reported surface, and does not, is worse than one whose
  * scope is stated.
  */
class AnsiSuppressionTest extends AbstractTestingBasis {

  private val Escape: Char = 27.toChar

  private val src =
    """domain D is {
      |  context C is {
      |    entity E is { ??? }
      |  }
      |}
      |""".stripMargin

  /** Validate once, then render the SAME messages under a given setting — mirroring the real
    * sequence, where rendering happens after validation rather than during it.
    */
  private def renderedUnder(noANSI: Boolean): String =
    val messages =
      pc.withOptions(CommonOptions(noANSIMessages = true)) { _ =>
        RiddlLib.validateString(src, "ansi-test").all
      }
    pc.withOptions(pc.options.copy(noANSIMessages = noANSI)) { _ =>
      messages.map(_.format).mkString("\n")
    }

  "message rendering" should {

    "emit NO ANSI escapes when noANSIMessages is set at FORMAT time" in {
      val text = renderedUnder(noANSI = true)
      withClue(s"rendered text still contains an escape:\n$text\n") {
        text.contains(Escape) mustBe false
      }
    }

    /** The control, and the reason the bug was invisible: without it, a fix that never emitted
      * escapes at all would pass the assertion above while having removed the feature. It also
      * demonstrates the property the report turned on — the SAME messages render differently
      * depending only on the options in force when `format` is called.
      */
    "still emit escapes when the option is NOT set, proving the flag is what matters" in {
      val plain = renderedUnder(noANSI = true)
      val coloured = renderedUnder(noANSI = false)
      withClue("colouring is driven by the option; if these are equal the flag is inert again\n") {
        coloured must not be plain
      }
      coloured.contains(Escape) mustBe true
    }
  }
}
