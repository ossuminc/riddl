/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.diagrams

import com.ossuminc.riddl.language.AST.{Domain, Identifier, Root}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.passes.diagrams.{ContextDiagramData, DiagramsPass, DiagramsPassOutput}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.{Await, PlatformContext, URL}
import org.scalatest.TestData
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

abstract class SharedDiagramsPassTest(using PlatformContext) extends AbstractValidatingTest {

  "Diagrams Data" must {
    "construct ContextDiagramData" in { (td: TestData) =>
      val d = Domain(At(), Identifier(At(), "domain"))
      val contextDiagramData = ContextDiagramData(d)
      contextDiagramData.aggregates mustBe empty
      contextDiagramData.relationships mustBe empty
      contextDiagramData.domain mustBe d
    }
    "construct DiagramsPassOutput" in { (td: TestData) =>
      val diagramsPassOutput = DiagramsPassOutput()
      diagramsPassOutput.messages mustBe empty
      diagramsPassOutput.contextDiagrams mustBe empty
      diagramsPassOutput.dataFlowDiagrams mustBe empty
    }
  }
  "DiagramsPass" must {
    "be named correctly" in { (td: TestData) =>
      DiagramsPass.name mustBe "Diagrams"
    }
    "collect actors from interactions nested in a sequence" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Diner is {
          |  user Patron is "a hungry person"
          |  context Kitchen is { ??? }
          |  epic Dining is {
          |    user Patron wants to "eat" so that "hunger ends"
          |    case Nested is {
          |      user Patron wants to "order food" so that "food arrives"
          |      sequence {
          |        step from user Patron "orders from" to context Kitchen
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidateAggregate(input) { passesResult =>
        val pass = new DiagramsPass(passesResult.input, passesResult.outputs)
        val output =
          Pass.runPass[DiagramsPassOutput](passesResult.input, passesResult.outputs, pass)
        val data = output.useCaseDiagrams.values.headOption.getOrElse(
          fail("no use case diagram was produced")
        )
        data.actors.keySet mustBe Set("Patron", "Kitchen")
      }
    }
    "collect actors from interactions nested several containers deep" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Diner is {
          |  user Patron is "a hungry person"
          |  user Waiter is "a server"
          |  context Kitchen is { ??? }
          |  context Bar is { ??? }
          |  context Till is { ??? }
          |  epic Dining is {
          |    user Patron wants to "eat" so that "hunger ends"
          |    case Deep is {
          |      user Patron wants to "order food" so that "food arrives"
          |      sequence {
          |        step from user Patron "orders from" to user Waiter
          |        optional {
          |          step from user Waiter "sends order to" to context Kitchen
          |          parallel {
          |            step from user Waiter "pours at" to context Bar
          |            step from user Waiter "rings up at" to context Till
          |          }
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidateAggregate(input) { passesResult =>
        val pass = new DiagramsPass(passesResult.input, passesResult.outputs)
        val output =
          Pass.runPass[DiagramsPassOutput](passesResult.input, passesResult.outputs, pass)
        val data = output.useCaseDiagrams.values.headOption.getOrElse(
          fail("no use case diagram was produced")
        )
        data.actors.keySet mustBe Set("Patron", "Waiter", "Kitchen", "Bar", "Till")
        // Users must come first — they are drawn on the left side of the diagram — and each
        // actor must appear once despite Waiter being named by four of the five steps.
        data.actors.keys.toSeq.take(2) mustBe Seq("Patron", "Waiter")
      }
    }
    "creator with empty PassesOutput yields IllegalArgumentException" in { (td: TestData) =>
      val creator = DiagramsPass.creator()
      val input = PassInput(Root())
      val outputs = PassesOutput()
      val pass = intercept[IllegalArgumentException] { creator(input, outputs) }
      pass.isInstanceOf[IllegalArgumentException] mustBe true
    }
  }
}
