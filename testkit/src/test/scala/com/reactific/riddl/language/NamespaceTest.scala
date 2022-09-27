package com.reactific.riddl.language

import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.testkit.RunCommandSpecBase
import org.scalatest.Assertion

/** Unit Tests For Includes */
class NamespaceTest extends RunCommandSpecBase {
  "FooBar" should {
    "be validated" in {
      val args = Array[String](
        "--quiet",
        "--suppress-missing-warnings",
        "--suppress-style-warnings",
        "validate",
        "examples/src/riddl/FooBar"
      )
      runCommand(args)
    }
  }

  def runCommand(
    args: Array[String] = Array.empty[String]
  ): Assertion = {
    val rc = CommandPlugin.runMain(args)
    rc mustBe 0
  }
}
