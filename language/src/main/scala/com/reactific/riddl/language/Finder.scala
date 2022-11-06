/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*

case class KindStats(
  var count: Int = 0,
  var maturitySum: Int = 0,
  var completed: Int = 0,
  var documented: Int = 0)

case class Statistics(
  var maximum_depth: Int = 0,
  var terms_count: Int = 0,
  var all_stats: KindStats = KindStats(),
  var other_stats: KindStats = KindStats(),
  var adaptorStats: KindStats = KindStats(),
  var contextStats: KindStats = KindStats(),
  var domainStats: KindStats = KindStats(),
  var entityStats: KindStats = KindStats(),
  var functionStats: KindStats = KindStats(),
  var handlerStats: KindStats = KindStats(),
  var plantStats: KindStats = KindStats(),
  var processorStats: KindStats = KindStats(),
  var projectionStats: KindStats = KindStats(),
  var repositoryStats: KindStats = KindStats(),
  var sagaStats: KindStats = KindStats(),
  var storyStats: KindStats = KindStats())

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

  def makeVitalStats[T <: VitalDefinition[?, ?]](
    v: T,
    stats: => KindStats
  ): Unit = {
    stats.count += 1
    stats.maturitySum += v.maturity
    if (v.nonEmpty) stats.completed += 1
    if (v.brief.nonEmpty && v.description.nonEmpty) stats.documented += 1
  }

  def generateStatistics(): Statistics = {
    val stats = Folding.foldLeftWithStack(Statistics())(root) {
      case (state, definition, parents) =>
        if (parents.size >= state.maximum_depth) {
          state.maximum_depth = parents.size + 1
        }
        if (!definition.isEmpty) state.all_stats.completed += 1
        if (definition.brief.nonEmpty && definition.description.nonEmpty) {
          state.all_stats.documented += 1
        }
        state.all_stats.count += 1
        definition match {
          case vd: VitalDefinition[?, ?] =>
            state.all_stats.maturitySum += vd.maturity
            vd match {
              case a: Adaptor => makeVitalStats(a, state.adaptorStats)
              case a: Application =>
                makeVitalStats(a, state.contextStats)
              case c: Context => makeVitalStats(c, state.contextStats)
              case d: Domain  => makeVitalStats(d, state.domainStats)
              case e: Entity  => makeVitalStats(e, state.entityStats)
              case f: Function =>
                makeVitalStats(f, state.functionStats)
              case h: Handler => makeVitalStats(h, state.handlerStats)
              case p: Plant   => makeVitalStats(p, state.plantStats)
              case p: Processor =>
                makeVitalStats(p, state.processorStats)
              case p: Projection =>
                makeVitalStats(p, state.projectionStats)
              case r: Repository =>
                makeVitalStats(r, state.repositoryStats)
              case s: Saga  => makeVitalStats(s, state.sagaStats)
              case s: Story => makeVitalStats(s, state.storyStats)
            }
          case t: Term =>
            state.terms_count += 1
            state.other_stats.count += 1
            if (t.nonEmpty) state.other_stats.completed += 1
          case d: Definition =>
            state.other_stats.count += 1
            if (d.nonEmpty) { state.other_stats.completed += 1 }
          case _ => // ignore
        }
        state
    }
    stats
  }
}
