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

  trait GeneratorBase[D <: Def] extends DefTraveler[Lines, D] {
    def lines: Lines

    def indent: Int

    val spc: String = " ".repeat(indent)

    protected def visitExplanation(
      maybeExplanation: Option[Explanation]
    ): Unit = {
      maybeExplanation.foreach { explanation ⇒
        lines.append("explained as {\n")
        explanation.markdown.foreach { line ⇒
          lines.append(spc + "  " + line + "\n")
        }
        lines.append(spc + "}\n")

        explanation.links.foreach { links ⇒
          lines.append(s"$spc  with (\n")
          lines.append(
            links.map(link ⇒ spc + "    \"" + link.url.s + "\"").mkString(",\n")
          )
          lines.append(spc + "  )\n")
        }
        lines.append(s"$spc}\n")
      }
    }

    protected def visitTypeExpression(typEx: AST.TypeExpression): String = {
      typEx match {
        case AST.PredefinedType(id) ⇒ id.toString
        case AST.TypeRef(id) ⇒ id.toString
        case AST.Optional(ref) ⇒ s"$ref?"
        case AST.ZeroOrMore(ref) ⇒ s"$ref*"
        case AST.OneOrMore(ref) ⇒ s"$ref+"
      }
    }
  }

  case class TypeGenerator(typ: TypeDef, lines: Lines, indent: Int = 2)
      extends Traversal.TypeTraveler[Lines]
      with GeneratorBase[TypeDef] {

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
          s"any [ ${of.map(_.value).mkString(", ")} ]\n"
        case AST.Alternation(of) ⇒
          s"choose ${of.map(visitTypeExpression).mkString(" or ")}\n"
        case AST.Aggregation(of) ⇒
          s"combine {\n${of
            .map {
              case (k, v) ⇒
                s"$spc  ${k.value} is ${visitTypeExpression(v)}"
            }
            .mkString(s",\n")}\n$spc}\n"
      }
    }

    protected def close(): Lines = {
      lines
    }
  }

  case class DomainGenerator(
    domain: DomainDef,
    lines: Lines = Lines(),
    indent: Int = 0
  ) extends Traversal.DomainTraveler[Lines]
      with GeneratorBase[DomainDef] {

    def open(): Lines = {
      lines.append(s"domain ${domain.id}")
      domain.subdomain.foreach(
        id ⇒ lines.append(s" is subdomain of $id")
      )
      lines.append(" {\n")
    }

    def close(): Lines = {
      lines.append("}")
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
      extends Traversal.ChannelTraveler[Lines]
      with GeneratorBase[ChannelDef] {

    def open(): Lines = {
      lines.append(s"${spc}channel ${channel.id} {\n")
    }

    def visitCommand(command: CommandRef): Unit = {
      lines.append(
        s"$spc  commands: ${channel.commands.map(_.id).mkString(", ")}\n"
      )
    }

    def visitEvent(event: EventRef): Unit = {
      lines.append(
        s"$spc  events: ${channel.events.map(_.id).mkString(", ")}\n"
      )
    }

    def visitQuery(query: QueryRef): Unit = {
      lines.append(
        s"$spc  queries: ${channel.queries.map(_.id).mkString(", ")}\n"
      )
    }

    def visitResult(result: ResultRef): Unit = {
      lines.append(
        s"    results: ${channel.results.map(_.id).mkString(", ")}\n"
      )
    }

    def close(): Lines = {
      lines.append(s"$spc}\n")
    }
  }

  case class ContextGenerator(context: ContextDef, lines: Lines, indent: Int)
      extends Traversal.ContextTraveler[Lines]
      with GeneratorBase[ContextDef] {

    def open(): Lines = {
      lines.append(s"  context ${context.id} {\n")
    }

    def close(): Lines = {
      lines.append("  }\n")
    }

    def visitType(t: TypeDef): Traversal.TypeTraveler[Lines] = {
      TypeGenerator(t, lines, indent + 2)
    }

    def visitCommand(command: CommandDef): Unit = {}

    def visitEvent(event: EventDef): Unit = {}

    def visitQuery(query: QueryDef): Unit = {}

    def visitResult(result: ResultDef): Unit = {}

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
      extends Traversal.EntityTraveler[Lines]
      with GeneratorBase[EntityDef] {

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

    def close(): Lines = {
      lines.append(spc + "}\n")
    }

    def visitProducer(c: ChannelRef): Unit = {
      lines.append(s"${spc}  produces channel ${c.id}\n")
    }

    def visitConsumer(c: ChannelRef): Unit = {
      lines.append(s"${spc}  consumes channel ${c.id}\n")
    }

    def visitInvariant(i: InvariantDef): Unit = {
      lines.append(s"${spc}  invariant ${i.id}")
    }

    def visitFeature(f: FeatureDef): FeatureTraveler[Lines] = {
      FeatureGenerator(f, lines, indent + 2)
    }
  }

  case class FeatureGenerator(feature: FeatureDef, lines: Lines, indent: Int)
      extends Traversal.FeatureTraveler[Lines]
      with GeneratorBase[FeatureDef] {

    def open(): Lines = {
      lines.append(s"${spc}feature ${feature.id} {\n")
      lines.append(s"$spc  description {\n")
      feature.description.foreach(s ⇒ lines.append(spc + s + "\n"))
      lines.append(s"$spc}\n")
    }

    def visitBackground(background: Background): Unit = {}
    def visitExample(example: Example): Unit = {}

    def close(): Lines = {
      lines.append(s"$spc}\n")
    }
  }

  case class InteractionGenerator(
    interaction: InteractionDef,
    lines: Lines,
    indent: Int
  ) extends Traversal.InteractionTraveler[Lines]
      with GeneratorBase[InteractionDef] {

    def open(): Lines = {
      lines.append(s"    interaction ${interaction.id} {\n")
    }

    def close(): Lines = {
      lines.append("    }\n")
    }

    def visitRole(role: RoleDef): Unit = {}

    def visitAction(action: ActionDef): Unit = {}
  }

  case class AdaptorGenerator(adaptor: AdaptorDef, lines: Lines, indent: Int)
      extends Traversal.AdaptorTraveler[Lines]
      with GeneratorBase[AdaptorDef] {

    def open(): Lines = {
      lines.append(s"    adaptor ${adaptor.id} {\n")
    }

    override def close(): Lines = {
      lines.append(s"    |\n")
    }
  }
}
