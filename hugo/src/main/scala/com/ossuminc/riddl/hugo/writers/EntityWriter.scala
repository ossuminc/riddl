package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.hugo.diagrams.mermaid.EntityRelationshipDiagram
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.symbols.Symbols.Parents

import scala.annotation.unused

trait EntityWriter { this: MarkdownWriter =>

  private def emitState(
    state: State,
    parents: Parents
  ): this.type = {
    h2(state.identify)
    emitDefDoc(state, parents)
    val maybeType = refMap.definitionOf[Type](state.typ.pathId, state)
    val fields = maybeType match {
      case Some(typ: AggregateTypeExpression) => typ.fields
      case Some(_)                            => Seq.empty[Field]
      case None                               => Seq.empty[Field]
    }
    emitERD(state.id.format, fields, parents)
    h2("Fields")
    emitFields(fields)
    for h <- state.handlers do emitHandler(h, state +: parents)
    emitUsage(state)
  }

  def emitHandler(handler: Handler, parents: Parents, level: Int = 3): Unit = {
    heading(handler.identify, level)
    emitDefDoc(handler, parents)
    emitUsage(handler)
    handler.clauses.foreach { clause =>
      clause match {
        case oic: OnInitClause => heading("Initialize", level + 1)
        case omc: OnMessageClause => heading(" On " + omc.msg.format, level + 1)
        case otc: OnTerminationClause => heading("Terminate", level + 1)
        case ooc: OnOtherClause => heading("Other", level + 1)
      }
      codeBlock(clause.statements)
    }
  }

  private def emitERD(
    name: String,
    fields: Seq[Field],
    parents: Seq[Definition]
  ): Unit = {
    h2("Entity Relationships")
    val erd = EntityRelationshipDiagram(refMap)
    val lines = erd.generate(name, fields, parents)
    emitMermaidDiagram(lines)
  }

  private def emitFiniteStateMachine(@unused entity: Entity): Unit = ()

  def emitEntity(entity: Entity, parents: Parents): Unit = {
    containerHead(entity, "Entity")
    emitDefDoc(entity, parents)
    emitOptions(entity.options)
    if entity.hasOption[EntityIsFiniteStateMachine] then {
      h2("Finite State Machine")
      emitFiniteStateMachine(entity)
    }
    emitInvariants(entity.invariants)
    emitTypes(entity, entity +: parents)
    for state <- entity.states do emitState(state, entity +: parents)
    for handler <- entity.handlers do emitHandler(handler, entity +: parents)
    for function <- entity.functions do emitFunction(function, entity +: parents)
    emitProcessorDetails(entity, parents)
    emitUsage(entity)
    emitTerms(entity.terms)
  }

}
