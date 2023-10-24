/*
 * Copyright 2023 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.diagrams

import com.reactific.riddl.passes.{Pass, PassInfo, PassInput, PassOutput, PassesOutput}
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import scala.collection.mutable

/** The information needed to generate a Data Flow Diagram. DFDs are generated
  * for each [[Context]] and consist of the streaming components that that
  * are connected.
  *
  */
case class DataFlowDiagramData()

/** The information needed to generate a Use Case Diagram. The diagram for a
  * use case is very similar to a Sequence Diagram showing the interactions
  * between involved components of the model.
  */
case class UseCaseDiagramData()
case class ContextDiagramData()

case class DiagramsPassOutput(
  messages: Messages.Messages = Messages.empty,
  dataFlowDiagrams: Map[Context, DataFlowDiagramData] = Map.empty,
  userCaseDiagrams: Map[Epic, Seq[UseCaseDiagramData]] = Map.empty,
  contextDiagrams: Map[Context, ContextDiagramData] = Map.empty
) extends PassOutput

class DiagramsPass(input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

  def name: String = DiagramsPass.name

  private val dataFlowDiagrams: mutable.HashMap[Context, DataFlowDiagramData] = mutable.HashMap.empty
  private val useCaseDiagrams: mutable.HashMap[Epic, Seq[UseCaseDiagramData]] = mutable.HashMap.empty
  private val contextDiagrams: mutable.HashMap[Context, ContextDiagramData] = mutable.HashMap.empty

  protected def process(definition: Definition, parents: mutable.Stack[Definition]): Unit = {}

  def postProcess(root: com.reactific.riddl.language.AST.RootContainer): Unit = {}

  def result: DiagramsPassOutput = DiagramsPassOutput(
    messages.toMessages,
    dataFlowDiagrams.toMap,
    useCaseDiagrams.toMap,
    contextDiagrams.toMap
  )
}

object DiagramsPass extends PassInfo {
  val name = "Diagrams"
}
