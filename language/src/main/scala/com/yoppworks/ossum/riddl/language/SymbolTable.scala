package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import com.yoppworks.ossum.riddl.language.Folding.foldLeft

import scala.annotation.tailrec
import scala.collection.mutable
import scala.reflect._

/** Unit Tests For SymbolTable */
case class SymbolTable(container: ContainingDefinition) {
  type Parentage = Map[Definition, ContainingDefinition]

  val parentage: Parentage = {
    val predefined = Map[Definition, ContainingDefinition](
      Strng -> container,
      Number -> container,
      Id -> container,
      Bool -> container,
      Time -> container,
      Date -> container,
      TimeStamp -> container,
      URL -> container
    )
    foldLeft[Parentage](container, container, predefined) {
      (parent, child, next) =>
        next + (child -> parent)
    }
  }

  def parentOf(definition: Definition): Option[ContainingDefinition] = {
    parentage.get(definition)
  }

  def parentsOf(definition: Definition): List[ContainingDefinition] = {
    @tailrec
    def recurse(
      init: List[ContainingDefinition],
      examine: Definition
    ): List[ContainingDefinition] = {
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
    recurse(List.empty[ContainingDefinition], definition)
  }

  type Symbols =
    mutable.HashMap[String, mutable.Set[(Definition, ContainingDefinition)]]

  val symbols: Symbols = {
    val predefined = mutable.HashMap
      .newBuilder[String, mutable.Set[(Definition, ContainingDefinition)]]
    predefined.addAll(
      Seq(
        Strng.id.value -> mutable.Set(Strng -> container),
        Number.id.value -> mutable.Set(Number -> container),
        Id.id.value -> mutable.Set(Id -> container),
        Bool.id.value -> mutable.Set(Bool -> container),
        Time.id.value -> mutable.Set(Time -> container),
        Date.id.value -> mutable.Set(Date -> container),
        TimeStamp.id.value -> mutable.Set(TimeStamp -> container),
        URL.id.value -> mutable.Set(URL -> container),
        container.id.value -> mutable.Set(container -> container)
      )
    )
    foldLeft[Symbols](container, container, predefined.result()) {
      (parent, child, next) =>
        val extracted = next.getOrElse(
          child.id.value,
          mutable.Set.empty[(Definition, ContainingDefinition)]
        )
        val included = extracted.addOne(child -> parent)
        next.update(child.id.value, included)
        next
    }
  }

  def lookup[D <: Definition: ClassTag](
    ref: Reference,
    within: ContainingDefinition
  ): List[D] = {
    lookup[D](ref.id, within)
  }

  def lookup[D <: Definition: ClassTag](
    id: Identifier,
    within: ContainingDefinition
  ): List[D] = {
    val parents = within +: parentsOf(within)
    val clazz = classTag[D].runtimeClass
    symbols.get(id.value) match {
      case Some(set) =>
        val result = set
          .filter {
            case (d: Definition, container: ContainingDefinition) =>
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
