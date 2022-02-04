package com.yoppworks.ossum.riddl.translator.hugo

import com.yoppworks.ossum.riddl.language.AST.{Container, Definition}
import com.yoppworks.ossum.riddl.language._
import pureconfig.generic.auto._
import pureconfig.{ConfigReader, ConfigSource}

import java.io.File
import java.net.URL
import java.nio.file.Path
import scala.collection.mutable

case class HugoTranslatorConfig(
  showTimes: Boolean = false,
  showWarnings: Boolean = false,
  showMissingWarnings: Boolean = false,
  showStyleWarnings: Boolean = false,
  baseURL: URL = new URL("https://example.io/"),
  inputPath: Option[Path] = None,
  outputPath: Option[Path] = None
) extends TranslatorConfiguration {
  def contentRoot: Path = {
    outputPath.getOrElse(Path.of(".")).resolve("content")
  }
}

case class HugoTranslatorState(config: HugoTranslatorConfig) {
  val files: mutable.ListBuffer[MarkdownWriter] = mutable.ListBuffer.empty[MarkdownWriter]
  val dirs: mutable.Stack[Path] = mutable.Stack[Path]()
  dirs.push(config.contentRoot)

  def addDir(name: String): Path = {
    dirs.push(Path.of(name))
    dirs.foldRight(Path.of("")) { case (name, path) => path.resolve(name) }
  }

  def addFile(path: Path): MarkdownWriter = {
    val mdw = MarkdownWriter(path)
    files.append(mdw)
    mdw
  }
}


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
    val state = HugoTranslatorState(config)
    val parents = mutable.Stack[Container[Definition]]()
    Folding.foldLeft(state, parents)(root) {
      case (state, definition: Definition, stack) =>
        definition match {
          case d: Container[Definition] =>
            val dirPath = state.addDir(d.id.format)
            val filePath = dirPath.resolve("_index.md")
            val mkd = state.addFile(filePath)
            mkd.emitContainer(d, stack.map(_.id.format).toSeq.reverse)
            state
        }
    }
    Seq.empty[File]
  }

}
