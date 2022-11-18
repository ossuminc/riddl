/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.AST.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

/** Unit Tests For AssignmentCompatibility */
class AssignmentCompatibilityTest extends AnyWordSpec with Matchers {

  val abstrct = Abstract(At.empty)
  val datetime = DateTime(At.empty)
  val timestamp = TimeStamp(At.empty)
  val date = Date(At.empty)
  val time = Time(At.empty)
  val number = Number(At.empty)
  val integer = Integer(At.empty)
  val real = Real(At.empty)
  val decimal = Decimal(At.empty)
  val range = AST.RangeType(At.empty, 0, 100)
  val nothing = AST.Nothing(At.empty)
  val string = AST.Strng(At.empty)

  "AssignmentCompatibility" should {
    "check compatibility of Date" in {
      date.isAssignmentCompatible(timestamp) must be(true)
      date.isAssignmentCompatible(datetime) must be(true)
      date.isAssignmentCompatible(date) must be(true)
      date.isAssignmentCompatible(abstrct) must be(true)
      date.isAssignmentCompatible(string) must be(true)
      date.isAssignmentCompatible(time) must be(false)
      date.isAssignmentCompatible(nothing) must be(false)
    }
    "check compatibility of DateTime " in {
      datetime.isAssignmentCompatible(datetime) must be(true)
      datetime.isAssignmentCompatible(timestamp) must be(true)
      datetime.isAssignmentCompatible(abstrct) must be(true)
      datetime.isAssignmentCompatible(date) must be(true)
      datetime.isAssignmentCompatible(string) must be(true)
      datetime.isAssignmentCompatible(number) must be(false)
    }

    "check compatibility of Nothing " in {
      nothing.isAssignmentCompatible(datetime) must be(false)
      nothing.isAssignmentCompatible(timestamp) must be(false)
      nothing.isAssignmentCompatible(abstrct) must be(false)
      nothing.isAssignmentCompatible(date) must be(false)
      nothing.isAssignmentCompatible(number) must be(false)
      nothing.isAssignmentCompatible(string) must be(false)
    }

    "check compatibility of Time" in {
      time.isAssignmentCompatible(datetime) must be(true)
      time.isAssignmentCompatible(timestamp) must be(true)
      time.isAssignmentCompatible(abstrct) must be(true)
      time.isAssignmentCompatible(string) must be(true)
      time.isAssignmentCompatible(date) must be(false)
      time.isAssignmentCompatible(integer) must be(false)
      time.isAssignmentCompatible(number) must be(false)
    }

    "check compatibility of TimeStamp" in {
      timestamp.isAssignmentCompatible(string) must be(true)
      timestamp.isAssignmentCompatible(timestamp) must be(true)
      timestamp.isAssignmentCompatible(datetime) must be(true)
      timestamp.isAssignmentCompatible(date) must be(true)
      timestamp.isAssignmentCompatible(abstrct) must be(true)
    }
  }
}
