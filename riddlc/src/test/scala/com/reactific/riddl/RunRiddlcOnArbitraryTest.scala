package com.reactific.riddl

/** Unit Tests To Run Riddlc On Examples */

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

class RunRiddlcOnArbitraryTest extends AnyWordSpec with Matchers {

  val cwd = "/Users/reid/Code/Improving/improving-app-riddl"
  val config = "src/main/riddl/ImprovingApp.conf"

  "riddlc" should {
    s"run from $config" in {
      pending // FIXME: Never commit this as non-pending
      if (Files.isDirectory(Path.of(cwd))) {
        if (Files.isReadable(Path.of(cwd, config))) {
          val args = Array("--verbose", "--debug", "--show-times", "from", config)
          RIDDLC.runMain(args) == 0
        } else {
          fail(s"No configuration file at $config")
        }
      } else {
        fail(s"No directory to change to: $cwd")
      }
    }
  }
}
