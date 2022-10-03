package com.reactific.riddl.language

import com.reactific.riddl.commands.ASimpleTestCommand
import com.reactific.riddl.testkit.RunCommandOnExamplesTest
import org.scalatest.exceptions.TestFailedException

/** Unit Tests For Includes */
class NamespaceTest
    extends RunCommandOnExamplesTest[
      ASimpleTestCommand.Options,
      ASimpleTestCommand
    ](commandName = "validate") {
  "FooBarSameDomain" should {
    "error w/ highest severity level 5" in {
      intercept[TestFailedException] { runTest("FooBarSameDomain") }
    }
  }

  "FooBarTwoDomains" should {
    "error w/ highest severity level 5" in {
      intercept[TestFailedException] { runTest("FooBarTwoDomains") }
    }
  }

  "FooBarSuccess" should {
    "succeed in compilation" in { runTest("FooBarSuccess") mustEqual () }
  }
}
