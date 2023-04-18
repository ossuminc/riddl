/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl

import com.reactific.riddl.testkit.RunCommandSpecBase
import java.nio.file.Files
import java.nio.file.Path

/** Run a "from" command on a specific .conf file */
class RunRiddlcOnArbitraryTest extends RunCommandSpecBase {

  // NOTE: This test will succeed if the cwd or config don't exist to allow
  //  it to pass when run from GitHub workflow. Beware of false positives
  //  when using it to test locally.

  val cwd = "/Users/reid/Code/improving.app/riddl"
  val config = "src/main/riddl/ImprovingApp.conf"

  "riddlc" should {
    s"run --show-times from $config hugo" in {
      if Files.isDirectory(Path.of(cwd)) then {
        val fullPath = Path.of(cwd, config)
        if Files.isReadable(fullPath) then {
          val args = Seq("--show-times", "from", fullPath.toString, "hugo")
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
  }
}
