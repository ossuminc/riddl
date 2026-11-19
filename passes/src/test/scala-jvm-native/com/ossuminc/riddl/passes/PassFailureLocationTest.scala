/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Message
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{AbstractTestingBasisWithTestData, pc}

import org.scalatest.TestData

/** riddl-models, 2026-11-19: an exception thrown inside a pass was reported at `At.empty`, which
  * renders `empty(1:1->1)` and made a ClassCastException in a constructor check read as an
  * empty-file problem. Each `traverse` now names the node it was visiting ([[PassFailure]]), and
  * the last-resort reporter puts the `[severe]` there.
  *
  * One pass per traversal family -- the base [[Pass]], [[DepthFirstPass]] (which processes a parent
  * AFTER its children, the order a "last node visited" variable gets wrong) and [[HierarchyPass]] --
  * plus a failure outside the traversal, which has no node and must name its phase.
  */
class PassFailureLocationTest extends AbstractTestingBasisWithTestData {

  private val src: String =
    """domain Dom is {
      |  context Ctx is {
      |    entity Boom is { ??? }
      |    entity Fine is { ??? }
      |  }
      |}
      |""".stripMargin
  private val boomLine = 3

  private def boom(v: RiddlValue): Unit = v match
    case e: Entity if e.id.value == "Boom" => throw ClassCastException("planted")
    case _                                 => ()

  private class BaseBoom(i: PassInput, o: PassesOutput) extends Pass(i, o):
    def name = "BaseBoom"
    protected def process(definition: RiddlValue, parents: ParentStack): Unit = boom(definition)
    def result(root: PassRoot): PassOutput = PassOutput.empty

  private class DepthBoom(i: PassInput, o: PassesOutput) extends DepthFirstPass(i, o):
    def name = "DepthBoom"
    protected def process(definition: RiddlValue, parents: ParentStack): Unit = boom(definition)
    def result(root: PassRoot): PassOutput = PassOutput.empty

  private class HierarchyBoom(i: PassInput, o: PassesOutput) extends HierarchyPass(i, o):
    def name = "HierarchyBoom"
    protected def openContainer(definition: Definition, parents: Parents): Unit = boom(definition)
    protected def closeContainer(definition: Definition, parents: Parents): Unit = ()
    protected def processLeaf(definition: Leaf, parents: Parents): Unit = ()
    protected def processValue(value: RiddlValue, parents: Parents): Unit = ()
    def result(root: PassRoot): PassOutput = PassOutput.empty

  private class PostBoom(i: PassInput, o: PassesOutput) extends Pass(i, o):
    def name = "PostBoom"
    protected def process(definition: RiddlValue, parents: ParentStack): Unit = ()
    override def postProcess(root: PassRoot): Unit = throw IllegalStateException("planted")
    def result(root: PassRoot): PassOutput = PassOutput.empty

  private def severes(creator: PassCreator, td: TestData): Seq[Message] =
    val root = Riddl.parse(RiddlParserInput(src, td)) match
      case Right(r)   => r
      case Left(msgs) => fail(msgs.map(_.format).mkString("\n"))
    Pass.runThesePasses(PassInput(root), Seq(creator)).messages.filter(_.kind == Messages.SevereError)

  private def mustNameBoom(msgs: Seq[Message], passName: String): Unit =
    withClue(msgs.map(_.format).mkString("\n")) {
      msgs.size mustBe 1
      msgs.head.loc.line mustBe boomLine
      msgs.head.message must include(s"The $passName pass failed while processing Entity 'Boom'")
      msgs.head.message must include("ClassCastException: planted")
    }

  "A pass that throws" must {
    "report the node it was visiting, in a base Pass" in { (td: TestData) =>
      mustNameBoom(severes((i, o) => BaseBoom(i, o), td), "BaseBoom")
    }
    "report the node it was visiting, in a DepthFirstPass" in { (td: TestData) =>
      mustNameBoom(severes((i, o) => DepthBoom(i, o), td), "DepthBoom")
    }
    "report the node it was visiting, in a HierarchyPass" in { (td: TestData) =>
      mustNameBoom(severes((i, o) => HierarchyBoom(i, o), td), "HierarchyBoom")
    }
    "name its phase when it throws outside the traversal" in { (td: TestData) =>
      val msgs = severes((i, o) => PostBoom(i, o), td)
      withClue(msgs.map(_.format).mkString("\n")) {
        msgs.size mustBe 1
        msgs.head.message must include("The PostBoom pass failed while processing its postProcess step")
        msgs.head.message must include("IllegalStateException: planted")
      }
    }
  }
}
