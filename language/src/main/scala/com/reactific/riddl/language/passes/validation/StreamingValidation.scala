package com.reactific.riddl.language.passes.validation

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.ast.At

trait StreamingValidation extends ExampleValidation {

  protected var inlets: Seq[Inlet] = Seq.empty[Inlet]

  def addInlet(in: Inlet): this.type = {
    inlets = inlets :+ in
    this
  }

  protected var outlets: Seq[Outlet] = Seq.empty[Outlet]

  def addOutlet(out: Outlet): this.type = {
    outlets = outlets :+ out
    this
  }

  protected var connectors: Seq[Connector] = Seq.empty[Connector]

  def addConnector(conn: Connector): this.type = {
    connectors = connectors :+ conn
    this
  }

  protected var streamlets: Seq[Streamlet] = Seq.empty[Streamlet]

  def addStreamlet(proc: Streamlet): this.type = {
    streamlets = streamlets :+ proc
    this
  }

  def checkStreaming(): Unit = {
    checkStreamingUsage()
    checkConnectorPersistence()
    checkUnattachedOutlets()
    checkUnusedOutlets()
  }

  private def checkStreamingUsage(): Unit = {
    if (inlets.isEmpty && outlets.isEmpty && streamlets.isEmpty) {
      messages.add(
        Messages.style(
          "Models without any streaming data will exhibit minimal effect",
          At.empty
        )
      )
    }
  }

  private def checkConnectorPersistence(): Unit = {
    connectors.foreach {  connector =>
      val connParents = resolution.symbols.parentsOf(connector)
      val maybeConnContext = resolution.symbols.contextOf(connector)
      require(maybeConnContext.nonEmpty, "Connector with no Context")
      val pipeContext = maybeConnContext.get
      val maybeToInlet = connector.to.flatMap(inlet =>
        resolvePath[Inlet](inlet.pathId, connector +: connParents)
      )
      val maybeFromOutlet = connector.from.flatMap(outlet =>
        resolvePath[Outlet](outlet.pathId, connector +:connParents)
      )
      val maybeInletContext = maybeToInlet.flatMap(inlet => resolution.symbols.contextOf(inlet))
      val maybeOutletContext = maybeFromOutlet.flatMap(outlet => resolution.symbols.contextOf(outlet))
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
          messages.addStyle(connector.loc, message)
        }
      } else {
        if (!outletIsSameContext || !inletIsSameContext) {
          val message =
            s"The persistence option on ${connector.identify} should be " +
            s"specified because an end of the connector is not connected " +
            s"within the same context"
          messages.addStyle(connector.loc, message)
        }
      }
    }
  }

  private def checkUnattachedOutlets(): Unit = {
    val connected: Seq[(Outlet, Inlet)] = for {
      conn <- connectors
      parents = resolution.symbols.parentsOf(conn)
      maybeInletRef = conn.to
      inletRef <- maybeInletRef
      maybeOutletRef = conn.from
      outletRef <- maybeOutletRef
      inlet <- resolvePath[Inlet](inletRef.pathId, parents)
      outlet <- resolvePath[Outlet](outletRef.pathId, parents)
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
      messages.addMissing(outlet.loc, message)
    }
  }
}
