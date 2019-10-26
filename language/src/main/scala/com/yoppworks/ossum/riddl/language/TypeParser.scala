package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._

import scala.collection.immutable.ListMap

/** Parsing rules for Type definitions */
trait TypeParser extends CommonParser {

  def uniqueIdType[_: P]: P[UniqueId] = {
    P(location ~ "Id" ~ "(" ~/ identifier.? ~ ")").map {
      case (loc, Some(id)) => UniqueId(loc, id)
      case (loc, None)     => UniqueId(loc, Identifier(loc, ""))
    }
  }

  def referToType[_: P]: P[ReferenceType] = {
    P(location ~ "refer" ~ "to" ~/ entityRef).map { tpl =>
      (ReferenceType.apply _).tupled(tpl)
    }
  }

  def enumerationType[_: P]: P[Enumeration] = {
    P(location ~ "any" ~/ open ~ identifier.rep(1, sep = ",".?) ~ close)
      .map(enums => (Enumeration.apply _).tupled(enums))
  }

  def alternationType[_: P]: P[Alternation] = {
    P(
      location ~
        "choose" ~/ open ~ typeExpression.rep(2, P("or" | "|")) ~
        close
    ).map { x =>
      (Alternation.apply _).tupled(x)
    }
  }

  def typeRef[_: P]: P[TypeRef] = {
    P(location ~ identifier).map {
      case (location, identifier) =>
        TypeRef(location, identifier)
    }
  }

  def cardinality[_: P](p: => P[TypeExpression]): P[TypeExpression] = {
    P(
      "many".!.? ~ "optional".!.? ~
        location ~ p ~
        ("?".! | "*".! | "+".! | "...?".! | "...".!).?
    ).map {
      case (None, None, loc, typ, Some("?"))          => Optional(loc, typ)
      case (None, None, loc, typ, Some("+"))          => OneOrMore(loc, typ)
      case (None, None, loc, typ, Some("*"))          => ZeroOrMore(loc, typ)
      case (None, None, loc, typ, Some("...?"))       => ZeroOrMore(loc, typ)
      case (None, None, loc, typ, Some("..."))        => OneOrMore(loc, typ)
      case (Some(_), None, loc, typ, None)            => OneOrMore(loc, typ)
      case (None, Some(_), loc, typ, None)            => Optional(loc, typ)
      case (Some(_), Some(_), loc, typ, None)         => ZeroOrMore(loc, typ)
      case (None, Some(_), loc, typ, Some("?"))       => Optional(loc, typ)
      case (Some(_), None, loc, typ, Some("+"))       => OneOrMore(loc, typ)
      case (Some(_), Some(_), loc, typ, Some("*"))    => ZeroOrMore(loc, typ)
      case (Some(_), Some(_), loc, typ, Some("...?")) => ZeroOrMore(loc, typ)
      case (Some(_), None, loc, typ, Some("..."))     => OneOrMore(loc, typ)
      case (None, None, loc, typ, None)               => typ
      case (_, _, loc, typ, _) =>
        error(loc, s"Cannot determine cardinality for $typ")
        typ
    }
  }

  def typeExpression[_: P]: P[TypeExpression] = {
    P(
      cardinality(
        P(
          uniqueIdType | enumerationType | alternationType | referToType |
            aggregationType | mappingType | rangeType | typeRef
        )
      )
    )
  }

  def field[_: P]: P[(Identifier, TypeExpression)] = {
    P(identifier ~ is ~ typeExpression)
  }

  def fields[_: P]: P[Seq[(Identifier, TypeExpression)]] = {
    P(field.rep(1, P(",")))
  }

  def aggregationType[_: P]: P[Aggregation] = {
    P(
      location ~
        "combine" ~/ open ~ fields ~ close
    ).map {
      case (loc, types) =>
        Aggregation(loc, ListMap[Identifier, TypeExpression](types: _*))
    }
  }

  def mappingType[_: P]: P[Mapping] = {
    P(location ~ "mapping" ~ "from" ~/ typeExpression ~ "to" ~ typeExpression)
      .map { tpl =>
        (Mapping.apply _).tupled(tpl)
      }
  }

  def rangeType[_: P]: P[RangeType] = {
    P(location ~ "range" ~ "from" ~/ literalInteger ~ "to" ~ literalInteger)
      .map { tpl =>
        (RangeType.apply _).tupled(tpl)
      }
  }

  def typeDef[_: P]: P[TypeDef] = {
    P(
      location ~ "type" ~/ identifier ~ is ~ typeExpression ~ addendum
    ).map { tpl =>
      (TypeDef.apply _).tupled(tpl)
    }
  }

}
