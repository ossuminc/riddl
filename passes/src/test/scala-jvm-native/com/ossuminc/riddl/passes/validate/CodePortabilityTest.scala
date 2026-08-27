/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** A27: `code(language, body)` is a sanctioned escape hatch, and the deal attached to sanctioning
  * it is that every use stays visible — "the validator warns about portability on EVERY use".
  *
  * The requirement was written when the item was filed and **never built**; a repo-wide search on
  * 2026-08-26 found no portability diagnostic anywhere, only an emptiness check. Two
  * reconciliations missed it because the item reads as fully delivered.
  *
  * **The severity is the part to not "fix" later.** It is a StyleWarning because, since Reid's
  * 2026-08-27 generability ruling, anything above a StyleWarning makes a model non-generable —
  * so promoting this would make every use of the hatch block the generation the hatch exists to
  * serve. `stays generable` below pins exactly that, and it is the case a well-meaning severity
  * bump would break.
  */
class CodePortabilityTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def portability(src: String, origin: String): Messages =
    diagnostics(src, origin).filter(_.message.contains("is not portable"))

  private def model(bodies: String): String =
    s"""domain D is {
       |  context C is {
       |    command Go is { id: String }
       |    record R is { n: Integer }
       |    entity E is {
       |      inlet in is command Go
       |      state S of record D.C.R is {
       |        handler H is {
       |          on command Go is {
       |$bodies
       |          }
       |        }
       |      }
       |    }
       |  }
       |}""".stripMargin

  private val oneCode = model("            ```scala\nval x = 1\n```")

  "the code portability warning" should {

    "fire on a single use" in { (td: TestData) =>
      val found = portability(oneCode, td.name)
      found.size mustBe 1
      found.head.kind mustBe Messages.StyleWarning
      found.head.ruleId.map(_.code) mustBe Some("stmt-code-not-portable")
    }

    "name the language, since that is what the model is now tied to" in { (td: TestData) =>
      portability(oneCode, td.name).head.message must include("scala")
    }

    // A27 says EVERY use. A per-language summary would hide how many sites there are, which is
    // the opposite of the visibility the hatch was sanctioned in exchange for.
    "fire once per occurrence, not once per language" in { (td: TestData) =>
      val two = model("            ```scala\nval x = 1\n```\n            ```scala\nval y = 2\n```")
      portability(two, td.name).size mustBe 2
    }

    "say nothing when there is no code statement" in { (td: TestData) =>
      portability(model("""            do "the ordinary way""""), td.name) mustBe empty
    }

    // The control that protects the severity choice.
    "leave the model GENERABLE — the hatch must not block the generation it exists to serve" in {
      (td: TestData) =>
        val onlyPortability = diagnostics(oneCode, td.name).filter(_.message.contains("is not portable"))
        onlyPortability.isGenerable mustBe true
        onlyPortability.head.kind.isGenerable mustBe true
    }
  }
}
