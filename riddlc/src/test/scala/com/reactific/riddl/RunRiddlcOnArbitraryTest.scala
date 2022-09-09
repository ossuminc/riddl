/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reactific.riddl

import com.reactific.riddl.testkit.RunCommandSpecBase
import java.nio.file.Files
import java.nio.file.Path

/** Run a "from" command on a specific .conf file */
class RunRiddlcOnArbitraryTest extends RunCommandSpecBase {

  val cwd = "/Users/reid/Code/Improving/improving-app-riddl"
  val config = "src/main/riddl/ImprovingApp.conf"

  "riddlc" should {
    s"run from $config" in {
      pending // FIXME: Never commit this as non-pending
      if (Files.isDirectory(Path.of(cwd))) {
        if (Files.isReadable(Path.of(cwd, config))) {
          val args = Seq(
            "--verbose", "--debug", "--show-times", "from", config, "hugo"
          )
          runWith(args)
        } else { fail(s"No configuration file at $config") }
      } else { fail(s"No directory to change to: $cwd") }
    }
  }
}
