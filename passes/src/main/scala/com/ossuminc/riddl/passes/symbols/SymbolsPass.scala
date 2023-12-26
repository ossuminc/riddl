/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.symbols

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.{Pass, PassInfo, PassInput, PassesOutput}
import com.ossuminc.riddl.passes.symbols.Symbols.{Parentage, Parents, SymTab, SymTabItem}

import scala.annotation.unused
import scala.collection.mutable

object SymbolsPass extends PassInfo {
  val name: String = "Symbols"
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

  override def postProcess(root: Root @unused): Unit = ()

  private def rootLessParents(parents: Seq[Definition]): Parents = {
    parents.filter {
      case _: Root              => false // Roots don't have names and don't matter
      case x: Definition if x.isImplicit => false // Parents with no names don't count
      case _                             => true // Everything else is fair game
    }
  }

  def process(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    definition match {
      case _: Root              => // ignore
      case d: Definition if d.isImplicit => // Implicit (nameless) things, like includes, don't go in symbol table
      case definition: Definition =>
        val name = definition.id.value
        if name.nonEmpty then {
          val parentsCopy: Parents = rootLessParents(parents.toSeq)
          val existing = symTab.getOrElse(name, Seq.empty[SymTabItem])
          val pairToAdd = definition -> parentsCopy
          if existing.contains(pairToAdd) then
            // no need to put a duplicate
            ()
          else
            val included: Seq[SymTabItem] = existing :+ pairToAdd
            symTab.update(name, included)
            parentage.update(definition, parentsCopy)
          end if
        } else {
          messages.addError(definition.loc, "Non implicit value with empty name should not happen")
        }
    }
  }

  override def result: SymbolsOutput = {
    SymbolsOutput(Messages.empty, symTab, parentage)
  }

  override def close(): Unit = ()

}
