package com.yoppworks.ossum.riddl.translator.graph

import java.nio.file.Path

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.scalatest.matchers.must
import org.scalatest.wordspec.AnyWordSpec

/** Unit Tests For GraphVizAPITest */
class GraphVizAPITest extends AnyWordSpec with must.Matchers {

  private final val binPathForOS: Path = {
    val osName = System.getProperty("os.name").replaceAll("\\s", "")
    osName match {
      case "MacOSX" => Path.of("/usr/local/bin")
      case _        => Path.of("/usr/bin")
    }
  }
  private final val simpleDiagram = """graph {
                                      |    a -- b;
                                      |    b -- c;
                                      |    a -- c;
                                      |    d -- c;
                                      |    e -- c;
                                      |    e -- a;
                                      |}""".stripMargin

  import GraphVizAPI._

  private final val buffer = new StringBuilder(simpleDiagram).addln

  val basicConfig = GraphVizAPIConfig()
    .copy(dotPath = binPathForOS, dotProgramName = dot, outputType = svg)

  "GraphVizAPITest" should {
    "draw a simple diagram in dot" in {
      val config = basicConfig.copy(dotProgramName = circo, outputType = dot_)
      val graphviz = GraphVizAPI(config, buffer)
      val resultF: Future[(Int, String, String)] = graphviz.runDot
      val result = Await.result(resultF, 1.minute)
      result._1 mustBe 0
      result._3 mustBe empty
      result._2 must startWith("graph {")
    }

    "draw a simple diagram in SVG" in {
      val graphviz = GraphVizAPI(basicConfig, buffer)
      val resultF: Future[(Int, String, String)] = graphviz.runDot
      val result = Await.result(resultF, 1.minute)
      result._1 mustBe 0
      result._3 mustBe empty
      val expected =
        // scalastyle:off
        """<?xml version="1.0" encoding="UTF-8" standalone="no"?><!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd"><!-- Generated by graphviz version 2"""
      // scalastyle:on
      result._2 must startWith(expected)

    }
  }
}
