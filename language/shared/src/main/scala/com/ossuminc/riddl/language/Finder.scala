/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*

import scalajs.js.annotation._

/** The context for finding things within a given [Ccontainer]] or [[AST.RiddlValue]] as found in the AST model. This
  * provides the ability to find values in the model by traversing it and looking for the matching condition.
  * @param root
  *   The container of RiddlValues to traverse for the sought condition
  */
@JSExportTopLevel("Finder")
case class Finder(root: Container[RiddlValue]) {

  /** Search the `root` for a [[AST.RiddlValue]] that matches the boolean expression
    * @param select
    *   The boolean expression to search for
    * @return
    *   A [[scala.Seq]] of the matching [[AST.RiddlValue]]
    */
  @JSExport
  def find(select: RiddlValue => Boolean): Seq[RiddlValue] = {
    Folding.foldEachDefinition(root, root, Seq.empty) { case (_, value, state) =>
      if select(value) then state :+ value else state
    }
  }

  /** THe return value for the [[Finder.findWithParents()]] function */
  type DefWithParents = Seq[(RiddlValue, Seq[RiddlValue])]

  /** Find a matching set of [[AST.RiddlValue]] but return them with their parents
    *
    * @param select
    *   The boolean expression derived from a candidate [[AST.RiddlValue]] that selects it to the result set
    * @return
    *   A [[Finder#DefWithParents]] that returns a [[scala.Seq]] of two-tuples with
    *   the [[AST.RiddlValue]] a a [[scala.Seq]] of the parents of that value.
    */
  @JSExport def findWithParents(
    select: RiddlValue => Boolean
  ): DefWithParents = {
    Folding.foldLeftWithStack(Seq.empty[(RiddlValue, Seq[RiddlValue])],root) { case (state, definition, parents) =>
      if select(definition) then state :+ (definition -> parents) else state
    }
  }

  /** Find definitions that are empty
    *
    * @return
    *   A [[scala.Seq]] of [[AST.RiddlValue]], along with their parents that are empty
    */
  @JSExport def findEmpty: DefWithParents = findWithParents(_.isEmpty)
}
