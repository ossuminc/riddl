/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.Messages.DeprecationCode
import com.ossuminc.riddl.utils.{AbstractTestingBasis, pc}

/** Targeted deprecation fixing: the edits must resolve the deprecation and change NOTHING else.
  *
  * The whole point of an edit list over `root2RiddlSource` is that a user with carefully arranged
  * source does not pay for a wholesale reformat to fix three keywords. So these tests assert on the
  * resulting TEXT, not merely that edits were produced — a reformat that happened to fix the
  * deprecation would pass a weaker test and defeat the feature.
  */
class DeprecationEditsTest extends AbstractTestingBasis {

  /** Apply edits the way a consumer would. They arrive in descending order precisely so this naive
    * loop is correct; applying ascending without adjusting offsets would corrupt the file.
    */
  private def applyEdits(source: String, edits: Seq[SourceEdit]): String =
    edits.foldLeft(source) { (text, e) =>
      text.substring(0, e.start) + e.replacement + text.substring(e.end)
    }

  private val deprecated =
    """domain D is {
      |  context C is {
      |    command PlaceOrder yields event OrderPlaced is { id: Integer }
      |    event OrderPlaced is { id: Integer }
      |    entity E is {
      |      record Fields is { id: Integer }
      |      state Current of record E.Fields
      |      handler H is {
      |        on command PlaceOrder { prompt "note it" yield event OrderPlaced }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "deprecationEdits" should {

    "replace only the deprecated keyword, leaving the rest byte-identical" in {
      val edits = RiddlLib.deprecationEdits(deprecated, "dep.riddl").toEither match
        case Left(msgs) => fail(msgs.format)
        case Right(es)  => es
      edits must not be empty
      // Sample deprecation moved from `reply`->`yield` to `prompt`->`do` at 2.0: `reply` stopped
      // being deprecated when it became the query-result statement, so its code no longer exists.
      edits.map(_.code) must contain(DeprecationCode.PromptStatement)

      val fixed = applyEdits(deprecated, edits)
      fixed must include("""do "note it"""")
      fixed must not include "prompt \"note it\""
      // The load-bearing assertion: everything else is untouched. Comparing the two texts with
      // the one keyword normalised proves no reformatting crept in.
      fixed mustBe deprecated.replace("""prompt "note it"""", """do "note it"""")
    }

    "produce source that no longer reports the deprecation" in {
      val edits = RiddlLib.deprecationEdits(deprecated, "dep.riddl").toEither.toOption.get
      val fixed = applyEdits(deprecated, edits)
      val remaining = RiddlLib.deprecationEdits(fixed, "dep.riddl").toEither.toOption.get
      remaining.map(_.code) must not contain DeprecationCode.PromptStatement
    }

    "return edits in descending start order so naive application is safe" in {
      // Two deprecations in one file: applying ascending without offset adjustment would place
      // the second edit at a stale offset.
      val two = deprecated.replace(
        """on command PlaceOrder { prompt "note it" yield event OrderPlaced }""",
        "on command PlaceOrder { prompt \"note it\"\n          prompt \"tell someone\"\n" +
          "          yield event OrderPlaced }"
      )
      val edits = RiddlLib.deprecationEdits(two, "two.riddl").toEither.toOption.get
      edits.size must be >= 2
      edits.map(_.start) mustBe edits.map(_.start).sorted.reverse
      val fixed = applyEdits(two, edits)
      fixed must include("do \"note it\"")
      fixed must include("do \"tell someone\"")
    }

    "omit deprecations whose fix is not a pure span replacement" in {
      // `shape-keyword` and `state-is-record` need an insertion elsewhere or a human decision.
      // Emitting a guess for them would corrupt source, so they must be absent.
      val edits = RiddlLib.deprecationEdits(deprecated, "dep.riddl").toEither.toOption.get
      edits.map(_.code).foreach { c =>
        DeprecationCode.mechanicalReplacement.keySet must contain(c)
      }
    }

    "report a parse failure rather than silently returning no edits" in {
      RiddlLib.deprecationEdits("domain D is { this is not riddl", "bad.riddl").toEither match
        case Left(msgs) => msgs.hasErrors mustBe true
        case Right(es)  => fail(s"expected a parse failure, got ${es.size} edits")
    }
  }
}
