package com.yoppworks.ossum.riddl.translator.hugo

import com.yoppworks.ossum.riddl.language.AST._
import com.yoppworks.ossum.riddl.language.Validation.ValidatingOptions
import com.yoppworks.ossum.riddl.language._
import pureconfig.generic.auto._
import pureconfig.{ConfigReader, ConfigSource}

import java.io.{File, IOException}
import java.net.URL
import java.nio.file.Path
import scala.collection.mutable
import scala.sys.process.Process

case class HugoTranslatingOptions(
  validatingOptions: ValidatingOptions = ValidatingOptions(),
  projectName: Option[String] = None,
  inputPath: Option[Path] = None,
  outputPath: Option[Path] = None,
  configPath: Option[Path] = None,
  logger: Option[Logger] = None,
  baseUrl: Option[URL] = Some(new URL("https://example.com/")),
  themes: Seq[(String, URL)] = Seq(
    "hugo-geekdoc" -> HugoTranslator.geekDoc_url,
    "riddl-hugo-theme" -> HugoTranslator.riddl_hugo_theme_url
  )

) extends TranslatingOptions {
  lazy val outputRoot: Path = outputPath.getOrElse(Path.of("."))
  lazy val contentRoot: Path = outputRoot.resolve("content")
  lazy val themesRoot: Path = outputRoot.resolve("themes")
  lazy val configFile: Path = outputRoot.resolve("config.toml")
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

  def addFile(parents: Seq[String], fileName: String): MarkdownWriter = {
    val parDir = parents.foldLeft(options.contentRoot) { (next, par) => next.resolve(par) }
    val path = parDir.resolve(fileName)
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

  val riddl_hugo_theme_url: URL =
    Path.of(System.getProperty("user.dir"))
      .resolve("hugo-theme/target/riddl-hugo-theme.zip")
      .toUri.toURL

  val geekdoc_dest_dir = "hugo-geekdoc"
  val geekDoc_version = "v0.25.1"
  val geekDoc_file = "hugo-geekdoc.tar.gz"
  val geekDoc_url = new URL(
    s"https://github.com/thegeeklab/hugo-geekdoc/releases/download/$geekDoc_version/$geekDoc_file")

  val sitemap_xsd = "https://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd"

  def loadATheme(from: URL, destDir: Path): Unit = {
    import java.io.InputStream
    import java.nio.file.{Files, StandardCopyOption}
    val fileName = from.getPath.split('/').last
    val in: InputStream = from.openStream
    destDir.toFile.mkdirs()
    val dl_path = destDir.resolve(fileName)
    Files.copy(in, dl_path, StandardCopyOption.REPLACE_EXISTING)
    val rc = Process(s"tar zxf $fileName", cwd = destDir.toFile).!
    if (rc != 0) {
      throw new IOException(s"Failed to unzip $dl_path")
    }
    dl_path.toFile.delete()
  }

  def loadThemes(options: HugoTranslatingOptions): Unit = {
    for ((name, url) <- options.themes) {
      val destDir = options.themesRoot.resolve(name)
      loadATheme(url, destDir)
    }
  }

  def deleteAll(directory: File): Boolean = {
    val maybeFiles = Option(directory.listFiles)
    if (maybeFiles.nonEmpty) {
      for (file <- maybeFiles.get) {
        deleteAll(file)
      }
    }
    directory.delete
  }

  def makeDirectoryStructure(options: HugoTranslatingOptions): Unit = {
    val outDir = options.outputRoot.toFile
    if (outDir.exists()) {
      deleteAll(outDir)
    }
    val parent = outDir.getParentFile
    require(parent.isDirectory, "Parent of output directory is not a directory!")
    if (0 != Process(s"hugo new site ${outDir.getAbsolutePath}", cwd = parent).!) {
      options.log.error(s"Hugo could not create a site here: $outDir")
    } else {
      loadThemes(options)
    }
  }

  def writeConfigToml(options: HugoTranslatingOptions, author: Option[AuthorInfo]): Unit = {
    import java.nio.charset.StandardCharsets
    import java.nio.file.Files
    val content = configTemplate(options, author)
    val outFile = options.configFile
    Files.write(outFile, content.getBytes(StandardCharsets.UTF_8))
  }

  def parents(stack: mutable.Stack[Container[Definition]]): Seq[String] = {
    // The stack goes from most nested to highest. We don't want to change the
    // stack (its mutable) so we copy it to a Seq first, then reverse it, then
    // drop all the root containers (file includes) to finally end up at a domin
    // and then map to just the name of that domain.
    val result = stack.toSeq.reverse.dropWhile(_.isRootContainer).map(_.id.format)
    result
  }

  def setUpContainer(
    c: Container[Definition],
    state: HugoTranslatorState,
    stack: mutable.Stack[Container[Definition]]
  ): (MarkdownWriter, Seq[String]) = {
    state.addDir(c.id.format)
    val pars = parents(stack)
    state.addFile(pars :+ c.id.format, "_index.md") -> pars
  }

  def setUpDefinition(
    d: Definition,
    state: HugoTranslatorState,
    stack: mutable.Stack[Container[Definition]]
  ): (MarkdownWriter, Seq[String]) = {
    val pars = parents(stack)
    state.addFile(pars, d.id.format + ".md") -> pars
  }

  override def translate(
    root: AST.RootContainer,
    options: HugoTranslatingOptions,
    config: HugoTranslatorConfig
  ): Seq[File] = {
    makeDirectoryStructure(options)
    val maybeAuthor = root.domains.headOption match {
      case Some(domain) =>
        domain.author
      case None => Option.empty[AuthorInfo]
    }
    writeConfigToml(options, maybeAuthor)
    val state = HugoTranslatorState(options, config)
    val parents = mutable.Stack[Container[Definition]]()

    val newState = Folding.foldLeft(state, parents)(root) {
      case (st, e: AST.Entity, stack) =>
        val (mkd, parents) = setUpContainer(e, st, stack)
        mkd.emitEntity(e, parents)
        st
      case (st, f: AST.Function, stack) =>
        val (mkd, parents) = setUpContainer(f, st, stack)
        mkd.emitFunction(f, parents)
        st
      case (st, c: AST.Context, stack) =>
        val (mkd, parents) = setUpContainer(c, st, stack)
        mkd.emitContext(c, parents)
        st
      case (st, a: AST.Adaptor, stack) =>
        val (mkd, parents) = setUpContainer(a, st, stack)
        mkd.emitAdaptor(a, parents)
        st
      case (st, s: AST.Saga, stack) =>
        val (mkd, parents) = setUpContainer(s, st, stack)
        mkd.emitSaga(s, parents)
        st
      case (st, s: AST.Story, stack) =>
        val (mkd, parents) = setUpContainer(s, st, stack)
        mkd.emitStory(s, parents)
        st
      case (st, p: AST.Plant, stack) =>
        val (mkd, parents) = setUpContainer(p, st, stack)
        mkd.emitPlant(p, parents)
        st
      case (st, p: AST.Processor, stack) =>
        val (mkd, parents) = setUpContainer(p, st, stack)
        mkd.emitProcessor(p, parents)
        st
      case (st, d: AST.Domain, stack) =>
        val (mkd, parents) = setUpContainer(d, st, stack)
        mkd.emitDomain(d, parents)
        st
      case (st, a: AST.Adaptation, stack) =>
        val (mkd, parents) = setUpDefinition(a, st, stack)
        mkd.emitAdaptation(a, parents)
        st
      case (st, p: AST.Pipe, stack) =>
        val (mkd, parents) = setUpDefinition(p, st, stack)
        mkd.emitPipe(p, parents)
        st
      case (st, _: RootContainer, _) =>
        // skip, not needed
        st
      case (st, _, _) => // skip, handled by the MarkdownWriter
        st
    }
    newState.files.foreach(_.write())
    newState.files.map(_.filePath.toFile).toSeq
  }

  def configTemplate(options: HugoTranslatingOptions, author: Option[AuthorInfo]): String = {
    val auth: AuthorInfo = author.getOrElse(
      AuthorInfo(1 -> 1, name = LiteralString(1 -> 1, "Not Provided"),
        email = LiteralString(1 -> 1, "somebody@somewere.tld")
      ))
    s"""######################## Hugo Configuration ####################
       |
       |# Configure GeekDocs
       |languageCode = 'en-us'
       |title = '${options.projectName.get}'
       |name = "${options.projectName.get}"
       |description = "${options.projectName.get}"
       |homepage = "https://example.org/"
       |demosite = "https://example.org/"
       |tags = ["docs", "documentation", "responsive", "simple", "riddl"]
       |min_version = "0.83.0"
       |theme = ["hugo-geekdoc"]
       |pygmentsCodeFences=  true
       |pygmentsStyle=  "monokailight"
       |
       |[author]
       |    name = "${auth.name.s}"
       |    email = "${auth.email.s}"
       |    homepage = "${auth.url.getOrElse(new URL("https://example.org/"))}"
       |""".stripMargin
  }
}
