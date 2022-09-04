package com.reactific.riddl.commands

/** Unit Tests For Running Riddlc Commands from Plugins */
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.utils.{Plugin, PluginSpecBase, SysLogger}
import pureconfig.ConfigSource
import scopt.OParser

import java.nio.file.Path

class PluginCommandTest extends PluginSpecBase(
  svcClassPath = Path.of(
    "com/reactific/riddl/commands/CommandPlugin.class"),
  implClassPath = Path.of(
    "com/reactific/riddl/commands/TestCommand$Options.class"
  ),
  testClassesDir = Path.of("commands/target/scala-2.13/test-classes/"),
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

  "TestCommand" should {
    "get options from command line" in {
      val plugins = Plugin.loadPluginsFrom[CommandPlugin[CommandOptions]](tmpDir)
      plugins must not(be(empty))
      val p = plugins.head
      p.getClass must be(classOf[TestCommand])
      val logger = SysLogger()
      val plugin = p.asInstanceOf[TestCommand]
      val args: Seq[String] = Seq("test", "Success!")
      val (parser, default) = plugin.getOptions(logger)
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
      p.getClass must be(classOf[TestCommand])
      val plugin = p.asInstanceOf[TestCommand]
      val logger = SysLogger()
      val reader = plugin.getConfigReader(logger)
        val path: Path = Path.of("commands/src/test/input/test.conf")
        ConfigSource.file(path.toFile).load[TestCommand.Options](reader) match {
          case Right(loadedOptions) =>
            loadedOptions.arg1 mustBe "Success!"
          case Left(failures) => fail(failures.prettyPrint())
        }
    }
  }
}
