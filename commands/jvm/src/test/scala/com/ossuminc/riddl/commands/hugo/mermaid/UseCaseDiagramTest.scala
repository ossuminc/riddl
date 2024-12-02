/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo.mermaid

import com.ossuminc.riddl.diagrams.mermaid.{UseCaseDiagram, UseCaseDiagramSupport}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.diagrams.{DiagramsPass, DiagramsPassOutput}
import com.ossuminc.riddl.passes.{AbstractRunPassTest, PassInput, PassesOutput, PassesResult}
import com.ossuminc.riddl.utils.{Await, URL, ec, pc}
import org.scalatest.TestData

import java.net.URI
import java.nio.file.Path
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

class UseCaseDiagramTest extends AbstractRunPassTest {

  case class TestUCDSupport(passesResult: PassesResult) extends UseCaseDiagramSupport {
    private val symTab = passesResult.symbols
    def makeDocLink(namedValue: Definition): String = {
      val parents = symTab.parentsOf(namedValue)
      val uri = new URI(
        "http",
        "example.com",
        parents.map(_.id.value).mkString("//", "/", "/") + s"${namedValue.id.value}",
        null,
        null
      )
      uri.toASCIIString
    }
  }

  "UseCaseDiagram" should {
    "generate a simple diagram correctly" in { (td: TestData) =>
      val url = URL.fromCwdPath("commands/shared/src/test/input/epic.riddl")
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        val result = runPassesWith(rpi, DiagramsPass.creator())
        val maybeDPO = result.outputOf[DiagramsPassOutput](DiagramsPass.name)
        maybeDPO match
          case Some(dpo: DiagramsPassOutput) =>
            dpo.useCaseDiagrams.headOption match
              case Some((uc, useCaseDiagramData)) =>
                val useCaseDiagram = UseCaseDiagram(TestUCDSupport(result), useCaseDiagramData)
                val lines = useCaseDiagram.generate
                // info(lines.mkString("\n"))
                lines mustNot be(empty)
                succeed
              case None => fail("No use cases or epic")
            end match
          case None => fail("no DiagramsPassOutput")
        end match
      }
      Await.result(future, 10.seconds)
    }
  }

  "UseCaseDiagramSupport" should {
    "getDefinitionFor should work" in { (td: TestData) =>
      val outputs: PassesOutput = PassesOutput()
      val pid = PathIdentifier(At(), Seq("foo"))
      val item = Domain(At(), Identifier(At(), "foo"))
      val parent = Root(At(), Contents(item))
      outputs.refMap.add[Domain](pid, parent, item)
      val passesResult = PassesResult(PassInput.empty, outputs)
      case class TestUseCaseDiagramSupport(passesResult: PassesResult) extends UseCaseDiagramSupport {
        def makeDocLink(definition: Definition): String = ???
      }
      val tucds = TestUseCaseDiagramSupport(passesResult)
      tucds.getDefinitionFor[Domain](pid, parent) match {
        case Some(domain: Domain) => succeed
        case x                    => fail(s"Unexpected: $x")
      }
    }
  }
}
