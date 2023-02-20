/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Definition

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
      |       returns {}
      |     }
      |    entity one is {
      |      state entityState is { ??? }
      |      handler one  is { ??? }
      |      function one is { ??? }
      |      invariant one is { ??? }
      |    }
      |    entity two is {
      |      state entityState is { ??? }
      |      handler one  is { ??? }
      |      function one is { ??? }
      |      invariant one is { ??? }
      |    }
      |    adaptor one to context over.consumption is { ??? }
      |  }
      |  type AString = String
      |}
      |""".stripMargin

  "Folding" should {
    "visit each definition" in {

      parseTopLevelDomains(input) match {
        case Left(errors) => fail(errors.format)
        case Right(content) =>
          val empty = Seq.empty[Seq[String]]
          val result = Folding.foldLeftWithStack(empty)(content) {
            case (track, definition, stack) =>
              val path = stack.map(_.identify).reverse :+ definition.identify
              track :+ path
          }
          val expectedCount = 25
          result.length must be(expectedCount)
          val expectedResult = List(
            List("Root"),
            List("Root", "Domain 'one'"),
            List("Root", "Domain 'one'", "Type 'AString'"),
            List("Root", "Domain 'one'", "Context 'one'"),
            List("Root", "Domain 'one'", "Context 'one'", "Flow 'b'"),
            List(
              "Root",
              "Domain 'one'",
              "Context 'one'",
              "Flow 'b'",
              "Inlet 'b_in'"
            ),
            List(
              "Root",
              "Domain 'one'",
              "Context 'one'",
              "Flow 'b'",
              "Outlet 'b_out'"
            ),
            List("Root", "Domain 'one'", "Context 'one'", "Term 'whomprat'"),
            List("Root", "Domain 'one'", "Context 'one'", "Connector 'a'"),
            List("Root", "Domain 'one'", "Context 'two'"),
            List("Root", "Domain 'one'", "Context 'two'", "Entity 'one'"),
            List(
              "Root",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'one'",
              "State " + "'entityState'"
            ),
            List(
              "Root",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'one'",
              "Handler 'one'"
            ),
            List(
              "Root",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'one'",
              "Function " + "'one'"
            ),
            List(
              "Root",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'one'",
              "Invariant " + "'one'"
            ),
            List("Root", "Domain 'one'", "Context 'two'", "Entity 'two'"),
            List(
              "Root",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'two'",
              "State " + "'entityState'"
            ),
            List(
              "Root",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'two'",
              "Handler 'one'"
            ),
            List(
              "Root",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'two'",
              "Function " + "'one'"
            ),
            List(
              "Root",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'two'",
              "Invariant " + "'one'"
            ),
            List("Root", "Domain 'one'", "Context 'two'", "Adaptor 'one'"),
            List("Root", "Domain 'one'", "Context 'two'", "Function 'foo'"),
            List(
              "Root",
              "Domain 'one'",
              "Context 'two'",
              "Function 'foo'",
              "Field 'a'"
            ),
            List(
              "Root",
              "Domain 'one'",
              "Context 'two'",
              "Function 'foo'",
              "Field 'b'"
            ),
            List("Root", "Domain 'one'", "Context 'two'", "Term 'ForcePush'")
          )
          result mustBe expectedResult
      }
    }

    case class Tracker(
      contPush: Int = 0,
      defs: Int = 0,
      contPop: Int = 0)

    class Tracking(print: Boolean = false) extends Folding.Folder[Tracker] {
      def openContainer(
        t: Tracker,
        container: Definition,
        stack: Seq[Definition]
      ): Tracker = {
        if (print) {
          info(
            "> " + container.identify + "(" + stack.map(_.identify)
              .mkString(", ") + ")"
          )
        }
        t.copy(contPush = t.contPush + 1)
      }

      def doDefinition(
        t: Tracker,
        definition: Definition,
        stack: Seq[Definition]
      ): Tracker = {
        if (print) {
          info(
            "==" + definition.identify + "(" + stack.map(_.identify)
              .mkString(", ") + ")"
          )
        }
        t.copy(defs = t.defs + 1)
      }

      def closeContainer(
        t: Tracker,
        container: Definition,
        stack: Seq[Definition]
      ): Tracker = {
        if (print) {
          info(
            "< " + container.identify + "(" + stack.map(_.identify)
              .mkString(", ") + ")"
          )
        }
        t.copy(contPop = t.contPop + 1)
      }
    }

    "can 'fold around' 3 functions" in {
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(root) =>
          val tracking = new Tracking
          val tracked = Folding.foldAround(Tracker(), root, tracking)
          val expectedContainers = 16
          val expectedLeaves = 9
          tracked.defs mustBe expectedLeaves
          tracked.contPush mustBe expectedContainers
          tracked.contPush mustBe tracked.contPop
      }
    }
  }
}
