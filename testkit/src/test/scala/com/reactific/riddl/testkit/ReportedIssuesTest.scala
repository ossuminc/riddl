package com.reactific.riddl.testkit

import com.reactific.riddl.language.{CommonOptions, Messages}
import com.reactific.riddl.passes.{PassesResult, Riddl}
import org.scalatest.Assertion

import java.nio.file.Path

class ReportedIssuesTest extends ValidatingTest {

  val dir = "testkit/src/test/input/issues"

  val options: CommonOptions = CommonOptions(
    showTimes = true,
    showWarnings = false,
    showMissingWarnings = false,
    showStyleWarnings = false,
    showUsageWarnings = false
  )

  def checkOne(fileName: String)(checkResult: Either[Messages.Messages, PassesResult] => Assertion): Assertion = {
    val file = Path.of(dir, fileName).toFile
    val either = Riddl.parseAndValidate(file, options)
    checkResult(either)
  }

  def doOne(fileName: String): Assertion = {
    parseAndValidateFile(
      Path.of(dir, fileName).toFile,
      CommonOptions(
        showTimes = true,
        showWarnings = false,
        showMissingWarnings = false,
        showStyleWarnings = false
      )
    )
    succeed
  }

  "Reported Issues" should {
    "375" in {
      checkOne("375.riddl") {
        case Left(messages) =>
          messages.length must be(10)
          val errors = messages.justErrors
          errors.length must be(5)
          val f = errors.map(_.format)
          f contains ("Path 'DooFoo' was not resolved,")
          f contains ("Path 'FooExamplexxx.garbage' was not resolved,")
          f contains ("Path 'FooExamplexxxx.garbage' was not resolved")
          f contains ("Path 'Examplexxxx.Foo' was not resolved,")
          f contains ("Setting a value requires assignment compatibility, but field:")
          val usage = messages.justUsage
          usage.length must be(5)
          val u = usage.map(_.format)
          u contains("Entity 'FooEntity' is unused")
          u contains("Entity 'OtherEntity' is unused:")
          u contains("Command 'DoFoo' is unused")
          u contains("Record 'OtherState' is unused:")
          u contains("Models without any streaming data will exhibit minimal effect:")
          info(messages.format)
          succeed
        case Right(result) =>
          val messages = result.messages
          val errors = messages.filter(_.kind.isError)
          if errors.isEmpty then
            info(messages.format)
            fail("Errors were expected")
          else
          errors mustBe empty
          fail("Expected errors")
      }
    }
  }
}
