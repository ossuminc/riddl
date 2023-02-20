package com.reactific.riddl.language.validation

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages

trait StreamingValidationState extends ExampleValidationState {

  private var inlets: Seq[Inlet] = Seq.empty[Inlet]

  def addInlet(in: Inlet): this.type = {
    inlets = inlets :+ in
    this
  }

  private var outlets: Seq[Outlet] = Seq.empty[Outlet]

  def addOutlet(out: Outlet): this.type = {
    outlets = outlets :+ out
    this
  }

  private var connectors: Seq[Connector] = Seq.empty[Connector]

  def addConnection(conn: Connector): this.type = {
    connectors = connectors :+ conn
    this
  }

  private var streamlets: Seq[Streamlet] = Seq.empty[Streamlet]

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
      add(
        Messages.style(
          "Models without any streaming data will exhibit minimal effect",
          root.loc
        )
      )
    }
  }

  private def checkConnectorPersistence(): Unit = {
    connectors.foldLeft[this.type](this) { (state: this.type, connector) =>
      val connParents = state.symbolTable.parentsOf(connector)
      val maybeConnContext = state.symbolTable.contextOf(connector)
      require(maybeConnContext.nonEmpty, "Connector with no Context")
      val pipeContext = maybeConnContext.get
      val maybeToInlet = connector.to.flatMap(inlet =>
        state.resolvePathIdentifier[Inlet](inlet.pathId, connParents)
      )
      val maybeFromOutlet = connector.from.flatMap(outlet =>
        state.resolvePathIdentifier[Outlet](outlet.pathId, connParents)
      )
      val maybeInletContext = maybeToInlet
        .flatMap(inlet => state.symbolTable.contextOf(inlet))
      val maybeOutletContext = maybeFromOutlet
        .flatMap(outlet => state.symbolTable.contextOf(outlet))
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
          state.addStyle(connector.loc, message)
        } else state
      } else {
        if (!outletIsSameContext || !inletIsSameContext) {
          val message =
            s"The persistence option on ${connector.identify} should be " +
            s"specified because an end of the connector is not connected " +
            s"within the same context"
          state.addStyle(connector.loc, message)
        } else state
      }
    }
  }

  private def checkUnattachedOutlets(): Unit = {
    val connected: Seq[(Outlet, Inlet)] = for {
      conn <- connectors
      parents = symbolTable.parentsOf(conn)
      maybeInletRef = conn.to
      inletRef <- maybeInletRef
      maybeOutletRef = conn.from
      outletRef <- maybeOutletRef
      inlet <- resolvePathIdentifier[Inlet](inletRef.pathId, parents)
      outlet <- resolvePathIdentifier[Outlet](outletRef.pathId, parents)
    } yield {
      (outlet, inlet)
    }

    val inUseOutlets = connected.map(_._1)
    val unattachedOutlets = outlets.toSet[Outlet] -- inUseOutlets

    val s2 = unattachedOutlets.foldLeft[this.type](this) {
      (st: this.type, outlet) =>
        val message = s"${outlet.identify} is not connected"
        st.addWarning(outlet.loc, message)
    }

    val inUseInlets = connected.map(_._2)
    val unattachedInlets = inlets.toSet[Inlet] -- inUseInlets

    unattachedInlets.foldLeft[this.type](s2) { (st: this.type, inlet) =>
      val message = s"${inlet.identify} is not connected"
      st.addWarning(inlet.loc, message)
    }

  }

  private def checkUnusedOutlets(): Unit = {
    val usedOutlets: Seq[Outlet] = sends.flatMap { case (send, pars) =>
      resolvePathIdentifier[Outlet](send.portlet.pathId, pars)
    }.toSeq

    val unusedOutlets: Set[Outlet] = outlets.toSet -- usedOutlets

    for { outlet <- unusedOutlets } {
      val message = s"${outlet.identify} has nothing sent to it"
      addMissing(outlet.loc, message)
    }
  }
}
