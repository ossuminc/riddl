package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.{ReferenceMap, ResolutionOutput, Usages}
import com.ossuminc.riddl.passes.symbols.{Symbols, SymbolsOutput}
import com.ossuminc.riddl.passes.validate.ValidationOutput
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable


/** Test case for Pass and its related classes */
class PassTest extends AnyWordSpec with Matchers {

  "PassOutput" must {
    "have an empty value" in {
      val mt = PassOutput.empty
      mt.messages.isEmpty mustBe true
    }
  }

  "PassesOutput" must {
    "yield values with no input" in {
      val po = PassesOutput()
      po.messages mustBe empty
      po.symbols mustBe SymbolsOutput()
      po.resolution mustBe ResolutionOutput()
      po.validation mustBe ValidationOutput()
      po.refMap mustBe ReferenceMap(Messages.Accumulator.empty)
      po.usage mustBe Usages(mutable.HashMap.empty, mutable.HashMap.empty)
    }
  }

  class TestPass(input: PassInput, output: PassesOutput) extends Pass(input, output) {
    def name: String = TestPass.name
    def postProcess(root: com.ossuminc.riddl.language.AST.Root): Unit = ???
    protected def process(definition: RiddlValue, parents: Symbols.ParentStack): Unit = ???
    def result: com.ossuminc.riddl.passes.PassOutput = ???
  }
  object TestPass extends PassInfo[PassOptions] {
    val name: String = "TestPass"
    override def creator(options: PassOptions): PassCreator =  (input,output) => new TestPass(input, output)
  }

  class TestPass2(input: PassInput, output: PassesOutput) extends Pass(input, output) {
    requires(TestPass)
    def name: String = "TestPass2"
    def postProcess(root: com.ossuminc.riddl.language.AST.Root): Unit = ???
    protected def process(definition: RiddlValue, parents: Symbols.ParentStack): Unit = ???
    def result: com.ossuminc.riddl.passes.PassOutput = ???
  }


  "Pass" must {
    "validate requires method" in {
      val input = PassInput(Root.empty)
      val output = PassesOutput()
      val tp = TestPass(input, output)
      val thrown = intercept[Exception] {
        TestPass2(input, output)
      }
      thrown.getMessage mustBe "requirement failed: Required pass 'TestPass' was not run prior to 'TestPass2'"
    }
  }

  "HierarchyPass" must {
    "have a test" in {
      pending
    }
  }

  "CollectingPass" must {
    "have a test" in {
      pending
    }
  }

}
