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

/** A6 (Task 13): a `tell <msg> to <procRef>` requires a modelled channel from the SENDER to the
  * target.
  *
  * **Strengthened and promoted to an Error, 2026-09-02.** It previously asked only whether the
  * TARGET had any inbound connector, which any target that anything feeds satisfies -- so six
  * unreachable tells in reactive-bbq validated clean. It is now transitive sender-to-target
  * reachability over the connector graph, and an Error, because CM §17 states it as a hard
  * requirement and a generator handed an unmodelled delivery has nothing to emit.
  *
  * Sharing a context does not excuse the channel (Reid, 2026-08-18): `tell` is sugar for a send
  * on the outlet connected to the target's inlet, so the author is being told to model it.
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

    "ERROR when no connector carries the message from the sender to the target" in {
      (td: TestData) =>
        parseAndValidate(model(""), td.name, shouldFailOnErrors = false) {
          case (_, _, msgs: Messages) =>
            assertValidationMessage(
              msgs,
              Error,
              "'tell' target 'E' is not reachable from"
            )
        }
    }

    "not warn when a connector reaches the tell target's inlet" in { (td: TestData) =>
      val connector = "connector Pipe is { from outlet c.Src.out to inlet c.E.ein }"
      parseAndValidate(model(connector), td.name, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          msgs.filter(m => m.message.contains("is not reachable from")) mustBe empty
      }
    }

    // The shape that escaped the old check, and the reason this suite exists in its present form.
    // riddl-generator reported six of these in reactive-bbq; the model validated at
    // "0 errors, 0 warnings" because Kitchen, Bar and Loyalty each had connectors from the app
    // contexts -- just not from the contexts telling them. Written as a MODEL THAT VIOLATES THE
    // RULE rather than as a unit test over the predicate, per the reporter's own lesson: "the
    // validator checks X" is verified by running a model that violates X.
    "ERROR when the only connector into the target comes from a THIRD party" in { (td: TestData) =>
      val src =
        """domain d is {
          |  command Cmd is { x: Integer }
          |  context Sender is {
          |    handler h is { on command d.Cmd { tell command d.Cmd to context d.Target } }
          |  }
          |  context Target is {
          |    inlet tin is command d.Cmd
          |    handler th is { on command d.Cmd { ??? } }
          |  }
          |  context Unrelated is {
          |    outlet uout is command d.Cmd
          |    handler uh is { on command d.Cmd { ??? } }
          |  }
          |  connector Elsewhere is { from outlet d.Unrelated.uout to inlet d.Target.tin }
          |}
          |""".stripMargin
      parseAndValidate(src, td.name, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          // The label is the path AS WRITTEN (`tellTargetLabel` formats the written path), so a
          // qualified target reads `d.Target`. Asserting `'Target'` passed nothing but my own
          // assumption -- the instrumented run is what showed the real string.
          assertValidationMessage(msgs, Error, "'tell' target 'd.Target' is not reachable from")
      }
    }

    // Reachability is TRANSITIVE. A direct-connector test would reject this, and rejecting a
    // legitimate pipeline is expensive now that the rule is an Error rather than a warning.
    "accept a channel that routes through an intermediate context" in { (td: TestData) =>
      val src =
        """domain d is {
          |  command Cmd is { x: Integer }
          |  context Sender is {
          |    outlet sout is command d.Cmd
          |    handler h is { on command d.Cmd { tell command d.Cmd to context d.Target } }
          |  }
          |  context Relay is {
          |    inlet rin is command d.Cmd
          |    outlet rout is command d.Cmd
          |    handler rh is { on command d.Cmd { ??? } }
          |  }
          |  context Target is {
          |    inlet tin is command d.Cmd
          |    handler th is { on command d.Cmd { ??? } }
          |  }
          |  connector One is { from outlet d.Sender.sout to inlet d.Relay.rin }
          |  connector Two is { from outlet d.Relay.rout to inlet d.Target.tin }
          |}
          |""".stripMargin
      parseAndValidate(src, td.name, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          msgs.filter(m => m.message.contains("is not reachable from")) mustBe empty
      }
    }

    // The sender's enclosing CONTEXT is a legitimate origin: a message leaving a context does so
    // on the context's own outlet (2026-08-18), so demanding a connector from the adaptor itself
    // would contradict the rule that the context is the port at the boundary. This is the exact
    // shape of reactive-bbq's six sites -- a tell inside an adaptor, crossing a boundary.
    "accept a tell from inside an adaptor when its CONTEXT reaches the target" in {
      (td: TestData) =>
        val src =
          """domain d is {
            |  command Cmd is { x: Integer }
            |  context Sender is {
            |    outlet sout is command d.Cmd
            |    adaptor A to context d.Target is {
            |      handler ah is { on command d.Cmd { tell command d.Cmd to context d.Target } }
            |    }
            |  }
            |  context Target is {
            |    inlet tin is command d.Cmd
            |    handler th is { on command d.Cmd { ??? } }
            |  }
            |  connector Cross is { from outlet d.Sender.sout to inlet d.Target.tin }
            |}
            |""".stripMargin
        parseAndValidate(src, td.name, shouldFailOnErrors = false) {
          case (_, _, msgs: Messages) =>
            msgs.filter(m => m.message.contains("is not reachable from")) mustBe empty
        }
    }

    // Pins the exemption. Without a case here, widening it -- or letting it swallow a target that
    // DOES declare an inlet -- would look exactly like the rule working.
    "stay SILENT when the target declares no inlet at all, which is a DIFFERENT diagnostic" in {
      (td: TestData) =>
        val src =
          """domain d is {
            |  command Cmd is { x: Integer }
            |  context Sender is {
            |    handler h is { on command d.Cmd { tell command d.Cmd to context d.Target } }
            |  }
            |  context Target is {
            |    entity Inner is {
            |      handler ih is { on command d.Cmd { do "handle it" } }
            |    }
            |    handler th is { on command d.Cmd { do "route it" } }
            |  }
            |}
            |""".stripMargin
        parseAndValidate(src, td.name, shouldFailOnErrors = false) {
          case (_, _, msgs: Messages) =>
            msgs.filter(m => m.message.contains("is not reachable from")) mustBe empty
            // ...and the omission IS reported, by the check that owns it. Asserting the silence
            // alone would pass equally if nothing diagnosed the model at all -- which is exactly
            // what the first draft of this case did: it used a `???` body, the standing `???`
            // ruling exempted the target from the companion check too, and "silent" then proved
            // nothing. A real body is what makes the companion diagnostic reachable.
            msgs.map(_.message).mkString("\n") must include("inlet")
        }
    }
  }
}
