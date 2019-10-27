package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._

import scala.collection.immutable.ListMap
import Terminals.Predefined
import Terminals.Readability
import Terminals.Punctuation._

/** Parsing rules for Type definitions */
trait TypeParser extends CommonParser {

  def typeRef[_: P]: P[TypeRef] = {
    P(location ~ identifier).map {
      case (location, identifier) =>
        TypeRef(location, identifier)
    }
  }

  def referToType[_: P]: P[ReferenceType] = {
    P(location ~ "refer" ~ "to" ~/ entityRef).map { tpl =>
      (ReferenceType.apply _).tupled(tpl)
    }
  }

  def enumerator[_: P]: P[Enumerator] = {
    P(identifier ~ aggregationType.?).map {
      case (id, typex) =>
        Enumerator(id.loc, id, typex)
    }
  }

  def enumerationType[_: P]: P[Enumeration] = {
    P(
      location ~ squareOpen ~/
        enumerator.rep(1, sep = comma.?) ~ squareClose
    ).map(enums => (Enumeration.apply _).tupled(enums))
  }

  def alternationType[_: P]: P[Alternation] = {
    P(
      location ~
        roundOpen ~ typeExpression.rep(2, P("or" | "|" | ",")) ~ roundClose
    ).map { x =>
      (Alternation.apply _).tupled(x)
    }
  }

  def cardinality[_: P](p: => P[TypeExpression]): P[TypeExpression] = {
    P(
      Readability.many.!.? ~ Readability.optional.!.? ~
        location ~ p ~
        (question.! | asterisk.! | plus.! | ellipsisQuestion.! | ellipsis.!).?
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
      case (None, None, _, typ, None)                 => typ
      case (_, _, loc, typ, _) =>
        error(loc, s"Cannot determine cardinality for $typ")
        typ
    }
  }

  def predefinedTypes[_: P]: P[TypeExpression] = {
    P(
      (location ~ Predefined.String).map(AST.Strng) |
        (location ~ Predefined.Boolean).map(AST.Bool) |
        (location ~ Predefined.Number).map(AST.Number) |
        (location ~ Predefined.Integer).map(AST.Integer) |
        (location ~ Predefined.Decimal).map(AST.Decimal) |
        (location ~ Predefined.Real).map(AST.Real) |
        (location ~ Predefined.DateTime).map(AST.DateTime) |
        (location ~ Predefined.Date).map(AST.Date) |
        (location ~ Predefined.TimeStamp).map(AST.TimeStamp) |
        (location ~ Predefined.Time).map(AST.Time) |
        (location ~ Predefined.URL).map(AST.URL) |
        (location ~ Predefined.Pattern ~ roundOpen ~
          literalString ~ roundClose)
          .map(tpl => (Pattern.apply _).tupled(tpl)) |
        (location ~ Predefined.Id ~ roundOpen ~/ identifier.? ~ roundClose)
          .map {
            case (loc, Some(id)) => UniqueId(loc, id)
            case (loc, None)     => UniqueId(loc, Identifier(loc, ""))
          }
    )
  }

  def field[_: P]: P[(Identifier, TypeExpression)] = {
    P(identifier ~ is ~ typeExpression)
  }

  def fields[_: P]: P[Seq[(Identifier, TypeExpression)]] = {
    P(field.rep(1, comma))
  }

  def aggregationType[_: P]: P[Aggregation] = {
    P(
      location ~ curlyOpen ~ fields ~ curlyClose
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

  def typeExpression[_: P]: P[TypeExpression] = {
    P(
      cardinality(
        P(
          predefinedTypes | enumerationType | alternationType | referToType |
            aggregationType | mappingType | rangeType | typeRef
        )
      )
    )
  }

  def typeDef[_: P]: P[TypeDef] = {
    P(
      location ~ "type" ~/ identifier ~ is ~ typeExpression ~ addendum
    ).map { tpl =>
      (TypeDef.apply _).tupled(tpl)
    }
  }

}
