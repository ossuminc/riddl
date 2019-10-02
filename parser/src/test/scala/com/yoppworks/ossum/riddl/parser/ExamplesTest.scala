package com.yoppworks.ossum.riddl.parser

import java.io.File

import TopLevelParser.topLevelDomains

import scala.io.Source

/** Unit Tests For ExamplesTest */
class ExamplesTest extends ParsingTest {

  val files: Map[String, String] = Map(
    "Reactive BBQ" → "rbbq.riddl",
    "Empty" → "empty.riddl",
    "Pet Store" → "petstore.riddl",
    "Everything" → "everything.riddl"
  )

  "Examples" should {
    "all compile" in {
      val results = for ((label, fileName) ← files) yield {
        checkFile(label, fileName)
      }
    }
  }
}
