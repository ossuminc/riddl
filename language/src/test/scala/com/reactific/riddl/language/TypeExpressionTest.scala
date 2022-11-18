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

  "Simple Predefined Types" must {
    "support Abstract" in {
      val a = Abstract(At.empty)
      a.kind mustBe Predefined.Abstract
      AST.kind(a) mustBe Predefined.Abstract
    }
    "support Boolean" in {
      val b = Bool(At.empty)
      b.kind mustBe Predefined.Boolean
      AST.kind(b) mustBe Predefined.Boolean
      b.isEmpty mustBe true
      b.isContainer mustBe false
    }
    "support Current" in {
      val c = Current(At.empty)
      c.kind mustBe Predefined.Current
      AST.kind(c) mustBe Predefined.Current
      c.isEmpty mustBe true
      c.isContainer mustBe false
    }
    "support Currency" in {
      val c = Currency(At.empty, "CA")
      c.kind mustBe Predefined.Currency
      AST.kind(c) mustBe Predefined.Currency
      c.isEmpty mustBe true
      c.isContainer mustBe false
    }
    "support Date" in {
      val d = Date(At.empty)
      d.kind mustBe Predefined.Date
      AST.kind(d) mustBe Predefined.Date
      d.isEmpty mustBe true
      d.isContainer mustBe false
    }
    "support DateTime" in {
      val d = DateTime(At.empty)
      d.kind mustBe Predefined.DateTime
      AST.kind(d) mustBe Predefined.DateTime
      d.isEmpty mustBe true
      d.isContainer mustBe false
    }
    "support Decimal" in {
      val d = Decimal(At.empty)
      d.kind mustBe Predefined.Decimal
      AST.kind(d) mustBe Predefined.Decimal
      d.isEmpty mustBe true
      d.isContainer mustBe false
      d.format mustBe Predefined.Decimal
    }
    "support Duration" in {
      val d = Duration(At.empty)
      d.kind mustBe Predefined.Duration
      AST.kind(d) mustBe Predefined.Duration
      d.isEmpty mustBe true
      d.isContainer mustBe false
      d.format mustBe Predefined.Duration
    }
    "support Integer" in {
      val i = Integer(At.empty)
      i.kind mustBe Predefined.Integer
      AST.kind(i) mustBe Predefined.Integer
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.Integer
    }
    "support Length" in {
      val i = Length(At.empty)
      i.kind mustBe Predefined.Length
      AST.kind(i) mustBe Predefined.Length
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.Length
    }
    "support Location" in {
      val i = Location(At.empty)
      i.kind mustBe Predefined.Location
      AST.kind(i) mustBe Predefined.Location
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.Location
    }
    "support Luminosity" in {
      val i = Luminosity(At.empty)
      i.kind mustBe Predefined.Luminosity
      AST.kind(i) mustBe Predefined.Luminosity
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.Luminosity
    }
    "support Mass" in {
      val i = Mass(At.empty)
      i.kind mustBe Predefined.Mass
      AST.kind(i) mustBe Predefined.Mass
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.Mass
    }
    "support Mole" in {
      val i = Mole(At.empty)
      i.kind mustBe Predefined.Mole
      AST.kind(i) mustBe Predefined.Mole
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.Mole
    }
    "support Nothing" in {
      val i = Nothing(At.empty)
      i.kind mustBe Predefined.Nothing
      AST.kind(i) mustBe Predefined.Nothing
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.Nothing
    }
    "support Number" in {
      val i = Number(At.empty)
      i.kind mustBe Predefined.Number
      AST.kind(i) mustBe Predefined.Number
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.Number
    }
    "support Real" in {
      val i = Real(At.empty)
      i.kind mustBe Predefined.Real
      AST.kind(i) mustBe Predefined.Real
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.Real
    }
    "support Temperature" in {
      val i = Temperature(At.empty)
      i.kind mustBe Predefined.Temperature
      AST.kind(i) mustBe Predefined.Temperature
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.Temperature
    }
    "support Time" in {
      val i = Time(At.empty)
      i.kind mustBe Predefined.Time
      AST.kind(i) mustBe Predefined.Time
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.Time
    }
    "support TimeStamp" in {
      val i = TimeStamp(At.empty)
      i.kind mustBe Predefined.TimeStamp
      AST.kind(i) mustBe Predefined.TimeStamp
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.TimeStamp
    }
    "support URL" in {
      val i = URL(At.empty)
      i.kind mustBe Predefined.URL
      AST.kind(i) mustBe Predefined.URL
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.URL
    }
    "support UUID" in {
      val i = UUID(At.empty)
      i.kind mustBe Predefined.UUID
      AST.kind(i) mustBe Predefined.UUID
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.UUID
    }
  }

  "Constructed Predefined Types" must {
    "support Pattern" in {
      val i = Pattern(At.empty, Seq(LiteralString(At.empty, "^$")))
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.kind mustBe Predefined.Pattern
      AST.kind(i) mustBe Predefined.Pattern
      i.format mustBe "Pattern(\"^$\")"
    }
    "support Range" in {
      val r = RangeType(At.empty, 2, 4)
      r.isEmpty mustBe true
      r.isContainer mustBe false
      r.kind mustBe Predefined.Range
      AST.kind(r) mustBe Predefined.Range
      r.format mustBe "Range(2,4)"
    }
    "support String" in {
      val s = Strng(At.empty, Some(42), None)
      s.kind mustBe Predefined.String
      AST.kind(s) mustBe Predefined.String
      s.format mustBe "String(42,)"
      s.isEmpty mustBe true
      s.isContainer mustBe false
    }

    "support UniqueId" in {
      val i = UniqueId(At.empty, PathIdentifier(At.empty, Seq("a", "b")))
      i.format mustBe "Id(a.b)"
      i.kind mustBe Predefined.Id
      i.isEmpty mustBe true
      i.isContainer mustBe false
    }

  }
}
