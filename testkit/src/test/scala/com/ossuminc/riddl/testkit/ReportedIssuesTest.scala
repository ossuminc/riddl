package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.language.{CommonOptions, Messages}
import com.ossuminc.riddl.passes.{PassesResult, Riddl}
import org.scalatest.Assertion

import java.nio.file.Path

class ReportedIssuesTest extends ValidatingTest {

  val dir = "testkit/src/test/input/issues"

  val options: CommonOptions = CommonOptions(
    showTimes = true,
    showWarnings = false
  )

  def checkOne(fileName: String): Assertion = {
    checkOne(fileName){
      case Left(messages) =>
        fail(messages.format)
      case Right(result) =>
        succeed
    }
  }

  def checkOne(fileName: String)(checkResult: Either[Messages.Messages, PassesResult] => Assertion): Assertion = {
    val file = Path.of(dir, fileName).toFile
    val either = Riddl.parseAndValidate(file, options)
    checkResult(either)
  }

  def doOne(fileName: String): Assertion = {
    parseAndValidateFile(
      Path.of(dir, fileName).toFile,
      options
    )
    succeed
  }

  "Reported Issues" should {
    "375" in {
      checkOne("375.riddl") {
        case Left(messages) =>
          // info(messages.format)
          val errors = messages.justErrors
          errors.length must be(3)
          val f = errors.map(_.format)
          f contains ("Path 'FooExamplexxx.garbage' was not resolved,")
          f contains ("Path 'FooExamplexxxx.garbage' was not resolved")
          f contains ("Path 'Examplexxxx.Foo' was not resolved,")
          val usage = messages.justUsage
          usage.length must be(0)
          val u = usage.map(_.format)
          u contains ("Entity 'FooEntity' is unused")
          u contains ("Entity 'OtherEntity' is unused:")
          u contains ("Command 'DoFoo' is unused")
          u contains ("Record 'OtherState' is unused:")
          u contains ("Models without any streaming data will exhibit minimal effect:")
          // info(messages.format)
          succeed
        case Right(result) =>
          val messages = result.messages
          val errors = messages.justErrors
          if errors.isEmpty then
            // info(messages.format)
            fail("Expected 3 errors")
          else
            errors mustBe empty
            fail("Expected 3 errors")
      }
    }
    "435" in {
      checkOne("435.riddl") {
        case Left(messages) =>
          // info(messages.format)
          messages.size must be(1)
          val message = messages.head.format
          message must include("Expected")

        case Right(result) =>
          // info(result.messages.format)
          fail("Should have produced a syntax error on 'contest'")
      }
    }
    "406" in {
      checkOne("406.riddl")
    }
    "445" in {
      checkOne("445.riddl")
    }
    "447" in {
      checkOne("447.riddl")
    }
    "479" in {
      checkOne("479.riddl") {
        case Left(messages) =>
          val errors = messages.justErrors
          errors.size mustBe 1
          errors.head.message must include("Expected one of")
        case Right(result) => 
          fail("should not have parsed correctly")
      }
    }
    "480" in {
      checkOne("480.riddl")
    }
    "486" in {
      checkOne("486.riddl") {
        case Left(messages) =>
          val errors = messages.justErrors
          errors.size mustBe 1
          errors.head.message must include("whitespace after keyword")
        case Right(result) =>
          fail("Should not have parsed correctly")
      }
    }
  }
}
