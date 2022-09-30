package com.reactific.riddl.language

import com.reactific.riddl.commands.ASimpleTestCommand
import com.reactific.riddl.testkit.RunCommandOnExamplesTest

/** Unit Tests For Includes */
class NamespaceTest
    extends RunCommandOnExamplesTest[
      ASimpleTestCommand.Options,
      ASimpleTestCommand
    ](commandName = "validate") {
  "FooBarSameDomain" should {
    "error w/ highest severity level 5" in {
      runTest("FooBarSameDomain") mustEqual 5
    }
  }

  "FooBarTwoDomains" should {
    "error w/ highest severity level 5" in {
      runTest("FooBarTwoDomains") mustEqual 5
    }
  }

  "FooBarSuccess" should {
    "succeed in compilation" in { runTest("FooBarSuccess") mustEqual 0 }
  }
}
