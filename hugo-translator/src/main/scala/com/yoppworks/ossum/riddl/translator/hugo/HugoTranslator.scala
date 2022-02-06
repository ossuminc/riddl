package com.yoppworks.ossum.riddl.translator.hugo

import com.yoppworks.ossum.riddl.language.AST.{Container, Definition, RootContainer}
import com.yoppworks.ossum.riddl.language.Validation.ValidatingOptions
import com.yoppworks.ossum.riddl.language._
import pureconfig.generic.auto._
import pureconfig.{ConfigReader, ConfigSource}

import java.io.{File, IOException}
import java.net.URL
import java.nio.file.Path
import scala.collection.mutable

case class HugoTranslatingOptions(
  validatingOptions: ValidatingOptions = ValidatingOptions(),
  projectName: Option[String] = None,
  inputPath: Option[Path] = None,
  outputPath: Option[Path] = None,
  configPath: Option[Path] = None,
  logger: Option[Logger] = None,
  baseUrl: Option[URL] = Some(new URL("http://example.com/")),
  themeUrl: Option[URL] = Some(HugoTranslator.geekDoc_url)
) extends TranslatingOptions {
  def contentRoot: Path = {
    outputPath.getOrElse(Path.of(".")).resolve("content")
  }
}

case class HugoTranslatorConfig() extends TranslatorConfiguration

case class HugoTranslatorState(options: HugoTranslatingOptions, config: HugoTranslatorConfig) {
  val files: mutable.ListBuffer[MarkdownWriter] = mutable.ListBuffer.empty[MarkdownWriter]
  val dirs: mutable.Stack[Path] = mutable.Stack[Path]()
  dirs.push(options.contentRoot)

  def parentDirs: Path = dirs.foldRight(Path.of("")) { case (nm, path) => path.resolve(nm) }

  def addDir(name: String): Path = {
    dirs.push(Path.of(name))
    parentDirs
  }

  def addFile(fileName: String): MarkdownWriter = {
    val path = parentDirs.resolve(fileName)
    val mdw = MarkdownWriter(path)
    files.append(mdw)
    mdw
  }
}


object HugoTranslator extends Translator[HugoTranslatingOptions, HugoTranslatorConfig] {
  val defaultConfig: HugoTranslatorConfig = HugoTranslatorConfig()
  val defaultOptions: HugoTranslatingOptions = HugoTranslatingOptions()

  def loadConfig(path: Path): ConfigReader.Result[HugoTranslatorConfig] = {
    ConfigSource.file(path).load[HugoTranslatorConfig]
  }

  val geekDoc_version = "v0.25.1"
  val geekDoc_file = "hugo-geekdoc.tar.gz"
  val geekDoc_url = new URL(
    s"https://github.com/thegeeklab/hugo-geekdoc/releases/download/$geekDoc_version/$geekDoc_file")

  val sitemap_xsd = "https://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd"

  def loadGeekDoc(outDir: Path): Unit = {
    import java.io.InputStream
    import java.nio.file.{Files, StandardCopyOption}
    import scala.sys.process.Process
    val in: InputStream = geekDoc_url.openStream
    val tar_gz_File = outDir.resolve("themes").resolve(geekDoc_file)
    Files.copy(in, tar_gz_File, StandardCopyOption.REPLACE_EXISTING)
    Process(s"tar zxf $tar_gz_File").! match {
      case rc: Int if rc != 0 =>
        throw new IOException(s"Failed to unzip $tar_gz_File")
    }
  }

  def parents(stack: mutable.Stack[Container[Definition]]): Seq[String] = {
    stack.map(_.id.format).toSeq.reverse
  }

  def setUpContainer(
    c: Container[Definition],
    state: HugoTranslatorState,
    stack: mutable.Stack[Container[Definition]]
  ): (MarkdownWriter, Seq[String]) = {
    state.addDir(c.id.format)
    val pars = parents(stack)
    stack.push(c)
    state.addFile("_index.md") -> pars
  }

  def setUpDefinition(
    d: Definition,
    state: HugoTranslatorState,
    stack: mutable.Stack[Container[Definition]]
  ): (MarkdownWriter, Seq[String]) = {
    val dirPath = state.addFile(d.id.format + ".md")
    dirPath -> parents(stack)
  }

  override def translate(
    root: AST.RootContainer,
    options: HugoTranslatingOptions,
    config: HugoTranslatorConfig
  ): Seq[File] = {
    val state = HugoTranslatorState(options, config)
    val parents = mutable.Stack[Container[Definition]]()
    val newState = Folding.foldLeft(state, parents)(root) {
      case (st, _: RootContainer, _) =>
        // skip, not needed
        st
      case (st, definition: Definition, stack) =>
        if (definition.isContainer) {
          val (mkd, parents) =
            setUpContainer(definition.asInstanceOf[Container[Definition]], st, stack)
          definition match {
            case e: AST.Entity => mkd.emitEntity(e, parents)
            case f: AST.Function => mkd.emitFunction(f, parents)
            case c: AST.Context => mkd.emitContext(c, parents)
            case a: AST.Adaptor => mkd.emitAdaptor(a, parents)
            case s: AST.Saga => mkd.emitSaga(s, parents)
            case s: AST.Story => mkd.emitStory(s, parents)
            case p: AST.Plant => mkd.emitPlant(p, parents)
            case p: AST.Processor => mkd.emitProcessor(p, parents)
            case d: AST.Domain => mkd.emitDomain(d, parents)
            case _ => // skip, handled by the MarkdownWriter
          }
          stack.pop()
        } else {
          definition match {
            case a: AST.Adaptation =>
              val (mkd, parents) = setUpDefinition(definition, st, stack)
              mkd.emitAdaptation(a, parents)
            case p: AST.Pipe =>
              val (mkd, parents) = setUpDefinition(definition, st, stack)
              mkd.emitPipe(p, parents)
            case _: Definition => // handled by MarkdownWriter or above
          }
        }
        st
    }
    newState.files.foreach(_.write())
    newState.files.map(_.filePath.toFile).toSeq
  }
}
