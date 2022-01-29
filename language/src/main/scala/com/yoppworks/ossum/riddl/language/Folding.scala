package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*

object Folding {

  trait State[S <: State[?]] {
    def step(f: S => S): S
  }

  type SimpleDispatch[S] = (Container[Definition], Definition, S) => S

  def foldEachDefinition[S](
    parent: Container[Definition],
    container: Container[Definition],
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
        result = domain.plants.foldLeft(result) { (next, pl) =>
          foldEachDefinition(domain, pl, next)(f)
        }
        result = domain.stories.foldLeft(result) { (next, st) =>
          foldEachDefinition(domain, st, next)(f)
        }
        result = domain.contexts.foldLeft(result) { (next, context) =>
          foldEachDefinition[S](domain, context, next)(f)
        }
        domain.interactions.foldLeft(result) { (next, interaction) =>
          foldEachDefinition[S](domain, interaction, next)(f)
        }
        domain.domains.foldLeft(result) { (next, dmn) => foldEachDefinition(domain, dmn, next)(f) }

      case context: Context =>
        result = f(parent, context, result)
        result = context.types.foldLeft(result) { (next, adaptor) => f(context, adaptor, next) }
        result = context.adaptors.foldLeft(result) { (next, adaptor) =>
          foldEachDefinition[S](context, adaptor, next)(f)
        }
        result = context.entities.foldLeft(result) { (next, entity) =>
          foldEachDefinition[S](context, entity, next)(f)
        }
        result = context.sagas.foldLeft(result) { (next, saga) =>
          foldEachDefinition(context, saga, next)(f)
        }
        result = context.functions.foldLeft(result) { (next, feature) =>
          foldEachDefinition[S](context, feature, next)(f)
        }
        context.interactions.foldLeft(result) { (next, interaction) =>
          foldEachDefinition[S](context, interaction, next)(f)
        }
      case story: Story =>
        result = f(parent, story, result)
        story.contents.foldLeft(result) { (next, example) =>
          f(story, example, next)
        }
      case entity: Entity =>
        result = f(parent, entity, result)
        result = entity.types.foldLeft(result) { (next, typ) => f(entity, typ, next) }
        result = entity.states.foldLeft(result) { (next, state) =>
          val s = f(entity, state, next)
          state.typeEx.fields.foldLeft(s) { (next, field: Field) => f(state, field, next) }
        }
        result = entity.invariants.foldLeft(result) { (next, inv) => f(entity, inv, next) }
        result = entity.handlers.foldLeft(result) { (next, handler) => f(entity, handler, next) }
        entity.functions.foldLeft(result) { (next, feature) =>
          foldEachDefinition[S](entity, feature, next)(f)
        }
      case plant: Plant =>
        result = f(parent, plant, result)
        result = plant.pipes.foldLeft(result) { (next, pipe) => f(plant, pipe, next) }
        result = plant.processors.foldLeft(result) { (next, proc) =>
          foldEachDefinition(plant, proc, next)(f)
        }
        result = plant.inJoints.foldLeft(result) { (next, joint) => f(plant, joint, next) }
        plant.outJoints.foldLeft(result) { (next, joint) => f(plant, joint, next) }
      case processor: Processor =>
        result = f(parent, processor, result)
        processor.contents.foldLeft(result) { (next, proc) => f(processor, proc, next) }
      case saga: Saga =>
        result = f(parent, saga, result)
        saga.contents.foldLeft(result) { (next, sagaAction) =>
          foldEachDefinition(saga, sagaAction, next)(f)
        }
      case sagaAction: SagaAction =>
        result = f(parent, sagaAction, result)
        sagaAction.contents.foldLeft(result) { (next, example) => f(sagaAction, example, next) }
      case interaction: Interaction =>
        result = f(parent, interaction, result)
        interaction.contents.foldLeft(result) { (next, action) => f(interaction, action, next) }
      case function: Function =>
        result = f(parent, function, result)
        function.contents.foldLeft(result) { (next, example) => f(function, example, next) }
      case adaptor: Adaptor =>
        result = f(parent, adaptor, result)
        adaptor.contents.foldLeft(result) { (next, adaptation) => f(adaptor, adaptation, next) }
      case st: AST.State =>
        result = f(parent, st, result)
        st.typeEx.fields.foldLeft(result) { (next, field) => f(st, field, next) }
    }
  }

  trait Folding[S <: State[S]] {

    def doDomainContent(domain: Domain, state: S): S = {
      domain.contents.foldLeft(state) { case (next, dd) =>
        dd match {
          case typ: Type => doType(next, domain, typ)
          case context: Context => foldLeft(domain, context, next)
          case plant: Plant => foldLeft(domain, plant, next)
          case story: Story => foldLeft(domain, story, next)
          case interaction: Interaction => foldLeft(domain, interaction, next)
          case subDomain: Domain => foldLeft(domain, subDomain, next)
        }
      }
    }

    /** Container Traversal This foldLeft allows the hierarchy of containers to be navigated
     */
    // noinspection ScalaStyle
    final def foldLeft[CT <: Container[Definition]](
      parent: CT,
      container: CT,
      initState: S
    ): S = {
      container match {
        case root: RootContainer => root.contents.foldLeft(initState) { (s, domain) =>
          val s1 = openRootDomain(s, root, domain)
          val s2 = doDomainContent(domain, s1)
          closeRootDomain(s2, root, domain)
        }
        case domain: Domain =>
          val domainContainer = parent.asInstanceOf[Domain]
          openDomain(initState, domainContainer, domain).step { state =>
            doDomainContent(domain, state)
          }.step { state => closeDomain(state, domainContainer, domain) }
        case context: Context =>
          val parentDomain = parent.asInstanceOf[Domain]
          openContext(initState, parentDomain, context).step { state =>
            context.contents.foldLeft(state) { (next, cd) =>
              cd match {
                case typ: Type => doType(next, context, typ)
                case entity: Entity => foldLeft(context, entity, next)
                case adaptor: Adaptor => foldLeft(context, adaptor, next)
                case saga: Saga => foldLeft(context, saga, next)
                case function: Function => foldLeft(context, function, next)
                case interaction: Interaction => foldLeft(context, interaction, next)
              }
            }
          }.step { state => closeContext(state, parentDomain, context) }
        case story: Story =>
          val parentDomain = parent.asInstanceOf[Domain]
          openStory(initState, parentDomain, story).step { state =>
            story.contents.foldLeft(state) { (next, example) =>
              doStoryExample(next, story, example)
            }
          }.step { state => closeStory(state, parentDomain, story) }
        case entity: Entity =>
          val parentContext = parent.asInstanceOf[Context]
          openEntity(initState, parentContext, entity).step { state =>
            entity.contents.foldLeft(state) { (st, entityDefinition) =>
              entityDefinition match {
                case typ: Type => doType(st, entity, typ)
                case entityState: AST.State => foldLeft(entity, entityState, st)
                case handler: Handler => doHandler(st, entity, handler)
                case function: Function => foldLeft(entity, function, st)
                case invariant: Invariant => doInvariant(st, entity, invariant)
              }
            }
          }.step { state => closeEntity(state, parentContext, entity) }
        case plant: Plant =>
          val containingDomain = parent.asInstanceOf[Domain]
          openPlant(initState, containingDomain, plant).step { state =>
            plant.contents.foldLeft(state) { (next, pd) =>
              pd match {
                case pipe: Pipe           => doPipe(next, plant, pipe)
                case processor: Processor => foldLeft(plant, processor, next)
                case joint: Joint         => doJoint(next, plant, joint)
              }
            }
          }.step { state => closePlant(state, containingDomain, plant) }
        case processor: Processor =>
          val containingPlant = parent.asInstanceOf[Plant]
          openProcessor(initState, containingPlant, processor).step { state =>
            processor.contents.foldLeft(state) { (next, streamlet) =>
              streamlet match {
                case inlet: Inlet => doInlet(next, processor, inlet)
                case outlet: Outlet => doOutlet(next, processor, outlet)
                case example: Example => doProcessorExample(next, processor, example)
              }
            }
          }.step { state => closeProcessor(state, containingPlant, processor) }
        case saga: Saga =>
          val parentDomain = parent.asInstanceOf[Context]
          openSaga(initState, parentDomain, saga).step { state =>
            saga.contents.foldLeft(state) { (next, action) => doSagaAction(next, saga, action) }
          }.step { state => closeSaga(state, parentDomain, saga) }
        case _: SagaAction =>
          // Handled by Saga
          initState
        case interaction: Interaction =>
          val interactionContainer = parent.asInstanceOf[Container[Interaction]]
          openInteraction(initState, interactionContainer, interaction).step { state =>
            interaction.contents.foldLeft(state) { (next, id) =>
              id match {case action: MessageAction => doAction(next, interaction, action)}
            }
          }.step { state => closeInteraction(state, interactionContainer, interaction) }
        case function: Function =>
          openFunction(initState, parent, function).step { state =>
            function.contents.foldLeft(state) { (next, fd) =>
              fd match {case example: Example => doFunctionExample(next, function, example)}
            }
          }.step { state => closeFunction(state, parent, function) }
        case adaptor: Adaptor =>
          val parentContext = parent.asInstanceOf[Context]
          openAdaptor(initState, parentContext, adaptor).step { state =>
            adaptor.contents.foldLeft(state) { (s, ad) =>
              ad match { case adaptation: Adaptation => doAdaptation(s, adaptor, adaptation) }
            }
          }.step { state => closeAdaptor(state, parentContext, adaptor) }
        case state: AST.State =>
          val parentEntity = parent.asInstanceOf[Entity]
          openState(initState, parentEntity, state).step { foldingState =>
            state.typeEx.fields.foldLeft(foldingState) { (next, field) =>
              doStateField(next, state, field)
            }
          }.step { foldingState => closeState(foldingState, parentEntity, state) }
      }
    }

    def openRootDomain(s: S, container: AST.RootContainer, domain: AST.Domain): S

    def closeRootDomain(s: S, container: AST.RootContainer, domain: AST.Domain): S

    def openDomain(state: S, container: Domain, domain: Domain): S

    def closeDomain(state: S, container: Domain, domain: Domain): S

    def openContext(
      state: S,
      container: Domain,
      context: Context
    ): S

    def closeContext(
      state: S,
      container: Domain,
      context: Context
    ): S

    def openStory(
      state: S,
      container: Domain,
      story: Story
    ): S

    def closeStory(
      state: S,
      container: Domain,
      story: Story
    ): S

    def openEntity(
      state: S,
      container: Context,
      entity: Entity
    ): S

    def closeEntity(
      state: S,
      container: Context,
      entity: Entity
    ): S

    def openPlant(
      state: S,
      container: Domain,
      plant: Plant
    ): S

    def closePlant(
      state: S,
      container: Domain,
      plant: Plant
    ): S

    def openProcessor(
      state: S,
      container: Plant,
      processor: Processor
    ): S

    def closeProcessor(
      state: S,
      container: Plant,
      processor: Processor
    ): S

    def openState(
      state: S,
      container: Entity,
      s: AST.State
    ): S

    def closeState(
      state: S,
      container: Entity,
      s: AST.State
    ): S

    def openSaga(
      state: S,
      container: Context,
      saga: Saga
    ): S

    def closeSaga(
      state: S,
      container: Context,
      saga: Saga
    ): S

    def openInteraction(
      state: S,
      container: Container[Interaction],
      interaction: Interaction
    ): S

    def closeInteraction(
      state: S,
      container: Container[Interaction],
      interaction: Interaction
    ): S

    def openFunction[TCD <: Container[Definition]](
      state: S,
      container: TCD,
      feature: Function
    ): S

    def closeFunction[TCD <: Container[Definition]](
      state: S,
      container: TCD,
      feature: Function
    ): S

    def openAdaptor(
      state: S,
      container: Context,
      adaptor: Adaptor
    ): S

    def closeAdaptor(
      state: S,
      container: Context,
      adaptor: Adaptor
    ): S

    def doType(
      state: S,
      container: Container[Definition],
      typ: Type
    ): S

    def doStateField(
      state: S,
      container: AST.State,
      field: Field
    ): S

    def doHandler(
      state: S,
      container: Entity,
      consumer: Handler
    ): S

    def doAction(
      state: S,
      container: Interaction,
      action: ActionDefinition
    ): S

    def doFunctionExample(
      state: S,
      container: Function,
      example: Example
    ): S

    def doStoryExample(
      state: S,
      container: Story,
      example: Example
    ): S

    def doProcessorExample(
      state: S,
      container: Processor,
      example: Example
    ): S

    def doInvariant(
      state: S,
      container: Entity,
      invariant: Invariant
    ): S

    def doPipe(
      state: S,
      container: Plant,
      pipe: Pipe
    ): S

    def doInlet(
      state: S,
      container: Processor,
      inlet: Inlet
    ): S

    def doOutlet(
      state: S,
      container: Processor,
      outlet: Outlet
    ): S

    def doJoint(
      state: S,
      container: Plant,
      joint: Joint
    ): S

    def doSagaAction(
      state: S,
      container: AST.Saga,
      definition: AST.SagaAction
    ): S

    def doAdaptation(
      state: S,
      container: Adaptor,
      rule: Adaptation
    ): S
  }
}
