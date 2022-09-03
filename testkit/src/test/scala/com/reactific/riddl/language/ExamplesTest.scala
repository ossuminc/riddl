package com.reactific.riddl.language

import com.reactific.riddl.language.testkit.ValidatingTest

import java.nio.file.Path

/** Unit Tests For ExamplesTest */
class ExamplesTest extends ValidatingTest {

  val files: Map[String, String] = Map(
    "Reactive BBQ" -> "rbbq.riddl",
    "Empty" -> "empty.riddl",
    "Pet Store" -> "petstore.riddl",
    "Everything" -> "everything.riddl",
    "DokN" -> "dokn.riddl"
  )

  val dir = "testkit/src/test/input/"

  "Examples" should {
    "all compile" in {
      for ((label, fileName) <- files) yield {
        parseAndValidateFile(
          label, Path.of(dir, fileName).toFile,
          CommonOptions(showTimes=true,showWarnings=false, showMissingWarnings=false, showStyleWarnings=false)
        )
      }
      succeed
    }
  }
}
