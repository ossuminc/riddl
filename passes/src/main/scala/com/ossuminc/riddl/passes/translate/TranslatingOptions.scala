package com.ossuminc.riddl.passes.translate

import java.nio.file.Path

trait TranslatingOptions {
  def inputFile: Option[Path] = None
  def outputDir: Option[Path] = Some(Path.of(System.getProperty("java.io.tmpdir")))
  def projectName: Option[String] = None
}
