package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.Validation.MissingWarning
import com.yoppworks.ossum.riddl.language.Validation.StyleWarning
import com.yoppworks.ossum.riddl.language.Validation.ValidationOptions

import scala.collection.immutable.HashMap
import scala.util.hashing.Hashing

/** Validate files */
class ValidateExamplesTest extends ValidatingTest {

  val files: Map[String, String] = Map("Reactive BBQ" -> "rbbq.riddl")

  "ValidateExamples" should {
    "all validate with no errors or warnings" in {
      val results = for ((label, fileName) <- files) yield {
        validateFile(label, fileName) { case (_, messages) =>
          val errors = messages.filter(_.kind.isError)
          val warnings = messages.iterator.filter(_.kind.isWarning)
            .filterNot(_.kind == MissingWarning).filterNot(_.kind == StyleWarning).toList
          info(errors.iterator.map(_.format).mkString("\n"))
          info(warnings.iterator.map(_.format).mkString("\n"))
          errors mustBe empty
          warnings mustBe empty
        }
      }
    }
  }

  "Enumerations" should {
    "enforce Enumerators to start with lower case" in {
      validateFile("t0001", "enumerations/t0001.riddl") { case (_, messages) =>
        assert(messages.exists(msg => msg.kind.isStyle))
      }
    }
    "allow enumerators with values" in {
      validateFile("t0002", "enumerations/t0002.riddl") { case (result, messages) =>
        assert(!messages.exists(_.kind.isError))
      }
    }
  }
  "Mappings" should {
    "allow ranges" in {
      validateFile("t0001", "mappings/t0001.riddl") { case (result, messages) =>
        assert(!messages.exists(_.kind.isError))
      }
    }
  }

  "Ranges" should {
    "allow ranges" in {
      validateFile("t0001", "ranges/t0001.riddl") { case (result, messages) =>
        assert(!messages.exists(_.kind.isError))
      }
    }
  }

  "options.showStyleWarnings" should {
    "determine if style warnings are returned from validation" in {
      validateFile(
        "badstyle",
        "domains/badstyle.riddl",
        options = ValidationOptions(
          showTimes = false,
          showWarnings = true,
          showMissingWarnings = true,
          showStyleWarnings = false
        )
      ) { case (result, messages) =>
        assert(!messages.exists(_.kind.isError))
        assert(!messages.exists(_.kind.isStyle))
        assert(messages.exists(_.kind.isMissing))
      }
      validateFile(
        "badstyle",
        "domains/badstyle.riddl",
        options = ValidationOptions(
          showTimes = false,
          showWarnings = true,
          showMissingWarnings = true,
          showStyleWarnings = true
        )
      ) { case (result, messages) =>
        assert(!messages.exists(_.kind.isError))
        assert(messages.exists(_.kind.isStyle))
        assert(messages.exists(_.kind.isMissing))
      }
    }
  }
  "options.showMissingWarnings" should {
    "determine if missing warnings are returned from validation" in {
      validateFile(
        "badstyle",
        "domains/badstyle.riddl",
        options = ValidationOptions(
          showTimes = false,
          showWarnings = true,
          showMissingWarnings = false,
          showStyleWarnings = true
        )
      ) { case (result, messages) =>
        assert(!messages.exists(_.kind.isError))
        assert(!messages.exists(_.kind.isMissing))
      }
      validateFile(
        "badstyle",
        "domains/badstyle.riddl",
        options = ValidationOptions(
          showTimes = false,
          showWarnings = true,
          showMissingWarnings = true,
          showStyleWarnings = true
        )
      ) { case (result, messages) =>
        assert(!messages.exists(_.kind.isError))
        assert(messages.exists(_.kind.isMissing))
      }
    }
  }

}
