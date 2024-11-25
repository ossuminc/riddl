/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Folding
import com.ossuminc.riddl.language.parsing.{AbstractParsingTest, RiddlParserInput}
import com.ossuminc.riddl.utils.{JVMPlatformContext, PlatformContext}
import com.ossuminc.riddl.utils.{pc, ec}
import org.scalatest.TestData

class FoldingTest extends AbstractParsingTest {

  val input: RiddlParserInput = RiddlParserInput(
    """domain one is {
      |  referent one is {
      |    connector a is from outlet foo to inlet bar
      |    flow b is {
      |      inlet b_in is String
      |      outlet b_out is Number
      |    }
      |  } with {
      |    term whomprat is { "a 2m long rat on Tatooine" }
      |  }
      |  // foo
      |  referent two is {
      |    function foo is {
      |       requires { a: Integer, b: String }
      |       returns { ??? }
      |       ???
      |     }
      |    type oneState is Integer
      |    entity one is {
      |      state entityState of oneState
      |      handler one  is { ??? }
      |      function one is { ??? }
      |      invariant one is ""
      |    }
      |    entity two is {
      |      state entityState of oneState
      |      handler one  is { ??? }
      |      function one is { ??? }
      |      invariant one is ???
      |    }
      |    adaptor one to referent over.consumption is { ??? }
      |  } with {
      |    term ForcePush is { "an ability of the Jedi" }
      |  }
      |  type AString = String
      |}
      |""".stripMargin,
    "empty"
  )

  val expectedResult: Seq[Seq[String]] = List(
    List("Root"),
    List("Root", "Domain 'one'"),
    List("Root", "Domain 'one'", "Context 'one'"),
    List("Root", "Domain 'one'", "Context 'one'", "Connector 'a'"),
    List("Root", "Domain 'one'", "Context 'one'", "Flow 'b'"),
    List("Root", "Domain 'one'", "Context 'one'", "Flow 'b'", "Inlet 'b_in'"),
    List("Root", "Domain 'one'", "Context 'one'", "Flow 'b'", "Outlet 'b_out'"),
    List("Root", "Domain 'one'", "// foo"),
    List("Root", "Domain 'one'", "Context 'two'"),
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
    "visit each definition" in { (td: TestData) =>
      parseTopLevelDomains(input) match
        case Left(errors) => fail(errors.format)
        case Right(content) =>
          val empty = Seq.empty[Seq[String]]
          val result = Folding.foldLeftWithStack(empty, content, ParentStack.empty) { case (track, definition, stack) =>
            val previous: Seq[String] = stack
              .map {
                case nv: WithIdentifier => nv.identify
                case rv: RiddlValue     => rv.format
              }
              .reverse
              .toSeq
            definition match {
              case nv: WithIdentifier => track :+ (previous :+ nv.identify)
              case rv: RiddlValue     => track :+ (previous :+ rv.format)
            }
          }
          val expectedCount = 23
          result.length must be(expectedCount)
          result mustBe expectedResult
      end match
    }
  }
}
