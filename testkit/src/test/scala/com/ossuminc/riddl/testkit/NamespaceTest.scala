/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.commands.ASimpleTestCommand
import org.scalatest.exceptions.TestFailedException

/** Compilation Tests For Includes Examples */
class NamespaceTest
    extends RunCommandOnExamplesTest[ASimpleTestCommand.Options, ASimpleTestCommand](commandName = "validate") {
  "FooBarSameDomain" should {
    "error w/ highest severity level 5" in {
      intercept[TestFailedException] {runTest("FooBarSameDomain")}
    }
  }

  "FooBarTwoDomains" should {
    "succeed in validation" in {
      runTest("FooBarTwoDomains")
    }
  }

  "FooBarSuccess" should {
    "succeed in compilation" in { runTest("FooBarSuccess") mustEqual () }
  }
}
