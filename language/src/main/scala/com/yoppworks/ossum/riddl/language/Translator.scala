package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.Validation.ValidatingOptions
import com.yoppworks.ossum.riddl.language.parsing.RiddlParserInput
import pureconfig.*
import pureconfig.error.*

import java.io.File
import java.nio.file.Path

trait TranslatingOptions {
  def validatingOptions: ValidatingOptions

  def projectName: Option[String]

  def inputPath: Option[Path]

  def outputPath: Option[Path]

  def configPath: Option[Path]

  def logger: Option[Logger]

  final def log: Logger = logger.getOrElse(SysLogger())
}

trait TranslatorConfiguration

trait TranslatorState {
  def config: TranslatorConfiguration

  def generatedFiles: Seq[File]

  def addFile(file: File): TranslatorState
}

/** Unit Tests For Translator */
trait Translator[OPT <: TranslatingOptions, CONF <: TranslatorConfiguration] {

  def loadConfig(path: Path): ConfigReader.Result[CONF]

  def defaultOptions: OPT

  def defaultConfig: CONF

  protected final def getConfig(
    logger: Logger,
    path: Option[Path]
  ): Option[CONF] = {
    path match {
      case None => Option(defaultConfig)
      case Some(p) =>
        if (p.toFile.exists()) {
          loadConfig(p) match {
            case Left(failures: ConfigReaderFailures) =>
              failures.toList.foreach { crf: ConfigReaderFailure =>
                val location = crf.origin match {
                  case Some(origin) => origin.description
                  case None         => "unknown location"
                }
                logger.error(s"In $location:\n${crf.description}")
              }
              None
            case Right(configuration) => Option(configuration)
          }
        } else {
          logger.error(s"File $p does not exist")
          None
        }
    }
  }

  protected def translate(
    root: RootContainer,
    options: OPT,
    config: CONF
  ): Seq[File]

  final def translate(
    root: RootContainer,
    options: OPT
  ): Seq[File] = {
    val showTimes = options.validatingOptions.parsingOptions.showTimes
    val cfg = Riddl
      .timer(stage = "load configuration", showTimes) { getConfig(options.log, options.configPath) }
    Riddl.timer(stage = "translate", showTimes) {
      cfg.fold(Seq.empty[File])(translate(root, options, _))
    }
  }

  final def parseValidateTranslate(
    options: OPT
  ): Seq[File] = {
    Riddl.parseAndValidate(options.inputPath.get.toFile, options.validatingOptions) match {
      case Some(root) => translate(root, options)
      case None       => Seq.empty[File]
    }
  }

  final def parseValidateTranslate(
    input: RiddlParserInput,
    options: OPT
  ): Seq[File] = {
    Riddl.parseAndValidate(input, options.validatingOptions) match {
      case Some(root) => translate(root, options)
      case None       => Seq.empty[File]
    }
  }

  final def parseValidateTranslateFile(
    path: Path,
    options: OPT
  ): Seq[File] = { parseValidateTranslate(RiddlParserInput(path), options) }
}
