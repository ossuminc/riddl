package com.reactific.riddl.language

import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.parsing.RiddlParserInput

import java.nio.file.Path

trait TranslatingOptions {
  def inputFile: Option[Path]
  def outputDir: Option[Path]
  def projectName: Option[String]
}

trait TranslatorState {
  def generatedFiles: Seq[Path]

  def addFile(file: Path): TranslatorState
}

/** Unit Tests For Translator */
trait Translator[OPT <: TranslatingOptions] {

  protected def translateImpl(
    root: RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT,
  ): Seq[Path]

  final def translate(
    root: RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Seq[Path] = {
    val showTimes = commonOptions.showTimes
    Riddl.timer(stage = "translate", showTimes) {
      translateImpl(root, log, commonOptions, options)
    }
  }

  final def parseValidateTranslate(
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Seq[Path] = {
    require(options.inputFile.nonEmpty, "Input path option must not be empty")
    Riddl.parseAndValidate(options.inputFile.get, log, commonOptions) match {
      case Some(root) => translate(root, log, commonOptions, options)
      case None       => Seq.empty[Path]
    }
  }

  final def parseValidateTranslate(
    input: RiddlParserInput,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Seq[Path] = {
    Riddl.parseAndValidate(input, log, commonOptions) match {
      case Some(root) => translate(root, log, commonOptions, options)
      case None       => Seq.empty[Path]
    }
  }
}
