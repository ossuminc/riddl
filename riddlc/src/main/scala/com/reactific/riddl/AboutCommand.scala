package com.reactific.riddl

import com.reactific.riddl.commands.CommandOptions
import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.commands.CommonOptionsHelper
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.utils.Logger
import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.nio.file.Path

/** Unit Tests For FromCommand */
object AboutCommand {
  case class Options(
    command: String = "about",
    inputFile: Option[Path] = None,
    targetCommand: Option[String] = None)
      extends CommandOptions
}

class AboutCommand extends CommandPlugin[AboutCommand.Options]("about") {
  import AboutCommand.Options
  override def getOptions: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(pluginName).action((_, c) => c.copy(command = pluginName))
      .text("Print out information about RIDDL") -> AboutCommand.Options()
  }

  override def getConfigReader: ConfigReader[AboutCommand.Options] = {
    (cur: ConfigCursor) =>
      for {
        topCur <- cur.asObjectCursor
        topRes <- topCur.atKey(pluginName)
        cmd <- topRes.asString
      } yield { Options(cmd, inputFile = None, targetCommand = None) }
  }

  override def run(
    options: AboutCommand.Options,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, Unit] = {
    if (commonOptions.verbose || !commonOptions.quiet) {
      val about: String = {
        CommonOptionsHelper.blurb ++ System.lineSeparator() ++
          "Extensive Documentation here: https://riddl.tech"
      }
      println(about)
    }
    Right(())
  }
}
