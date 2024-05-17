package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.RiddlValue
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.symbols.Symbols

case class TestPassOutput() extends PassOutput {
  override def messages: Messages = List.empty
}

class TestPass(input: PassInput, output: PassesOutput) extends Pass(input, output) {
  def name: String = TestPass.name

  def postProcess(root: com.ossuminc.riddl.language.AST.Root): Unit = ()

  protected def process(definition: RiddlValue, parents: Symbols.ParentStack): Unit = ()

  def result: com.ossuminc.riddl.passes.PassOutput = TestPassOutput()
}

object TestPass extends PassInfo[PassOptions] {
  val name: String = "TestPass"

  override def creator(options: PassOptions): PassCreator = (input, output) => new TestPass(input, output)
}

class TestPass2(input: PassInput, output: PassesOutput) extends Pass(input, output) {
  requires(TestPass)

  def name: String = "TestPass2"

  def postProcess(root: com.ossuminc.riddl.language.AST.Root): Unit = ???

  protected def process(definition: RiddlValue, parents: Symbols.ParentStack): Unit = {}

  def result: com.ossuminc.riddl.passes.PassOutput = ???
}

object TestPass2 extends PassInfo[PassOptions] {
  val name: String = "TestPass2"
  override def creator(options: PassOptions): PassCreator = (input, output) => new TestPass2(input, output)
}
