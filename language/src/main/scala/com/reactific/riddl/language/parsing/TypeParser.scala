package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Terminals.*
import com.reactific.riddl.language.Terminals.Punctuation.*
import com.reactific.riddl.language.{AST, Location}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for Type definitions */
trait TypeParser extends ReferenceParser {

  def referToType[u: P]: P[ReferenceType] = {
    P(location ~ Keywords.reference ~ Readability.to ~/ entityRef).map { tpl =>
      (ReferenceType.apply _).tupled(tpl)
    }
  }

  def stringType[u: P]: P[Strng] = {
    P(
      location ~ Predefined.String ~
        (Punctuation.roundOpen ~ literalInteger.? ~ Punctuation.comma ~ literalInteger.? ~
          Punctuation.roundClose).?
    ).map {
      case (loc, Some((min, max))) => Strng(loc, min, max)
      case (loc, None)             => Strng(loc, None, None)
    }
  }

  def urlType[u: P]: P[URL] = {
    P(
      location ~ Predefined.URL ~ (Punctuation.roundOpen ~ literalString ~ Punctuation.roundClose).?
    ).map { tpl => (URL.apply _).tupled(tpl) }
  }

  def simplePredefinedTypes[u: P]: P[TypeExpression] = {
    P(
      stringType | urlType |
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
        (location ~ Predefined.UUID).map(AST.UUID) |
        (location ~ Predefined.Nothing).map(AST.Nothing) |
        (location ~ undefined(())).map(AST.Nothing)
    )
  }

  def patternType[u: P]: P[Pattern] = {
    P(
      location ~ Predefined.Pattern ~/ roundOpen ~/
        (literalStrings | Punctuation.undefined.!.map(_ => Seq.empty[LiteralString])) ~ roundClose./
    ).map(tpl => (Pattern.apply _).tupled(tpl))
  }

  def uniqueIdType[u: P]: P[UniqueId] = {
    (location ~ Predefined.Id ~ roundOpen ~/ pathIdentifier.? ~ roundClose./).map {
      case (loc, Some(id)) => UniqueId(loc, id)
      case (loc, None)     => UniqueId(loc, PathIdentifier(loc, Seq.empty[String]))
    }
  }

  def enumValue[u: P]: P[Option[LiteralInteger]] = {
    P(Punctuation.roundOpen ~ literalInteger ~ Punctuation.roundClose).?
  }

  def enumerator[u: P]: P[Enumerator] = {
    P(location ~ identifier ~ enumValue ~ briefly ~ description).map { tpl =>
      (Enumerator.apply _).tupled(tpl)
    }
  }

  /** Type reference parser that requires the 'type' keyword qualifier
    */
  def isTypeRef[u: P]: P[TypeRef] = { P(is ~/ Keywords.`type` ~/ typeRef) }

  def enumeration[u: P]: P[Enumeration] = {
    P(
      location ~ Keywords.any ~ Readability.of.? ~ open ~/
        (enumerator.rep(1, sep = comma.?) |
          Punctuation.undefined.!.map(_ => Seq.empty[Enumerator])) ~ close
    ).map(enums => (Enumeration.apply _).tupled(enums))
  }

  def alternation[u: P]: P[Alternation] = {
    P(
      location ~ Keywords.one ~ Readability.of.? ~/ open ~
        (Punctuation.undefined.!.map(_ => Seq.empty[TypeExpression]) |
          typeExpression.rep(0, P("or" | "|" | ","))) ~ close
    ).map { x => (Alternation.apply _).tupled(x) }
  }

  def field[u: P]: P[Field] = {
    P(location ~ identifier ~ is ~ typeExpression ~ briefly ~ description)
      .map(tpl => (Field.apply _).tupled(tpl))
  }

  def fields[u: P]: P[Seq[Field]] = {
    P(Punctuation.undefined.!.map(_ => Seq.empty[Field]) | field.rep(min = 0, comma))
  }

  def aggregation[u: P]: P[Aggregation] = {
    P(location ~ open ~ fields ~ close).map { case (loc, fields) => Aggregation(loc, fields) }
  }

  def messageKind[u: P]: P[MessageKind] = {
    P(StringIn(Keywords.command, Keywords.event, Keywords.query, Keywords.result).!).map { mk =>
      mk.toLowerCase() match {
        case kind if kind == Keywords.command => CommandKind
        case kind if kind == Keywords.event   => EventKind
        case kind if kind == Keywords.query   => QueryKind
        case kind if kind == Keywords.result  => ResultKind
      }
    }
  }

  def messageType[u: P]: P[MessageType] = {
    P(location ~ messageKind ~ aggregation).map { case (loc, mk, agg) =>
      MessageType(
        loc,
        mk,
        Field(
          loc,
          Identifier(loc, "sender"),
          ReferenceType(loc, EntityRef(loc, PathIdentifier(loc, Seq.empty[String])))
        ) +: agg.fields
      )
    }
  }

  /** Parses mappings, i.e.
    * {{{
    * mapping { from Integer to String }
    * }}}
    */
  def mapping[u: P]: P[Mapping] = {
    P(
      location ~ Keywords.mapping ~ Readability.from ~/ typeExpression ~ Readability.to ~
        typeExpression
    ).map { tpl => (Mapping.apply _).tupled(tpl) }
  }

  /** Parses ranges, i.e.
    * {{{
    *   range { from 1 to 2 }
    * }}}
    */
  def range[u: P]: P[RangeType] = {
    P(
      location ~ Keywords.range ~ roundOpen ~/
        literalInteger.?.map(_.getOrElse(LiteralInteger(Location.empty, 0))) ~ comma ~
        literalInteger.?.map(_.getOrElse(LiteralInteger(Location.empty, Int.MaxValue))) ~
        roundClose./
    ).map { tpl => (RangeType.apply _).tupled(tpl) }
  }

  def cardinality[u: P](p: => P[TypeExpression]): P[TypeExpression] = {
    P(
      Keywords.many.!.? ~ Keywords.optional.!.? ~ location ~ p ~
        StringIn(question, asterisk, plus, ellipsisQuestion, ellipsis).!.?
    ).map {
      case (None, None, loc, typ, Some("?"))       => Optional(loc, typ)
      case (None, None, loc, typ, Some("+"))       => OneOrMore(loc, typ)
      case (None, None, loc, typ, Some("*"))       => ZeroOrMore(loc, typ)
      case (Some(_), None, loc, typ, None)         => OneOrMore(loc, typ)
      case (None, Some(_), loc, typ, None)         => Optional(loc, typ)
      case (Some(_), Some(_), loc, typ, None)      => ZeroOrMore(loc, typ)
      case (None, Some(_), loc, typ, Some("?"))    => Optional(loc, typ)
      case (Some(_), None, loc, typ, Some("+"))    => OneOrMore(loc, typ)
      case (Some(_), Some(_), loc, typ, Some("*")) => ZeroOrMore(loc, typ)
      case (None, None, _, typ, None)              => typ
      case (_, _, loc, typ, _) =>
        error(loc, s"Cannot determine cardinality for $typ")
        typ
    }
  }

  def typeExpression[u: P]: P[TypeExpression] = {
    P(cardinality(P(
      simplePredefinedTypes | patternType | uniqueIdType | enumeration | alternation | referToType |
        aggregation | messageType | mapping | range | typeRef
    )))
  }

  def typeDef[u: P]: P[Type] = {
    P(location ~ Keywords.`type` ~/ identifier ~ is ~ typeExpression ~ briefly ~ description).map {
      tpl => (Type.apply _).tupled(tpl)
    }
  }
}
