/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.utils.CommonOptions
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

import scala.runtime.stdLibPatches.Predef.assert

/** Validate files */
class ValidateExamplesTest extends JVMAbstractValidatingTest {

  val files: Map[String, String] = Map(
    "Reactive BBQ" -> "rbbq.riddl",
    "Group Found" -> "unfound-group.riddl"
  )

  "ValidateExamples" should {
    "validate all with no errors or warnings" in { (td: TestData) =>
      for ((label, fileName) <- files) yield {
        validateFile(label, fileName) { case (_, messages) =>
          val errors = messages.justErrors
          val warnings = messages.justWarnings.filterNot(_.message.contains("unused"))
          // info(s"Errors:\n${errors.format}")
          // info(s"Warnings:\n${warnings.format}")
          errors mustBe empty
          warnings mustNot be(empty)
        }
      }
    }
  }

  "Enumerations" should {
    "enforce Enumerators to start with lower case" in { (td: TestData) =>
      validateFile(label = "t0001", fileName = "enumerations/t0001.riddl") { case (_, messages) =>
        assertValidationMessage(messages, "style warnings")(_.kind.isStyle)
      }
    }
    "allow enumerators with values" in { (td: TestData) =>
      validateFile("t0002", "enumerations/t0002.riddl") { case (_, messages) =>
        assert(!messages.exists(_.kind.isError))
      }
    }
  }
  "Mappings" should {
    "allow ranges" in { (td: TestData) =>
      validateFile("t0001", "mappings/t0001.riddl") { case (_, messages) =>
        assert(!messages.exists(_.kind.isError))
      }
    }
  }

  "Ranges" should {
    "allow ranges" in { (td: TestData) =>
      validateFile("t0001", "ranges/t0001.riddl") { case (_, messages) =>
        assert(!messages.exists(_.kind.isError))
      }
    }
  }

  "options.showStyleWarnings" should {
    "determine if style warnings are returned from validation" in { (td: TestData) =>
      pc.withOptions(CommonOptions(showStyleWarnings = false)) { _ =>
        validateFile(
          label = "badstyle",
          fileName = "domains/badstyle.riddl"
        ) { case (_, messages) =>
          assert(!messages.exists(_.kind.isError))
          assert(!messages.exists(_.kind.isStyle))
          assert(messages.exists(_.kind.isMissing))
        }
      }
      validateFile(
        label = "badstyle",
        fileName = "domains/badstyle.riddl"
      ) { case (_, messages) =>
        assert(!messages.exists(_.kind.isError))
        assert(messages.exists(_.kind.isStyle))
        assert(messages.exists(_.kind.isMissing))
      }
    }
  }
  "options.showMissingWarnings" should {
    "determine if missing warnings are returned from validation" in { (td: TestData) =>
      pc.withOptions(CommonOptions(showMissingWarnings = false)) { _ =>
        validateFile(
          label = "badstyle",
          fileName = "domains/badstyle.riddl"
        ) { case (_, messages) =>
          assert(!messages.exists(_.kind.isError))
          assert(!messages.exists(_.kind.isMissing))
        }
      }
      validateFile(
        label = "badstyle",
        fileName = "domains/badstyle.riddl"
      ) { case (_, messages) =>
        assert(!messages.exists(_.kind.isError))
        assert(messages.exists(_.kind.isMissing))
      }
    }
  }
}
