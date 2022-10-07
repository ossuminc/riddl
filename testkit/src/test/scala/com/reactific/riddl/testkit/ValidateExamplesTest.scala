package com.reactific.riddl.testkit

import com.reactific.riddl.language.CommonOptions

/** Validate files */
class ValidateExamplesTest extends ValidatingTest {

  val files: Map[String, String] = Map("Reactive BBQ" -> "rbbq.riddl")

  "ValidateExamples" should {
    "all validate with no errors or warnings" in {
      for ((label, fileName) <- files) yield {
        validateFile(label, fileName) { case (_, messages) =>
          val errors = messages.filter(_.kind.isError)
          val warnings = messages.iterator
            .filter(m => m.kind.isWarning && !m.kind.isIgnorable).toList
          info(s"Errors:\n${errors.format}")
          info(s"Warnings:\n${warnings.format}")
          errors mustBe empty
          warnings mustBe empty
        }
      }
    }
  }

  "Enumerations" should {
    "enforce Enumerators to start with lower case" in {
      validateFile(label = "t0001", fileName = "enumerations/t0001.riddl") {
        case (_, messages) =>
          assertValidationMessage(messages, "style warnings")(_.kind.isStyle)
      }
    }
    "allow enumerators with values" in {
      validateFile("t0002", "enumerations/t0002.riddl") { case (_, messages) =>
        assert(!messages.exists(_.kind.isError))
      }
    }
  }
  "Mappings" should {
    "allow ranges" in {
      validateFile("t0001", "mappings/t0001.riddl") { case (_, messages) =>
        assert(!messages.exists(_.kind.isError))
      }
    }
  }

  "Ranges" should {
    "allow ranges" in {
      validateFile("t0001", "ranges/t0001.riddl") { case (_, messages) =>
        assert(!messages.exists(_.kind.isError))
      }
    }
  }

  "options.showStyleWarnings" should {
    "determine if style warnings are returned from validation" in {
      validateFile(
        label = "badstyle",
        fileName = "domains/badstyle.riddl",
        options = CommonOptions(showStyleWarnings = false)
      ) { case (_, messages) =>
        assert(!messages.exists(_.kind.isError))
        assert(!messages.exists(_.kind.isStyle))
        assert(messages.exists(_.kind.isMissing))
      }
      validateFile(
        label = "badstyle",
        fileName = "domains/badstyle.riddl",
        options = CommonOptions()
      ) { case (_, messages) =>
        assert(!messages.exists(_.kind.isError))
        assert(messages.exists(_.kind.isStyle))
        assert(messages.exists(_.kind.isMissing))
      }
    }
  }
  "options.showMissingWarnings" should {
    "determine if missing warnings are returned from validation" in {
      validateFile(
        label = "badstyle",
        fileName = "domains/badstyle.riddl",
        options = CommonOptions(showMissingWarnings = false)
      ) { case (_, messages) =>
        assert(!messages.exists(_.kind.isError))
        assert(!messages.exists(_.kind.isMissing))
      }
      validateFile(
        label = "badstyle",
        fileName = "domains/badstyle.riddl",
        options = CommonOptions()
      ) { case (_, messages) =>
        assert(!messages.exists(_.kind.isError))
        assert(messages.exists(_.kind.isMissing))
      }
    }
  }
}
