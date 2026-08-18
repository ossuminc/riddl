/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.utils.{pc, CommonOptions}

import org.scalatest.TestData

/** What `Contents.definitions` becoming include-transparent means for VALIDATION (2026-08-06).
  *
  * The accessor was made transparent for consumers (synapify's task file of that date), but three
  * validation checks read it, and each was quietly answering the wrong question because it stopped
  * at the `Include` wrapper:
  *
  *   - `checkContents` asked "does this container have content" and got `false` for a container
  *     whose content all arrived through an include -- a MissingWarning about a domain that plainly
  *     has definitions.
  *   - `checkIncludeHygiene` asked "did this include contribute anything" and got `false` for an
  *     include whose file contributes through a FURTHER include.
  *   - `checkUniqueContent` asked "do these siblings have unique names" and could not see across an
  *     include boundary, so `type Thing` beside an included `entity Thing` passed silently even
  *     though ResolutionPass *is* include-transparent and `Ctx.Thing` is genuinely ambiguous.
  *
  * The first two are false positives that go away; the third is a real defect that starts being
  * reported. Reid approved taking the corpus hit for the third on 2026-08-06.
  *
  * Options are pinned rather than defaulted for the reason `IncludeHygieneTest` documents: `pc` is
  * a process-wide singleton whose options sibling suites flip underneath this one.
  */
class IncludeTransparentValidationTest extends JVMAbstractValidatingTest {

  private val withWarnings: CommonOptions =
    CommonOptions(showWarnings = true, showMissingWarnings = true, showStyleWarnings = true)

  "a container whose content arrives entirely through an include" should {

    "not be told it should have content" in { (_: TestData) =>
      // `a51-good-include/main.riddl` is `domain d is { include "types" }` -- nothing else. The
      // domain has two types; only the wrapper stood between the check and them.
      pc.withOptions(withWarnings) { _ =>
        validateFile("include-transparent-content", "a51-good-include/main.riddl") {
          case (_, messages) =>
            val bogus = messages.filter(_.message.contains("should have content"))
            withClue(s"expected no 'should have content' warning, got:\n${messages.format}") {
              bogus mustBe empty
            }
        }
      }
    }
  }

  "an include that contributes only through a further include" should {

    "not be reported as contributing no definitions" in { (_: TestData) =>
      // main -> outer -> inner, where only `inner` holds the types. A51(b) looked at `outer` alone.
      pc.withOptions(withWarnings) { _ =>
        validateFile(
          "include-transparent-nested",
          "include-transparency/nested-include/main.riddl"
        ) { case (_, messages) =>
          val bogus = messages.filter(_.message.contains("Include contributes no definitions"))
          withClue(s"expected no empty-include warning, got:\n${messages.format}") {
            bogus mustBe empty
          }
        }
      }
    }
  }

  "two siblings sharing a name across an include boundary" should {

    "be an ERROR, as they are when written in one file" in { (_: TestData) =>
      // `type Thing` in the context body, `entity Thing` in the included file. ResolutionPass sees
      // both as candidates, so `Ctx.Thing` resolves arbitrarily -- precisely what the same-file
      // form is an Error for (see UniqueSiblingNamesTest).
      pc.withOptions(withWarnings) { _ =>
        validateFile(
          "include-transparent-duplicates",
          "include-transparency/dup-across-include/main.riddl"
        ) { case (_, messages) =>
          val dups = messages.justErrors.filter(_.message.contains("duplicate content names"))
          withClue(
            s"expected a duplicate-name ERROR across the include, got:\n${messages.format}"
          ) {
            dups mustNot be(empty)
          }
        }
      }
    }
  }
}
