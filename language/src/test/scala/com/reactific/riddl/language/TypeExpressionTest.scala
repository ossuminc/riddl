/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At

/** Unit Tests For TypeExpressions */
class TypeExpressionTest extends AnyWordSpec with Matchers {

  val abstract_ = Abstract(At.empty)
  val bool = Bool(At.empty)
  val current = Current(At.empty)
  val currency = Currency(At.empty, "CA")
  val date = Date(At.empty)
  val dateTime = DateTime(At.empty)
  val decimal = Decimal(At.empty)
  val duration = Duration(At.empty)
  val integer = Integer(At.empty)
  val length_ = Length(At.empty)
  val location = Location(At.empty)
  val luminosity = Luminosity(At.empty)
  val mass = Mass(At.empty)
  val mole = Mole(At.empty)
  val nothing = Nothing(At.empty)
  val number = Number(At.empty)
  val real = Real(At.empty)
  val temperature = Temperature(At.empty)
  val time = Time(At.empty)
  val timestamp = TimeStamp(At.empty)
  val url = URL(At.empty)
  val uuid = UUID(At.empty)

  val pattern = Pattern(At.empty, Seq(LiteralString(At.empty, "^$")))
  val range = RangeType(At.empty, 2, 4)
  val string = Strng(At.empty, Some(42), None)
  val id = UniqueId(At.empty, PathIdentifier(At.empty, Seq("a", "b")))

  "Simple Predefined Types" must {
    "support Abstract" in {
      abstract_.kind mustBe Predefined.Abstract
      AST.kind(abstract_) mustBe Predefined.Abstract
    }
    "support Boolean" in {
      bool.kind mustBe Predefined.Boolean
      AST.kind(bool) mustBe Predefined.Boolean
      bool.isEmpty mustBe true
      bool.isContainer mustBe false
    }
    "support Current" in {
      current.kind mustBe Predefined.Current
      AST.kind(current) mustBe Predefined.Current
      current.isEmpty mustBe true
      current.isContainer mustBe false
    }
    "support Currency" in {
      currency.kind mustBe Predefined.Currency
      AST.kind(currency) mustBe Predefined.Currency
      currency.isEmpty mustBe true
      currency.isContainer mustBe false
    }
    "support Date" in {
      date.kind mustBe Predefined.Date
      AST.kind(date) mustBe Predefined.Date
      date.isEmpty mustBe true
      date.isContainer mustBe false
    }
    "support DateTime" in {
      dateTime.kind mustBe Predefined.DateTime
      AST.kind(dateTime) mustBe Predefined.DateTime
      dateTime.isEmpty mustBe true
      dateTime.isContainer mustBe false
    }
    "support Decimal" in {
      decimal.kind mustBe Predefined.Decimal
      AST.kind(decimal) mustBe Predefined.Decimal
      decimal.isEmpty mustBe true
      decimal.isContainer mustBe false
      decimal.format mustBe Predefined.Decimal
    }
    "support Duration" in {
      duration.kind mustBe Predefined.Duration
      AST.kind(duration) mustBe Predefined.Duration
      duration.isEmpty mustBe true
      duration.isContainer mustBe false
      duration.format mustBe Predefined.Duration
    }
    "support Integer" in {
      integer.kind mustBe Predefined.Integer
      AST.kind(integer) mustBe Predefined.Integer
      integer.isEmpty mustBe true
      integer.isContainer mustBe false
      integer.format mustBe Predefined.Integer
    }
    "support Length" in {
      length_.kind mustBe Predefined.Length
      AST.kind(length_) mustBe Predefined.Length
      length_.isEmpty mustBe true
      length_.isContainer mustBe false
      length_.format mustBe Predefined.Length
    }
    "support Location" in {
      location.kind mustBe Predefined.Location
      AST.kind(location) mustBe Predefined.Location
      location.isEmpty mustBe true
      location.isContainer mustBe false
      location.format mustBe Predefined.Location
    }
    "support Luminosity" in {
      luminosity.kind mustBe Predefined.Luminosity
      AST.kind(luminosity) mustBe Predefined.Luminosity
      luminosity.isEmpty mustBe true
      luminosity.isContainer mustBe false
      luminosity.format mustBe Predefined.Luminosity
    }
    "support Mass" in {
      mass.kind mustBe Predefined.Mass
      AST.kind(mass) mustBe Predefined.Mass
      mass.isEmpty mustBe true
      mass.isContainer mustBe false
      mass.format mustBe Predefined.Mass
    }
    "support Mole" in {
      mole.kind mustBe Predefined.Mole
      AST.kind(mole) mustBe Predefined.Mole
      mole.isEmpty mustBe true
      mole.isContainer mustBe false
      mole.format mustBe Predefined.Mole
    }
    "support Nothing" in {
      nothing.kind mustBe Predefined.Nothing
      AST.kind(nothing) mustBe Predefined.Nothing
      nothing.isEmpty mustBe true
      nothing.isContainer mustBe false
      nothing.format mustBe Predefined.Nothing
    }
    "support Number" in {
      number.kind mustBe Predefined.Number
      AST.kind(number) mustBe Predefined.Number
      number.isEmpty mustBe true
      number.isContainer mustBe false
      number.format mustBe Predefined.Number
    }
    "support Real" in {
      real.kind mustBe Predefined.Real
      AST.kind(real) mustBe Predefined.Real
      real.isEmpty mustBe true
      real.isContainer mustBe false
      real.format mustBe Predefined.Real
    }
    "support Temperature" in {
      temperature.kind mustBe Predefined.Temperature
      AST.kind(temperature) mustBe Predefined.Temperature
      temperature.isEmpty mustBe true
      temperature.isContainer mustBe false
      temperature.format mustBe Predefined.Temperature
    }
    "support Time" in {
      time.kind mustBe Predefined.Time
      AST.kind(time) mustBe Predefined.Time
      time.isEmpty mustBe true
      time.isContainer mustBe false
      time.format mustBe Predefined.Time
    }
    "support TimeStamp" in {
      timestamp.kind mustBe Predefined.TimeStamp
      AST.kind(timestamp) mustBe Predefined.TimeStamp
      timestamp.isEmpty mustBe true
      timestamp.isContainer mustBe false
      timestamp.format mustBe Predefined.TimeStamp
    }
    "support URL" in {
      url.kind mustBe Predefined.URL
      AST.kind(url) mustBe Predefined.URL
      url.isEmpty mustBe true
      url.isContainer mustBe false
      url.format mustBe Predefined.URL
    }
    "support UUID" in {
      uuid.kind mustBe Predefined.UUID
      AST.kind(uuid) mustBe Predefined.UUID
      uuid.isEmpty mustBe true
      uuid.isContainer mustBe false
      uuid.format mustBe Predefined.UUID
    }
  }

  "Constructed Predefined Types" must {
    "support Pattern" in {
      pattern.isEmpty mustBe true
      pattern.isContainer mustBe false
      pattern.kind mustBe Predefined.Pattern
      AST.kind(pattern) mustBe Predefined.Pattern
      pattern.format mustBe "Pattern(\"^$\")"
    }
    "support Range" in {
      range.isEmpty mustBe true
      range.isContainer mustBe false
      range.kind mustBe Predefined.Range
      AST.kind(range) mustBe Predefined.Range
      range.format mustBe "Range(2,4)"
    }
    "support String" in {
      string.kind mustBe Predefined.String
      AST.kind(string) mustBe Predefined.String
      string.format mustBe "String(42,)"
      string.isEmpty mustBe true
      string.isContainer mustBe false
    }

    "support UniqueId" in {
      id.format mustBe "Id(a.b)"
      id.kind mustBe Predefined.Id
      id.isEmpty mustBe true
      id.isContainer mustBe false
    }
  }

  "Assignment Compatibility" must {
    "support abstract equality" in {
      val abs = Abstract(At.empty)
      abs.isAssignmentCompatible(abstract_) mustBe true
      abs.isAssignmentCompatible(bool) mustBe true
      abs.isAssignmentCompatible(current) mustBe true
      abs.isAssignmentCompatible(currency) mustBe true
      abs.isAssignmentCompatible(date) mustBe true
      abs.isAssignmentCompatible(dateTime) mustBe true
      abs.isAssignmentCompatible(decimal) mustBe true
      abs.isAssignmentCompatible(duration) mustBe true
      abs.isAssignmentCompatible(id) mustBe true
      abs.isAssignmentCompatible(integer) mustBe true
      abs.isAssignmentCompatible(location) mustBe true
      abs.isAssignmentCompatible(length_) mustBe true
      abs.isAssignmentCompatible(luminosity) mustBe true
      abs.isAssignmentCompatible(mass) mustBe true
      abs.isAssignmentCompatible(mole) mustBe true
      abs.isAssignmentCompatible(nothing) mustBe true
      abs.isAssignmentCompatible(number) mustBe true
      abs.isAssignmentCompatible(pattern) mustBe true
      abs.isAssignmentCompatible(range) mustBe true
      abs.isAssignmentCompatible(real) mustBe true
      abs.isAssignmentCompatible(string) mustBe true
      abs.isAssignmentCompatible(temperature) mustBe true
      abs.isAssignmentCompatible(time) mustBe true
      abs.isAssignmentCompatible(timestamp) mustBe true
      abs.isAssignmentCompatible(url) mustBe true
      abs.isAssignmentCompatible(uuid) mustBe true
    }
    "support TimeStamp equality" in {
      val ts = TimeStamp(At.empty)
      ts.isAssignmentCompatible(DateTime(At.empty)) mustBe true
      ts.isAssignmentCompatible(Date(At.empty)) mustBe true
      ts.isAssignmentCompatible(Strng(At.empty)) mustBe true
      ts.isAssignmentCompatible(
        Pattern(At.empty, Seq(LiteralString(At.empty, "")))
      ) mustBe true
      ts.isAssignmentCompatible(TimeStamp(At.empty)) mustBe true
    }
  }
}
