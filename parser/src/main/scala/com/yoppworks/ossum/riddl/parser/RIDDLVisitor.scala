package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._

import scala.collection.mutable

/** A visitor implementation for regenerating the input RIDDL */
class RIDDLVisitor extends Traversal.Visitor[String] {

  override def visitDomain(
    d: AST.DomainDef
  ): Traversal.DomainVisitor[String] = {
    new DomainVisitor(d)
  }

  def visitType(
    t: AST.TypeDef,
    buffer: mutable.Buffer[String],
    indent: Int = 2
  ): String = {
    val buff = collection.mutable.Buffer("")
    val spc = " ".repeat(indent)
    buff.append(spc, s"type ${t.id.value} = ")
    buff.append(
      t.typ match {
        case x: AST.PredefinedType ⇒ x.id.value
        case AST.TypeRef(id) ⇒ id.value
        case AST.Enumeration(of) ⇒
          s"any [ ${of.map(_.value).mkString(", ")} ]"
        case AST.Alternation(of) ⇒
          s"choose ${of.map(_.id.value).mkString(" or")}"
        case AST.Aggregation(of) ⇒
          s"combine {\n${of
            .map {
              case (k, v) ⇒ s"$k: $v"
            }
            .mkString(s",\n$spc  ")}\n$spc}"
        case AST.Optional(ref) ⇒ s"${ref.id.value}?"
        case AST.ZeroOrMore(ref) ⇒ s"${ref.id.value}*"
        case AST.OneOrMore(ref) ⇒ s"${ref.id.value}+"
      }
    )
    buff.append("\n")
    buff.mkString
  }

  class DomainVisitor(domain: DomainDef)
      extends Traversal.DomainVisitor[String](domain) {

    val buff: mutable.Buffer[String] = collection.mutable.Buffer("")

    override def open(d: DomainDef): Unit = {
      buff.append(s"domain ${d.id.value}")
      d.subdomain.foreach(id ⇒ buff.append(s" is subdomain of ${id.value}"))
      buff.append(" {\n")
    }

    def visit(ty: TypeDef): Unit = visitType(ty, buff)

    def visit(ch: AST.ChannelDef): Unit = {
      val buff = collection.mutable.Buffer("")
      buff.append(s"  channel ${ch.id} {\n")
      buff.append(s"    commands: ${ch.commands.mkString(", ")}\n")
      buff.append(s"    events: ${ch.events.mkString(", ")}\n")
      buff.append(s"    queries: ${ch.queries.mkString(", ")}\n")
      buff.append(s"    results: ${ch.results.mkString(", ")}\n")
      buff.append(s"  }\n\n")
      buff.mkString
    }

    override def close: String = {
      buff.append("\n}\n")
      buff.mkString.toString
    }
  }

  override def visitContext(
    c: AST.ContextDef
  ): Traversal.ContextVisitor[String] = {
    new ContextVisitor(c)
  }

  class ContextVisitor(context: ContextDef)
      extends Traversal.ContextVisitor[String](context) {
    private val buff = mutable.Buffer("")

    def visit(t: TypeDef): Unit = visitType(t, buff, indent = 4)

    def visit(c: CommandDef): Unit = {}

    def visit(e: EventDef): Unit = {}

    def visit(q: QueryDef): Unit = {}

    def visit(r: ResultDef): Unit = {}

    def open(d: ContextDef): Unit = {}

    def close: String = { "" }
  }

  override def visitEntity(e: EntityDef): Traversal.EntityVisitor[String] = {
    new EntityVisitor(e)
  }

  class EntityVisitor(entity: EntityDef)
      extends Traversal.EntityVisitor[String](entity) {
    def visit(f: AST.FeatureDef): Unit = {}

    def visit(i: AST.InvariantDef): Unit = {}

    def open(d: EntityDef): Unit = {}

    override def close: String = { "" }
  }

  override def visitInteraction(
    i: AST.InteractionDef
  ): Traversal.InteractionVisitor[String] = {
    new InteractionVisitor(i)
  }

  class InteractionVisitor(interaction: InteractionDef)
      extends Traversal.InteractionVisitor[String](interaction) {
    def visit(role: RoleDef): Unit = {}

    def visit(action: ActionDef): Unit = {}

    def open(d: InteractionDef): Unit = {}

    def close: String = { "" }
  }

  override def visitAdaptor(
    a: AST.AdaptorDef
  ): Traversal.AdaptorVisitor[String] = {
    new AdaptorVisitor(a)
  }

  class AdaptorVisitor(adaptor: AdaptorDef)
      extends Traversal.AdaptorVisitor[String](adaptor) {
    override def open(d: AdaptorDef): Unit = {}

    override def close: String = { "" }
  }
}
