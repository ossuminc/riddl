/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.passes.symbols

import com.reactific.riddl.language.AST
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.passes.{ParserOutput, Pass}
import com.reactific.riddl.language.passes.symbols.Symbols.{Parentage, Parents, SymTab, SymTabItem}

import scala.collection.mutable

/** Symbol Table for Validation and other purposes. This symbol table is built
  * from the AST model after syntactic parsing is complete. It will also work
  * for any sub-tree of the model that is rooted by a ParentDefOf[Definition]
  * node.
  *
  * The symbol tree contains a mapping from leaf name to the entire list of
  * parent definitions (symbols) as well as a mapping from definitions to their
  * parents (parentage). Bot maps are built during a single pass of the AST.
  *
  * @param input
  *   The output of the parser pass is the input to SymbolPass
  */
case class SymbolsPass(input: ParserOutput) extends Pass[ParserOutput,SymbolsOutput](input) {

  override def name: String = "symbols"

  private val symTab: SymTab = mutable.HashMap.empty[String, Seq[SymTabItem]]

  private val parentage: Parentage = mutable.HashMap.empty[Definition, Parents]

  override def postProcess(): Unit = ()

  private def rootLessParents(parents: Seq[Definition]): Parents = {
    parents.filter {
      case _: RootContainer => false
      case _ => true
    }
  }

  def processADefinition(definition: Definition, parents: Seq[Definition]): Unit = {
    definition match {
      case _: RootContainer => // ignore
      case d: Definition if d.isImplicit => // Implicit (nameless) things, like includes, don't go in symbol table
      case definition: Definition =>
        val name = definition.id.value
        if (name.nonEmpty) {
          val copy: Parents = rootLessParents(parents)
          val existing = symTab.getOrElse(name, Seq.empty[SymTabItem])
          val included: Seq[SymTabItem] = existing :+ (definition -> copy)
          symTab.update(name, included)
          parentage.update(definition, copy)
        }
    }
  }

  override def result: SymbolsOutput = SymbolsOutput(input.root, input.commonOptions, input.messages,
    symTab, parentage)

  override def close: Unit = ()

  /**
   * Process one leaf definition from the model. Leaf definitions occur
   * at the leaves of the definitional hierarchy, and have no further children
   *
   * @param leaf
   * The definition to consider
   * @param parents
   * The parents of the definition as a stack from nearest to the Root
   */
  override def processLeafDefinition(leaf: AST.LeafDefinition, parents: Seq[AST.Definition]): Unit =
    processADefinition(leaf, parents)

  override def processHandlerDefinition(hndlrDef: AST.HandlerDefinition, parents: Seq[AST.Definition]): Unit =
    processADefinition(hndlrDef, parents)

  override def processApplicationDefinition(appDef: AST.ApplicationDefinition, parents: Seq[AST.Definition]): Unit =
    processADefinition(appDef, parents)

  override def processEntityDefinition(entDef: AST.EntityDefinition, parents: Seq[AST.Definition]): Unit =
    processADefinition(entDef, parents)

  override def processRepositoryDefinition(repoDef: AST.RepositoryDefinition, parents: Seq[AST.Definition]): Unit =
    processADefinition(repoDef, parents)

  override def processProjectorDefinition(projDef: AST.ProjectorDefinition, parents: Seq[AST.Definition]): Unit =
    processADefinition(projDef, parents)

  override def processSagaDefinition(sagaDef: AST.SagaDefinition, parents: Seq[AST.Definition]): Unit =
    processADefinition(sagaDef, parents)

  override def processContextDefinition(contextDef: AST.ContextDefinition, parents: Seq[AST.Definition]): Unit =
    processADefinition(contextDef, parents)

  override def processDomainDefinition(domDef: AST.DomainDefinition, parents: Seq[AST.Definition]): Unit =
    processADefinition(domDef, parents)

  override def processAdaptorDefinition(adaptDef: AdaptorDefinition, parents: Seq[Definition]): Unit =
    processADefinition(adaptDef, parents)

  override def processEpicDefinition(epicDef: EpicDefinition, parents: Seq[Definition]): Unit =
    processADefinition(epicDef, parents)

  override def processFunctionDefinition(funcDef: FunctionDefinition, parents: Seq[Definition]): Unit =
    processADefinition(funcDef, parents)

  override def processOnClauseDefinition(ocd: OnClauseDefinition, parents: Seq[Definition]): Unit =
    processADefinition(ocd, parents)

  override def processRootDefinition(rootDef: RootDefinition, parents: Seq[Definition]): Unit =
    processADefinition(rootDef, parents)

  override def processStateDefinition(stateDef: StateDefinition, parents: Seq[Definition]): Unit =
    processADefinition(stateDef, parents)

  override def processStreamletDefinition(streamDef: StreamletDefinition, parents: Seq[Definition]): Unit =
    processADefinition(streamDef, parents)

  override def processUseCaseDefinition(useCaseDef: UseCaseDefinition, parents: Seq[Definition]): Unit =
    processADefinition(useCaseDef, parents)
}

