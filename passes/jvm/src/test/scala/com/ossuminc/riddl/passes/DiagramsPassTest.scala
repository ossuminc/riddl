package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{PlatformIOContext, URL}
import com.ossuminc.riddl.passes.{pc,ec}
import com.ossuminc.riddl.passes.diagrams.* 
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import org.scalatest.TestData

class DiagramsPassTest extends SharedDiagramsPassTest {
  "generate diagrams output" in { (td: TestData) =>
    val url = URL.fromCwdPath("language/jvm/src/test/input/everything.riddl")
    val future = RiddlParserInput.fromURL(url, td).map { rpi =>
      parseValidateAndThen(rpi) {
        (passesResult: PassesResult, root: Root, rpi: RiddlParserInput, messages: Messages.Messages) =>
          val pass = new DiagramsPass(passesResult.input, passesResult.outputs)
          val output = Pass.runPass[DiagramsPassOutput](passesResult.input, passesResult.outputs, pass)
          output.messages.justErrors must be(empty)
          output.contextDiagrams must not be (empty)
          output.useCaseDiagrams must not be (empty)
          output.dataFlowDiagrams must be(empty) // FIXME: change to 'not be(empty)' when implemented
      }
    }
    Await.result(future, 10.seconds)
  }
}
