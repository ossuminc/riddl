/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A6 (Task 13): a `tell <msg> to <procRef>` should warn when the target processor has no inlet
  * reached by any modeled connector (direct-connector reachability only).
  */
class TellReachabilityTest extends AbstractValidatingTest {

  private def model(connector: String): String =
    s"""domain d is {
       |  context c is {
       |    command Cmd is { x: Integer }
       |    entity E is {
       |      inlet ein is command Cmd
       |      handler eh is { on command Cmd { ??? } }
       |    }
       |    source Src is {
       |      outlet out is command Cmd
       |      handler sh is {
       |        on command Cmd { tell command Cmd to entity E }
       |      }
       |    }
       |    $connector
       |  }
       |}
       |""".stripMargin

  "Tell reachability (A6)" should {

    "warn when a tell target has no connector reaching one of its inlets" in { (td: TestData) =>
      parseAndValidate(model(""), td.name, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          assertValidationMessage(
            msgs,
            Warning,
            "'tell' target 'E' is not reachable via any connector"
          )
      }
    }

    "not warn when a connector reaches the tell target's inlet" in { (td: TestData) =>
      val connector = "connector Pipe is { from outlet c.Src.out to inlet c.E.ein }"
      parseAndValidate(model(connector), td.name, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          msgs.filter(m =>
            m.kind == Warning && m.message.contains("is not reachable via any connector")
          ) mustBe empty
      }
    }
  }
}
