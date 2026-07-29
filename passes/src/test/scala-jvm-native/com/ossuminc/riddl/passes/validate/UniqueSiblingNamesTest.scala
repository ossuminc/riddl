/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** A definition's nested definitions must have unique names — REGARDLESS OF KIND.
  *
  * The check used to group by `identify`, which is `Kind 'name'`, so it only caught same-kind
  * collisions: `type Thing` beside `entity Thing` in one context passed silently. A path identifier
  * names ONE thing, so two same-named siblings make `Ctx.Thing` ambiguous and whichever resolution
  * wins is arbitrary — which is exactly the kind of imprecision RIDDL exists to prevent.
  */
class UniqueSiblingNamesTest extends AbstractValidatingTest {

  "sibling names" should {

    "be an ERROR when two siblings share a name across different kinds" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Dom is {
          |  context Ctx is {
          |    type Thing is Integer
          |    entity Thing is { handler H is { on other is { do "x" } } }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
        val dups = messages.justErrors.filter(_.message.contains("duplicate content names"))
        withClue(s"expected a duplicate-name ERROR, got:\n${messages.format}") {
          dups mustNot be(empty)
        }
      }
    }

    "be an ERROR when two siblings share a name and a kind" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Dom is {
          |  context Ctx is {
          |    type Thing is Integer
          |    type Thing is String
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
        messages.justErrors.filter(_.message.contains("duplicate content names")) mustNot be(empty)
      }
    }

    /** Same name under DIFFERENT parents is legal and must stay legal — the full path distinguishes
      * them. This is the shape `api-management.riddl` has, with a `FromEntity` inlet on each of two
      * processors, and it is not a violation.
      */
    "be legal when the same name appears under different parents" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Dom is {
          |  context Ctx is {
          |    event Ev is { x: Integer }
          |    processor Splitter as sink is { inlet FromEntity is event Dom.Ctx.Ev }
          |    processor Store as sink is { inlet FromEntity is event Dom.Ctx.Ev }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
        withClue(s"same name under different parents must NOT be an error:\n${messages.format}") {
          messages.justErrors.filter(_.message.contains("duplicate content names")) mustBe empty
        }
      }
    }
  }
}
