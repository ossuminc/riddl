/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.utils.{ec, pc}
import org.scalatest.TestData

/** The `everything.riddl` fixture (which `include`s `everything_full.riddl`) exercises the widest
  * spread of RIDDL syntax there is, including entity options that RIDDL published as valid but
  * never registered — every one of which drew a spurious "not a recognized RIDDL option" style
  * warning. Guard against that regressing. `everything_full.riddl` is an include fragment (it opens
  * with `context full is {`) so it must be reached through `everything.riddl`.
  */
class EverythingFullOptionsTest extends JVMAbstractValidatingTest {

  "everything.riddl (including everything_full.riddl)" should {
    "emit no 'not a recognized RIDDL option' warnings" in { (_: TestData) =>
      parseAndValidateTestInput(
        "everything.riddl",
        "everything.riddl",
        directory = "language/input/",
        shouldFailOnErrors = false
      ) { case (_, result) =>
        val unrecognized =
          result.messages.map(_.message).filter(_.contains("is not a recognized RIDDL option"))
        withClue(s"unrecognized options: ${unrecognized.mkString("; ")}") {
          unrecognized mustBe empty
        }
      }
    }
  }
}
