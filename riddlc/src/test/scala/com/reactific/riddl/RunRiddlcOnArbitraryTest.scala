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

  val cwd = "/Users/reid/Code/Improving/improving-app/riddl"
  val config = "src/main/riddl/KalixStudy.conf"

  "riddlc" should {
    s"run --show-times from $config hugo" in {
      if (Files.isDirectory(Path.of(cwd))) {
        val fullPath = Path.of(cwd, config)
        if (Files.isReadable(fullPath)) {
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
