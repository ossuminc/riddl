/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.{AST, At}

import fastparse.*
import fastparse.MultiLineWhitespace.*

import scala.collection

/** Parsing rules for Type definitions */
private[parsing] trait TypeParser {
  // `& StatementParser`: `constant` reuses `numericLiteral`/`booleanLiteral`/`promptValue` from
  // StatementParser rather than duplicating them (see the `private[parsing]` notes on those rules).
  this: CommonParser & StatementParser =>

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

  private def zone[u: P]: P[Option[LiteralString]] = {
    P(Index ~ CharsWhileIn("A-Z0-9:.+\\-", 2).!.? ~ Index).map { case (start, str, end) =>
      str.map(s => LiteralString(at(start, end), s))
    }
  }

  private def zonedPredefTypes[u: P]: P[TypeExpression] = {
    P(
      Index ~ PredefTypes.zonedDateTypes ~
        Punctuation.roundOpen ~ zone ~ Punctuation.roundClose ~
        Index
    ).map { case (start, dateType, zone, end) =>
      val loc = at(start, end)
      dateType match
        case PredefType.ZonedDate     => ZonedDate(loc, zone)
        case PredefType.ZonedDateTime => ZonedDateTime(loc, zone)
      end match
    }
  }

  private def otherPredefTypes[u: P]: P[TypeExpression] = {
    P(
      Index ~ PredefTypes.otherTypes ~ Index
    ).map { case (start, otherType, end) =>
      val loc = at(start, end)
      otherType match
        case PredefType.Anything => AST.Anything(loc)
        // `Abstract` is the DEPRECATED spelling of `Anything`; it yields the SAME node and emits
        // one deprecation at the keyword (pattern mirrors `reply` -> `yield` and `prompt` -> `do`).
        case PredefType.Abstract =>
          deprecation(
            loc,
            "The `Abstract` type is deprecated; use `Anything` instead",
            code = Option(Messages.DeprecationCode.AbstractType),
            autoFixable = true
          )
          AST.Anything(loc)
        case PredefType.Location => AST.Location(loc)
        case PredefType.Nothing  => AST.Nothing(loc)
        case PredefType.Natural  => AST.Natural(loc)
        case PredefType.Number   => AST.Number(loc)
        case PredefType.UUID     => AST.UUID(loc)
        case PredefType.UserId   => AST.UserId(loc)
        case _ =>
          error(loc, "Unrecognized predefined type")
          AST.Anything(loc)
      end match
    }
  }

  private def predefinedTypes[u: P]: P[TypeExpression] = {
    P(
      // GROUP 1: Most common primitive types (70-80%)
      stringType | integerPredefTypes | realPredefTypes |
        // GROUP 2: Common temporal types (10-15%)
        timePredefTypes |
        // GROUP 3: Less common types (5-10%)
        otherPredefTypes | decimalType | blobType |
        // GROUP 4: Rare specialized types (1-5%)
        currencyType | urlType | zonedPredefTypes
    )./
  }

  private def decimalType[u: P]: P[Decimal] = {
    P(
      Index ~ PredefType.Decimal ~/ Punctuation.roundOpen ~
        integer ~ Punctuation.comma ~ integer ~
        Punctuation.roundClose ~/ Index
    ).map { case (start, whole, fractional, end) => Decimal(at(start, end), whole, fractional) }
  }

  /** `Blob(<kind>)` -- a predefined type for opaque bulk content of a declared kind.
    *
    * The spelling matches `AST.Blob.format` (`s"$kind($blobKind)"`), which is what PrettifyPass
    * emits for every [[PredefinedType]], so parse and emit agree without a prettify case. Until
    * 2.0, `AST.Blob`, its `BlobKind` enum, BAST read/write and the JSON `BlobDto` all existed with
    * NO way to write one in RIDDL source: `Blob` sat in the reserved-name list, so `type B is Blob`
    * failed to resolve AND `type Blob is ...` was rejected as redefining a built-in.
    */
  private def blobType[u: P]: P[Blob] = {
    P(
      Index ~ PredefType.Blob ~/ Punctuation.roundOpen ~
        blobKind ~ Punctuation.roundClose ~/ Index
    ).map { case (start, bk, end) => Blob(at(start, end), bk) }
  }

  private def blobKind[u: P]: P[BlobKind] = {
    // StringIn is a trie, so shared prefixes are handled regardless of order.
    P(
      StringIn("Text", "XML", "JSON", "Image", "Audio", "Video", "CSV", "FileSystem").!
    ).map(BlobKind.valueOf)
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
    // The keyword generalizes from `entity` to every processor kind. Longest-first is not
    // needed here (no keyword is a prefix of another) but the alternation order still
    // mirrors ReferenceParser.processorRef for readability.
    // Keywords.keywords enforces the keyword/identifier boundary (a non-identifier character,
    // or end of input, must follow) -- without it, `Id(contextRegistry)` would parse as
    // `Id(context Registry)`, same hazard as `event` inside `event-sourced` (Keywords.scala:19).
    def kindKw[u: P]: P[String] = Keywords.keywords(
      StringIn(
        Keyword.adaptor,
        Keyword.context,
        Keyword.entity,
        Keyword.projector,
        Keyword.repository,
        Keyword.streamlet
      ).!
    )
    (Index ~ PredefType.Id ~ Punctuation.roundOpen ~/
      kindKw.? ~ pathIdentifier ~ Punctuation.roundClose ~/ Index) map {
      case (start, kw, pid, end) =>
        UniqueId(at(start, end), pid, kw)
    }
  }

  private def enumValue[u: P]: P[Option[Long]] = {
    P(Punctuation.roundOpen ~ integer ~ Punctuation.roundClose./).?
  }

  def enumerator[u: P]: P[Enumerator] = {
    P(Index ~~ identifier ~ enumValue ~ withMetaData ~~ Index).map {
      case (start, id, value, metaData, end) =>
        Enumerator(at(start, end), id, value, metaData.toContents)
    }
  }

  private def enumerators[u: P]: P[Seq[Enumerator]] = {
    enumerator.rep(1, maybe(Punctuation.comma)) | undefined[u, Seq[Enumerator]](
      Seq.empty[Enumerator]
    )

  }

  def enumeration[u: P]: P[Enumeration] = {
    P(
      Index ~ Keywords.any ~ of.? ~/ open ~/ enumerators ~ close ~/ Index
    ).map { case (start, enums, end) => Enumeration(at(start, end), enums.toContents) }
  }

  private def alternation[u: P]: P[Alternation] = {
    P(
      Index ~ Keywords.one ~ of.? ~/ open ~
        // `???` and an empty body both yield no alternatives but mean different things, so they
        // are kept apart here: None is the explicit "not decided yet" placeholder, Some(Nil) is a
        // body that genuinely lists nothing.
        (Punctuation.undefinedMark.!.map(_ => Option.empty[Seq[AliasedTypeExpression]]) |
          aliasedTypeExpression
            .rep(0, P(Keywords.or | Punctuation.verticalBar | Punctuation.comma))
            .map(Some(_))) ~ close
        ~/ Index
    ).map { case (start, alternatives, end) =>
      val loc = at(start, end)
      val contents = alternatives.getOrElse(Seq.empty[AliasedTypeExpression])
      alternatives match
        case Some(alts) if alts.isEmpty =>
          // Nothing to choose between. `???` is how you say "not decided yet".
          error(loc, "An alternation must have at least one alternative, or `???`")
        case Some(alts) if alts.sizeIs == 1 =>
          // Legal for now, but a choice of one is just that type wearing a wrapper.
          deprecation(
            loc,
            "An alternation of a single alternative is deprecated; give it a second alternative " +
              "or use the type directly",
            code = Option(Messages.DeprecationCode.SingleAlternation),
            autoFixable = false
          )
        case _ => ()
      end match
      Alternation(loc, contents.toContents)
    }
  }

  /** `A | B` -- the same alternation as `one of { A or B }`, in the infix notation most computer
    * scientists already read.
    *
    * Deliberately NOT the canonical form: PrettifyPass emits `one of { ... }`, because RIDDL is
    * meant to stay readable by people who are not computer scientists. Both spellings parse to the
    * IDENTICAL `Alternation`, so a round trip through prettify normalises to the words and loses
    * nothing.
    *
    * Operands are `aliasedTypeExpression`, exactly as inside the braces -- a predefined type is not
    * a valid alternative in either spelling, so `String | Integer` reports the same unresolved
    * paths that `one of { String or Integer }` does rather than diverging.
    *
    * At least one `|` is REQUIRED, so a lone type expression fails here and falls through to the
    * ordinary ordering. That is what makes it safe to try first, which in turn is what keeps the
    * two spellings behaving the same way.
    */
  private def infixAlternation[u: P]: P[Alternation] = {
    P(
      Index ~ aliasedTypeExpression ~
        (Punctuation.verticalBar ~ aliasedTypeExpression).rep(1) ~ Index
    ).map { case (start, first, rest, end) =>
      Alternation(at(start, end), (first +: rest).toContents)
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
        // GROUP 0: `A | B` -- must precede predefinedTypes so the infix spelling behaves exactly
        // as `one of { ... }`; it requires a `|` so nothing else is captured by trying it first.
        infixAlternation |
          // GROUP 1: Most common in field definitions (60-70%)
          predefinedTypes |
          // GROUP 2: Keyword-based constructs MUST come before aliasedTypeExpression
          // (otherwise keywords like "any", "one", "mapping", "set", "sequence", "graph", "table", "range", "replica"
          // get matched as type names)
          enumeration | alternation | sequenceType | aSetType | mappingFromTo | graphType | tableType | rangeType | replicaType |
          // GROUP 3: Specific patterns must come before general aliasedTypeExpression
          uniqueIdType | entityReferenceType | patternType |
          // GROUP 4: Very common - general type references (30-40%)
          aliasedTypeExpression |
          // GROUP 5: Common structured types (20-25%)
          aggregation |
          // GROUP 6: Less common - decimal type (3-5%)
          decimalType
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
        Index ~ identifier.map(_.value) ~ Punctuation.colon ~ fieldTypeExpression ~~ Index
      ).map { case (start, id, typeEx, end) => MethodArgument(at(start, end), id, typeEx) }
    ).rep(min = 0, Punctuation.comma)
  }

  def method[u: P]: P[Method] = {
    P(
      Index ~ identifier ~ Punctuation.roundOpen ~ arguments ~ Punctuation.roundClose ~
        is ~ fieldTypeExpression ~ withMetaData ~~ Index
    ).map { case (start, id, args, typeExp, descriptives, end) =>
      Method.apply(at(start, end), id, typeExp, args, descriptives.toContents)
    }
  }

  /** A field whose name is a DEFINITION keyword, caught only so the diagnostic can say so.
    *
    * `simpleIdentifier` rejects the keywords that INTRODUCE a definition (`entity`, `context`,
    * `domain`, …) — that is what stops `domain domain is { … }` parsing. The consequence for a
    * field is a message pointing at the wrong place: `command Store is { entity: Order }` failed
    * with *"Expected one of ("(" | "replies" | "yields")"* reported at the `{`, several tokens
    * BEFORE the offending word, because the whole aggregation alternative had to fail before the
    * enclosing alternation could report. Nothing in that message mentions `entity`, and nothing
    * suggests the escape.
    *
    * Tried AFTER `field`, so a legal field is never affected, and gated on the keyword being
    * followed by a colon so this cannot swallow any other construct. The escape it names is real
    * and verified: `'entity': Order` parses, because `quotedIdentifier` accepts single quotes and
    * bypasses the keyword filter. Note only DEFINITION keywords are affected — `version: String`
    * and `copyright: String` are perfectly legal field names and stay that way.
    */
  private def keywordNamedField[u: P]: P[Nothing] = {
    // Built from the SAME set `simpleIdentifier` filters against, so the two cannot disagree
    // about which words are definition keywords.
    P(
      (CharIn("a-zA-Z") ~~ CharsWhileIn("a-zA-Z0-9_\\-").?).!.filter(
        Keyword.definitionKeywords.contains
      ) ~~ Punctuation.colon
    )./.flatMap { kw =>
      Fail.opaque(
        s"a field name, but '$kw' introduces a definition and cannot be one unqualified; " +
          s"write it quoted as '$kw' to use it as a field name"
      )
    }
  }

  private def aggregateContent[u: P]: P[AggregateContents] = {
    P(field | method | comment | keywordNamedField)./.asInstanceOf[P[AggregateContents]]
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
        case kind if kind == Keyword.type_   => AggregateUseCase.TypeCase
        case kind if kind == Keyword.command => AggregateUseCase.CommandCase
        case kind if kind == Keyword.event   => AggregateUseCase.EventCase
        case kind if kind == Keyword.query   => AggregateUseCase.QueryCase
        case kind if kind == Keyword.result  => AggregateUseCase.ResultCase
        case kind if kind == Keyword.record  => AggregateUseCase.RecordCase
        case kind if kind == Keyword.graph   => AggregateUseCase.GraphCase
        case kind if kind == Keyword.table   => AggregateUseCase.TableCase
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
    P(Index ~ aggregateUseCase ~ aggregation ~~ Index).map { case (start, mk, agg, end) =>
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
    // Cardinality can be specified with:
    // - Prefix: "many" (=+), "optional" (=?), or "many optional" (=*)
    // - Suffix: ? (optional), + (one-or-more), * (zero-or-more)
    // - But NOT both prefix and suffix together (ambiguous)
    P(
      Index ~
        Keywords.many.!.? ~ Keywords.optional.!.? ~ p ~ StringIn(
          Punctuation.question,
          Punctuation.asterisk,
          Punctuation.plus
        ).!.? ~/ Index
    ).map {
      // Suffix only (no prefix)
      case (start, None, None, typ, Some("?"), end) => Optional(at(start, end), typ)
      case (start, None, None, typ, Some("+"), end) => OneOrMore(at(start, end), typ)
      case (start, None, None, typ, Some("*"), end) => ZeroOrMore(at(start, end), typ)
      // Prefix only (no suffix)
      case (start, Some("many"), None, typ, None, end)     => OneOrMore(at(start, end), typ)
      case (start, None, Some("optional"), typ, None, end) => Optional(at(start, end), typ)
      case (start, Some("many"), Some("optional"), typ, None, end) =>
        ZeroOrMore(at(start, end), typ)
      // No cardinality modifier
      case (_, None, None, typ, None, _) => typ
      // Invalid: prefix and suffix together
      case (start, prefix, optPrefix, typ, Some(suffix), end) =>
        val prefixStr = (prefix, optPrefix) match
          case (Some("many"), Some("optional")) => "many optional"
          case (Some("many"), None)             => "many"
          case (None, Some("optional"))         => "optional"
          case _                                => ""
        error(
          at(start, end),
          s"Cannot combine cardinality prefix '$prefixStr' with suffix '$suffix' for $typ; use one or the other"
        )
        typ
    }
  }

  // `private[parsing]`, not `private`: A20's `promptValue` (StatementParser) reuses this directly
  // for the `as <type>` ascription, rather than duplicating the type-expression grammar.
  private[parsing] def typeExpression[u: P]: P[TypeExpression] = {
    P(
      cardinality(
        // GROUP 0: `A | B` -- see infixAlternation; tried first, requires a `|`.
        infixAlternation |
          // GROUP 1: Most common - cheap predefined types (40-50% of cases)
          predefinedTypes |
          // GROUP 2: Keyword-based constructs MUST come before aliasedTypeExpression
          // (otherwise keywords like "any", "one", "mapping", "set", "sequence", "graph", "table", "range", "replica"
          // get matched as type names)
          enumeration | alternation | sequenceType | aSetType | mappingFromTo | graphType | tableType | rangeType | replicaType |
          // GROUP 3: Other specific patterns before general type references
          uniqueIdType | entityReferenceType | patternType |
          // GROUP 4: Very common - general type references and aggregations (30-40%)
          aliasedTypeExpression | aggregation | aggregateUseCaseTypeExpression |
          // GROUP 5: Less common - decimal type (5-10%)
          decimalType
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

  /** `command X yields event E` / `query X replies result R`.
    *
    * TWO keywords, one AST field. A command can only yield and a query can only reply, so which
    * keyword is legal follows from `useCase` -- a second `Option` field would always be None. What
    * the keyword buys is READABILITY: until 2.0 both pairings were spelled `yields`, so a
    * declaration did not say which half of the language it belonged to.
    *
    * The pairing is checked HERE rather than in ValidationPass because both facts are known at
    * parse time and neither needs resolution, and because `error(...)` in this rule is non-fatal
    * and accumulating -- the sibling type-alias check just below has emitted errors this way all
    * along. A parse FAILURE would be the wrong tool: it could only point at the keyword, where this
    * can name the keyword and the use case together.
    */
  private def defOfTypeKindType[u: P]: P[Type] = {
    P(
      Index ~ aggregateUseCase ~/ identifier ~
        ((Keywords.yields.map(_ => Keyword.yields) | Keywords.replies.map(_ => Keyword.replies)) ~
          messageRef).? ~
        (scalaAggregateDefinition | (is ~ (aliasedTypeExpression | aggregation))) ~ withMetaData ~/ Index
    )./.map { case (start, useCase, id, declared, ateOrAgg, descriptives, end) =>
      val loc = at(start, end)
      val yields = declared.map(_._2)
      declared.foreach { case (keyword, _) =>
        val wanted = useCase match
          case AggregateUseCase.CommandCase => Some(Keyword.yields)
          case AggregateUseCase.QueryCase   => Some(Keyword.replies)
          case _                            => None // events and results declare no response
        wanted match
          case Some(expected) if keyword != expected =>
            error(
              loc,
              s"a ${useCase.useCase} declares its response with `$expected`, not `$keyword`. " +
                s"`${Keyword.yields}` pairs a command with an event; " +
                s"`${Keyword.replies}` pairs a query with a result"
            )
          // NOT checked here: a response clause on a kind that declares none (`record R yields
          // ...`). ValidationPass already reports that, with a better message, and a parse-time
          // `error` would PREEMPT it -- parse errors stop the pass chain, so whatever the parser
          // says is the ONLY thing the author sees. The parser therefore keeps exactly the check
          // validation cannot make: `usecase` is in the AST, but which KEYWORD was written is not.
          case None => ()
          case _    => () // correct pairing
        end match
      }
      ateOrAgg match {
        case agg: Aggregation =>
          val mt = AggregateUseCaseTypeExpression(agg.loc, useCase, agg.contents, yields)
          Type(loc, id, mt, descriptives.toContents)
        case ate: AliasedTypeExpression =>
          if yields.nonEmpty then
            error(
              loc,
              "`yields`/`replies` requires an aggregate command/query body, not a type alias"
            )
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
      val loc = at(start, end)
      // The TYPE-FIRST spelling of an aggregate use case -- `type Pay is command { … }` -- is
      // deprecated in 2.0, removed in 3.0. It yields the SAME AST as the kind-first
      // `command Pay is { … }` (see defOfTypeKindType), and PrettifyPass already emits kind-first
      // for both, so a type-first model never round-trips back to its own spelling. It is also
      // strictly less expressive: `yields` exists ONLY on the kind-first rule
      // (ebnf-grammar.ebnf:112), which is what blocked the dokn migration.
      //
      // Emitting HERE, rather than in `aggregateUseCaseTypeExpression`, is what scopes this to the
      // spelling being retired. A nested `f: command { … }` inside an aggregation reaches that
      // expression through `field`'s own `typeExpression` and is deliberately untouched; only an
      // aggregate use case standing as the DIRECT type expression of a `type` definition is the
      // type-first form.
      typ match
        case ate: AggregateUseCaseTypeExpression =>
          val kw = ate.usecase.useCase
          deprecation(
            loc,
            s"Declaring `${id.value}` as `type ${id.value} is $kw { … }` is deprecated; " +
              s"write `$kw ${id.value} is { … }` instead",
            code = Option(Messages.DeprecationCode.TypeFirstAggregate),
            autoFixable = true
          )
        case _ => ()
      end match
      Type(loc, id, typ, descriptives.toContents)
    }
  }

  def typeDef[u: P]: P[Type] = { defOfType | defOfTypeKindType }

  // The four arms `Constant` may hold. Keyword-led (`promptValue`, `booleanLiteral`) and
  // punctuation-led (`numericLiteral`) forms are tried before `literalString`, which is the
  // permissive bare-quote fallback and must go last.
  private def constantValue[u: P]: P[ConstantValue] = {
    P(
      promptValue.map(pv => pv: ConstantValue) |
        booleanLiteral.map(bl => bl: ConstantValue) |
        numericLiteral.map(nl => nl: ConstantValue) |
        literalString.map(ls => ls: ConstantValue)
    )
  }

  // True when `text` is what a NON-string arm of `constantValue` would have matched for `typeEx` --
  // i.e. the author quoted a value that need not have been quoted. `Bool` is itself a NumericType
  // (`AST.scala:2497`), so it is handled as its own case rather than falling into the digit-pattern
  // check below. Scoped precisely: a String-typed constant is never reported, and an alias/named
  // type (a TypeRef, not a literal predefined type) is left alone -- resolving it needs the symbol
  // table, which does not exist at parse time.
  private def isNumericLike(typeEx: TypeExpression, text: String): Boolean = {
    typeEx match
      case _: Bool        => text == "true" || text == "false"
      case _: NumericType => text.matches("""[+-]?\d+(\.\d+)?([eE][+-]?\d+)?""")
      case _              => false
    end match
  }

  // Names the literal kind `typeEx` actually calls for, so the deprecation's advice matches the
  // type. `Bool` is itself a NumericType (see `isNumericLike` above), so without this a Boolean
  // constant would be told to hold "a numeric literal" -- true of every OTHER arm of the match,
  // but not the fix for a Boolean one, which needs `true`/`false`.
  private def literalKindFor(typeEx: TypeExpression): String =
    typeEx match
      case _: Bool => "a boolean literal (true or false)"
      case _       => "a numeric literal"

  def constant[u: P]: P[Constant] = {
    P(
      Index ~ Keywords.constant ~ identifier ~ is ~ typeExpression ~
        Punctuation.equalsSign ~ constantValue ~ withMetaData ~/ Index
    ).map { case (start, id, typeEx, value, descriptives, end) =>
      // CONSUME the deprecated quoted spelling into the node the value actually denotes, the same
      // bargain as `ConnectorOptionToIntention`/`EntityOptionToIntention`: the fix happens here, at
      // parse time, so there is no old-shaped node left for prettify to decide about -- it just
      // emits the bare literal, and the round trip converges. Without this, `autoFixable = true`
      // was a lie: the AST kept holding a `LiteralString`, so `emitConstant` re-emitted the quotes
      // unchanged on every prettify.
      val fixedValue: ConstantValue = value match
        case ls: LiteralString if isNumericLike(typeEx, ls.s) =>
          deprecation(
            ls.loc,
            s"A ${typeEx.format} constant should hold ${literalKindFor(typeEx)}, not a string",
            code = Option(Messages.DeprecationCode.QuotedConstantLiteral),
            autoFixable = true
          )
          typeEx match
            case _: Bool => BooleanLiteral(ls.loc, ls.s == "true")
            case _       => NumericLiteral(ls.loc, ls.s)
        case _ => value
      Constant(at(start, end), id, typeEx, fixedValue, descriptives.toContents)
    }
  }
}
