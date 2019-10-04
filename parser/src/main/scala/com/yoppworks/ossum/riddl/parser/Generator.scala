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

  abstract class GeneratorBase[D <: Def](definition: D)
      extends DefTraveler[Lines, D] {
    def lines: Lines

    def indent: Int

    val spc: String = " ".repeat(indent)

    protected def visitExplanation(
      maybeExplanation: Option[Explanation]
    ): Unit = {
      maybeExplanation.foreach { explanation ⇒
        lines.append(" explained as {\n")
        explanation.markdown.foreach { line ⇒
          lines.append(spc + "  \"" + line + "\"\n")
        }
        lines.append(spc + "}")
        if (explanation.links.isDefined) {
          explanation.links.foreach { links ⇒
            lines.append(s" referencing {\n")
            lines.append(
              links.map(link ⇒ spc + "  \"" + link.url.s + "\"").mkString(",\n")
            )
            lines.append("\n" + spc + "}")
          }
        } else {
          lines.append("\n")
        }
      }
    }

    protected def visitTypeExpression(typEx: AST.TypeExpression): String = {
      typEx match {
        case AST.PredefinedType(id) ⇒ id.toString
        case AST.TypeRef(id) ⇒ s"$id"
        case AST.Optional(ref) ⇒ s"$ref?"
        case AST.ZeroOrMore(ref) ⇒ s"$ref*"
        case AST.OneOrMore(ref) ⇒ s"$ref+"
      }
    }

    def close(): Lines = {
      lines.append(s"$spc}")
      visitExplanation(definition.explanation)
      lines.append("\n")
    }

  }

  case class TypeGenerator(typ: TypeDef, lines: Lines, indent: Int = 2)
      extends GeneratorBase[TypeDef](typ)
      with Traversal.TypeTraveler[Lines] {

    protected def open(): Lines = {
      lines.append(
        s"${spc}type ${typ.id} is "
      )
    }

    def visitType(ty: Type): Unit = {
      val text = ty match {
        case exp: TypeExpression ⇒ visitTypeExpression(exp) + "\n"
        case dfn: TypeDefinition ⇒ visitTypeDefinition(dfn)
      }
      lines.append(text)
    }

    def visitTypeDefinition(
      typeDef: AST.TypeDefinition
    ): String = {
      typeDef match {
        case AST.Enumeration(of) ⇒
          s"any [ ${of.map(_.value).mkString(" ")} ]\n"
        case AST.Alternation(of) ⇒
          s"choose ${of.map(_.id.toString).mkString(" or ")}\n"
        case AST.Aggregation(of) ⇒
          s"combine {\n${of
            .map {
              case (k, v) ⇒
                s"$spc  ${k.value} is ${visitTypeExpression(v)}"
            }
            .mkString(s",\n")}\n$spc}\n"
      }
    }

    override def close(): Lines = {
      lines
    }
  }

  case class DomainGenerator(
    domain: DomainDef,
    lines: Lines = Lines(),
    indent: Int = 0
  ) extends GeneratorBase[DomainDef](domain)
      with Traversal.DomainTraveler[Lines] {

    def open(): Lines = {
      lines.append(s"domain ${domain.id}")
      domain.subdomain.foreach(
        id ⇒ lines.append(s" is subdomain of $id")
      )
      lines.append(" {\n")
    }

    override def close(): Lines = {
      lines.append(s"$spc}")
      visitExplanation(domain.explanation)
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
      lines.append(s"${spc}channel ${channel.id} {\n")
    }

    def visitCommands(commands: Seq[CommandRef]): Unit = {
      lines.append(s"$spc  commands {")
      lines.append(
        commands.map(_.id).mkString(", ")
      )
      lines.append("}\n")
    }

    def visitEvents(events: Seq[EventRef]): Unit = {
      lines.append(s"$spc  events {")
      lines.append(
        events.map(_.id).mkString(", ")
      )
      lines.append("}\n")
    }

    def visitQueries(queries: Seq[QueryRef]): Unit = {
      lines.append(s"$spc  queries {")
      lines.append(
        queries.map(_.id).mkString(", ")
      )
      lines.append("}\n")
    }

    def visitResults(results: Seq[ResultRef]): Unit = {
      lines.append(s"$spc  results {")
      lines.append(
        results.map(_.id).mkString(", ")
      )
      lines.append("}\n")
    }
  }

  case class ContextGenerator(context: ContextDef, lines: Lines, indent: Int)
      extends GeneratorBase[ContextDef](context)
      with Traversal.ContextTraveler[Lines] {

    def open(): Lines = {
      lines.append(s"${spc}context ${context.id} {\n")
    }

    def visitType(t: TypeDef): Traversal.TypeTraveler[Lines] = {
      TypeGenerator(t, lines, indent + 2)
    }

    def visitCommand(command: CommandDef): Unit = {
      lines.append(
        s"${spc}  command ${command.id} is ${visitTypeExpression(command.typ)}"
      )
      val keyword = if (command.events.size > 1) "events" else "event"
      lines.append(
        s" yields $keyword ${command.events.map(_.id).mkString(", ")}\n"
      )
    }

    def visitEvent(event: EventDef): Unit = {
      lines.append(
        s"${spc}  event ${event.id} is ${visitTypeExpression(event.typ)}\n"
      )
    }

    def visitQuery(query: QueryDef): Unit = {
      lines.append(
        s"${spc}  query ${query.id} is ${visitTypeExpression(query.typ)}"
      )
      lines.append(s" yields result ${query.result.id}\n")
    }

    def visitResult(result: ResultDef): Unit = {
      lines.append(
        s"${spc}  result ${result.id} is ${visitTypeExpression(result.typ)}\n"
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
        s"${spc}entity ${entity.id} is ${visitTypeExpression(entity.typ)} {\n"
      )
      entity.options.foreach { options ⇒
        lines.append(s"$spc  options: ")
        lines.append(options.map(_.toString).mkString(" "))
        lines.append("\n")
      }
      lines
    }

    def visitProducer(c: ChannelRef): Unit = {
      lines.append(s"$spc  produces channel ${c.id}\n")
    }

    def visitConsumer(c: ChannelRef): Unit = {
      lines.append(s"$spc  consumes channel ${c.id}\n")
    }

    def visitInvariant(i: InvariantDef): Unit = {
      lines.append(s"$spc  invariant ${i.id}")
    }

    def visitFeature(f: FeatureDef): FeatureTraveler[Lines] = {
      FeatureGenerator(f, lines, indent + 2)
    }
  }

  case class FeatureGenerator(feature: FeatureDef, lines: Lines, indent: Int)
      extends GeneratorBase[FeatureDef](feature)
      with Traversal.FeatureTraveler[Lines] {

    def open(): Lines = {
      lines.append(s"${spc}feature ${feature.id} {\n")
      lines.append(s"$spc  description {\n")
      feature.description.foreach(s ⇒ lines.append(spc + s + "\n"))
      lines.append(s"$spc}\n")
    }

    def visitBackground(background: Background): Unit = {}
    def visitExample(example: Example): Unit = {}
  }

  case class InteractionGenerator(
    interaction: InteractionDef,
    lines: Lines,
    indent: Int
  ) extends GeneratorBase[InteractionDef](interaction)
      with Traversal.InteractionTraveler[Lines] {

    def open(): Lines = {
      lines.append(s"${spc}interaction ${interaction.id} {\n")
    }

    def visitRole(role: RoleDef): Unit = {}

    def visitAction(action: ActionDef): Unit = {}
  }

  case class AdaptorGenerator(adaptor: AdaptorDef, lines: Lines, indent: Int)
      extends GeneratorBase[AdaptorDef](adaptor)
      with Traversal.AdaptorTraveler[Lines] {

    def open(): Lines = {
      lines.append(s"${spc}adaptor ${adaptor.id} {\n")
    }
  }
}
