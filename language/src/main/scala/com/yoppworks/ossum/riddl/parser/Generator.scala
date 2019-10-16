package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import com.yoppworks.ossum.riddl.parser.Traversal._

import scala.collection.mutable

/** This is the RIDDL generator to convert an AST back to RIDDL plain text */
object Generator {

  type Lines = mutable.StringBuilder

  object Lines {

    def apply(s: String = ""): Lines = {
      val lines = new mutable.StringBuilder(s)
      lines.append(s)
      lines
    }
  }

  def fromDomain(domain: DomainDef): Lines = {
    DomainGenerator(domain).traverse
  }

  abstract class GeneratorBase[D <: Definition](definition: D)
      extends DefTraveler[Lines, D] {
    def payload: Lines

    def indent: Int

    val spc: String = " ".repeat(indent)

    protected def visitExplanation(
      maybeExplanation: Option[Explanation]
    ): Unit = {
      maybeExplanation.foreach { explanation =>
        payload.append(" explained as {\n")
        explanation.markdown.foreach { line =>
          payload.append(spc + "  \"" + line + "\"\n")
        }
        payload.append(spc + "}")
      }
    }

    protected def visitSeeAlso(
      maybeSeeAlso: Option[SeeAlso]
    ): Unit = {
      maybeSeeAlso.foreach { links =>
        payload.append(s" see also {\n")
        links.citations.foreach { line =>
          payload.append(spc + "  \"" + line + "\"\n")
        }
        payload.append(spc + "}")
      }
    }

    protected def visitTypeExpression(typEx: AST.TypeExpression): String = {
      typEx match {
        case TypeRef(_, id)     => id.value
        case pt: PredefinedType => pt.id.value
        case Optional(_, id)    => id.value + "?"
        case ZeroOrMore(_, id)  => id.value + "*"
        case OneOrMore(_, id)   => id.value + "+"
      }
    }

    override def close(): Unit = {
      payload.append(s"$spc}")
    }

    protected def terminus(): Lines = {
      payload.append("\n")
    }

  }

  case class TypeGenerator(typeDef: TypeDef, payload: Lines, indent: Int = 2)
      extends GeneratorBase[TypeDef](typeDef)
      with Traversal.TypeTraveler[Lines] {

    protected def open(): Unit = {
      payload.append(
        s"${spc}type ${typeDef.id.value} is "
      )
    }

    override def close(): Unit = {}

    def visitType(ty: Type): Unit = {
      ty match {
        case exp: TypeExpression =>
          payload.append(visitTypeExpression(exp))
        case dfn: TypeSpecification =>
          payload.append(visitTypeDefinition(dfn))
      }
    }

    def visitTypeDefinition(
      typeDef: AST.TypeSpecification
    ): String = {
      typeDef match {
        case AST.Enumeration(_, of) =>
          s"any [ ${of.map(_.value).mkString(" ")} ]"
        case AST.Alternation(_, of) =>
          s"choose ${of.map(_.id.value).mkString(" or ")}"
        case AST.Aggregation(_, of) =>
          s"combine {\n${of
            .map {
              case (k: Identifier, v: TypeExpression) =>
                s"$spc  ${k.value} is ${visitTypeExpression(v)}"
            }
            .mkString(s",\n")}\n$spc}"
      }
    }
  }

  case class DomainGenerator(
    domain: DomainDef,
    payload: Lines = Lines(),
    indent: Int = 0
  ) extends GeneratorBase[DomainDef](domain)
      with Traversal.DomainTraveler[Lines] {

    def open(): Unit = {
      payload.append(s"domain ${domain.id.value}")
      domain.subdomain.foreach(
        id => payload.append(s" is subdomain of ${id.value}")
      )
      payload.append(" {\n")
    }

    def visitType(
      typ: TypeDef
    ): Traversal.TypeTraveler[Lines] = {
      TypeGenerator(typ, payload, indent + 2)
    }

    def visitChannel(
      channel: ChannelDef
    ): Traversal.ChannelTraveler[Lines] = {
      ChannelGenerator(channel, payload, indent + 2)
    }

    def visitInteraction(
      i: InteractionDef
    ): Traversal.InteractionTraveler[Lines] = {
      InteractionGenerator(i, payload, indent + 2)
    }

    def visitContext(
      context: ContextDef
    ): Traversal.ContextTraveler[Lines] = {
      ContextGenerator(context, payload, indent + 2)
    }
  }

  case class ChannelGenerator(channel: ChannelDef, payload: Lines, indent: Int)
      extends GeneratorBase[ChannelDef](channel)
      with Traversal.ChannelTraveler[Lines] {

    def open(): Unit = {
      payload.append(s"${spc}channel ${channel.id.value} {\n")
    }

    def visitCommands(commands: Seq[CommandRef]): Unit = {
      payload.append(s"$spc  commands {")
      payload.append(
        commands.map(_.id.value).mkString(", ")
      )
      payload.append("}\n")
    }

    def visitEvents(events: Seq[EventRef]): Unit = {
      payload.append(s"$spc  events {")
      payload.append(
        events.map(_.id.value).mkString(", ")
      )
      payload.append("}\n")
    }

    def visitQueries(queries: Seq[QueryRef]): Unit = {
      payload.append(s"$spc  queries {")
      payload.append(
        queries.map(_.id.value).mkString(", ")
      )
      payload.append("}\n")
    }

    def visitResults(results: Seq[ResultRef]): Unit = {
      payload.append(s"$spc  results {")
      payload.append(
        results.map(_.id.value).mkString(", ")
      )
      payload.append("}\n")
    }
  }

  case class ContextGenerator(
    context: ContextDef,
    payload: Lines,
    indent: Int
  ) extends GeneratorBase[ContextDef](context)
      with Traversal.ContextTraveler[Lines] {

    def open(): Unit = {
      payload.append(s"${spc}context ${context.id.value} {\n")
    }

    def visitType(t: TypeDef): Traversal.TypeTraveler[Lines] = {
      TypeGenerator(t, payload, indent + 2)
    }

    def visitCommand(command: CommandDef): Unit = {
      payload.append(
        s"$spc  command ${command.id.value} is ${visitTypeExpression(command.typ)}"
      )
      val keyword = if (command.events.size > 1) "events" else "event"
      payload.append(
        s" yields $keyword ${command.events.map(_.id.value).mkString(", ")}\n"
      )
    }

    def visitEvent(event: EventDef): Unit = {
      payload.append(
        s"$spc  event ${event.id.value} is ${visitTypeExpression(event.typ)}\n"
      )
    }

    def visitQuery(query: QueryDef): Unit = {
      payload.append(
        s"$spc  query ${query.id.value} is ${visitTypeExpression(query.typ)}"
      )
      payload.append(s" yields result ${query.result.id.value}\n")
    }

    def visitResult(result: ResultDef): Unit = {
      payload.append(
        s"$spc  result ${result.id.value} is ${visitTypeExpression(result.typ)}\n"
      )
    }

    def visitAdaptor(
      a: AdaptorDef
    ): Traversal.AdaptorTraveler[Lines] =
      AdaptorGenerator(a, payload, indent + 2)

    def visitInteraction(
      i: InteractionDef
    ): Traversal.InteractionTraveler[Lines] =
      InteractionGenerator(i, payload, indent + 2)

    def visitEntity(e: EntityDef): Traversal.EntityTraveler[Lines] = {
      EntityGenerator(e, payload, indent + 2)
    }
  }

  case class EntityGenerator(entity: EntityDef, payload: Lines, indent: Int)
      extends GeneratorBase[EntityDef](entity)
      with Traversal.EntityTraveler[Lines] {

    def open(): Unit = {
      payload.append(
        s"${spc}entity ${entity.id.value} is ${visitTypeExpression(entity.typ)} {\n"
      )
      entity.options.size match {
        case 1 =>
          payload.append(s"$spc  option is ${entity.options.head.id.value}\n")
        case x: Int if x > 1 =>
          payload.append(s"$spc  options { ")
          payload.append(entity.options.map(_.id.value).mkString(" "))
          payload.append(" }\n")
        case _ =>
      }
    }

    def visitProducer(c: ChannelRef): Unit = {
      payload.append(s"$spc  produces channel ${c.id.value}\n")
    }

    def visitConsumer(c: ChannelRef): Unit = {
      payload.append(s"$spc  consumes channel ${c.id.value}\n")
    }

    def visitInvariant(i: InvariantDef): Unit = {
      payload.append(s"$spc  invariant ${i.id.value}")
    }

    def visitFeature(f: FeatureDef): FeatureTraveler[Lines] = {
      FeatureGenerator(f, payload, indent + 2)
    }
  }

  case class FeatureGenerator(feature: FeatureDef, payload: Lines, indent: Int)
      extends GeneratorBase[FeatureDef](feature)
      with Traversal.FeatureTraveler[Lines] {

    def open(): Unit = {
      payload.append(s"${spc}feature ${feature.id.value} {\n")
      payload.append(s"$spc  description {\n")
      feature.description.foreach(s => payload.append(spc + s + "\n"))
      payload.append(s"$spc}\n")
    }

    def visitBackground(background: Background): Unit = {}
    def visitExample(example: ExampleDef): Unit = {}
  }

  case class InteractionGenerator(
    interaction: InteractionDef,
    payload: Lines,
    indent: Int
  ) extends GeneratorBase[InteractionDef](interaction)
      with Traversal.InteractionTraveler[Lines] {

    def open(): Unit = {
      payload.append(s"${spc}interaction ${interaction.id.value} {\n")
    }

    def visitRole(role: RoleDef): Unit = {}

    def visitAction(action: ActionDef): Unit = {}
  }

  case class AdaptorGenerator(adaptor: AdaptorDef, payload: Lines, indent: Int)
      extends GeneratorBase[AdaptorDef](adaptor)
      with Traversal.AdaptorTraveler[Lines] {

    def open(): Unit = {
      payload.append(s"${spc}adaptor ${adaptor.id.value} {\n")
    }

  }
}
