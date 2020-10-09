package com.yoppworks.ossum.riddl.translator.graph

import com.yoppworks.ossum.riddl.language.ParsingTest

/** Unit Tests For GraphASTTest */
class GraphASTTest extends ParsingTest {

  "GraphASTTest" should {
    "draw a simple domain" in {
      val input = """domain d_1 is { ??? }
                    |domain d_2 is {
                    |  context c_3 is { ??? }
                    |  context c_4 is { ??? }
                    |}
                    |""".stripMargin
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content.contents must not be empty
          val gast = GraphAST(content, GraphASTConfig())
          val actual = gast.drawRoot(content).mkString
          val expected =
            """digraph "root(default(0:0))" {
              |    subgraph cluster1 {
              |      label=d_1;
              |      "d_1(default(1:8))" [shape=ellipse, color=black, fillcolor=aquamarine, style=filled, fontsize=12.0];
              |    }
              |    subgraph cluster2 {
              |      label=d_2;
              |      "d_2(default(2:8))" [shape=ellipse, color=black, fillcolor=aquamarine, style=filled, fontsize=12.0];
              |        "c_3(default(3:11))" [shape=ellipse, color=black, fillcolor=yellow, style=filled, fontsize=12.0];
              |      "d_2(default(2:8))" -> "c_3(default(3:11))" ;
              |        "c_4(default(4:11))" [shape=ellipse, color=black, fillcolor=yellow, style=filled, fontsize=12.0];
              |      "d_2(default(2:8))" -> "c_4(default(4:11))" ;
              |    }
              |}
              |""".stripMargin
          actual mustBe (expected)
      }

    }
  }
}
