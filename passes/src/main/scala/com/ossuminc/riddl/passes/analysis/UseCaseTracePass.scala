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
  * NOT rediscover states — and re-keys each [[StateTransition]] by its triggering message to build
  * a message-keyed transition function `δ(state, messageType) → { (nextState, guarded: Boolean) }`.
  * A transition is `guarded` when the morph/become mechanism is nested inside a `when`/`match`
  * within its on-clause rather than being a top-level statement.
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
  *     reachable from the entry state by applying the block's sibling deliveries in ANY order (a
  *     fixpoint reachability closure over the block's messages, kept tractable at O(states ×
  *     deliveries)). The entity's post-block state advances greedily in source order.
  *
  * Admissibility keys on whether the current state HANDLES the message (has an `on m` clause), not
  * merely on whether it transitions:
  *   - handled with a clear UNGUARDED transition → admissible, advance, SILENT;
  *   - handled with NO transition (self-loop) → admissible, stay, SILENT;
  *   - handled only via a GUARDED/conditional transition → [[Messages.CompletenessWarning]]
  *     mentioning the ambiguity, then advance optimistically to the guarded target;
  *   - NOT handled at all in the current state → [[Messages.CompletenessWarning]] (the genuine
  *     inadmissibility). Guarded/ambiguous cases still warn (epics exist for completeness
  *     checking), but a clearly-admissible self-loop never does.
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
    outputs
      .outputOf[EntityLifecycleOutput](EntityLifecyclePass.name)
      .getOrElse(EntityLifecycleOutput())

  private def lookupOne[T <: Definition: ClassTag](pid: PathIdentifier): Option[T] =
    if pid.value.isEmpty then None
    else symTab.lookup[T](pid.value.reverse).headOption

  // ============================================================
  // FSM projection (Step 1) — reuse EntityLifecyclePass's states/transitions/initialState
  // ============================================================

  /** A message-keyed transition target: the next state and whether the transition is guarded. */
  private type Target = (State, Boolean)

  /** The projected state machine of an entity: its initial state, the message-keyed transition
    * function δ, and the set of messages HANDLED in each state (whether or not they transition — a
    * handled-without-transition message is an admissible self-loop). Only entities with a lifecycle
    * (≥ 2 states) appear here.
    */
  private case class Projection(
    q0: State,
    delta: Map[(State, Type), Seq[Target]],
    handled: Map[State, ISet[Type]]
  )

  /** Admissibility outcome of delivering a message to an entity in a given state. */
  private enum Adm:
    case NotHandled // no `on M` clause active in this state → inadmissible
    case SelfLoop // handled with no state change → admissible, stay
    case Advance(next: State) // handled with a clear unguarded transition → advance
    case GuardedAdvance(next: State) // handled only via a guarded transition → ambiguous

  /** Classify a delivery of message `m` to an entity while it is in state `s`. Admissibility keys
    * on whether `s` HANDLES `m` (its state/entity handlers have an `on m` clause); δ is consulted
    * only to pick the resulting state. Handled with no transition ⇒ admissible self-loop.
    */
  private def classify(proj: Projection, s: State, m: Type): Adm =
    if !proj.handled.getOrElse(s, ISet.empty[Type]).contains(m) then Adm.NotHandled
    else
      proj.delta.get((s, m)) match
        case None | Some(Nil) => Adm.SelfLoop
        case Some(targets) =>
          targets.find(!_._2) match
            case Some((next, _)) => Adm.Advance(next)
            case None            => Adm.GuardedAdvance(targets.head._1)

  /** The message Types accepted by any `on <message>` clause among the given handlers. */
  private def handledTypesOf(handlers: Seq[Handler]): ISet[Type] =
    handlers
      .flatMap(_.clauses)
      .collect { case omc: OnMessageLikeClause => omc }
      .flatMap(omc => lookupOne[Type](omc.msg.pathId))
      .toSet

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
          msgType <- triggerMessageType(t.trigger)
        yield (fromState, msgType) -> (t.toState, isGuarded(t))
      }
      val delta = entries.groupBy(_._1).map { case (k, vs) => k -> vs.map(_._2) }
      // Messages handleable in each state = the state's own handlers plus the entity-level
      // handlers (which apply in every state, mirroring EntityLifecyclePass's fromState=None
      // expansion). Reuses the discovered states — no re-discovery of state structure.
      val entityHandled = handledTypesOf(entity.handlers)
      val handled = lc.states.map(s => s -> (handledTypesOf(s.handlers) ++ entityHandled)).toMap
      entity -> Projection(q0, delta, handled)
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
      entity <- lookupOne[Entity](s.to.pathId)
      proj <- projections.get(entity)
      msgType <- lookupOne[Type](s.message.pathId)
    yield (entity, proj, msgType)

  /** Sequential delivery: check admissibility in the current state and thread the resulting state.
    * Handled-with-no-transition is an admissible self-loop (silent, state unchanged).
    */
  private def checkSequential(
    uc: UseCase,
    s: SendMessageInteraction,
    state: mutable.Map[Entity, State]
  ): Unit =
    resolveDelivery(s).foreach { case (entity, proj, msgType) =>
      val current = state.getOrElseUpdate(entity, proj.q0)
      classify(proj, current, msgType) match
        case Adm.NotHandled    => warnNotHandled(uc, s.loc, msgType, entity, current)
        case Adm.SelfLoop      => () // admissible, no state change
        case Adm.Advance(next) => state.update(entity, next) // clear transition, silent
        case Adm.GuardedAdvance(next) =>
          warnGuarded(uc, s.loc, msgType, entity, current)
          state.update(entity, next) // advance optimistically to the guarded target
    }

  /** Parallel block: steps are unordered. Admissibility uses an any-order reachability closure of
    * states reachable from each entity's entry state by applying the block's sibling deliveries;
    * the post-block state advances greedily in source order (one representative interleaving).
    */
  private def checkParallel(
    uc: UseCase,
    contents: Seq[RiddlValue],
    state: mutable.Map[Entity, State]
  ): Unit =
    val deliveries: Seq[(Entity, Projection, Type, SendMessageInteraction)] =
      collectDeliveries(contents).flatMap(s =>
        resolveDelivery(s).map { case (e, p, m) => (e, p, m, s) }
      )
    deliveries.groupBy(_._1).foreach { case (entity, forEntity) =>
      val proj = forEntity.head._2
      val entry = state.getOrElseUpdate(entity, proj.q0)
      val msgTypes = forEntity.map(_._3).distinct
      val reachable = reachableClosure(entry, msgTypes, proj.delta)
      forEntity.foreach { case (_, _, msgType, s) =>
        // Admissible if SOME reachable state handles the message. A clear (self-loop or unguarded)
        // handling in any reachable state ⇒ silent; only-guarded handling ⇒ ambiguity warning;
        // handled in no reachable state ⇒ genuine inadmissibility.
        val outcomes = reachable.toSeq.map(st => classify(proj, st, msgType))
        val isClear = outcomes.exists {
          case Adm.SelfLoop | (_: Adm.Advance) => true
          case _                               => false
        }
        val isGuarded = outcomes.exists {
          case _: Adm.GuardedAdvance => true
          case _                     => false
        }
        if isClear then ()
        else if isGuarded then warnGuarded(uc, s.loc, msgType, entity, entry)
        else warnNotHandled(uc, s.loc, msgType, entity, entry)
      }
      // Post-block state: apply the block's deliveries greedily in source order.
      var current = entry
      forEntity.foreach { case (_, _, msgType, _) =>
        classify(proj, current, msgType) match
          case Adm.Advance(next)        => current = next
          case Adm.GuardedAdvance(next) => current = next
          case _                        => () // self-loop / not-handled: no state change
      }
      state.update(entity, current)
    }

  /** Deliveries directly reachable in a container subtree, skipping Optional (skippable) subtrees.
    */
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

  private def warnNotHandled(uc: UseCase, loc: At, m: Type, e: Entity, s: State): Unit =
    messages.addCompleteness(
      loc,
      s"use-case '${uc.id.value}' delivers '${m.id.value}' to '${e.id.value}' but state " +
        s"'${s.id.value}' does not handle it — the scenario is not admissible",
      suggestion =
        s"Reorder the interaction steps so '${e.id.value}' reaches a state that handles " +
          s"'${m.id.value}', or add an 'on ${m.id.value}' clause to state '${s.id.value}'."
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
