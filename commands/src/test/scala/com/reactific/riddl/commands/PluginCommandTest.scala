package com.reactific.riddl.commands

/** Unit Tests For Running Riddlc Commands from Plugins */
import com.reactific.riddl.utils.{Plugin, PluginSpecBase}
import pureconfig.ConfigSource
import scopt.OParser

import java.nio.file.Path

class PluginCommandTest extends PluginSpecBase(
  svcClassPath = Path.of(
    "com/reactific/riddl/commands/CommandPlugin.class"),
  implClassPath = Path.of(
    "com/reactific/riddl/commands/ASimpleTestCommand.class"
  ),
  moduleName = "commands",
  jarFilename = "test-command.jar"
)
// abstract class PluginSpecBase(
  //  svcClassPath: Path = Path.of(
  //    "com/reactific/riddl/utils/PluginInterface.class"),
  //  implClassPath: Path = Path.of(
  //    "com/reactific/riddl/utils/TestPlugin.class"),
  //  testClassesDir: Path = Path.of(
  //    "utils/target/scala-2.13/test-classes/"),
  //  jarFilename: String = "test-plugin.jar"
  //) extends AnyWordSpec with Matchers with BeforeAndAfterAll {
 {

  "PluginCommandTest " should {
    "get options from command line" in {
      val plugins = Plugin.loadPluginsFrom[CommandPlugin[CommandOptions]](tmpDir)
      plugins must not(be(empty))
      val p = plugins.head
      p.getClass must be(classOf[ASimpleTestCommand])
      val plugin = p.asInstanceOf[ASimpleTestCommand]
      val args: Seq[String] = Seq("test", "Success!")
      val (parser, default) = plugin.getOptions
      OParser.parse(parser, args, default) match {
        case Some(to) =>
          to.arg1 must be("Success!")
        case None =>
          fail("No options returned from OParser.parse")
      }
    }
    "get options from config file" in {
      val plugins = Plugin
        .loadPluginsFrom[CommandPlugin[CommandOptions]](tmpDir)
      plugins must not(be(empty))
      val p = plugins.head
      p.getClass must be(classOf[ASimpleTestCommand])
      val plugin = p.asInstanceOf[ASimpleTestCommand]
      val reader = plugin.getConfigReader
        val path: Path = Path.of("commands/src/test/input/test.conf")
        ConfigSource.file(path.toFile).load[ASimpleTestCommand.Options](reader) match {
          case Right(loadedOptions) =>
            loadedOptions.arg1 mustBe "Success!"
          case Left(failures) => fail(failures.prettyPrint())
        }
    }
  }
}
