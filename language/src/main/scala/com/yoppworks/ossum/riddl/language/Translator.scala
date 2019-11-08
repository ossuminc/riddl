package com.yoppworks.ossum.riddl.language

import java.io.File

import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessage
import pureconfig._
import pureconfig.error._

/** Unit Tests For Translator */
trait Translator {
  trait Configuration extends Product

  trait State {
    def config: Configuration
    def generatedFiles: Seq[File]
    def addFile(file: File): State
  }

  private def handleParserResult(
    result: Either[Seq[ParserError], RootContainer],
    origin: String,
    errorLog: (=> String) => Unit,
    configFile: File,
    showWarnings: Boolean = true
  ): Seq[File] = {
    result match {
      case Left(errors) =>
        errors.map(_.format).foreach(errorLog(_))
        errorLog(s"TOTAL: ${errors.length} syntax errors")
        Seq.empty[File]
      case Right(root) =>
        val errors: Seq[ValidationMessage] =
          Validation.validate[RootContainer](root)
        if (errors.nonEmpty) {
          val (warns, errs) = errors.partition(_.kind.isWarning)
          if (showWarnings) {
            warns.map(_.format(origin)).foreach(errorLog(_))
          }
          errors.map(_.format(origin)).foreach(errorLog(_))
          Seq.empty[File]
        } else {
          translate(root, configFile)
        }
    }
  }

  def parseValidateTranslateFile(
    file: File,
    errorLog: (=> String) => Unit,
    configFile: File
  ): Seq[File] = {
    handleParserResult(
      TopLevelParser.parse(file),
      file.getName,
      errorLog,
      configFile
    )
  }

  def parseValidateTranslate(
    input: RiddlParserInput,
    errorLog: (=> String) => Unit,
    configFile: File
  ): Seq[File] = {
    handleParserResult(
      TopLevelParser.parse(input),
      input.origin,
      errorLog,
      configFile
    )
  }

  def handleConfigLoad[C](result: ConfigReader.Result[C]): Option[C] = {
    result match {
      case Left(failures: ConfigReaderFailures) =>
        failures.toList.foreach { crf: ConfigReaderFailure =>
          val location = crf.location match {
            case Some(cvl) => cvl.description
            case None      => "unknown location"
          }
          System.err.println(s"In $location:")
          System.err.println(crf.description)
        }
        None
      case Right(configuration) =>
        Some(configuration)
    }
  }

  def translate(root: RootContainer, confFile: File): Seq[File]
}
