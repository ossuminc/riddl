package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*

import scala.annotation.unused

object Folding {

  trait State[S <: State[?]] {
    def step(f: S => S): S
  }

  type SimpleDispatch[S] = (Container, Definition, S) => S

  def foldEachDefinition[S](
    parent: Container,
    container: Container,
    state: S
  )(f: SimpleDispatch[S]
  ): S = {
    var result = state
    container match {
      case root: RootContainer => root.contents.foldLeft(result) { (next, container) =>
          foldEachDefinition[S](root, container, next)(f)
        }
      case domain: Domain =>
        result = f(parent, domain, result)
        result = domain.types.foldLeft(result) { (next, ty) => f(domain, ty, next) }
        result = domain.topics.foldLeft(result) { (next, topic) =>
          foldEachDefinition[S](domain, topic, next)(f)
        }
        result = domain.contexts.foldLeft(result) { (next, context) =>
          foldEachDefinition[S](domain, context, next)(f)
        }
        domain.interactions.foldLeft(result) { (next, interaction) =>
          foldEachDefinition[S](domain, interaction, next)(f)
        }
      case context: Context =>
        result = f(parent, context, result)
        result = context.types.foldLeft(result) { (next, ty) => f(context, ty, next) }
        result = context.adaptors.foldLeft(result) { (next, adaptor) => f(context, adaptor, next) }
        result = context.entities.foldLeft(result) { (next, entity) =>
          foldEachDefinition[S](context, entity, next)(f)
        }
        context.interactions.foldLeft(result) { (next, interaction) =>
          foldEachDefinition[S](context, interaction, next)(f)
        }
      case entity: Entity =>
        result = f(parent, entity, result)
        val reducables =
          (entity.types.iterator ++ entity.handlers ++ entity.functions ++ entity.invariants).toList
        result = reducables.foldLeft(result) { (next, r) => f(entity, r, next) }
        val foldables = (entity.features.iterator ++ entity.states).toList
        foldables.foldLeft(result) { (next, foldable) =>
          foldEachDefinition[S](entity, foldable, next)(f)
        }
      case plant: Plant =>
        result = f(parent, plant, result)
        plant.contents.foldLeft(result) { (next, item) => f(plant, item, next) }
      case saga: Saga =>
        result = f(parent, saga, result)
        saga.contents.foldLeft(result) { (next, sagaAction) => f(saga, sagaAction, next) }
      case interaction: Interaction =>
        result = f(parent, interaction, result)
        interaction.contents.foldLeft(result) { (next, action) => f(interaction, action, next) }
      case feature: Feature =>
        result = f(parent, feature, result)
        feature.contents.foldLeft(result) { (next, example) => f(feature, example, next) }
      case adaptor: Adaptor => f(parent, adaptor, result)
      case topic: Topic =>
        result = f(parent, topic, result)
        val foldables: List[MessageDefinition] =
          (topic.commands.iterator ++ topic.events ++ topic.queries ++ topic.results).toList
        foldables.foldLeft(result) { (next, message) =>
          foldEachDefinition(topic, message, next)(f)
        }
      case message: MessageDefinition =>
        result = f(parent, message, result)
        message.contents.foldLeft(result) { (next, field) => f(message, field, next) }
      case st: AST.State =>
        result = f(parent, st, result)
        st.typeEx match {
          case agg: Aggregation => agg.fields.foldLeft(result) { (next, field) =>
              f(st, field, next)
            }
          case _ => state
        }
    }
  }

  trait Folding[S <: State[S]] {

    // noinspection ScalaStyle

    /** Container Traversal This foldLeft allows the hierarchy of containers to be navigated
      */
    final def foldLeft(
      parent: Container,
      container: Container,
      initState: S
    ): S = {
      container match {
        case root: RootContainer => root.contents.foldLeft(initState) { case (s, content) =>
            foldLeft(root, content, s)
          }
        case domain: Domain => openDomain(initState, parent, domain).step { state =>
            domain.types.foldLeft(state) { (next, ty) => doType(next, domain, ty) }
          }.step { state =>
            domain.topics.foldLeft(state) { (next, topic) => foldLeft(domain, topic, next) }
          }.step { state =>
            domain.contexts.foldLeft(state) { (next, context) => foldLeft(domain, context, next) }
          }.step { state =>
            domain.interactions.foldLeft(state) { (next, interaction) =>
              foldLeft(domain, interaction, next)
            }
          }.step { state => closeDomain(state, parent, domain) }
        case context: Context => openContext(initState, parent, context).step { state =>
            context.types.foldLeft(state) { (next, ty) => doType(next, context, ty) }
          }.step { state =>
            context.adaptors.foldLeft(state) { (next, adaptor) => foldLeft(context, adaptor, next) }
          }.step { state =>
            context.entities.foldLeft(state) { (next, entity) => foldLeft(context, entity, next) }
          }.step { state =>
            context.interactions.foldLeft(state) { (next, in) => foldLeft(context, in, next) }
          }.step { state => closeContext(state, parent, context) }
        case entity: Entity => openEntity(initState, parent, entity).step { state =>
            entity.types.foldLeft(state) { (next, typ) => doType(next, entity, typ) }
          }.step { state =>
            entity.handlers.foldLeft(state) { (next, handler) => doHandler(next, entity, handler) }
          }.step { state =>
            entity.features.foldLeft(state) { (next, feature) => foldLeft(entity, feature, next) }
          }.step { state =>
            entity.functions.foldLeft(state) { (next, function) =>
              doFunction(next, entity, function)
            }
          }.step { state =>
            entity.invariants.foldLeft(state) { (next, invariant) =>
              doInvariant(next, entity, invariant)
            }
        }.step { state =>
          entity.states.foldLeft(state) { (next, s) => foldLeft(entity, s, next) }
        }.step { state => closeEntity(state, parent, entity) }
        case plant: Plant => openPlant(initState, parent, plant).step { state =>
          plant.pipes.foldLeft(state) { (next, pipe) => doPipe(next, plant, pipe) }
        }.step { state =>
          plant.processors.foldLeft(state) { (next, proc) => doProcessor(next, plant, proc) }
        }.step { state =>
          plant.joints.foldLeft(state) { (next, joint) => doJoint(next, plant, joint) }
        }.step { state => closePlant(state, parent, plant) }
        case saga: Saga => openSaga(initState, parent, saga).step { state =>
          saga.contents.foldLeft(state) { (next, action) => doSagaAction(next, saga, action) }
        }.step { state => closeSaga(state, parent, saga) }
        case interaction: Interaction => openInteraction(initState, parent, interaction)
          .step { state =>
            interaction.actions.foldLeft(state) { (next, action) =>
              doAction(next, interaction, action)
            }
          }.step { state => closeInteraction(state, parent, interaction) }
        case feature: Feature => openFeature(initState, parent, feature).step { state =>
          feature.examples.foldLeft(state) { (next, example) =>
            doExample(next, feature, example)
          }
          }
        case adaptor: Adaptor => openAdaptor(initState, parent, adaptor).step { state =>
            adaptor.adaptations.foldLeft(state) { (s, a) => doAdaptation(s, adaptor, a) }
            closeAdaptor(state, parent, adaptor)
          }
        case topic: Topic => openTopic(initState, parent, topic).step { state =>
            val foldables: List[MessageDefinition] =
              (topic.commands.iterator ++ topic.events ++ topic.queries ++ topic.results).toList
            foldables.foldLeft(state) { (next, message) => foldLeft(topic, message, next) }
          }.step { state => closeTopic(state, parent, topic) }
        case message: MessageDefinition => openMessage(initState, parent, message).step { state =>
            message.typ match {
              case a: Aggregation => a.fields.foldLeft(state) { (next, field) =>
                  doField(next, message, field)
                }
              case _ => state
            }
          }.step { state => closeMessage(state, parent, message) }

        case st: AST.State => openState(initState, parent, st).step { state =>
            st.typeEx match {
              case agg: Aggregation => agg.fields.foldLeft(state) { (next, field) =>
                  doField(next, st, field)
                }
              case _ => state
            }
          }
      }
    }

    def openDomain(
      state: S,
      @unused
      container: Container,
      @unused
      domain: Domain
    ): S = { state }

    def closeDomain(
      state: S,
      @unused
      container: Container,
      @unused
      domain: Domain
    ): S = { state }

    def openContext(
      state: S,
      @unused
      container: Container,
      @unused
      context: Context
    ): S = { state }

    def closeContext(
      state: S,
      @unused
      container: Container,
      @unused
      context: Context
    ): S = { state }

    def openEntity(
      state: S,
      @unused
      container: Container,
      @unused
      entity: Entity
    ): S = { state }

    def closeEntity(
      state: S,
      @unused
      container: Container,
      @unused
      entity: Entity
    ): S = { state }

    def openPlant(
      state: S,
      @unused
      container: Container,
      @unused
      plant: Plant
    ): S = { state }
    def closePlant(
      state: S,
      @unused
      container: Container,
      @unused
      plant: Plant
    ): S = { state }

    def openState(
      state: S,
      @unused
      container: Container,
      @unused
      s: AST.State
    ): S = { state }

    def closeState(
      state: S,
      @unused
      container: Container,
      @unused
      s: AST.State
    ): S = { state }

    def openTopic(
      state: S,
      @unused
      container: Container,
      @unused
      topic: Topic
    ): S = {state}

    def closeTopic(
      state: S,
      @unused
      container: Container,
      @unused
      channel: Topic
    ): S = {state}

    def openSaga(
      state: S,
      @unused
      container: Container,
      @unused
      saga: Saga
    ): S = {state}

    def closeSaga(
      state: S,
      @unused
      container: Container,
      @unused
      saga: Saga
    ): S = {state}

    def openInteraction(
      state: S,
      @unused
      container: Container,
      @unused
      interaction: Interaction
    ): S = {state}

    def closeInteraction(
      state: S,
      @unused
      container: Container,
      @unused
      interaction: Interaction
    ): S = { state }

    def openFeature(
      state: S,
      @unused
      container: Container,
      @unused
      feature: Feature
    ): S = { state }

    def closeFeature(
      state: S,
      @unused
      container: Container,
      @unused
      feature: Feature
    ): S = { state }

    def openAdaptor(
      state: S,
      @unused
      container: Container,
      @unused
      adaptor: Adaptor
    ): S = { state }

    def closeAdaptor(
      state: S,
      @unused
      container: Container,
      @unused
      adaptor: Adaptor
    ): S = { state }

    def doType(
      state: S,
      @unused
      container: Container,
      @unused
      typ: Type
    ): S = { state }

    def openMessage(
      state: S,
      container: Container,
      message: MessageDefinition
    ): S = {
      message match {
        case e: Event   => openEvent(state, container, e)
        case c: Command => openCommand(state, container, c)
        case q: Query   => openQuery(state, container, q)
        case r: Result  => openResult(state, container, r)
      }
    }

    def openCommand(
      state: S,
      @unused
      container: Container,
      @unused
      command: Command
    ): S = { state }

    def openEvent(
      state: S,
      @unused
      container: Container,
      @unused
      event: Event
    ): S = { state }

    def openQuery(
      state: S,
      @unused
      container: Container,
      @unused
      query: Query
    ): S = { state }

    def openResult(
      state: S,
      @unused
      container: Container,
      @unused
      result: Result
    ): S = { state }

    def closeMessage(
      state: S,
      container: Container,
      message: MessageDefinition
    ): S = {
      message match {
        case e: Event   => closeEvent(state, container, e)
        case c: Command => closeCommand(state, container, c)
        case q: Query   => closeQuery(state, container, q)
        case r: Result  => closeResult(state, container, r)
      }
    }

    def closeCommand(
      state: S,
      @unused
      container: Container,
      @unused
      command: Command
    ): S = { state }

    def closeEvent(
      state: S,
      @unused
      container: Container,
      @unused
      event: Event
    ): S = { state }

    def closeQuery(
      state: S,
      @unused
      container: Container,
      @unused
      query: Query
    ): S = { state }

    def closeResult(
      state: S,
      @unused
      container: Container,
      @unused
      result: Result
    ): S = { state }

    def doField(
      state: S,
      @unused
      container: Container,
      @unused
      field: Field
    ): S = { state }

    def doHandler(
      state: S,
      @unused
      container: Container,
      @unused
      consumer: Handler
    ): S = { state }

    def doAction(
      state: S,
      @unused
      container: Container,
      @unused
      action: ActionDefinition
    ): S = { state }

    def doExample(
      state: S,
      @unused
      container: Container,
      @unused
      example: Example
    ): S = { state }

    def doFunction(
      state: S,
      @unused
      container: Container,
      @unused
      function: Function
    ): S = { state }

    def doInvariant(
      state: S,
      @unused
      container: Container,
      @unused
      invariant: Invariant
    ): S = { state }

    def doPipe(
      state: S,
      @unused
      container: Container,
      @unused
      pipe: Pipe
    ): S = { state }
    def doProcessor(
      state: S,
      @unused
      container: Container,
      @unused
      pipe: Processor
    ): S = {state}

    def doJoint(
      state: S,
      @unused
      container: Container,
      @unused
      joint: Joint
    ): S = {state}

    def doSagaAction(
      state: S,
      @unused
      saga: AST.Saga,
      @unused
      definition: AST.Definition
    ): S = {state}

    def doPredefinedType(
      state: S,
      @unused
      container: Container,
      @unused
      predef: PredefinedType
    ): S = {state}

    def doAdaptation(
      state: S,
      @unused
      container: Container,
      @unused
      rule: Adaptation
    ): S = { state }
  }

}
