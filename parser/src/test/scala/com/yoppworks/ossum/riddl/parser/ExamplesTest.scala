package com.yoppworks.ossum.riddl.parser

import java.io.File

import TopLevelParser.topLevelDomains

/** Unit Tests For ExamplesTest */
class ExamplesTest extends ParsingTest {

  val directory = "parser/src/test/input/"

  val files: Map[String, String] = Map(
    "Reactive BBQ" → "rbbq.riddl",
    "Empty" → "empty.riddl",
    "Pet Store" → "petstore.riddl",
    "Everything" → "everything.riddl"
  )

  "Examples" should {
    "all compile" in {
      val results = for ((label, fileName) ← files) yield {
        val file = new File(directory + fileName)
        (label, TopLevelParser.parseFile(file, topLevelDomains(_)))
      }
      var failed = false
      println(
        results
          .map {
            case (label, Left(error)) ⇒
              failed = true
              s"$label:$error"
            case (label, Right(_)) ⇒
              s"$label: Succeeded"
          }
          .mkString("\n")
      )
      if (failed)
        fail("Not all examples succeeded")
      else
        succeed
    }
  }
}
