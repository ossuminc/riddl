/*
 * Copyright 2023 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.diagrams

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, Messages}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.ResolutionPass
import com.ossuminc.riddl.passes.symbols.SymbolsPass
import com.ossuminc.riddl.passes.validate.ValidationPass

import scala.collection.mutable

/** The information needed to generate a Data Flow Diagram. DFDs are generated for each
  * [[com.ossuminc.riddl.language.AST.Context]] and consist of the
  * streaming components that that are connected.
  */
case class DataFlowDiagramData()

/** The information needed to generate a Use Case Diagram. The diagram for a use case is very similar to a Sequence
  * Diagram showing the interactions between involved components of the model.
  */
case class UseCaseDiagramData(
  name: String,
  actors: Map[String,Definition],
  interactions: Seq[Interaction]
)

/** The information needed to generate a Context Diagram showing the relationships between bounded contexts
  *
  * @param domain
  *   The domain or subdomain to which the context map pertains
  * @param aggregates
  *   The aggregate entities involved in the context relationships
  * @param relationships
  *   The relationships between contexts
  */
case class ContextDiagramData(
  domain: Domain,
  aggregates: Seq[Entity] = Seq.empty,
  relationships: Seq[(Context, String)]
)

case class DiagramsPassOutput(
  messages: Messages.Messages = Messages.empty,
  dataFlowDiagrams: Map[Context, DataFlowDiagramData] = Map.empty,
  userCaseDiagrams: Map[Epic, Seq[UseCaseDiagramData]] = Map.empty,
  contextDiagrams: Map[Context, ContextDiagramData] = Map.empty
) extends PassOutput

class DiagramsPass(input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

  def name: String = DiagramsPass.name

  requires(SymbolsPass)
  requires(ResolutionPass)
  requires(ValidationPass)

  private val refMap = outputs.refMap
  private val symTab = outputs.symbols

  private val dataFlowDiagrams: mutable.HashMap[Context, DataFlowDiagramData] = mutable.HashMap.empty
  private val useCaseDiagrams: mutable.HashMap[Epic, Seq[UseCaseDiagramData]] = mutable.HashMap.empty
  private val contextDiagrams: mutable.HashMap[Context, ContextDiagramData] = mutable.HashMap.empty

  protected def process(definition: RiddlValue, parents: mutable.Stack[Definition]): Unit = {
    definition match
      case c: Context =>
        val aggregates = c.entities.filter(_.hasOption[EntityIsAggregate])
        val relationships = findRelationships(c)
        val domain = parents.head.asInstanceOf[Domain]
        contextDiagrams.put(c, ContextDiagramData(domain, aggregates, relationships))
      case epic: Epic =>
        val data = epic.cases.map(uc => captureUseCase(uc, epic) )
        useCaseDiagrams.put(epic, data)
      case _ => ()
  }

  private def findRelationships(context: AST.Context): Seq[(Context, String)] = {
    val statements: Seq[Statement] = pullStatements(context) ++
      context.entities.flatMap(pullStatements) ++
      context.adaptors.flatMap(pullStatements) ++
      context.repositories.flatMap(pullStatements) ++
      context.streamlets.flatMap(pullStatements)

    for {
      s <- statements
      ref <- getStatementReferences(s)
      definition <- refMap.definitionOf(ref, context)
      relationship <- inferRelationship(context, definition)
    } yield {
      context -> relationship
    }
  }

  private def getStatementReferences(statement: Statement): Seq[Reference[Definition]] = {
    statement match
      case SendStatement(_, msg, portlet)   => Seq(msg, portlet)
      case TellStatement(_, msg, processor) => Seq(msg, processor)
      case SetStatement(_, field, _)        => Seq(field)
      case ReplyStatement(_, message)       => Seq(message)
      case _                                => Seq.empty
  }

  private def inferRelationship(context: Context, definition: Definition): Option[String] = {
    definition match
      case m: Type if m.typ.isContainer && m.typ.hasDefinitions =>
        symTab.contextOf(m).map(c => s"Uses ${m.identify} in ${c.identify}")
      case f: Field =>
        symTab.contextOf(f).map(c => s"Sets ${f.identify} in ${c.identify}")
      case p: Portlet =>
        symTab.contextOf(p).map(c => s"Sends to ${p.identify} in ${c.identify}")
      case p: Processor[?, ?] =>
        symTab.contextOf(p).map(c => s"Tells to ${p.identify} in ${c.identify}")
      case _ => None
  }

  private def pullStatements(processor: Processor[?, ?]): Seq[Statement] = {
    val s1 = processor.functions.flatMap(_.statements)
    val s2 = for {
      h <- processor.handlers
      omc <- h.clauses if omc.isInstanceOf[OnMessageClause]
      s <- omc.statements
    } yield { s }
    s1 ++ s2
  }

  private def actorsFirst(a: (String, Definition), b: (String, Definition)): Boolean = {
    a._2 match
      case _: User if b._2.isInstanceOf[User] => a._1 < b._1
      case _: User => true
      case _: Definition if b._2.isInstanceOf[User] => false
      case _: Definition => a._1 < b._1
  }

  private def captureUseCase(uc: UseCase, epic: Epic): UseCaseDiagramData = {
    val actors: Map[String, Definition] = {
      uc.contents.map {
          case tri: TwoReferenceInteraction =>
            val fromDef = refMap.definitionOf[Definition](tri.from.pathId, uc)
            val toDef = refMap.definitionOf[Definition](tri.to.pathId, uc)
            Seq(
              tri.from.pathId.format -> fromDef,
              tri.to.pathId.format -> toDef
            )
          case _: InteractionContainer | _: Interaction | _: Comment => Seq.empty 
        }
        .filterNot(_.isEmpty) // ignore empty things with no references
        .flatten // get rid of seq of seq
        .filterNot(_._1.isEmpty)
        .map(x => x._1 -> x._2.getOrElse(Root.empty))
        .distinctBy(_._1) // reduce to the distinct ones
        .sortWith(actorsFirst)
        .toMap
    }
    UseCaseDiagramData(uc.identify, actors, uc.contents.filter[Interaction])
  }


  def postProcess(root: Root): Unit = {}

  def result: DiagramsPassOutput = {
    DiagramsPassOutput(
      messages.toMessages,
      dataFlowDiagrams.toMap,
      useCaseDiagrams.toMap,
      contextDiagrams.toMap
    )
  }
}

object DiagramsPass extends PassInfo {
  val name: String = "Diagrams"
}
