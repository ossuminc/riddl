package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._

import Terminals._
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

  def stringType[_: P]: P[Strng] = {
    P(
      location ~ Predefined.String ~ (
        Punctuation.roundOpen ~ literalInteger.? ~
          Punctuation.comma ~ literalInteger.? ~
          Punctuation.roundClose
      ).?
    ).map {
      case (loc, Some((min, max))) => Strng(loc, min, max)
      case (loc, None)             => Strng(loc, None, None)
    }
  }

  def urlType[_: P]: P[URL] = {
    P(
      location ~ Predefined.URL ~
        (Punctuation.roundOpen ~ literalString ~ Punctuation.roundClose).?
    ).map { tpl =>
      (URL.apply _).tupled(tpl)
    }
  }

  def simplePredefinedTypes[_: P]: P[TypeExpression] = {
    P(
      stringType |
        urlType |
        (location ~ Predefined.Boolean).map(AST.Bool) |
        (location ~ Predefined.Number).map(AST.Number) |
        (location ~ Predefined.Integer).map(AST.Integer) |
        (location ~ Predefined.Decimal).map(AST.Decimal) |
        (location ~ Predefined.Real).map(AST.Real) |
        (location ~ Predefined.Duration).map(AST.Duration) |
        (location ~ Predefined.LatLong).map(AST.LatLong) |
        (location ~ Predefined.DateTime).map(AST.DateTime) |
        (location ~ Predefined.Date).map(AST.Date) |
        (location ~ Predefined.TimeStamp).map(AST.TimeStamp) |
        (location ~ Predefined.Time).map(AST.Time) |
        (location ~ Predefined.Nothing).map(AST.Nothing) |
        (location ~ undefined).map(AST.Nothing)
    )
  }

  def patternType[_: P]: P[Pattern] = {
    P(
      location ~ Predefined.Pattern ~/ roundOpen ~/ (
        literalStrings |
          Punctuation.undefined.!.map(_ => Seq.empty[LiteralString])
      ) ~ roundClose ~/ description
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

  def enumValue[_: P]: P[Option[LiteralInteger]] = {
    P(Punctuation.roundOpen ~ literalInteger ~ Punctuation.roundClose).?
  }

  def enumerator[_: P]: P[Enumerator] = {
    P(identifier ~ enumValue ~ aggregationWithoutDescription.? ~ description)
      .map {
        case (id, enumVal, typex, desc) =>
          Enumerator(id.loc, id, enumVal, typex, desc)
      }
  }

  def enumeration[_: P]: P[Enumeration] = {
    P(
      location ~ Keywords.any ~ Readability.of.? ~ open ~/
        (enumerator.rep(1, sep = comma.?) |
          Punctuation.undefined.!.map(_ => Seq.empty[Enumerator])) ~ close ~ description
    ).map(enums => (Enumeration.apply _).tupled(enums))
  }

  def alternation[_: P]: P[Alternation] = {
    P(
      location ~ Keywords.one ~ Readability.of.? ~/
        open ~ (
        typeExpression.rep(2, P("or" | "|" | ",")) |
          Punctuation.undefined.!.map(_ => Seq.empty[TypeExpression])
      ) ~ close ~ description
    ).map { x =>
      (Alternation.apply _).tupled(x)
    }
  }

  def field[_: P]: P[Field] = {
    P(location ~ identifier ~ is ~ typeExpression ~ description)
      .map(tpl => (Field.apply _).tupled(tpl))
  }

  def fields[_: P]: P[Seq[Field]] = {
    P(
      field.rep(min = 0, comma) |
        Punctuation.undefined.!.map(_ => Seq.empty[Field])
    )
  }

  def aggregationWithoutDescription[_: P]: P[Aggregation] = {
    P(
      location ~ open ~ fields ~ close
    ).map { case (loc, fields) => Aggregation(loc, fields, None) }
  }

  def aggregation[_: P]: P[Aggregation] = {
    P(
      aggregationWithoutDescription ~ description
    ).map { case (agg, desc) => agg.copy(description = desc) }
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
      Keywords.many.!.? ~ Keywords.optional.!.? ~
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
