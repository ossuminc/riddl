package com.reactific.riddl

import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.utils.SysLogger
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scopt.OParser

class RiddlCommandsTest extends AnyWordSpec with Matchers {

  def runSimpleCommand(command: String): Assertion = {
    CommandPlugin.loadCommandNamed(command) match {
      case Right(cmd) =>
        val args = Array(command)
        val log = SysLogger()
        val (parser, default) = cmd.getOptions(log)
        val (result, _) = OParser.runParser(parser, args, default)
        result match {
          case Some(options) =>
            cmd.run(options, CommonOptions(), log) match {
              case Right(_) =>
                succeed
              case Left(errors) =>
                fail(errors.format)
            }
          case None =>
            fail("Parsing options failed")
        }
      case Left(errors) =>
        fail(errors.format)
    }

  }
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
}
