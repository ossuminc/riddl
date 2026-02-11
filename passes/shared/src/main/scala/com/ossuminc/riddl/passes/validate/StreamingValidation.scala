/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.PassRoot
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable

trait StreamingValidation(using pc: PlatformContext) extends TypeValidation {

  def addInlet(inlet: Inlet): Unit = inlets.addOne(inlet)
  def addOutlet(outlet: Outlet): Unit = outlets.addOne(outlet)
  def addStreamlet(streamlet: Streamlet): Unit = streamlets.addOne(streamlet)
  def addConnector(connector: Connector): Unit = connectors.addOne(connector)

  def checkStreaming(root: PassRoot): Unit = {
    checkStreamingUsage(root)
    checkConnectorPersistence()
    checkUnattachedOutlets()
  }

  protected val inlets: mutable.ListBuffer[Inlet] = mutable.ListBuffer.empty
  protected val outlets: mutable.ListBuffer[Outlet] = mutable.ListBuffer.empty
  protected val streamlets: mutable.ListBuffer[Streamlet] = mutable.ListBuffer.empty
  protected val connectors: mutable.ListBuffer[Connector] = mutable.ListBuffer.empty

  private def checkStreamingUsage(root: PassRoot): Unit = {
    if streamlets.nonEmpty then {
      // Build a map from each streamlet to its connected streamlets via connectors
      // First, resolve all connector endpoints to their parent streamlets
      val connectedStreamlets = mutable.Set.empty[Streamlet]
      // Adjacency list: streamlet → set of downstream streamlets (outlet→inlet direction)
      val adjacency = mutable.Map.empty[Streamlet, mutable.Set[Streamlet]]

      connectors.filterNot(_.isEmpty).foreach { connector =>
        val connParents = symbols.parentsOf(connector)
        val maybeOutlet = resolvePath[Outlet](connector.from.pathId, connParents)
        val maybeInlet = resolvePath[Inlet](connector.to.pathId, connParents)

        val maybeFromStreamlet = maybeOutlet.flatMap { outlet =>
          symbols.parentOf(outlet).collect { case s: Streamlet => s }
        }
        val maybeToStreamlet = maybeInlet.flatMap { inlet =>
          symbols.parentOf(inlet).collect { case s: Streamlet => s }
        }

        (maybeFromStreamlet, maybeToStreamlet) match {
          case (Some(fromSl), Some(toSl)) =>
            connectedStreamlets += fromSl
            connectedStreamlets += toSl
            adjacency.getOrElseUpdate(fromSl, mutable.Set.empty) += toSl
          case (Some(fromSl), None) =>
            connectedStreamlets += fromSl
          case (None, Some(toSl)) =>
            connectedStreamlets += toSl
          case _ => ()
        }
      }

      // Check 1: Isolated streamlets (non-Void, not connected to any connector)
      streamlets.foreach { streamlet =>
        streamlet.shape match {
          case _: Void => () // Void streamlets are excluded
          case _ =>
            if !connectedStreamlets.contains(streamlet) then
              messages.addWarning(
                streamlet.errorLoc,
                s"${streamlet.identify} has no connections to any connector"
              )
        }
      }

      // Check 2: Source→Sink reachability via BFS
      val sources = streamlets.filter(_.shape.isInstanceOf[Source])
      val sinks = streamlets.filter(_.shape.isInstanceOf[Sink]).toSet

      sources.foreach { source =>
        if adjacency.contains(source) then {
          // BFS from this source
          val visited = mutable.Set.empty[Streamlet]
          val queue = mutable.Queue.empty[Streamlet]
          queue.enqueue(source)
          visited += source
          var reachesSink = false

          while queue.nonEmpty && !reachesSink do
            val current = queue.dequeue()
            if sinks.contains(current) && current != source then
              reachesSink = true
            else
              adjacency.getOrElse(current, mutable.Set.empty).foreach { neighbor =>
                if !visited.contains(neighbor) then
                  visited += neighbor
                  queue.enqueue(neighbor)
              }
          end while

          if !reachesSink then
            messages.addWarning(
              source.errorLoc,
              s"${source.identify} is a source but has no downstream path to any sink"
            )
        }
      }

      // Check 3: Sink←Source reverse reachability via BFS
      val reverseAdjacency = mutable.Map.empty[Streamlet, mutable.Set[Streamlet]]
      adjacency.foreach { case (from, toSet) =>
        toSet.foreach { to =>
          reverseAdjacency.getOrElseUpdate(to, mutable.Set.empty) += from
        }
      }

      val sourceSet = sources.toSet
      sinks.foreach { sink =>
        if connectedStreamlets.contains(sink) then {
          val visited = mutable.Set.empty[Streamlet]
          val queue = mutable.Queue.empty[Streamlet]
          queue.enqueue(sink)
          visited += sink
          var reachedBySource = false

          while queue.nonEmpty && !reachedBySource do
            val current = queue.dequeue()
            if sourceSet.contains(current) then
              reachedBySource = true
            else
              reverseAdjacency.getOrElse(current, mutable.Set.empty).foreach { neighbor =>
                if !visited.contains(neighbor) then
                  visited += neighbor
                  queue.enqueue(neighbor)
              }
          end while

          if !reachedBySource then
            messages.addWarning(
              sink.errorLoc,
              s"${sink.identify} is a sink but has no upstream path from any source"
            )
        }
      }
    }
  }

  private def checkConnectorPersistence(): Unit = {
    connectors.filterNot(_.isEmpty).foreach { connector =>
      val connParents = symbols.parentsOf(connector)
      symbols.contextOf(connector) match {
        case None => require(false, "Connector with no Context")
        case Some(pipeContext) =>
          val maybeToInlet = resolvePath[Inlet](connector.to.pathId, connParents)
          val maybeFromOutlet = resolvePath[Outlet](connector.from.pathId, connParents)
          val maybeInletContext = maybeToInlet.flatMap(inlet => symbols.contextOf(inlet))
          val maybeOutletContext = maybeFromOutlet.flatMap(outlet => symbols.contextOf(outlet))
          val inletIsSameContext = maybeInletContext.nonEmpty &&
            (pipeContext == maybeInletContext.fold(Context.empty)(identity))
          val outletIsSameContext = maybeOutletContext.nonEmpty &&
            (pipeContext == maybeOutletContext.fold(Context.empty)(identity))

          if connector.hasOption("persistent") then {
            if outletIsSameContext && inletIsSameContext then {
              val message =
                s"The persistence option on ${connector.identify} is not needed " +
                  s"since both ends of the connector connect within the same context"
              val option = connector.options.find(_.name == "persistent").get
              messages.addWarning(option.loc, message)
            }
          } else {
            if !outletIsSameContext || !inletIsSameContext then {
              val message =
                s"The persistence option on ${connector.identify} should be " +
                  s"specified because an end of the connector is not connected " +
                  s"within the same context"
              messages.addWarning(connector.errorLoc, message)
            }
          }
      }
    }
  }

  private def checkUnattachedOutlets(): Unit = {
    val connected: Seq[(Outlet, Inlet)] = for
      conn <- connectors.toSeq
      parents = symbols.parentsOf(conn)
      inletRef = conn.to
      outletRef = conn.from
      inlet <- resolvePath[Inlet](inletRef.pathId, parents)
      outlet <- resolvePath[Outlet](outletRef.pathId, parents)
    yield {
      (outlet, inlet)
    }

    def findUnconnected[OI <: Portlet](portlets: scala.collection.Set[OI]): Unit = {
      portlets.foreach { portlet =>
        val message = s"${portlet.identify} is not connected"
        messages.addWarning(portlet.errorLoc, message)
      }
    }

    val inUseOutlets = connected.map(_._1)
    val unattachedOutlets: scala.collection.Set[Outlet] = outlets.toSet[Outlet] -- inUseOutlets

    findUnconnected(unattachedOutlets)

    val inUseInlets = connected.map(_._2)
    val unattachedInlets: scala.collection.Set[Inlet] = inlets.toSet[Inlet] -- inUseInlets

    findUnconnected(unattachedInlets)
  }
}
