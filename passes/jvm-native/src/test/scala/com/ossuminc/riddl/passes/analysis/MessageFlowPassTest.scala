/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesResult}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.{pc, ec}

import org.scalatest.*

class MessageFlowPassTest extends AbstractValidatingTest {

  private def runMessageFlowPass(
    input: String,
    origin: String = "test"
  )(
    check: (MessageFlowOutput, Messages.Messages) => Assertion
  ): Assertion =
    val rpi = RiddlParserInput(input, origin)
    parseValidateAndThen(rpi, shouldFailOnErrors = false) {
      (pr: PassesResult, root: Root, _, msgs: Messages.Messages) =>
        val passInput = PassInput(root)
        val outputs = pr.outputs
        val pass = MessageFlowPass(passInput, outputs)
        val mfo = Pass.runPass[MessageFlowOutput](passInput, outputs, pass)
        check(mfo, msgs)
    }
  end runMessageFlowPass

  "MessageFlowPass" should {
    "detect adaptor bridge edge from declaration with ??? body" in {
      (td: TestData) =>
        runMessageFlowPass(
          """domain D is {
            |  context Source is {
            |    adaptor ToTarget to context D.Target is { ??? }
            |  }
            |  context Target is { ??? }
            |}
            |""".stripMargin
        ) { (mfo, _) =>
          mfo.edges must not be empty
          val bridges = mfo.edges.filter(
            _.mechanism == FlowMechanism.AdaptorBridge
          )
          bridges must not be empty
          // Outbound adaptor: producer=Source, consumer=Target
          bridges.head.producer.id.value mustBe "Source"
          bridges.head.consumer.id.value mustBe "Target"
          bridges.head.messageType mustBe None
        }
    }

    "detect adaptor bridge edge with prompt-only handler" in {
      (td: TestData) =>
        runMessageFlowPass(
          """domain D is {
            |  context Source is {
            |    adaptor ToTarget to context D.Target is {
            |      handler Routing is {
            |        on command D.Target.DoSomething is {
            |          prompt "convert and forward"
            |        }
            |      }
            |    }
            |    type DoSomething is command { ??? }
            |  }
            |  context Target is {
            |    type DoSomething is command { ??? }
            |  }
            |}
            |""".stripMargin
        ) { (mfo, _) =>
          val bridges = mfo.edges.filter(
            _.mechanism == FlowMechanism.AdaptorBridge
          )
          // At minimum, the declaration-level edge exists
          bridges must not be empty
          bridges.exists(_.messageType.isEmpty) mustBe true
        }
    }

    "set correct direction for inbound adaptor" in {
      (td: TestData) =>
        runMessageFlowPass(
          """domain D is {
            |  context Receiver is {
            |    adaptor FromSender from context D.Sender is { ??? }
            |  }
            |  context Sender is { ??? }
            |}
            |""".stripMargin
        ) { (mfo, _) =>
          val bridges = mfo.edges.filter(
            _.mechanism == FlowMechanism.AdaptorBridge
          )
          bridges must not be empty
          // Inbound adaptor: producer=Sender(referent), consumer=Receiver(source)
          bridges.head.producer.id.value mustBe "Sender"
          bridges.head.consumer.id.value mustBe "Receiver"
        }
    }

    "set correct direction for outbound adaptor" in {
      (td: TestData) =>
        runMessageFlowPass(
          """domain D is {
            |  context Source is {
            |    adaptor ToTarget to context D.Target is { ??? }
            |  }
            |  context Target is { ??? }
            |}
            |""".stripMargin
        ) { (mfo, _) =>
          val bridges = mfo.edges.filter(
            _.mechanism == FlowMechanism.AdaptorBridge
          )
          bridges must not be empty
          // Outbound adaptor: producer=Source, consumer=Target(referent)
          bridges.head.producer.id.value mustBe "Source"
          bridges.head.consumer.id.value mustBe "Target"
        }
    }

    "detect tell statement edges" in { (td: TestData) =>
      runMessageFlowPass(
        """domain D is {
          |  context C is {
          |    type Cmd is command { ??? }
          |    type Evt is event { ??? }
          |    entity Sender is {
          |      handler H is {
          |        on command D.C.Cmd is {
          |          tell event D.C.Evt to entity D.C.Receiver
          |        }
          |      }
          |    }
          |    entity Receiver is {
          |      handler H is {
          |        on event D.C.Evt is { ??? }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (mfo, _) =>
        val tells = mfo.edges.filter(
          _.mechanism == FlowMechanism.Tell
        )
        tells must not be empty
        tells.head.producer.id.value mustBe "Sender"
        tells.head.consumer.id.value mustBe "Receiver"
        tells.head.messageType mustBe defined
        tells.head.messageType.get.id.value mustBe "Evt"
      }
    }

    "emit warnings on failed resolution" in { (td: TestData) =>
      runMessageFlowPass(
        """domain D is {
          |  context C is {
          |    entity E is {
          |      handler H is {
          |        on command D.C.NonExistent is {
          |          tell event D.C.AlsoMissing to entity D.C.Ghost
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (mfo, _) =>
        val warnings = mfo.messages.filter(_.kind.isWarning)
        warnings must not be empty
      }
    }
  }
}
