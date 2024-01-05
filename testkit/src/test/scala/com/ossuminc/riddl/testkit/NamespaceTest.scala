/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.commands.ASimpleTestCommand
import com.ossuminc.riddl.language.Messages.*

import java.nio.file.Path
import scala.annotation.unused
import org.scalatest.Assertion
import org.scalatest.exceptions.TestFailedException

/** Compilation Tests For Includes Examples */
class NamespaceTest
    extends RunCommandOnExamplesTest[ASimpleTestCommand.Options, ASimpleTestCommand](commandName = "validate") {

  override def onFailure(
    @unused commandName: String,
    @unused caseName: String,
    @unused configFile: Path,
    @unused messages: Messages,
    @unused tempDir: Path
  ): Assertion = {
    info(messages.format)
    info(s"tempDir = ${tempDir.toAbsolutePath}")
    fail(messages.format)
  }

  "FooBarSameDomain" should {
    "error w/ highest severity level 5" in {
      val exception = intercept[TestFailedException](runTest("FooBarSameDomain"))
      exception mustBe a[TestFailedException]
      exception.getMessage must include("ambiguous")
    }
  }

  "FooBarTwoDomains" should {
    "succeed in validation" in {
      runTest("FooBarTwoDomains")
    }
  }

  "FooBarSuccess" should {
    "succeed in validation" in { runTest("FooBarSuccess") mustEqual () }
  }
}
