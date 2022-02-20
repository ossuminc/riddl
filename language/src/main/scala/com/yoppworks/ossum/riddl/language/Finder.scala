package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Definition
import com.yoppworks.ossum.riddl.language.AST.ParentDefOf

case class Finder(root: ParentDefOf[Definition]) {

  def find(select: Definition => Boolean): Seq[Definition] = {
    Folding.foldEachDefinition(root, root, Seq.empty[Definition]) {
      case (_, definition, state) =>
        if (select(definition)) state :+ definition else state
    }
  }
}
