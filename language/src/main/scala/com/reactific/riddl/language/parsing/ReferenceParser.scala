/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Terminals.Keywords
import fastparse.P
import fastparse.ScalaWhitespace.*

trait ReferenceParser extends CommonParser {

  def maybe[u: P](keyword: String): P[Unit] = P(keyword).?

  def commandRef[u: P]: P[CommandRef] = {
    P(location ~ Keywords.command ~ pathIdentifier).map(tpl => (CommandRef.apply _).tupled(tpl))
  }

  def eventRef[u: P]: P[EventRef] = {
    P(location ~ Keywords.event ~ pathIdentifier).map(tpl => (EventRef.apply _).tupled(tpl))
  }

  def queryRef[u: P]: P[QueryRef] = {
    P(location ~ Keywords.query ~ pathIdentifier).map(tpl => (QueryRef.apply _).tupled(tpl))
  }

  def resultRef[u: P]: P[ResultRef] = {
    P(location ~ Keywords.result ~ pathIdentifier).map(tpl => (ResultRef.apply _).tupled(tpl))
  }

  def messageRef[u: P]: P[MessageRef] = { P(commandRef | eventRef | queryRef | resultRef) }

  def entityRef[u: P]: P[EntityRef] = {
    P(location ~ maybe(Keywords.entity) ~ pathIdentifier)
      .map(tpl => (EntityRef.apply _).tupled(tpl))
  }

  def handlerRef[u: P]: P[HandlerRef] = {
    P(location ~ Keywords.handler ~ pathIdentifier).map(tpl => (HandlerRef.apply _).tupled(tpl))
  }

  def stateRef[u: P]: P[StateRef] = {
    P(location ~ Keywords.state ~ pathIdentifier).map(tpl => (StateRef.apply _).tupled(tpl))
  }

  def typeRef[u: P]: P[TypeRef] = {
    P(location ~ maybe(Keywords.`type`) ~ pathIdentifier).map(tpl => (TypeRef.apply _).tupled(tpl))
  }

  def actionRef[u: P]: P[FunctionRef] = {
    P(location ~ maybe(Keywords.action) ~ pathIdentifier)
      .map(tpl => (FunctionRef.apply _).tupled(tpl))
  }

  def contextRef[u: P]: P[ContextRef] = {
    P(location ~ maybe(Keywords.context) ~ pathIdentifier)
      .map(tpl => (ContextRef.apply _).tupled(tpl))
  }

  def domainRef[u: P]: P[DomainRef] = {
    P(location ~ maybe(Keywords.domain) ~ pathIdentifier)
      .map(tpl => (DomainRef.apply _).tupled(tpl))
  }

  def pipeRef[u: P]: P[PipeRef] = {
    P(location ~ maybe(Keywords.pipe) ~ pathIdentifier).map(tpl => (PipeRef.apply _).tupled(tpl))
  }

  def outletRef[u: P]: P[OutletRef] = {
    P(location ~ Keywords.outlet ~ pathIdentifier).map(tpl => (OutletRef.apply _).tupled(tpl))
  }

  def inletRef[u: P]: P[InletRef] = {
    P(location ~ Keywords.inlet ~ pathIdentifier).map(tpl => (InletRef.apply _).tupled(tpl))
  }
}
