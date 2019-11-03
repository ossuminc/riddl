package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._

import scala.collection.immutable.ListMap
import Terminals.Keywords
import Terminals.Predefined
import Terminals.Readability
import Terminals.Punctuation._

/** Parsing rules for Type definitions */
trait TypeParser extends CommonParser {

  def typeRef[_: P]: P[TypeRef] = {
    P(location ~ pathIdentifier).map {
      case (location, identifier) =>
        TypeRef(location, identifier)
    }
  }

  def referToType[_: P]: P[ReferenceType] = {
    P(location ~ "refer" ~ "to" ~/ entityRef ~ description).map { tpl =>
      (ReferenceType.apply _).tupled(tpl)
    }
  }

  def simplePredefinedTypes[_: P]: P[TypeExpression] = {
    P(
      (location ~ Predefined.String).map(AST.Strng) |
        (location ~ Predefined.Boolean).map(AST.Bool) |
        (location ~ Predefined.Number).map(AST.Number) |
        (location ~ Predefined.Integer).map(AST.Integer) |
        (location ~ Predefined.Decimal).map(AST.Decimal) |
        (location ~ Predefined.Real).map(AST.Real) |
        (location ~ Predefined.LatLong).map(AST.LatLong) |
        (location ~ Predefined.DateTime).map(AST.DateTime) |
        (location ~ Predefined.Date).map(AST.Date) |
        (location ~ Predefined.TimeStamp).map(AST.TimeStamp) |
        (location ~ Predefined.Time).map(AST.Time) |
        (location ~ Predefined.URL).map(AST.URL)
    )
  }

  def patternType[_: P]: P[Pattern] = {
    P(
      location ~ Predefined.Pattern ~/
        roundOpen ~/ literalStrings ~ roundClose ~/ description
    ).map(tpl => (Pattern.apply _).tupled(tpl))
  }

  def uniqueIdType[_: P]: P[UniqueId] = {
    (location ~ Predefined.Id ~ roundOpen ~/ pathIdentifier.? ~ roundClose ~/
      description).map {
      case (loc, Some(id), add) => UniqueId(loc, id, add)
      case (loc, None, add) =>
        UniqueId(loc, PathIdentifier(loc, Seq.empty[String]), add)
    }
  }

  def enumerator[_: P]: P[Enumerator] = {
    P(identifier ~ aggregation.?).map {
      case (id, typex) =>
        Enumerator(id.loc, id, typex)
    }
  }

  def enumeration[_: P]: P[Enumeration] = {
    P(
      location ~ Keywords.any ~ Readability.of.? ~ open ~/
        enumerator.rep(1, sep = comma.?) ~ close ~ description
    ).map(enums => (Enumeration.apply _).tupled(enums))
  }

  def alternation[_: P]: P[Alternation] = {
    P(
      location ~ Keywords.one ~ Readability.of.? ~/
        open ~ typeExpression.rep(2, P("or" | "|" | ",")) ~
        close ~ description
    ).map { x =>
      (Alternation.apply _).tupled(x)
    }
  }

  def field[_: P]: P[(Identifier, TypeExpression)] = {
    P(identifier ~ is ~ typeExpression)
  }

  def fields[_: P]: P[Seq[(Identifier, TypeExpression)]] = {
    P(field.rep(1, comma))
  }

  def aggregation[_: P]: P[Aggregation] = {
    P(
      location ~ open ~ fields ~ close ~ description
    ).map {
      case (loc, types, addendum) =>
        Aggregation(
          loc,
          ListMap[Identifier, TypeExpression](types: _*),
          addendum
        )
    }
  }

  def mapping[_: P]: P[Mapping] = {
    P(
      location ~ "mapping" ~ open ~ "from" ~/ typeExpression ~ "to" ~
        typeExpression ~ close ~ description
    ).map { tpl =>
      (Mapping.apply _).tupled(tpl)
    }
  }

  def range[_: P]: P[RangeType] = {
    P(
      location ~ "range" ~ open ~
        "from" ~/ literalInteger ~ "to" ~ literalInteger ~
        close ~ description
    ).map { tpl =>
      (RangeType.apply _).tupled(tpl)
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

  def typeExpression[_: P]: P[TypeExpression] = {
    P(
      cardinality(
        P(
          simplePredefinedTypes | patternType | uniqueIdType |
            enumeration | alternation | referToType |
            aggregation | mapping | range | typeRef
        )
      )
    )
  }

  def typeDef[_: P]: P[Type] = {
    P(
      location ~ "type" ~/ identifier ~ is ~ typeExpression ~ description
    ).map { tpl =>
      (Type.apply _).tupled(tpl)
    }
  }
}
