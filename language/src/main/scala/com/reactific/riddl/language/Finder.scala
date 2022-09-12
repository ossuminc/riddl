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

import com.reactific.riddl.language.AST.*

case class Finder(root: Definition) {

  def find(select: Definition => Boolean): Seq[Definition] = {
    Folding.foldEachDefinition(root, root, Seq.empty[Definition]) {
      case (_, definition, state) =>
        if (select(definition)) state :+ definition else state
    }
  }

  type DefWithParents = Seq[(Definition, Seq[Definition])]

  def findWithParents(
    select: Definition => Boolean
  ): DefWithParents = {
    Folding.foldLeftWithStack(Seq.empty[(Definition,
      Seq[Definition])])(root) {
      case (state, definition, parents) =>
        if (select(definition)) state :+ (definition -> parents)  else state
    }
  }

  def findEmpty: DefWithParents = findWithParents(_.isEmpty)

  case class Statistics(
    var definitions: Int = 0,
    var incomplete: Int = 0,
    var maximum_depth : Int = 0,
    var missing_documentation: Int = 0,
    var total_maturity: Int = 0,
    var percent_complete: Float = 0.0F,
    var percent_documented: Float = 0.0F,
    var average_maturity: Float = 0.0F
  )

  def generateStatistics(): Statistics = {
    val stats = Folding.foldLeftWithStack(Statistics())(root) {
      case (state, definition, parents) =>
        if (parents.size >= state.maximum_depth ) {
          state.maximum_depth = parents.size + 1
        }
        if (definition.brief.isEmpty || definition.description.isEmpty) {
          state.missing_documentation += 1
        }
        if (definition.isEmpty) {
          state.incomplete += 1
        }
        state.definitions += 1
        definition match {
          case vd: VitalDefinition[?] =>
            state.total_maturity += vd.maturity(parents)
          case d: Definition if d.nonEmpty =>
            state.total_maturity += d.contents.length
          case _ => // ignore
        }
        state
    }
    val completed = stats.definitions - stats.incomplete
    stats.percent_complete =
      (completed.toFloat / stats.definitions.toFloat) * 100.0F
    val documented = stats.definitions - stats.missing_documentation
    stats.percent_documented =
      (documented.toFloat / stats.definitions.toFloat)* 100.0F
    stats.average_maturity =
      (stats.total_maturity.toFloat / stats.definitions.toFloat)
    stats
  }
}
