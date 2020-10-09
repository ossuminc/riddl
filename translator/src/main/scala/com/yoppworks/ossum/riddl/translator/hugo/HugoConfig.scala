package com.yoppworks.ossum.riddl.translator.hugo

import java.net.URL
import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.TranslatorConfiguration

/** FormatConfig for Configuration of Hugo output */
case class HugoConfig(
  showTimes: Boolean = false,
  showWarnings: Boolean = false,
  showMissingWarnings: Boolean = false,
  showStyleWarnings: Boolean = false,
  inputPath: Option[Path] = None,
  outputPath: Option[Path] = Some(Path.of(".")),
  themeUrl: Option[URL] = None)
    extends TranslatorConfiguration {

  def basePath: Path = { outputPath.getOrElse(Path.of(".")) }
}
