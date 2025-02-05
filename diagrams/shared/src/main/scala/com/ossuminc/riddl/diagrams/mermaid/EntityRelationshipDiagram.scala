/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.resolve.ReferenceMap
import com.ossuminc.riddl.utils.PlatformContext

import scala.scalajs.js.annotation.*

@JSExportTopLevel("EntityRelationshipDiagram")
class EntityRelationshipDiagram(refMap: ReferenceMap)(using pc: PlatformContext) {

  private def makeTypeName(
    pid: PathIdentifier,
    parent: Branch[?]
  ): String = {
    refMap.definitionOf[Definition](pid, parent) match {
      case None => s"unresolved path: ${pid.format}"
      case Some(definition: Definition) => definition.id.format
    }
  }

  private def makeTypeName(
    typeEx: TypeExpression,
    parent: Branch[?]
  ): String = {
    val name = typeEx match {
      case AliasedTypeExpression(_, _, pid)      => makeTypeName(pid, parent)
      case EntityReferenceTypeExpression(_, pid) => makeTypeName(pid, parent)
      case UniqueId(_, pid)                      => makeTypeName(pid, parent)
      case Alternation(_, of) =>
        of.toSeq.map(ate => makeTypeName(ate.pathId, parent)).mkString("-")
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
    parent: Branch[?]
  ): String = {
    val typeName = makeTypeName(to.typeEx, parent)
    if typeName.nonEmpty then
      val connector = to.typeEx match
        case _: OneOrMore                     => from + " ||--|{ " + typeName
        case _: ZeroOrMore                    => from + " ||--o{ " + typeName
        case _: Optional                      => from + " ||--o| " + typeName
        case _: AliasedTypeExpression         => from + " ||--|| " + typeName
        case _: EntityReferenceTypeExpression => from + " ||--|| " + typeName
        case _: UniqueId                      => from + " ||--|| " + typeName
        case _ => ""
      connector + " : references"
    else typeName
  }

  def generate(
    name: String,
    fields: Seq[Field],
    parent: Branch[?]
  ): Seq[String] = {

    val typ: Seq[String] = s"$name {" +: fields.map { f =>
      val typeName = makeTypeName(f.typeEx, parent)
      val fieldName = f.id.format.replace(" ", "-")
      val comment = "\"" + f.briefString + "\""
      s"  $typeName $fieldName $comment"
    } :+ "}"

    val relationships: Seq[String] = fields
      .map(makeERDRelationship(name, _, parent))
      .filter(_.nonEmpty)

    Seq("erDiagram") ++ typ ++ relationships
  }
}
