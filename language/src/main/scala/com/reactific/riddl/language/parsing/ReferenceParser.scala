/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.StringIn
import fastparse.ScalaWhitespace.*
import Terminals.*

private[parsing] trait ReferenceParser extends CommonParser {

  private def adaptorRef[u: P]: P[AdaptorRef] = {
    P(location ~ Keywords.adaptor ~ pathIdentifier)
      .map(tpl => (AdaptorRef.apply _).tupled(tpl))
  }

  private def maybeName[u: P]: P[Option[Identifier]] = {
    P((identifier ~ Punctuation.colon).?)
  }

  private def commandRef[u: P]: P[CommandRef] = {
    P(location ~ Keywords.command ~ maybeName ~ pathIdentifier)
      .map(tpl => (CommandRef.apply _).tupled(tpl))
  }

  private def eventRef[u: P]: P[EventRef] = {
    P(location ~ Keywords.event ~ maybeName ~ pathIdentifier)
      .map(tpl => (EventRef.apply _).tupled(tpl))
  }

  private def queryRef[u: P]: P[QueryRef] = {
    P(location ~ Keywords.query ~ maybeName ~ pathIdentifier)
      .map(tpl => (QueryRef.apply _).tupled(tpl))
  }

  private def resultRef[u: P]: P[ResultRef] = {
    P(location ~ Keywords.result ~ maybeName ~ pathIdentifier)
      .map(tpl => (ResultRef.apply _).tupled(tpl))
  }

  private def recordRef[u: P]: P[RecordRef] = {
    P(location ~ Keywords.record ~ maybeName ~ pathIdentifier)
      .map(tpl => (RecordRef.apply _).tupled(tpl))
  }

  def messageRef[u: P]: P[MessageRef] = {
    P(commandRef | eventRef | queryRef | resultRef | recordRef)
  }

  def entityRef[u: P]: P[EntityRef] = {
    P(location ~ Keywords.entity ~ pathIdentifier)
      .map(tpl => (EntityRef.apply _).tupled(tpl))
  }

  def functionRef[u: P]: P[FunctionRef] = {
    P(location ~ Keywords.function ~ pathIdentifier)
      .map(tpl => (FunctionRef.apply _).tupled(tpl))
  }

  def handlerRef[u: P]: P[HandlerRef] = {
    P(location ~ Keywords.handler ~ pathIdentifier)
      .map(tpl => (HandlerRef.apply _).tupled(tpl))
  }

  def stateRef[u: P]: P[StateRef] = {
    P(location ~ Keywords.state ~ pathIdentifier)
      .map(tpl => (StateRef.apply _).tupled(tpl))
  }

  def typeRef[u: P]: P[TypeRef] = {
    P(
      location ~
        StringIn(
          Keywords.`type`,
          Keywords.command,
          Keywords.query,
          Keywords.event,
          Keywords.result,
          Keywords.record,
          Keywords.other
        ).? ~ pathIdentifier
    )
      .map(tpl => (TypeRef.apply _).tupled(tpl))
  }

  def fieldRef[u: P]: P[FieldRef] = {
    P(location ~ Keywords.field ~ pathIdentifier)
      .map(tpl => (FieldRef.apply _).tupled(tpl))
  }

  def constantRef[u: P]: P[ConstantRef] = {
    P(location ~ Keywords.const ~ pathIdentifier)
      .map(tpl => (ConstantRef.apply _).tupled(tpl))
  }

  def contextRef[u: P]: P[ContextRef] = {
    P(location ~ Keywords.context ~ pathIdentifier)
      .map(tpl => (ContextRef.apply _).tupled(tpl))
  }

  def outletRef[u: P]: P[OutletRef] = {
    P(location ~ Keywords.outlet ~ pathIdentifier)
      .map(tpl => (OutletRef.apply _).tupled(tpl))
  }

  def inletRef[u: P]: P[InletRef] = {
    P(location ~ Keywords.inlet ~ pathIdentifier)
      .map(tpl => (InletRef.apply _).tupled(tpl))
  }

  private def streamletRef[u: P]: P[StreamletRef] = {
    P(
      location ~ StringIn(
        Keywords.source,
        Keywords.sink,
        Keywords.merge,
        Keywords.split,
        Keywords.void
      ) ~ pathIdentifier
    ).map(tpl => (StreamletRef.apply _).tupled(tpl))
  }

  private def projectorRef[u: P]: P[ProjectorRef] = {
    P(location ~ Keywords.projector ~ pathIdentifier)
      .map(tpl => (ProjectorRef.apply _).tupled(tpl))
  }

  private def repositoryRef[u: P]: P[RepositoryRef] = {
    P(location ~ Keywords.repository ~ pathIdentifier)
      .map(tpl => (RepositoryRef.apply _).tupled(tpl))
  }

  def sagaRef[u: P]: P[SagaRef] = {
    P(location ~ Keywords.saga ~ pathIdentifier)
      .map(tpl => (SagaRef.apply _).tupled(tpl))
  }

  def epicRef[u: P]: P[EpicRef] = {
    P(location ~ Keywords.epic ~ pathIdentifier)
      .map(tpl => (EpicRef.apply _).tupled(tpl))
  }

  def userRef[u: P]: P[UserRef] = {
    P(location ~ Keywords.user ~ pathIdentifier)
      .map(tpl => (UserRef.apply _).tupled(tpl))
  }

  private def applicationRef[u: P]: P[ApplicationRef] = {
    P(location ~ Keywords.application ~ pathIdentifier)
      .map(tpl => (ApplicationRef.apply _).tupled(tpl))
  }

  def outputRef[u: P]: P[OutputRef] = {
    P(location ~ outputAliases ~ pathIdentifier)
      .map { case (loc, _, pid) => OutputRef(loc, pid) }
  }

  def inputRef[u: P]: P[InputRef] = {
    P(location ~ inputAliases ~ pathIdentifier)
      .map { case (loc, _, pid) => InputRef(loc, pid) }
  }

  def groupRef[u: P]: P[GroupRef] = {
    P(location ~ groupAliases ~ pathIdentifier)
      .map { case (loc, _, pid) => GroupRef(loc, pid) }
  }

  def authorRefs[u: P]: P[Seq[AuthorRef]] = {
    P(location ~ by ~ Keywords.author ~ pathIdentifier)
      .map(tpl => (AuthorRef.apply _).tupled(tpl))
      .rep(0, ",", 3)
  }

  def processorRef[u: P]: P[ProcessorRef[Processor[?, ?]]] = {
    P(
      adaptorRef | applicationRef | contextRef | entityRef | projectorRef |
        repositoryRef | streamletRef
    )
  }

  def arbitraryInteractionRef[u: P]: P[Reference[Definition]] = {
    P(processorRef | sagaRef | inputRef | outputRef | groupRef)
  }

  def anyInteractionRef[u: P]: P[Reference[Definition]] = {
    arbitraryInteractionRef | userRef
  }
}
