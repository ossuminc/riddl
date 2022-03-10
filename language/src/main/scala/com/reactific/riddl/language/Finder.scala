/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
