package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.Validation.MissingWarning
import com.yoppworks.ossum.riddl.language.Validation.StyleWarning

/** Validate files */
class ValidateExamplesTest extends ValidatingTest {

  val files: Map[String, String] = Map(
    "Reactive BBQ" -> "rbbq.riddl"
  )

  "ValidateExamples" should {
    "all validate with no errors or warnings" in {
      val results = for ((label, fileName) <- files) yield {
        validateFile(label, fileName) {
          case (_, messages) =>
            val errors = messages.filter(_.kind.isError)
            val warnings = messages
              .filter(_.kind.isWarning)
              .filterNot(_.kind == MissingWarning)
              .filterNot(_.kind == StyleWarning)
            info(errors.map(_.format(fileName)).mkString("\n"))
            info(warnings.map(_.format(fileName)).mkString("\n"))
            errors mustBe empty
            warnings mustBe empty
        }
      }
    }
  }
}
