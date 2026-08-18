/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** What may introduce a state's record reference.
  *
  * `of` is the canonical 2.0 spelling. `is` was also accepted, and because `is` is itself optional
  * so was nothing at all — which left one keyword doing two jobs in a single production, since
  * `stateBody` already uses `is` to introduce the BODY, exactly as every other definition does.
  *
  * The non-canonical spellings are DEPRECATED rather than removed: they are used throughout the
  * test suite and the external corpus, so removing them outright would invalidate a great deal at
  * once. These cases pin both halves of that ruling — the old spellings still parse, AND they say
  * so.
  */
class StateRecordIntroTest extends AbstractParsingTest {

  private def parseState(intro: String, td: TestData): (Boolean, Messages.Messages) =
    val input = RiddlParserInput(
      s"""domain D is { context C is { entity E is {
         |  record Data is { x: Integer }
         |  state S $intro record D.C.E.Data is { handler H is { on other is { do "x" } } }
         |}}}
         |""".stripMargin,
      td
    )
    TopLevelParser.parseInputWithMessages(input) match
      case Left(errors)     => (false, errors)
      case Right((_, msgs)) => (true, msgs)
  end parseState

  "a state's record reference" should {

    "be introduced by `of`, silently" in { (td: TestData) =>
      val (parsed, msgs) = parseState("of", td)
      parsed mustBe true
      withClue(s"the canonical spelling must draw no deprecation:\n${msgs.format}") {
        msgs.filter(_.isDeprecation) mustBe empty
      }
    }

    "still accept `is`, with a deprecation" in { (td: TestData) =>
      val (parsed, msgs) = parseState("is", td)
      parsed mustBe true
      val deprecations = msgs.filter(_.isDeprecation)
      deprecations.size mustBe 1
      deprecations.head.message must include("Use `of`")
    }

    /** `is` is optional, so omitting the introducer entirely has always parsed too. It is the same
      * non-canonical shape and draws the same deprecation.
      */
    "still accept no introducer at all, with a deprecation" in { (td: TestData) =>
      val (parsed, msgs) = parseState("", td)
      parsed mustBe true
      msgs.filter(_.isDeprecation).size mustBe 1
    }
  }
}
