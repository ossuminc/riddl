/*
 * Copyright 2019-2026 Ossum, Inc.
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
  trigger: OnMessageClause,
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
    entity.handlers.foreach { handler =>
      extractTransitions(handler, None, entity, transitions)
    }

    // Walk state-specific handlers (if states have handlers)
    // In RIDDL, handlers are at entity level, but we check if
    // handler names match state names as a convention
    // Also look for handlers defined within states if that
    // pattern exists in the model

    val initialState = states.headOption
    val statesWithOutgoing = transitions.map(_.fromState).collect {
      case Some(s) => s
    }.toSet ++ (
      // Entity-level transitions (fromState=None) mean any state
      // can transition, so all states have potential outgoing
      if transitions.exists(_.fromState.isEmpty) then states.toSet
      else scala.collection.immutable.Set.empty[State]
    )
    val terminalStates = states.filterNot(statesWithOutgoing.contains)

    EntityLifecycle(
      entity = entity,
      states = states,
      transitions = transitions.toSeq,
      initialState = initialState,
      terminalStates = terminalStates
    )
  }

  private def extractTransitions(
    handler: Handler,
    fromState: Option[State],
    entity: Entity,
    transitions: mutable.ListBuffer[StateTransition]
  ): Unit = {
    handler.clauses.foreach {
      case omc: OnMessageClause =>
        val finder = Finder(omc.contents)
        val morphs = finder.recursiveFindByType[MorphStatement]
        val becomes = finder.recursiveFindByType[BecomeStatement]

        morphs.foreach { morph =>
          val maybeState =
            refMap.definitionOf[State](morph.state, entity)
          maybeState.foreach { targetState =>
            transitions.addOne(
              StateTransition(
                fromState = fromState,
                toState = targetState,
                trigger = omc,
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
            refMap.definitionOf[Handler](become.handler, entity)
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
                  trigger = omc,
                  mechanism = become
                )
              )
            }
          }
        }
      case _ => ()
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
