package com.yoppworks.ossum.riddl.language

class FoldingTest extends ParsingTest {

  "Folding" should {
    "visit each definition" in {
      val input =
        """domain one is {
          |  plant one is {
          |    pipe a is { ??? }
          |    processor b is {
          |      inlet b_in is String
          |      outlet b_out is Number
          |    }
          |    joint foo
          |  }
          |  interaction one is { ??? }
          |  context one is { ??? }
          |  context two is {
          |    interaction two is { ??? }
          |    function foo is { ??? }
          |    entity one is { ??? }
          |    entity two is {
          |      state entityState is {}
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
          val count = Folding.foldLeft[Int](0)(content) {
            case (value, _, _) =>
              value + 1
          }
          val expected = 17
          count must be(expected)
      }
    }
  }
}
