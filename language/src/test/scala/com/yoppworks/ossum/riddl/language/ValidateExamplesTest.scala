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
            val warnings = messages.iterator
              .filter(_.kind.isWarning)
              .filterNot(_.kind == MissingWarning)
              .filterNot(_.kind == StyleWarning)
              .toList
            info(errors.iterator.map(_.format(fileName)).mkString("\n"))
            info(warnings.iterator.map(_.format(fileName)).mkString("\n"))
            errors mustBe empty
            warnings mustBe empty
        }
      }
    }
  }

  "Enumerations" should {
    "enforce Enumerators to start with lower case" in {
      validateFile("t0001", "enumerations/t0001.riddl") {
        case (_, messages) =>
          assert(messages.exists(msg => msg.kind.isStyle))
      }
    }
  }
}
