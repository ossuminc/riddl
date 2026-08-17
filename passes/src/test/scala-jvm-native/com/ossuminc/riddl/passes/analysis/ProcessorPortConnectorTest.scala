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

/** [2.4] A connector between ports owned by NON-Streamlet processors produces a flow edge.
  *
  * Since the unified processor model every Processor is port-bearing — `WithInlets`/`WithOutlets`
  * are mixed into the Processor base — but `MessageFlowPass.processConnector` matched the concrete
  * `Streamlet` case class when walking from a port up to its owner, and otherwise looked at the
  * port's GRANDparent. For an Entity's own outlet that grandparent is the Context, which is not a
  * Processor, so the walk answered `None` and the edge was dropped in silence: no warning, no
  * error, just a connector missing from the graph the simulator and generator consume.
  */
class ProcessorPortConnectorTest extends AbstractValidatingTest {

  private val entityPorts =
    """domain D is {
      |  context C is {
      |    event Evt is { id: String }
      |    entity Source is {
      |      outlet Out is type D.C.Evt
      |      handler H is { on event D.C.Evt is { ??? } }
      |    }
      |    entity Target is {
      |      inlet In is type D.C.Evt
      |      handler H is { on event D.C.Evt is { ??? } }
      |    }
      |    connector Pipe from outlet D.C.Source.Out to inlet D.C.Target.In
      |  }
      |}
      |""".stripMargin

  "a connector between two entities' own ports" should {
    "produce a ConnectorPipe edge naming the ENTITIES, not the ports" in { (td: TestData) =>
      val rpi = RiddlParserInput(entityPorts, "test")
      parseValidateAndThen(rpi, shouldFailOnErrors = false) {
        (pr: PassesResult, root: Root, _, _: Messages.Messages) =>
          val passInput = PassInput(root)
          val pass = MessageFlowPass(passInput, pr.outputs)
          val mfo = Pass.runPass[MessageFlowOutput](passInput, pr.outputs, pass)
          val pipes = mfo.edges.filter(_.mechanism == FlowMechanism.ConnectorPipe)
          withClue(s"edges were: ${mfo.edges.map(e => e.producer.id.value -> e.consumer.id.value)}") {
            pipes must not be empty
          }
          pipes.head.producer.id.value mustBe "Source"
          pipes.head.consumer.id.value mustBe "Target"
          pipes.head.messageType.map(_.id.value) mustBe Some("Evt")
      }
    }
  }
}
