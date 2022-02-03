package com.yoppworks.riddl.translator.hugo

import com.yoppworks.ossum.riddl.language.Folding.Folding
import com.yoppworks.ossum.riddl.language.{AST, Folding}

case class HugoTranslatorState() extends Folding.State[HugoTranslatorState] {
  override def step(f: HugoTranslatorState => HugoTranslatorState): HugoTranslatorState = {
    f(this)
  }
}

class HugoTranslatorFolding extends Folding[HugoTranslatorState] {
  override def openRootDomain(
    s: HugoTranslatorState, container: AST.RootContainer, domain: AST
  .Domain): HugoTranslatorState = ???

  override def closeRootDomain(
    s: HugoTranslatorState, container: AST.RootContainer, domain: AST
  .Domain): HugoTranslatorState = ???

  override def openDomain(state: HugoTranslatorState, container: AST.Domain, domain: AST.Domain)
  : HugoTranslatorState = ???

  override def closeDomain(state: HugoTranslatorState, container: AST.Domain, domain: AST.Domain)
  : HugoTranslatorState = ???

  override def openContext(
    state: HugoTranslatorState, container: AST.Domain, context: AST
  .Context): HugoTranslatorState = ???

  override def closeContext(
    state: HugoTranslatorState, container: AST.Domain, context: AST
  .Context): HugoTranslatorState = ???

  override def openStory(state: HugoTranslatorState, container: AST.Domain, story: AST.Story)
  : HugoTranslatorState = ???

  override def closeStory(state: HugoTranslatorState, container: AST.Domain, story: AST.Story)
  : HugoTranslatorState = ???

  override def openEntity(state: HugoTranslatorState, container: AST.Context, entity: AST.Entity)
  : HugoTranslatorState = ???

  override def closeEntity(
    state: HugoTranslatorState, container: AST.Context, entity: AST
  .Entity): HugoTranslatorState = ???

  override def openPlant(state: HugoTranslatorState, container: AST.Domain, plant: AST.Plant)
  : HugoTranslatorState = ???

  override def closePlant(state: HugoTranslatorState, container: AST.Domain, plant: AST.Plant)
  : HugoTranslatorState = ???

  override def openProcessor(
    state: HugoTranslatorState, container: AST.Plant, processor: AST
  .Processor): HugoTranslatorState = ???

  override def closeProcessor(
    state: HugoTranslatorState, container: AST.Plant, processor: AST
  .Processor): HugoTranslatorState = ???

  override def openState(state: HugoTranslatorState, container: AST.Entity, s: AST.State)
  : HugoTranslatorState = ???

  override def closeState(state: HugoTranslatorState, container: AST.Entity, s: AST.State)
  : HugoTranslatorState = ???

  override def openSaga(state: HugoTranslatorState, container: AST.Context, saga: AST.Saga)
  : HugoTranslatorState = ???

  override def closeSaga(state: HugoTranslatorState, container: AST.Context, saga: AST.Saga)
  : HugoTranslatorState = ???

  override def openInteraction(
    state: HugoTranslatorState, container: AST.Container[AST
  .Interaction], interaction: AST.Interaction): HugoTranslatorState = ???

  override def closeInteraction(
    state: HugoTranslatorState, container: AST.Container[AST
  .Interaction], interaction: AST.Interaction): HugoTranslatorState = ???

  override def openFunction[TCD <: AST.Container[AST.Definition]](
    state: HugoTranslatorState,
    container: TCD, feature: AST.Function): HugoTranslatorState = ???

  override def closeFunction[TCD <: AST.Container[AST.Definition]](
    state: HugoTranslatorState,
    container: TCD, feature: AST.Function): HugoTranslatorState = ???

  override def openAdaptor(
    state: HugoTranslatorState, container: AST.Context, adaptor: AST
  .Adaptor): HugoTranslatorState = ???

  override def closeAdaptor(
    state: HugoTranslatorState, container: AST.Context, adaptor: AST
  .Adaptor): HugoTranslatorState = ???

  override def doType(
    state: HugoTranslatorState, container: AST.Container[AST.Definition],
    typ: AST.Type): HugoTranslatorState = ???

  override def doStateField(state: HugoTranslatorState, container: AST.State, field: AST.Field)
  : HugoTranslatorState = ???

  override def doHandler(
    state: HugoTranslatorState, container: AST.Entity, consumer: AST
  .Handler): HugoTranslatorState = ???

  override def doAction(
    state: HugoTranslatorState, container: AST.Interaction, action: AST
  .ActionDefinition): HugoTranslatorState = ???

  override def doFunctionExample(
    state: HugoTranslatorState, container: AST.Function,
    example: AST.Example): HugoTranslatorState = ???

  override def doStoryExample(
    state: HugoTranslatorState, container: AST.Story, example: AST
  .Example): HugoTranslatorState = ???

  override def doProcessorExample(
    state: HugoTranslatorState, container: AST.Processor,
    example: AST.Example): HugoTranslatorState = ???

  override def doInvariant(
    state: HugoTranslatorState, container: AST.Entity, invariant: AST
  .Invariant): HugoTranslatorState = ???

  override def doPipe(state: HugoTranslatorState, container: AST.Plant, pipe: AST.Pipe)
  : HugoTranslatorState = ???

  override def doInlet(state: HugoTranslatorState, container: AST.Processor, inlet: AST.Inlet)
  : HugoTranslatorState = ???

  override def doOutlet(state: HugoTranslatorState, container: AST.Processor, outlet: AST.Outlet)
  : HugoTranslatorState = ???

  override def doJoint(state: HugoTranslatorState, container: AST.Plant, joint: AST.Joint)
  : HugoTranslatorState = ???

  override def doSagaAction(
    state: HugoTranslatorState, container: AST.Saga, definition: AST
  .SagaAction): HugoTranslatorState = ???

  override def doAdaptation(
    state: HugoTranslatorState, container: AST.Adaptor, rule: AST
  .Adaptation): HugoTranslatorState = ???
}
