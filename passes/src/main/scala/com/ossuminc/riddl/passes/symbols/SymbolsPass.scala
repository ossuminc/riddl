/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.symbols

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Messages, PredefinedModule, RuleId}
import com.ossuminc.riddl.passes.symbols.Symbols.*
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.symbols.Symbols.{Parentage, SymTab, SymTabItem}
import com.ossuminc.riddl.utils.PlatformContext

import scala.annotation.unused
import scala.collection.mutable

object SymbolsPass extends PassInfo[PassOptions] {
  val name: String = "Symbols"
  def creator(options: PassOptions = PassOptions.empty)(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => SymbolsPass(in, out)
  }
}

/** Symbol Table for Validation and other purposes. This symbol table is built from the AST model
  * after syntactic parsing is complete. It will also work for any sub-tree of the model that is
  * rooted by a ParentDefOf[Definition] node.
  *
  * The symbol tree contains a mapping from leaf name to the entire list of parent definitions
  * (symbols) as well as a mapping from definitions to their parents (parentage). Bot maps are built
  * during a single pass of the AST.
  *
  * @param input
  *   The output of the parser pass is the input to SymbolPass
  */
case class SymbolsPass(input: PassInput, outputs: PassesOutput)(using pc: PlatformContext)
    extends Pass(input, outputs) {

  override def name: String = SymbolsPass.name

  private val symTab: SymTab = mutable.HashMap.empty[String, Seq[SymTabItem]]

  private val parentage: Parentage = mutable.HashMap.empty[Definition, Parents]

  /** The predefined `Riddl` standard module's symbols and parentage, kept SEPARATE from the user's
    * so that anything enumerating `symTab`/`parentage` (`AnalysisResult.domains`,
    * `UseCaseWitnessPass`, the overloaded-symbol scan, …) sees the user's model and nothing else.
    * [[SymbolsOutput]] consults these only as a fallback, which is what makes a user definition of
    * the same name WIN.
    */
  private val predefinedSymTab: SymTab = mutable.HashMap.empty[String, Seq[SymTabItem]]
  private val predefinedParentage: Parentage = mutable.HashMap.empty[Definition, Parents]

  /** Make the predefined `Riddl` standard module ([[PredefinedModule]]) available to EVERY model
    * with no `import`. The module is deliberately NOT injected into the user's `Root.contents` — a
    * model that never mentions its definitions must produce byte-identical prettify/BAST/JSON
    * output and exactly the messages it produced before the module existed. So the ONLY seam is the
    * symbol table.
    */
  override def postProcess(root: PassRoot @unused): Unit = {
    PredefinedModule.symbolEntries.foreach { case (definition, parents) =>
      predefinedParentage.update(definition, parents)
      val name = definition.id.value
      if name.nonEmpty then predefinedSymTab.update(name, Seq(definition -> parents))
      end if
    }
  }

  private def rootLessParents(parents: Parents): Parents = {
    parents.filter {
      case _: Root                        => false // Roots don't have names and don't matter
      case x: Definition if x.isAnonymous => false // Parents with no names don't count
      case _                              => true // Everything else is fair game
    }
  }

  def process(definition: RiddlValue, parents: ParentStack): Unit = {
    definition match {
      case _: Root                          => // Root doesn't have a name
      case _: BASTImport                    => // BAST imports don't go in symbol table
      case _: MatchCase                     => // MatchCase is handled within MatchStatement
      case _: MatchPattern                  => // A29: patterns are handled within MatchStatement
      case _: NonDefinitionValues           => // none of these can have names
      case nv: Definition if nv.isAnonymous => // Nameless things, like includes, aren't stored
      case nv: Definition if nv.id.isEmpty  => // Empty names are not stored
      case namedValue: Definition => // NOTE: Anything with a name goes in symbol table
        val name = namedValue.id.value
        if name.nonEmpty then {
          val parentsCopy: Parents = rootLessParents(parents.toParents)
          val existing = symTab.getOrElse(name, Seq.empty[SymTabItem])
          val pairToAdd = namedValue -> parentsCopy
          if existing.contains(pairToAdd) then
            // no need to put a duplicate
            ()
          else
            val included: Seq[SymTabItem] = existing :+ pairToAdd
            symTab.update(name, included)
            parentage.update(namedValue, parentsCopy)
          end if
        } else {
          messages.addError(
            namedValue.loc,
            "Non implicit value with empty name should not happen",
            suggestion =
              "This is an internal RIDDL symbol-table error; please report it with the model that triggered it.",
            ruleId = Some(RuleId.EmptyNonImplicitName)
          )
        }
      // case rv: RiddlValue => // everything should be handled above
      //    assert(false, s"SymTab didn't process: $rv") // NOTE: nothing else has a name
    }
  }

  override def result(root: PassRoot): SymbolsOutput = {
    if pc.options.debug then println(symTab.toPrettyString)
    end if
    SymbolsOutput(root, Messages.empty, symTab, parentage, predefinedSymTab, predefinedParentage)
  }

  override def close(): Unit = ()

}
