/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable

trait StreamingValidation(using pc: PlatformContext) extends TypeValidation {

  def addInlet(inlet: Inlet): Unit = inlets.addOne(inlet)
  def addOutlet(outlet: Outlet): Unit = outlets.addOne(outlet)
  def addStreamlet(streamlet: Streamlet): Unit = streamlets.addOne(streamlet)
  def addConnector(connector: Connector): Unit = connectors.addOne(connector)

  def checkStreaming(root: Root): Unit = {
    checkStreamingUsage(root)
    checkConnectorPersistence()
    checkUnattachedOutlets()
  }

  protected val inlets: mutable.ListBuffer[Inlet] = mutable.ListBuffer.empty
  protected val outlets: mutable.ListBuffer[Outlet] = mutable.ListBuffer.empty
  protected val streamlets: mutable.ListBuffer[Streamlet] = mutable.ListBuffer.empty
  protected val connectors: mutable.ListBuffer[Connector] = mutable.ListBuffer.empty

  private def checkStreamingUsage(root: Root): Unit = {
    if inlets.isEmpty && outlets.isEmpty && streamlets.isEmpty then {
      messages.add(
        Messages.usage(
          "Models without any streaming data will exhibit minimal effect",
          root.loc.atEnd
        )
      )
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
