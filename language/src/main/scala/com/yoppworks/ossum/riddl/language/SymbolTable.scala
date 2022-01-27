package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*

import scala.annotation.tailrec
import scala.collection.mutable
import scala.reflect.*

/** Symbol Table for Validation This symbol table is built from the AST model after syntactic
  * parsing is complete. It will also work for any sub-tree of the model that is rooted by a
  * Container node.
  */
case class SymbolTable(container: Container[Definition]) {
  type Parentage = Map[Definition, Container[Definition]]

  private val emptyParentage: Parentage = Map.empty[Definition, Container[Definition]]

  val parentage: Parentage = {
    Folding.foldEachDefinition[Parentage](container, container, emptyParentage) {
      (parent, child, next) =>
        next + (child -> parent)
    }
  }

  type Symbols = mutable.HashMap[String, mutable.Set[(Definition, Container[Definition])]]

  final val symbols = mutable.HashMap
    .empty[String, mutable.Set[(Definition, Container[Definition])]]

  Folding.foldEachDefinition[Unit](container, container, ()) { (parent, child, _) =>
    addToSymTab(child.id.value, child -> parent)
    child match {
      case t: Type => t.typ match {
        case e: Enumeration => e.enumerators.foreach { etor =>
          addToSymTab(etor.id.value, etor -> parent)
          // type reference and identifier relations must be handled by semantic validation
        }
        case mt: MessageType => mt.fields.foreach { fld =>
          addToSymTab(fld.id.value, fld -> parent)
        }
        case agg: Aggregation => agg.fields.foreach { fld =>
          addToSymTab(fld.id.value, fld -> parent)
        }
        case _ => addToSymTab(t.id.value, t -> parent) // types are definitions too
      }
      case _ =>
    }
  }

  def parentOf(definition: Definition): Option[Container[Definition]] = {
    parentage.get(definition)
  }

  def parentsOf(definition: Definition): List[Container[Definition]] = {
    @tailrec
    def recurse(
      init: List[Container[Definition]],
      examine: Definition
    ): List[Container[Definition]] = {
      parentage.get(examine) match {
        case None                                  => init
        case Some(parent) if init.contains(parent) => init
        case Some(parent) =>
          val newList = init :+ parent
          recurse(newList, parent)
      }
    }

    recurse(List.empty[Container[Definition]], definition)
  }

  def pathOf(definition: Definition): Seq[String] = {
    definition.id.value +: parentsOf(definition).map(_.id.value)
  }

  private def addToSymTab(name: String, pair: (Definition, Container[Definition])): Unit = {
    val extracted = symbols.getOrElse(name, mutable.Set.empty[(Definition, Container[Definition])])
    val included = extracted += pair
    symbols.update(name, included)
  }

  type LookupResult[D <: Definition] = List[(Definition, Option[D])]

  def lookupSymbol[D <: Definition : ClassTag](
                                                id: Seq[String]
                                              ): LookupResult[D] = {
    val clazz = classTag[D].runtimeClass
    val leafName = id.head
    val containerNames = id.tail
    symbols.get(leafName) match {
      case Some(set) => set.filter {
        case (_: Definition, container: Container[Definition]) =>
          val parentNames = (container +: parentsOf(container)).map(_.id.value)
          containerNames.zip(parentNames).forall { case (containerName, parentName) =>
            containerName == parentName
          }
      }.map { case (d: Definition, _: Container[Definition]) =>
        if (clazz.isInstance(d)) {
          (d, Option(d.asInstanceOf[D]))
        }
        else {
          (d, None)
        }
        }.toList
      case None => List.empty
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
    val containerNames = id.tail
    symbols.get(leafName) match {
      case Some(set) =>
        val result = set.filter { case (d: Definition, container: Container[Definition]) =>
          if (clazz.isInstance(d)) {
            // It is in the result set as long as the container names
            // given in the provided id are the same as the container
            // names in the symbol table.
            val parentNames = (container +: parentsOf(container)).map(_.id.value)
            containerNames.zip(parentNames).forall { case (containerName, parentName) =>
              containerName == parentName
            }
          } else {false}
        }.map(_._1.asInstanceOf[D])
        result.toList
      case None => List.empty[D]
    }
  }

  def foreachOverloadedSymbol[T](entries: Seq[Seq[Definition]] => T): T = {
    val overloads = symbols.filter(_._2.size > 1)
    val defs = overloads.toSeq.map(_._2).map(_.map(_._1).toSeq)
    entries(defs)
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
