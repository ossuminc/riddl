package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*

import scala.collection.mutable

object Folding {

  type SimpleDispatch[S] = (Container[Definition], Definition, S) => S

  def foldEachDefinition[S](
    parent: Container[Definition],
    child: Definition,
    state: S
  )(f: SimpleDispatch[S]
  ): S = {
    child match {
      case subcontainer: ParentDefOf[Definition] =>
        val result = f(parent, child, state)
        subcontainer.contents.foldLeft(result) { case (next, child) =>
          foldEachDefinition[S](subcontainer, child, next)(f)
        }
      case ch: Definition => f(parent, ch, state)
    }
  }

  final def foldLeftWithStack[S](
    value: S,
    parents: mutable.Stack[ParentDefOf[Definition]] = mutable.Stack
      .empty[ParentDefOf[Definition]]
  )(top: ParentDefOf[Definition]
  )(f: (S, Definition, Seq[ParentDefOf[Definition]]) => S
  ): S = {
    val initial = f(value, top, parents.toSeq)
    parents.push(top)
    try {
      top.contents.foldLeft(initial) { (next, definition) =>
        definition match {
          case i: Include => i.contents.foldLeft(next) {
              case (n, cd: ParentDefOf[Definition]) =>
                foldLeftWithStack(n, parents)(cd)(f)
              case (n, d: Definition) => f(n, d, parents.toSeq)
            }
          case c: ParentDefOf[Definition] =>
            foldLeftWithStack(next, parents)(c)(f)
          case d: Definition => f(next, d, parents.toSeq)
        }
      }
    } finally { parents.pop() }
  }

  /*  final def foldLeft[S](
    value: S,
    parents: mutable.Stack[ParentDefOf[Definition]] =
    mutable.Stack.empty[ParentDefOf[Definition]]
  )(top: Seq[ParentDefOf[Definition]])(
    f: (S, Definition, mutable.Stack[ParentDefOf[Definition]]) => S
  ): S = {
    top.foldLeft(value) {
      case (next, definition: ParentDefOf[Definition]) =>
        foldLeftWithStack(next, parents)(definition)(f)
    }
  }*/

  final def foldAround[S](
    value: S,
    top: ParentDefOf[Definition],
    folder: Folder[S],
    parents: mutable.Stack[ParentDefOf[Definition]] = mutable.Stack
      .empty[ParentDefOf[Definition]]
  ): S = {
    parents.push(top)
    try {
      val parentStack = parents.toSeq.drop(1)
      // Let them know a container is being opened
      val startState = folder.openContainer(value, top, parentStack)
      val middleState = top.contents.foldLeft(startState) {
        case (next, container: ParentDefOf[Definition]) =>
          // Container node so recurse
          foldAround(next, container, folder, parents)
        case (next, definition: Definition) =>
          // Leaf node so mention it
          folder.doDefinition(next, definition, parentStack)
      }
      // Let them know a container is being closed
      folder.closeContainer(middleState, top, parentStack)
    } finally { parents.pop() }
  }

  trait Folder[STATE] {
    def openContainer(
      state: STATE,
      container: ParentDefOf[Definition],
      parents: Seq[ParentDefOf[Definition]]
    ): STATE

    def doDefinition(
      state: STATE,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): STATE

    def closeContainer(
      state: STATE,
      container: ParentDefOf[Definition],
      parents: Seq[ParentDefOf[Definition]]
    ): STATE
  }

  trait State[S <: State[?]] {
    def step(f: S => S): S
  }

  trait Folding[S <: State[S]] {

    final def foldRootLeft(
      root: RootContainer,
      initState: S
    ): S = {
      root.contents.foldLeft(initState) { (s, domain) =>
        foldLeft(root, domain, s)
      }
    }

    final def foldIncludeLeft(
      parent: ParentDefOf[Definition],
      include: Include,
      initState: S
    ): S = {
      val s2 = openInclude(initState, parent, include)
      include.contents.foldLeft(s2) {
        case (s, incl: Include) =>
          // parent, not include, as if include didn't even exist :)
          foldIncludeLeft(parent, incl, s)
        case (s, d: ParentDefOf[Definition]) =>
          // parent, not include, as if include didn't even exist :)
          foldLeft(parent, d, s)
        case _ =>
          throw new IllegalStateException("Include contains a non-definition?")
      }
      closeInclude(initState, parent, include)
    }

    /** Container Traversal This foldLeft allows the hierarchy of containers to
      * be navigated
      */
    // noinspection ScalaStyle
    final def foldLeft[CT <: ParentDefOf[Definition]](
      parent: CT,
      container: CT,
      initState: S
    ): S = {
      container match {
        case t: Type => doType(initState, parent, t)
        case domain: Domain => openDomain(initState, parent, domain)
            .step { state =>
              domain.contents.foldLeft(state) { case (next, dd) =>
                dd match {
                  case typ: Type        => foldLeft(domain, typ, next)
                  case context: Context => foldLeft(domain, context, next)
                  case plant: Plant     => foldLeft(domain, plant, next)
                  case story: Story     => foldLeft(domain, story, next)
                  case interaction: Interaction =>
                    foldLeft(domain, interaction, next)
                  case subDomain: Domain => foldLeft(domain, subDomain, next)
                  case include: Include =>
                    foldIncludeLeft(domain, include, next)
                }
              }
            }.step { state => closeDomain(state, parent, domain) }
        case context: Context =>
          val parentDomain = parent.asInstanceOf[Domain]
          openContext(initState, parentDomain, context).step { state =>
            context.contents.foldLeft(state) { (next, cd) =>
              cd match {
                case typ: Type          => foldLeft(context, typ, next)
                case entity: Entity     => foldLeft(context, entity, next)
                case adaptor: Adaptor   => foldLeft(context, adaptor, next)
                case saga: Saga         => foldLeft(context, saga, next)
                case function: Function => foldLeft(context, function, next)
                case interaction: Interaction =>
                  foldLeft(context, interaction, next)
                case include: Include => foldIncludeLeft(context, include, next)
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
                case typ: Type              => foldLeft(entity, typ, st)
                case entityState: AST.State => foldLeft(entity, entityState, st)
                case handler: Handler       => doHandler(st, entity, handler)
                case function: Function     => foldLeft(entity, function, st)
                case invariant: Invariant => doInvariant(st, entity, invariant)
                case include: Include => foldIncludeLeft(entity, include, st)
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
                case include: Include => foldIncludeLeft(plant, include, next)
              }
            }
          }.step { state => closePlant(state, containingDomain, plant) }
        case processor: Processor =>
          val containingPlant = parent.asInstanceOf[Plant]
          openProcessor(initState, containingPlant, processor).step { state =>
            processor.contents.foldLeft(state) { (next, streamlet) =>
              streamlet match {
                case inlet: Inlet   => doInlet(next, processor, inlet)
                case outlet: Outlet => doOutlet(next, processor, outlet)
                case example: Example =>
                  doProcessorExample(next, processor, example)
              }
            }
          }.step { state => closeProcessor(state, containingPlant, processor) }
        case saga: Saga =>
          val parentDomain = parent.asInstanceOf[Context]
          openSaga(initState, parentDomain, saga).step { state =>
            saga.contents.foldLeft(state) { (next, action) =>
              doSagaStep(next, saga, action)
            }
          }.step { state => closeSaga(state, parentDomain, saga) }
        case _: SagaStep =>
          // Handled by Saga
          initState
        case interaction: Interaction =>
          val interactionContainer = parent
            .asInstanceOf[ParentDefOf[Interaction]]
          openInteraction(initState, interactionContainer, interaction).step {
            state =>
              interaction.contents.foldLeft(state) { (next, id) =>
                id match {
                  case action: MessageAction =>
                    doAction(next, interaction, action)
                }
              }
          }.step { state =>
            closeInteraction(state, interactionContainer, interaction)
          }
        case function: Function => openFunction(initState, parent, function)
            .step { state =>
              function.contents.foldLeft(state) { (next, fd) =>
                fd match {
                  case example: Example =>
                    doFunctionExample(next, function, example)
                }
              }
            }.step { state => closeFunction(state, parent, function) }
        case adaptor: Adaptor =>
          val parentContext = parent.asInstanceOf[Context]
          openAdaptor(initState, parentContext, adaptor).step { state =>
            adaptor.contents.foldLeft(state) { (s, ad) =>
              ad match {
                case adaptation: Adaptation =>
                  doAdaptation(initState, adaptor, adaptation)
                case include: Include => foldIncludeLeft(adaptor, include, s)
              }
            }
          }.step { state => closeAdaptor(state, parentContext, adaptor) }
        case state: AST.State =>
          val parentEntity = parent.asInstanceOf[Entity]
          openState(initState, parentEntity, state).step { foldingState =>
            state.typeEx.fields.foldLeft(foldingState) { (next, field) =>
              doStateField(next, state, field)
            }
          }.step { foldingState =>
            closeState(foldingState, parentEntity, state)
          }
        case i: Include       => foldIncludeLeft(parent, i, initState)
        case r: RootContainer => foldRootLeft(r, initState)
        case _: Handler       => initState // handled by Entity case
        case _: OnClause      => initState // handled by Entity case
        case _: Adaptation =>
          throw new IllegalStateException("Adaptation not expected")
      }
    }

    def openRootDomain(
      s: S,
      container: AST.RootContainer,
      domain: AST.Domain
    ): S

    def closeRootDomain(
      s: S,
      container: AST.RootContainer,
      domain: AST.Domain
    ): S

    def openInclude(
      s: S,
      container: AST.ParentDefOf[Definition],
      include: Include
    ): S

    def closeInclude(
      s: S,
      container: AST.ParentDefOf[AST.Definition],
      include: AST.Include
    ): S

    def openDomain(
      state: S,
      container: ParentDefOf[Definition],
      domain: Domain
    ): S

    def closeDomain(
      state: S,
      container: ParentDefOf[Definition],
      domain: Domain
    ): S

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
      container: ParentDefOf[Interaction],
      interaction: Interaction
    ): S

    def closeInteraction(
      state: S,
      container: ParentDefOf[Interaction],
      interaction: Interaction
    ): S

    def openFunction[TCD <: ParentDefOf[Definition]](
      state: S,
      container: TCD,
      feature: Function
    ): S

    def closeFunction[TCD <: ParentDefOf[Definition]](
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
      container: ParentDefOf[Definition],
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

    def doSagaStep(
      state: S,
      container: AST.Saga,
      definition: AST.SagaStep
    ): S

    def doAdaptation(
      state: S,
      container: Adaptor,
      rule: Adaptation
    ): S
  }
}
