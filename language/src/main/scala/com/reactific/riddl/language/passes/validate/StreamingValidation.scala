package com.reactific.riddl.language.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.ast.At

trait StreamingValidation extends ExampleValidation {


  def checkStreaming(root: RootContainer): Unit = {
    val start = root.domains.headOption.map(_.id.loc).getOrElse(At.empty)
    checkStreamingUsage(start)
    checkConnectorPersistence()
    checkUnattachedOutlets()
    checkUnusedOutlets()
  }

  val inlets: Seq[Inlet] = resolution.kindMap.definitionsOfKind[Inlet]
  val outlets: Seq[Outlet] = resolution.kindMap.definitionsOfKind[Outlet]
  val streamlets: Seq[Streamlet] = resolution.kindMap.definitionsOfKind[Streamlet]
  val connectors: Seq[Connector] = resolution.kindMap.definitionsOfKind[Connector]

  private def checkStreamingUsage(loc: At): Unit = {
    if (inlets.isEmpty && outlets.isEmpty && streamlets.isEmpty) {
      messages.add(
        Messages.usage(
          "Models without any streaming data will exhibit minimal effect",
          loc
        )
      )
    }
  }

  private def checkConnectorPersistence(): Unit = {
    connectors.foreach {  connector =>
      val connParents = symbols.parentsOf(connector)
      val maybeConnContext = symbols.contextOf(connector)
      require(maybeConnContext.nonEmpty, "Connector with no Context")
      val pipeContext = maybeConnContext.get
      val maybeToInlet = connector.to.flatMap(inlet =>
        resolvePath[Inlet](inlet.pathId, connector +: connParents)
      )
      val maybeFromOutlet = connector.from.flatMap(outlet =>
        resolvePath[Outlet](outlet.pathId, connector +:connParents)
      )
      val maybeInletContext = maybeToInlet.flatMap(inlet => symbols.contextOf(inlet))
      val maybeOutletContext = maybeFromOutlet.flatMap(outlet => symbols.contextOf(outlet))
      val inletIsSameContext = maybeInletContext.nonEmpty &&
        (pipeContext == maybeInletContext.get)
      val outletIsSameContext = maybeOutletContext.nonEmpty &&
        (pipeContext == maybeOutletContext.get)

      if (connector.hasOption[ConnectorPersistentOption]) {
        if (outletIsSameContext && inletIsSameContext) {
          val message =
            s"The persistence option on ${connector.identify} is not " +
              s"needed since both ends of the connector connect within the same " +
              s"context"
          messages.addWarning(connector.loc, message)
        }
      } else {
        if (!outletIsSameContext || !inletIsSameContext) {
          val message =
            s"The persistence option on ${connector.identify} should be " +
            s"specified because an end of the connector is not connected " +
            s"within the same context"
          messages.addWarning(connector.loc, message)
        }
      }
    }
  }

  private def checkUnattachedOutlets(): Unit = {
    val connected: Seq[(Outlet, Inlet)] = for {
      conn <- connectors
      parents = symbols.parentsOf(conn)
      maybeInletRef = conn.to
      inletRef <- maybeInletRef
      maybeOutletRef = conn.from
      outletRef <- maybeOutletRef
      inlet <- resolvePath[Inlet](inletRef.pathId, conn +: parents)
      outlet <- resolvePath[Outlet](outletRef.pathId, conn +: parents)
    } yield {
      (outlet, inlet)
    }

    val inUseOutlets = connected.map(_._1)
    val unattachedOutlets = outlets.toSet[Outlet] -- inUseOutlets

    unattachedOutlets.foreach { outlet =>
        val message = s"${outlet.identify} is not connected"
        messages.addWarning(outlet.loc, message)
    }

    val inUseInlets = connected.map(_._2)
    val unattachedInlets = inlets.toSet[Inlet] -- inUseInlets

    unattachedInlets.foreach { inlet =>
      val message = s"${inlet.identify} is not connected"
      messages.addWarning(inlet.loc, message)
    }

  }

  private def checkUnusedOutlets(): Unit = {
    val usedOutlets: Seq[Outlet] = sends.flatMap { case (send, pars) =>
      resolvePath[Outlet](send.portlet.pathId, pars)
    }.toSeq

    val unusedOutlets: scala.collection.Set[Outlet] = outlets.toSet --
      usedOutlets

    for { outlet <- unusedOutlets } {
      val message = s"${outlet.identify} has nothing sent to it"
      messages.addUsage(outlet.loc, message)
    }
  }
}
