package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.reflect._

/** Unit Tests For SymbolTable */
case class SymbolTable(container: Container) {
  type Parentage = Map[Definition, Container]

  val parentage: Parentage = {
    Folding.foldEachDefinition[Parentage](
      container,
      container,
      Map.empty[Definition, Container]
    ) { (parent, child, next) =>
      next + (child -> parent)
    }
  }

  def parentOf(definition: Definition): Option[Container] = {
    parentage.get(definition)
  }

  def parentsOf(definition: Definition): List[Container] = {
    @tailrec
    def recurse(
      init: List[Container],
      examine: Definition
    ): List[Container] = {
      parentage.get(examine) match {
        case None =>
          init
        case Some(parent) if init.contains(parent) =>
          init
        case Some(parent) =>
          val newList = init :+ parent
          recurse(newList, parent)
      }
    }
    recurse(List.empty[Container], definition)
  }

  type Symbols =
    mutable.HashMap[String, mutable.Set[(Definition, Container)]]

  val emptySymbols =
    mutable.HashMap.empty[String, mutable.Set[(Definition, Container)]]

  val symbols: Symbols = {
    Folding.foldEachDefinition[Symbols](container, container, emptySymbols) {
      (parent, child, next) =>
        val extracted = next.getOrElse(
          child.id.value,
          mutable.Set.empty[(Definition, Container)]
        )
        val included = extracted += (child -> parent)
        next.update(child.id.value, included)
        next
    }
  }

  def lookup[D <: Definition: ClassTag](
    ref: Reference,
    within: Container
  ): List[D] = {
    lookup[D](ref.id, within)
  }

  def lookup[D <: Definition: ClassTag](
    id: Identifier,
    within: Container
  ): List[D] = {
    val parents = within +: parentsOf(within)
    val clazz = classTag[D].runtimeClass
    symbols.get(id.value) match {
      case Some(set) =>
        val result = set
          .filter {
            case (d: Definition, container: Container) =>
              val compatible = clazz.isInstance(d)
              val in_scope = parents.contains(container)
              compatible && in_scope
          }
          .map(_._1.asInstanceOf[D])
        result.toList
      case None =>
        List.empty[D]
    }
  }
}
