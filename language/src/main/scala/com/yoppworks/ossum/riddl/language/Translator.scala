package com.yoppworks.ossum.riddl.language

import java.io.File
import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.Riddl.Options
import pureconfig._
import pureconfig.error._

/** Unit Tests For Translator */
trait Translator {

  trait Configuration extends Options {
    def inputPath: Option[Path]
  }

  type CONF <: Configuration

  trait State {
    def config: Configuration
    def generatedFiles: Seq[File]
    def addFile(file: File): State
  }

  def loadConfig(path: Path): ConfigReader.Result[CONF]

  def defaultConfig: CONF

  protected final def getConfig(
    logger: Riddl.Logger,
    path: Option[Path]
  ): Option[CONF] = {
    path match {
      case None => Some(defaultConfig)
      case Some(p) =>
        loadConfig(p) match {
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
          case Right(configuration) =>
            Some(configuration)
        }
    }
  }

  def translate(
    root: RootContainer,
    logger: Riddl.Logger,
    config: CONF
  ): Seq[File]

  final def translate(
    root: RootContainer,
    logger: Riddl.Logger,
    config: Option[Path]
  ): Seq[File] = {
    val cfg = getConfig(logger, config)
    cfg.map(translate(root, logger, _)).getOrElse(Seq.empty[File])
  }

  final def parseValidateTranslateFile(
    path: Path,
    logger: Riddl.Logger,
    config: CONF
  ): Seq[File] = {
    Riddl.parseAndValidate(path, logger, config) match {
      case Some(root) =>
        translate(root, logger, config)
      case None =>
        Seq.empty[File]
    }
  }

  final def parseValidateTranslate(
    input: RiddlParserInput,
    logger: Riddl.Logger,
    config: CONF
  ): Seq[File] = {

    Riddl.parseAndValidate(input, logger, config) match {
      case Some(root) =>
        translate(root, logger, config)
      case None =>
        Seq.empty[File]
    }
  }

  final def run(
    inputFile: Path,
    logger: Riddl.Logger,
    configFile: Option[Path]
  ): Seq[File] = {
    getConfig(logger, configFile) match {
      case Some(config) =>
        parseValidateTranslateFile(inputFile, logger, config)
      case None =>
        Seq.empty[File]
    }
  }
}
