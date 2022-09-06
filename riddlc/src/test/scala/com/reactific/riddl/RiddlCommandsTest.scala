package com.reactific.riddl

import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.utils.SysLogger
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RiddlCommandsTest extends AnyWordSpec with Matchers {

  val inputFile = "testkit/src/test/input/rbbq.riddl"
  val hugoConfig = "testkit/src/test/input/hugo.conf"
  val validateConfig = "testkit/src/test/input/validate.conf"
  val outputDir: String => String =
    (name: String) => s"riddlc/target/test/$name"

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
    "handle parse" in {
      val args = Array("parse", inputFile)
      RIDDLC.runMain(args) mustBe 0
    }
    "handle validate" in {
      val args = Array(
        "--verbose", "--suppress-missing-warnings", "--suppress-style-warnings",
        "validate", inputFile
        )
      RIDDLC.runMain(args) mustBe 0
    }
    "handle dump" in {
      val args = Array(
        "--verbose", "--suppress-missing-warnings", "--suppress-style-warnings",
        "dump", inputFile
      )
      RIDDLC.runMain(args) mustBe 0

    }
    "handle hugo" in {
      val args = Array(
        "--verbose", "--suppress-missing-warnings", "--suppress-style-warnings",
        "hugo", inputFile, "-o", outputDir("hugo")
      )
      RIDDLC.runMain(args) mustBe 0
    }
    "handle hugo from config" in {
      val args = Array(
        "--verbose", "--suppress-missing-warnings", "--suppress-style-warnings",
        "from", hugoConfig)
      RIDDLC.runMain(args) mustBe 0
      // runHugo(path)
      // val root = Path.of(output).resolve(path)
      // val img = root.resolve("static/images/RBBQ.png")
      // Files.exists(img) mustBe true
    }
    "repeat validation of the ReactiveBBQ example" in {
      val args = Array(
        "--verbose", "--suppress-missing-warnings", "--suppress-style-warnings",
        "repeat", validateConfig, "validate", "1s", "2")
      RIDDLC.runMain(args) mustBe 0
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
