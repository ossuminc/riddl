/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Folding

class FoldingTest extends ParsingTest {

  val input: String =
    """domain one is {
      |  context one is {
      |    connector a is { ??? }
      |    term whomprat is described by "a 2m long rat on Tatooine"
      |    flow b is {
      |      inlet b_in is String
      |      outlet b_out is Number
      |    }
      |  }
      |  // context one is { ??? }
      |  context two is {
      |    term ForcePush is described by "an ability of the Jedi"
      |    function foo is {
      |       requires { a: Integer, b: String }
      |       returns { ??? }
      |       body ???
      |     }
      |    type oneState is Integer
      |    entity one is {
      |      state entityState of oneState is { ??? }
      |      handler one  is { ??? }
      |      function one is { ??? }
      |      invariant one is ""
      |    }
      |    entity two is {
      |      state entityState of oneState is { ??? }
      |      handler one  is { ??? }
      |      function one is { ??? }
      |      invariant one is ???
      |    }
      |    adaptor one to context over.consumption is { ??? }
      |  }
      |  type AString = String
      |}
      |""".stripMargin

  val expectedResult: Seq[Seq[String]] = List(
    List("Root"),
    List("Root", "Domain 'one'"),
    List("Root", "Domain 'one'", "Context 'one'"),
    List("Root", "Domain 'one'", "Context 'one'", "Connector 'a'"),
    List("Root", "Domain 'one'", "Context 'one'", "Term 'whomprat'"),
    List("Root", "Domain 'one'", "Context 'one'", "Flow 'b'"),
    List("Root", "Domain 'one'", "Context 'one'", "Flow 'b'", "Inlet 'b_in'"),
    List("Root", "Domain 'one'", "Context 'one'", "Flow 'b'", "Outlet 'b_out'"),
    List("Root", "Domain 'one'", "LineComment(empty(10:3),context one is { ??? })"),
    List("Root", "Domain 'one'", "Context 'two'"),
    List("Root", "Domain 'one'", "Context 'two'", "Term 'ForcePush'"),
    List("Root", "Domain 'one'", "Context 'two'", "Function 'foo'"),
    List("Root", "Domain 'one'", "Context 'two'", "Type 'oneState'"),
    List("Root", "Domain 'one'", "Context 'two'", "Entity 'one'"),
    List("Root", "Domain 'one'", "Context 'two'", "Entity 'one'", "State 'entityState'"),
    List("Root", "Domain 'one'", "Context 'two'", "Entity 'one'", "Handler 'one'"),
    List("Root", "Domain 'one'", "Context 'two'", "Entity 'one'", "Function 'one'"),
    List("Root", "Domain 'one'", "Context 'two'", "Entity 'one'", "Invariant 'one'"),
    List("Root", "Domain 'one'", "Context 'two'", "Entity 'two'"),
    List("Root", "Domain 'one'", "Context 'two'", "Entity 'two'", "State 'entityState'"),
    List("Root", "Domain 'one'", "Context 'two'", "Entity 'two'", "Handler 'one'"),
    List("Root", "Domain 'one'", "Context 'two'", "Entity 'two'", "Function " + "'one'"),
    List("Root", "Domain 'one'", "Context 'two'", "Entity 'two'", "Invariant " + "'one'"),
    List("Root", "Domain 'one'", "Context 'two'", "Adaptor 'one'"),
    List("Root", "Domain 'one'", "Type 'AString'")
  )

  "Folding" should {
    "visit each definition" in {

      parseTopLevelDomains(input) match {
        case Left(errors) => fail(errors.format)
        case Right(content) =>
          val empty = Seq.empty[Seq[String]]
          val result = Folding.foldLeftWithStack(empty)(content) { case (track, definition, stack) =>
            val previous: Seq[String] = stack.map {
              case nv: NamedValue => nv.identify
              case rv: RiddlValue => rv.toString
            }.reverse
            definition match {
              case nv: NamedValue => track :+ (previous :+ nv.identify)
              case rv: RiddlValue => track :+ (previous :+ rv.toString)
            }
          }
          val expectedCount = 25
          result.length must be(expectedCount)
          result mustBe expectedResult
      }
    }
  }
}
