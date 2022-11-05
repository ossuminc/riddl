/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.AST
import com.reactific.riddl.language.ast.Location
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for Type definitions */
trait TypeParser extends CommonParser {

  def entityReferenceType[u: P]: P[EntityReferenceTypeExpression] = {
    P(
      location ~ Keywords.reference ~ Readability.to.? ~/
        maybe(Keywords.entity) ~ pathIdentifier
    ).map { tpl => (EntityReferenceTypeExpression.apply _).tupled(tpl) }
  }

  def stringType[u: P]: P[Strng] = {
    P(
      location ~ Predefined.String ~/
        (Punctuation.roundOpen ~ integer.? ~ Punctuation.comma ~ integer.? ~
          Punctuation.roundClose).?
    ).map {
      case (loc, Some((min, max))) => Strng(loc, min, max)
      case (loc, None)             => Strng(loc, None, None)
    }
  }

  def isoCountryCode[u: P]: P[String] = { P(CharIn("A-Z").rep(2, "", 2).!) }

  def currencyType[u: P]: P[Currency] = {
    P(
      location ~ Predefined.Currency ~/
        (Punctuation.roundOpen ~ isoCountryCode ~ Punctuation.roundClose)
    ).map { tpl => (Currency.apply _).tupled(tpl) }
  }

  def urlType[u: P]: P[URL] = {
    P(
      location ~ Predefined.URL ~/
        (Punctuation.roundOpen ~ literalString ~ Punctuation.roundClose).?
    ).map { tpl => (URL.apply _).tupled(tpl) }
  }

  def simplePredefinedTypes[u: P]: P[TypeExpression] = {
    P(
      stringType | currencyType | urlType |
        (location ~ Predefined.Abstract).map(AST.Abstract) |
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
        (location ~ Predefined.Length).map(AST.Length) |
        (location ~ Predefined.Luminosity).map(AST.Luminosity) |
        (location ~ Predefined.Mass).map(AST.Mass) |
        (location ~ Predefined.Mole).map(AST.Mole) |
        (location ~ Predefined.Temperature).map(AST.Temperature) |
        (location ~ Predefined.Current).map(AST.Current) |
        (location ~ Predefined.Nothing).map(AST.Nothing) |
        (location ~ Punctuation.undefinedMark).map(AST.Abstract)
    )./
  }

  def patternType[u: P]: P[Pattern] = {
    P(
      location ~ Predefined.Pattern ~/ Punctuation.roundOpen ~
        (literalStrings |
          Punctuation.undefinedMark.!.map(_ => Seq.empty[LiteralString])) ~
        Punctuation.roundClose./
    ).map(tpl => (Pattern.apply _).tupled(tpl))
  }

  def uniqueIdType[u: P]: P[UniqueId] = {
    (location ~ Predefined.Id ~ Punctuation.roundOpen ~/
      maybe(Keywords.entity) ~ pathIdentifier.? ~ Punctuation.roundClose./)
      .map {
        case (loc, Some(pid)) => UniqueId(loc, pid)
        case (loc, None) =>
          UniqueId(loc, PathIdentifier(loc, Seq.empty[String]))
      }
  }

  def enumValue[u: P]: P[Option[Long]] = {
    P(Punctuation.roundOpen ~ integer ~ Punctuation.roundClose./).?
  }

  def enumerator[u: P]: P[Enumerator] = {
    P(location ~ identifier ~ enumValue ~ briefly ~ description).map { tpl =>
      (Enumerator.apply _).tupled(tpl)
    }
  }

  def enumeration[u: P]: P[Enumeration] = {
    P(
      location ~ Keywords.any ~ Readability.of.? ~/ open ~/
        (enumerator.rep(1, sep = Punctuation.comma.?) |
          Punctuation.undefinedMark.!.map(_ => Seq.empty[Enumerator])) ~ close./
    ).map(enums => (Enumeration.apply _).tupled(enums))
  }

  def alternation[u: P]: P[Alternation] = {
    P(
      location ~ Keywords.one ~ Readability.of.? ~/ open ~
        (Punctuation.undefinedMark.!
          .map(_ => Seq.empty[AliasedTypeExpression]) |
          aliasedTypeExpression.rep(0, P("or" | "|" | ","))) ~ close./
    ).map { x => (Alternation.apply _).tupled(x) }
  }

  def aliasedTypeExpression[u: P]: P[AliasedTypeExpression] = {
    P(location ~ maybe(Keywords.`type`) ~ pathIdentifier)./
      .map(tpl => (AliasedTypeExpression.apply _).tupled(tpl))
  }

  def fieldTypeExpression[u: P]: P[TypeExpression] = {
    P(cardinality(
      simplePredefinedTypes./ | patternType | uniqueIdType | enumeration |
        alternation | entityReferenceType | mappingType | rangeType |
        aliasedTypeExpression
    ))
  }

  def field[u: P]: P[Field] = {
    P(location ~ identifier ~ is ~ fieldTypeExpression ~ briefly ~ description)
      .map(tpl => (Field.apply _).tupled(tpl))
  }

  def fields[u: P]: P[Seq[Field]] = {
    P(
      Punctuation.undefinedMark.!.map(_ => Seq.empty[Field]) |
        field.rep(min = 0, Punctuation.comma)
    )
  }

  def aggregation[u: P]: P[Aggregation] = {
    P(location ~ Keywords.fields.? ~ open ~ fields ~ close).map {
      case (loc, fields) => Aggregation(loc, fields)
    }
  }

  def messageKind[u: P]: P[MessageKind] = {
    P(
      StringIn(
        Keywords.command,
        Keywords.event,
        Keywords.query,
        Keywords.result
      ).!
    ).map { mk =>
      mk.toLowerCase() match {
        case kind if kind == Keywords.command => CommandKind
        case kind if kind == Keywords.event   => EventKind
        case kind if kind == Keywords.query   => QueryKind
        case kind if kind == Keywords.result  => ResultKind
      }
    }
  }

  def makeMessageType(
    loc: Location,
    mk: MessageKind,
    agg: Aggregation
  ): MessageType = { MessageType(loc, mk, agg.fields) }

  def messageType[u: P]: P[MessageType] = {
    P(location ~ messageKind ~ aggregation).map { case (loc, mk, agg) =>
      makeMessageType(loc, mk, agg)
    }
  }

  /** Parses mappings, i.e.
    * {{{
    *   mapping from Integer to String
    * }}}
    */
  def mappingType[u: P]: P[Mapping] = {
    P(
      location ~ Keywords.mapping ~ Readability.from ~/ typeExpression ~
        Readability.to ~ typeExpression
    ).map { tpl => (Mapping.apply _).tupled(tpl) }
  }

  /** Parses ranges, i.e.
    * {{{
    *   range(1,2)
    * }}}
    */
  def rangeType[u: P]: P[RangeType] = {
    P(
      location ~ Keywords.range ~ Punctuation.roundOpen ~/
        integer.?.map(_.getOrElse(0L)) ~ Punctuation.comma ~
        integer.?.map(_.getOrElse(Long.MaxValue)) ~ Punctuation.roundClose./
    ).map { tpl => (RangeType.apply _).tupled(tpl) }
  }

  def cardinality[u: P](p: => P[TypeExpression]): P[TypeExpression] = {
    P(
      Keywords.many.!.? ~ Keywords.optional.!.? ~ location ~ p ~ StringIn(
        Punctuation.question,
        Punctuation.asterisk,
        Punctuation.plus,
        Punctuation.ellipsisQuestion,
        Punctuation.ellipsis
      ).!.?
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
    P(cardinality(
      simplePredefinedTypes | patternType | uniqueIdType | enumeration |
        alternation | entityReferenceType | aggregation | messageType |
        mappingType | rangeType | aliasedTypeExpression
    ))
  }

  def typeDef[u: P]: P[Type] = {
    P(
      (location ~ Keywords.`type` ~/ identifier ~ is ~ typeExpression ~
        briefly ~ description).map { tpl => (Type.apply _).tupled(tpl) } |
        (location ~ messageKind ~/ identifier ~ is ~ location ~ aggregation ~
          briefly ~ description).map { case (loc, mk, id, loc2, agg, b, d) =>
          val mt = makeMessageType(loc2, mk, agg)
          Type(loc, id, mt, b, d)
        }
    )
  }

  def types[u: P]: P[Seq[Type]] = { typeDef.rep(0) }
}
