/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.PredefType

/** Unit Tests For TypeExpressions */
class TypeExpressionTest extends AnyWordSpec with Matchers {

  val abstract_ = Abstract(At.empty)
  val bool: Bool = Bool(At.empty)
  val current: Current = Current(At.empty)
  val currency: Currency = Currency(At.empty, "CA")
  val date: Date = Date(At.empty)
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
      abstract_.kind mustBe PredefType.Abstract
      AST.errorDescription(abstract_) mustBe PredefType.Abstract
    }
    "support Boolean" in {
      bool.kind mustBe PredefType.Boolean
      AST.errorDescription(bool) mustBe PredefType.Boolean
      bool.isEmpty mustBe true
      bool.isContainer mustBe false
    }
    "support Current" in {
      current.kind mustBe PredefType.Current
      AST.errorDescription(current) mustBe PredefType.Current
      current.isEmpty mustBe true
      current.isContainer mustBe false
    }
    "support Currency" in {
      currency.kind mustBe PredefType.Currency
      AST.errorDescription(currency) mustBe PredefType.Currency
      currency.isEmpty mustBe true
      currency.isContainer mustBe false
    }
    "support Date" in {
      date.kind mustBe PredefType.Date
      AST.errorDescription(date) mustBe PredefType.Date
      date.isEmpty mustBe true
      date.isContainer mustBe false
    }
    "support DateTime" in {
      dateTime.kind mustBe PredefType.DateTime
      AST.errorDescription(dateTime) mustBe PredefType.DateTime
      dateTime.isEmpty mustBe true
      dateTime.isContainer mustBe false
    }
    "support Decimal" in {
      decimal.kind mustBe PredefType.Decimal
      AST.errorDescription(decimal) mustBe s"Decimal(8,3)"
      decimal.isEmpty mustBe true
      decimal.isContainer mustBe false
      decimal.format mustBe s"Decimal(8,3)"
    }
    "support Duration" in {
      duration.kind mustBe PredefType.Duration
      AST.errorDescription(duration) mustBe PredefType.Duration
      duration.isEmpty mustBe true
      duration.isContainer mustBe false
      duration.format mustBe PredefType.Duration
    }
    "support Integer" in {
      integer.kind mustBe PredefType.Integer
      AST.errorDescription(integer) mustBe PredefType.Integer
      integer.isEmpty mustBe true
      integer.isContainer mustBe false
      integer.format mustBe PredefType.Integer
    }
    "support Length" in {
      length_.kind mustBe PredefType.Length
      AST.errorDescription(length_) mustBe PredefType.Length
      length_.isEmpty mustBe true
      length_.isContainer mustBe false
      length_.format mustBe PredefType.Length
    }
    "support Location" in {
      location.kind mustBe PredefType.Location
      AST.errorDescription(location) mustBe PredefType.Location
      location.isEmpty mustBe true
      location.isContainer mustBe false
      location.format mustBe PredefType.Location
    }
    "support Luminosity" in {
      luminosity.kind mustBe PredefType.Luminosity
      AST.errorDescription(luminosity) mustBe PredefType.Luminosity
      luminosity.isEmpty mustBe true
      luminosity.isContainer mustBe false
      luminosity.format mustBe PredefType.Luminosity
    }
    "support Mass" in {
      mass.kind mustBe PredefType.Mass
      AST.errorDescription(mass) mustBe PredefType.Mass
      mass.isEmpty mustBe true
      mass.isContainer mustBe false
      mass.format mustBe PredefType.Mass
    }
    "support Mole" in {
      mole.kind mustBe PredefType.Mole
      AST.errorDescription(mole) mustBe PredefType.Mole
      mole.isEmpty mustBe true
      mole.isContainer mustBe false
      mole.format mustBe PredefType.Mole
    }
    "support Nothing" in {
      nothing.kind mustBe PredefType.Nothing
      AST.errorDescription(nothing) mustBe PredefType.Nothing
      nothing.isEmpty mustBe true
      nothing.isContainer mustBe false
      nothing.format mustBe PredefType.Nothing
    }
    "support Number" in {
      number.kind mustBe PredefType.Number
      AST.errorDescription(number) mustBe PredefType.Number
      number.isEmpty mustBe true
      number.isContainer mustBe false
      number.format mustBe PredefType.Number
    }
    "support Real" in {
      real.kind mustBe PredefType.Real
      AST.errorDescription(real) mustBe PredefType.Real
      real.isEmpty mustBe true
      real.isContainer mustBe false
      real.format mustBe PredefType.Real
    }
    "support Temperature" in {
      temperature.kind mustBe PredefType.Temperature
      AST.errorDescription(temperature) mustBe PredefType.Temperature
      temperature.isEmpty mustBe true
      temperature.isContainer mustBe false
      temperature.format mustBe PredefType.Temperature
    }
    "support Time" in {
      time.kind mustBe PredefType.Time
      AST.errorDescription(time) mustBe PredefType.Time
      time.isEmpty mustBe true
      time.isContainer mustBe false
      time.format mustBe PredefType.Time
    }
    "support TimeStamp" in {
      timestamp.kind mustBe PredefType.TimeStamp
      AST.errorDescription(timestamp) mustBe PredefType.TimeStamp
      timestamp.isEmpty mustBe true
      timestamp.isContainer mustBe false
      timestamp.format mustBe PredefType.TimeStamp
    }
    "support URL" in {
      url.kind mustBe PredefType.URL
      AST.errorDescription(url) mustBe PredefType.URL
      url.isEmpty mustBe true
      url.isContainer mustBe false
      url.format mustBe PredefType.URL
    }
    "support UUID" in {
      uuid.kind mustBe PredefType.UUID
      AST.errorDescription(uuid) mustBe PredefType.UUID
      uuid.isEmpty mustBe true
      uuid.isContainer mustBe false
      uuid.format mustBe PredefType.UUID
    }
  }

  "Constructed Predefined Types" must {
    "support Pattern" in {
      pattern.isEmpty mustBe true
      pattern.isContainer mustBe false
      pattern.kind mustBe PredefType.Pattern
      AST.errorDescription(pattern) mustBe "Pattern(\"^$\")"
      pattern.format mustBe "Pattern(\"^$\")"
    }
    "support Range" in {
      range.isEmpty mustBe true
      range.isContainer mustBe false
      range.kind mustBe PredefType.Range
      AST.errorDescription(range) mustBe "Range(2,4)"
      range.format mustBe "Range(2,4)"
    }
    "support String" in {
      string.kind mustBe PredefType.String
      AST.errorDescription(string) mustBe PredefType.String
      string.format mustBe "String(42,)"
      string.isEmpty mustBe true
      string.isContainer mustBe false
    }

    "support UniqueId" in {
      id.format mustBe "Id(a.b)"
      id.kind mustBe PredefType.Id
      id.isEmpty mustBe true
      id.isContainer mustBe false
    }
  }

  val enumeration: Enumeration = Enumeration(
    At.empty,
    Seq(
      Enumerator(At.empty, Identifier(At.empty, "one")),
      Enumerator(At.empty, Identifier(At.empty, "two")),
      Enumerator(At.empty, Identifier(At.empty, "three"))
    )
  )

  val aggregation: Aggregation = Aggregation(
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

  val alternation: Alternation = Alternation(
    At.empty,
    Seq(
      AliasedTypeExpression(At.empty, "message", PathIdentifier(At.empty, Seq("a", "b"))),
      AliasedTypeExpression(At.empty, "message", PathIdentifier(At.empty, Seq("z", "y")))
    )
  )

  val mapping: Mapping = Mapping(At.empty, string, integer)

  val reference: EntityReferenceTypeExpression = EntityReferenceTypeExpression(
    At.empty,
    PathIdentifier(At.empty, Seq("a", "b", "c", "d", "entity"))
  )

  val message: AggregateUseCaseTypeExpression =
    AggregateUseCaseTypeExpression(At.empty, RecordCase, aggregation.fields)

  val alias: AliasedTypeExpression = AliasedTypeExpression(At.empty, "message", PathIdentifier(At.empty, Seq("a",
    "b", "foo")))

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
      alternation.format mustBe "one of { message a.b, message z.y }"
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
      AST.errorDescription(alias) mustBe "message a.b.foo"
      alias.format mustBe "message a.b.foo"
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
