/*
 * Copyright 2019-2026 Ossum Inc.
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
    checkConnectorPlacement()
    checkPortletCardinality()
    checkUnattachedOutlets()
    checkExternalContextConnectors()
  }

  /** A context is "external" if it carries the External intention or (during deprecation) the
    * legacy `external` option.
    */
  private def isExternalContext(c: Context): Boolean =
    c.intention.contains(Intention.External) || c.hasOption("external")

  /** A37 (connector-dependent rules): for every connector touching a portlet owned by an external
    * context, require the connector to carry `option persistent` (Error). Additionally, when a
    * connector directly links an external context's port to a NON-`Adaptor` processor in another
    * context, advise inserting an adaptor (suppressible StyleWarning). Endpoint resolution reuses
    * the same `resolvePath`/`contextOf` machinery as the other connector checks; only direct
    * connectors are considered (no transitive graph walk).
    */
  private def checkExternalContextConnectors(): Unit = {
    def ownerProcessor(p: Portlet): Option[Processor[?]] =
      symbols.parentOf(p).collect { case pr: Processor[?] => pr }

    connectors.filterNot(_.isEmpty).foreach { connector =>
      val connParents = symbols.parentsOf(connector)
      val maybeOutlet = resolvePath[Outlet](connector.from.pathId, connParents)
      val maybeInlet = resolvePath[Inlet](connector.to.pathId, connParents)
      val outletCtx = maybeOutlet.flatMap(symbols.contextOf)
      val inletCtx = maybeInlet.flatMap(symbols.contextOf)

      // Rule 4: persistence requirement. Emit once per distinct external context touched.
      val fromExt = outletCtx.filter(isExternalContext)
      val toExt = inletCtx.filter(isExternalContext)
      val externalTouched: Seq[Context] = (fromExt, toExt) match
        case (Some(a), Some(b)) if a eq b => Seq(a)
        case _                            => fromExt.toSeq ++ toExt.toSeq
      if externalTouched.nonEmpty && !connector.hasOption("persistent") then
        externalTouched.foreach { extCtx =>
          messages.addError(
            connector.errorLoc,
            s"Connector '${connector.id.value}' touches external context '${extCtx.id.value}' " +
              s"and must be 'persistent'",
            suggestion =
              s"Add the 'persistent' option to ${connector.identify}; connectors to an external " +
                s"context must be durable so data is not lost at the trust boundary."
          )
        }
      end if

      // Rule 5: adaptor advisory. A direct external-port → non-adaptor processor link in another
      // context should probably go through an adaptor. Best-effort, direct connectors only.
      def advise(extCtx: Context, otherOwner: Processor[?]): Unit =
        messages.addStyle(
          connector.errorLoc,
          s"Consider an adaptor between external context '${extCtx.id.value}' and " +
            s"'${otherOwner.id.value}' so the external system stays plug-in replaceable",
          suggestion =
            s"Insert an adaptor between '${extCtx.id.value}' and '${otherOwner.id.value}' and " +
              s"connect the external port to the adaptor instead of directly to ${otherOwner.identify}."
        )

      (outletCtx, inletCtx) match
        case (Some(oc), Some(ic)) if isExternalContext(oc) && !(oc eq ic) =>
          maybeInlet.flatMap(ownerProcessor).foreach { toOwner =>
            if !toOwner.isInstanceOf[Adaptor] then advise(oc, toOwner)
          }
        case _ => ()
      (outletCtx, inletCtx) match
        case (Some(oc), Some(ic)) if isExternalContext(ic) && !(oc eq ic) =>
          maybeOutlet.flatMap(ownerProcessor).foreach { fromOwner =>
            if !fromOwner.isInstanceOf[Adaptor] then advise(ic, fromOwner)
          }
        case _ => ()
    }
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
        streamlet.effectiveShape match {
          case _: Void => () // Void streamlets are excluded
          case _ =>
            if !connectedStreamlets.contains(streamlet) then
              messages.addCompleteness(
                streamlet.errorLoc,
                s"${streamlet.identify} has no connections to any connector",
                suggestion =
                  s"Connect ${streamlet.identify} to another streamlet with a connector, " +
                    "e.g. 'connector c is { from outlet ThisOutlet to inlet ThatInlet }'."
              )
        }
      }

      // Check 2: Source→Sink reachability via BFS
      val sources = streamlets.filter(_.effectiveShape.isInstanceOf[Source])
      val sinks = streamlets.filter(_.effectiveShape.isInstanceOf[Sink]).toSet

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
            if sinks.contains(current) && current != source then reachesSink = true
            else
              adjacency.getOrElse(current, mutable.Set.empty).foreach { neighbor =>
                if !visited.contains(neighbor) then
                  visited += neighbor
                  queue.enqueue(neighbor)
              }
          end while

          if !reachesSink then
            messages.addCompleteness(
              source.errorLoc,
              s"${source.identify} is a source but has no downstream path to any sink",
              suggestion =
                "Add connectors routing this source's outlet through to a sink so the data it produces is consumed."
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
            if sourceSet.contains(current) then reachedBySource = true
            else
              reverseAdjacency.getOrElse(current, mutable.Set.empty).foreach { neighbor =>
                if !visited.contains(neighbor) then
                  visited += neighbor
                  queue.enqueue(neighbor)
              }
          end while

          if !reachedBySource then
            messages.addCompleteness(
              sink.errorLoc,
              s"${sink.identify} is a sink but has no upstream path from any source",
              suggestion =
                "Add connectors routing a source's output into this sink so it receives data."
            )
        }
      }
    }
  }

  /** Validate connector placement (scope) and persistence. Each end is resolved to its containing
    * context and domain, and the connector's own scope (context vs domain) is taken from whether it
    * has a containing context. From that:
    *   - Rule 1: ends in different domains -> ERROR (a cross-domain edge is a domain-analysis
    *     failure); terminal — no further placement/persistence checks.
    *   - Rule 2: a domain-scoped connector whose ends share ONE context is over-scoped -> ERROR.
    *   - Rule 3: a context-scoped connector whose ends cross contexts is under-scoped -> ERROR.
    *   - Rule 4: a correctly domain-scoped (cross-context) connector lacking `persistent` ->
    *     WARNING (lose-nothing durability across a context boundary can be required for model
    *     correctness).
    *   - A same-context connector that nonetheless declares `persistent` -> WARNING (not needed).
    */
  private def checkConnectorPlacement(): Unit = {
    def domainOf(d: Definition): Option[Domain] =
      symbols.parentsOf(d).collectFirst { case dom: Domain => dom }

    connectors.filterNot(_.isEmpty).foreach { connector =>
      val connParents = symbols.parentsOf(connector)
      // No containing context => the connector sits directly in a domain (or higher).
      val connectorInDomain = symbols.contextOf(connector).isEmpty
      val maybeToInlet = resolvePath[Inlet](connector.to.pathId, connParents)
      val maybeFromOutlet = resolvePath[Outlet](connector.from.pathId, connParents)
      val outletCtx = maybeFromOutlet.flatMap(symbols.contextOf)
      val inletCtx = maybeToInlet.flatMap(symbols.contextOf)
      val outletDom = maybeFromOutlet.flatMap(domainOf)
      val inletDom = maybeToInlet.flatMap(domainOf)

      val sameContext = (outletCtx, inletCtx) match
        case (Some(a), Some(b)) => a eq b
        case _                  => false
      val crossContext = (outletCtx, inletCtx) match
        case (Some(a), Some(b)) => !(a eq b)
        case _                  => false
      val crossDomain = (outletDom, inletDom) match
        case (Some(a), Some(b)) => !(a eq b)
        case _                  => false

      if crossDomain then
        messages.addError(
          connector.errorLoc,
          s"${connector.identify} connects across domains (${outletDom.get.identify} and " +
            s"${inletDom.get.identify}); a connector that crosses a domain boundary indicates a " +
            s"failure of domain analysis and is not allowed",
          suggestion =
            "Keep the connector within one domain; if two domains must communicate, model it with " +
              "an adaptor and messaging rather than a direct stream connector."
        )
      else if connectorInDomain then
        if sameContext then
          messages.addError(
            connector.errorLoc,
            s"${connector.identify} is defined at domain scope but both ends are within " +
              s"${outletCtx.get.identify}; the connector is over-scoped",
            suggestion =
              s"Move ${connector.identify} into ${outletCtx.get.identify}; a connector whose ends " +
                s"are in the same context belongs in that context, not the domain."
          )
        else if crossContext && !connector.hasOption("persistent") then
          // CompletenessWarning (not a plain Warning) so AI/tooling can adapt: durability across a
          // context boundary can be required for model correctness, not merely a deployment concern.
          messages.addCompleteness(
            connector.errorLoc,
            s"${connector.identify} spans a context boundary but is not 'persistent'; durability " +
              s"across a context boundary can be required for model correctness, not merely a " +
              s"deployment concern",
            suggestion =
              s"Add the 'persistent' option to ${connector.identify} so data is not lost across " +
                s"faults at the context boundary."
          )
        end if
      else // connector is at context scope
        if crossContext then
          messages.addError(
            connector.errorLoc,
            s"${connector.identify} connects across contexts (${outletCtx.get.identify} and " +
              s"${inletCtx.get.identify}) but is defined at context scope; it is under-scoped",
            suggestion =
              "Move the connector up to the domain that contains both contexts; a connector that " +
                "crosses contexts must be defined at domain scope."
          )
        else if sameContext && connector.hasOption("persistent") then
          val option = connector.options.find(_.name == "persistent").get
          messages.addWarning(
            option.loc,
            s"The persistence option on ${connector.identify} is not needed " +
              s"since both ends of the connector connect within the same context",
            suggestion =
              s"Remove the 'persistent' option from ${connector.identify}; both ends are in the same context."
          )
        end if
    }
  }

  /** A31: exactly one connector may attach to any given inlet or outlet. Fan-in/out is modeled by
    * declaring MULTIPLE ports on a processor (which changes its arity-derived shape to merge/split/
    * router), never by attaching several connectors to a single port. Resolve each connector's
    * `from` outlet and `to` inlet to the actual [[Outlet]]/[[Inlet]] definitions, count how many
    * connectors reference each, and emit an Error for any portlet referenced by more than one. Zero
    * connectors is the separate `checkUnattachedOutlets` completeness concern and is not handled
    * here.
    */
  private def checkPortletCardinality(): Unit = {
    val outletCounts = mutable.LinkedHashMap.empty[Outlet, Int]
    val inletCounts = mutable.LinkedHashMap.empty[Inlet, Int]

    connectors.filterNot(_.isEmpty).foreach { connector =>
      val connParents = symbols.parentsOf(connector)
      resolvePath[Outlet](connector.from.pathId, connParents).foreach { outlet =>
        outletCounts.update(outlet, outletCounts.getOrElse(outlet, 0) + 1)
      }
      resolvePath[Inlet](connector.to.pathId, connParents).foreach { inlet =>
        inletCounts.update(inlet, inletCounts.getOrElse(inlet, 0) + 1)
      }
    }

    outletCounts.foreach { case (outlet, count) =>
      if count > 1 then
        messages.addError(
          outlet.errorLoc,
          s"Outlet '${outlet.id.value}' is connected by $count connectors; exactly one is allowed " +
            "(model fan-out with multiple outlets)",
          suggestion =
            "Attach only one connector to this outlet; to fan out, declare additional outlets on the " +
              "processor (which makes it a split or router) and connect each separately."
        )
    }

    inletCounts.foreach { case (inlet, count) =>
      if count > 1 then
        messages.addError(
          inlet.errorLoc,
          s"Inlet '${inlet.id.value}' is connected by $count connectors; exactly one is allowed " +
            "(model fan-in with multiple inlets)",
          suggestion =
            "Attach only one connector to this inlet; to fan in, declare additional inlets on the " +
              "processor (which makes it a merge or router) and connect each separately."
        )
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
        messages.addCompleteness(
          portlet.errorLoc,
          message,
          suggestion =
            s"Connect ${portlet.identify} with a connector, or remove it if it is unused."
        )
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
