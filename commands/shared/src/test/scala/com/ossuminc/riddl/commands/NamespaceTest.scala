/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.{ec, pc}
import org.scalatest.Assertion

import java.nio.file.Path
import scala.annotation.unused

/** Compilation Tests For Includes Examples */
class NamespaceTest extends RunCommandOnExamplesTest() {

  override def onFailure(
    @unused commandName: String,
    @unused caseName: String,
    @unused messages: Messages,
    @unused tempDir: Path
  ): Assertion = {
    info(s"tempDir = ${tempDir.toAbsolutePath}")
    fail(messages.justErrors.format)
  }

  "FooBarSameDomain" should {
    "error with highest severity level 5" in {
      runTest("FooBarSameDomain", "validate")
    }
  }

  "FooBarTwoDomains" should {
    "succeed in validation" in {
      runTest("FooBarTwoDomains", "validate")
    }
  }

  "FooBarSuccess" should {
    "succeed in validation" in {
      runTest("FooBarSuccess", "validate") mustEqual ()
    }
  }
}
