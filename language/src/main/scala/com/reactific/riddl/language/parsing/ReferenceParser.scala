/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.P
import fastparse.StringIn
import fastparse.ScalaWhitespace.*

import scala.reflect.ClassTag
import scala.reflect.classTag

trait ReferenceParser extends CommonParser {

  def adaptorRef[u: P]: P[AdaptorRef] = {
    P(location ~ Keywords.adaptor ~ pathIdentifier)
      .map(tpl => (AdaptorRef.apply _).tupled(tpl))
  }
  def commandRef[u: P]: P[CommandRef] = {
    P(location ~ Keywords.command ~ pathIdentifier)
      .map(tpl => (CommandRef.apply _).tupled(tpl))
  }

  def eventRef[u: P]: P[EventRef] = {
    P(location ~ Keywords.event ~ pathIdentifier)
      .map(tpl => (EventRef.apply _).tupled(tpl))
  }

  def queryRef[u: P]: P[QueryRef] = {
    P(location ~ Keywords.query ~ pathIdentifier)
      .map(tpl => (QueryRef.apply _).tupled(tpl))
  }

  def resultRef[u: P]: P[ResultRef] = {
    P(location ~ Keywords.result ~ pathIdentifier)
      .map(tpl => (ResultRef.apply _).tupled(tpl))
  }

  def otherRef[u: P]: P[OtherRef] = {
    P(location ~ Keywords.other).map(OtherRef)
  }

  def messageRef[u: P]: P[MessageRef] = {
    P(commandRef | eventRef | queryRef | resultRef | otherRef)
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
    P(location ~ maybe(Keywords.`type`) ~ pathIdentifier)
      .map(tpl => (TypeRef.apply _).tupled(tpl))
  }

  def contextRef[u: P]: P[ContextRef] = {
    P(location ~ Keywords.context ~ pathIdentifier)
      .map(tpl => (ContextRef.apply _).tupled(tpl))
  }

  def domainRef[u: P]: P[DomainRef] = {
    P(location ~ Keywords.domain ~ pathIdentifier)
      .map(tpl => (DomainRef.apply _).tupled(tpl))
  }

  def pipeRef[u: P]: P[PipeRef] = {
    P(location ~ Keywords.pipe ~ pathIdentifier)
      .map(tpl => (PipeRef.apply _).tupled(tpl))
  }

  def outletRef[u: P]: P[OutletRef] = {
    P(location ~ Keywords.outlet ~ pathIdentifier)
      .map(tpl => (OutletRef.apply _).tupled(tpl))
  }

  def inletRef[u: P]: P[InletRef] = {
    P(location ~ Keywords.inlet ~ pathIdentifier)
      .map(tpl => (InletRef.apply _).tupled(tpl))
  }

  def processorRef[u: P]: P[ProcessorRef] = {
    P(
      location ~ StringIn(
        Keywords.source,
        Keywords.sink,
        Keywords.merge,
        Keywords.split,
        Keywords.void
      ) ~ pathIdentifier
    ).map(tpl => (ProcessorRef.apply _).tupled(tpl))
  }

  def projectionRef[u: P]: P[ProjectionRef] = {
    P(location ~ Keywords.projection ~ pathIdentifier)
      .map(tpl => (ProjectionRef.apply _).tupled(tpl))
  }

  def sagaRef[u: P]: P[SagaRef] = {
    P(location ~ Keywords.saga ~ pathIdentifier)
      .map(tpl => (SagaRef.apply _).tupled(tpl))
  }

  def storyRef[u: P]: P[StoryRef] = {
    P(location ~ Keywords.story ~ pathIdentifier)
      .map(tpl => (StoryRef.apply _).tupled(tpl))
  }

  def actorRef[u: P]: P[ActorRef] = {
    P(location ~ Keywords.actor ~ pathIdentifier)
      .map(tpl => (ActorRef.apply _).tupled(tpl))
  }

  def applicationRef[u: P]: P[ApplicationRef] = {
    P(location ~ Keywords.application ~ pathIdentifier)
      .map(tpl => (ApplicationRef.apply _).tupled(tpl))
  }

  def outputRef[u: P]: P[OutputRef] = {
    P(location ~ Keywords.output ~ pathIdentifier)
      .map(tpl => (OutputRef.apply _).tupled(tpl))
  }

  def inputRef[u: P]: P[InputRef] = {
    P(location ~ Keywords.input ~ pathIdentifier)
      .map(tpl => (InputRef.apply _).tupled(tpl))
  }

  def reference[u: P, K <: Definition: ClassTag]: P[Reference[K]] = {
    val clazz = classTag[K].runtimeClass
    val context = classOf[Context]
    val entity = classOf[Entity]
    val state = classOf[State]
    val projection = classOf[Projection]
    clazz match {
      case c: Class[?] if c == context =>
        contextRef.asInstanceOf[P[Reference[K]]]
      case c: Class[?] if c == entity => entityRef.asInstanceOf[P[Reference[K]]]
      case c: Class[?] if c == state  => stateRef.asInstanceOf[P[Reference[K]]]
      case c: Class[?] if c == projection =>
        projectionRef.asInstanceOf[P[Reference[K]]]
    }
  }

}
