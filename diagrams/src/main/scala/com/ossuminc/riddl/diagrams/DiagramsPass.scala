/*
 * Copyright 2023 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.diagrams

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, Messages}
import com.ossuminc.riddl.passes.*

import scala.collection.mutable

/** The information needed to generate a Data Flow Diagram. DFDs are generated for each [[Context]] and consist of the
  * streaming components that that are connected.
  */
case class DataFlowDiagramData()

/** The information needed to generate a Use Case Diagram. The diagram for a use case is very similar to a Sequence
  * Diagram showing the interactions between involved components of the model.
  */
case class UseCaseDiagramData()

/** The information needed to generate a Context Diagram showing the relationships between bounded contexts
  */
case class ContextDiagramData(
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

  private val refMap = outputs.refMap
  private val symTab = outputs.symbols

  private val dataFlowDiagrams: mutable.HashMap[Context, DataFlowDiagramData] = mutable.HashMap.empty
  private val useCaseDiagrams: mutable.HashMap[Epic, Seq[UseCaseDiagramData]] = mutable.HashMap.empty
  private val contextDiagrams: mutable.HashMap[Context, ContextDiagramData] = mutable.HashMap.empty

  protected def process(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    definition match
      case c: Context =>
        val aggregates = c.entities.filter(_.hasOption[EntityIsAggregate])
        val relationships = findRelationships(c)
        contextDiagrams.put(c, ContextDiagramData(aggregates, relationships))
      case _ => ()
  }

  def findRelationships(context: AST.Context): Seq[(Context, String)] = {
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

  def getStatementReferences(statement: Statement): Seq[Reference[Definition]] = {
    statement match
      case SendStatement(_, msg, portlet)   => Seq(msg, portlet)
      case TellStatement(_, msg, processor) => Seq(msg, processor)
      case SetStatement(_, field, _)        => Seq(field)
      case ReplyStatement(_, message)       => Seq(message)
      case _                                => Seq.empty
  }

  def inferRelationship(context: Context, definition: Definition): Option[String] = {
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

  def pullStatements(processor: Processor[?, ?]): Seq[Statement] = {
    val s1 = processor.functions.flatMap(_.statements)
    val s2 = for {
      h <- processor.handlers
      omc <- h.clauses if omc.isInstanceOf[OnMessageClause]
      s <- omc.statements
    } yield { s }
    s1 ++ s2
  }

  def postProcess(root: RootContainer): Unit = {}

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
