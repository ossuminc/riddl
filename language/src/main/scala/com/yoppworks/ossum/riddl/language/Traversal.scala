package com.yoppworks.ossum.riddl.language

import AST._

/** Traversal Module */
object Traversal {

  trait DefTraveler[P, D <: Definition] {
    def definition: D
    def traverse: P
    protected def payload: P
    protected def open(): Unit
    protected def close(): Unit
    protected def terminus(): P
    protected def visitExplanation(exp: Explanation): Unit
    protected def visitSeeAlso(exp: SeeAlso): Unit

    def visitAddendum(add: Option[Addendum]): Unit = {
      definition.addendum.foreach { x =>
        x.explanation.foreach(visitExplanation)
        x.seeAlso.foreach(visitSeeAlso)
      }
    }
  }

  trait DomainTraveler[P] extends DefTraveler[P, DomainDef] {
    def domain: DomainDef
    final def definition: DomainDef = domain
    final def traverse: P = {
      open()
      domain.types.foreach(visitType(_).traverse)
      domain.interactions.foreach(visitInteraction(_).traverse)
      domain.contexts.foreach(visitContext(_).traverse)
      close()
      visitAddendum(domain.addendum)
      terminus()
    }

    def visitType(typ: AST.TypeDef): TypeTraveler[P]
    def visitInteraction(i: InteractionDef): InteractionTraveler[P]
    def visitContext(context: ContextDef): ContextTraveler[P]
  }

  trait TypeTraveler[P] extends DefTraveler[P, AST.TypeDef] {
    def typeDef: AST.TypeDef
    final def definition: AST.TypeDef = typeDef
    final def traverse: P = {
      open()
      visitType(typeDef.typ)
      close()
      visitAddendum(typeDef.addendum)
      terminus()
    }
    def visitType(ty: AST.TypeValue): Unit
  }

  trait ChannelTraveler[P] extends DefTraveler[P, ChannelDef] {
    def channel: ChannelDef
    final def definition: ChannelDef = channel
    final def traverse: P = {
      open()
      visitCommands(channel.commands)
      visitEvents(channel.events)
      visitQueries(channel.queries)
      visitResults(channel.results)
      close()
      visitAddendum(channel.addendum)
      terminus()
    }
    def visitCommands(commands: Seq[CommandRef]): Unit
    def visitEvents(events: Seq[EventRef]): Unit
    def visitQueries(queries: Seq[QueryRef]): Unit
    def visitResults(results: Seq[ResultRef]): Unit
  }

  trait ContextTraveler[P] extends DefTraveler[P, ContextDef] {
    def context: ContextDef
    final def definition: ContextDef = context

    final def traverse: P = {
      open()
      context.types.foreach(visitType(_).traverse)
      context.commands.foreach(visitCommand)
      context.events.foreach(visitEvent)
      context.queries.foreach(visitQuery)
      context.results.foreach(visitResult)
      context.channels.foreach(visitChannel(_).traverse)
      context.adaptors.foreach(visitAdaptor(_).traverse)
      context.interactions.foreach(visitInteraction(_).traverse)
      context.entities.foreach(visitEntity(_).traverse)
      close()
      visitAddendum(context.addendum)
      terminus()
    }
    def visitType(ty: AST.TypeDef): TypeTraveler[P]
    def visitCommand(command: CommandDef): Unit
    def visitEvent(event: EventDef): Unit
    def visitQuery(query: QueryDef): Unit
    def visitResult(result: ResultDef): Unit
    def visitChannel(chan: ChannelDef): ChannelTraveler[P]
    def visitAdaptor(a: AdaptorDef): AdaptorTraveler[P]
    def visitInteraction(i: InteractionDef): InteractionTraveler[P]
    def visitEntity(e: EntityDef): EntityTraveler[P]
  }

  trait AdaptorTraveler[P] extends DefTraveler[P, AdaptorDef] {
    def adaptor: AdaptorDef
    final def definition: AdaptorDef = adaptor
    final def traverse: P = {
      open()
      close()
      visitAddendum(adaptor.addendum)
      terminus()
    }
  }

  trait EntityTraveler[P] extends DefTraveler[P, EntityDef] {
    def entity: EntityDef
    final def definition: EntityDef = entity
    final def traverse: P = {
      open()
      entity.consumes.foreach(visitConsumer)
      entity.produces.foreach(visitProducer)
      entity.invariants.foreach(visitInvariant)
      entity.features.foreach(visitFeature)
      close()
      visitAddendum(entity.addendum)
      terminus()
    }
    def visitProducer(p: ChannelRef): Unit
    def visitConsumer(c: ChannelRef): Unit
    def visitInvariant(i: InvariantDef): Unit
    def visitFeature(f: FeatureDef): FeatureTraveler[P]
  }

  trait FeatureTraveler[P] extends DefTraveler[P, FeatureDef] {
    def feature: FeatureDef
    final def definition: FeatureDef = feature
    final def traverse: P = {
      open()
      feature.background.foreach(visitBackground)
      feature.examples.foreach(visitExample)
      close()
      visitAddendum(feature.addendum)
      terminus()
    }
    def visitExample(example: ExampleDef): Unit
    def visitBackground(background: Background): Unit
  }

  trait InteractionTraveler[P] extends DefTraveler[P, InteractionDef] {
    def interaction: InteractionDef
    final def definition: InteractionDef = interaction
    final def traverse: P = {
      open()
      interaction.roles.foreach(visitRole)
      interaction.actions.foreach(visitAction)
      close()
      visitAddendum(interaction.addendum)
      terminus()
    }
    def visitRole(role: RoleDef): Unit
    def visitAction(action: ActionDef): Unit
  }

  def traverse[T](
    root: RootContainer
  )(f: ContainingDefinition => DomainTraveler[T]): Seq[T] = {
    root.content.map { container =>
      f(container).traverse
    }
  }

  def traverse[T](
    context: ContextDef
  )(f: ContextDef => ContextTraveler[T]): T = {
    f(context).traverse
  }

  def traverse[D <: Definition, T](
    ast: RiddlNode
  )(f: RiddlNode => DefTraveler[T, D]): T = {
    f(ast).traverse
  }
}
