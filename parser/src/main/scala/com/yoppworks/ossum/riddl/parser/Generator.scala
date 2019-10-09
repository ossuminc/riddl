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
    def lines: Lines

    def indent: Int

    val spc: String = " ".repeat(indent)

    final def visitAddendum(add: Option[Addendum]): Unit = {
      definition.addendum.foreach { x =>
        visitExplanation(x.explanation)
        visitSeeAlso(x.seeALso)
      }
    }

    protected def visitExplanation(
      maybeExplanation: Option[Explanation]
    ): Unit = {
      maybeExplanation.foreach { explanation =>
        lines.append(" explained as {\n")
        explanation.markdown.foreach { line =>
          lines.append(spc + "  \"" + line + "\"\n")
        }
        lines.append(spc + "}")
      }
    }

    protected def visitSeeAlso(
      maybeSeeAlso: Option[SeeAlso]
    ): Unit = {
      maybeSeeAlso.foreach { links =>
        lines.append(s" see also {\n")
        links.citations.foreach { line =>
          lines.append(spc + "  \"" + line + "\"\n")
        }
        lines.append(spc + "}")
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

    def close(): Lines = {
      visitAddendum(definition.addendum)
      lines.append("\n")
    }
  }

  case class TypeGenerator(typeDef: TypeDef, lines: Lines, indent: Int = 2)
      extends GeneratorBase[TypeDef](typeDef)
      with Traversal.TypeTraveler[Lines] {

    protected def open(): Lines = {
      lines.append(
        s"${spc}type ${typeDef.id.value} is "
      )
    }

    def visitType(ty: Type): Unit = {
      val text = ty match {
        case exp: TypeExpression => visitTypeExpression(exp)
        case dfn: TypeDefinition => visitTypeDefinition(dfn)
      }
      lines.append(text)
    }

    def visitTypeDefinition(
      typeDef: AST.TypeDefinition
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
    lines: Lines = Lines(),
    indent: Int = 0
  ) extends GeneratorBase[DomainDef](domain)
      with Traversal.DomainTraveler[Lines] {

    def open(): Lines = {
      lines.append(s"domain ${domain.id.value}")
      domain.subdomain.foreach(
        id => lines.append(s" is subdomain of ${id.value}")
      )
      lines.append(" {\n")
    }

    override def close(): Lines = {
      lines.append(s"$spc}")
      visitAddendum(domain.addendum)
      lines
    }

    def visitType(
      typ: TypeDef
    ): Traversal.TypeTraveler[Lines] = {
      TypeGenerator(typ, lines, indent + 2)
    }

    def visitChannel(
      channel: ChannelDef
    ): Traversal.ChannelTraveler[Lines] = {
      ChannelGenerator(channel, lines, indent + 2)
    }

    def visitInteraction(
      i: InteractionDef
    ): Traversal.InteractionTraveler[Lines] = {
      InteractionGenerator(i, lines, indent + 2)
    }

    def visitContext(
      context: ContextDef
    ): Traversal.ContextTraveler[Lines] = {
      ContextGenerator(context, lines, indent + 2)
    }
  }

  case class ChannelGenerator(channel: ChannelDef, lines: Lines, indent: Int)
      extends GeneratorBase[ChannelDef](channel)
      with Traversal.ChannelTraveler[Lines] {

    def open(): Lines = {
      lines.append(s"${spc}channel ${channel.id.value} {\n")
    }

    override def close(): Lines = {
      lines.append(s"$spc}")
      super.close()
    }

    def visitCommands(commands: Seq[CommandRef]): Unit = {
      lines.append(s"$spc  commands {")
      lines.append(
        commands.map(_.id.value).mkString(", ")
      )
      lines.append("}\n")
    }

    def visitEvents(events: Seq[EventRef]): Unit = {
      lines.append(s"$spc  events {")
      lines.append(
        events.map(_.id.value).mkString(", ")
      )
      lines.append("}\n")
    }

    def visitQueries(queries: Seq[QueryRef]): Unit = {
      lines.append(s"$spc  queries {")
      lines.append(
        queries.map(_.id.value).mkString(", ")
      )
      lines.append("}\n")
    }

    def visitResults(results: Seq[ResultRef]): Unit = {
      lines.append(s"$spc  results {")
      lines.append(
        results.map(_.id.value).mkString(", ")
      )
      lines.append("}\n")
    }
  }

  case class ContextGenerator(
    context: ContextDef,
    lines: Lines,
    indent: Int
  ) extends GeneratorBase[ContextDef](context)
      with Traversal.ContextTraveler[Lines] {

    def open(): Lines = {
      lines.append(s"${spc}context ${context.id.value} {\n")
    }

    override def close(): Lines = {
      lines.append(s"$spc}")
      super.close()
    }

    def visitType(t: TypeDef): Traversal.TypeTraveler[Lines] = {
      TypeGenerator(t, lines, indent + 2)
    }

    def visitCommand(command: CommandDef): Unit = {
      lines.append(
        s"$spc  command ${command.id.value} is ${visitTypeExpression(command.typ)}"
      )
      val keyword = if (command.events.size > 1) "events" else "event"
      lines.append(
        s" yields $keyword ${command.events.map(_.id.value).mkString(", ")}\n"
      )
    }

    def visitEvent(event: EventDef): Unit = {
      lines.append(
        s"$spc  event ${event.id.value} is ${visitTypeExpression(event.typ)}\n"
      )
    }

    def visitQuery(query: QueryDef): Unit = {
      lines.append(
        s"$spc  query ${query.id.value} is ${visitTypeExpression(query.typ)}"
      )
      lines.append(s" yields result ${query.result.id.value}\n")
    }

    def visitResult(result: ResultDef): Unit = {
      lines.append(
        s"$spc  result ${result.id.value} is ${visitTypeExpression(result.typ)}\n"
      )
    }

    def visitAdaptor(
      a: AdaptorDef
    ): Traversal.AdaptorTraveler[Lines] = AdaptorGenerator(a, lines, indent + 2)

    def visitInteraction(
      i: InteractionDef
    ): Traversal.InteractionTraveler[Lines] =
      InteractionGenerator(i, lines, indent + 2)

    def visitEntity(e: EntityDef): Traversal.EntityTraveler[Lines] = {
      EntityGenerator(e, lines, indent + 2)
    }
  }

  case class EntityGenerator(entity: EntityDef, lines: Lines, indent: Int)
      extends GeneratorBase[EntityDef](entity)
      with Traversal.EntityTraveler[Lines] {

    def open(): Lines = {
      lines.append(
        s"${spc}entity ${entity.id.value} is ${visitTypeExpression(entity.typ)} {\n"
      )
      entity.options.size match {
        case 1 =>
          lines.append(s"$spc  option is ${entity.options.head.id.value}\n")
        case x: Int if x > 1 =>
          lines.append(s"$spc  options { ")
          lines.append(entity.options.map(_.id.value).mkString(" "))
          lines.append(" }\n")
        case _ =>
      }
      lines
    }

    override def close(): Lines = {
      lines.append(s"$spc}")
      super.close()
    }

    def visitProducer(c: ChannelRef): Unit = {
      lines.append(s"$spc  produces channel ${c.id.value}\n")
    }

    def visitConsumer(c: ChannelRef): Unit = {
      lines.append(s"$spc  consumes channel ${c.id.value}\n")
    }

    def visitInvariant(i: InvariantDef): Unit = {
      lines.append(s"$spc  invariant ${i.id.value}")
    }

    def visitFeature(f: FeatureDef): FeatureTraveler[Lines] = {
      FeatureGenerator(f, lines, indent + 2)
    }
  }

  case class FeatureGenerator(feature: FeatureDef, lines: Lines, indent: Int)
      extends GeneratorBase[FeatureDef](feature)
      with Traversal.FeatureTraveler[Lines] {

    def open(): Lines = {
      lines.append(s"${spc}feature ${feature.id.value} {\n")
      lines.append(s"$spc  description {\n")
      feature.description.foreach(s => lines.append(spc + s + "\n"))
      lines.append(s"$spc}\n")
    }

    override def close(): Lines = {
      lines.append(s"$spc}")
      super.close()
    }

    def visitBackground(background: Background): Unit = {}
    def visitExample(example: ExampleDef): Unit = {}
  }

  case class InteractionGenerator(
    interaction: InteractionDef,
    lines: Lines,
    indent: Int
  ) extends GeneratorBase[InteractionDef](interaction)
      with Traversal.InteractionTraveler[Lines] {

    def open(): Lines = {
      lines.append(s"${spc}interaction ${interaction.id.value} {\n")
    }

    override def close(): Lines = {
      lines.append(s"$spc}")
      super.close()
    }

    def visitRole(role: RoleDef): Unit = {}

    def visitAction(action: ActionDef): Unit = {}
  }

  case class AdaptorGenerator(adaptor: AdaptorDef, lines: Lines, indent: Int)
      extends GeneratorBase[AdaptorDef](adaptor)
      with Traversal.AdaptorTraveler[Lines] {

    def open(): Lines = {
      lines.append(s"${spc}adaptor ${adaptor.id.value} {\n")
    }

    override def close(): Lines = {
      lines.append(s"$spc}")
      super.close()
    }
  }
}
