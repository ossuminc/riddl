/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** riddl-models, 2026-11-21 (CM §20.4): a `yield` is published on every outlet of its processor
  * that admits its type, so a `send` of the same value to one of those outlets beside it puts a
  * second copy on that outlet. reactive-bbq carries ~100 such pairs (`TableOrder.riddl`).
  */
class SendDuplicatesYieldTest extends AbstractValidatingTest {

  private def model(clause: String): String =
    s"""domain D is {
       |  context C is {
       |    command Add yields event Added is { id: String } with { briefly "c" }
       |    event Added is { id: String } with { briefly "e" }
       |    query Find replies result Found is { id: String } with { briefly "q" }
       |    result Found is { id: String } with { briefly "r" }
       |    entity E is {
       |      inlet In is command C.Add with { briefly "i" }
       |      inlet QIn is query C.Find with { briefly "i" }
       |      outlet Out is event C.Added with { briefly "o" }
       |      outlet Answers is result C.Found with { briefly "o" }
       |      handler H is {
       |        on a: command C.Add is {
       |$clause
       |        }
       |        on f: query C.Find is {
       |          reply result C.Found(id = f.id)
       |          send result C.Found(id = f.id) to outlet C.E.Answers
       |        }
       |      } with { briefly "h" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def found(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse:\n${m.format}")
      case None    => captured.filter(_.ruleId.contains(RuleId.SendDuplicatesYield))

  private def onAdd(msgs: Messages): Messages = msgs.filter(_.message.contains("'yield'"))

  "a send beside a yield of the same value" should {
    "be reported when the send follows the yield" in { (td: TestData) =>
      val ms = onAdd(found(model("""          yield event C.Added(id = a.id)
                                   |          send event C.Added(id = a.id) to outlet C.E.Out""".stripMargin), td.name))
      withClue(ms.map(_.format).mkString("\n")) {
        ms must have size 1
        ms.head.kind mustBe Messages.StyleWarning
        ms.head.message must include("Out")
      }
    }
    "be reported when the send precedes the yield" in { (td: TestData) =>
      val ms = onAdd(found(model("""          send event C.Added(id = a.id) to outlet C.E.Out
                                   |          yield event C.Added(id = a.id)""".stripMargin), td.name))
      ms must have size 1
    }
    "be reported for a reply and a send of the same result" in { (td: TestData) =>
      val ms = found(model("          yield event C.Added(id = a.id)"), td.name)
        .filter(_.message.contains("'reply'"))
      ms must have size 1
    }
    "not be reported for a send of a DIFFERENT value" in { (td: TestData) =>
      val ms = onAdd(found(model("""          yield event C.Added(id = a.id)
                                   |          send event C.Added(id = "other") to outlet C.E.Out""".stripMargin), td.name))
      ms mustBe empty
    }
    "not be reported across statement lists" in { (td: TestData) =>
      val ms = onAdd(found(model("""          when a.id == "x" then
                                   |            yield event C.Added(id = a.id)
                                   |          else
                                   |            send event C.Added(id = a.id) to outlet C.E.Out
                                   |            yield event C.Added(id = "y")
                                   |          end""".stripMargin), td.name))
      ms mustBe empty
    }
    "not be reported for a yield alone" in { (td: TestData) =>
      onAdd(found(model("          yield event C.Added(id = a.id)"), td.name)) mustBe empty
    }
  }
}
