/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Messages}

trait StreamingValidation extends TypeValidation {

  def checkStreaming(root: Root): Unit = {
    val start = root.domains.headOption.map(_.id.loc).getOrElse(At.empty)
    checkStreamingUsage(start)
    checkConnectorPersistence()
    checkUnattachedOutlets()
  }

  val inlets: Seq[Inlet] = resolution.kindMap.definitionsOfKind[Inlet]
  val outlets: Seq[Outlet] = resolution.kindMap.definitionsOfKind[Outlet]
  val streamlets: Seq[Streamlet] = resolution.kindMap.definitionsOfKind[Streamlet]
  val connectors: Seq[Connector] = resolution.kindMap.definitionsOfKind[Connector]
  val processors: Seq[Processor[?,?]] = resolution.kindMap.definitionsOfKind[Processor[?,?]]

  private def checkStreamingUsage(loc: At): Unit = {
    if inlets.isEmpty && outlets.isEmpty && streamlets.isEmpty then {
      messages.add(
        Messages.usage(
          "Models without any streaming data will exhibit minimal effect",
          loc
        )
      )
    }
  }

  private def checkConnectorPersistence(): Unit = {
    connectors.foreach { connector =>
      val connParents = symbols.parentsOf(connector)
      symbols.contextOf(connector) match {
        case None => require(false, "Connector with no Context")
        case Some(pipeContext) =>
          val maybeToInlet = connector.to.flatMap(inlet => resolvePath[Inlet](inlet.pathId, connector +: connParents))
          val maybeFromOutlet =
            connector.from.flatMap(outlet => resolvePath[Outlet](outlet.pathId, connector +: connParents))
          val maybeInletContext = maybeToInlet.flatMap(inlet => symbols.contextOf(inlet))
          val maybeOutletContext = maybeFromOutlet.flatMap(outlet => symbols.contextOf(outlet))
          val inletIsSameContext = maybeInletContext.nonEmpty &&
            (pipeContext == maybeInletContext.fold(Context.empty)(identity))
          val outletIsSameContext = maybeOutletContext.nonEmpty &&
            (pipeContext == maybeOutletContext.fold(Context.empty)(identity))

          if connector.hasOption[ConnectorPersistentOption] then {
            if outletIsSameContext && inletIsSameContext then {
              val message =
                s"The persistence option on ${connector.identify} is not needed " +
                  s"since both ends of the connector connect within the same context"
              messages.addWarning(connector.loc, message)
            }
          } else {
            if !outletIsSameContext || !inletIsSameContext then {
              val message =
                s"The persistence option on ${connector.identify} should be " +
                  s"specified because an end of the connector is not connected " +
                  s"within the same context"
              messages.addWarning(connector.loc, message)
            }
          }
      }
    }
  }

  private def checkUnattachedOutlets(): Unit = {
    val connected: Seq[(Outlet, Inlet)] = for
      conn <- connectors
      parents = symbols.parentsOf(conn)
      maybeInletRef = conn.to
      inletRef <- maybeInletRef
      maybeOutletRef = conn.from
      outletRef <- maybeOutletRef
      inlet <- resolvePath[Inlet](inletRef.pathId, conn +: parents)
      outlet <- resolvePath[Outlet](outletRef.pathId, conn +: parents)
    yield {
      (outlet, inlet)
    }

    def findUnconnected[OI <: Portlet](portlets: scala.collection.Set[OI]): Unit = {
      portlets.foreach { portlet =>
        val message = s"${portlet.identify} is not connected"
        messages.addWarning(portlet.loc, message)
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
