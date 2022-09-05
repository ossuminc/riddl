package com.reactific.riddl
import com.reactific.riddl.commands.{CommandOptions, CommandPlugin}
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.utils.Logger
import pureconfig.ConfigReader
import scopt.OParser

import java.nio.file.Path

object TestCommand {
  case class Options(
    command: String = "test",
    args: Map[String,String] = Map.empty[String,String],
    commonOptions: CommonOptions = CommonOptions(),
  ) extends CommandOptions {
    override def inputFile: Option[Path] = None
  }
}

/** A pluggable command for testing plugin commands! */
class TestCommand extends CommandPlugin[TestCommand.Options]("test") {
  import TestCommand.Options
  override def getOptions: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(pluginName)
      .action( (_,to) => to.copy(command = pluginName))
      .children(
        arg[Map[String,String]]("args").action( (m,to) =>
          to.copy(args = m))
          .validate { map =>
            if (map.keys.forall(_.nonEmpty)) { Right(()) }
            else { Left("All argument keys must be nonempty") }
          }
      ) -> Options()
  }

  override def run(
    options: Options,
    common: CommonOptions,
    log: Logger
  ): Either[Messages,Unit] = {
    options.args.foreach {
      case (name,value) =>
        println(s"$name: $value")
    }
    Right(())
  }

  override def getConfigReader: ConfigReader[Options] = ???
}
