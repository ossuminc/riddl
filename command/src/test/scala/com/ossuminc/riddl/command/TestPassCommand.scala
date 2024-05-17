package com.ossuminc.riddl.command

import com.ossuminc.riddl.language.{At, CommonOptions, Messages}
import com.ossuminc.riddl.passes.{Pass, PassOptions, PassesCreator, TestPass}
import com.ossuminc.riddl.utils.Logger
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.{OParser, OParserBuilder}

import java.io.File
import java.nio.file.Path

case class TestPassCommand() extends PassCommand[TestPassCommand.Options](TestPassCommand.name):
  override def getPasses(log: Logger, commonOptions: CommonOptions, options: TestPassCommand.Options): PassesCreator =
    Pass.standardPasses :+ TestPass.creator(options)

  override def overrideOptions(options: TestPassCommand.Options, newOutputDir: Path): TestPassCommand.Options =
    options.copy(outputDir = Some(newOutputDir))

  override def getOptions: (OParser[Unit, TestPassCommand.Options], TestPassCommand.Options) =
    TestPassCommand.optionsParser

  override def getConfigReader: ConfigReader[TestPassCommand.Options] = TestPassCommand.configReader

object TestPassCommand {
  val name = "test-pass-command"

  case class Options(inputFile: Option[Path], outputDir: Option[Path]) extends PassCommandOptions with PassOptions {
    override def check: Messages.Messages = {
      super.check ++ Seq(Messages.info("check called", At()))
    }
    def command: String = TestPassCommand.name
  }

  def optionsParser: (OParser[Unit, Options], Options) = {
    val builder: OParserBuilder[Options] = scopt.OParser.builder[Options]
    import builder.*

    cmd(TestPassCommand.name)
      .children(
        builder
          .arg[File]("input-file")
          .action { (file, opt) => opt.copy(inputFile = Some(file.toPath)) }
          .text("The input to be parsed"),
        arg[String]("output-directory")
          .action { (dir, opt) => opt.copy(outputDir = Some(Path.of(dir))) }
          .text("The path to the directory where the output will be placed")
      )
      .text("Just used for testing") -> Options(None, None)
  }

  def configReader: ConfigReader[Options] = { (cur: ConfigCursor) =>
    for
      topCur <- cur.asObjectCursor
      topRes <- topCur.atKey(name)
      objCur <- topRes.asObjectCursor
      inFileRes <- objCur.atKey("input-file").map(_.asString)
      inFile <- inFileRes
      targetRes <- objCur.atKey("output-directory").map(_.asString)
      target <- targetRes
    yield {
      Options(inputFile = Some(Path.of(inFile)), outputDir = Some(Path.of(target)))
    }
  }
}
