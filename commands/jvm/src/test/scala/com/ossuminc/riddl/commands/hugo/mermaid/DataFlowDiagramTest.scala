/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo.mermaid

import com.ossuminc.riddl.diagrams.mermaid.DataFlowDiagram
import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.AST.Domain
import com.ossuminc.riddl.language.AST.Nebula
import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.AST.VitalDefinition
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.passes.validate.JVMAbstractValidatingTest
import com.ossuminc.riddl.utils.{ec, pc, Await, URL}
import org.scalatest.TestData

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

class DataFlowDiagramTest extends JVMAbstractValidatingTest {
  "DataFlowDiagram" should {
    "generate a simple diagram correctly" in { (td: TestData) =>
      val url = URL.fromCwdPath("language/input/everything.riddl")
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        simpleParseAndValidate(rpi) match {
          case Left(messages) => fail(messages.justErrors.format)
          case Right(passesResult: PassesResult) =>
            val dfd = DataFlowDiagram(passesResult)
            val domains = 
              passesResult.root match
                case root1: Root => AST.getTopLevelDomains(root1)
                case nebula: Nebula => nebula.contents.filter[Domain]
                case vital: VitalDefinition[?] => vital.contents.filter[Domain]
              end match
            val contexts = AST.getContexts(domains.head)
            val actual = dfd.generate(contexts.head)
            val expected =
              """flowchart LR
                |Commands[\"Outlet Source.Commands"\]
                |Commands[/"Inlet Sink.Commands"/]
                |APlant[{"Context Everything.APlant"}]
                |command ACommand["OnMessageClause adaptCommands.command ACommand"]
                |Commands -- Type 'DoAThing' --> Commands
                |""".stripMargin
            actual must be(expected)
        }
      }
      Await.result(future, 10.seconds)
    }
  }
}
