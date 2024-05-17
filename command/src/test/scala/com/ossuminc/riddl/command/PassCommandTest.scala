package com.ossuminc.riddl.command

import java.nio.file.Path
import com.ossuminc.riddl.language.Messages
import org.scalatest.exceptions.TestFailedException

class PassCommandTest extends CommandTestBase {

  override val confFile = s"$inputDir/test-pass-command.conf"

  "PassCommandOptions" must {
    "check content" in {
      val pco = TestPassCommand.Options(
        Some(Path.of("command/src/test/input/simple.riddl")),
        None
      )
      val messages = pco.check
      messages.size must be(2)
      val message_text = messages.format
      message_text must include("An output directory was not provided")
      message_text must include("check called")
    }
  }

  "PassCommand" must {
    "check filled options" in {
      pending // fails to find command when run in parallel :(
      CommandPlugin.loadCommandNamed(TestPassCommand.name) match {
        case Left(messages) => fail(messages.format)
        case Right(plugin: CommandPlugin[TestPassCommand.Options] @unchecked) =>
          val expected = TestPassCommand.Options(
            Some(Path.of("test.riddl")),
            Some(Path.of("target/test-output"))
          )
          super.check[TestPassCommand.Options](plugin, expected) { (options: TestPassCommand.Options) =>
            val messages = options.check
            messages.size must be(1)
            messages.head.message must include("check called")
          }
      }
    }
    "check empty input options" in {
      pending // fails to find command when run in parallel :(
      CommandPlugin.loadCommandNamed(TestPassCommand.name) match {
        case Left(messages) => fail(messages.format)
        case Right(plugin: CommandPlugin[TestPassCommand.Options] @unchecked) =>
          val expected = TestPassCommand.Options(
            Some(Path.of("test.riddl")),
            Some(Path.of("none"))
          )
          val path = Path.of(s"$inputDir/test-pass-command2.conf")
          intercept[TestFailedException] {
            super.check[TestPassCommand.Options](plugin, expected, path) { (options: TestPassCommand.Options) =>
              val messages = options.check
              messages.size must be(1)
              messages(0).message must include("check called")
            }
          }
      }
    }
  }

}
