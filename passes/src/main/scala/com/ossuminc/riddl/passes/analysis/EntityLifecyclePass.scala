/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, Messages}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.ossuminc.riddl.passes.validate.ValidationPass
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable
import scala.scalajs.js.annotation.*

/** A transition between entity states
  *
  * @param fromState
  *   The source state (None means any state / entity-level handler)
  * @param toState
  *   The target state
  * @param trigger
  *   The on-clause that triggers the transition
  * @param mechanism
  *   The morph or become statement causing the transition
  */
@JSExportTopLevel("StateTransition")
case class StateTransition(
  fromState: Option[State],
  toState: State,
  trigger: OnClause,
  mechanism: Statement
)

/** The lifecycle (state machine) extracted from an entity
  *
  * @param entity
  *   The entity this lifecycle describes
  * @param states
  *   All states defined on the entity
  * @param transitions
  *   All state transitions discovered in handlers
  * @param initialState
  *   The first declared state (conventional initial state)
  * @param terminalStates
  *   States with no outgoing transitions
  */
@JSExportTopLevel("EntityLifecycle")
case class EntityLifecycle(
  entity: Entity,
  states: Seq[State],
  transitions: Seq[StateTransition],
  initialState: Option[State],
  terminalStates: Seq[State]
)

/** Output of the EntityLifecyclePass
  *
  * @param root
  *   The root of the model
  * @param messages
  *   Any messages generated during analysis
  * @param lifecycles
  *   Map from entity to its extracted lifecycle
  */
@JSExportTopLevel("EntityLifecycleOutput")
case class EntityLifecycleOutput(
  root: PassRoot = Root.empty,
  messages: Messages.Messages = Messages.empty,
  lifecycles: Map[Entity, EntityLifecycle] = Map.empty
) extends PassOutput

@JSExportTopLevel("EntityLifecyclePass$")
object EntityLifecyclePass extends PassInfo[PassOptions] {
  val name: String = "EntityLifecycle"
  def creator(
    options: PassOptions = PassOptions.empty
  )(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) =>
      EntityLifecyclePass(in, out)
  }
}

/** A pass that extracts explicit state machines from entities that
  * have multiple states and morph/become statements. This provides
  * pre-computed lifecycle data for simulation and code generation.
  */
@JSExportTopLevel("EntityLifecyclePass")
case class EntityLifecyclePass(
  input: PassInput,
  outputs: PassesOutput
)(using PlatformContext)
    extends Pass(input, outputs) {

  requires(SymbolsPass)
  requires(ResolutionPass)
  requires(ValidationPass)

  override def name: String = EntityLifecyclePass.name

  private lazy val refMap = outputs.refMap
  private lazy val symTab = outputs.symbols

  private val collectedLifecycles: mutable.HashMap[Entity, EntityLifecycle] =
    mutable.HashMap.empty

  protected def process(
    definition: RiddlValue,
    parents: ParentStack
  ): Unit = {
    definition match
      case entity: Entity if entity.states.sizeIs >= 2 =>
        val lifecycle = extractLifecycle(entity)
        collectedLifecycles.put(entity, lifecycle)
      case _ => ()
  }

  private def extractLifecycle(entity: Entity): EntityLifecycle = {
    val states = entity.states.toSeq
    val transitions = mutable.ListBuffer.empty[StateTransition]

    // Walk entity-level handlers (fromState = None means any state)
    val entityLevelTransitions = mutable.ListBuffer.empty[StateTransition]
    entity.handlers.foreach { handler =>
      extractTransitions(handler, None, entity, entityLevelTransitions)
    }

    // Expand entity-level transitions (fromState=None) to one per state
    entityLevelTransitions.foreach { t =>
      states.foreach { state =>
        transitions.addOne(t.copy(fromState = Some(state)))
      }
    }

    // Walk state-level handlers
    states.foreach { state =>
      state.handlers.foreach { handler =>
        extractTransitions(handler, Some(state), entity, transitions)
      }
    }

    // Detect initial state from on init + set state
    val initialState = detectInitialState(entity, states)

    val statesWithOutgoing = transitions.map(_.fromState).collect {
      case Some(s) => s
    }.toSet
    val terminalStates = states.filterNot(statesWithOutgoing.contains)

    EntityLifecycle(
      entity = entity,
      states = states,
      transitions = transitions.toSeq,
      initialState = initialState,
      terminalStates = terminalStates
    )
  }

  /** Resolve a StateRef to a State, trying refMap first then
    * falling back to matching by name within the entity.
    */
  private def resolveState(
    stateRef: StateRef,
    entity: Entity
  ): Option[State] = {
    refMap.definitionOf[State](stateRef.pathId).orElse {
      // Fallback: match by last path component against entity states
      val stateName = stateRef.pathId.value.lastOption.getOrElse("")
      entity.states.find(_.id.value == stateName)
    }
  }

  /** Detect initial state by searching for `set state X` inside
    * `on init` clauses. Falls back to first declared state.
    */
  private def detectInitialState(
    entity: Entity,
    states: Seq[State]
  ): Option[State] = {
    // Collect all handlers from entity-level and state-level
    val allHandlers = entity.handlers ++ states.flatMap(_.handlers)

    // Search for OnInitializationClause containing SetStatement with StateRef
    val initStateOpt = allHandlers.iterator.flatMap(_.clauses).collectFirst {
      case oic: OnInitializationClause =>
        val finder = Finder(oic.contents)
        val sets = finder.recursiveFindByType[SetStatement]
        sets.collectFirst {
          case ss: SetStatement if ss.field.isInstanceOf[StateRef] =>
            val stateRef = ss.field.asInstanceOf[StateRef]
            resolveState(stateRef, entity)
        }.flatten
    }.flatten

    initStateOpt.orElse(states.headOption)
  }

  private def extractTransitions(
    handler: Handler,
    fromState: Option[State],
    entity: Entity,
    transitions: mutable.ListBuffer[StateTransition]
  ): Unit = {
    handler.clauses.foreach { oc =>
      val finder = Finder(oc.contents)
      val morphs = finder.recursiveFindByType[MorphStatement]
      val becomes = finder.recursiveFindByType[BecomeStatement]

      morphs.foreach { morph =>
        val maybeState = resolveState(morph.state, entity)
        maybeState.foreach { targetState =>
          transitions.addOne(
            StateTransition(
              fromState = fromState,
              toState = targetState,
              trigger = oc,
              mechanism = morph
            )
          )
        }
      }

      becomes.foreach { become =>
        // become changes the handler, which implies a state
        // transition in FSM semantics. Try to find the state
        // associated with the target handler.
        val maybeHandler =
          refMap.definitionOf[Handler](become.handler.pathId)
        maybeHandler.foreach { targetHandler =>
          // See if we can associate this handler with a state
          // by name convention or containment
          entity.states.find { state =>
            state.id.value == targetHandler.id.value ||
            state.id.value + "Handler" ==
              targetHandler.id.value
          }.foreach { targetState =>
            transitions.addOne(
              StateTransition(
                fromState = fromState,
                toState = targetState,
                trigger = oc,
                mechanism = become
              )
            )
          }
        }
      }
    }
  }

  override def result(root: PassRoot): EntityLifecycleOutput = {
    EntityLifecycleOutput(
      root = root,
      messages = messages.toMessages,
      lifecycles = collectedLifecycles.toMap
    )
  }
}
