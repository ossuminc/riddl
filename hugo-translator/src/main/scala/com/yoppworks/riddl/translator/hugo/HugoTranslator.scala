package com.yoppworks.riddl.translator.hugo

import com.yoppworks.ossum.riddl.language.{AST, Riddl, Translator, TranslatorConfiguration}
import pureconfig.generic.auto._
import pureconfig.{ConfigReader, ConfigSource}

import java.io.File
import java.net.URL
import java.nio.file.Path

case class HugoTranslatorConfig(
  showTimes: Boolean = false,
  showWarnings: Boolean = false,
  showMissingWarnings: Boolean = false,
  showStyleWarnings: Boolean = false,
  inputPath: Option[Path] = None,
  outputPath: Option[Path] = None
) extends TranslatorConfiguration

class HugoTranslator extends Translator[HugoTranslatorConfig] {
  type CONF = HugoTranslatorConfig
  val defaultConfig: HugoTranslatorConfig = HugoTranslatorConfig()

  def loadConfig(path: Path): ConfigReader.Result[HugoTranslatorConfig] = {
    ConfigSource.file(path).load[HugoTranslatorConfig]
  }

  val geekdoc_version = "v0.25.1"
  val geekdoc_url = new URL(
    s"https://github.com/thegeeklab/hugo-geekdoc/releases/download/${
      geekdoc_version
    }/hugo-geekdoc.tar.gz")

  val sitmap_xsd = "https://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd"

  def loadTemplate(rootDir: Path): Unit = {

  }

  def loadGeekDoc(rootDir: Path): Unit = {
    val dest = rootDir.resolve("")
  }

  override def translate(
    root: AST.RootContainer,
    outputRoot: Option[Path],
    logger: Riddl.Logger,
    config: HugoTranslatorConfig
  ): Seq[File] = {
    Seq.empty[File]
  }

}
