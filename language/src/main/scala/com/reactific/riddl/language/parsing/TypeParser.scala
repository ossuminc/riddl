/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.{AST, At}
import fastparse.*
import fastparse.MultiLineWhitespace.*
import Readability.*

/** Parsing rules for Type definitions */
private[parsing] trait TypeParser extends CommonParser {

  private def entityReferenceType[u: P]: P[EntityReferenceTypeExpression] = {
    P(
      location ~ Keywords.reference ~ Readability.to.? ~/
        maybe(Keyword.entity) ~ pathIdentifier
    ).map { tpl => (EntityReferenceTypeExpression.apply _).tupled(tpl) }
  }

  private def stringType[u: P]: P[Strng] = {
    P(
      location ~ PredefTypes.String_ ~/
        (Punctuation.roundOpen ~ integer.? ~ Punctuation.comma ~ integer.? ~
          Punctuation.roundClose).?
    ).map {
      case (loc, Some((min, max))) => Strng(loc, min, max)
      case (loc, None)             => Strng(loc, None, None)
    }
  }

  private def isoCountryCode[u: P]: P[String] = {
    P(
      StringIn(
        "AFN",
        "AED",
        "AMD",
        "ANG",
        "AOA",
        "ARS",
        "AUD",
        "AWG",
        "AZN",
        "BAM",
        "BBD",
        "BDT",
        "BGN",
        "BHD",
        "BIF",
        "BMD",
        "BND",
        "BOB",
        "BOV",
        "BRL",
        "BSD",
        "BTN",
        "BWP",
        "BYN",
        "BZD",
        "CAD",
        "CDF",
        "CHE",
        "CHF",
        "CHW",
        "CLF",
        "CLP",
        "CNY",
        "COP",
        "COU",
        "CRC",
        "CUC",
        "CUP",
        "CVE",
        "CZK",
        "DJF",
        "DKK",
        "DOP",
        "EGP",
        "ERN",
        "ETB",
        "EUR",
        "FJD",
        "FKP",
        "GBP",
        "GEL",
        "GHS",
        "GIP",
        "GMD",
        "GNF",
        "GTQ",
        "GYD",
        "HKD",
        "HNL",
        "HRK",
        "HTG",
        "HUF",
        "IDR",
        "ILS",
        "INR",
        "IQD",
        "IRR",
        "ISK",
        "JMD",
        "JOD",
        "JPY",
        "KES",
        "KGS",
        "KHR",
        "KMF",
        "KPW",
        "KRW",
        "KWD",
        "KYD",
        "KZT",
        "LAK",
        "LBP",
        "LKR",
        "LRD",
        "LSL",
        "LYD",
        "MAD",
        "MDL",
        "MGA",
        "MKD",
        "MMK",
        "MNT",
        "MOP",
        "MRU",
        "MUR",
        "MVR",
        "MWK",
        "MXN",
        "MXV",
        "MYR",
        "MZN",
        "NAD",
        "NGN",
        "NIO",
        "NOK",
        "NPR",
        "NZD",
        "OMR",
        "PEN",
        "PGK",
        "PHP",
        "PKR",
        "PLN",
        "PYG",
        "QAR",
        "RON",
        "RSD",
        "RUB",
        "RWF",
        "SAR",
        "SBD",
        "SCR",
        "SDG",
        "SEK",
        "SGD",
        "SHP",
        "SLE",
        "SOS",
        "SRD",
        "STN",
        "SVC",
        "SYP",
        "SZL",
        "THB",
        "TJS",
        "TMT",
        "TND",
        "TOP",
        "TRY",
        "TTD",
        "TWD",
        "TZS",
        "UAH",
        "UGX",
        "USD",
        "USN",
        "UYI",
        "UYU",
        "UZS",
        "VED",
        "VEF",
        "VND",
        "VUV",
        "WST",
        "XAF",
        "XCD",
        "XDR",
        "XOF",
        "XPF",
        "XSU",
        "XUA",
        "YER",
        "ZAR",
        "ZMW",
        "ZWL"
      ).!
    )
  }

  private def currencyType[u: P]: P[Currency] = {
    P(
      location ~ PredefTypes.Currency ~/
        (Punctuation.roundOpen ~ isoCountryCode ~ Punctuation.roundClose)
    ).map { tpl => (Currency.apply _).tupled(tpl) }
  }

  private def urlType[u: P]: P[URL] = {
    P(
      location ~ PredefTypes.URL ~/
        (Punctuation.roundOpen ~ literalString ~ Punctuation.roundClose).?
    ).map { tpl => (URL.apply _).tupled(tpl) }
  }

  private def integerPredefTypes[u: P]: P[IntegerTypeExpression] = {
    P(
      location ~ PredefTypes.integerTypes | rangeType
    ).map {
      case (at, PredefType.Boolean) => AST.Bool(at)
      case (at, PredefType.Integer) => AST.Integer(at)
      case (at, PredefType.Natural) => AST.Natural(at)
      case (at, PredefType.Whole)   => AST.Whole(at)
      case (at, _: String) =>
        assert(true) // shouldn't happen
        AST.Integer(at)
      case range: RangeType => range
    }
  }

  private def realPredefTypes[u: P]: P[RealTypeExpression] = {
    P(
      location ~ PredefTypes.realTypes
    ).map {
      case (at: At, PredefType.Current)     => Current(at)
      case (at: At, PredefType.Length)      => Length(at)
      case (at: At, PredefType.Luminosity)  => Luminosity(at)
      case (at: At, PredefType.Mass)        => Mass(at)
      case (at: At, PredefType.Mole)        => Mole(at)
      case (at: At, PredefType.Number)      => Number(at)
      case (at: At, PredefType.Real)        => Real(at)
      case (at: At, PredefType.Temperature) => Temperature(at)
    }
  }

  private def timePredefTypes[u: P]: P[TypeExpression] = {
    P(
      location ~ PredefTypes.timeTypes
    ).map {
      case (at: At, PredefType.Duration) => Duration(at)
      case (at, PredefType.DateTime)     => DateTime(at)
      case (at, PredefType.Date)         => Date(at)
      case (at, PredefType.TimeStamp)    => TimeStamp(at)
      case (at, PredefType.Time)         => Time(at)
    }
  }

  private def otherPredefTypes[u: P]: P[TypeExpression] = {
    P(
      location ~ PredefTypes.otherTypes
    ).map {
      case (at, PredefType.Abstract) => AST.Abstract(at)
      case (at, PredefType.Location) => AST.Location(at)
      case (at, PredefType.Nothing)  => AST.Nothing(at)
      case (at, PredefType.Natural)  => AST.Natural(at)
      case (at, PredefType.Number)   => AST.Number(at)
      case (at, PredefType.UUID)     => AST.UUID(at)
      case (at, PredefType.UserId)   => AST.UserId(at)
      case (at, _) =>
        error("Unrecognized predefined type")
        AST.Abstract(at)
    }
  }

  private def predefinedTypes[u: P]: P[TypeExpression] = {
    P(
      stringType | currencyType | urlType | integerPredefTypes | realPredefTypes | timePredefTypes |
        decimalType | otherPredefTypes
    )./
  }

  private def decimalType[u: P]: P[Decimal] = {
    P(
      location ~ PredefType.Decimal ~/ Punctuation.roundOpen ~
        integer ~ Punctuation.comma ~ integer ~
        Punctuation.roundClose
    )./.map(tpl => (Decimal.apply _).tupled(tpl))
  }

  private def patternType[u: P]: P[Pattern] = {
    P(
      location ~ PredefType.Pattern ~/ Punctuation.roundOpen ~
        (literalStrings |
          Punctuation.undefinedMark.!.map(_ => Seq.empty[LiteralString])) ~
        Punctuation.roundClose./
    ).map(tpl => (Pattern.apply _).tupled(tpl))
  }

  private def uniqueIdType[u: P]: P[UniqueId] = {
    (location ~ PredefType.Id ~ Punctuation.roundOpen ~/
      maybe(Keyword.entity) ~ pathIdentifier ~ Punctuation.roundClose./) map { case (loc, pid) =>
      UniqueId(loc, pid)
    }
  }

  private def enumValue[u: P]: P[Option[Long]] = {
    P(Punctuation.roundOpen ~ integer ~ Punctuation.roundClose./).?
  }

  private def enumerator[u: P]: P[Enumerator] = {
    P(location ~ identifier ~ enumValue ~ briefly ~ description ~ endOfLineComment).map { tpl =>
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

  private def alternation[u: P]: P[Alternation] = {
    P(
      location ~ Keywords.one ~ Readability.of.? ~/ open ~
        (Punctuation.undefinedMark.!.map(_ => Seq.empty[AliasedTypeExpression]) |
          aliasedTypeExpression.rep(0, P("or" | "|" | ","))) ~ close./
    ).map { x => (Alternation.apply _).tupled(x) }
  }

  private def aliasedTypeExpression[u: P]: P[AliasedTypeExpression] = {
    P(
      location ~ Keywords.aggregateTypes.? ~ pathIdentifier
    )./.map {
      case (loc, Some(key), pid) => AliasedTypeExpression(loc, key, pid)
      case (loc, None, pid)      => AliasedTypeExpression(loc, "type", pid)
    }
  }

  private def fieldTypeExpression[u: P]: P[TypeExpression] = {
    P(
      cardinality(
        predefinedTypes | patternType | uniqueIdType |
          enumeration | aliasedTypeExpression | aliasedTypeExpression | sequenceType | rangeType |
          alternation | entityReferenceType | aliasedTypeExpression
      )
    )
  }

  def field[u: P]: P[Field] = {
    P(
      location ~ identifier ~ is ~ fieldTypeExpression ~ briefly ~ description ~ endOfLineComment
    ).map(tpl => (Field.apply _).tupled(tpl))
  }

  def arguments[u: P]: P[Seq[MethodArgument]] = {
    P(
      (
        location ~ identifier.map(_.value) ~ Punctuation.colon ~ fieldTypeExpression
      ).map(tpl => (MethodArgument.apply _).tupled(tpl))
    ).rep(min = 0, Punctuation.comma)
  }

  def method[u: P]: P[Method] = {
    P(
      location ~ identifier ~ Punctuation.roundOpen ~ arguments ~ Punctuation.roundClose ~
        is ~ fieldTypeExpression ~ briefly ~ description ~ endOfLineComment
    ).map(tpl => (Method.apply _).tupled(tpl))
  }

  private def aggregateDefinitions[u: P]: P[Seq[AggregateDefinition]] = {
    P(
      undefined(Seq.empty[AggregateDefinition]) |
        (field | method).rep(min = 1, Punctuation.comma)
    )
  }

  def aggregation[u: P]: P[Aggregation] = {
    P(location ~ open ~ aggregateDefinitions ~ close).map { case (loc, contents) =>
      val groups = contents.groupBy(_.getClass)
      val fields = mapTo[Field](groups.get(classOf[Field]))
      val methods = mapTo[Method](groups.get(classOf[Method]))
      Aggregation(loc, fields, methods)
    }
  }

  private def aggregateUseCase[u: P]: P[AggregateUseCase] = {
    P(
      Keywords.aggregateTypes
    ).map { mk =>
      mk.toLowerCase() match {
        case kind if kind == Keyword.type_   => TypeCase
        case kind if kind == Keyword.command => CommandCase
        case kind if kind == Keyword.event   => EventCase
        case kind if kind == Keyword.query   => QueryCase
        case kind if kind == Keyword.result  => ResultCase
        case kind if kind == Keyword.record  => RecordCase
      }
    }
  }

  private def makeAggregateUseCaseType(
    loc: At,
    mk: AggregateUseCase,
    agg: Aggregation
  ): AggregateUseCaseTypeExpression = {
    AggregateUseCaseTypeExpression(loc, mk, agg.fields, agg.methods)
  }

  private def aggregateUseCaseTypeExpression[u: P]: P[AggregateUseCaseTypeExpression] = {
    P(location ~ aggregateUseCase ~ aggregation).map { case (loc, mk, agg) =>
      makeAggregateUseCaseType(loc, mk, agg)
    }
  }

  /** Parses mappings, i.e.
    * {{{
    *   mapping from Integer to String
    * }}}
    */
  private def mappingFromTo[u: P]: P[Mapping] = {
    P(
      location ~ Keywords.mapping ~ Readability.from ~/ typeExpression ~
        Readability.to ~ typeExpression
    ).map { tpl => (Mapping.apply _).tupled(tpl) }
  }

  /** Parses sets, i.e.
    * {{{
    *   set of String
    * }}}
    */
  private def set[u: P]: P[Set] = {
    P(
      location ~ Keywords.set ~ Readability.of ~ typeExpression
    )./.map { tpl => (Set.apply _).tupled(tpl) }
  }

  /** Parses sequences, i.e.
    * {{{
    *     sequence of String
    * }}}
    */
  private def sequenceType[u: P]: P[Sequence] = {
    P(
      location ~ Keywords.sequence ~ Readability.of ~ typeExpression
    )./.map { tpl => (Sequence.apply _).tupled(tpl) }
  }

  /** Parses ranges, i.e.
    * {{{
    *   range(1,2)
    * }}}
    */
  private def rangeType[u: P]: P[RangeType] = {
    P(
      location ~ Keywords.range ~ Punctuation.roundOpen ~/
        integer.?.map(_.getOrElse(0L)) ~ Punctuation.comma ~
        integer.?.map(_.getOrElse(Long.MaxValue)) ~ Punctuation.roundClose./
    ).map { tpl => (RangeType.apply _).tupled(tpl) }
  }

  private def cardinality[u: P](p: => P[TypeExpression]): P[TypeExpression] = {
    P(
      Keywords.many.!.? ~ Keywords.optional.!.? ~ location ~ p ~ StringIn(
        Punctuation.question,
        Punctuation.asterisk,
        Punctuation.plus
      ).!.?
    ).map {
      case (None, None, loc, typ, Some("?"))                     => Optional(loc, typ)
      case (None, None, loc, typ, Some("+"))                     => OneOrMore(loc, typ)
      case (None, None, loc, typ, Some("*"))                     => ZeroOrMore(loc, typ)
      case (Some("many"), None, loc, typ, None)                  => OneOrMore(loc, typ)
      case (None, Some("optional"), loc, typ, None)              => Optional(loc, typ)
      case (Some("many"), Some("optional"), loc, typ, None)      => ZeroOrMore(loc, typ)
      case (None, Some("optional"), loc, typ, Some("?"))         => Optional(loc, typ)
      case (Some("many"), None, loc, typ, Some("+"))             => OneOrMore(loc, typ)
      case (Some("many"), Some("optional"), loc, typ, Some("*")) => ZeroOrMore(loc, typ)
      case (None, None, _, typ, None)                            => typ
      case (_, _, loc, typ, _) =>
        error(loc, s"Cannot determine cardinality for $typ")
        typ
    }
  }

  private def typeExpression[u: P]: P[TypeExpression] = {
    P(
      cardinality(
        predefinedTypes | patternType | uniqueIdType | enumeration | sequenceType |
          aggregateUseCaseTypeExpression | mappingFromTo | rangeType |
          decimalType | alternation | entityReferenceType |
          aggregation | aggregateUseCaseTypeExpression | aliasedTypeExpression
      )
    )
  }

  def replicaTypeExpression[u: P]: P[TypeExpression] = {
    P(integerPredefTypes | mappingFromTo | set)
  }

  private def defOfTypeKindType[u: P]: P[Type] = {
    P(
      location ~ aggregateUseCase ~/ identifier ~ is ~ (aliasedTypeExpression | aggregation) ~ briefly ~
        description ~ endOfLineComment
    ).map { case (loc, useCase, id, ateOrAgg, brief, description, comment) =>
      ateOrAgg match {
        case agg: Aggregation =>
          val mt = AggregateUseCaseTypeExpression(agg.loc, useCase, agg.fields, agg.methods)
          Type(loc, id, mt, brief, description, comment)
        case ate: AliasedTypeExpression =>
          Type(loc, id, ate, brief, description, comment)
        case _ =>
          require(false, "Oops! Impossible case")
          // Type just to satisfy compiler because it doesn't know require(false...) will throw
          Type(loc, id, Nothing(loc), brief, description, comment)
      }
    }
  }

  private def defOfType[u: P]: P[Type] = {
    P(
      location ~ Keywords.type_ ~/ identifier ~ is ~ typeExpression ~ briefly ~
        description ~ endOfLineComment
    ).map { case (loc, id, typ, brief, description, comment) =>
      Type(loc, id, typ, brief, description, comment)
    }
  }

  def typeDef[u: P]: P[Type] = { defOfType | defOfTypeKindType }

  def types[u: P]: P[Seq[Type]] = { typeDef.rep(0) }

  def constant[u: P]: P[Constant] = {
    P(
      location ~ Keywords.constant ~ identifier ~ is ~ typeExpression ~
        Punctuation.equalsSign ~ literalString ~ briefly ~ description ~ endOfLineComment
    ).map { tpl => (Constant.apply _).tupled(tpl) }
  }

}
