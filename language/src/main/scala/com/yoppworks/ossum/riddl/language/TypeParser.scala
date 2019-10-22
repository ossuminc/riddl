package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._

import scala.collection.immutable.ListMap

/** Parsing rules for Type definitions */
trait TypeParser extends CommonParser {

  def enumerationType[_: P]: P[Enumeration] = {
    P(location ~ "any" ~/ open ~ identifier.rep(1, sep = ",".?) ~ close).map {
      enums =>
        (Enumeration.apply _).tupled(enums)
    }
  }

  def alternationType[_: P]: P[Alternation] = {
    P(
      location ~
        "choose" ~/ open ~ (location ~ identifier).rep(2, P("or" | "|")) ~ close
    ).map {
      case (loc, ids: Seq[(Location, Identifier)]) =>
        Alternation(loc, ids.map(x => TypeRef(x._1, x._2)))
    }
  }

  def typeExpression[_: P]: P[TypeExpression] = {
    P(cardinality(typeRef))
  }

  def cardinality[_: P](p: => P[TypeExpression]): P[TypeExpression] = {
    P(location ~ p ~ ("?".! | "*".! | "+".!).?).map {
      case (loc, typ, Some("?")) => Optional(loc, typ.id)
      case (loc, typ, Some("+")) => OneOrMore(loc, typ.id)
      case (loc, typ, Some("*")) => ZeroOrMore(loc, typ.id)
      case (_, typ, Some(_))     => typ
      case (_, typ, None)        => typ
    }
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

  def referToType[_: P]: P[ReferenceType] = {
    P(location ~ "reference" ~ "to" ~/ entityRef).map { tpl =>
      (ReferenceType.apply _).tupled(tpl)
    }
  }

  def typeDefinitions[_: P]: P[TypeSpecification] = {
    P(
      enumerationType | alternationType | aggregationType | mappingType |
        rangeType | referToType
    )
  }

  def types[_: P]: P[TypeValue] = {
    P(typeDefinitions | typeExpression)
  }

  def typeDef[_: P]: P[TypeDef] = {
    P(
      location ~ "type" ~/ identifier ~ is ~ types ~ addendum
    ).map { tpl =>
      (TypeDef.apply _).tupled(tpl)
    }
  }

  def typeRef[_: P]: P[TypeRef] = {
    P(location ~ identifier).map { id =>
      TypeRef(id._1, id._2)
    }
  }
}
