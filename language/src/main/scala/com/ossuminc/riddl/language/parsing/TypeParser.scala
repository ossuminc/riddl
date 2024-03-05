/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, At}
import fastparse.*
import fastparse.MultiLineWhitespace.*
import Readability.*

/** Parsing rules for Type definitions */
private[parsing] trait TypeParser extends CommonParser {

  private def entityReferenceType[u: P]: P[EntityReferenceTypeExpression] = {
    P(
      location ~ Keywords.reference ~ Readability.to.? ~/
        maybe(Keyword.entity) ~ pathIdentifier
    ).map { tpl => EntityReferenceTypeExpression.apply.tupled(tpl) }
  }

  private def stringType[u: P]: P[String_] = {
    P(
      location ~ PredefTypes.String_ ~/
        (Punctuation.roundOpen ~ integer.? ~ Punctuation.comma ~ integer.? ~
          Punctuation.roundClose).?
    ).map {
      case (loc, Some((min, max))) => String_(loc, min, max)
      case (loc, None)             => String_(loc, None, None)
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
    ).map { tpl => Currency.apply.tupled(tpl) }
  }

  private def urlType[u: P]: P[URL] = {
    P(
      location ~ PredefTypes.URL ~/
        (Punctuation.roundOpen ~ literalString ~ Punctuation.roundClose).?
    ).map { tpl => URL.apply.tupled(tpl) }
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
    )./.map(tpl => Decimal.apply.tupled(tpl))
  }

  private def patternType[u: P]: P[Pattern] = {
    P(
      location ~ PredefType.Pattern ~/ Punctuation.roundOpen ~
        (literalStrings |
          Punctuation.undefinedMark.!.map(_ => Seq.empty[LiteralString])) ~
        Punctuation.roundClose./
    ).map(tpl => Pattern.apply.tupled(tpl))
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
    P(location ~ identifier ~ enumValue ~ briefly ~ description).map { tpl =>
      Enumerator.apply.tupled(tpl)
    }
  }

  private def enumerators[u: P]: P[Seq[Enumerator]] = {
    enumerator.rep(1, maybe(Punctuation.comma)) | undefined[u, Seq[Enumerator]](Seq.empty[Enumerator])

  }

  def enumeration[u: P]: P[Enumeration] = {
    P(
      location ~ Keywords.any ~ Readability.of.? ~/ open ~/ enumerators ~ close./
    ).map(enums => Enumeration.apply.tupled(enums))
  }

  private def alternation[u: P]: P[Alternation] = {
    P(
      location ~ Keywords.one ~ Readability.of.? ~/ open ~
        (Punctuation.undefinedMark.!.map(_ => Seq.empty[AliasedTypeExpression]) |
          aliasedTypeExpression.rep(0, P("or" | "|" | ","))) ~ close./
    ).map { x =>
      Alternation.apply.tupled(x)
    }
  }

  private def aliasedTypeExpression[u: P]: P[AliasedTypeExpression] = {
    P(
      location ~ Keywords.typeKeywords.? ~ pathIdentifier
    )./.map {
      case (loc, Some(key), pid) =>
        AliasedTypeExpression(loc, key, pid)
      case (loc, None, pid) =>
        AliasedTypeExpression(loc, "type", pid)
    }
  }

  private def fieldTypeExpression[u: P]: P[TypeExpression] = {
    P(
      cardinality(
        predefinedTypes | patternType | uniqueIdType | enumeration | sequenceType | mappingFromTo | aSetType |
          graphType | tableType | replicaType | rangeType | decimalType | alternation | aggregation |
          aliasedTypeExpression | entityReferenceType
      )
    )
  }

  def field[u: P]: P[Field] = {
    P(
      location ~ identifier ~ is ~ fieldTypeExpression ~ briefly ~ description
    ).map(tpl => Field.apply.tupled(tpl))
  }

  def arguments[u: P]: P[Seq[MethodArgument]] = {
    P(
      (
        location ~ identifier.map(_.value) ~ Punctuation.colon ~ fieldTypeExpression
      ).map(tpl => MethodArgument.apply.tupled(tpl))
    ).rep(min = 0, Punctuation.comma)
  }

  def method[u: P]: P[Method] = {
    P(
      location ~ identifier ~ Punctuation.roundOpen ~ arguments ~ Punctuation.roundClose ~
        is ~ fieldTypeExpression ~ briefly ~ description
    ).map(tpl => Method.apply.tupled(tpl))
  }

  private def aggregateContent[u: P]: P[RiddlValue] = {
    P(field | method | comment)
  }

  private def aggregateDefinitions[u: P]: P[Seq[RiddlValue]] = {
    P(
      undefined(Seq.empty[RiddlValue]) | aggregateContent.rep(min = 1, Punctuation.comma.?)
    )
  }

  def aggregation[u: P]: P[Aggregation] = {
    P(location ~ open ~ aggregateDefinitions ~ close).map { case (loc, contents) =>
      Aggregation(loc, contents)
    }
  }

  private def aggregateUseCase[u: P]: P[AggregateUseCase] = {
    P(
      Keywords.typeKeywords
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
    AggregateUseCaseTypeExpression(loc, mk, agg.contents)
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
    ).map(tpl => Mapping.apply.tupled(tpl))
  }

  /** Parses sets, i.e.
    * {{{
    *   set of String
    * }}}
    */
  private def aSetType[u: P]: P[Set] = {
    P(
      location ~ Keywords.set ~ Readability.of ~ typeExpression
    )./.map { tpl => Set.apply.tupled(tpl) }
  }

  /** Parses sequences, i.e.
    * {{{
    *     sequence of String
    * }}}
    */
  private def sequenceType[u: P]: P[Sequence] = {
    P(
      location ~ Keywords.sequence ~ Readability.of ~ typeExpression
    )./.map { tpl => Sequence.apply.tupled(tpl) }
  }

  /** Parses graphs whose nodes can be any type */
  private def graphType[u: P]: P[Graph] = {
    P(location ~ Keywords.graph ~ Readability.of ~ typeExpression)./.map { tpl => Graph.apply.tupled(tpl) }
  }

  /** Parses tables of at least one dimension of cells of an arbitrary type */
  private def tableType[u: P]: P[Table] = {
    P(
      location ~ Keywords.table ~ Readability.of ~ typeExpression ~ Readability.of ~ Punctuation.squareOpen ~
        integer.rep(1, ",") ~ Punctuation.squareClose
    )./.map { tpl => Table.apply.tupled(tpl) }
  }

  private def replicaType[x: P]: P[Replica] = {
    P(
      location ~ Keywords.replica ~ Readability.of ~ replicaTypeExpression
    ).map { tpl => Replica.apply.tupled(tpl) }
  }

  private def replicaTypeExpression[u: P]: P[TypeExpression] = {
    P(integerPredefTypes | mappingFromTo | aSetType)
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
    ).map { tpl => RangeType.apply.tupled(tpl) }
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
        predefinedTypes | patternType | uniqueIdType | enumeration | sequenceType | mappingFromTo | aSetType |
          graphType | tableType | replicaType | aggregateUseCaseTypeExpression | rangeType | decimalType |
          alternation | entityReferenceType | aggregation | aggregateUseCaseTypeExpression | aliasedTypeExpression
      )
    )
  }

  private def defOfTypeKindType[u: P]: P[Type] = {
    P(
      location ~ aggregateUseCase ~/ identifier ~ is ~ (aliasedTypeExpression | aggregation) ~ briefly ~
        description
    ).map { case (loc, useCase, id, ateOrAgg, brief, description) =>
      ateOrAgg match {
        case agg: Aggregation =>
          val mt = AggregateUseCaseTypeExpression(agg.loc, useCase, agg.contents)
          Type(loc, id, mt, brief, description)
        case ate: AliasedTypeExpression =>
          Type(loc, id, ate, brief, description)
        case _ =>
          require(false, "Oops! Impossible case")
          // Type just to satisfy compiler because it doesn't know require(false...) will throw
          Type(loc, id, Nothing(loc), brief, description)
      }
    }
  }

  private def defOfType[u: P]: P[Type] = {
    P(
      location ~ Keywords.type_ ~/ identifier ~ is ~ typeExpression ~ briefly ~ description
    ).map { case (loc, id, typ, brief, description) =>
      Type(loc, id, typ, brief, description)
    }
  }

  def typeDef[u: P]: P[Type] = { defOfType | defOfTypeKindType }

  def types[u: P]: P[Seq[Type]] = { typeDef.rep(0) }

  def constant[u: P]: P[Constant] = {
    P(
      location ~ Keywords.constant ~ identifier ~ is ~ typeExpression ~
        Punctuation.equalsSign ~ literalString ~ briefly ~ description
    ).map { tpl => Constant.apply.tupled(tpl) }
  }

}
