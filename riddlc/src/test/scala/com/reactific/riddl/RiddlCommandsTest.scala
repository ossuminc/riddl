package com.reactific.riddl

import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.utils.SysLogger
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RiddlCommandsTest extends AnyWordSpec with Matchers {

  "Riddlc Commands" should {
    "generate info" in {
      runSimpleCommand("info")
    }
    "provide help" in {
      runSimpleCommand("help")
    }
    "print version" in {
      runSimpleCommand("version")
    }
  }

  def runSimpleCommand(
    command: String,
    args: Array[String] = Array.empty[String]
  ): Assertion = {
    val log = SysLogger()
    CommandPlugin.runCommandWithArgs(command,args, log, CommonOptions()) match {
      case Right(_) =>
        succeed
      case Left(errors) =>
        fail(errors.format)
    }
  }

}
