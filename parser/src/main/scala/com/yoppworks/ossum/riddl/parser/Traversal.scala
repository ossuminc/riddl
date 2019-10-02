package com.yoppworks.ossum.riddl.parser

import cats.Monoid
import com.yoppworks.ossum.riddl.parser.AST._

/** Traversal Module */
object Traversal {

  trait DefTraveler[P, D <: Def] {
    def traverse: P
    protected def open(d: D): P
    protected def close(): P
    protected def visitExplanation(exp: Option[Explanation]): Unit
  }

  abstract class DomainTraveler[P](
    domain: DomainDef
  ) extends DefTraveler[P, DomainDef] {
    final def traverse: P = {
      open(domain)
      domain.types.foreach(visitType)
      domain.channels.foreach(visitChannel)
      domain.interactions.foreach(visitInteraction(_).traverse)
      domain.contexts.foreach(visitContext(_).traverse)
      visitExplanation(domain.explanation)
      close()
    }

    def visitType(typ: TypeDef): Unit
    def visitChannel(chan: ChannelDef): Unit
    def visitInteraction(i: InteractionDef): InteractionTraveler[P]
    def visitContext(context: ContextDef): ContextTraveler[P]
  }

  abstract class ContextTraveler[P](context: ContextDef)
      extends DefTraveler[P, ContextDef] {
    final def traverse: P = {
      open(context)
      context.types.foreach(visitType)
      context.commands.foreach(visitCommand)
      context.events.foreach(visitEvent)
      context.queries.foreach(visitQuery)
      context.results.foreach(visitResult)
      context.adaptors.foreach(visitAdaptor(_).traverse)
      context.interactions.foreach(visitInteraction(_).traverse)
      context.entities.foreach(visitEntity(_).traverse)
      close()
    }
    def visitType(ty: TypeDef): Unit
    def visitCommand(command: CommandDef): Unit
    def visitEvent(event: EventDef): Unit
    def visitQuery(query: QueryDef): Unit
    def visitResult(result: ResultDef): Unit
    def visitAdaptor(a: AdaptorDef): AdaptorTraveler[P]
    def visitInteraction(i: InteractionDef): InteractionTraveler[P]
    def visitEntity(e: EntityDef): EntityTraveler[P]
  }

  abstract class AdaptorTraveler[P](adaptor: AdaptorDef)
      extends DefTraveler[P, AdaptorDef] {
    final def traverse: P = {
      val t = open(adaptor)
      close()
    }
  }

  abstract class EntityTraveler[P](entity: EntityDef)
      extends DefTraveler[P, EntityDef] {
    final def traverse: P = {
      open(entity)
      entity.invariants.foreach(visitInvariant)
      entity.features.foreach(visitFeature)
      visitExplanation(entity.explanation)
      close()
    }

    def visitInvariant(i: InvariantDef): Unit
    def visitFeature(f: FeatureDef): Unit
  }

  abstract class InteractionTraveler[P](
    interaction: InteractionDef
  ) extends DefTraveler[P, InteractionDef] {
    final def traverse: P = {
      open(interaction)
      interaction.roles.foreach(visitRole)
      interaction.actions.foreach(visitAction)
      close()
    }
    def visitRole(role: RoleDef): Unit
    def visitAction(action: ActionDef): Unit
  }

  def traverse[T <: Monoid[_]](
    domains: Domains
  )(f: DomainDef ⇒ DomainTraveler[T]): Seq[T] = {
    domains.map { domain ⇒
      f(domain).traverse
    }
  }

  def traverse[T <: Monoid[_]](
    context: ContextDef
  )(f: ContextDef => ContextTraveler[T]): T = {
    f(context).traverse
  }

  def traverse[D <: Def, T <: Monoid[_]](
    ast: AST
  )(f: AST ⇒ DefTraveler[T, D]): T = {
    f(ast).traverse
  }
}
