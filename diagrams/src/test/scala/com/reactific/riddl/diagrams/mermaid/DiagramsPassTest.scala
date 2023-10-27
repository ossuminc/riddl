package com.reactific.riddl.diagrams.mermaid

import com.reactific.riddl.diagrams.{DiagramsPass, DiagramsPassOutput}
import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.passes.{Pass, PassInput, PassesResult}
import com.reactific.riddl.testkit.ValidatingTest

import java.nio.file.Path

/** Unit Tests For DiagramsPassTest */
class DiagramsPassTest extends ValidatingTest {
  "DiagramsPass" must {
    "generate context diagram data" in {
      val file = Path.of("diagrams", "src", "test", "input", "context-relationships.riddl").toFile
      val input = RiddlParserInput(file)
      parseValidateAndThen(input) {
        (passesResult: PassesResult, root: RootContainer, rpi: RiddlParserInput, messages: Messages) =>
          val in = PassInput(root)
          val outputs = passesResult.outputs
          val pass = DiagramsPass(in, passesResult.outputs)
          val out = Pass.runPass[DiagramsPassOutput](in, outputs, pass)
          out.contextDiagrams.size mustBe (5)
      }
    }
  }
}
