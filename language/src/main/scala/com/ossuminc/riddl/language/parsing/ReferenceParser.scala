/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*
import Readability.*

private[parsing] trait ReferenceParser extends CommonParser {

  private def adaptorRef[u: P]: P[AdaptorRef] = {
    P(location ~ Keywords.adaptor ~ pathIdentifier)
      .map(tpl => AdaptorRef.apply.tupled(tpl))
  }

  private def commandRef[u: P]: P[CommandRef] = {
    P(location ~ Keywords.command ~ pathIdentifier)
      .map(tpl => CommandRef.apply.tupled(tpl))
  }

  private def eventRef[u: P]: P[EventRef] = {
    P(location ~ Keywords.event ~ pathIdentifier)
      .map(tpl => EventRef.apply.tupled(tpl))
  }

  private def queryRef[u: P]: P[QueryRef] = {
    P(location ~ Keywords.query ~ pathIdentifier)
      .map(tpl => QueryRef.apply.tupled(tpl))
  }

  private def resultRef[u: P]: P[ResultRef] = {
    P(location ~ Keywords.result ~ pathIdentifier)
      .map(tpl => ResultRef.apply.tupled(tpl))
  }

  private def recordRef[u: P]: P[RecordRef] = {
    P(location ~ Keywords.record ~ pathIdentifier)
      .map(tpl => RecordRef.apply.tupled(tpl))
  }

  def messageRef[u: P]: P[MessageRef] = {
    P(commandRef | eventRef | queryRef | resultRef | recordRef)
  }

  def entityRef[u: P]: P[EntityRef] = {
    P(location ~ Keywords.entity ~ pathIdentifier)
      .map(tpl => EntityRef.apply.tupled(tpl))
  }

  def functionRef[u: P]: P[FunctionRef] = {
    P(location ~ Keywords.function ~ pathIdentifier)
      .map(tpl => FunctionRef.apply.tupled(tpl))
  }

  def handlerRef[u: P]: P[HandlerRef] = {
    P(location ~ Keywords.handler ~ pathIdentifier)
      .map(tpl => HandlerRef.apply.tupled(tpl))
  }

  def stateRef[u: P]: P[StateRef] = {
    P(location ~ Keywords.state ~ pathIdentifier)
      .map(tpl => StateRef.apply.tupled(tpl))
  }

  def typeRef[u: P]: P[TypeRef] = {
    P(
      location ~ Keywords.typeKeywords.? ~ pathIdentifier
    ).map {
      case (loc, Some(key), pid) => TypeRef(loc, key, pid)
      case (loc, None, pid)      => TypeRef(loc, "type", pid)
    }
  }

  def fieldRef[u: P]: P[FieldRef] = {
    P(location ~ Keywords.field ~ pathIdentifier)
      .map(tpl => FieldRef.apply.tupled(tpl))
  }

  def constantRef[u: P]: P[ConstantRef] = {
    P(location ~ Keywords.constant ~ pathIdentifier)
      .map(tpl => ConstantRef.apply.tupled(tpl))
  }

  def contextRef[u: P]: P[ContextRef] = {
    P(location ~ Keywords.context ~ pathIdentifier)
      .map(tpl => ContextRef.apply.tupled(tpl))
  }

  def outletRef[u: P]: P[OutletRef] = {
    P(location ~ Keywords.outlet ~ pathIdentifier)
      .map(tpl => OutletRef.apply.tupled(tpl))
  }

  def inletRef[u: P]: P[InletRef] = {
    P(location ~ Keywords.inlet ~ pathIdentifier)
      .map(tpl => InletRef.apply.tupled(tpl))
  }

  private def streamletRef[u: P]: P[StreamletRef] = {
    P(
      location ~ Keywords.streamlets ~ pathIdentifier
    ).map(tpl => StreamletRef.apply.tupled(tpl))
  }

  private def projectorRef[u: P]: P[ProjectorRef] = {
    P(location ~ Keywords.projector ~ pathIdentifier)
      .map(tpl => ProjectorRef.apply.tupled(tpl))
  }

  def repositoryRef[u: P]: P[RepositoryRef] = {
    P(location ~ Keywords.repository ~ pathIdentifier)
      .map(tpl => RepositoryRef.apply.tupled(tpl))
  }

  private def sagaRef[u: P]: P[SagaRef] = {
    P(location ~ Keywords.saga ~ pathIdentifier)
      .map(tpl => SagaRef.apply.tupled(tpl))
  }

  def epicRef[u: P]: P[EpicRef] = {
    P(location ~ Keywords.epic ~ pathIdentifier)
      .map(tpl => EpicRef.apply.tupled(tpl))
  }

  def userRef[u: P]: P[UserRef] = {
    P(location ~ Keywords.user ~ pathIdentifier)
      .map(tpl => UserRef.apply.tupled(tpl))
  }

  private def applicationRef[u: P]: P[ApplicationRef] = {
    P(location ~ Keywords.application ~ pathIdentifier)
      .map(tpl => ApplicationRef.apply.tupled(tpl))
  }

  def outputRef[u: P]: P[OutputRef] = {
    P(location ~ outputAliases ~ pathIdentifier)
      .map { case (loc, keyword, pid) => OutputRef(loc, keyword, pid) }
  }

  def inputRef[u: P]: P[InputRef] = {
    P(location ~ inputAliases ~ pathIdentifier)
      .map { case (loc, keyword, pid) => InputRef(loc, keyword, pid) }
  }

  def groupRef[u: P]: P[GroupRef] = {
    P(location ~ groupAliases ~ pathIdentifier)
      .map { case (loc, keyword, pid) => GroupRef(loc, keyword, pid) }
  }

  def authorRef[u: P]: P[AuthorRef] = {
    P(
      location ~ by ~ Keywords.author ~ pathIdentifier
    ).map(tpl => AuthorRef.apply.tupled(tpl))
  }

  def processorRef[u: P]: P[ProcessorRef[Processor[?, ?]]] = {
    P(
      adaptorRef | applicationRef | contextRef | entityRef | projectorRef |
        repositoryRef | streamletRef
    )
  }

  private def arbitraryInteractionRef[u: P]: P[Reference[Definition]] = {
    P(processorRef | sagaRef | inputRef | outputRef | groupRef)
  }

  def anyInteractionRef[u: P]: P[Reference[Definition]] = {
    arbitraryInteractionRef | userRef
  }
}
