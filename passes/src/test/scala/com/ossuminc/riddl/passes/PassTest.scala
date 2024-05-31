package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.Messages.Accumulator
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.language.{CommonOptions, Messages}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.{ReferenceMap, ResolutionOutput, Usages}
import com.ossuminc.riddl.passes.symbols.Symbols.Parents
import com.ossuminc.riddl.passes.symbols.{Symbols, SymbolsOutput}
import com.ossuminc.riddl.passes.validate.ValidationOutput
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable
import java.nio.file.Path

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



  "Pass" must {
    "validate requires method" in {
      val input = PassInput(Root.empty)
      val output = PassesOutput()
      val tp = TestPass(input, output)
      val thrown = intercept[IllegalArgumentException] {
        TestPass2(input, output)
      }
      thrown.getMessage mustBe "requirement failed: Required pass 'TestPass' was not run prior to 'TestPass2'"
    }

    "runValidation works" in {
      val testInput = RiddlParserInput(Path.of("language/src/test/input/everything.riddl"))
      Riddl.parse(testInput) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root) =>
          val input = PassInput(root, CommonOptions.empty)
          val result = Pass.runThesePasses(input, Pass.standardPasses)
          if result.messages.hasErrors then fail(result.messages.justErrors.format)
          val outputs = PassesOutput()
          val vo = Pass.runValidation(input, result.outputs)
          vo.messages.justErrors mustBe empty
    }

    "runThesePasses catches exceptions" in {
      val testInput = RiddlParserInput(Path.of("language/src/test/input/everything.riddl"))
      Riddl.parse(testInput) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root) =>
          val input = PassInput(root, CommonOptions.empty)
          val result = Pass.runThesePasses(input, Pass.standardPasses)
          if result.messages.hasErrors then fail(result.messages.justErrors.format)
          succeed
    }
  }

  case class TestHierarchyPass(input: PassInput, outputs: PassesOutput) extends HierarchyPass(input, outputs) {
    def processForTest(node: RiddlValue, parents: Symbols.ParentStack): (Int, Int, Int, Int) = {
      super.process(node, parents)
      (opens, closes, leaves, values)
    }

    var (opens, closes, leaves, values) = (0, 0, 0, 0)
    override protected def openContainer(definition: Definition, parents: Parents): Unit = opens = opens + 1

    override protected def processLeaf(definition: LeafDefinition, parents: Parents): Unit = leaves = leaves + 1

    override protected def closeContainer(definition: Definition, parents: Parents): Unit = closes = closes + 1

    override protected def processValue(value: RiddlValue, parents: Parents): Unit = values = values + 1

    override def name: String = "TestHierarchyPass"

    override def postProcess(root: Root): Unit = ()

    override def result: PassOutput = PassOutput.empty
  }

  "HierarchyPass" must {
    "traverses all kinds of nodes" in {
      val testInput = RiddlParserInput(Path.of("language/src/test/input/everything.riddl"))
      Riddl.parseAndValidate(testInput) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(result: PassesResult) =>
          val input = PassInput(result.root)
          val outputs = PassesOutput()
          val hp = TestHierarchyPass(input, outputs)
          val out: PassOutput = Pass.runPass[PassOutput](input, outputs, hp)
          val (opens, closes, leaves, values) = hp.processForTest(result.root, mutable.Stack.empty)
          opens.mustBe(closes)
          opens.mustBe(51)
          values.mustBe(19)
          leaves.mustBe(25)
    }
    "traverses partial trees" in {
      val input = RiddlParserInput(
        """domain foo is { context bar is {
          | /* comment */
          | term baz is briefly "a character in a play"
          |}}
          |""".stripMargin
      )
      Riddl.parseAndValidate(input) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(result: PassesResult) =>
          val input = PassInput(result.root)
          val outputs = PassesOutput()
          val hp = TestHierarchyPass(input, outputs)
          val out: PassOutput = Pass.runPass[PassOutput](input, outputs, hp)
          val (opens, closes, leaves, values) = hp.processForTest(result.root, mutable.Stack.empty)
          opens must be(closes)
          opens must be(3)
          values must be(1)
          leaves must be(1)

    }
  }
}
