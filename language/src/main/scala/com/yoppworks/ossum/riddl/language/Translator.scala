package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.parsing.RiddlParserInput

import java.io.File
import java.nio.file.Path

trait TranslatingOptions {
  def projectName: Option[String]
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
    inputPath: Path,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT,
  ): Seq[File]

  final def translate(
    root: RootContainer,
    inputPath: Path,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Seq[File] = {
    val showTimes = commonOptions.showTimes
    Riddl.timer(stage = "translate", showTimes) {
      translateImpl(root, inputPath, log, commonOptions, options)
    }
  }

  final def parseValidateTranslate(
    inputPath: Path,
    log: Logger,
    commonOptions: CommonOptions,
    validatingOptions: ValidatingOptions,
    options: OPT
  ): Seq[File] = {
    Riddl.parseAndValidate(inputPath, log, commonOptions, validatingOptions) match {
      case Some(root) => translate(root, inputPath, log, commonOptions, options)
      case None       => Seq.empty[File]
    }
  }

  final def parseValidateTranslate(
    input: RiddlParserInput,
    inputPath: Path,
    log: Logger,
    commonOptions: CommonOptions,
    validatingOptions: ValidatingOptions,
    options: OPT
  ): Seq[File] = {
    Riddl.parseAndValidate(input, log, commonOptions, validatingOptions) match {
      case Some(root) => translate(root, inputPath, log, commonOptions, options)
      case None       => Seq.empty[File]
    }
  }

  final def parseValidateTranslateFile(
    path: Path,
    log: Logger,
    commonOptions: CommonOptions,
    validatingOptions: ValidatingOptions,
    options: OPT
  ): Seq[File] = {
    parseValidateTranslate(RiddlParserInput(path), path, log, commonOptions, validatingOptions, options)
  }
}
