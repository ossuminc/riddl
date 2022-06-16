package com.reactific.riddl.translator.kalix

import com.reactific.riddl.language.AST
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.TranslatingOptions
import com.reactific.riddl.language.Translator
import com.reactific.riddl.utils.Logger

import java.nio.file.Path

case class KalixOptions(
  inputFile: Option[Path] = None,
  outputDir: Option[Path] = None,
  kalixPath: Option[Path] = None,
  projectName: Option[String] = None)
    extends TranslatingOptions

object KalixTranslator extends Translator[KalixOptions] {
  val defaultOptions: KalixOptions = KalixOptions()

  override protected def translateImpl(
    root: AST.RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: KalixOptions
  ): Seq[Path] = {
    require(options.inputFile.nonEmpty, "An input path was not provided.")
    require(options.outputDir.nonEmpty, "An output path was not provided.")
    require(options.projectName.nonEmpty, "A project name must be provided")
    Seq.empty[Path]
  }
}
