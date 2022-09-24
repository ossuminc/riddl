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
    Folding.foldLeftWithStack(Seq.empty[(Definition, Seq[Definition])])(root) {
      case (state, definition, parents) =>
        if (select(definition)) state :+ (definition -> parents) else state
    }
  }

  def findEmpty: DefWithParents = findWithParents(_.isEmpty)

  case class KindStats(var count: Int = 0, var maturitySum: Int = 0)
  case class Statistics(
    var definitions: Int = 0,
    var incomplete: Int = 0,
    var maximum_depth: Int = 0,
    var missing_documentation: Int = 0,
    var total_maturity: Int = 0,
    var terms_count: Int = 0,
    var adaptorStats: KindStats = KindStats(),
    var contextStats: KindStats = KindStats(),
    var domainStats: KindStats = KindStats(),
    var entityStats: KindStats = KindStats(),
    var functionStats: KindStats = KindStats(),
    var handlerStats: KindStats = KindStats(),
    var plantStats: KindStats = KindStats(),
    var processorStats: KindStats = KindStats(),
    var projectionStats: KindStats = KindStats(),
    var sagaStats: KindStats = KindStats(),
    var storyStats: KindStats = KindStats())

  def generateStatistics(): Statistics = {
    val stats = Folding.foldLeftWithStack(Statistics())(root) {
      case (state, definition, parents) =>
        if (parents.size >= state.maximum_depth) {
          state.maximum_depth = parents.size + 1
        }
        if (definition.brief.isEmpty || definition.description.isEmpty) {
          state.missing_documentation += 1
        }
        if (definition.isEmpty) { state.incomplete += 1 }
        state.definitions += 1
        definition match {
          case vd: VitalDefinition[?, ?] =>
            state.total_maturity += vd.maturity(parents)
            vd match {
              case a: Adaptor =>
                state.adaptorStats.count += 1
                state.adaptorStats.maturitySum += a.maturity(parents)
              case c: Context =>
                state.contextStats.count += 1
                state.contextStats.maturitySum += c.maturity(parents)
              case d: Domain =>
                state.domainStats.count += 1
                state.domainStats.maturitySum += d.maturity(parents)
              case e: Entity =>
                state.entityStats.count += 1
                state.entityStats.maturitySum += e.maturity(parents)
              case f: Function =>
                state.functionStats.count += 1
                state.functionStats.maturitySum += f.maturity(parents)
              case h: Handler =>
                state.handlerStats.count += 1
                state.handlerStats.maturitySum += h.maturity(parents)
              case p: Plant =>
                state.plantStats.count += 1
                state.plantStats.maturitySum += p.maturity(parents)
              case p: Processor =>
                state.processorStats.count += 1
                state.processorStats.maturitySum += p.maturity(parents)
              case p: Projection =>
                state.projectionStats.count += 1
                state.projectionStats.maturitySum += p.maturity(parents)
              case s: Saga =>
                state.sagaStats.count += 1
                state.sagaStats.maturitySum += s.maturity(parents)
              case s: Story =>
                state.storyStats.count += 1
                state.storyStats.maturitySum += s.maturity(parents)
            }
          case d: Definition =>
            if (d.nonEmpty) { state.total_maturity += d.contents.length }
            d match {
              case _: Term => state.terms_count += 1
              case _       => // ignore
            }
          case _ => // ignore
        }
        state
    }
    stats
  }
}
