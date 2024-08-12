package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{ParsingTest, RiddlParserInput}
import org.scalatest.{Assertion, TestData}

import java.nio.file.Path

class TestVisitor extends PassVisitor:
  var opens = 0
  var closes = 0
  var depth = 0
  var leaves = 0
  var values = 0
  def incr(): Unit = 
    depth = depth + 1; opens = opens + 1
  def decr(): Unit = 
    require(depth > 0); depth = depth - 1; closes = closes + 1
  def leaf(): Unit = leaves += 1
  def value(): Unit = values += 1
  def openType(typ: Type, parents: Parents): Unit = incr()
  def openDomain(domain: Domain, parents: Parents): Unit = incr()
  def openContext(context: Context, parents: Parents): Unit = incr()
  def openEntity(entity: Entity, parents: Parents): Unit = incr()
  def openAdaptor(adaptor: Adaptor, parents: Parents): Unit = incr()
  def openEpic(epic: Epic, parents: Parents): Unit = incr()
  def openUseCase(uc: UseCase, parents: Parents): Unit = incr()
  def openFunction(function: Function, parents: Parents): Unit = incr()
  def openSaga(saga: Saga, parents: Parents): Unit = incr()
  def openStreamlet(streamlet: Streamlet, parents: Parents): Unit = incr()
  def openRepository(repository: Repository, parents: Parents): Unit = incr()
  def openProjector(projector: Projector, parents: Parents): Unit = incr()
  def openHandler(handler: Handler, parents: Parents): Unit = incr()
  def openOnClause(onClause: OnClause, parents: Parents): Unit = incr()
  def openApplication(application: Application, parents: Parents): Unit = incr()
  def openGroup(group: Group, parents: Parents): Unit = incr()
  def openOutput(output: Output, parents: Parents): Unit = incr()
  def openInput(input: Input, parents: Parents): Unit = incr()

  // Close for each type of container definition
  def closeType(typ: Type, parents: Parents): Unit = decr()
  def closeDomain(domain: Domain, parents: Parents): Unit = decr()
  def closeContext(context: Context, parents: Parents): Unit = decr()
  def closeEntity(entity: Entity, parents: Parents): Unit = decr()
  def closeAdaptor(adaptor: Adaptor, parents: Parents): Unit = decr()
  def closeEpic(epic: Epic, parents: Parents): Unit = decr()
  def closeUseCase(uc: UseCase, parents: Parents): Unit = decr()
  def closeFunction(function: Function, parents: Parents): Unit = decr()
  def closeSaga(saga: Saga, parents: Parents): Unit = decr()
  def closeStreamlet(streamlet: Streamlet, parents: Parents): Unit = decr()
  def closeRepository(repository: Repository, parents: Parents): Unit = decr()
  def closeProjector(projector: Projector, parents: Parents): Unit = decr()
  def closeHandler(handler: Handler, parents: Parents): Unit = decr()
  def closeOnClause(onClause: OnClause, parents: Parents): Unit = decr()
  def closeApplication(application: Application, parents: Parents): Unit = decr()
  def closeGroup(group: Group, parents: Parents): Unit = decr()
  def closeOutput(output: Output, parents: Parents): Unit = decr()
  def closeInput(input: Input, parents: Parents): Unit = decr()

  // LeafDefinitions
  def doField(field: Field): Unit = leaf()
  def doMethod(method: Method): Unit = leaf()
  def doTerm(term: Term): Unit = leaf()
  def doAuthor(author: Author): Unit = leaf()
  def doConstant(constant: Constant): Unit = leaf()
  def doInvariant(invariant: Invariant): Unit = leaf()
  def doSagaStep(sagaStep: SagaStep): Unit = leaf()
  def doInlet(inlet: Inlet): Unit = leaf()
  def doOutlet(outlet: Outlet): Unit = leaf()
  def doConnector(connector: Connector): Unit = leaf()
  def doUser(user: User): Unit = leaf()
  def doSchema(schema: Schema): Unit = leaf()
  def doState(state: State): Unit = leaf()
  def doEnumerator(enumerator: Enumerator): Unit = leaf()
  def doContainedGroup(containedGroup: ContainedGroup): Unit = leaf()

  // Non Definition values
  def doComment(comment: Comment): Unit = value()
  def doAuthorRef(reference: AuthorRef): Unit = value()
  def doBriefDescription(brief: BriefDescription): Unit = value()
  def doDescription(description: Description): Unit = value()
  def doStatement(statement: Statement): Unit = value()
  def doInteraction(interation: Interaction): Unit = value()
  def doOptionValue(optionValue: OptionValue): Unit = value()
  def doUserStory(userStory: UserStory): Unit = value()
end TestVisitor

case class TestPassOutput(root: Root, messages: Messages) extends PassOutput

class VisitingPassTest extends ParsingTest {

  "VisitingPass" must {
    "descend cleanly" in { td =>
      val path: Path = Path.of("passes/jvm/src/test/input/everything.riddl")
      parsePath(path) match
        case Left(msgs) => fail(msgs.justErrors.format)
        case Right(root) =>
          info(s"root.domains.contents.size= ${root.domains.head.contents.size}")
          val visitor = new TestVisitor
          val input = PassInput(root)
          val outputs = PassesOutput()
          val pass = new VisitingPass(input, outputs, visitor) {
            def name: String = "VisitingPassTest"
            override def result(root: Root): PassOutput = TestPassOutput(root, List.empty)
          }
          val result = Pass.runPass[TestPassOutput](input, outputs, pass)
          info(s"""depth=${visitor.depth}
               |leaves=${visitor.leaves}
               |values=${visitor.values}
               |opens=${visitor.opens}
               |closes=${visitor.closes}
               |""".stripMargin)
          visitor.depth must be(0)
          visitor.leaves must be(20)
          visitor.values must be(13)
          visitor.opens must be(visitor.closes)
      end match
    }
  }
}
