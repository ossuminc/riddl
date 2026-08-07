/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import fastparse.*
import MultiLineWhitespace.*
import com.ossuminc.riddl.language.parsing.Keywords.{keyword, keywords}

object PredefTypes {

  def realTypes[u: P]: P[String] = keywords(
    StringIn(
      PredefType.Current,
      PredefType.Length,
      PredefType.Luminosity,
      PredefType.Mass,
      PredefType.Mole,
      PredefType.Number,
      PredefType.Real,
      PredefType.Temperature
    ).!
  )

  def integerTypes[u: P]: P[String] = keywords(
    StringIn(PredefType.Boolean, PredefType.Integer, PredefType.Whole, PredefType.Natural).!
  )

  def timeTypes[u: P]: P[String] = keywords(
    StringIn(
      PredefType.Duration,
      PredefType.DateTime,
      PredefType.Date,
      PredefType.TimeStamp,
      PredefType.Time
    ).!
  )

  def zonedDateTypes[u: P]: P[String] = keywords(
    StringIn(PredefType.ZonedDateTime, PredefType.ZonedDate).!
  )

  def otherTypes[u: P]: P[String] = keywords(
    StringIn(
      // order matters in this list, because of common prefixes
      PredefType.Abstract, // deprecated spelling of Anything
      PredefType.Anything,
      PredefType.Length,
      PredefType.Location,
      PredefType.Nothing,
      PredefType.Number,
      PredefType.UUID,
      PredefType.UserId
    ).!
  )

  def Abstract[u: P]: P[Unit] = keyword("Abstract") // deprecated spelling of Anything
  def Anything[u: P]: P[Unit] = keyword("Anything")
  def Blob[u: P]: P[Unit] = keyword("Blob")
  def Boolean[u: P]: P[Unit] = keyword("Boolean")
  def Current[u: P]: P[Unit] = keyword("Current") // in amperes
  def Currency[u: P]: P[Unit] = keyword("Currency") // for some nation
  def Date[u: P]: P[Unit] = keyword("Date")
  def DateTime[u: P]: P[Unit] = keyword("DateTime")
  def Decimal[u: P]: P[Unit] = keyword("Decimal")
  def Duration[u: P]: P[Unit] = keyword("Duration")
  def Id[u: P]: P[Unit] = keyword("Id")
  def Integer[u: P]: P[Unit] = keyword("Integer")
  def Location[u: P]: P[Unit] = keyword("Location")
  def Length[u: P]: P[Unit] = keyword("Length") // in meters
  def Luminosity[u: P]: P[Unit] = keyword("Luminosity") // in candelas
  def Mass[u: P]: P[Unit] = keyword("Mass") // in kilograms
  def Mole[u: P]: P[Unit] = keyword("Mole") // in mol (amount of substance)
  def Nothing[u: P]: P[Unit] = keyword("Nothing")
  def Natural[u: P]: P[Unit] = keyword("Natural")
  def Number[u: P]: P[Unit] = keyword("Number")
  def Pattern[u: P]: P[Unit] = keyword("Pattern")
  def Real[u: P]: P[Unit] = keyword("Real")
  def String_[u: P]: P[Unit] = keyword("String")
  def Temperature[u: P]: P[Unit] = keyword("Temperature") // in Kelvin
  def Time[u: P]: P[Unit] = keyword("Time")
  def TimeStamp[u: P]: P[Unit] = keyword("TimeStamp")
  def URL[u: P]: P[Unit] = keyword("URL")
  def UserId[u: P]: P[Unit] = keyword("UserId")
  def UUID[u: P]: P[Unit] = keyword("UUID")
  def Whole[u: P]: P[Unit] = keyword("Whole")

  /** The TOKENIZER's view of "a predefined type name" -- `TokenParser` uses it to classify a word
    * as [[AST.Token.Predefined]] for syntax highlighting. It is NOT the type parser; that is
    * `TypeParser.predefinedTypes`. Keeping a name here that the type parser cannot build makes an
    * editor highlight something a model cannot actually write, which is how `Range` and `Unknown`
    * came to be highlighted-but-unusable. Add a name here only alongside a real parser rule.
    */
  def anyPredefType[u: P]: P[Unit] =
    P(
      realTypes | integerTypes | timeTypes | otherTypes | Abstract | Anything | Blob | Boolean | Current | Currency |
        Date | DateTime | Decimal | Duration | Id | Integer | Location | Length | Luminosity | Mass | Mole | Nothing |
        Natural | Number | Pattern | Real | String_ | Temperature | Time | TimeStamp | URL |
        UserId | UUID | Whole
    )
}

object PredefType {

  /** Deprecated spelling of [[Anything]]; still accepted by the parser but emits a deprecation. */
  final val Abstract = "Abstract"
  final val Anything = "Anything"
  final val Blob = "Blob"
  final val Boolean = "Boolean"
  final val Current = "Current" // in amperes
  final val Currency = "Currency" // for some nation
  final val Date = "Date"
  final val DateTime = "DateTime"
  final val Decimal = "Decimal"
  final val Duration = "Duration"
  final val Id = "Id"
  final val Integer = "Integer"
  final val Location = "Location"
  final val Length = "Length" // in meters
  final val Luminosity = "Luminosity" // in candelas
  final val Mass = "Mass" // in kilograms
  final val Mole = "Mole" // in mol (amount of substance)
  final val Nothing = "Nothing"
  final val Natural = "Natural"
  final val Number = "Number"
  final val Pattern = "Pattern"
  // NO capitalized `Range`. The range type is spelled LOWERCASE -- `range(1,10)`, parsed by
  // `TypeParser.rangeType` off `Keyword.range`. A capitalized `Range` lived here until 2.0 and was
  // a phantom: it reserved the name against user definitions and highlighted it as a built-in,
  // while `type X is Range` failed to resolve. `Range` is now an ordinary name a model may use.
  final val Real = "Real"
  final val String = "String"
  final val Temperature = "Temperature" // in Kelvin
  final val Time = "Time"
  final val TimeStamp = "TimeStamp"
  // NO `Unknown`. It had no AST node and no parser rule -- only a name reservation and a tokenizer
  // entry -- so it was purely vestigial. Removed in 2.0; `Unknown` is now an ordinary name.
  final val URL = "URL"
  final val UserId = "UserId"
  final val UUID = "UUID"
  final val Whole = "Whole"
  final val ZonedDate = "ZonedDate"
  final val ZonedDateTime = "ZonedDateTime"

  // NOTE: Keep this list in synch with the one in TokenParser
  final val allPredefTypes: Seq[String] = Seq(
    Abstract,
    Anything,
    Blob,
    Boolean,
    Current,
    Currency,
    Date,
    DateTime,
    Decimal,
    Duration,
    Id,
    Integer,
    Location,
    Length,
    Luminosity,
    Mass,
    Mole,
    Nothing,
    Natural,
    Number,
    Pattern,
    Real,
    String,
    Temperature,
    Time,
    TimeStamp,
    URL,
    UserId,
    UUID,
    Whole,
    ZonedDate,
    ZonedDateTime
  )
}
