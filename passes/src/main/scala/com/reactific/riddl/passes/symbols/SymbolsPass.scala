/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.symbols

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.passes.{Pass, PassInfo, PassInput, PassesOutput}
import com.reactific.riddl.passes.symbols.Symbols.{Parentage, Parents, SymTab, SymTabItem}

import scala.annotation.unused
import scala.collection.mutable

object SymbolsPass extends PassInfo {
  val name: String = "symbols"
}

/** Symbol Table for Validation and other purposes. This symbol table is built from the AST model after syntactic
  * parsing is complete. It will also work for any sub-tree of the model that is rooted by a ParentDefOf[Definition]
  * node.
  *
  * The symbol tree contains a mapping from leaf name to the entire list of parent definitions (symbols) as well as a
  * mapping from definitions to their parents (parentage). Bot maps are built during a single pass of the AST.
  *
  * @param input
  *   The output of the parser pass is the input to SymbolPass
  */
case class SymbolsPass(input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

  override def name: String = SymbolsPass.name

  private val symTab: SymTab = mutable.HashMap.empty[String, Seq[SymTabItem]]

  private val parentage: Parentage = mutable.HashMap.empty[Definition, Parents]

  override def postProcess(root: RootContainer @unused): Unit = ()

  private def rootLessParents(parents: Seq[Definition]): Parents = {
    parents.filter {
      case _: RootContainer => false
      case _                => true
    }
  }

  def process(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    definition match {
      case _: RootContainer              => // ignore
      case d: Definition if d.isImplicit => // Implicit (nameless) things, like includes, don't go in symbol table
      case definition: Definition =>
        val name = definition.id.value
        if name.nonEmpty then {
          val copy: Parents = rootLessParents(parents.toSeq)
          val existing = symTab.getOrElse(name, Seq.empty[SymTabItem])
          val included: Seq[SymTabItem] = existing :+ (definition -> copy)
          symTab.update(name, included)
          parentage.update(definition, copy)
        }
    }
  }

  override def result: SymbolsOutput = SymbolsOutput(Messages.empty, symTab, parentage)

  override def close(): Unit = ()

}
