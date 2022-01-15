package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.Keywords

import fastparse.P
import fastparse.ScalaWhitespace.*

trait ReferenceParser extends CommonParser {
  def commandRef[u: P]: P[CommandRef] = {
    P(location ~ Keywords.command ~/ pathIdentifier).map(tpl => (CommandRef.apply _).tupled(tpl))
  }

  def eventRef[u: P]: P[EventRef] = {
    P(location ~ Keywords.event ~/ pathIdentifier).map(tpl => (EventRef.apply _).tupled(tpl))
  }

  def queryRef[u: P]: P[QueryRef] = {
    P(location ~ Keywords.query ~/ pathIdentifier).map(tpl => (QueryRef.apply _).tupled(tpl))
  }

  def resultRef[u: P]: P[ResultRef] = {
    P(location ~ Keywords.result ~/ pathIdentifier).map(tpl => (ResultRef.apply _).tupled(tpl))
  }

  def messageRef[u: P]: P[MessageRef] = { P(commandRef | eventRef | queryRef | resultRef) }

  def entityRef[u: P]: P[EntityRef] = {
    P(location ~ Keywords.entity ~/ pathIdentifier).map(tpl => (EntityRef.apply _).tupled(tpl))
  }

  def typeRef[u: P]: P[TypeRef] = {
    P(location ~ pathIdentifier).map(tpl => (TypeRef.apply _).tupled(tpl))
  }

  def actionRef[u: P]: P[FunctionRef] = {
    P(location ~ Keywords.action ~/ pathIdentifier).map(tpl => (FunctionRef.apply _).tupled(tpl))
  }

  def contextRef[u: P]: P[ContextRef] = {
    P(location ~ Keywords.context ~/ pathIdentifier).map(tpl => (ContextRef.apply _).tupled(tpl))
  }

  def domainRef[u: P]: P[DomainRef] = {
    P(location ~ Keywords.domain ~/ pathIdentifier).map(tpl => (DomainRef.apply _).tupled(tpl))
  }

  def pipeRef[u: P]: P[PipeRef] = {
    P(location ~ Keywords.pipe ~/ pathIdentifier).map(tpl => (PipeRef.apply _).tupled(tpl))
  }

  def outletRef[u: P]: P[OutletRef] = {
    P(location ~ Keywords.outlet ~/ pathIdentifier).map(tpl => (OutletRef.apply _).tupled(tpl))
  }

  def inletRef[u: P]: P[InletRef] = {
    P(location ~ Keywords.inlet ~/ pathIdentifier).map(tpl => (InletRef.apply _).tupled(tpl))
  }
}
