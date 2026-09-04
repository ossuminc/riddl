/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.RuleId
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

  /** Register a [[Processor]] as a node of the streaming graph.
    *
    * EVERY processor kind participates, not just [[Streamlet]]: since the unified processor model,
    * ports and stream shape live on [[Processor]], so an Adaptor, Entity, Projector, Repository or
    * Context that declares ports is a genuine node in a stream path. A processor with no ports has
    * a [[Void]] shape and is excluded by the checks themselves, so registering it costs nothing.
    */
  def addProcessor(processor: Processor[?]): Unit = processors.addOne(processor)

  @deprecated(
    "Use addProcessor: the streaming graph covers every Processor kind, not only Streamlet",
    "2.0.0"
  )
  def addStreamlet(streamlet: Streamlet): Unit = addProcessor(streamlet)

  def addConnector(connector: Connector): Unit = connectors.addOne(connector)

  /** Is this processor the END of a stream chain? Implemented by [[ValidationPass]], which owns the
    * handler-clause and alternation helpers the answer needs. See its doc for the ruling.
    */
  protected def isStreamTail(proc: Processor[?]): Boolean

  /** The members a type admits, expanded through any alternation; the type itself otherwise. */
  protected def typeMembers(t: Type): Seq[Type]

  def checkStreaming(root: PassRoot): Unit = {
    checkStreamingUsage(root)
    checkStreamCycles()
    checkConnectorPlacement()
    checkPortletCardinality()
    checkUnattachedOutlets()
    checkExternalContextConnectors()
    checkAsyncOverParallelization()
  }

  /** A context is "external" if it carries the External intention or (during deprecation) the
    * legacy `external` option.
    *
    * PROTECTED, not private, because asking only one of those two questions is a bug that has been
    * made three times. `external context Foo` sets the INTENTION and is what models actually write;
    * `with { option external }` sets the option. Testing `hasOption` alone missed every corpus use
    * and produced 1120 false warnings in a single run (2026-08-12). Every external exemption goes
    * through this method — do not re-inline either half.
    */
  protected def isExternalContext(c: Context): Boolean =
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

      // Rule 4: persistence requirement. Emit once per distinct external context CROSSED.
      //
      // **It is CROSSING that matters, not touching (riddl-models, 2026-08-18; Reid's reading).**
      // This used to fire on any connector with an endpoint in an external context, including one
      // whose BOTH ends were inside the SAME external context -- which crosses nothing. That put
      // the author in a trap with no legal spelling: without the keyword this Error demanded it,
      // and with the keyword `checkConnectorPlacement` warned that "persistence is not needed
      // since both ends of the connector connect within the same context". Same line, one word,
      // two contradictory diagnostics and no third option.
      //
      // The shape is not exotic -- it is a DIRECT consequence of the cross-context boundary Error
      // (`c67cfdbfd`): a peer may no longer land on a definition inside a context, so an external
      // context that publishes must declare its own outlet, and its source processor reaches that
      // outlet through a connector that is necessarily wholly inside the external context.
      // riddl-models hit it 12 times across PaymentGateway, NotificationService, HRSystem,
      // PrintingService, PhotographyService, AccountingSystem and SupplierSystem.
      //
      // `sameContext` is computed exactly as `checkConnectorPlacement` computes it, so the two
      // checks can no longer disagree about what "the same context" means.
      val sameContext = (outletCtx, inletCtx) match
        case (Some(a), Some(b)) => a eq b
        case _                  => false
      val fromExt = outletCtx.filter(isExternalContext)
      val toExt = inletCtx.filter(isExternalContext)
      val externalTouched: Seq[Context] =
        if sameContext then Seq.empty else fromExt.toSeq ++ toExt.toSeq
      if externalTouched.nonEmpty && !connector.isPersistent then
        externalTouched.foreach { extCtx =>
          messages.addError(
            connector.errorLoc,
            s"Connector '${connector.id.value}' touches external context '${extCtx.id.value}' " +
              s"and must be 'persistent'",
            suggestion =
              s"Add the 'persistent' option to ${connector.identify}; connectors to an external " +
                s"context must be durable so data is not lost at the trust boundary.",
            ruleId = Some(RuleId.ConnectorTouchesExternal)
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
              s"connect the external port to the adaptor instead of directly to ${otherOwner.identify}.",
          ruleId = Some(RuleId.ConsiderAdaptor)
        )

      // A single connector between two *different* external contexts satisfies both
      // perspectives; emit only ONE advisory. Prefer the outlet (producer) side when
      // it is external, otherwise consider the inlet (consumer) side.
      // Does `peer` already defend itself against `extCtx` with an anti-corruption layer?
      //
      // **Required since the boundary Error (riddl-models, 2026-08-18).** The owner test below is
      // no longer sufficient on its own: a cross-context connector must now terminate on the
      // CONTEXT'S OWN portlet, so `ownerProcessor` is always the Context and never an Adaptor.
      // The advisory therefore fired unconditionally -- and asked for precisely the arrangement
      // the Error forbids, since an Adaptor is content of a context and can no longer be the
      // landing point. It was unsatisfiable by construction. (Reid ruled [1.6] the same day with
      // NO adaptor exemption, so the fix belongs here, in the advisory, not in the Error.)
      //
      // Matching on `referent` rather than on "has any adaptor" keeps the advisory useful: a
      // context defended against one external system still gets advised about a different one.
      // Resolve with the PARENT-INDEPENDENT `definitionOf`, not `resolvePath`. `resolvePath` keys
      // the refMap on `parents.head`, and for an adaptor's `referent` the recorded parent is the
      // ADAPTOR itself -- `symbols.parentsOf(adaptor)` hands back its enclosing Context instead,
      // so the lookup missed and the advisory kept firing. This is the same trap CLAUDE.md already
      // records for adaptor cross-context TYPE resolution; it applies to the context ref too.
      // Caught by a test, not by reading.
      def hasAdaptorFor(peer: Context, extCtx: Context): Boolean =
        peer.adaptors.exists { adaptor =>
          resolution.refMap
            .definitionOf[Context](adaptor.referent.pathId, adaptor)
            .exists(_ eq extCtx)
        }

      (outletCtx, inletCtx) match
        case (Some(oc), Some(ic)) if !(oc eq ic) =>
          if isExternalContext(oc) then
            maybeInlet.flatMap(ownerProcessor).foreach { toOwner =>
              if !toOwner.isInstanceOf[Adaptor] && !hasAdaptorFor(ic, oc) then advise(oc, toOwner)
            }
          else if isExternalContext(ic) then
            maybeOutlet.flatMap(ownerProcessor).foreach { fromOwner =>
              if !fromOwner.isInstanceOf[Adaptor] && !hasAdaptorFor(oc, ic) then advise(ic, fromOwner)
            }
          end if
        case _ => ()
    }
  }

  protected val inlets: mutable.ListBuffer[Inlet] = mutable.ListBuffer.empty
  protected val outlets: mutable.ListBuffer[Outlet] = mutable.ListBuffer.empty
  protected val processors: mutable.ListBuffer[Processor[?]] = mutable.ListBuffer.empty
  protected val connectors: mutable.ListBuffer[Connector] = mutable.ListBuffer.empty

  /** A node of the streaming graph, keyed by IDENTITY.
    *
    * Never key these collections on the processor itself. `Definition.equals` is structural and
    * `hashCode` is (id, loc, class), so it is `loc` that distinguishes two same-named processors —
    * by accident. On a tree built WITHOUT locations (one read back from JSON, where every `loc` is
    * `At.empty`) two distinct processors sharing a name collapse into ONE key, silently merging
    * their edges and producing reachability answers for a graph the model does not contain. This is
    * the same hazard, and the same remedy, as [[ByIdentity]] in `checkPortletCardinality`.
    */
  private type Node = ByIdentity[Processor[?]]

  /** processor -> processors directly downstream of it, following `from outlet` -> `to inlet`.
    *
    * Extracted 2026-09-02 so `ValidationPass.checkTellReachability` walks the SAME graph the
    * streaming checks walk. Building a second adjacency map was the alternative and it is the
    * shape this repo keeps recording as a defect: two copies of one derivation that can disagree,
    * where only one of them is exercised by the tests that matter.
    */
  protected def connectorAdjacency()
    : Map[ByIdentity[Processor[?]], scala.collection.immutable.Set[ByIdentity[Processor[?]]]] = {
    val adj = mutable.Map.empty[ByIdentity[Processor[?]], mutable.Set[ByIdentity[Processor[?]]]]
    connectors.filterNot(_.isEmpty).foreach { connector =>
      val connParents = symbols.parentsOf(connector)
      val from = resolvePath[Outlet](connector.from.pathId, connParents)
        .flatMap(o => symbols.parentOf(o).collect { case p: Processor[?] => p })
      val to = resolvePath[Inlet](connector.to.pathId, connParents)
        .flatMap(i => symbols.parentOf(i).collect { case p: Processor[?] => p })
      (from, to) match
        case (Some(f), Some(t)) =>
          adj.getOrElseUpdate(ByIdentity(f), mutable.Set.empty) += ByIdentity(t)
        case _ => ()
      end match
    }
    adj.map { case (k, v) => k -> v.toSet }.toMap
  }

  private def checkStreamingUsage(root: PassRoot): Unit = {
    if processors.nonEmpty then {
      def node(p: Processor[?]): Node = ByIdentity(p)

      // Build a map from each processor to its connected processors via connectors.
      // First, resolve all connector endpoints to their owning processors. EVERY Processor kind is
      // a node: a port's owner may be an Adaptor, Context, Entity, Projector or Repository just as
      // readily as a Streamlet, and each conveys data through the path exactly the same way.
      val connectedProcessors = mutable.Set.empty[Node]
      // Adjacency list: processor → set of downstream processors (outlet→inlet direction)
      val adjacency = mutable.Map.empty[Node, mutable.Set[Node]]

      connectors.filterNot(_.isEmpty).foreach { connector =>
        val connParents = symbols.parentsOf(connector)
        val maybeOutlet = resolvePath[Outlet](connector.from.pathId, connParents)
        val maybeInlet = resolvePath[Inlet](connector.to.pathId, connParents)

        val maybeFromProcessor = maybeOutlet.flatMap { outlet =>
          symbols.parentOf(outlet).collect { case p: Processor[?] => p }
        }
        val maybeToProcessor = maybeInlet.flatMap { inlet =>
          symbols.parentOf(inlet).collect { case p: Processor[?] => p }
        }

        (maybeFromProcessor, maybeToProcessor) match {
          case (Some(from), Some(to)) =>
            connectedProcessors += node(from)
            connectedProcessors += node(to)
            adjacency.getOrElseUpdate(node(from), mutable.Set.empty) += node(to)
          case (Some(from), None) =>
            connectedProcessors += node(from)
          case (None, Some(to)) =>
            connectedProcessors += node(to)
          case _ => ()
        }
      }

      // Check 1: Isolated processors (non-Void, not connected to any connector). A processor with
      // no ports has a Void shape, so every portless context/entity/repository is excluded here.
      processors.filterNot(isPredefined).foreach { processor =>
        processor.effectiveShape match {
          case _: Void => () // Void processors (no ports) are excluded
          case _ =>
            if !connectedProcessors.contains(node(processor)) then
              messages.addCompleteness(
                processor.errorLoc,
                s"${processor.identify} has no connections to any connector",
                suggestion =
                  s"Connect ${processor.identify} to another processor with a connector, " +
                    "e.g. 'connector c is { from outlet ThisOutlet to inlet ThatInlet }'.",
                ruleId = Some(RuleId.ProcessorUnconnected)
              )
        }
      }

      // Check 2: Source→tail reachability via BFS. Reaching the predefined `BottomlessPit`
      // TERMINATES a pipeline just as a modelled tail does — that is the whole point of it — so
      // it satisfies reachability without being reported on itself.
      val modelProcessors = processors.filterNot(isPredefined)
      val sources = modelProcessors.filter(_.effectiveShape.isInstanceOf[Source])
      val sinks = modelProcessors.filter(_.effectiveShape.isInstanceOf[Sink])
      val sourceNodes = sources.map(node).toSet
      // A chain TAIL is defined by what the processor DOES with what arrives — it handles every
      // type its inlets admit and passes none of them on — never by a Sink SHAPE (Reid, 2026-09-04,
      // mirroring the chain-head ruling below). Until then this asked `sinkNodes.contains(n)`, and
      // A6 made that unsatisfiable: a terminal log that records to its repository must own the
      // outlet it writes on, so by arity it is a flow, and every source above it was reported as
      // reaching nothing. `isStreamTail` lives in ValidationPass, which owns the helpers.
      // A pipeline that ends in `BottomlessPit` IS terminated, and one that begins at
      // `ForeverEmpty` IS fed; the predefined terminators satisfy reachability for the model
      // processors they touch, while never being reported on themselves.
      def terminates(n: Node): Boolean =
        isStreamTail(n.value) ||
          (isPredefined(n.value) && n.value.effectiveShape.isInstanceOf[Sink])
      // Built here rather than at Check 3 because `originates` below needs it: a chain head is
      // defined by having no inbound edge.
      val reverseAdjacency = mutable.Map.empty[Node, mutable.Set[Node]]
      adjacency.foreach { case (from, toSet) =>
        toSet.foreach { to =>
          reverseAdjacency.getOrElseUpdate(to, mutable.Set.empty) += from
        }
      }

      // A chain HEAD is where data enters the graph, and Reid ruled 2026-08-14 that it need only
      // bear an OUTLET -- "a chain of outlet-connector-inlet MUST start with an outlet (Source,
      // Merge, Flow, Split, Router), never a Sink (only has inlet(s))". `Void` is excluded too,
      // having neither port, which the enumeration does not say but the rule requires.
      //
      // Asking "is it shaped `Source`?" was too narrow and reported models that are correctly
      // wired. reactive-bbq is the case that showed it: `TableOrderRepository` (sink) <-
      // `TableOrderEventSplit` (split) <- `TableOrder` (entity as flow) <- `RestaurantApp`
      // (application context as router). There is no `Source`-shaped processor anywhere in that
      // chain and there does not need to be -- data enters through an application context fed by
      // USERS, from outside the streaming graph entirely. The repositories were wired, not
      // unwired, so the RULE was wrong rather than merely its wording.
      //
      // A head is therefore any node with an outlet and no inbound edge. A `Source` always
      // qualifies (no inlets, so never an edge target) and is kept explicitly for the predefined
      // `ForeverEmpty`, which satisfies reachability for what it feeds without being reported on.
      def hasOutlet(p: Processor[?]): Boolean = p.outlets.nonEmpty
      def isGraphHead(n: Node): Boolean =
        hasOutlet(n.value) && !reverseAdjacency.contains(n)
      def originates(n: Node): Boolean =
        sourceNodes.contains(n) ||
          (isPredefined(n.value) && n.value.effectiveShape.isInstanceOf[Source]) ||
          isGraphHead(n)

      sources.foreach { source =>
        val start = node(source)
        // A source with no outgoing edge at all is Check 1's concern ("no connections"), so it is
        // skipped here rather than reported twice.
        if adjacency.contains(start) then {
          // BFS from this source
          val visited = mutable.Set.empty[Node]
          val queue = mutable.Queue.empty[Node]
          queue.enqueue(start)
          visited += start
          var reachesSink = false

          while queue.nonEmpty && !reachesSink do
            val current = queue.dequeue()
            if terminates(current) && !(current.value eq source) then reachesSink = true
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
                "Route this source's outlet, through connectors, to a processor that handles every " +
                  "message type its inlets admit and does not send that same type onward.",
              ruleId = Some(RuleId.SourceReachesNoSink)
            )
        }
      }

      // Check 3: Sink←Source reverse reachability via BFS
      sinks.foreach { sink =>
        val start = node(sink)
        if connectedProcessors.contains(start) then {
          val visited = mutable.Set.empty[Node]
          val queue = mutable.Queue.empty[Node]
          queue.enqueue(start)
          visited += start
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
                "Add connectors routing a source's output into this sink so it receives data.",
              ruleId = Some(RuleId.SinkReachedByNoSource)
            )
        }
      }
    }
  }

  /** A stream graph may not contain a cycle (Reid's ruling, 2026-09-04): *"They can be long and
    * convoluted, but they must have a start and a finish."* Connectors carrying one message type
    * that form a loop let a message circulate forever, and that is an Error, not a completeness
    * question.
    *
    * PER TYPE, deliberately. Two contexts that exchange a command one way and an event back form a
    * loop of processors and no loop of messages — a request/response pair is two chains, each with
    * a start and a finish. So edges are grouped by the type the OUTLET carries (alternation members
    * expanded, so a connector typed `one of { A or B }` and one typed `A` join the same graph), and
    * a cycle must close within one type. This is the same "same type" test `isStreamTail` applies
    * at a chain's end, and for the same reason.
    *
    * Each cycle is reported once, at its first member, naming every member in order. A self-loop
    * (an outlet wired to an inlet of the same processor) is a cycle of one.
    */
  private def checkStreamCycles(): Unit = {
    val edgesByType = mutable.Map.empty[ByIdentity[Type], mutable.Map[Node, mutable.Set[Node]]]
    val typeOf = mutable.Map.empty[ByIdentity[Type], Type]
    connectors.filterNot(_.isEmpty).foreach { connector =>
      val connParents = symbols.parentsOf(connector)
      val maybeOutlet = resolvePath[Outlet](connector.from.pathId, connParents)
      val from = maybeOutlet.flatMap(o => symbols.parentOf(o).collect { case p: Processor[?] => p })
      val to = resolvePath[Inlet](connector.to.pathId, connParents)
        .flatMap(i => symbols.parentOf(i).collect { case p: Processor[?] => p })
      val carried: Seq[Type] = maybeOutlet.toSeq
        .flatMap(o => resolution.refMap.definitionOf[Type](o.type_.pathId))
        .flatMap(typeMembers)
      for
        f <- from
        t <- to
        ty <- carried
      do
        val key = ByIdentity(ty)
        typeOf(key) = ty
        edgesByType
          .getOrElseUpdate(key, mutable.Map.empty)
          .getOrElseUpdate(ByIdentity(f), mutable.Set.empty) += ByIdentity(t)
      end for
    }

    def byPosition(n: Node): Int = n.value.loc.offset

    edgesByType.toSeq.sortBy { case (k, _) => typeOf(k).loc.offset }.foreach { case (key, adj) =>
      val ty = typeOf(key)
      val state = mutable.Map.empty[Node, Int] // 0 unvisited, 1 on the current path, 2 done
      val path = mutable.ArrayBuffer.empty[Node]
      val reported = mutable.Set.empty[scala.collection.immutable.Set[Node]]

      def report(cycle: Seq[Node]): Unit =
        val members = (cycle :+ cycle.head).map(_.value.identify).mkString(" -> ")
        messages.addError(
          cycle.head.value.errorLoc,
          s"Connectors carrying ${ty.identify} form a cycle: $members; a stream must have a " +
            "start and a finish, so a message must never be able to return to a processor it " +
            "already passed through",
          suggestion = "Remove or retarget one connector in the loop so the message cannot circulate.",
          ruleId = Some(RuleId.GraphCycle)
        )

      def visit(n: Node): Unit =
        state(n) = 1
        path += n
        adj.getOrElse(n, mutable.Set.empty).toSeq.sortBy(byPosition).foreach { m =>
          state.getOrElse(m, 0) match
            case 0 => visit(m)
            case 1 =>
              val cycle = path.drop(path.indexOf(m)).toSeq
              if reported.add(cycle.toSet) then report(cycle)
            case _ => ()
          end match
        }
        path.remove(path.length - 1)
        state(n) = 2
      end visit

      adj.keys.toSeq.sortBy(byPosition).foreach { n =>
        if state.getOrElse(n, 0) == 0 then visit(n)
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
  /** THE CONTEXT IS THE SINK AT THE BOUNDARY (Reid's ruling, 2026-08-18).
    *
    * A connector that crosses a context boundary must terminate on the CONTEXT'S OWN portlet at
    * each end -- the source context's own outlet, the target context's own inlet -- and never reach
    * past the boundary onto a portlet declared by something the context contains.
    *
    * This is an **Error, not a warning**, and the reason is the whole point of a bounded context.
    * Reaching straight at a context's contents contradicts the boundary rather than under-stating
    * it: a context publishes a public API -- its message set -- while its representations and inner
    * workings stay private. A cross-context connector wired to a contained entity's inlet binds a
    * peer to that entity's existence and to its current command/query set, so the entity can no
    * longer change without breaking a stranger. That is a contradiction of the model, which is what
    * an Error means here, as against the omission a CompletenessWarning means.
    *
    * INTRA-context this rule does not apply at all, and deliberately: inside one context anything
    * may talk to anything, and a connector may drive a contained entity's own inlet directly.
    */
  private def checkBoundaryEncapsulation(
    connector: Connector,
    maybeFromOutlet: Option[Outlet],
    maybeToInlet: Option[Inlet],
    outletCtx: Option[Context],
    inletCtx: Option[Context]
  ): Unit = {
    // The port belongs to the context ITSELF when the context is its immediate parent. Anything
    // else -- an entity, projector, repository, adaptor or shape-keyword streamlet -- is content
    // the boundary exists to keep private.
    def ownerOf(portlet: Definition): Option[Branch[?]] = symbols.parentOf(portlet)

    for outlet <- maybeFromOutlet; ctx <- outletCtx do
      val owner = ownerOf(outlet)
      if !owner.exists(_ eq ctx) then
        messages.addError(
          connector.errorLoc,
          s"${connector.identify} crosses a context boundary but leaves from an outlet of " +
            s"${owner.map(_.identify).getOrElse("an unresolved definition")}, which is inside " +
            s"${ctx.identify}; a context is the SOURCE for everything leaving it",
          suggestion =
            s"Declare an outlet on ${ctx.identify} itself and connect it from there; route the " +
              s"inner definition's outlet to it within ${ctx.identify}. The boundary exists to " +
              s"keep the context's contents private -- only its message set is public.",
          ruleId = Some(RuleId.BoundaryOutlet)
        )
      end if
    end for

    for inlet <- maybeToInlet; ctx <- inletCtx do
      val owner = ownerOf(inlet)
      if !owner.exists(_ eq ctx) then
        messages.addError(
          connector.errorLoc,
          s"${connector.identify} crosses a context boundary but arrives at an inlet of " +
            s"${owner.map(_.identify).getOrElse("an unresolved definition")}, which is inside " +
            s"${ctx.identify}; a context is the SINK for everything entering it",
          suggestion =
            s"Declare an inlet on ${ctx.identify} itself and connect to that; let its handlers " +
              s"dispatch or translate inward. Reaching past the boundary binds the sender to " +
              s"${ctx.identify}'s internals, which it is entitled to change.",
          ruleId = Some(RuleId.BoundaryInlet)
        )
      end if
    end for
  }

  private def checkConnectorPlacement(): Unit = {
    def domainOf(d: Definition): Option[Domain] =
      symbols.parentsOf(d).collectFirst { case dom: Domain => dom }

    /** Every enclosing Domain, nearest first. `domainOf` takes only the NEAREST, which cannot tell
      * two divisions of one enterprise from two unrelated domains -- the distinction the
      * cross-domain rule actually turns on.
      */
    def domainChain(d: Definition): Seq[Domain] =
      symbols.parentsOf(d).collect { case dom: Domain => dom }

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
      // Endpoints under a COMMON ANCESTOR domain are related, and a connector between them is
      // permitted (Reid, 2026-09-03). The rule exists to catch a connector between UNRELATED
      // domains -- "a failure of domain analysis" -- and a shared ancestor rules that out: it is
      // movement inside one enterprise between two of its own divisions.
      //
      // **Ruled for SIBLINGS; implemented as common ancestor**, which subsumes it. The rationale
      // is about relatedness, not depth, so `Corporate.Finance -> Restaurant.FrontOfHouse` is the
      // same kind of movement as `Corporate -> Restaurant` and nothing in the reasoning separates
      // them. Top-level domains with no parent share no ancestor, so the protection this rule was
      // written for is untouched.
      //
      // Identity (`eq`), never `contains`: `Definition.equals` is structural, so two distinct
      // same-named domains would compare equal and fake a shared ancestor.
      val outletDomains = maybeFromOutlet.map(domainChain).getOrElse(Seq.empty)
      val inletDomains = maybeToInlet.map(domainChain).getOrElse(Seq.empty)
      val sharesAncestorDomain = outletDomains.exists(o => inletDomains.exists(_ eq o))
      val crossDomain = (outletDom, inletDom) match
        case (Some(a), Some(b)) => !(a eq b) && !sharesAncestorDomain
        case _                  => false

      if crossDomain then
        messages.addError(
          connector.errorLoc,
          s"${connector.identify} connects UNRELATED domains (${outletDom.get.identify} and " +
            s"${inletDom.get.identify}) -- they share no ancestor domain; a connector between " +
            s"unrelated domains indicates a failure of domain analysis and is not allowed",
          suggestion =
            "Keep the connector within one domain, or place the two domains under a common parent " +
              "domain if they really are divisions of one whole. If they are genuinely unrelated, " +
              "model the communication with an adaptor and messaging rather than a direct stream " +
              "connector.",
          ruleId = Some(RuleId.CrossesDomains)
        )
      else if connectorInDomain then
        if sameContext then
          messages.addError(
            connector.errorLoc,
            s"${connector.identify} is defined at domain scope but both ends are within " +
              s"${outletCtx.get.identify}; the connector is over-scoped",
            suggestion =
              s"Move ${connector.identify} into ${outletCtx.get.identify}; a connector whose ends " +
                s"are in the same context belongs in that context, not the domain.",
            ruleId = Some(RuleId.DomainScopeUnnecessary)
          )
        else if crossContext then
          checkBoundaryEncapsulation(connector, maybeFromOutlet, maybeToInlet, outletCtx, inletCtx)
          if !connector.isPersistent then
            // CompletenessWarning (not a plain Warning) so AI/tooling can adapt: durability across a
            // context boundary can be required for model correctness, not merely a deployment concern.
            messages.addCompleteness(
              connector.errorLoc,
              s"${connector.identify} spans a context boundary but is not 'persistent'; durability " +
                s"across a context boundary can be required for model correctness, not merely a " +
                s"deployment concern",
              suggestion =
                s"Add the 'persistent' option to ${connector.identify} so data is not lost across " +
                  s"faults at the context boundary.",
              ruleId = Some(RuleId.BoundaryNotPersistent)
            )
          end if
        end if
      else // connector is at context scope
        if crossContext then
          messages.addError(
            connector.errorLoc,
            s"${connector.identify} connects across contexts (${outletCtx.get.identify} and " +
              s"${inletCtx.get.identify}) but is defined at context scope; it is under-scoped",
            suggestion =
              "Move the connector up to the domain that contains both contexts; a connector that " +
                "crosses contexts must be defined at domain scope.",
            ruleId = Some(RuleId.CrossesContexts)
          )
        else if sameContext && connector.isPersistent then
          // Point at the OPTION when it is still spelled that way, else at the connector itself.
          // This used to be `.find(...).get`, which was safe only while persistence could ONLY come
          // from an option; once it could also come from an intention the `.get` threw
          // NoSuchElementException and the whole streaming check died with a Severe.
          val where =
            connector.options.find(_.name == "persistent").map(_.loc).getOrElse(connector.errorLoc)
          messages.addWarning(
            where,
            s"Persistence on ${connector.identify} is not needed " +
              s"since both ends of the connector connect within the same context",
            suggestion =
              s"Remove 'persistent' from ${connector.identify}; both ends are in the same context.",
            ruleId = Some(RuleId.PersistenceNotNeeded)
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
  /** Counts a portlet by IDENTITY rather than by value.
    *
    * `Definition.equals` compares class, `id`, `loc`, `metadata` and fields — so two genuinely
    * DISTINCT ports that happen to be spelled the same are equal whenever their locations match.
    * That never bites on a freshly parsed tree, where every node carries a distinct source
    * location, which is exactly why it went unnoticed: it is `loc` that was doing the
    * distinguishing, by accident. On any tree built WITHOUT locations — one read back from JSON,
    * where every `loc` is `At.empty` — two ports like `APIProductEventSplit.FromEntity` and
    * `APIProductRepository.FromEntity`, same name and same type, collapse into ONE map key and the
    * single connector on each is reported as two connectors on one port.
    *
    * `resolvePath` returns the actual node from the tree, so reference identity is both correct and
    * available. The wrapper keeps `LinkedHashMap`'s insertion order, which is what makes the
    * emitted messages deterministic.
    */
  /** Identity-keyed wrapper. `private[validate]` rather than `private` so
    * `ValidationPass.checkTellReachability` reuses THIS one: a second copy would be a second
    * chance to key a graph on structural equality, which is the exact hazard the comment on
    * [[Node]] above describes.
    */
  private[validate] final class ByIdentity[T <: AnyRef](val value: T):
    override def hashCode: Int = System.identityHashCode(value)
    override def equals(that: Any): Boolean = that match
      case other: ByIdentity[?] => value eq other.value
      case _                    => false
  end ByIdentity

  private def checkPortletCardinality(): Unit = {
    val outletCounts = mutable.LinkedHashMap.empty[ByIdentity[Outlet], Int]
    val inletCounts = mutable.LinkedHashMap.empty[ByIdentity[Inlet], Int]

    connectors.filterNot(_.isEmpty).foreach { connector =>
      val connParents = symbols.parentsOf(connector)
      // The predefined terminators are exempt: any number of connectors may drain into
      // `BottomlessPit.hole` or draw from `ForeverEmpty.spout`.
      resolvePath[Outlet](connector.from.pathId, connParents).filterNot(isPredefined).foreach {
        outlet =>
          val key = ByIdentity(outlet)
          outletCounts.update(key, outletCounts.getOrElse(key, 0) + 1)
      }
      resolvePath[Inlet](connector.to.pathId, connParents).filterNot(isPredefined).foreach {
        inlet =>
          val key = ByIdentity(inlet)
          inletCounts.update(key, inletCounts.getOrElse(key, 0) + 1)
      }
    }

    outletCounts.foreach { case (key, count) =>
      val outlet = key.value
      if count > 1 then
        messages.addError(
          outlet.errorLoc,
          s"Outlet '${outlet.id.value}' is connected by $count connectors; exactly one is allowed " +
            "(model fan-out with multiple outlets)",
          suggestion =
            "Attach only one connector to this outlet; to fan out, declare additional outlets on the " +
              "processor (which makes it a split or router) and connect each separately.",
          ruleId = Some(RuleId.OutletCardinality)
        )
    }

    inletCounts.foreach { case (key, count) =>
      val inlet = key.value
      if count > 1 then
        messages.addError(
          inlet.errorLoc,
          s"Inlet '${inlet.id.value}' is connected by $count connectors; exactly one is allowed " +
            "(model fan-in with multiple inlets)",
          suggestion =
            "Attach only one connector to this inlet; to fan in, declare additional inlets on the " +
              "processor (which makes it a merge or router) and connect each separately.",
          ruleId = Some(RuleId.InletCardinality)
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
    * (a) each connector joins its `from` outlet and its `to` inlet; (b) each processor's own
    * inlets+outlets are joined (a processor's ports are part of one flow). Because connectors are
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

      // Edge kind (b): a processor's own inlets+outlets belong to the same pipeline. Every
      // Processor kind, not just Streamlet — an adaptor or entity mid-pipeline joins its ports
      // into the same component exactly as a streamlet does.
      processors.foreach { processor =>
        val portIdxs = (processor.inlets ++ processor.outlets).map(indexOf).filter(_ >= 0)
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
                "leave the rest un-marked so the code generator can fuse them into a single operator.",
            ruleId = Some(RuleId.AllPortletsAsync)
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
            s"Connect ${portlet.identify} with a connector, or remove it if it is unused.",
          ruleId = Some(RuleId.PortletUnconnected)
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
