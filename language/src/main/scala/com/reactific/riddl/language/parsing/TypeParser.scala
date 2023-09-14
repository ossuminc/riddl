/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.AST
import com.reactific.riddl.language.ast.At
import fastparse.*
import fastparse.ScalaWhitespace.*
import Terminals.*

/** Parsing rules for Type definitions */
private[parsing] trait TypeParser extends CommonParser {

  private def entityReferenceType[u: P]: P[EntityReferenceTypeExpression] = {
    P(
      location ~ Keywords.reference ~ Readability.to.? ~/
        maybe(Keywords.entity) ~ pathIdentifier
    ).map { tpl => (EntityReferenceTypeExpression.apply _).tupled(tpl) }
  }

  private def stringType[u: P]: P[Strng] = {
    P(
      location ~ Predefined.String ~/
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
      location ~ Predefined.Currency ~/
        (Punctuation.roundOpen ~ isoCountryCode ~ Punctuation.roundClose)
    ).map { tpl => (Currency.apply _).tupled(tpl) }
  }

  private def urlType[u: P]: P[URL] = {
    P(
      location ~ Predefined.URL ~/
        (Punctuation.roundOpen ~ literalString ~ Punctuation.roundClose).?
    ).map { tpl => (URL.apply _).tupled(tpl) }
  }

  private def integerPredefTypes[u: P]: P[IntegerTypeExpression] = {
    P(
      location ~ (StringIn(Predefined.Boolean, Predefined.Integer, Predefined.Whole, Predefined.Natural).! ~~ !CharPred(
        _.isLetterOrDigit
      )
        | rangeType)
    ).map {
      case (at, Predefined.Boolean)  => AST.Bool(at)
      case (at, Predefined.Integer)  => AST.Integer(at)
      case (at, Predefined.Natural)  => AST.Natural(at)
      case (at, Predefined.Whole)    => AST.Whole(at)
      case (_: At, range: RangeType) => range
      case (_, _)                    => ??? // FIXME: correct this
    }
  }

  private def realPredefTypes[u: P]: P[RealTypeExpression] = {
    P(
      location ~ (StringIn(
        Predefined.Current,
        Predefined.Length,
        Predefined.Luminosity,
        Predefined.Mass,
        Predefined.Mole,
        Predefined.Number,
        Predefined.Real,
        Predefined.Temperature
      ).! ~~ !CharPred(_.isLetterOrDigit))
    ).map {
      case (at: At, Predefined.Current)     => Current(at)
      case (at: At, Predefined.Length)      => Length(at)
      case (at: At, Predefined.Luminosity)  => Luminosity(at)
      case (at: At, Predefined.Mass)        => Mass(at)
      case (at: At, Predefined.Mole)        => Mole(at)
      case (at: At, Predefined.Number)      => Number(at)
      case (at: At, Predefined.Real)        => Real(at)
      case (at: At, Predefined.Temperature) => Temperature(at)
    }
  }

  private def timePredefTypes[u: P]: P[TypeExpression] = {
    P(
      location ~ StringIn(
        Predefined.Duration,
        Predefined.DateTime,
        Predefined.Date,
        Predefined.TimeStamp,
        Predefined.Time
      ).! ~~ !CharPred(_.isLetterOrDigit)
    ).map {
      case (at: At, Predefined.Duration) => Duration(at)
      case (at, Predefined.DateTime)     => DateTime(at)
      case (at, Predefined.Date)         => Date(at)
      case (at, Predefined.TimeStamp)    => TimeStamp(at)
      case (at, Predefined.Time)         => Time(at)
    }
  }

  private def otherTypes[u: P]: P[TypeExpression] = {
    P(
      location ~ StringIn(
        // order matters in this list, because of common prefixes
        Predefined.Abstract,
        Predefined.Length,
        Predefined.Location,
        Predefined.Nothing,
        Predefined.Number,
        Predefined.UUID
      ).! ~~ !CharPred(_.isLetterOrDigit)
    ).map {
      case (at, Predefined.Abstract) => AST.Abstract(at)
      case (at, Predefined.Location) => AST.Location(at)
      case (at, Predefined.Nothing)  => AST.Nothing(at)
      case (at, Predefined.Natural)  => AST.Natural(at)
      case (at, Predefined.Number)   => AST.Number(at)
      case (at, Predefined.UUID)     => AST.UUID(at)
      case (at, _) =>
        error("Unrecognized predefined type")
        AST.Abstract(at)
    }
  }

  private def simplePredefinedTypes[u: P]: P[TypeExpression] = {
    P(
      stringType | currencyType | urlType | integerPredefTypes | realPredefTypes | timePredefTypes |
        decimalType | otherTypes
    )./
  }

  private def decimalType[u: P]: P[Decimal] = {
    P(
      location ~ Predefined.Decimal ~/ Punctuation.roundOpen ~
        integer ~ Punctuation.comma ~ integer ~
        Punctuation.roundClose
    )./.map(tpl => (Decimal.apply _).tupled(tpl))
  }

  private def patternType[u: P]: P[Pattern] = {
    P(
      location ~ Predefined.Pattern ~/ Punctuation.roundOpen ~
        (literalStrings |
          Punctuation.undefinedMark.!.map(_ => Seq.empty[LiteralString])) ~
        Punctuation.roundClose./
    ).map(tpl => (Pattern.apply _).tupled(tpl))
  }

  private def uniqueIdType[u: P]: P[UniqueId] = {
    (location ~ Predefined.Id ~ Punctuation.roundOpen ~/
      maybe(Keywords.entity) ~ pathIdentifier ~ Punctuation.roundClose./) map { case (loc, pid) =>
      UniqueId(loc, pid)
    }
  }

  private def enumValue[u: P]: P[Option[Long]] = {
    P(Punctuation.roundOpen ~ integer ~ Punctuation.roundClose./).?
  }

  private def enumerator[u: P]: P[Enumerator] = {
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

  private def alternation[u: P]: P[Alternation] = {
    P(
      location ~ Keywords.one ~ Readability.of.? ~/ open ~
        (Punctuation.undefinedMark.!.map(_ => Seq.empty[AliasedTypeExpression]) |
          aliasedTypeExpression.rep(0, P("or" | "|" | ","))) ~ close./
    ).map { x => (Alternation.apply _).tupled(x) }
  }

  private def aliasedTypeExpression[u: P]: P[AliasedTypeExpression] = {
    P(location ~ maybe(Keywords.`type`) ~ pathIdentifier)./.map(tpl => (AliasedTypeExpression.apply _).tupled(tpl))
  }

  private def fieldTypeExpression[u: P]: P[TypeExpression] = {
    P(
      cardinality(
        simplePredefinedTypes./ | patternType | uniqueIdType |
          enumeration | setType | mappingType | sequenceType | rangeType |
          alternation | entityReferenceType | aliasedTypeExpression
      )
    )
  }

  def field[u: P]: P[Field] = {
    P(
      location ~ identifier ~ is ~ fieldTypeExpression ~ briefly ~ description
    )
      .map(tpl => (Field.apply _).tupled(tpl))
  }

  def fields[u: P]: P[Seq[Field]] = {
    P(
      Punctuation.undefinedMark.!.map(_ => Seq.empty[Field]) |
        field.rep(min = 0, Punctuation.comma)
    )
  }

  def aggregation[u: P]: P[Aggregation] = {
    P(location ~ Keywords.fields.? ~ open ~ fields ~ close).map { case (loc, fields) =>
      Aggregation(loc, fields)
    }
  }

  private def aggregateUseCase[u: P]: P[AggregateUseCase] = {
    P(
      StringIn(
        Keywords.command,
        Keywords.event,
        Keywords.query,
        Keywords.result,
        Keywords.record
      ).!
    ).map { mk =>
      mk.toLowerCase() match {
        case kind if kind == Keywords.command => CommandCase
        case kind if kind == Keywords.event   => EventCase
        case kind if kind == Keywords.query   => QueryCase
        case kind if kind == Keywords.result  => ResultCase
        case kind if kind == Keywords.record  => RecordCase
      }
    }
  }

  private def makeAggregateUseCaseType(
    loc: At,
    mk: AggregateUseCase,
    agg: Aggregation
  ): AggregateUseCaseTypeExpression = {
    AggregateUseCaseTypeExpression(loc, mk, agg.fields)
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
  private def mappingType[u: P]: P[Mapping] = {
    P(
      location ~ Keywords.mapping ~ Readability.from ~/ typeExpression ~
        Readability.to ~ typeExpression
    ).map { tpl => (Mapping.apply _).tupled(tpl) }
  }

  /** Parses sets, i.e.
    * {{{
    *   set of String
    * }}}
    *
    * @tparam u
    * @return
    */
  private def setType[u: P]: P[Set] = {
    P(
      location ~ Keywords.set ~ Readability.of ~ typeExpression
    )./.map { tpl => (Set.apply _).tupled(tpl) }
  }

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
    P(
      cardinality(
        simplePredefinedTypes | patternType | uniqueIdType | enumeration |
          sequenceType | setType | mappingType | rangeType |
          decimalType | alternation | entityReferenceType |
          aggregation | aggregateUseCaseTypeExpression | aliasedTypeExpression
      )
    )
  }

  def replicaTypeExpression[u: P]: P[TypeExpression] = {
    P(integerPredefTypes | mappingType | setType)
  }

  private def defOfTypeKindType[u: P]: P[Type] = {
    P(
      location ~ aggregateUseCase ~/ identifier ~ is ~ aggregation ~ briefly ~
        description
    ).map { case (loc, mk, id, agg, b, d) =>
      val mt = AggregateUseCaseTypeExpression(agg.loc, mk, agg.fields)
      Type(loc, id, mt, b, d)
    }
  }

  private def defOfType[u: P]: P[Type] = {
    P(
      location ~ Keywords.`type` ~/ identifier ~ is ~ typeExpression ~ briefly ~
        description
    ).map { case (loc, id, typEx, b, d) => Type(loc, id, typEx, b, d) }
  }

  def typeDef[u: P]: P[Type] = { defOfType | defOfTypeKindType }

  def types[u: P]: P[Seq[Type]] = { typeDef.rep(0) }

  def constant[u: P]: P[Constant] = {
    P(
      location ~ Keywords.const ~ identifier ~ is ~ typeExpression ~
        Punctuation.equalsSign ~ literalString ~ briefly ~ description
    ).map { tpl => (Constant.apply _).tupled(tpl) }
  }

}
