/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.testkit

import com.reactific.riddl.commands.ASimpleTestCommand
import org.scalatest.exceptions.TestFailedException

/** Compilation Tests For Includes Examples */
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
