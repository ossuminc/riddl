/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Messages, toSeq}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.ResolutionPass
import com.ossuminc.riddl.passes.symbols.SymbolsPass
import com.ossuminc.riddl.passes.validate.ValidationPass
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.immutable.Set as ISet
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.scalajs.js.annotation.*

/** Output of the [[UseCaseTracePass]] (A36 Level 2). Carries the completeness warnings emitted for
  * use-case interaction sequences that are not admissible traces through the projected state
  * machines of the entities they drive.
  *
  * @param root
  *   The root of the model
  * @param messages
  *   The completeness warnings produced by the pass
  */
@JSExportTopLevel("UseCaseTraceOutput")
case class UseCaseTraceOutput(
  root: PassRoot = Root.empty,
  messages: Messages.Messages = Messages.empty
) extends PassOutput

@JSExportTopLevel("UseCaseTracePass$")
object UseCaseTracePass extends PassInfo[PassOptions] {
  val name: String = "UseCaseTrace"
  def creator(
    options: PassOptions = PassOptions.empty
  )(using PlatformContext): PassCreator = { (in: PassInput, out: PassesOutput) =>
    UseCaseTracePass(in, out)
  }
}

/** A36 Level 2 — trace admissibility via FSM projection.
  *
  * For each [[UseCase]], this pass verifies that the ordered interaction sequence is an *admissible
  * trace* through the projected state machines of the entities it drives. It reuses
  * [[EntityLifecyclePass]]'s per-entity projection (states, transitions, initial state) — it does
  * NOT rediscover states — and re-keys each [[StateTransition]] by its triggering message to build a
  * message-keyed transition function
  * `δ(state, messageType) → { (nextState, guarded: Boolean) }`. A transition is `guarded` when the
  * morph/become mechanism is nested inside a `when`/`match` within its on-clause rather than being a
  * top-level statement.
  *
  * Only [[SendMessageInteraction]] steps deliver a message to an entity FSM; every other step kind
  * (Show/Take/Focus/URL/Self/Refusal/Vague/Arbitrary) is transparent to FSM state. An entity with 0
  * or 1 states has no sequencing and therefore never produces a Level-2 warning (such entities have
  * no lifecycle in [[EntityLifecyclePass]]).
  *
  * Container semantics (F1 — HONOR containers):
  *   - Sequential (and top-level order): steps in order, threading each driven entity's state.
  *   - Optional: skippable — its steps are NOT checked for admissibility and do NOT change state
  *     (treated as "may or may not occur").
  *   - Parallel: unordered — a delivery is admissible if its message is handleable in some state
  *     reachable from the entry state by applying the block's sibling deliveries in ANY order
  *     (a fixpoint reachability closure over the block's messages, kept tractable at
  *     O(states × deliveries)). The entity's post-block state advances greedily in source order.
  *
  * Strictness (F5 — favor emitting warnings; epics exist for completeness checking):
  *   - a clear UNGUARDED transition exists → admissible, advance, SILENT;
  *   - NO transition for the message in the current state → [[Messages.CompletenessWarning]];
  *   - only a GUARDED/conditional transition exists → [[Messages.CompletenessWarning]] mentioning
  *     the ambiguity, then advance optimistically to the guarded target.
  *
  * All output is advisory [[Messages.CompletenessWarning]] (gated, like its siblings, by
  * `showCompletenessWarnings` at output time). No AST/parser/BAST/JSON surface is touched.
  */
@JSExportTopLevel("UseCaseTracePass")
case class UseCaseTracePass(
  input: PassInput,
  outputs: PassesOutput
)(using PlatformContext)
    extends Pass(input, outputs) {

  requires(SymbolsPass)
  requires(ResolutionPass)
  requires(ValidationPass)
  requires(EntityLifecyclePass)

  override def name: String = UseCaseTracePass.name

  private lazy val symTab = outputs.symbols

  private lazy val lifecycleOutput: EntityLifecycleOutput =
    outputs.outputOf[EntityLifecycleOutput](EntityLifecyclePass.name).getOrElse(EntityLifecycleOutput())

  private def lookupOne[T <: Definition: ClassTag](pid: PathIdentifier): Option[T] =
    if pid.value.isEmpty then None
    else symTab.lookup[T](pid.value.reverse).headOption

  // ============================================================
  // FSM projection (Step 1) — reuse EntityLifecyclePass's states/transitions/initialState
  // ============================================================

  /** A message-keyed transition target: the next state and whether the transition is guarded. */
  private type Target = (State, Boolean)

  /** The projected state machine of an entity: its initial state and message-keyed transition
    * function δ. Only entities with a lifecycle (≥ 2 states) appear here.
    */
  private case class Projection(q0: State, delta: Map[(State, Type), Seq[Target]])

  /** Resolve the triggering message Type of an on-clause; None for init/other/lifecycle clauses. */
  private def triggerMessageType(oc: OnClause): Option[Type] =
    oc match
      case omc: OnMessageLikeClause => lookupOne[Type](omc.msg.pathId)
      case _                        => None

  /** A transition is guarded when its mechanism (the morph/become statement) is NOT a top-level
    * statement of the on-clause body — i.e. it is nested inside a `when`/`match` (or other
    * conditional) rather than executed unconditionally.
    */
  private def isGuarded(t: StateTransition): Boolean =
    !t.trigger.contents.toSeq.exists(_ eq t.mechanism)

  private lazy val projections: Map[Entity, Projection] =
    lifecycleOutput.lifecycles.map { case (entity, lc) =>
      val q0 = lc.initialState.getOrElse(lc.states.head)
      val entries: Seq[((State, Type), Target)] = lc.transitions.flatMap { t =>
        for
          fromState <- t.fromState
          msgType   <- triggerMessageType(t.trigger)
        yield (fromState, msgType) -> (t.toState, isGuarded(t))
      }
      val delta = entries.groupBy(_._1).map { case (k, vs) => k -> vs.map(_._2) }
      entity -> Projection(q0, delta)
    }

  // ============================================================
  // Trace walk (Steps 2 & 3)
  // ============================================================

  protected def process(
    definition: RiddlValue,
    parents: ParentStack
  ): Unit =
    definition match
      case uc: UseCase => walkSteps(uc, uc.contents.toSeq, mutable.Map.empty[Entity, State])
      case _           => () // interaction steps are reached from their enclosing UseCase
    end match

  /** Walk an ordered step sequence, honoring containers and threading per-entity FSM state. */
  private def walkSteps(
    uc: UseCase,
    contents: Seq[RiddlValue],
    state: mutable.Map[Entity, State]
  ): Unit =
    contents.foreach {
      case _: OptionalInteractions   => () // skippable: no admissibility check, no state change
      case p: ParallelInteractions   => checkParallel(uc, p.contents.toSeq, state)
      case s: SequentialInteractions => walkSteps(uc, s.contents.toSeq, state)
      case s: SendMessageInteraction => checkSequential(uc, s, state)
      case _                         => () // non-delivering steps are transparent to FSM state
    }

  /** Resolve a send step to (entity-with-FSM, delivered message Type). Empty when the receiver is
    * not an entity, has no lifecycle (0/1 states), or the message does not resolve.
    */
  private def resolveDelivery(s: SendMessageInteraction): Option[(Entity, Projection, Type)] =
    for
      entity  <- lookupOne[Entity](s.to.pathId)
      proj    <- projections.get(entity)
      msgType <- lookupOne[Type](s.message.pathId)
    yield (entity, proj, msgType)

  /** Sequential delivery: consult δ(currentState, message) and thread the resulting state. */
  private def checkSequential(
    uc: UseCase,
    s: SendMessageInteraction,
    state: mutable.Map[Entity, State]
  ): Unit =
    resolveDelivery(s).foreach { case (entity, proj, msgType) =>
      val current = state.getOrElseUpdate(entity, proj.q0)
      proj.delta.get((current, msgType)) match
        case None | Some(Nil) =>
          warnNoTransition(uc, s.loc, msgType, entity, current)
        case Some(targets) =>
          targets.find(!_._2) match
            case Some((next, _)) => state.update(entity, next) // clear unguarded transition, silent
            case None =>
              warnGuarded(uc, s.loc, msgType, entity, current)
              state.update(entity, targets.head._1) // advance optimistically to guarded target
    }

  /** Parallel block: steps are unordered. Admissibility uses an any-order reachability closure of
    * states reachable from each entity's entry state by applying the block's sibling deliveries; the
    * post-block state advances greedily in source order (one representative interleaving).
    */
  private def checkParallel(
    uc: UseCase,
    contents: Seq[RiddlValue],
    state: mutable.Map[Entity, State]
  ): Unit =
    val deliveries: Seq[(Entity, Projection, Type, SendMessageInteraction)] =
      collectDeliveries(contents).flatMap(s => resolveDelivery(s).map { case (e, p, m) => (e, p, m, s) })
    deliveries.groupBy(_._1).foreach { case (entity, forEntity) =>
      val proj = forEntity.head._2
      val entry = state.getOrElseUpdate(entity, proj.q0)
      val msgTypes = forEntity.map(_._3).distinct
      val reachable = reachableClosure(entry, msgTypes, proj.delta)
      forEntity.foreach { case (_, _, msgType, s) =>
        val handling = reachable.toSeq.flatMap(st => proj.delta.getOrElse((st, msgType), Nil))
        if handling.isEmpty then warnNoTransition(uc, s.loc, msgType, entity, entry)
        else if handling.exists(!_._2) then () // some ordering admits it via an unguarded transition
        else warnGuarded(uc, s.loc, msgType, entity, entry)
      }
      // Post-block state: apply the block's deliveries greedily in source order.
      var current = entry
      forEntity.foreach { case (_, _, msgType, _) =>
        proj.delta.get((current, msgType)).flatMap(_.headOption).foreach { case (next, _) =>
          current = next
        }
      }
      state.update(entity, current)
    }

  /** Deliveries directly reachable in a container subtree, skipping Optional (skippable) subtrees. */
  private def collectDeliveries(contents: Seq[RiddlValue]): Seq[SendMessageInteraction] =
    contents.flatMap {
      case s: SendMessageInteraction => Seq(s)
      case _: OptionalInteractions   => Seq.empty
      case ic: InteractionContainer  => collectDeliveries(ic.contents.toSeq)
      case _                         => Seq.empty
    }

  /** States reachable from `s0` by applying any sequence of the given messages under δ. */
  private def reachableClosure(
    s0: State,
    msgTypes: Seq[Type],
    delta: Map[(State, Type), Seq[Target]]
  ): ISet[State] =
    val seen = mutable.HashSet(s0)
    val queue = mutable.Queue(s0)
    while queue.nonEmpty do
      val cur = queue.dequeue()
      msgTypes.foreach { m =>
        delta.getOrElse((cur, m), Nil).foreach { case (next, _) =>
          if seen.add(next) then queue.enqueue(next)
        }
      }
    end while
    seen.toSet

  private def warnNoTransition(uc: UseCase, loc: At, m: Type, e: Entity, s: State): Unit =
    messages.addCompleteness(
      loc,
      s"use-case '${uc.id.value}' delivers '${m.id.value}' to '${e.id.value}' but its state " +
        s"machine has no transition for it in state '${s.id.value}' — the scenario is not admissible",
      suggestion =
        s"Reorder the interaction steps so '${e.id.value}' reaches a state that handles " +
          s"'${m.id.value}', or add an 'on ${m.id.value}' transition from state '${s.id.value}'."
    )

  private def warnGuarded(uc: UseCase, loc: At, m: Type, e: Entity, s: State): Unit =
    messages.addCompleteness(
      loc,
      s"use-case '${uc.id.value}' delivers '${m.id.value}' to '${e.id.value}' in state " +
        s"'${s.id.value}' is only conditionally admissible (the transition is guarded); the " +
        "scenario may be inadmissible",
      suggestion =
        s"Ensure the guard on the '${m.id.value}' transition of '${e.id.value}' holds when this " +
          "step runs, or make the transition unconditional."
    )

  override def result(root: PassRoot): UseCaseTraceOutput =
    UseCaseTraceOutput(root = root, messages = messages.toMessages)
}
