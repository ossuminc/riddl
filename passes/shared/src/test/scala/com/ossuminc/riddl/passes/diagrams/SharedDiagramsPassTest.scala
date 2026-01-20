/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.diagrams

import com.ossuminc.riddl.language.AST.{Domain, Identifier, Root}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.passes.diagrams.{ContextDiagramData, DiagramsPass, DiagramsPassOutput}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}
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
    "creator with empty PassesOutput yields IllegalArgumentException" in { (td: TestData) =>
      val creator = DiagramsPass.creator()
      val input = PassInput(Root())
      val outputs = PassesOutput()
      val pass = intercept[IllegalArgumentException] { creator(input, outputs) }
      pass.isInstanceOf[IllegalArgumentException] mustBe true
    }
  }
}
