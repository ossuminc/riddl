package com.yoppworks.ossum.riddl.language

class FoldingTest extends ParsingTest {

  "Folding" should {
    "visit each definition" in {
      val input = """domain one is {
                    |  plant one is {
                    |    pipe a is { ??? }
                    |    flow b is {
                    |      inlet b_in is String
                    |      outlet b_out is Number
                    |    }
                    |  }
                    |  interaction one is { ??? }
                    |  context one is { ??? }
                    |  context two is {
                    |    interaction two is { ??? }
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
                    |}
                    |""".stripMargin
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          val empty = Seq.empty[Seq[String]]
          val result = Folding.foldLeft(empty)(content) { case (track, definition, stack) =>
            val path = stack.toSeq.map(_.identify).reverse :+ definition.identify
            track :+ path
          }
          val expectedCount = 23
          result.length must be(expectedCount)
          val expectedResult = List(
            List("Root '<path>'"),
            List("Root '<path>'", "Domain 'one'"),
            List("Root '<path>'", "Domain 'one'", "Context 'one'"),
            List("Root '<path>'", "Domain 'one'", "Context 'two'"),
            List("Root '<path>'", "Domain 'one'", "Context 'two'", "Entity 'one'"),
            List(
              "Root '<path>'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'one'",
              "State " + "'entityState'"
            ),
            List("Root '<path>'", "Domain 'one'", "Context 'two'", "Entity 'one'", "Handler 'one'"),
            List(
              "Root '<path>'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'one'",
              "Function " + "'one'"
            ),
            List(
              "Root '<path>'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'one'",
              "Invariant " + "'one'"
            ),
            List("Root '<path>'", "Domain 'one'", "Context 'two'", "Entity 'two'"),
            List(
              "Root '<path>'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'two'",
              "State " + "'entityState'"
            ),
            List("Root '<path>'", "Domain 'one'", "Context 'two'", "Entity 'two'", "Handler 'one'"),
            List(
              "Root '<path>'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'two'",
              "Function " + "'one'"
            ),
            List(
              "Root '<path>'",
              "Domain 'one'",
              "Context 'two'",
              "Entity 'two'",
              "Invariant " + "'one'"
            ),
            List("Root '<path>'", "Domain 'one'", "Context 'two'", "Adaptor 'one'"),
            List("Root '<path>'", "Domain 'one'", "Context 'two'", "Function 'foo'"),
            List("Root '<path>'", "Domain 'one'", "Context 'two'", "Interaction 'two'"),
            List("Root '<path>'", "Domain 'one'", "Interaction 'one'"),
            List("Root '<path>'", "Domain 'one'", "Plant 'one'"),
            List("Root '<path>'", "Domain 'one'", "Plant 'one'", "Pipe 'a'"),
            List("Root '<path>'", "Domain 'one'", "Plant 'one'", "Flow 'b'"),
            List("Root '<path>'", "Domain 'one'", "Plant 'one'", "Flow 'b'", "Inlet 'b_in'"),
            List("Root '<path>'", "Domain 'one'", "Plant 'one'", "Flow 'b'", "Outlet 'b_out'")
          )
          result mustBe expectedResult
      }
    }
  }
}
