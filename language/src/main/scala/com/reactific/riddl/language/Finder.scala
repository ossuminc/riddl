package com.reactific.riddl.language

import com.reactific.riddl.language.AST.{Definition, ParentDefOf}

case class Finder(root: ParentDefOf[Definition]) {

  def find(select: Definition => Boolean): Seq[Definition] = {
    Folding.foldEachDefinition(root, root, Seq.empty[Definition]) {
      case (_, definition, state) =>
        if (select(definition)) state :+ definition else state
    }
  }

  type DefWithParents = Seq[(Definition, Seq[ParentDefOf[Definition]])]

  def findWithParents(
    select: Definition => Boolean
  ): DefWithParents = {
    Folding.foldLeftWithStack(Seq.empty[(Definition,
      Seq[ParentDefOf[Definition]])])(root) {
      case (state, definition, parents) =>
        if (select(definition)) state :+ (definition -> parents)  else state
    }
  }

  def findEmpty: DefWithParents = findWithParents(_.isEmpty)
}
