package com.reactific.riddl.commands
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser

import java.io.File
import java.nio.file.Path

object InputFileCommandPlugin {
  case class Options(
    command: String = "unspecified",
    inputFile: Option[Path] = None
  ) extends CommandOptions
}

/** Unit Tests For InputFileCommandPlugin */
abstract class InputFileCommandPlugin(
 name: String
) extends CommandPlugin[InputFileCommandPlugin.Options](name) {
  import InputFileCommandPlugin.Options
  def getOptions(): (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(name).children(
      opt[File]('i', "input-file").action((f, opt) =>
      opt.copy(inputFile = Some(f.toPath))
    )) -> InputFileCommandPlugin.Options()
  }

  override def getConfigReader(): ConfigReader[Options] = {
    (cur: ConfigCursor) => {
      for {
        topCur <- cur.asObjectCursor
        topRes <- topCur.atKey(name)
        objCur <- topRes.asObjectCursor
        inFileRes <- objCur.atKey("input-file").map(_.asString)
        inFile <- inFileRes
      } yield { Options(inputFile = Some(Path.of(inFile))) }
    }
  }
}
