package com.ossuminc.riddl.sbt.test.app

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.Assertion

import com.ossuminc.riddl.commands.Commands

/** Generate a test site */
class GenerateTestSiteSpec extends AnyWordSpec with Matchers:
  def runWith(commandArgs: Seq[String]): Assertion =
    Commands.runMain(commandArgs.toArray) must be(0)

  "GenerateTestSite" should {
    "validate RIDDL and generate Hugo" in {
      val command = Array(
        "--show-times",
        "--verbose",
        "from",
        "src/main/riddl/riddlc.conf",
        "validate"
      )
      runWith(command)
    }
  }
end GenerateTestSiteSpec
