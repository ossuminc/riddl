package com.yoppworks.ossum.riddl.translator

import java.io.File

import com.yoppworks.ossum.riddl.language.AST._
import com.yoppworks.ossum.riddl.language.Traversal.ChannelTraveler
import com.yoppworks.ossum.riddl.language.Traversal.DefTraveler
import com.yoppworks.ossum.riddl.language.Traversal.FeatureTraveler
import com.yoppworks.ossum.riddl.language.AST
import com.yoppworks.ossum.riddl.language.Traversal

import scala.collection.mutable

/** This is the RIDDL Prettifier to convert an AST back to RIDDL plain text */
object Prettify extends Translator {

  type Lines = mutable.StringBuilder

  object Lines {

    def apply(s: String = ""): Lines = {
      val lines = new mutable.StringBuilder(s)
      lines.append(s)
      lines
    }
  }

  def translate(root: RootContainer, configFile: File): Unit = {
    root.content.foreach(x => translateContainer(x, configFile))
  }

  def translateContainer(container: Container, configFile: File): Unit = {
    container match {
      case domain: DomainDef =>
        DomainPrettifier(domain).traverse
      case x: Container =>
        throw new NotImplementedError()
    }
  }

  def forContainer(container: Container): Lines = {
    container match {
      case domain: DomainDef   => DomainPrettifier(domain).traverse
      case context: ContextDef => ContextPrettifier(context).traverse
      case entity: EntityDef   => EntityPrettifier(entity).traverse
      case interaction: InteractionDef =>
        InteractionPrettifier(interaction).traverse
      case feature: FeatureDef => FeaturePrettifier(feature).traverse
      case channel: ChannelDef => ChannelPrettifier(channel).traverse
      case adaptor: AdaptorDef => AdaptorPrettifier(adaptor).traverse
      case _                   => throw new RuntimeException("No can do")
    }
  }

  abstract class PrettifyBase[D <: Definition](definition: D)
      extends DefTraveler[Lines, D] {
    def payload: Lines

    def indent: Int

    val spc: String = " ".repeat(indent)

    protected def visitExplanation(
      exp: Explanation
    ): Unit = {
      payload.append(" explained as {\n")
      exp.markdown.foreach { line =>
        payload.append(spc + "  \"" + line + "\"\n")
      }
      payload.append(spc + "}")
    }

    protected def visitSeeAlso(
      sa: SeeAlso
    ): Unit = {
      payload.append(s" see also {\n")
      sa.citations.foreach { line =>
        payload.append(spc + "  \"" + line + "\"\n")
      }
      payload.append(spc + "}")
    }

    protected def visitTypeExpr(typEx: AST.TypeExpression): String = {
      typEx match {
        case pt: PredefinedType   => pt.id.value
        case TypeRef(_, id)       => "type " + id.value
        case Optional(_, typex)   => visitTypeExpr(typex) + "?"
        case ZeroOrMore(_, typex) => visitTypeExpr(typex) + "*"
        case OneOrMore(_, typex)  => visitTypeExpr(typex) + "+"
        case UniqueId(_, id)      => s"Id($id)"
        case AST.Enumeration(_, of) =>
          s"any { ${of.map(_.value).mkString(" ")} }"
        case AST.Alternation(_, of) =>
          s"choose { ${of.map(visitTypeExpr).mkString(" or ")} }"
        case AST.Aggregation(_, of) =>
          s"combine {\n${of
            .map {
              case (k: Identifier, v: TypeExpression) =>
                s"$spc  ${k.value} is ${visitTypeExpr(v)}"
            }
            .mkString(s",\n")}\n$spc}"
        case AST.Mapping(_, from, to) =>
          s"mapping from ${visitTypeExpr(from)} to ${visitTypeExpr(to)}"
        case AST.RangeType(_, min, max) =>
          s"range from $min to $max"
        case AST.ReferenceType(_, id) =>
          s"reference to $id"
      }
    }

    override def close(): Unit = {
      payload.append(s"$spc}")
    }

    protected def terminus(): Lines = {
      payload.append("\n")
    }
  }

  case class TypePrettifier(typeDef: TypeDef, payload: Lines, indent: Int = 2)
      extends PrettifyBase[TypeDef](typeDef)
      with Traversal.TypeTraveler[Lines] {

    protected def open(): Unit = {
      payload.append(
        s"${spc}type ${typeDef.id.value} is "
      )
    }

    override def close(): Unit = {}

    override def visitTypeExpression(typex: TypeExpression): Unit = {
      payload.append(visitTypeExpr(typex))
    }
  }

  case class DomainPrettifier(
    domain: DomainDef,
    payload: Lines = Lines(),
    indent: Int = 0
  ) extends PrettifyBase[DomainDef](domain)
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
      TypePrettifier(typ, payload, indent + 2)
    }

    def visitChannel(
      channel: ChannelDef
    ): Traversal.ChannelTraveler[Lines] = {
      ChannelPrettifier(channel, payload, indent + 2)
    }

    def visitInteraction(
      i: InteractionDef
    ): Traversal.InteractionTraveler[Lines] = {
      InteractionPrettifier(i, payload, indent + 2)
    }

    def visitContext(
      context: ContextDef
    ): Traversal.ContextTraveler[Lines] = {
      ContextPrettifier(context, payload, indent + 2)
    }
  }

  case class ChannelPrettifier(
    channel: ChannelDef,
    payload: Lines = Lines(),
    indent: Int = 0
  ) extends PrettifyBase[ChannelDef](channel)
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

  case class ContextPrettifier(
    context: ContextDef,
    payload: Lines = Lines(),
    indent: Int = 0
  ) extends PrettifyBase[ContextDef](context)
      with Traversal.ContextTraveler[Lines] {

    def open(): Unit = {
      payload.append(s"${spc}context ${context.id.value} {\n")
    }

    def visitType(t: TypeDef): Traversal.TypeTraveler[Lines] = {
      TypePrettifier(t, payload, indent + 2)
    }

    def visitCommand(command: CommandDef): Unit = {
      payload.append(
        s"$spc  command ${command.id.value} is ${visitTypeExpr(command.typ)}"
      )
      val keyword = if (command.events.size > 1) "events" else "event"
      payload.append(
        s" yields $keyword ${command.events.map(_.id.value).mkString(", ")}\n"
      )
    }

    def visitEvent(event: EventDef): Unit = {
      payload.append(
        s"$spc  event ${event.id.value} is ${visitTypeExpr(event.typ)}\n"
      )
    }

    def visitQuery(query: QueryDef): Unit = {
      payload.append(
        s"$spc  query ${query.id.value} is ${visitTypeExpr(query.typ)}"
      )
      payload.append(s" yields result ${query.result.id.value}\n")
    }

    def visitResult(result: ResultDef): Unit = {
      payload.append(
        s"$spc  result ${result.id.value} is ${visitTypeExpr(result.typ)}\n"
      )
    }

    override def visitChannel(chan: ChannelDef): ChannelTraveler[Lines] = {
      ChannelPrettifier(chan, payload, indent + 2)
    }

    def visitAdaptor(
      a: AdaptorDef
    ): Traversal.AdaptorTraveler[Lines] =
      AdaptorPrettifier(a, payload, indent + 2)

    def visitInteraction(
      i: InteractionDef
    ): Traversal.InteractionTraveler[Lines] =
      InteractionPrettifier(i, payload, indent + 2)

    def visitEntity(e: EntityDef): Traversal.EntityTraveler[Lines] = {
      EntityPrettifier(e, payload, indent + 2)
    }

  }

  case class EntityPrettifier(
    entity: EntityDef,
    payload: Lines = Lines(),
    indent: Int = 0
  ) extends PrettifyBase[EntityDef](entity)
      with Traversal.EntityTraveler[Lines] {

    def open(): Unit = {
      payload.append(
        s"${spc}entity ${entity.id.value} is ${visitTypeExpr(entity.typ)} {\n"
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
      FeaturePrettifier(f, payload, indent + 2)
    }
  }

  case class FeaturePrettifier(
    feature: FeatureDef,
    payload: Lines = Lines(),
    indent: Int = 0
  ) extends PrettifyBase[FeatureDef](feature)
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

  case class InteractionPrettifier(
    interaction: InteractionDef,
    payload: Lines = Lines(),
    indent: Int = 0
  ) extends PrettifyBase[InteractionDef](interaction)
      with Traversal.InteractionTraveler[Lines] {

    def open(): Unit = {
      payload.append(s"${spc}interaction ${interaction.id.value} {\n")
    }

    def visitRole(role: RoleDef): Unit = {}

    def visitAction(action: ActionDef): Unit = {}
  }

  case class AdaptorPrettifier(
    adaptor: AdaptorDef,
    payload: Lines = Lines(),
    indent: Int = 0
  ) extends PrettifyBase[AdaptorDef](adaptor)
      with Traversal.AdaptorTraveler[Lines] {

    def open(): Unit = {
      payload.append(s"${spc}adaptor ${adaptor.id.value} {\n")
    }

  }
}
