/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{pc, CommonOptions}

import org.scalatest.TestData

/** A52: a single-valued metadata kind (brief description, ULID) appearing more than once in a
  * definition is redundant — only the first is used, so the extras draw a StyleWarning.
  */
class MetadataCardinalityTest extends AbstractValidatingTest {

  "Metadata cardinality (A52)" should {

    "emit a StyleWarning when a definition has two brief descriptions" in { (td: TestData) =>
      val input =
        """domain d is {
          |  type T is Integer
          |} with { briefly "one" briefly "two" }
          |""".stripMargin
      val rpi = RiddlParserInput(input, td)
      // Establish default options (style warnings on): suites run sequentially and pc's global
      // options can be left suppressed by an earlier suite (withOptions has no finally-restore).
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
          val dups = msgs.filter(m =>
            m.kind == Messages.StyleWarning &&
              m.message.contains("multiple 'brief description' metadata")
          )
          dups.size mustBe 1
        }
      }
    }

    "not warn when a definition has a single brief description" in { (td: TestData) =>
      val input =
        """domain d is {
          |  type T is Integer
          |} with { briefly "only one" }
          |""".stripMargin
      val rpi = RiddlParserInput(input, td)
      parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        msgs.filter(_.message.contains("only the first is used")) mustBe empty
      }
    }
  }
}
