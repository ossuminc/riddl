package com.reactific.riddl.language

/** Unit Tests For ExamplesTest */
class ExamplesTest extends ParsingTest {

  val files: Map[String, String] = Map(
    "Reactive BBQ" -> "rbbq.riddl",
    "Empty" -> "empty.riddl",
    "Pet Store" -> "petstore.riddl",
    "Everything" -> "everything.riddl"
  )

  "Examples" should {
    "all compile" in {
      for ((label, fileName) <- files) yield { checkFile(label, fileName) }
      succeed
    }
  }
}
