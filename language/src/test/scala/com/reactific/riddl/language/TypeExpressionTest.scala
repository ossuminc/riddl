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

  "TypeExpression" must {
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
      d.isEmpty mustBe true
      d.isContainer mustBe false
    }
    "support DateTime" in {
      val d = DateTime(At.empty)
      d.kind mustBe Predefined.DateTime
      d.isEmpty mustBe true
      d.isContainer mustBe false
    }
    "support Decimal" in {
      val d = Decimal(At.empty)
      d.kind mustBe Predefined.Decimal
      d.isEmpty mustBe true
      d.isContainer mustBe false
      d.format mustBe Predefined.Decimal
    }
    "support Duration" in {
      val d = Duration(At.empty)
      d.kind mustBe Predefined.Duration
      d.isEmpty mustBe true
      d.isContainer mustBe false
      d.format mustBe Predefined.Duration
    }
    "support Integer" in {
      val i = Integer(At.empty)
      i.kind mustBe Predefined.Integer
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.Integer
    }
    "support LatLong" in {
      /*val i = LatLong(Location.empty)
      i.kind mustBe Predefined.LatLong
      i.isEmpty mustBe true
      i.isContainer mustBe false
      i.format mustBe Predefined.LatLong*/
    }
    "support UniqueId" in {
      val i =
        UniqueId(At.empty, PathIdentifier(At.empty, Seq("a", "b")))
      i.format mustBe "Id(a.b)"
    }

    /*
    final val LatLong = "LatLong"
    final val Length = "Length" // in meters
    final val Luminosity = "Luminosity" // in candelas
    final val Mass = "Mass" // in kilograms
    final val Mole = "Mole" // in mol (amount of substance)
    final val Nothing = "Nothing"
    final val Number = "Number"
    final val Pattern = "Pattern"
    final val Range = "Range"
    final val Real = "Real"
    final val String = "String"
    final val Temperature = "Temperature" // in Kelvin
    final val Time = "Time"
    final val TimeStamp = "TimeStamp"
    final val URL = "URL"
    final val UUID = "UUID"
     */

    "support Strng" in {
      val s = Strng(At.empty)
      s.kind mustBe Predefined.String
      AST.kind(s) mustBe Predefined.String
    }
  }
}
