package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*

import scala.collection.mutable
import scala.reflect.*

/** Symbol Table for Validation This symbol table is built from the AST model after syntactic
  * parsing is complete. It will also work for any sub-tree of the model that is rooted by a
  * Container node.
  */
case class SymbolTable(container: ParentDefOf[Definition]) {

  type Parents = Seq[ParentDefOf[Definition]]
  type Parentage = mutable.HashMap[Definition, Parents]
  type SymTabItem = (Definition, Parents)
  type SymTab = mutable.HashMap[String, Seq[SymTabItem]]

  private def emptySymTab: SymTab =
    mutable.HashMap.empty[String, Seq[SymTabItem]]
  private def emptyParentage: Parentage =
    mutable.HashMap.empty[Definition, Parents]

  private def makeSymTab(
    top: ParentDefOf[Definition]
  ): (SymTab,Parentage) = {
    val symTab: SymTab = emptySymTab
    val parentage: Parentage = emptyParentage
    Folding.foldLeftWithStack[Unit](())(top) {
      case (_, _: RootContainer, _) =>
        // RootContainers don't go in the symbol table
      case (_, _: Include, _) =>
        // includes don't go in the symbol table
      case (_, definition, stack) =>
        val name = definition.id.value
        val copy: Parents = stack.toSeq.filter {
          case _: RootContainer => false
          case _ => true
        }
        val existing = symTab.getOrElse(name, Seq.empty[SymTabItem])
        val included: Seq[SymTabItem] = existing :+ (definition -> copy)
        symTab.update(name, included)
        parentage.update(definition, copy)
    }
    symTab -> parentage
  }

  final val (symbols2: SymTab, parentage: Parentage) = makeSymTab(container)

/*
  type Symbols = mutable.HashMap[String, mutable.Set[(Definition, Container[Definition])]]

  final val symbols = mutable.HashMap
    .empty[String, mutable.Set[(Definition, Container[Definition])]]

  Folding.foldEachDefinition[Unit](container, container, ()) {
    (parent, child, _) =>
      child match {
        case _: Include => // includes don't go in the symbol table
        case _ =>
          val name = child.id.value
          val extracted = symbols.getOrElse(name, mutable.Set.empty[(Definition, Container[Definition])])
          val included = extracted += (child -> parent)
          symbols.update(name, included)
      }

  }
*/

  def parentOf(definition: Definition): Option[ParentDefOf[Definition]] =
    parentage.get(definition) match {
      case Some(container) => container.headOption
      case None => None
    }

  def parentsOf(definition: Definition): Seq[ParentDefOf[Definition]] = {
    parentage.get(definition) match {
      case Some(list) => list
      case None => Seq.empty[ParentDefOf[Definition]]
    }
  }

  def pathOf(definition: Definition): Seq[String] = {
    definition.id.value +: parentsOf(definition).map(_.id.value)
  }

  type LookupResult[D <: Definition] = List[(Definition, Option[D])]

  private def hasSameParentNames(id: Seq[String], parents: Parents): Boolean = {
    val containerNames = id.tail
    val parentNames = parents.map(_.id.value)
    containerNames.zip(parentNames).forall {
      case (containerName, parentName) => containerName == parentName
    }

  }
  def lookupSymbol[D <: Definition: ClassTag](
    id: Seq[String]
  ): LookupResult[D] = {
    require(id.nonEmpty , "No name elements provided to lookupSymbol")
    val clazz = classTag[D].runtimeClass
    val leafName = id.head
    symbols2.get(leafName) match {
      case Some(set) => set.filter {
        case (_: Definition, parents: Seq[ParentDefOf[Definition]]) =>
          // whittle down the list of matches to the ones whose parents names
          // have the same as the id provided
          hasSameParentNames(id, parents)
        }.map {
          case (d: Definition, _: Seq[ParentDefOf[Definition]]) =>
            // If a name match is also the same type as desired by the caller
            // then give them the definition in the requested type, optionally
            if (clazz.isInstance(d)) {
              (d, Option(d.asInstanceOf[D]))
            } else {
              (d, None)
            }
        }.toList
      case None =>
        // Symbol wasn't found
        List.empty
    }
  }

  def lookup[D <: Definition: ClassTag](
    ref: Reference[D]
  ): List[D] = { lookup[D](ref.id.value) }

  def lookup[D <: Definition: ClassTag](
    id: Seq[String]
  ): List[D] = {
    val clazz = classTag[D].runtimeClass
    val leafName = id.head
    symbols2.get(leafName) match {
      case Some(set) =>
        val result = set.filter {
          case (d: Definition, parents: Parents) =>
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
    val overloads = symbols2.filter(_._2.size > 1).filter(_._1 != "sender")
    val defs = overloads.toSeq.map(_._2).map(_.map(_._1).toSeq)
    process(defs)
  }
}
