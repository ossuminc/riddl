package com.yoppworks.ossum.riddl.validator

/** Validate files */
class ExamplesTest extends ValidatingTest {

  val files: Map[String, String] = Map(
    "Reactive BBQ" -> "rbbq.riddl"
  )

  "Examples" should {
    "all validate" in {
      val results = for ((label, fileName) <- files) yield {
        validateFile(label, fileName) {
          case (_, messages) =>
            println(messages.map(_.format(fileName)).mkString("\n"))
            // messages.size mustBe 0
            pending
        }
      }
    }
  }

}
