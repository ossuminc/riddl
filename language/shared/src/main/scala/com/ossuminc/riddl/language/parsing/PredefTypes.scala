/*
 * Copyright 2019 Ossum, Inc.
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
  
  def zonedDateTypes[u:P]: P[String] = keywords(
    StringIn(PredefType.ZonedDate, PredefType.ZonedDate).!
  )

  def otherTypes[u: P]: P[String] = keywords(
    StringIn(
      // order matters in this list, because of common prefixes
      PredefType.Abstract,
      PredefType.Length,
      PredefType.Location,
      PredefType.Nothing,
      PredefType.Number,
      PredefType.UUID,
      PredefType.UserId
    ).!
  )

  def Abstract[u: P]: P[Unit] = keyword("Abstract")
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
  def Range[u: P]: P[Unit] = keyword("Range")
  def Real[u: P]: P[Unit] = keyword("Real")
  def String_[u: P]: P[Unit] = keyword("String")
  def Temperature[u: P]: P[Unit] = keyword("Temperature") // in Kelvin
  def Time[u: P]: P[Unit] = keyword("Time")
  def TimeStamp[u: P]: P[Unit] = keyword("TimeStamp")
  def Unknown[u: P]: P[Unit] = keyword("Unknown")
  def URL[u: P]: P[Unit] = keyword("URL")
  def UserId[u: P]: P[Unit] = keyword("UserId")
  def UUID[u: P]: P[Unit] = keyword("UUID")
  def Whole[u: P]: P[Unit] = keyword("Whole")

  def anyPredefType[u:P]: P[Unit] =
    P(realTypes | integerTypes | timeTypes | otherTypes | Abstract | Boolean | Current | Currency | Date | DateTime |
      Decimal | Duration | Id | Integer | Location | Length | Luminosity | Mass | Mole | Nothing | Natural | Number |
      Pattern | Range | Real | String_ | Temperature | Time | TimeStamp | Unknown | URL | UserId | UUID | Whole)
}

object PredefType {
  final val Abstract = "Abstract"
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
  final val Range = "Range"
  final val Real = "Real"
  final val String = "String"
  final val Temperature = "Temperature" // in Kelvin
  final val Time = "Time"
  final val TimeStamp = "TimeStamp"
  final val Unknown = "Unknown"
  final val URI = "URI"
  final val UserId = "UserId"
  final val UUID = "UUID"
  final val Whole = "Whole"
  final val ZonedDate = "ZonedDate"
  final val ZonedDateTime = "ZonedDateTime"

  // NOTE: Keep this list in synch with the one in TokenParser
  final val allPredefTypes: Seq[String] = Seq(
    Abstract,
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
    Range,
    Real,
    String,
    Temperature,
    Time,
    TimeStamp,
    Unknown,
    URI,
    UserId,
    UUID,
    Whole,
    ZonedDate,
    ZonedDateTime
  )
}
