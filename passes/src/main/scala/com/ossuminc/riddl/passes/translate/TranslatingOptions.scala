package com.ossuminc.riddl.passes.translate

import java.nio.file.Path

trait TranslatingOptions {
  def inputFile: Option[Path]
  def outputDir: Option[Path]
  def projectName: Option[String]
}
