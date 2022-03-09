package com.reactific.riddl.language

import com.reactific.riddl.language.AST.{Definition, ParentDefOf}

class FoldingTest extends ParsingTest {

  val input = """domain one is {
                |  plant one is {
                |    pipe a is { ??? }
                |    term whomprat is described by "a 2m long rat on Tatooine"
                |    flow b is {
                |      inlet b_in is String
                |      outlet b_out is Number
                |    }
                |  }
                |  context one is { ??? }
                |  context two is {
                |    term ForcePush is described by "an ability of the Jedi"
                |    function foo is {
                |       requires { a: Integer, b: String }
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
                |    adaptor one for context over.consumption is { ??? }
                |  }
                |  type AString = String
                |}
                |""".stripMargin

  "Folding" should {
    "visit each definition" in {

      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          val empty = Seq.empty[Seq[String]]
          val result = Folding.foldLeftWithStack(empty)(content) {
            case (track, definition, stack) =>
              val path = stack.map(_.kindId).reverse :+ definition.kindId
              track :+ path
          }
          val expectedCount = 24
          result.length must be(expectedCount)
          val expectedResult = List(
            List("Root 'Root'"),
            List("Root 'Root'", "Domain 'one'"),
            List("Root 'Root'", "Domain 'one'", "Type 'AString'"),
            List("Root 'Root'", "Domain 'one'", "Context 'one'"),
            List("Root 'Root'", "Domain 'one'", "Context 'two'"),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'one'"
            ),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'one'",
              "State " + "'entityState'"
            ),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'one'",
              "Handler 'one'"
            ),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'one'",
              "Function " + "'one'"
            ),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'one'",
              "Invariant " + "'one'"
            ),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'two'"
            ),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'two'",
              "State " + "'entityState'"
            ),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'two'",
              "Handler 'one'"
            ),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'two'",
              "Function " + "'one'"
            ),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'two'",
              "Invariant " + "'one'"
            ),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Context 'two'",
              "Adaptor 'one'"
            ),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Context 'two'",
              "Function 'foo'"
            ),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Context 'two'",
              "Term 'ForcePush'"
            ),
            List("Root 'Root'", "Domain 'one'", "Plant 'one'"),
            List("Root 'Root'", "Domain 'one'", "Plant 'one'", "Pipe 'a'"),
            List("Root 'Root'", "Domain 'one'", "Plant 'one'", "Flow 'b'"),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Plant 'one'",
              "Flow 'b'",
              "Inlet 'b_in'"
            ),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Plant 'one'",
              "Flow 'b'",
              "Outlet 'b_out'"
            ),
            List(
              "Root 'Root'",
              "Domain 'one'",
              "Plant 'one'",
              "Term 'whomprat'"
            )
          )
          result mustBe expectedResult
      }
    }
    "can 'fold around' 3 functions" in {
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(root) =>
          case class Tracker(contPush: Int = 0, defs: Int = 0, contPop: Int = 0)
          class Tracking extends Folding.Folder[Tracker] {
            def openContainer(
              t: Tracker,
              container: ParentDefOf[Definition],
              stack: Seq[ParentDefOf[Definition]]
            ): Tracker = {
              println(
                "> " + container.identify + "(" + stack.map(_.identify)
                  .mkString(", ") + ")"
              )
              t.copy(contPush = t.contPush + 1)
            }

            def doDefinition(
              t: Tracker,
              definition: Definition,
              stack: Seq[ParentDefOf[Definition]]
            ): Tracker = {
              println(
                "==" + definition.identify + "(" + stack.map(_.identify)
                  .mkString(", ") + ")"
              )
              t.copy(defs = t.defs + 1)
            }

            def closeContainer(
              t: Tracker,
              container: ParentDefOf[Definition],
              stack: Seq[ParentDefOf[Definition]]
            ): Tracker = {
              println(
                "< " + container.identify + "(" + stack.map(_.identify)
                  .mkString(", ") + ")"
              )

              t.copy(contPop = t.contPop + 1)
            }
          }
          val tracking = new Tracking
          val tracked = Folding.foldAround(Tracker(), root, tracking)
          val expectedContainers = 17
          val expectedDefinitions = 7
          tracked.contPush mustBe expectedContainers
          tracked.contPush mustBe tracked.contPop
          tracked.defs mustBe expectedDefinitions
      }
    }
  }
}
