/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.ParsingTest
import com.ossuminc.riddl.utils.{pc, ec, JVMPlatformContext, PlatformContext}

import scala.collection.mutable
import java.nio.file.Path

case class TestPassOutput(root: Root, messages: Messages) extends PassOutput

class VisitingPassTest extends ParsingTest {
  given PlatformContext = JVMPlatformContext()

  "VisitingPass" must {
    "descend cleanly" in { _ =>
      val path: Path = Path.of("language/jvm/src/test/input/everything.riddl")
      parsePath(path) match
        case Left(msgs)  => fail(msgs.justErrors.format)
        case Right(root) =>
          // info(s"root.domains.contents.size= ${root.domains.head.contents.size}")
          val visitor = new TestVisitor
          val input = PassInput(root)
          val outputs = PassesOutput()
          val pass = new VisitingPass(input, outputs, visitor) {
            def name: String = "VisitingPassTest"
            override def result(root: Root): PassOutput = TestPassOutput(root, List.empty)
          }
          Pass.runPass[TestPassOutput](input, outputs, pass)
          // info(visitor.kindMap.toSeq.sortBy(_._1).map(_.toString).mkString("\n"))
          // info(s"""depth=${visitor.depth}
          //      |leaves=${visitor.leaves}
          //      |values=${visitor.values}
          //      |opens=${visitor.opens}
          //      |closes=${visitor.closes}
          //      |""".stripMargin)
          visitor.depth must be(0)
          visitor.leaves must be(19)
          visitor.values must be(25)
          visitor.opens must be(visitor.closes)
      end match
    }
  }
}

class TestVisitor extends PassVisitor:
  var opens = 0
  var closes = 0
  var depth = 0
  var leaves = 0
  var values = 0
  val kindMap: mutable.HashMap[String, Int] = mutable.HashMap.empty
  def incr(parent: Container[?]): Unit =
    depth = depth + 1
    opens = opens + 1
    val old = kindMap.getOrElse(parent.kind, 0)
    kindMap.put(parent.kind, old + 1)

  def decr(parent: Container[?]): Unit =
    require(depth > 0); depth = depth - 1; closes = closes + 1

  def leaf(leaf: LeafDefinition): Unit =
    leaves = leaves + 1
    val old = kindMap.getOrElse(leaf.kind, 0)
    kindMap.put(leaf.kind, old + 1)

  def value(value: RiddlValue): Unit =
    values = values + 1
    val old = kindMap.getOrElse(value.kind, 0)
    kindMap.put(value.kind, old + 1)

  def openType(typ: Type, parents: Parents): Unit = incr(typ)
  def openDomain(domain: Domain, parents: Parents): Unit = incr(domain)
  def openContext(context: Context, parents: Parents): Unit = incr(context)
  def openEntity(entity: Entity, parents: Parents): Unit = incr(entity)
  def openAdaptor(adaptor: Adaptor, parents: Parents): Unit = incr(adaptor)
  def openEpic(epic: Epic, parents: Parents): Unit = incr(epic)
  def openUseCase(uc: UseCase, parents: Parents): Unit = incr(uc)
  def openFunction(function: Function, parents: Parents): Unit = incr(function)
  def openSaga(saga: Saga, parents: Parents): Unit = incr(saga)
  def openStreamlet(streamlet: Streamlet, parents: Parents): Unit = incr(streamlet)
  def openRepository(repository: Repository, parents: Parents): Unit = incr(repository)
  def openProjector(projector: Projector, parents: Parents): Unit = incr(projector)
  def openHandler(handler: Handler, parents: Parents): Unit = incr(handler)
  def openOnClause(onClause: OnClause, parents: Parents): Unit = incr(onClause)
  def openApplication(application: Application, parents: Parents): Unit = incr(application)
  def openGroup(group: Group, parents: Parents): Unit = incr(group)
  def openOutput(output: Output, parents: Parents): Unit = incr(output)
  def openInput(input: Input, parents: Parents): Unit = incr(input)
  def openInclude(include: Include[?], parents: Parents): Unit = incr(include)

  // Close for each type of container definition
  def closeType(typ: Type, parents: Parents): Unit = decr(typ)
  def closeDomain(domain: Domain, parents: Parents): Unit = decr(domain)
  def closeContext(context: Context, parents: Parents): Unit = decr(context)
  def closeEntity(entity: Entity, parents: Parents): Unit = decr(entity)
  def closeAdaptor(adaptor: Adaptor, parents: Parents): Unit = decr(adaptor)
  def closeEpic(epic: Epic, parents: Parents): Unit = decr(epic)
  def closeUseCase(uc: UseCase, parents: Parents): Unit = decr(uc)
  def closeFunction(function: Function, parents: Parents): Unit = decr(function)
  def closeSaga(saga: Saga, parents: Parents): Unit = decr(saga)
  def closeStreamlet(streamlet: Streamlet, parents: Parents): Unit = decr(streamlet)
  def closeRepository(repository: Repository, parents: Parents): Unit = decr(repository)
  def closeProjector(projector: Projector, parents: Parents): Unit = decr(projector)
  def closeHandler(handler: Handler, parents: Parents): Unit = decr(handler)
  def closeOnClause(onClause: OnClause, parents: Parents): Unit = decr(onClause)
  def closeApplication(application: Application, parents: Parents): Unit = decr(application)
  def closeGroup(group: Group, parents: Parents): Unit = decr(group)
  def closeOutput(output: Output, parents: Parents): Unit = decr(output)
  def closeInput(input: Input, parents: Parents): Unit = decr(input)
  def closeInclude(include: Include[?], parents: Parents): Unit = decr(include)

  // LeafDefinitions
  def doField(field: Field): Unit = leaf(field)
  def doMethod(method: Method): Unit = leaf(method)
  def doTerm(term: Term): Unit = leaf(term)
  def doAuthor(author: Author): Unit = leaf(author)
  def doConstant(constant: Constant): Unit = leaf(constant)
  def doInvariant(invariant: Invariant): Unit = leaf(invariant)
  def doSagaStep(sagaStep: SagaStep): Unit = leaf(sagaStep)
  def doInlet(inlet: Inlet): Unit = leaf(inlet)
  def doOutlet(outlet: Outlet): Unit = leaf(outlet)
  def doConnector(connector: Connector): Unit = leaf(connector)
  def doUser(user: User): Unit = leaf(user)
  def doSchema(schema: Schema): Unit = leaf(schema)
  def doState(state: State): Unit = leaf(state)
  def doRelationship(relationship: Relationship): Unit = leaf(relationship)
  def doContainedGroup(containedGroup: ContainedGroup): Unit = leaf(containedGroup)

  // Non Definition values
  def doEnumerator(enumerator: Enumerator): Unit = value(enumerator)
  def doComment(comment: Comment): Unit = value(comment)
  def doAuthorRef(reference: AuthorRef): Unit = value(reference)
  def doBriefDescription(brief: BriefDescription): Unit = value(brief)
  def doDescription(description: Description): Unit = value(description)
  def doStatement(statement: Statements): Unit = value(statement)
  def doInteraction(interaction: Interaction): Unit = value(interaction)
  def doOptionValue(optionValue: OptionValue): Unit = value(optionValue)
  def doUserStory(userStory: UserStory): Unit = value(userStory)
end TestVisitor
