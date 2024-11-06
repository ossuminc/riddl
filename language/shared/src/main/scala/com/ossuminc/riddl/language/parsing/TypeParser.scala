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

import scala.collection

/** Parsing rules for Type definitions */
private[parsing] trait TypeParser {
  this: CommonParser =>

  private def entityReferenceType[u: P]: P[EntityReferenceTypeExpression] = {
    P(
      Index ~ Keywords.reference ~ to.? ~/
        maybe(Keyword.entity) ~/ pathIdentifier ~/ Index
    ).map { case (start, pid, end) => EntityReferenceTypeExpression(at(start, end), pid) }
  }

  private def stringType[u: P]: P[String_] = {
    P(
      Index ~ PredefTypes.String_ ~/
        (Punctuation.roundOpen ~ integer.? ~ Punctuation.comma ~ integer.? ~
          Punctuation.roundClose).? ~ Index
    ).map {
      case (start, Some((min, max)), end) => String_(at(start, end), min, max)
      case (start, None, end)             => String_(at(start, end), None, None)
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
      Index ~ PredefTypes.Currency ~/
        (Punctuation.roundOpen ~ isoCountryCode ~ Punctuation.roundClose) ~ Index
    ).map { case (start, cc, end) => Currency(at(start, end), cc) }
  }

  private def urlType[u: P]: P[URI] = {
    P(
      Index ~ PredefTypes.URL ~/
        (Punctuation.roundOpen ~ literalString ~ Punctuation.roundClose).? ~ Index
    ).map {
      case (start, Some(str), end) => URI(at(start, end), Some(str))
      case (start, None, end)      => URI(at(start, end), None)
    }
  }

  private def integerPredefTypes[u: P]: P[IntegerTypeExpression] = {
    P(
      Index ~ PredefTypes.integerTypes ~ Index
    ).map { case (start, typ, end) =>
      val loc = at(start, end)
      typ match
        case PredefType.Boolean => AST.Bool(loc)
        case PredefType.Integer => AST.Integer(loc)
        case PredefType.Natural => AST.Natural(loc)
        case PredefType.Whole   => AST.Whole(loc)
      end match
    }
  }

  private def realPredefTypes[u: P]: P[RealTypeExpression] = {
    P(
      Index ~ PredefTypes.realTypes ~ Index
    ).map { case (start, typ, end) =>
      val loc: At = at(start, end)
      typ match {
        case PredefType.Current     => Current(loc)
        case PredefType.Length      => Length(loc)
        case PredefType.Luminosity  => Luminosity(loc)
        case PredefType.Mass        => Mass(loc)
        case PredefType.Mole        => Mole(loc)
        case PredefType.Number      => Number(loc)
        case PredefType.Real        => Real(loc)
        case PredefType.Temperature => Temperature(loc)

      }
    }
  }

  private def timePredefTypes[u: P]: P[TypeExpression] = {
    P(
      Index ~ PredefTypes.timeTypes ~ Index
    ).map { case (start, timeType, end) =>
      val loc = at(start, end)
      timeType match
        case PredefType.Duration  => Duration(loc)
        case PredefType.DateTime  => DateTime(loc)
        case PredefType.Date      => Date(loc)
        case PredefType.TimeStamp => TimeStamp(loc)
        case PredefType.Time      => Time(loc)
      end match
    }
  }

  private def otherPredefTypes[u: P]: P[TypeExpression] = {
    P(
      Index ~ PredefTypes.otherTypes ~ Index
    ).map { case (start, otherType, end) =>
      val loc = at(start, end)
      otherType match
        case PredefType.Abstract => AST.Abstract(loc)
        case PredefType.Location => AST.Location(loc)
        case PredefType.Nothing  => AST.Nothing(loc)
        case PredefType.Natural  => AST.Natural(loc)
        case PredefType.Number   => AST.Number(loc)
        case PredefType.UUID     => AST.UUID(loc)
        case PredefType.UserId   => AST.UserId(loc)
        case _ =>
          error(loc, "Unrecognized predefined type")
          AST.Abstract(loc)
      end match
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
      Index ~ PredefType.Decimal ~/ Punctuation.roundOpen ~
        integer ~ Punctuation.comma ~ integer ~
        Punctuation.roundClose ~/ Index
    ).map { case (start, whole, fractional, end) => Decimal(at(start, end), whole, fractional) }
  }

  private def patternType[u: P]: P[Pattern] = {
    P(
      Index ~ PredefType.Pattern ~/ Punctuation.roundOpen ~
        (literalStrings |
          Punctuation.undefinedMark.!.map(_ => Seq.empty[LiteralString])) ~
        Punctuation.roundClose ~/ Index
    ).map { case (start, pattern, end) => Pattern(at(start, end), pattern) }
  }

  private def uniqueIdType[u: P]: P[UniqueId] = {
    (Index ~ PredefType.Id ~ Punctuation.roundOpen ~/
      maybe(Keyword.entity) ~ pathIdentifier ~ Punctuation.roundClose ~/ Index) map { case (start, pid, end) =>
      UniqueId(at(start, end), pid)
    }
  }

  private def enumValue[u: P]: P[Option[Long]] = {
    P(Punctuation.roundOpen ~ integer ~ Punctuation.roundClose./).?
  }

  def enumerator[u: P]: P[Enumerator] = {
    P(Index ~ identifier ~ enumValue ~ Index).map { case (start, id, value, end) =>
      Enumerator(at(start, end), id, value)
    }
  }

  private def enumerators[u: P]: P[Seq[Enumerator]] = {
    enumerator.rep(1, maybe(Punctuation.comma)) | undefined[u, Seq[Enumerator]](Seq.empty[Enumerator])

  }

  def enumeration[u: P]: P[Enumeration] = {
    P(
      Index ~ Keywords.any ~ of.? ~/ open ~/ enumerators ~ close ~/ Index
    ).map { case (start, enums, end) => Enumeration(at(start, end), enums.toContents) }
  }

  private def alternation[u: P]: P[Alternation] = {
    P(
      Index ~ Keywords.one ~ of.? ~/ open ~
        (Punctuation.undefinedMark.!.map(_ => Seq.empty[AliasedTypeExpression]) |
          aliasedTypeExpression.rep(0, P("or" | "|" | ","))) ~ close ~/ Index
    ).map { case (start, contents, end) =>
      Alternation(at(start, end), contents.toContents)
    }
  }

  private def aliasedTypeExpression[u: P]: P[AliasedTypeExpression] = {
    P(
      Index ~ Keywords.typeKeywords.? ~ pathIdentifier ~ Index
    ).map {
      case (start, Some(key), pid, end) =>
        AliasedTypeExpression(at(start, end), key, pid)
      case (start, None, pid, end) =>
        AliasedTypeExpression(at(start, end), "type", pid)
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
      Index ~ identifier ~ is ~ fieldTypeExpression ~ withMetaData ~ Index
    ).map { case (start, id, typeEx, descriptives, end) =>
      Field(at(start, end), id, typeEx, descriptives.toContents)
    }
  }

  def arguments[u: P]: P[Seq[MethodArgument]] = {
    P(
      (
        Index ~ identifier.map(_.value) ~ Punctuation.colon ~ fieldTypeExpression ~ Index
      ).map { case (start, id, typeEx, end) => MethodArgument(at(start, end), id, typeEx) }
    ).rep(min = 0, Punctuation.comma)
  }

  def method[u: P]: P[Method] = {
    P(
      Index ~ identifier ~ Punctuation.roundOpen ~ arguments ~ Punctuation.roundClose ~
        is ~ fieldTypeExpression ~ withMetaData ~ Index
    ).map { case (start, id, args, typeExp, descriptives, end) =>
      Method.apply(at(start, end), id, typeExp, args, descriptives.toContents)
    }
  }

  private def aggregateContent[u: P]: P[AggregateContents] = {
    P(field | method)./.asInstanceOf[P[AggregateContents]]
  }

  private def aggregateDefinitions[u: P]: P[Seq[AggregateContents]] = {
    P(
      undefined(Seq.empty[AggregateContents]) | aggregateContent.rep(min = 1, Punctuation.comma.?)
    )
  }

  def aggregation[u: P]: P[Aggregation] = {
    P(Index ~ open ~ aggregateDefinitions ~ close ~/ Index).map { case (start, contents, end) =>
      Aggregation(at(start, end), contents.toContents)
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
    P(Index ~ aggregateUseCase ~ aggregation ~ Index).map { case (start, mk, agg, end) =>
      makeAggregateUseCaseType(at(start, end), mk, agg)
    }
  }

  /** Parses mappings, i.e.
    * {{{
    *   mapping from Integer to String
    * }}}
    */
  private def mappingFromTo[u: P]: P[Mapping] = {
    P(
      Index ~ Keywords.mapping ~ from ~/ typeExpression ~ to ~ typeExpression ~/ Index
    ).map { case (start, from, to, end) => Mapping(at(start, end), from, to) }
  }

  /** Parses sets, i.e.
    * {{{
    *   set of String
    * }}}
    */
  private def aSetType[u: P]: P[Set] = {
    P(
      Index ~ Keywords.set ~ of ~ typeExpression ~/ Index
    ).map { (start, typeEx, end) => Set(at(start, end), typeEx) }
  }

  /** Parses sequences, i.e.
    * {{{
    *     sequence of String
    * }}}
    */
  private def sequenceType[u: P]: P[Sequence] = {
    P(
      Index ~ Keywords.sequence ~ of ~ typeExpression ~ Index
    )./.map { case (start, typeEx, end) => Sequence(at(start, end), typeEx) }
  }

  /** Parses graphs whose nodes can be any type */
  private def graphType[u: P]: P[Graph] = {
    P(Index ~ Keywords.graph ~ of ~ typeExpression ~/ Index).map { case (start, typeEx, end) =>
      Graph(at(start, end), typeEx)
    }
  }

  /** Parses tables of at least one dimension of cells of an arbitrary type */
  private def tableType[u: P]: P[Table] = {
    P(
      Index ~ Keywords.table ~ of ~ typeExpression ~ of ~ Punctuation.squareOpen ~
        integer.rep(1, ",") ~ Punctuation.squareClose ~/ Index
    ).map { case (start, typeEx, dimensions, end) => Table(at(start, end), typeEx, dimensions) }
  }

  private def replicaType[x: P]: P[Replica] = {
    P(
      Index ~ Keywords.replica ~ of ~ replicaTypeExpression ~ Index
    ).map { case (start, typeEx, end) => Replica(at(start, end), typeEx) }
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
      Index ~ Keywords.range ~ Punctuation.roundOpen ~/
        integer.?.map(_.getOrElse(0L)) ~ Punctuation.comma ~
        integer.?.map(_.getOrElse(Long.MaxValue)) ~ Punctuation.roundClose ~/ Index
    ).map { case (start, min, max, end) => RangeType(at(start, end), min, max) }
  }

  private def cardinality[u: P](p: => P[TypeExpression]): P[TypeExpression] = {
    P(
      Index ~
        Keywords.many.!.? ~ Keywords.optional.!.? ~ p ~ StringIn(
          Punctuation.question,
          Punctuation.asterisk,
          Punctuation.plus
        ).!.? ~/ Index
    ).map {
      case (start, None, None, typ, Some("?"), end)                     => Optional(at(start, end), typ)
      case (start, None, None, typ, Some("+"), end)                     => OneOrMore(at(start, end), typ)
      case (start, None, None, typ, Some("*"), end)                     => ZeroOrMore(at(start, end), typ)
      case (start, Some("many"), None, typ, None, end)                  => OneOrMore(at(start, end), typ)
      case (start, None, Some("optional"), typ, None, end)              => Optional(at(start, end), typ)
      case (start, Some("many"), Some("optional"), typ, None, end)      => ZeroOrMore(at(start, end), typ)
      case (start, None, Some("optional"), typ, Some("?"), end)         => Optional(at(start, end), typ)
      case (start, Some("many"), None, typ, Some("+"), end)             => OneOrMore(at(start, end), typ)
      case (start, Some("many"), Some("optional"), typ, Some("*"), end) => ZeroOrMore(at(start, end), typ)
      case (_, None, None, typ, None, _)                                => typ
      case (start, _, _, typ, _, end) =>
        error(at(start, end), s"Cannot determine cardinality for $typ")
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

  private def scalaAggregateDefinition[u: P]: P[Aggregation] = {
    P(
      Index ~ Punctuation.roundOpen ~ field.rep(0, ",") ~ Punctuation.roundClose ~/ Index
    ).map { case (start, fields, end) =>
      Aggregation(at(start, end), fields.toContents)
    }
  }

  private def defOfTypeKindType[u: P]: P[Type] = {
    P(
      Index ~ aggregateUseCase ~/ identifier ~
        (scalaAggregateDefinition | (is ~ (aliasedTypeExpression | aggregation))) ~ withMetaData ~ Index
    )./.map { case (start, useCase, id, ateOrAgg, descriptives, end) =>
      val loc = at(start, end)
      ateOrAgg match {
        case agg: Aggregation =>
          val mt = AggregateUseCaseTypeExpression(agg.loc, useCase, agg.contents)
          Type(loc, id, mt, descriptives.toContents)
        case ate: AliasedTypeExpression =>
          Type(loc, id, ate, descriptives.toContents)
        case _ =>
          require(false, "Oops! Impossible case")
          // Type just to satisfy compiler because it doesn't know require(false...) will throw
          Type(loc, id, Nothing(loc), descriptives.toContents)
      }
    }
  }

  private def defOfType[u: P]: P[Type] = {
    P(
      Index ~ Keywords.`type` ~/ identifier ~ is ~ typeExpression ~ withMetaData ~/ Index
    )./.map { case (start, id, typ, descriptives, end) =>
      Type(at(start, end), id, typ, descriptives.toContents)
    }
  }

  def typeDef[u: P]: P[Type] = { defOfType | defOfTypeKindType }

  def constant[u: P]: P[Constant] = {
    P(
      Index ~ Keywords.constant ~ identifier ~ is ~ typeExpression ~
        Punctuation.equalsSign ~ literalString ~ withMetaData ~ Index
    ).map { case (start, id, typeEx, litStr, descriptives, end) =>
      Constant(at(start, end), id, typeEx, litStr, descriptives.toContents)
    }
  }
}
