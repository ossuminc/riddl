/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Accumulator
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.resolve.{ReferenceMap, ResolutionOutput, Usages}
import com.ossuminc.riddl.passes.symbols.SymbolsOutput
import com.ossuminc.riddl.passes.validate.ValidationOutput
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.utils.{PathUtils, PlatformContext,AbstractTestingBasisWithTestData}
import com.ossuminc.riddl.utils.{pc,ec, Await}

import scala.collection.mutable
import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

/** Test case for Pass and its related classes */
class PassTest extends AbstractTestingBasisWithTestData {

  "PassOutput" must {
    "have an empty value" in { _ =>
      val mt = PassOutput.empty
      mt.messages.isEmpty mustBe true
    }
  }

  "PassesOutput" must {
    "yield values with no input" in { _ =>
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

    override def postProcess(root: com.ossuminc.riddl.language.AST.Root): Unit = ???

    protected def process(definition: RiddlValue, parents: ParentStack): Unit = ???

    def result(root: Root): PassOutput = ???
  }

  object TestPass extends PassInfo[PassOptions] {
    val name: String = "TestPass"

    override def creator(options: PassOptions)(using PlatformContext): PassCreator = (input, output) =>
      new TestPass(input, output)
  }

  class TestPass2(input: PassInput, output: PassesOutput) extends Pass(input, output) {
    requires(TestPass)

    def name: String = "TestPass2"

    override def postProcess(root: com.ossuminc.riddl.language.AST.Root): Unit = ???

    protected def process(definition: RiddlValue, parents: ParentStack): Unit = {}

    def result(root: Root): PassOutput = ???
  }

  object TestPass2 extends PassInfo[PassOptions] {
    val name: String = "TestPass2"

    override def creator(options: PassOptions)(using PlatformContext): PassCreator = (input, output) =>
      new TestPass2(input, output)
  }

  "Pass" must {
    "validate requires method" in { _ =>
      val input = PassInput(Root.empty)
      val output = PassesOutput()
      TestPass(input, output)
      val thrown = intercept[IllegalArgumentException] {
        TestPass2(input, output)
      }
      thrown.getMessage mustBe "requirement failed: Required pass 'TestPass' was not run prior to 'TestPass2'"
    }

    "runValidation works" in { td =>
      val url = PathUtils.urlFromCwdPath(Path.of("language/jvm/src/test/input/everything.riddl"))
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromURL(url, td).map { rpi =>
        Riddl.parse(rpi) match
          case Left(messages) => fail(messages.justErrors.format)
          case Right(root) =>
            val input = PassInput(root)
            val result = Pass.runThesePasses(input, Pass.standardPasses)
            if result.messages.hasErrors then fail(result.messages.justErrors.format)
            val vo = Pass.runValidation(input, result.outputs)
            vo.messages.justErrors mustBe empty
      }
      Await.result(future, 10.seconds)
    }

    "runThesePasses catches exceptions" in { td =>
      val url = PathUtils.urlFromCwdPath(Path.of("language/jvm/src/test/input/everything.riddl"))
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromURL(url, td).map { rpi =>
        Riddl.parse(rpi) match
          case Left(messages) => fail(messages.justErrors.format)
          case Right(root) =>
            val input = PassInput(root)
            val result = Pass.runThesePasses(input, Pass.standardPasses)
            if result.messages.hasErrors then fail(result.messages.justErrors.format)
            succeed
      }
      Await.result(future, 10.seconds)
    }
  }

  case class TestHierarchyPass(input: PassInput, outputs: PassesOutput) extends HierarchyPass(input, outputs) {
    def processForTest(node: RiddlValue, parents: ParentStack): (Int, Int, Int, Int) = {
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

    override def result(root: Root): PassOutput = PassOutput.empty
  }

  "HierarchyPass" must {
    "traverses all kinds of nodes" in { td =>
      val url = PathUtils.urlFromCwdPath(Path.of("language/jvm/src/test/input/everything.riddl"))
      val future = RiddlParserInput.fromURL(url, td).map { rpi =>
        Riddl.parseAndValidate(rpi) match
          case Left(messages) => fail(messages.justErrors.format)
          case Right(result: PassesResult) =>
            val input = PassInput(result.root)
            val outputs = PassesOutput()
            val hp = TestHierarchyPass(input, outputs)
            Pass.runPass[PassOutput](input, outputs, hp)
            val (opens, closes, leaves, values) = hp.processForTest(result.root, mutable.Stack.empty)
            opens must be(closes)
            opens must be(55)
            values must be(26)
            leaves must be(19)
      }
      Await.result(future, 10.seconds)
    }
    "traverses partial trees" in { td =>
      val input = RiddlParserInput(
        """domain foo is {
          |  context bar is {
          |   /* comment */
          |  } with { term baz is "a character in a play" }
          |}
          |""".stripMargin,
        td
      )
      Riddl.parseAndValidate(input) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(result: PassesResult) =>
          val input = PassInput(result.root)
          val outputs = PassesOutput()
          val hp = TestHierarchyPass(input, outputs)
          Pass.runPass[PassOutput](input, outputs, hp)
          val (opens, closes, leaves, values) = hp.processForTest(result.root, mutable.Stack.empty)
          opens must be(closes)
          opens must be(3)
          values must be(1)
          leaves must be(0)

    }
  }
}
