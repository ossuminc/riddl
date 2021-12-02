package com.yoppworks.ossum.riddl.translator.hugo

import java.io.File
import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.AST._
import com.yoppworks.ossum.riddl.language.Riddl
import com.yoppworks.ossum.riddl.language.Translator
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import pureconfig.generic.auto._

/** This is a translator to convert a RIDDL AST to a full documentation web site
  * using Hugo.
  */
class HugoTranslator extends Translator[HugoConfig] {

  type CONF = HugoConfig
  val defaultConfig: HugoConfig = HugoConfig()

  def loadConfig(path: Path): ConfigReader.Result[HugoConfig] = {
    ConfigSource.file(path).load[HugoConfig]
  }

  def translate(
    root: RootContainer,
    outputRoot: Option[Path],
    log: Riddl.Logger,
    configuration: HugoConfig
  ): Seq[File] = {
    val state = HugoState(configuration, root)
    val folding = new HugoFolding()
    folding.foldLeft(root, root, state).generatedFiles.map(_.path.toFile).toList
  }
}
