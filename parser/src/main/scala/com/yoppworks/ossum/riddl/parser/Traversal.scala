package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._

/** Unit Tests For Traversal */
object Traversal {

  trait Visitor[T] {
    def visitDomain(d: DomainDef): DomainVisitor[T]
    def visitContext(c: ContextDef): ContextVisitor[T]
    def visitEntity(e: EntityDef): EntityVisitor[T]
    def visitInteraction(i: InteractionDef): InteractionVisitor[T]
    def visitAdaptor(a: AdaptorDef): AdaptorVisitor[T]
  }

  abstract class DefVisitor[T, D <: Def] {
    def traverse(visitor: Visitor[T]): T
    def open(d: D): Unit
    def close: T
  }

  abstract class EntityVisitor[T](entity: EntityDef)
      extends DefVisitor[T, EntityDef] {
    final def traverse(visitor: Visitor[T]): T = {
      open(entity)
      entity.invariants.foreach(visit)
      entity.features.foreach(visit)
      close
    }

    def visit(i: InvariantDef): Unit
    def visit(f: FeatureDef): Unit
  }

  abstract class AdaptorVisitor[T](adaptor: AdaptorDef)
      extends DefVisitor[T, AdaptorDef] {
    final def traverse(visitor: Visitor[T]): T = {
      open(adaptor)
      close
    }
  }

  abstract class InteractionVisitor[T](interaction: InteractionDef)
      extends DefVisitor[T, InteractionDef] {
    final def traverse(visitor: Visitor[T]): T = {
      open(interaction)
      interaction.roles.foreach(visit)
      interaction.actions.foreach(visit)
      close
    }
    def visit(role: RoleDef): Unit
    def visit(action: ActionDef): Unit
  }

  abstract class ContextVisitor[T](context: ContextDef)
      extends DefVisitor[T, ContextDef] {
    final def traverse(visitor: Visitor[T]): T = {
      open(context)
      context.types.foreach(visit)
      context.commands.foreach(visit)
      context.events.foreach(visit)
      context.queries.foreach(visit)
      context.results.foreach(visit)
      context.adaptors.foreach(Traversal.traverse(_, visitor))
      context.interactions.foreach(Traversal.traverse(_, visitor))
      close
    }
    def visit(ty: TypeDef): Unit
    def visit(command: CommandDef): Unit
    def visit(event: EventDef): Unit
    def visit(query: QueryDef): Unit
    def visit(result: ResultDef): Unit
  }

  abstract class DomainVisitor[T](domain: DomainDef)
      extends DefVisitor[T, DomainDef] {
    final def traverse(visitor: Visitor[T]): T = {
      open(domain)
      domain.types.foreach(visit)
      domain.channels.foreach(visit)
      domain.contexts.foreach(Traversal.traverse(_, visitor))
      domain.interactions.foreach(Traversal.traverse(_, visitor))
      close
    }

    def visit(ty: TypeDef): Unit
    def visit(ch: ChannelDef): Unit
  }

  def traverse[T](input: AST, visitor: Visitor[T]): T = {
    input match {
      case domain: DomainDef ⇒
        val v = visitor.visitDomain(domain)
        v.traverse(visitor)
      case context: ContextDef ⇒
        val v = visitor.visitContext(context)
        v.traverse(visitor)
      case entity: EntityDef ⇒
        val v = visitor.visitEntity(entity)
        v.traverse(visitor)
      case interaction: InteractionDef ⇒
        val v = visitor.visitInteraction(interaction)
        v.traverse(visitor)
      case adaptor: AdaptorDef ⇒
        val v = visitor.visitAdaptor(adaptor)
        v.traverse(visitor)
      case other: AST ⇒ ???
    }
  }
}
