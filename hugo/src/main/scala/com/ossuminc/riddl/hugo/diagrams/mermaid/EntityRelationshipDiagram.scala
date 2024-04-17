package com.ossuminc.riddl.hugo.diagrams.mermaid

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.passes.resolve.ReferenceMap

class EntityRelationshipDiagram(refMap: ReferenceMap) {

  private def makeTypeName(
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): String = {
    parents.headOption match
      case None => s"unresolved path: ${pid.format}"
      case Some(parent) =>
        refMap.definitionOf[Definition](pid, parent) match {
          case None                   => s"unresolved path: ${pid.format}"
          case Some(defn: Definition) => defn.id.format
        }
  }

  private def makeTypeName(
    typeEx: TypeExpression,
    parents: Seq[Definition]
  ): String = {
    val name = typeEx match {
      case AliasedTypeExpression(_, _, pid)      => makeTypeName(pid, parents)
      case EntityReferenceTypeExpression(_, pid) => makeTypeName(pid, parents)
      case UniqueId(_, pid)                      => makeTypeName(pid, parents)
      case Alternation(_, of) =>
        of.map(ate => makeTypeName(ate.pathId, parents))
          .mkString("-")
      case _: Mapping                        => "Mapping"
      case _: Aggregation                    => "Aggregation"
      case _: AggregateUseCaseTypeExpression => "Message"
      case _                                 => typeEx.format
    }
    name.replace(" ", "-")
  }

  private def makeERDRelationship(
    from: String,
    to: Field,
    parents: Seq[Definition]
  ): String = {
    val typeName = makeTypeName(to.typeEx, parents)
    if typeName.nonEmpty then {
      to.typeEx match {
        case _: OneOrMore =>
          if typeName.isEmpty then typeName
          else { from + " ||--|{ " + typeName + " : references" }
        case _: ZeroOrMore =>
          if typeName.isEmpty then typeName
          else { from + " ||--o{ " + typeName + " : references" }
        case _: Optional =>
          if typeName.isEmpty then typeName
          else { from + " ||--o| " + typeName + " : references" }
        case _: AliasedTypeExpression | _: EntityReferenceTypeExpression | _: UniqueId =>
          if typeName.isEmpty then typeName
          else { from + " ||--|| " + typeName + " : references" }
        case _ => ""
      }
    } else { typeName }
  }

  def generate(
    name: String,
    fields: Seq[Field],
    parents: Seq[Definition]
  ): Seq[String] = {

    val typ: Seq[String] = s"$name {" +: fields.map { f =>
      val typeName = makeTypeName(f.typeEx, parents)
      val fieldName = f.id.format.replace(" ", "-")
      val comment = "\"" + f.brief.map(_.s).getOrElse("") + "\""
      s"  $typeName $fieldName $comment"
    } :+ "}"

    val relationships: Seq[String] = fields
      .map(makeERDRelationship(name, _, parents))
      .filter(_.nonEmpty)

    Seq("erDiagram") ++ typ ++ relationships
  }
}
