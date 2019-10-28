package com.yoppworks.ossum.riddl.translator

import java.io.File

import com.yoppworks.ossum.riddl.language.AST._
import com.yoppworks.ossum.riddl.language.RiddlParserInput
import com.yoppworks.ossum.riddl.language.TopLevelParser
import com.yoppworks.ossum.riddl.language.Validation
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessage
import pureconfig._
import pureconfig.error.ConfigReaderFailure
import pureconfig.error.ConfigReaderFailures

trait Translator {
  trait Configuration extends Product

  trait State {
    def config: Configuration
    def generatedFiles: Seq[File]
    def addFile(file: File): State
  }

  def parseValidateTranslate(
    input: RiddlParserInput,
    errorLog: (=> String) => Unit,
    configFile: File
  ): Seq[File] = {
    TopLevelParser.parse(input) match {
      case Left(error) =>
        errorLog(error)
        Seq.empty[File]
      case Right(root) =>
        val errors: Seq[ValidationMessage] =
          Validation.validate[RootContainer](root)
        if (errors.nonEmpty) {
          errors.map(_.format(input.origin)).foreach(errorLog(_))
          Seq.empty[File]
        } else {
          translate(root, configFile)
        }
    }
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
