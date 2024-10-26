package com.ossuminc.riddl

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{PassesResult, Riddl}
import com.ossuminc.riddl.passes.validate.JVMAbstractValidatingTest
import com.ossuminc.riddl.commands.Commands
import com.ossuminc.riddl.utils.{Await, CommonOptions, PathUtils, URL}
import com.ossuminc.riddl.utils.{pc, ec}
import org.scalatest.{Assertion, TestData}

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

class ReportedIssuesTest extends JVMAbstractValidatingTest {

  val dir = "riddlc/src/test/input/issues"

  val defaultOptions: CommonOptions = CommonOptions(
    showTimes = true,
    showIncludeTimes = true,
    showWarnings = false
  )

  def checkOneDir(configFile: String, command: String): Assertion = {
    val path = Path.of(dir ++ "/" + configFile).toAbsolutePath.toString
    val commandArgs = Seq("from", path, command)
    Commands.runMain(commandArgs.toArray) must be(0)
  }

  def doOne(fileName: String, options: CommonOptions = defaultOptions)(
    checkResult: Either[Messages.Messages, PassesResult] => Assertion
  ): Assertion = {
    pc.setOptions(options)
    val path = Path.of(dir).resolve(fileName)
    val url = PathUtils.urlFromPath(path)
    val future = RiddlParserInput.fromURL(url).map { rpi =>
      val either = Riddl.parseAndValidate(rpi)
      checkResult(either)
    }
    Await.result(future, 10.seconds)
  }

  def checkOne(fileName: String): Assertion = {
    doOne(fileName, defaultOptions) {
      case Left(messages) =>
        fail(messages.format)
      case Right(result) =>
        succeed
    }
  }

  "Reported Issues" should {
    "375" in { (td: TestData) =>
      doOne("375.riddl") {
        case Left(messages) =>
          // info(messages.format)
          val errors = messages.justErrors
          errors.length must be(3)
          val f = errors.map(_.format)
          f contains "Path 'FooExamplexxx.garbage' was not resolved,"
          f contains "Path 'FooExamplexxxx.garbage' was not resolved"
          f contains "Path 'Examplexxxx.Foo' was not resolved,"
          val usage = messages.justUsage
          usage.length must be(0)
          val u = usage.map(_.format)
          u contains "Entity 'FooEntity' is unused"
          u contains "Entity 'OtherEntity' is unused:"
          u contains "Command 'DoFoo' is unused"
          u contains "Record 'OtherState' is unused:"
          u contains "Models without any streaming data will exhibit minimal effect:"
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
    "435" in { (td: TestData) =>
      doOne("435.riddl") {
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
    "406" in { (td: TestData) =>
      checkOne("406.riddl")
    }
    "445" in { (td: TestData) =>
      checkOne("445.riddl")
    }
    "447" in { (td: TestData) =>
      checkOne("447.riddl")
    }
    "479" in { (td: TestData) =>
      doOne("479.riddl") {
        case Left(messages) =>
          val errors = messages.justErrors
          errors.size mustBe 1
          errors.head.message must include("Expected one of")
        case Right(result) =>
          fail("should not have parsed correctly")
      }
    }
    "480" in { (td: TestData) =>
      checkOne("480.riddl")
    }
    "480b" in { (td: TestData) =>
      checkOne("480b.riddl")
    }
    "486" in { (td: TestData) =>
      doOne("486.riddl") {
        case Left(messages) =>
          val errors = messages.justErrors
          errors.size mustBe 1
          errors.head.message must include("white space after a keyword")
        case Right(result) =>
          fail("Should not have parsed correctly")
      }
    }
    "495" in { (td: TestData) =>
      checkOne("495.riddl")
    }
    "584" in { (td: TestData) =>
      checkOneDir("584/Foo.conf", "validate")
    }
    "588" in { (td: TestData) =>
      val warning_text = "Vital definitions should have an author reference"
      doOne("588.riddl", defaultOptions.copy(showWarnings = true)) {
        case Left(messages: Messages) =>
          val errors = messages.justErrors
          if errors.nonEmpty then fail(errors.format)
          val warnings = messages.justWarnings
          warnings.size must be > 1
          warnings.find(_.message.contains(warning_text)) match {
            case Some(msg) => fail(s"Message with '$warning_text' found")
            case None      => succeed
          }
        case Right(result: PassesResult) =>
          val warnings: Messages = result.messages.justWarnings
          warnings.size must be > 1
          warnings.find(_.message.contains(warning_text)) match {
            case Some(msg) =>
              fail(s"Message with '$warning_text' found")
            case None =>
              succeed
          }
      }
    }
    "592" in { (td: TestData) =>
      doOne("592.riddl") {
        case Left(messages) =>
          val errors = messages.justErrors
          errors.find(_.message.contains("but a Portlet was expected")) match {
            case Some(msg) => succeed
            case None      => fail("a wrong-type error was expected")
          }
        case Right(result) =>
          fail("a wrong-type error was expected")
      }
    }
  }
}
