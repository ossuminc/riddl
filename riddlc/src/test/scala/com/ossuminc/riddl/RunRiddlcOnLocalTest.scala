/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.testkit.RunCommandSpecBase
import org.scalatest.Assertion

import java.nio.file.Files
import java.nio.file.Path

/** Run a "from" command on a specific .conf file from a specific set of local paths. These tests are designed to be
  * "pending" if the given directory doesn't exist. This allows you to develop local test cases for testing RIDDL
  */
class RunRiddlcOnLocalTest extends RunCommandSpecBase {

  // NOTE: This test will succeed if the cwd or config don't exist to allow
  //  it to pass when run from GitHub workflow. Beware of false positives
  //  when using it to test locally.

  def runOnLocalProject(cwd: String, config: String, cmd: String): Assertion = {
    if Files.isDirectory(Path.of(cwd)) then {
      val fullPath = Path.of(cwd, config)
      info(s"config path is: ${fullPath.toAbsolutePath.toString}")
      if Files.isReadable(fullPath) then {
        val args = Seq("from", fullPath.toString, cmd)
        runWith(args)
      } else {
        info(s"Skipping unreadable $fullPath")
        succeed
      }
    } else {
      info(s"Skipping non-directory: $cwd")
      succeed
    }
  }

  "riddlc" should {
    "validate FooBarTwoDomains" in {
      val cwd = "/Users/reid/Code/ossuminc/riddl-examples"
      val config = "src/riddl/FooBarSuccess/FooBar.conf"
      runOnLocalProject(cwd, config, "validate")
    }
    "run hugo on OffTheTop" in {
      pending
      val cwd = "/Users/reid/Code/Improving/OffTheTop"
      val config = "design/src/main/riddl/OffTheTop.conf"
      runOnLocalProject(cwd, config, "validate")
    }
    "validate kalix-improving-template" in {
      val cwd = "/Users/reid/Code/Improving/kalix-improving-template"
      val config = "design/src/main/riddl/example.conf"
      runOnLocalProject(cwd, config, "validate")
    }
    "validate Improving.app" in {
      val cwd = "/Users/reid/Code/improving.app/riddl"
      val config = "src/main/riddl/ImprovingApp.conf"
      runOnLocalProject(cwd, config, "validate")
    }
    "validate riddl-examples" in {
      val cwd = "/Users/reid/Code/Ossum/riddl-examples"
      val config = "src/riddl/dokn/dokn.conf"
      runOnLocalProject(cwd, config, "validate")
    }
  }
}
