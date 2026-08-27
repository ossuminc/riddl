/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{pc, AbstractTestingBasisWithTestData}
import org.scalatest.TestData

/** An edit OUTSIDE any Context must not be invisible to the incremental validator.
  *
  * Fingerprints are computed per Context, so a change to a definition sitting directly in a domain
  * changed nothing the validator could see: it concluded "nothing changed" and served the previous
  * result, hiding a real error. riddl-vscode found this and stopped using the incremental validator
  * over it — for live IDE feedback, silently dropping errors while the user types is worse than
  * being slow.
  */
class IncrementalDomainLevelEditTest extends AbstractTestingBasisWithTestData {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  /** A domain-level type, plus a Context so the model is not degenerate. */
  private val base: String =
    """domain D is {
      |  type Good is String with { briefly "g" }
      |  context C is {
      |    type Inner is String with { briefly "i" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  /** The SAME model with the domain-level type broken. Nothing inside any Context changes. */
  private val edited: String = base.replace("type Good is String", "type Good is Nonexistent")

  private def errorCount(root: Root): Int =
    Pass.runStandardPasses(PassInput(root)).messages.justErrors.size

  "an edit to a domain-level definition" should {

    "be seen by a WARM incremental validator (the reported bug)" in { (td: TestData) =>
      val expected = errorCount(parse(edited, "full"))
      withClue("the fixture must actually produce an error, or this proves nothing: ") {
        expected must be > 0
      }

      val warm = IncrementalValidator()
      val baseCount = warm.validate(parse(base, "base")).messages.justErrors.size
      withClue("the base model must be clean, or the comparison is meaningless: ") {
        baseCount mustBe 0
      }

      // Second call on the WARM validator: rc.8 returned the cached zero here.
      val warmCount = warm.validate(parse(edited, "edited")).messages.justErrors.size
      warmCount mustBe expected
    }

    "be seen by a FRESH incremental validator too (this always worked)" in { (td: TestData) =>
      val fresh = IncrementalValidator()
      fresh.validate(parse(edited, "edited")).messages.justErrors.size mustBe
        errorCount(parse(edited, "full"))
    }
  }

  "an edit INSIDE a Context" should {
    "still be seen -- the existing path is not disturbed" in { (td: TestData) =>
      val brokenInner = base.replace("type Inner is String", "type Inner is AlsoNonexistent")
      val warm = IncrementalValidator()
      warm.validate(parse(base, "base"))
      warm.validate(parse(brokenInner, "edited")).messages.justErrors.size mustBe
        errorCount(parse(brokenInner, "full"))
    }
  }

  "an unchanged model revalidated" should {
    "report the same thing twice -- caching still happens" in { (td: TestData) =>
      val warm = IncrementalValidator()
      val first = warm.validate(parse(base, "base")).messages.justErrors.size
      val second = warm.validate(parse(base, "base-again")).messages.justErrors.size
      second mustBe first
    }
  }
}
