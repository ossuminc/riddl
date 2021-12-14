package com.yoppworks.ossum.riddl.language

import java.io.File
import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.Riddl.Options
import pureconfig._
import pureconfig.error._

trait TranslatorConfiguration extends Options {
  def inputPath: Option[Path]
}

trait TranslatorState {
  def config: TranslatorConfiguration
  def generatedFiles: Seq[File]
  def addFile(file: File): TranslatorState
}

/** Unit Tests For Translator */
trait Translator[CONF <: TranslatorConfiguration] {

  def loadConfig(path: Path): ConfigReader.Result[CONF]

  def defaultConfig: CONF

  protected final def getConfig(
    logger: Riddl.Logger,
    path: Option[Path]
  ): Option[CONF] = {
    path match {
      case None => Some(defaultConfig)
      case Some(p) => loadConfig(p) match {
          case Left(failures: ConfigReaderFailures) =>
            failures.toList.foreach { crf: ConfigReaderFailure =>
              val location = crf.location match {
                case Some(cvl) => cvl.description
                case None      => "unknown location"
              }
              logger.error(s"In $location:")
              logger.error(crf.description)
            }
            None
          case Right(configuration) => Some(configuration)
        }
    }
  }

  def translate(
    root: RootContainer,
    outputRoot: Option[Path],
    logger: Riddl.Logger,
    config: CONF
  ): Seq[File]

  final def translate(
    root: RootContainer,
    outputRoot: Option[Path],
    logger: Riddl.Logger,
    config: Option[Path]
  ): Seq[File] = {
    val cfg = getConfig(logger, config)
    cfg.map(translate(root, outputRoot, logger, _)).getOrElse(Seq.empty[File])
  }

  final def parseValidateTranslateFile(
    path: Path,
    outputRoot: Option[Path],
    logger: Riddl.Logger,
    config: CONF
  ): Seq[File] = {
    Riddl.parseAndValidate(path, logger, config) match {
      case Some(root) => translate(root, outputRoot, logger, config)
      case None       => Seq.empty[File]
    }
  }

  final def parseValidateTranslate(
    input: RiddlParserInput,
    outputRoot: Option[Path],
    logger: Riddl.Logger,
    config: CONF
  ): Seq[File] = {

    Riddl.parseAndValidate(input, logger, config) match {
      case Some(root) => translate(root, outputRoot, logger, config)
      case None       => Seq.empty[File]
    }
  }

  final def run(
    inputFile: Path,
    outputRoot: Option[Path],
    logger: Riddl.Logger,
    configFile: Option[Path]
  ): Seq[File] = {
    getConfig(logger, configFile) match {
      case Some(config) => parseValidateTranslateFile(inputFile, outputRoot, logger, config)
      case None         => Seq.empty[File]
    }
  }
}
