package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.reflect._

/** Symbol Table for Validation This symbol table is built from the AST model
  * after syntactic parsing is complete. It will also work for any sub-tree of
  * the model that is rooted by a Container node.
  */
case class SymbolTable(container: Container) {
  type Parentage = Map[Definition, Container]

  val parentage: Parentage = {
    Folding.foldEachDefinition[Parentage](
      container,
      container,
      Map.empty[Definition, Container]
    ) { (parent, child, next) => next + (child -> parent) }
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
        case None                                  => init
        case Some(parent) if init.contains(parent) => init
        case Some(parent) =>
          val newList = init :+ parent
          recurse(newList, parent)
      }
    }

    recurse(List.empty[Container], definition)
  }

  def pathOf(definition: Definition): Seq[String] = {
    definition.id.value +: parentsOf(definition).map(_.id.value)
  }

  type Symbols = mutable.HashMap[String, mutable.Set[(Definition, Container)]]

  final val symbols = mutable.HashMap
    .empty[String, mutable.Set[(Definition, Container)]]

  private def addToSymTab(name: String, pair: (Definition, Container)): Unit = {
    val extracted = symbols
      .getOrElse(name, mutable.Set.empty[(Definition, Container)])
    val included = extracted += pair
    symbols.update(name, included)
  }

  Folding.foldEachDefinition[Unit](container, container, ()) {
    (parent, child, _) =>
      addToSymTab(child.id.value, child -> parent)
      child match {
        case e: Entity => e.states.foreach { s: State =>
            addToSymTab(s.id.value, s -> e)
          }
        case m: MessageDefinition => m.typ match {
            case a: Aggregation => a.fields.foreach { f: Field =>
                addToSymTab(f.id.value, f -> m)
              }
            case _ =>
          }
        case t: Type => t.typ match {
            case e: Enumeration => e.of.foreach { etor =>
                addToSymTab(etor.id.value, t -> parent)
              // type reference and identifier relations must be handled by semantic validation
              }
            case _ =>
          }
        case _ =>
      }
  }

  def lookup[D <: Definition: ClassTag](
    ref: Reference
  ): List[D] = { lookup[D](ref.id.value) }

  def lookup[D <: Definition: ClassTag](
    id: Seq[String]
  ): List[D] = {
    val clazz = classTag[D].runtimeClass
    val leafName = id.head
    val containerNames = id.tail
    symbols.get(leafName) match {
      case Some(set) =>
        val result = set.filter { case (d: Definition, container: Container) =>
          if (clazz.isInstance(d)) {
            // It is in the result set as long as the container names
            // given in the provided id are the same as the container
            // names in the symbol table.
            val parentNames = (container +: parentsOf(container))
              .map(_.id.value)
            containerNames.zip(parentNames).forall {
              case (containerName, parentName) => containerName == parentName
            }
          } else { false }
        }.map(_._1.asInstanceOf[D])
        result.toList
      case None => List.empty[D]
    }
  }

  /*

  def lookup[D <: Definition: ClassTag](
    id: Seq[String],
    context: Definition
  ): List[D] = {
    lookup[D](id) match {
      case Nil =>
        Nil
      case definition :: Nil =>
        List(definition)
      case head :: tail =>
        if (head == id.head) {
          tail.find { d =>
          }
          if (id.tail == (tail)) {}
        } else {
          Nil
        }
    }
  }

   */
}
