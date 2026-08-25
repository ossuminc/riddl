/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.json.JsonModel
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The do-statement's JSON discriminator is `"do"`, and `"prompt"` still reads (Reid, 2026-08-25).
  *
  * `do` is the canonical keyword and `prompt` the deprecated synonym, but the wire said `"prompt"`
  * for the STATEMENT while `{"value":"prompt", ...}` means the typed-hole VALUE — so one string
  * meant two different things depending on which field it sat in. That is the wire-level version of
  * the confusion the `PromptStatement` -> `DoStatement` rename fixes in the AST.
  *
  * **Reading both spellings is not politeness, it is necessary**: every JSON written before this
  * change carries `"prompt"`, and rejecting it would strand those files for no gain. Writing only
  * `"do"` is what removes the ambiguity going forward.
  */
class DoDiscriminatorTest extends AnyWordSpec with Matchers {

  private val src =
    """domain D is {
      |  context C is {
      |    command Go is { what: String(1,9) }
      |    record R is { a: String(1,9) }
      |    entity E is {
      |      state S of record C.R is { ??? }
      |      handler H is { on command C.Go is { do "handle it" } }
      |    }
      |  }
      |}
      |""".stripMargin

  private def jsonOf(text: String): String =
    RiddlLib.parseString(text, "do-disc") match
      case RiddlResult.Success(root) => RiddlLib.root2Json(root)
      case other => fail(s"parse failed: $other")

  "the do-statement discriminator" should {

    "be written as \"do\"" in {
      // Whitespace-tolerant: root2Json pretty-prints, so the pair is `"kind": "do"`.
      val json = jsonOf(src)
      withClue(json.take(400)) {
        """"kind"\s*:\s*"do"""".r.findFirstIn(json) mustBe defined
      }
    }

    "no longer be written as \"prompt\"" in {
      // `"prompt"` may still appear as a typed-hole VALUE discriminator; what must be gone is the
      // statement KIND. Asserting on the exact pair keeps the two apart.
      val json = jsonOf(src)
      withClue(json.take(400)) {
        """"kind"\s*:\s*"prompt"""".r.findFirstIn(json) mustBe None
      }
    }

    "still READ a file that carries the old \"prompt\" spelling" in {
      val current = jsonOf(src)
      val legacy = """"kind"(\s*:\s*)"do"""".r.replaceAllIn(current, m => s""""kind"${m.group(1)}"prompt"""")
      // Prove the fixture really IS the old shape before concluding anything from it loading.
      """"kind"\s*:\s*"prompt"""".r.findFirstIn(legacy) mustBe defined
      RiddlLib.parseJson(legacy, "legacy") match
        case RiddlResult.Success(_) => succeed
        case other => fail(s"legacy 'prompt' spelling no longer reads: $other")
    }
  }
}
