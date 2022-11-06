/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*

import scala.collection.mutable
import scala.reflect.*

/** Symbol Table for Validation and other purposes. This symbol table is built
  * from the AST model after syntactic parsing is complete. It will also work
  * for any sub-tree of the model that is rooted by a ParentDefOf[Definition]
  * node.
  *
  * The symbol tree contains a mapping from leaf name to the entire list of
  * parent definitions (symbols) as well as a mapping from definitions to their
  * parents (parentage). Bot maps are built during a single pass of the AST.
  *
  * @param container
  *   The node from which to build the symbol table
  */
case class SymbolTable(container: Definition) {

  type Parents = Seq[Definition]
  type Parentage = mutable.HashMap[Definition, Parents]
  type SymTabItem = (Definition, Parents)
  type SymTab = mutable.HashMap[String, Seq[SymTabItem]]

  private def emptySymTab: SymTab = mutable.HashMap
    .empty[String, Seq[SymTabItem]]
  private def emptyParentage: Parentage = mutable.HashMap
    .empty[Definition, Parents]

  private def makeSymTab(top: Definition): (SymTab, Parentage) = {
    def rootLessParents(parents: Parents): Parents = {
      parents.toSeq.filter {
        case _: RootContainer => false
        case _                => true
      }
    }
    val symTab: SymTab = emptySymTab
    val parentage: Parentage = emptyParentage
    Folding.foldLeftWithStack[Unit](())(top) {
      case (_, _: RootContainer, _) =>
      // RootContainers don't go in the symbol table
      case (_, _: Include[?], _) =>
      // includes don't go in the symbol table
      case (_, definition, parents) =>
        val name = definition.id.value
        if (name.nonEmpty) {
          val copy: Parents = rootLessParents(parents)
          val existing = symTab.getOrElse(name, Seq.empty[SymTabItem])
          val included: Seq[SymTabItem] = existing :+ (definition -> copy)
          symTab.update(name, included)
          parentage.update(definition, copy)
        }
    }
    symTab -> parentage
  }

  final private val (symbols: SymTab, parentage: Parentage) =
    makeSymTab(container)

  /** Get the parent of a definition
    *
    * @param definition
    *   The definition whose parent is to be sought.
    * @return
    *   optionally, the parent definition of the given definition
    */
  def parentOf(definition: Definition): Option[Definition] =
    parentage.get(definition) match {
      case Some(container) => container.headOption
      case None            => None
    }

  /** Get all parents of a definition
    *
    * @param definition
    *   The defintiion whose parents are to be sought.
    * @return
    *   the sequence of ParentDefOf parents or empty if none.
    */
  def parentsOf(definition: Definition): Seq[Definition] = {
    parentage.get(definition) match {
      case Some(list) => list
      case None       => Seq.empty[Definition]
    }
  }

  /** Get the full path of a definition
    *
    * @param definition
    *   The definition for which the path name is sought.
    * @return
    *   A list of strings from leaf to root giving the names of the definition
    *   and its parents.
    */
  def pathOf(definition: Definition): Seq[String] = {
    definition.id.value +: parentsOf(definition).map(_.id.value)
  }

  private def hasSameParentNames(id: Seq[String], parents: Parents): Boolean = {
    val containerNames = id.tail
    val parentNames = parents.map(_.id.value)
    containerNames.zip(parentNames).forall { case (containerName, parentName) =>
      containerName == parentName
    }
  }

  /** The result of a lookupSymbol request A lookupSymbol request returns a list
    * of tuples that contain the generic definition, as a Definition, and, if
    * the definition matches the type of interest, D, then an Option[D] for
    * convenience.
    */
  type LookupResult[D <: Definition] = List[(Definition, Option[D])]

  /** Look up a symbol in the table
    *
    * @param id
    *   The multi-part identifier of the symbol, from leaf to root, that is from
    *   most nested to least nested.
    * @tparam D
    *   The expected type of definition
    * @return
    *   A list of matching definitions of 2-tuples giving the definition as a
    *   Definition type and optionally as the requested type
    */
  def lookupSymbol[D <: Definition: ClassTag](
    id: Seq[String]
  ): LookupResult[D] = {
    require(id.nonEmpty, "No name elements provided to lookupSymbol")
    val clazz = classTag[D].runtimeClass
    val nameList = id.reverse
    val leafName = nameList.head
    symbols.get(leafName) match {
      case Some(set) => set
          .filter { case (_: Definition, parents: Seq[Definition]) =>
            // whittle down the list of matches to the ones whose parents names
            // have the same as the nameList provided
            hasSameParentNames(nameList, parents)
          }.map { case (d: Definition, _: Seq[Definition]) =>
            // If a name match is also the same type as desired by the caller
            // then give them the definition in the requested type, optionally
            if (clazz.isInstance(d)) { (d, Option(d.asInstanceOf[D])) }
            else { (d, None) }
          }.toList
      case None =>
        // Symbol wasn't found
        List.empty
    }
  }

  /** Look up a symbol in the table
    *
    * @param id
    *   The multi-part identifier of the symbol, from leaf to root, that is from
    *   most nested to least nested.
    * @tparam D
    *   The expected type of definition
    * @return
    *   A list of matching definitions of 2-tuples giving the definition as a
    *   Definition type and optionally as the requested type
    */
  def lookupParentage(
    names: Seq[String]
  ): List[SymTabItem] = {
    // require(id.nonEmpty, "No name elements provided to lookupSymbol")
    // val nameList = id.reverse
    val leafName = names.head
    symbols.get(leafName) match {
      case Some(set) => set
          .filter { case (_: Definition, parents: Seq[Definition]) =>
            // whittle down the list of matches to the ones whose parents names
            // have the same as the nameList provided
            hasSameParentNames(names, parents)
          }.toList
      case None =>
        // Symbol wasn't found
        List.empty[SymTabItem]
    }
  }

  def lookup[D <: Definition: ClassTag](
    ref: Reference[D]
  ): List[D] = { lookup[D](ref.pathId.value) }

  def lookup[D <: Definition: ClassTag](
    id: Seq[String]
  ): List[D] = {
    val clazz = classTag[D].runtimeClass
    val leafName = id.head
    symbols.get(leafName) match {
      case Some(set) =>
        val result = set.filter { case (d: Definition, parents: Parents) =>
          if (clazz.isInstance(d)) {
            // It is in the result set as long as the container names
            // given in the provided id are the same as the container
            // names in the symbol table.
            hasSameParentNames(id, parents)
          } else { false }
        }.map(_._1.asInstanceOf[D])
        result.toList
      case None => List.empty[D]
    }
  }

  def foreachOverloadedSymbol[T](process: Seq[Seq[Definition]] => T): T = {
    val overloads = symbols.filterNot(_._1.isEmpty).filter(_._2.size > 1)
    val defs = overloads.toSeq.map(_._2).map(_.map(_._1).toSeq)
    process(defs)
  }
}
