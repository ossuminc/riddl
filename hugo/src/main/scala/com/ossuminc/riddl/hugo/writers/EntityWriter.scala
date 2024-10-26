/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.diagrams.mermaid.EntityRelationshipDiagram

import scala.annotation.unused

trait EntityWriter { this: MarkdownWriter =>

  private def emitState(
    state: State,
    parents: Parents
  ): Unit = {
    h2(state.identify)
    emitDefDoc(state, parents)
    val maybeType = generator.refMap.definitionOf[Type](state.typ.pathId, parents.head)
    val fields = maybeType match {
      case Some(typ: AggregateTypeExpression) => typ.fields
      case Some(_)                            => Seq.empty[Field]
      case None                               => Seq.empty[Field]
    }
    emitERD(state.id.format, fields, parents)
    h3("Fields")
    emitFields(fields)
  }

  def emitHandler(handler: Handler, parents: Parents, level: Int = 3): Unit = {
    heading(handler.identify, level)
    emitDefDoc(handler, parents)
    handler.clauses.foreach { clause =>
      clause match {
        case oic: OnInitializationClause => heading("Initialize", level + 1)
        case omc: OnMessageClause => heading(" On " + omc.msg.format, level + 1)
        case otc: OnTerminationClause => heading("Terminate", level + 1)
        case ooc: OnOtherClause => heading("Other", level + 1)
      }
      codeBlock(clause.contents.filter[Statement])
    }
  }

  private def emitERD(
    name: String,
    fields: Seq[Field],
    parents: Parents,
  ): Unit = {
    h3("Entity Relationships")
    val erd = EntityRelationshipDiagram(generator.refMap)
    val lines = erd.generate(name, fields, parents.head)
    emitMermaidDiagram(lines)
  }

  private def emitFiniteStateMachine(@unused entity: Entity): Unit = ()

  def emitEntity(entity: Entity, parents: Parents): Unit = {
    containerHead(entity)
    emitVitalDefinitionDetails(entity, parents)
    if entity.hasOption("finite-state-machine") then {
      h2("Finite State Machine")
      emitFiniteStateMachine(entity)
    }
    emitInvariants(entity.invariants)
    emitTypes(entity.types, entity +: parents)
    for state <- entity.states do emitState(state, entity +: parents)
    for handler <- entity.handlers do emitHandler(handler, entity +: parents)
    for function <- entity.functions do emitFunction(function, entity +: parents)
    emitProcessorDetails(entity, parents)
  }

}
