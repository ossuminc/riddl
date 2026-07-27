/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.PredefinedModule
import com.ossuminc.riddl.passes.PassRoot
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable

trait StreamingValidation(using pc: PlatformContext) extends TypeValidation {

  /** True when `definition` is one of the predefined `Riddl` standard-module definitions (the
    * terminators `BottomlessPit`/`ForeverEmpty` and their ports). Reference identity against the
    * parsed singleton — NEVER a name match, so a user definition that happens to share the name is
    * treated as ordinary model content.
    *
    * The terminators are exempt from the streaming completeness rules because they exist precisely
    * to satisfy them: A31 (one connector per port) is meaningless for a universal drain that any
    * number of connectors may terminate in, and "not connected" / "no path to a sink" are model
    * concerns, not library concerns.
    */
  protected def isPredefined(definition: Definition): Boolean =
    PredefinedModule.isPredefined(definition)

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
    checkAsyncOverParallelization()
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

      // A single connector between two *different* external contexts satisfies both
      // perspectives; emit only ONE advisory. Prefer the outlet (producer) side when
      // it is external, otherwise consider the inlet (consumer) side.
      (outletCtx, inletCtx) match
        case (Some(oc), Some(ic)) if !(oc eq ic) =>
          if isExternalContext(oc) then
            maybeInlet.flatMap(ownerProcessor).foreach { toOwner =>
              if !toOwner.isInstanceOf[Adaptor] then advise(oc, toOwner)
            }
          else if isExternalContext(ic) then
            maybeOutlet.flatMap(ownerProcessor).foreach { fromOwner =>
              if !fromOwner.isInstanceOf[Adaptor] then advise(ic, fromOwner)
            }
          end if
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
      streamlets.filterNot(isPredefined).foreach { streamlet =>
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

      // Check 2: Source→Sink reachability via BFS. Reaching the predefined `BottomlessPit`
      // TERMINATES a pipeline just as a modelled sink does — that is the whole point of it — so
      // it satisfies reachability without being reported on itself.
      val modelStreamlets = streamlets.filterNot(isPredefined)
      val sources = modelStreamlets.filter(_.effectiveShape.isInstanceOf[Source])
      val sinks = modelStreamlets.filter(_.effectiveShape.isInstanceOf[Sink]).toSet
      val sourceSet = sources.toSet
      // A pipeline that ends in `BottomlessPit` IS terminated, and one that begins at
      // `ForeverEmpty` IS fed; the predefined terminators satisfy reachability for the model
      // streamlets they touch, while never being reported on themselves.
      def terminates(s: Streamlet): Boolean =
        sinks.contains(s) || (isPredefined(s) && s.effectiveShape.isInstanceOf[Sink])
      def originates(s: Streamlet): Boolean =
        sourceSet.contains(s) || (isPredefined(s) && s.effectiveShape.isInstanceOf[Source])

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
            if terminates(current) && current != source then reachesSink = true
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

      sinks.foreach { sink =>
        if connectedStreamlets.contains(sink) then {
          val visited = mutable.Set.empty[Streamlet]
          val queue = mutable.Queue.empty[Streamlet]
          queue.enqueue(sink)
          visited += sink
          var reachedBySource = false

          while queue.nonEmpty && !reachedBySource do
            val current = queue.dequeue()
            if originates(current) then reachedBySource = true
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
      // The predefined terminators are exempt: any number of connectors may drain into
      // `BottomlessPit.hole` or draw from `ForeverEmpty.spout`.
      resolvePath[Outlet](connector.from.pathId, connParents).filterNot(isPredefined).foreach {
        outlet => outletCounts.update(outlet, outletCounts.getOrElse(outlet, 0) + 1)
      }
      resolvePath[Inlet](connector.to.pathId, connParents).filterNot(isPredefined).foreach {
        inlet =>
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

  /** A7-ext (async over-parallelization): `option async` on a portlet marks it as a deliberate
    * async boundary so the code generator inserts a real async boundary there instead of fusing the
    * stream (cf. Akka Streams operator fusion). If EVERY portlet along a connected streaming
    * pipeline is `async`, the stream cannot be fused anywhere — a fully un-fused stream pays
    * message-passing overhead at every boundary and typically runs SLOWER than a fused one. Emit
    * ONE StyleWarning per such pipeline.
    *
    * A "pipeline" is a connected component of portlets under union-find over two kinds of edges:
    * (a) each connector joins its `from` outlet and its `to` inlet; (b) each streamlet's own
    * inlets+outlets are joined (a streamlet's ports are part of one flow). Because connectors are
    * accumulated across the whole model (including domain-scoped cross-context connectors, per
    * A31), a pipeline that spans contexts is analyzed as a single component. Only components with
    * ≥2 portlets warn (a lone async port is a legitimate single boundary), and only when ALL
    * portlets in the component are `async` (any non-async portlet means the stream still fuses
    * somewhere). Never an Error.
    */
  private def checkAsyncOverParallelization(): Unit = {
    val nodes: Array[Portlet] = (inlets.toSeq ++ outlets.toSeq).toArray
    val n = nodes.length
    if n >= 2 then {
      // Union-find over portlet indices; endpoints compared by reference identity (`eq`).
      val parent = Array.tabulate(n)(identity)
      def find(x: Int): Int = {
        var root = x
        while parent(root) != root do root = parent(root)
        var cur = x
        while parent(cur) != root do
          val next = parent(cur)
          parent(cur) = root
          cur = next
        end while
        root
      }
      def union(a: Int, b: Int): Unit = {
        val ra = find(a)
        val rb = find(b)
        if ra != rb then parent(ra) = rb
      }
      def indexOf(p: Portlet): Int = {
        var i = 0
        var found = -1
        while i < n && found < 0 do
          if nodes(i) eq p then found = i
          i += 1
        end while
        found
      }

      // Edge kind (b): a streamlet's own inlets+outlets belong to the same pipeline.
      streamlets.foreach { streamlet =>
        val portIdxs = (streamlet.inlets ++ streamlet.outlets).map(indexOf).filter(_ >= 0)
        portIdxs.headOption.foreach { head =>
          portIdxs.tail.foreach(union(head, _))
        }
      }

      // Edge kind (a): a connector joins its resolved `from` outlet and `to` inlet.
      connectors.filterNot(_.isEmpty).foreach { connector =>
        val connParents = symbols.parentsOf(connector)
        val maybeOutlet = resolvePath[Outlet](connector.from.pathId, connParents)
        val maybeInlet = resolvePath[Inlet](connector.to.pathId, connParents)
        (maybeOutlet, maybeInlet) match
          case (Some(outlet), Some(inlet)) =>
            val oi = indexOf(outlet)
            val ii = indexOf(inlet)
            if oi >= 0 && ii >= 0 then union(oi, ii)
          case _ => ()
      }

      // Group portlets into connected components by their union-find root.
      val components = mutable.LinkedHashMap.empty[Int, mutable.ListBuffer[Portlet]]
      var i = 0
      while i < n do
        components.getOrElseUpdate(find(i), mutable.ListBuffer.empty) += nodes(i)
        i += 1
      end while
      // A pipeline (≥2 portlets) with EVERY portlet marked `async` is over-parallelized.
      components.values.foreach { component =>
        if component.length >= 2 && component.forall(_.hasOption("async")) then
          val representative = component.head
          messages.addStyle(
            representative.errorLoc,
            "Every portlet in this streaming pipeline is marked 'async', so the stream cannot be " +
              "fused anywhere; a fully-async pipeline usually performs worse than a fused one because " +
              "it pays message-passing overhead at every boundary",
            suggestion =
              "Mark only the specific boundaries that genuinely need parallelism as 'async', and " +
                "leave the rest un-marked so the code generator can fuse them into a single operator."
          )
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
      // A predefined terminator's port is a library fixture, not model content: it is never
      // "unconnected" even when the model under validation is the standard module itself.
      portlets.filterNot(isPredefined).foreach { portlet =>
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
