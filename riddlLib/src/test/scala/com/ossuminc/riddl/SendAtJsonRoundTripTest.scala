/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** `send ... at <instant>` must survive AST -> JSON -> AST. The instant is an optional `"at"` key on
  * the send statement object; a plain send must serialize exactly as before (no `"at"` key at all),
  * so none of the corpus's models move for a feature they do not use. Runs on JVM, JS and Native.
  */
class SendAtJsonRoundTripTest extends AnyWordSpec with Matchers {

  private val model =
    """domain D is {
      |  context C is {
      |    command Book is { id: String, startsAt: TimeStamp }
      |    event ReminderDue is { id: String }
      |    outlet Notify is event ReminderDue
      |    handler H is {
      |      on b: command Book is {
      |        send event ReminderDue(id = b.id) to outlet Notify at b.startsAt
      |        send event ReminderDue(id = b.id) to outlet Notify at system.now
      |        send event ReminderDue(id = b.id) to outlet Notify
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "send ... at JSON round-trip" should {

    "be a JSON-identity fixed point" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) => RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the generated JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) => fail(s"parse of the RIDDL model failed: $errors")
      end match
    }

    "emit an `at` key only for the scheduled sends" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root) =>
          val json = RiddlLib.root2Json(root)
          json.sliding(4).count(_ == "\"at\"") mustBe 2
        case RiddlResult.Failure(errors) => fail(s"parse of the RIDDL model failed: $errors")
      end match
    }

    "rebuild the instants, and none where there was none" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root0) =>
          RiddlLib.parseJson(RiddlLib.root2Json(root0)) match
            case RiddlResult.Success(root1) =>
              val sends = Finder(root1).recursiveFindByType[SendStatement]
              sends.size mustBe 3
              sends.map(_.at.map(_.format)) mustBe Seq(Some("b.startsAt"), Some("system.now"), None)
            case RiddlResult.Failure(errors) => fail(s"parseJson failed: $errors")
          end match
        case RiddlResult.Failure(errors) => fail(s"parse of the RIDDL model failed: $errors")
      end match
    }
  }
}
