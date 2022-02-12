package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.parsing.RiddlParserInput

import java.io.File
import java.nio.file.Path

trait TranslatingOptions {
  def inputFile: Option[Path]
  def outputDir: Option[Path]
  def projectName: Option[String]
}

trait TranslatorState {
  def generatedFiles: Seq[File]

  def addFile(file: File): TranslatorState
}

/** Unit Tests For Translator */
trait Translator[OPT <: TranslatingOptions] {

  protected def translateImpl(
    root: RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT,
  ): Seq[File]

  final def translate(
    root: RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Seq[File] = {
    val showTimes = commonOptions.showTimes
    Riddl.timer(stage = "translate", showTimes) {
      translateImpl(root, log, commonOptions, options)
    }
  }

  final def parseValidateTranslate(
    log: Logger,
    commonOptions: CommonOptions,
    validatingOptions: ValidatingOptions,
    options: OPT
  ): Seq[File] = {
    require(options.inputFile.nonEmpty, "Input path option must not be empty")
    Riddl.parseAndValidate(options.inputFile.get, log, commonOptions, validatingOptions) match {
      case Some(root) => translate(root, log, commonOptions, options)
      case None       => Seq.empty[File]
    }
  }

  final def parseValidateTranslate(
    input: RiddlParserInput,
    log: Logger,
    commonOptions: CommonOptions,
    validatingOptions: ValidatingOptions,
    options: OPT
  ): Seq[File] = {
    Riddl.parseAndValidate(input, log, commonOptions, validatingOptions) match {
      case Some(root) => translate(root, log, commonOptions, options)
      case None       => Seq.empty[File]
    }
  }
}
