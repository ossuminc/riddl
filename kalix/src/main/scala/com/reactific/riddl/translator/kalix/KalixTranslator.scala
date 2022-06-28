package com.reactific.riddl.translator.kalix

import com.reactific.riddl.language._
import com.reactific.riddl.utils.Logger

import java.nio.file.Path

case class KalixOptions(
  inputFile: Option[Path] = None,
  outputDir: Option[Path] = None,
  kalixPath: Option[Path] = None,
  projectName: Option[String] = None)
    extends TranslatingOptions

object KalixOptions {
  val default: KalixOptions = KalixOptions()
}

case class KalixState(options: KalixOptions)
  extends TranslatorState[GrpcWriter] {}

object KalixTranslator extends Translator[KalixOptions] {

  override protected def translateImpl(
    root: AST.RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: KalixOptions
  ): Seq[Path] = {
    val paths = super.translateImpl(root, log, commonOptions, options)
    require(options.projectName.nonEmpty, "A project name must be provided")

    paths
  }
}
