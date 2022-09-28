package com.reactific.riddl.language

import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.testkit.RunCommandSpecBase

/** Unit Tests For Includes */
class NamespaceTest extends RunCommandSpecBase {
  "FooBarSameDomain" should {
    "error w/ highest severity level 5" in {
      val args = Array[String](
        "--suppress-style-warnings",
        "validate",
        "examples/src/riddl/FooBarSameDomain/FooBar.riddl"
      )
      CommandPlugin.runMain(args) mustEqual 5
    }
  }

  "FooBarTwoDomains" should {
    "error w/ highest severity level 5" in {
      val args = Array[String](
        "--suppress-style-warnings",
        "validate",
        "examples/src/riddl/FooBarTwoDomains/FooBar.riddl"
      )
      CommandPlugin.runMain(args) mustEqual 5
    }
  }

  "FooBarSuccess" should {
    "succeed in compilation" in {
      val args = Array[String](
        "--suppress-style-warnings",
        "validate",
        "examples/src/riddl/FooBarSuccess/FooBar.riddl"
      )
      CommandPlugin.runMain(args) mustEqual 0
    }
  }
}
