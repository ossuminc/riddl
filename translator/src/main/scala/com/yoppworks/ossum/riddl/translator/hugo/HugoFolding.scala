package com.yoppworks.ossum.riddl.translator.hugo

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Folding.Folding

/** Unit Tests For HugoFolding */
class HugoFolding extends Folding[HugoState] {

  override def openDomain(
    state: HugoState,
    container: Container,
    domain: Domain
  ): HugoState = {
    state.pushContext(container)
    val _ = state.openFile("overview.md")
    // hfile.state.open(s"domain ${domain.id.value} {")
    state
  }

  override def closeDomain(
    state: HugoState,
    container: Container,
    domain: Domain
  ): HugoState = {
    // state.close(domain)
    state
  }

  override def openContext(
    state: HugoState,
    container: Container,
    context: Context
  ): HugoState = {
    // state.open(s"context ${context.id.value} {")
    state
  }

  override def closeContext(
    state: HugoState,
    container: Container,
    context: Context
  ): HugoState = {
    // state.close(context)
    state
  }

  override def openEntity(
    state: HugoState,
    container: Container,
    entity: Entity
  ): HugoState = {
    /*
    state.open(s"entity ${entity.id.value} {").addLine(s"state is ").step {
      st =>
        entity.states.foldLeft(st) { (s, state) =>
          s.addLine(s"state ${state.id.value} is").visitTypeExpr(state.typeEx)
        }
    }.step { st =>
      entity.options.size match {
        case 1 => st.addLine(s"option is ${entity.options.head}")
        case x: Int if x > 1 =>
          st.addLine(s"options {")
            .addLine(entity.options.iterator.map(_.name).mkString(" "))
            .addLine(" }")
        case _ => st
      }
    }.step { st =>
      entity.consumers.foldLeft(st) { case (s, handler) =>
        s.addLine(s"consumes topic ${handler.id.value}")
      }
    }

     */
    state
  }

  override def closeEntity(
    state: HugoState,
    container: Container,
    entity: Entity
  ): HugoState = { state }

  override def openFeature(
    state: HugoState,
    container: Container,
    feature: Feature
  ): HugoState = { state }

  override def closeFeature(
    state: HugoState,
    container: Container,
    feature: Feature
  ): HugoState = { state }

  override def openAdaptor(
    state: HugoState,
    container: Container,
    adaptor: Adaptor
  ): HugoState = { state }

  override def closeAdaptor(
    state: HugoState,
    container: Container,
    adaptor: Adaptor
  ): HugoState = { state }

  override def openTopic(
    state: HugoState,
    container: Container,
    topic: Topic
  ): HugoState = { state }

  override def closeTopic(
    state: HugoState,
    container: Container,
    topic: Topic
  ): HugoState = { state }

  override def openInteraction(
    state: HugoState,
    container: Container,
    interaction: Interaction
  ): HugoState = { state }

  override def closeInteraction(
    state: HugoState,
    container: Container,
    interaction: Interaction
  ): HugoState = { state }

  override def openCommand(
    state: HugoState,
    container: Container,
    command: Command
  ): HugoState = {
    /*
    val keyword = if (command.events.size > 1) "events" else "event"
    state.addIndent(s"command ${command.id.value} is ").visitTypeExpr(
      command.typ
    ).add(
      s" yields $keyword ${command.events.map(_.id.value.mkString(".")).mkString(", ")}"
    ).add("\n")

     */
    state
  }

  override def openEvent(
    state: HugoState,
    container: Container,
    event: Event
  ): HugoState = {
    /*
    state.addIndent(s"event ${event.id.value} is ").visitTypeExpr(event.typ)
      .add("\n")

     */
    state
  }

  override def openQuery(
    state: HugoState,
    container: Container,
    query: Query
  ): HugoState = {
    /* state.addIndent(s"query ${query.id.value} is ").visitTypeExpr(query.typ)
      .add(s" yields result ${query.result.id.value.mkString(".")}").add("\n")
     */
    state
  }

  override def openResult(
    state: HugoState,
    container: Container,
    result: Result
  ): HugoState = {
    /* state.addIndent(s"result ${result.id.value} is ").visitTypeExpr(result
    .typ)
      .add("\n")

     */
    state
  }

  override def doType(
    state: HugoState,
    container: Container,
    typeDef: Type
  ): HugoState = {
    /* state.addIndent().add(s"type ${typeDef.id.value} is ")
      .visitTypeExpr(typeDef.typ).visitDescription(typeDef.description)
      .add("\n")

     */
    state
  }

  override def doAction(
    state: HugoState,
    container: Container,
    action: ActionDefinition
  ): HugoState = {
    /*
    action match {
      case m: MessageAction =>
        // TODO: fix this
        state.open(s"action ${action.id.value} is {")
        state.close(m)
    }
     */
    state
  }

  override def doExample(
    state: HugoState,
    container: Container,
    example: Example
  ): HugoState = { state }

  override def doFunction(
    state: HugoState,
    container: Container,
    function: Function
  ): HugoState = { state }

  override def doInvariant(
    state: HugoState,
    container: Container,
    invariant: Invariant
  ): HugoState = { state }

  override def doPredefinedType(
    state: HugoState,
    container: Container,
    predef: PredefinedType
  ): HugoState = { state }

  override def doTranslationRule(
    state: HugoState,
    container: Container,
    rule: TranslationRule
  ): HugoState = { state }
}
