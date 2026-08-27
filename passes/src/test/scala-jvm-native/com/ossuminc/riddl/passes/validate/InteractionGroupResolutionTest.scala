/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** A step inside `sequence`/`parallel`/`optional` must be resolved and validated exactly as the
  * same step is at the top level of a `case`.
  *
  * Until 2.0 it was neither. `ResolutionPass.resolveInteractions` had three arms reading
  * `case _: SequentialInteractions => () // no references`, and that comment was false in the way
  * that matters: the CONTAINER carries no references, but its CONTENTS do, and returning unit
  * dropped every one of them. `ValidationPass.validateUseCase` independently checked only whether
  * each container was EMPTY and never descended. Between them, a model could name commands,
  * entities and users that DO NOT EXIST inside any group and validate green -- exit 0, zero
  * diagnostics -- while the identical step one level out errored correctly.
  *
  * That is a SILENT pass, so no build or CI gate could see it. Reported by ossum.tech 2026-08-08,
  * whose docs-fence validator was reporting hollow passes: a fence referring to five undefined
  * definitions "validated" because every step sat inside a group.
  *
  * The root shape is the one this repo keeps rediscovering -- `InteractionContainer` is a
  * `Container` but NOT a `Branch` (its base `Interaction` is a RiddlValue, not a Definition), so
  * the generic traversal cannot descend into it either. Same family as the SagaStep hole.
  *
  * The test pairs each grouped form against the bare one and asserts they produce the SAME
  * diagnostics, which is stronger than asserting a count and cannot drift as messages are reworded.
  */
class InteractionGroupResolutionTest extends AbstractValidatingTest {

  /** The step is identical in every case; only the wrapper differs. */
  private def model(wrapped: String): String =
    s"""domain D is {
       |  author A is { name is "A" email is "a@b.c" }
       |  user Customer is "a customer"
       |  context C is { entity Cart is { ??? } }
       |  epic E is {
       |    user Customer wants to "x" so that "y"
       |    case TheCase is {
       |      user Customer wants to "x" so that "y"
       |$wrapped
       |    }
       |  }
       |}
       |""".stripMargin

  private val step =
    "      step send command NoSuchCommand from user Customer to entity NoSuchEntity"

  /** Errors mentioning the deliberately undefined names, sorted so ordering cannot matter. */
  private def unresolvedNames(src: String, origin: String): Seq[String] =
    var found = Seq.empty[String]
    parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
      found = messages.justErrors
        .map(_.message)
        .filter(m => m.contains("NoSuchCommand") || m.contains("NoSuchEntity"))
        .sorted
      succeed
    }
    found

  "a step inside an interaction group" should {

    val bare = unresolvedNames(model(step), "bare")

    "produce the same diagnostics bare as it does at the top level (control)" in { (td: TestData) =>
      // The control. If this ever goes empty the rest of the suite proves nothing, because
      // "grouped matches bare" is satisfied by both being silent.
      withClue("the BARE step reported nothing -- the control has broken\n") {
        bare mustNot be(empty)
      }
    }

    "resolve inside `sequence`" in { (td: TestData) =>
      unresolvedNames(model(s"      sequence {\n$step\n      }"), td.name) mustBe bare
    }

    "resolve inside `parallel`" in { (td: TestData) =>
      unresolvedNames(model(s"      parallel {\n$step\n      }"), td.name) mustBe bare
    }

    "resolve inside `optional`" in { (td: TestData) =>
      // `optional` is documented as exempt from ADMISSIBILITY analysis, but that is a different
      // question from whether a named command exists at all. An optional step naming a
      // nonexistent entity is still a broken model.
      unresolvedNames(model(s"      optional {\n$step\n      }"), td.name) mustBe bare
    }

    "resolve when groups are NESTED three deep" in { (td: TestData) =>
      // ossum.tech explicitly flagged depth as unprobed. Recursion handles it, but "handles it"
      // is the kind of claim this repo requires proving.
      val nested =
        s"      parallel {\n        sequence {\n          optional {\n$step\n          }\n        }\n      }"
      unresolvedNames(model(nested), td.name) mustBe bare
    }
  }
}
