/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.reactific.riddl.language.parsing.Terminals.*
import com.reactific.riddl.language.AST.*
/** Unit Tests For TypeExpressions */
class TypeExpressionTest extends AnyWordSpec with Matchers {

  val abstract_ = Abstract(At.empty)
  val bool = Bool(At.empty)
  val current = Current(At.empty)
  val currency = Currency(At.empty, "CA")
  val date = Date(At.empty)
  val dateTime = DateTime(At.empty)
  val decimal = Decimal(At.empty, 8, 3)
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
      AST.errorDescription(abstract_) mustBe Predefined.Abstract
    }
    "support Boolean" in {
      bool.kind mustBe Predefined.Boolean
      AST.errorDescription(bool) mustBe Predefined.Boolean
      bool.isEmpty mustBe true
      bool.isContainer mustBe false
    }
    "support Current" in {
      current.kind mustBe Predefined.Current
      AST.errorDescription(current) mustBe Predefined.Current
      current.isEmpty mustBe true
      current.isContainer mustBe false
    }
    "support Currency" in {
      currency.kind mustBe Predefined.Currency
      AST.errorDescription(currency) mustBe Predefined.Currency
      currency.isEmpty mustBe true
      currency.isContainer mustBe false
    }
    "support Date" in {
      date.kind mustBe Predefined.Date
      AST.errorDescription(date) mustBe Predefined.Date
      date.isEmpty mustBe true
      date.isContainer mustBe false
    }
    "support DateTime" in {
      dateTime.kind mustBe Predefined.DateTime
      AST.errorDescription(dateTime) mustBe Predefined.DateTime
      dateTime.isEmpty mustBe true
      dateTime.isContainer mustBe false
    }
    "support Decimal" in {
      decimal.kind mustBe Predefined.Decimal
      AST.errorDescription(decimal) mustBe s"Decimal(8,3)"
      decimal.isEmpty mustBe true
      decimal.isContainer mustBe false
      decimal.format mustBe s"Decimal(8,3)"
    }
    "support Duration" in {
      duration.kind mustBe Predefined.Duration
      AST.errorDescription(duration) mustBe Predefined.Duration
      duration.isEmpty mustBe true
      duration.isContainer mustBe false
      duration.format mustBe Predefined.Duration
    }
    "support Integer" in {
      integer.kind mustBe Predefined.Integer
      AST.errorDescription(integer) mustBe Predefined.Integer
      integer.isEmpty mustBe true
      integer.isContainer mustBe false
      integer.format mustBe Predefined.Integer
    }
    "support Length" in {
      length_.kind mustBe Predefined.Length
      AST.errorDescription(length_) mustBe Predefined.Length
      length_.isEmpty mustBe true
      length_.isContainer mustBe false
      length_.format mustBe Predefined.Length
    }
    "support Location" in {
      location.kind mustBe Predefined.Location
      AST.errorDescription(location) mustBe Predefined.Location
      location.isEmpty mustBe true
      location.isContainer mustBe false
      location.format mustBe Predefined.Location
    }
    "support Luminosity" in {
      luminosity.kind mustBe Predefined.Luminosity
      AST.errorDescription(luminosity) mustBe Predefined.Luminosity
      luminosity.isEmpty mustBe true
      luminosity.isContainer mustBe false
      luminosity.format mustBe Predefined.Luminosity
    }
    "support Mass" in {
      mass.kind mustBe Predefined.Mass
      AST.errorDescription(mass) mustBe Predefined.Mass
      mass.isEmpty mustBe true
      mass.isContainer mustBe false
      mass.format mustBe Predefined.Mass
    }
    "support Mole" in {
      mole.kind mustBe Predefined.Mole
      AST.errorDescription(mole) mustBe Predefined.Mole
      mole.isEmpty mustBe true
      mole.isContainer mustBe false
      mole.format mustBe Predefined.Mole
    }
    "support Nothing" in {
      nothing.kind mustBe Predefined.Nothing
      AST.errorDescription(nothing) mustBe Predefined.Nothing
      nothing.isEmpty mustBe true
      nothing.isContainer mustBe false
      nothing.format mustBe Predefined.Nothing
    }
    "support Number" in {
      number.kind mustBe Predefined.Number
      AST.errorDescription(number) mustBe Predefined.Number
      number.isEmpty mustBe true
      number.isContainer mustBe false
      number.format mustBe Predefined.Number
    }
    "support Real" in {
      real.kind mustBe Predefined.Real
      AST.errorDescription(real) mustBe Predefined.Real
      real.isEmpty mustBe true
      real.isContainer mustBe false
      real.format mustBe Predefined.Real
    }
    "support Temperature" in {
      temperature.kind mustBe Predefined.Temperature
      AST.errorDescription(temperature) mustBe Predefined.Temperature
      temperature.isEmpty mustBe true
      temperature.isContainer mustBe false
      temperature.format mustBe Predefined.Temperature
    }
    "support Time" in {
      time.kind mustBe Predefined.Time
      AST.errorDescription(time) mustBe Predefined.Time
      time.isEmpty mustBe true
      time.isContainer mustBe false
      time.format mustBe Predefined.Time
    }
    "support TimeStamp" in {
      timestamp.kind mustBe Predefined.TimeStamp
      AST.errorDescription(timestamp) mustBe Predefined.TimeStamp
      timestamp.isEmpty mustBe true
      timestamp.isContainer mustBe false
      timestamp.format mustBe Predefined.TimeStamp
    }
    "support URL" in {
      url.kind mustBe Predefined.URL
      AST.errorDescription(url) mustBe Predefined.URL
      url.isEmpty mustBe true
      url.isContainer mustBe false
      url.format mustBe Predefined.URL
    }
    "support UUID" in {
      uuid.kind mustBe Predefined.UUID
      AST.errorDescription(uuid) mustBe Predefined.UUID
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
      AST.errorDescription(pattern) mustBe Predefined.Pattern
      pattern.format mustBe "Pattern(\"^$\")"
    }
    "support Range" in {
      range.isEmpty mustBe true
      range.isContainer mustBe false
      range.kind mustBe Predefined.Range
      AST.errorDescription(range) mustBe "Range(2,4)"
      range.format mustBe "Range(2,4)"
    }
    "support String" in {
      string.kind mustBe Predefined.String
      AST.errorDescription(string) mustBe Predefined.String
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

  val enumeration = Enumeration(
    At.empty,
    Seq(
      Enumerator(At.empty, Identifier(At.empty, "one")),
      Enumerator(At.empty, Identifier(At.empty, "two")),
      Enumerator(At.empty, Identifier(At.empty, "three"))
    )
  )

  val aggregation = Aggregation(
    At.empty,
    Seq(
      Field(At.empty, Identifier(At.empty, "integer"), integer),
      Field(At.empty, Identifier(At.empty, "abstract"), abstract_),
      Field(At.empty, Identifier(At.empty, "bool"), bool),
      Field(At.empty, Identifier(At.empty, "current"), current),
      Field(At.empty, Identifier(At.empty, "currency"), currency),
      Field(At.empty, Identifier(At.empty, "date"), date),
      Field(At.empty, Identifier(At.empty, "dateTime"), dateTime),
      Field(At.empty, Identifier(At.empty, "decimal"), decimal),
      Field(At.empty, Identifier(At.empty, "duration"), duration),
      Field(At.empty, Identifier(At.empty, "integer"), integer),
      Field(At.empty, Identifier(At.empty, "length"), length_),
      Field(At.empty, Identifier(At.empty, "location"), location),
      Field(At.empty, Identifier(At.empty, "luminosity"), luminosity),
      Field(At.empty, Identifier(At.empty, "mass"), mass),
      Field(At.empty, Identifier(At.empty, "mole"), mole),
      Field(At.empty, Identifier(At.empty, "nothing"), nothing),
      Field(At.empty, Identifier(At.empty, "number"), number),
      Field(At.empty, Identifier(At.empty, "real"), real),
      Field(At.empty, Identifier(At.empty, "temperature"), temperature),
      Field(At.empty, Identifier(At.empty, "time"), time),
      Field(At.empty, Identifier(At.empty, "timestamp"), timestamp),
      Field(At.empty, Identifier(At.empty, "url"), url),
      Field(At.empty, Identifier(At.empty, "uuid"), uuid),
      Field(At.empty, Identifier(At.empty, "pattern"), pattern),
      Field(At.empty, Identifier(At.empty, "range"), range),
      Field(At.empty, Identifier(At.empty, "string"), string),
      Field(At.empty, Identifier(At.empty, "id"), id)
    )
  )

  val alternation = Alternation(
    At.empty,
    Seq(
      AliasedTypeExpression(At.empty, PathIdentifier(At.empty, Seq("a", "b"))),
      AliasedTypeExpression(At.empty, PathIdentifier(At.empty, Seq("z", "y")))
    )
  )

  val mapping = Mapping(At.empty, string, integer)

  val reference = EntityReferenceTypeExpression(
    At.empty,
    PathIdentifier(At.empty, Seq("a", "b", "c", "d", "entity"))
  )

  val message =
    AggregateUseCaseTypeExpression(At.empty, RecordCase, aggregation.fields)

  val alias = AliasedTypeExpression(
    At.empty,
    PathIdentifier(At.empty, Seq("a", "b", "foo"))
  )

  "Complex Expression Types" must {
    "Support Aggregation" in {
      AST.errorDescription(aggregation) mustBe "Aggregation of 27 fields"
      aggregation.format mustBe
        "{ integer: Integer, abstract: Abstract, " +
        "bool: Boolean, current: Current, currency: Currency, date: Date, " +
        "dateTime: DateTime, decimal: Decimal(8,3), duration: Duration, " +
        "integer: Integer, length: Length, location: Location, " +
        "luminosity: Luminosity, mass: Mass, mole: Mole, nothing: Nothing, " +
        "number: Number, real: Real, temperature: Temperature, time: Time, " +
        "timestamp: TimeStamp, url: URL, uuid: UUID, " +
        "pattern: Pattern(\"^$\"), range: Range(2,4), string: String(42,), " +
        "id: Id(a.b) }"
      aggregation.isEmpty mustBe false
      aggregation.isContainer mustBe true
    }
    "Support Enumeration" in {
      AST.errorDescription(enumeration) mustBe "Enumeration of 3 values"
      enumeration.format mustBe "{ one,two,three }"
      enumeration.isEmpty mustBe true
      enumeration.isContainer mustBe false
    }
    "Support Alternation" in {
      AST.errorDescription(alternation) mustBe "Alternation of 2 types"
      alternation.format mustBe "one of { a.b, z.y }"
      alternation.isEmpty mustBe true
      alternation.isContainer mustBe false
    }
    "Support Mapping" in {
      AST.errorDescription(mapping) mustBe "Map from String to Integer"
      mapping.format mustBe "mapping from String(42,) to Integer"
      mapping.isEmpty mustBe true
      mapping.isContainer mustBe false
    }
    "Support EntityReference" in {
      AST.errorDescription(reference) mustBe
        "Reference to entity a.b.c.d.entity"
      reference.format mustBe "entity a.b.c.d.entity"
      reference.isEmpty mustBe true
      reference.isContainer mustBe false
    }
    "Support Messages" in {
      AST.errorDescription(message) mustBe "Record of 27 fields and 0 methods"
      message.format mustBe
        "record { integer: Integer, abstract: Abstract, " +
        "bool: Boolean, current: Current, currency: Currency, date: Date, " +
        "dateTime: DateTime, decimal: Decimal(8,3), duration: Duration, " +
        "integer: Integer, length: Length, location: Location, " +
        "luminosity: Luminosity, mass: Mass, mole: Mole, nothing: Nothing, " +
        "number: Number, real: Real, temperature: Temperature, time: Time, " +
        "timestamp: TimeStamp, url: URL, uuid: UUID, " +
        "pattern: Pattern(\"^$\"), range: Range(2,4), string: String(42,), " +
        "id: Id(a.b) }"
      message.isEmpty mustBe false
      message.isContainer mustBe true
    }
    "Support type aliases" in {
      AST.errorDescription(alias) mustBe "a.b.foo"
      alias.format mustBe "a.b.foo"
      alias.isEmpty mustBe true
      alias.isContainer mustBe false
    }
  }

  val optional: AST.Optional = Optional(At.empty, integer)
  val oneOrMore: AST.OneOrMore = OneOrMore(At.empty, integer)
  val zeroOrMore: AST.ZeroOrMore = ZeroOrMore(At.empty, integer)

  "Cardinality" must {
    "support optional" in {
      AST.errorDescription(optional) mustBe "Integer?"
      optional.format mustBe "Integer?"
      optional.isEmpty mustBe true
      optional.isContainer mustBe false
    }
    "support oneOrMore" in {
      AST.errorDescription(oneOrMore) mustBe "Integer+"
      oneOrMore.format mustBe "Integer+"
      oneOrMore.isEmpty mustBe true
      oneOrMore.isContainer mustBe false
    }
    "support zeroOrMore" in {
      AST.errorDescription(zeroOrMore) mustBe "Integer*"
      zeroOrMore.format mustBe "Integer*"
      zeroOrMore.isEmpty mustBe true
      zeroOrMore.isContainer mustBe false
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
      ts.isAssignmentCompatible(ts) mustBe true
      ts.isAssignmentCompatible(timestamp) mustBe true
      ts.isAssignmentCompatible(dateTime) mustBe true
      ts.isAssignmentCompatible(date) mustBe true
      ts.isAssignmentCompatible(string) mustBe true
      ts.isAssignmentCompatible(pattern) mustBe true
    }
    "support UniqueId equality" in {
      id.isAssignmentCompatible(id) mustBe true
      id.isAssignmentCompatible(string) mustBe true
      id.isAssignmentCompatible(pattern) mustBe true
    }
    "support String" in {
      string.isAssignmentCompatible(string) mustBe true
      string.isAssignmentCompatible(pattern) mustBe true
    }
    "support Range" in {
      range.isAssignmentCompatible(integer) mustBe true
      range.isAssignmentCompatible(real) mustBe true
      range.isAssignmentCompatible(decimal) mustBe true
      range.isAssignmentCompatible(number) mustBe true
      range.isAssignmentCompatible(current) mustBe true
      range.isAssignmentCompatible(length_) mustBe true
      range.isAssignmentCompatible(luminosity) mustBe true
      range.isAssignmentCompatible(mass) mustBe true
      range.isAssignmentCompatible(mole) mustBe true
      range.isAssignmentCompatible(temperature) mustBe true
      range.isAssignmentCompatible(range) mustBe true
    }
    "support Nothing" in {
      nothing.isAssignmentCompatible(abstract_) mustBe false
      nothing.isAssignmentCompatible(bool) mustBe false
      nothing.isAssignmentCompatible(current) mustBe false
      nothing.isAssignmentCompatible(currency) mustBe false
      nothing.isAssignmentCompatible(date) mustBe false
      nothing.isAssignmentCompatible(dateTime) mustBe false
      nothing.isAssignmentCompatible(decimal) mustBe false
      nothing.isAssignmentCompatible(duration) mustBe false
      nothing.isAssignmentCompatible(id) mustBe false
      nothing.isAssignmentCompatible(integer) mustBe false
      nothing.isAssignmentCompatible(location) mustBe false
      nothing.isAssignmentCompatible(length_) mustBe false
      nothing.isAssignmentCompatible(luminosity) mustBe false
      nothing.isAssignmentCompatible(mass) mustBe false
      nothing.isAssignmentCompatible(mole) mustBe false
      nothing.isAssignmentCompatible(nothing) mustBe false
      nothing.isAssignmentCompatible(number) mustBe false
      nothing.isAssignmentCompatible(pattern) mustBe false
      nothing.isAssignmentCompatible(range) mustBe false
      nothing.isAssignmentCompatible(real) mustBe false
      nothing.isAssignmentCompatible(string) mustBe false
      nothing.isAssignmentCompatible(temperature) mustBe false
      nothing.isAssignmentCompatible(time) mustBe false
      nothing.isAssignmentCompatible(timestamp) mustBe false
      nothing.isAssignmentCompatible(url) mustBe false
      nothing.isAssignmentCompatible(uuid) mustBe false
    }
  }
}
