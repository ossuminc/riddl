/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl

import com.reactific.riddl.testkit.RunCommandSpecBase
import org.scalatest.{Assertion, Sequential}

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
        val args = Seq("--show-times", "from", fullPath.toString, "validate")
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
      val cwd = "/Users/reid/Code/reactific/reactific-examples"
      val config = "src/riddl/FooBarSuccess/FooBar.conf"
      validateLocalProject(cwd, config)
    }
    "validate OffTheTop" in {
      val cwd = "/Users/reid/Code/Improving/OffTheTop"
      val config = "design/src/main/riddl/OffTheTop.conf"
      validateLocalProject(cwd, config)
    }
    // FIXME: Fix Improving.app syntax and renable
    // "validate Improving.app" in {
    //   val cwd = "/Users/reid/Code/improving.app/riddl"
    //   val config = "src/main/riddl/ImprovingApp.conf"
    //   validateLocalProject(cwd, config)
    //   pending
    // }
  }
}
