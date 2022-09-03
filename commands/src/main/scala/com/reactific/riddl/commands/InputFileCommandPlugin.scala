package com.reactific.riddl.commands
import com.reactific.riddl.language.CommonOptions
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser

import java.io.File
import java.nio.file.Path

object InputFileCommandPlugin {
  case class Options(
    inputFile: Option[Path] = None,
    commonOptions: CommonOptions = CommonOptions()
  ) extends CommandOptions
}

/** Unit Tests For InputFileCommandPlugin */
abstract class InputFileCommandPlugin(
 name: String
) extends CommandPlugin[InputFileCommandPlugin.Options](name) {

  def getOptions: (OParser[Unit, InputFileCommandPlugin.Options], InputFileCommandPlugin.Options) = {
    val builder = OParser.builder[InputFileCommandPlugin.Options]
    import builder.*
    cmd("parse").children(
      arg[File]("input-file").action((f, opt) =>
      opt.copy(inputFile = Some(f.toPath))
    )) -> InputFileCommandPlugin.Options()
  }

  override def getConfigReader: ConfigReader[InputFileCommandPlugin.Options] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        inFileRes <- objCur.atKey("input-file").map(_.asString)
        inFile <- inFileRes
      } yield { InputFileCommandPlugin.Options(inputFile = Some(Path.of(inFile))) }
    }
  }
}
