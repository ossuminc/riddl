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

/** Run a "from" command on a specific .conf file */
class RunRiddlcOnArbitraryTest extends RunCommandSpecBase {

  // NOTE: This test will succeed if the cwd or config don't exist to allow
  //  it to pass when run from GitHub workflow. Beware of false positives
  //  when using it to test locally.

  def validateLocalProject(cwd: String, config: String): Assertion = {
    if Files.isDirectory(Path.of(cwd)) then {
      val fullPath = Path.of(cwd, config)
      info(s"config path is: ${fullPath.toAbsolutePath.toString}")
      if Files.isReadable(fullPath) then {
        val args = Seq("--show-times", "--verbose", "from", fullPath.toString, "validate")
        runWith(args)
      } else {
        info(s"Skipping unreadable $fullPath")
        succeed
      }
    } else {
      info(s"Root path is not a directory: $cwd")
      succeed
    }
  }

  "riddlc" should {
    "validate FooBarTwoDomains" in {
      val cwd = "/Users/reid/Code/ossuminc/riddl-examples"
      val config = "src/riddl/FooBarSuccess/FooBar.conf"
      validateLocalProject(cwd, config)
    }
    "validate OffTheTop" in {
      pending
      val cwd = "/Users/reid/Code/Improving/OffTheTop"
      val config = "design/src/main/riddl/OffTheTop.conf"
      validateLocalProject(cwd, config)
    }
    "validate kalix-improving-template" in {
      val cwd = "/Users/reid/Code/Improving/kalix-improving-template"
      val config = "design/src/main/riddl/ksoapp.conf"
      validateLocalProject(cwd, config)
    }
    "validate Improving.app" in {
      pending
      val cwd = "/Users/reid/Code/improving.app/riddl"
      val config = "src/main/riddl/ImprovingApp.conf"
      validateLocalProject(cwd, config)
    }
  }
}
