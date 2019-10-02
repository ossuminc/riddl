package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import com.yoppworks.ossum.riddl.parser.Traversal.DefTraveler

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
    DomainTraveler(domain).traverse
  }

  trait TravelerBase[D <: Def] extends DefTraveler[Lines, D] {
    def lines: Lines

    def visitExplanation(
      maybeExplanation: Option[Explanation]
    ): Unit = {
      maybeExplanation.foreach { explanation ⇒
        lines.append(
          "explained as {",
          "  purpose: \"",
          explanation.purpose.s,
          "\""
        )
        explanation.details.foreach { details ⇒
          lines.append("  details: \"", details.s, "\"")
        }
        explanation.links.foreach { link ⇒
          lines.append("  link(\"", link.url.s, "\"")
        }
        lines.append("}")
      }
      lines
    }

    protected def visitType(
      lines: Lines,
      t: AST.TypeDef,
      indent: Int = 2
    ): Lines = {
      val spc = " ".repeat(indent)
      lines.append(spc, s"type ${t.id.value} = ")
      lines.append(t.typ match {
        case x: AST.PredefinedType ⇒ x.id.value
        case AST.TypeRef(id) ⇒ s"type ${id.value}"
        case AST.Enumeration(of) ⇒
          s"any [ ${of.map(_.value).mkString(", ")} ]"
        case AST.Alternation(of) ⇒
          s"choose ${of.map(_.id.value).mkString(" or")}"
        case AST.Aggregation(of) ⇒
          s"combine {\n${of
            .map {
              case (k, v) ⇒ s"${k.value}: $v"
            }
            .mkString(s",\n$spc  ")}\n$spc}"
        case AST.Optional(ref) ⇒ s"${ref.value}?"
        case AST.ZeroOrMore(ref) ⇒ s"${ref.value}*"
        case AST.OneOrMore(ref) ⇒ s"${ref.value}+"
      })
      lines.append("\n")
      lines
    }
  }

  case class DomainTraveler(domain: DomainDef, lines: Lines = Lines())
      extends Traversal.DomainTraveler[Lines](domain)
      with TravelerBase[DomainDef] {

    def open(d: DomainDef): Lines = {
      lines.append(s"domain ${d.id.value}")
      d.subdomain.foreach(id ⇒ lines.append(s" is subdomain of ${id.value}"))
      lines.append(" {\n")
      lines
    }

    def close(): Lines = {
      lines.append("}\n")
    }

    def visitType(
      typ: TypeDef
    ): Unit = {
      visitType(lines, typ)
    }

    def visitChannel(
      channel: ChannelDef
    ): Unit = {
      lines.append(s"  channel ${channel.id} {\n")
      lines.append(s"    commands: ${channel.commands.mkString(", ")}\n")
      lines.append(s"    events: ${channel.events.mkString(", ")}\n")
      lines.append(s"    queries: ${channel.queries.mkString(", ")}\n")
      lines.append(s"    results: ${channel.results.mkString(", ")}\n")
      lines.append(s"  }\n")
    }

    def visitInteraction(
      i: InteractionDef
    ): Traversal.InteractionTraveler[Lines] = {
      InteractionTraveler(i, lines)
    }

    def visitContext(
      context: ContextDef
    ): Traversal.ContextTraveler[Lines] = {
      ContextTraveler(context, lines)
    }
  }

  case class ContextTraveler(context: ContextDef, lines: Lines)
      extends Traversal.ContextTraveler[Lines](context)
      with TravelerBase[ContextDef] {

    def open(c: ContextDef): Lines = {
      Lines(s"  context ${c.id.value} {\n")
    }

    def close(): Lines = {
      lines.append("  }\n")
    }

    def visitType(t: TypeDef): Unit = visitType(lines, t, indent = 4)

    def visitCommand(command: CommandDef): Unit = {}

    def visitEvent(event: EventDef): Unit = {}

    def visitQuery(query: QueryDef): Unit = {}

    def visitResult(result: ResultDef): Unit = {}

    def visitAdaptor(
      a: AdaptorDef
    ): Traversal.AdaptorTraveler[Lines] = AdaptorTraveler(a, lines)

    def visitInteraction(
      i: InteractionDef
    ): Traversal.InteractionTraveler[Lines] = InteractionTraveler(i, lines)

    def visitEntity(e: EntityDef): Traversal.EntityTraveler[Lines] = {
      EntityTraveler(e, lines)
    }
  }

  case class EntityTraveler(entity: EntityDef, lines: Lines)
      extends Traversal.EntityTraveler[Lines](entity)
      with TravelerBase[EntityDef] {

    def open(d: EntityDef): Lines = {
      lines.append(s"    entity ${entity.id.value} {\n")
    }

    def close(): Lines = {
      lines.append("    }\n")
    }

    override def visitInvariant(i: InvariantDef): Unit = {}

    override def visitFeature(f: FeatureDef): Unit = {}
  }

  case class InteractionTraveler(interaction: InteractionDef, lines: Lines)
      extends Traversal.InteractionTraveler[Lines](interaction)
      with TravelerBase[InteractionDef] {

    def open(d: InteractionDef): Lines = {
      lines.append(s"    interaction ${interaction.id.value} {\n")
    }

    def close(): Lines = {
      lines.append("    }\n")
    }

    def visitRole(role: RoleDef): Unit = {}

    def visitAction(action: ActionDef): Unit = {}
  }

  case class AdaptorTraveler(adaptor: AdaptorDef, lines: Lines)
      extends Traversal.AdaptorTraveler[Lines](adaptor)
      with TravelerBase[AdaptorDef] {

    def open(d: AdaptorDef): Lines = {
      lines.append(s"    adaptor ${adaptor.id.value} {\n")
    }

    override def close(): Lines = {
      lines.append(s"    |\n")
    }
  }
}
