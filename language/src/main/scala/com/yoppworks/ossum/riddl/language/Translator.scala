package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.Validation.ValidatingOptions
import com.yoppworks.ossum.riddl.language.parsing.RiddlParserInput

import java.io.File
import java.nio.file.Path

trait TranslatingOptions {
  def validatingOptions: ValidatingOptions

  def projectName: Option[String]

  def inputPath: Option[Path]

  def outputPath: Option[Path]

  def logger: Option[Logger]

  final def log: Logger = logger.getOrElse(SysLogger())
}

trait TranslatorState {
  def generatedFiles: Seq[File]

  def addFile(file: File): TranslatorState
}

/** Unit Tests For Translator */
trait Translator[OPT <: TranslatingOptions] {

  def defaultOptions: OPT


  protected def translateImpl(
    root: RootContainer,
    options: OPT,
  ): Seq[File]

  final def translate(
    root: RootContainer,
    options: OPT
  ): Seq[File] = {
    val showTimes = options.validatingOptions.parsingOptions.showTimes
    Riddl.timer(stage = "translate", showTimes) {
      translateImpl(root, options)
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
