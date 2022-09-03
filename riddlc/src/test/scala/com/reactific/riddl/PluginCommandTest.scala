package com.reactific.riddl

/** Unit Tests For Running Riddlc Commands from Plugins */
import com.reactific.riddl.utils.PluginSpecBase
import java.nio.file.Path

class PluginCommandTest extends PluginSpecBase(
  svcClassPath = Path.of(
    "com/reactific/riddl/RiddlcCommandPlugin.class"),
  implClassPath = Path.of(
    "com/reactific/riddl/TestCommand.class"
  ),
  testClassesDir = Path.of("riddlc/target/scala-2.13/test-classes/"),
  jarFilename = "test-command.jar"
) {

  "RIDDLC" should {
    "run a command via a plugin" in {
      val args = Array("run", "--plugins-dir", tmpDir.toString, "test","foo")
      RIDDLC.runMain(args) mustBe 0
    }
  }
}
