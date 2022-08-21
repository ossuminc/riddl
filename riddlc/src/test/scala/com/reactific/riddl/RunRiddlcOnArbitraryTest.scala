package com.reactific.riddl

/** Unit Tests To Run Riddlc On Examples */

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}
// import java.util.concurrent.TimeUnit

class RunRiddlcOnArbitraryTest extends AnyWordSpec with Matchers {

  val cwd = "/Users/reid/Code/Improving/improving-app-riddl"
  val config = "src/main/riddl/ImprovingApp.conf"
  // val staged = "/Users/reid/reactificriddlc/target/universal/stage/bin/riddlc"

  "riddlc" should {
    "run from config" in {
      // pendingUntilFixed {
        // if (Files.isExecutable(Path.of(staged))) {
          if (Files.isDirectory(Path.of(cwd))) {
            if (Files.isReadable(Path.of(cwd, config))) {
              /* val wd = Path.of(cwd).toFile
              val prog = Path.of(System.getProperty("user.dir"), staged).toFile
              val cmd = Array(prog.toString, "from", config)
              val process = Runtime.getRuntime.exec(cmd, null, wd)
              process.waitFor(30, TimeUnit.SECONDS)
              process.exitValue() mustBe 0 */
              val args = Array("from", config)
              RIDDLC.runMain(args) == 0
            } else {
              fail(s"No configuration file at $config")
            }
          } else {
            fail(s"No directory to change to: $cwd")
          }
       /* } else {
          fail("riddlc has not been staged, run: sbt stage")
        }*/
      // }
    }
  }
}
