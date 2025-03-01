/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import scala.collection.immutable.HashMap
import scala.collection.mutable

import scala.reflect.{ClassTag, classTag}
import scalajs.js.annotation.*

/** The referent for finding things within a given [[com.ossuminc.riddl.language.AST.Container]] of
  * [[com.ossuminc.riddl.language.AST.RiddlValue]] as found in the AST model. This provides the
  * ability to find values in the model by traversing it and looking for the matching condition.
  * @param root
  *   The container of RiddlValues to traverse for the sought condition
  */
@JSExportTopLevel("Finder")
case class Finder[CV <: RiddlValue](root: Container[CV]) {

  import scala.reflect.ClassTag

  /** Search the `root` for a [[AST.RiddlValue]] that matches the boolean expression
    *
    * @param select
    *   The boolean expression to search for
    *
    * @return
    *   A [[scala.Seq]] of the matching [[AST.RiddlValue]]
    */
  @JSExport
  def find(select: CV => Boolean): Seq[CV] =
    Folding.foldEachDefinition[Seq[CV], CV](root, Seq.empty[CV]) {
      case (state: Seq[CV], value: CV) =>
        if select(value) then state :+ value else state
    }
  end find

  /** Search the [[root]] for a certain kind of [[AST.RiddlValue]] and return those */
  @JSExport
  def findByType[T <: AST.RiddlValue: ClassTag]: Seq[T] =
    import scala.reflect.classTag
    val lookingFor = classTag[T].runtimeClass
    val result = find { (value: RiddlValue) => lookingFor.isAssignableFrom(value.getClass) }
    result.asInstanceOf[Seq[T]]
  end findByType

  def recursiveFindByType[T <: AST.RiddlValue: ClassTag]: Seq[T] =
    import scala.reflect.classTag
    val lookingFor = classTag[T].runtimeClass
    def consider(list: Seq[T], child: RiddlValue): Seq[T] =
      val nested = {
        child match
          case c: Container[?] =>
            c.contents.foldLeft(list) { case (next, child) => consider(next, child) }
          case IfThenElseStatement(_, _, thens, elses) =>
            val r1 = thens.foldLeft(list) { case (next, child) => consider(next, child) }
            elses.foldLeft(r1) { case (next, child) => consider(next, child) }
          case ForEachStatement(_, _, do_) =>
            do_.foldLeft(list) { case (next, child) => consider(next, child) }
          case SagaStep(_, _, dos, undos, _) =>
            val r2 = dos.foldLeft(list) { case (next, child) => consider(next, child) }
            undos.foldLeft(r2) { case (next, child) => consider(next, child) }
          case _ => list
        end match
      }
      if lookingFor.isAssignableFrom(child.getClass) then nested :+ child.asInstanceOf[T]
      else nested
    end consider
    root.contents.foldLeft(Seq.empty) { case (list, child) => consider(list, child) }
  end recursiveFindByType

  /** The return value for the [[Finder.findWithParents()]] function */
  type DefWithParents[T <: RiddlValue] = Seq[(T, Parents)]

  /** Find a matching set of [[AST.RiddlValue]] but return them with their parents
    *
    * @param select
    *   The boolean expression derived from a candidate [[AST.RiddlValue]] that selects it to the
    *   result set
    * @return
    *   A [[Finder#DefWithParents]] that returns a [[scala.Seq]] of two-tuples with the
    *   [[AST.RiddlValue]] a a [[scala.Seq]] of the parents of that value.
    */
  @JSExport
  def findWithParents[T <: RiddlValue: ClassTag](
    select: T => Boolean
  ): DefWithParents[T] =
    import scala.collection.mutable
    val lookingFor = classTag[T].runtimeClass
    Folding.foldLeftWithStack[Seq[(T, Parents)], CV](
      Seq.empty[(T, Parents)],
      root,
      ParentStack.empty
    ) { case (state, definition: CV, parents) =>
      if lookingFor.isAssignableFrom(definition.getClass) then
        val value: T = definition.asInstanceOf[T]
        if select(value) then state :+ (value -> parents)
        else state
      else state
    }
  end findWithParents

  /** Find the Parents for a given node in the root
    * @param node
    *   The node for which the Parents are sought.
    * @return
    *   Parents - A Sequence of Branch nodes from nearest parent towards the Root.
    */
  @JSExport
  def findParents(node: Definition): Parents = {
    val result = findWithParents[Definition](_ == node)
    result.headOption.map(_._2).getOrElse(Parents.empty)
  }

  /** Start from the root Container and for every definition it contains, compute the Parents (path
    * to that definition).
    * @return
    *   A HashMap[Definition,Parents] that provides the path to every definition in a fast-access
    *   data structure
    */
  @JSExport
  def findAllPaths: HashMap[Definition, Parents] = {
    val stack = ParentStack.empty[Branch[?]]
    val result: mutable.HashMap[Definition, Parents] = mutable.HashMap.empty
    Folding.foldLeftWithStack(result, root, stack) { case (map, definition: Definition, parents) =>
      map.addOne((definition, parents))
      map
    }
    result.toMap[Definition, Parents].asInstanceOf[HashMap[Definition, Parents]]
  }

  /** Run a transformation function on the [[Finder]] contents. They type parameter specifies what
    * kind of thing should be found, the `select` argument provides further refinement of which
    * things of that type should be selected. The transformation function, `transformF` does the
    * transformation, probably by using the Scala `.copy` method.
    *
    * @tparam TT
    *   The transform type. This narrows the search to just the contents that have the base type TT.
    * @param select
    *   The function to select which values should be operated on. It should return true if the
    *   transformation function should be executed on the element passed to it.
    * @param transformF
    *   The transformation function to convert one value to another. The returned value will replace
    *   the passed value in the [[Finder]]'s container.
    */
  @JSExport
  def transform[TT <: RiddlValue: ClassTag](select: TT => Boolean)(transformF: CV => CV): Unit =
    val clazz = classTag[TT].runtimeClass
    for { i <- root.contents.indices } do {
      val item: CV = root.contents(i)
      if clazz.isAssignableFrom(item.getClass) then
        if select(item.asInstanceOf[TT]) then root.contents(i) = transformF(item)
        end if
      end if
    }
  end transform

  /** Find definitions that are empty
    *
    * @return
    *   A [[scala.Seq]] of [[AST.RiddlValue]], along with their parents that are empty
    */
  @JSExport def findEmpty: DefWithParents[Definition] = findWithParents[Definition](_.isEmpty)
}

object Finder:
  def apply[CV <: RiddlValue](contents: Contents[CV]): Finder[CV] =
    val container = SimpleContainer[CV](contents)
    Finder[CV](container)
  end apply
end Finder
