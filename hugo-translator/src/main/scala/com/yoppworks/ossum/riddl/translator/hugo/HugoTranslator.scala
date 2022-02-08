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
  baseUrl: Option[URL] = Option(new URL("https://example.com/")),
  themes: Seq[(String, URL)] = Seq("hugo-geekdoc" -> HugoTranslator.geekDoc_url),
  sourceURL: Option[URL] = None,
  editPath: Option[String] = None,
  siteLogo: Option[URL] = None,
  siteLogoPath: Option[String] = None)
    extends TranslatingOptions {
  lazy val outputRoot: Path = outputPath.getOrElse(Path.of("."))
  lazy val contentRoot: Path = outputRoot.resolve("content")
  lazy val staticRoot: Path = contentRoot.resolve("static")
  lazy val themesRoot: Path = outputRoot.resolve("themes")
  lazy val riddlThemeRoot: Path = themesRoot.resolve(HugoTranslator.riddl_hugo_theme._1)
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

  val riddl_hugo_theme: (String, String) = "riddl-hugo-theme" -> "riddl-hugo-theme.zip"
  val geekdoc_dest_dir = "hugo-geekdoc"
  val geekDoc_version = "v0.25.1"
  val geekDoc_file = "hugo-geekdoc.tar.gz"
  val geekDoc_url = new URL(
    s"https://github.com/thegeeklab/hugo-geekdoc/releases/download/$geekDoc_version/$geekDoc_file"
  )

  val sitemap_xsd = "https://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd"

  def copyURLToDir(from: Option[URL], destDir: Path): String = {
    if (from.isDefined) {
      import java.io.InputStream
      import java.nio.file.{Files, StandardCopyOption}
      val fileName = from.get.getPath.split('/').last
      val in: InputStream = from.get.openStream
      destDir.toFile.mkdirs()
      val dl_path = destDir.resolve(fileName)
      Files.copy(in, dl_path, StandardCopyOption.REPLACE_EXISTING)
      fileName
    } else { "" }
  }

  def loadATheme(from: Option[URL], destDir: Path): Unit = {
    if (from.isDefined) {
      val fileName = copyURLToDir(from, destDir)
      val dl_path = destDir.resolve(fileName)
      val rc = Process(s"tar zxf $fileName", cwd = destDir.toFile).!
      if (rc != 0) { throw new IOException(s"Failed to unzip $dl_path") }
      dl_path.toFile.delete()
    }
  }

  def loadThemes(options: HugoTranslatingOptions): Unit = {
    for ((name, url) <- options.themes) {
      val destDir = options.themesRoot.resolve(name)
      loadATheme(Option(url), destDir)
    }
    val url = this.getClass.getClassLoader.getResource(riddl_hugo_theme._2)
    if (url == null) {
      options.log.severe(s"Build Botch: Can't load resource '${riddl_hugo_theme._2}'")
    } else { loadATheme(Option(url), options.themesRoot.resolve(riddl_hugo_theme._1)) }
  }

  def loadSiteLogo(options: HugoTranslatingOptions): Path = {
    if (options.siteLogo.nonEmpty) {
      val fileName = copyURLToDir(options.siteLogo, options.staticRoot)
      options.staticRoot.resolve(fileName)
    } else { options.riddlThemeRoot.resolve("img/YWLogo.png") }
  }

  def deleteAll(directory: File): Boolean = {
    val maybeFiles = Option(directory.listFiles)
    if (maybeFiles.nonEmpty) { for (file <- maybeFiles.get) { deleteAll(file) } }
    directory.delete
  }

  def makeDirectoryStructure(options: HugoTranslatingOptions): HugoTranslatingOptions = {
    try {
      val outDir = options.outputRoot.toFile
      if (outDir.exists()) { deleteAll(outDir) }
      val parent = outDir.getParentFile
      require(parent.isDirectory, "Parent of output directory is not a directory!")
      if (0 != Process(s"hugo new site ${outDir.getAbsolutePath}", cwd = parent).!) {
        options.log.error(s"Hugo could not create a site here: $outDir")
      } else { loadThemes(options) }
      val logoPath = loadSiteLogo(options).relativize(options.staticRoot).toString
      options.copy(siteLogoPath = Option(logoPath))
    } catch {
      case x: Exception =>
        options.log.error(s"While making directory structure: ${x.toString}")
        options
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
    val newOptions = makeDirectoryStructure(options)
    val maybeAuthor = root.domains.headOption match {
      case Some(domain) => domain.author
      case None         => Option.empty[AuthorInfo]
    }
    writeConfigToml(newOptions, maybeAuthor)
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

  // scalastyle:off method.length
  def configTemplate(options: HugoTranslatingOptions, author: Option[AuthorInfo]): String = {
    val auth: AuthorInfo = author.getOrElse(AuthorInfo(
      1 -> 1,
      name = LiteralString(1 -> 1, "Not Provided"),
      email = LiteralString(1 -> 1, "somebody@somewere.tld")
    ))
    val themes: String = {
      val names: Seq[String] = options.themes.map(_._1)
      (names :+ this.riddl_hugo_theme._1).mkString("[ \"", "\", \"", "\" ]")
    }
    val srcURL: String = options.sourceURL.fold("")(_.toString)
    val editPath: String = options.editPath.getOrElse("")
    val siteLogoPath: String = options.siteLogoPath.getOrElse("logo.png")
    val legalPath: String = "/legal"
    val privacyPath: String = "/privacy"

    s"""######################## Hugo Configuration ####################
       |
       |# Configure GeekDocs
       |languageCode = 'en-us'
       |title = '${options.projectName.getOrElse("Unspecified Project Title")}'
       |name = "${options.projectName.getOrElse("Unspecified Project Name")}"
       |description = "${options.projectName.getOrElse("Unspecified Project Description")}"
       |baseUrl = "${options.baseUrl.fold("https://example.prg/")(_.toString)}"
       |homepage = "https://example.org/"
       |demosite = "https://example.org/"
       |tags = ["docs", "documentation", "responsive", "simple", "riddl"]
       |min_version = "0.83.0"
       |theme = $themes
       |
       |# Author information from config
       |[author]
       |    name = "${auth.name.s}"
       |    email = "${auth.email.s}"
       |    homepage = "${auth.url.getOrElse(new URL("https://example.org/"))}"
       |
       |# Required to get well formatted code blocks
       |pygmentsUseClasses = true
       |pygmentsCodeFences = true
       |disablePathToLower = true
       |enableGitInfo      = true
       |pygmentsStyle      =  "monokailight"
       |
       |# Required if you want to render robots.txt template
       |enableRobotsTXT = true
       |
       |
       |# markup(down?) rendering configuration
       |[markup.goldmark.renderer]
       |  # Needed for mermaid shortcode
       |  unsafe = true
       |[markup.tableOfContents]
       |  startLevel = 1
       |  endLevel = 9
       |[markup.goldmark.extensions]
       |  definitionList = true
       |  footnote = true
       |  linkify = true
       |  strikethrough = true
       |  table = true
       |  taskList = true
       |  typographer = true
       |
       |
       |[taxonomies]
       |  tag = "tags"
       |
       |[params]
       |  # (Optional, default 6) Set how many table of contents levels to be showed on page.
       |  # Use false to hide ToC, note that 0 will default to 6 (https://gohugo.io/functions/default/)
       |  # You can also specify this parameter per page in front matter.
       |  geekdocToC = 4
       |
       |  # (Optional, default static/brand.svg) Set the path to a logo for the Geekdoc
       |  # relative to your 'static/' folder.
       |  geekdocLogo = "$siteLogoPath"
       |
       |  # (Optional, default false) Render menu from data file in 'data/menu/main.yaml'.
       |  # See also https://geekdocs.de/usage/menus/#bundle-menu.
       |  geekdocMenuBundle = false
       |
       |  # (Optional, default false) Collapse all menu entries, can not be overwritten
       |  # per page if enabled. Can be enabled per page via `geekdocCollapseSection`.
       |  geekdocCollapseAllSections = false
       |
       |  # (Optional, default true) Show page navigation links at the bottom of each
       |  # docs page (bundle menu only).
       |  geekdocNextPrev = true
       |
       |  # (Optional, default true) Show a breadcrumb navigation bar at the top of each docs page.
       |  # You can also specify this parameter per page in front matter.
       |  geekdocBreadcrumb = true
       |
       |  # (Optional, default none) Set source repository location. Used for 'Edit page' links.
       |  # You can also specify this parameter per page in front matter.
       |  geekdocRepo = "$srcURL"
       |
       |  # (Optional, default none) Enable 'Edit page' links. Requires 'GeekdocRepo' param
       |  # and path must point to 'content' directory of repo.
       |  # You can also specify this parameter per page in front matter.
       |  geekdocEditPath = "$editPath"
       |
       |  # (Optional, default true) Enables search function with flexsearch.
       |  # Index is built on the fly and might slow down your website.
       |  geekdocSearch = true
       |
       |  # (Optional, default false) Display search results with the parent folder as prefix. This
       |  # option allows you to distinguish between files with the same name in different folders.
       |  # NOTE: This parameter only applies when 'geekdocSearch = true'.
       |  geekdocSearchShowParent = true
       |
       |  # (Optional, default none) Add a link to your Legal Notice page to the site footer.
       |  # It can be either a remote url or a local file path relative to your content directory.
       |  geekdocLegalNotice = "$legalPath"
       |
       |  # (Optional, default none) Add a link to your Privacy Policy page to the site footer.
       |  # It can be either a remote url or a local file path relative to your content directory.
       |  geekdocPrivacyPolicy = "$privacyPath"
       |
       |  # (Optional, default true) Add an anchor link to headlines.
       |  geekdocAnchor = true
       |
       |  # (Optional, default true) Copy anchor url to clipboard on click.
       |  geekdocAnchorCopy = true
       |
       |  # (Optional, default true) Enable or disable image lazy loading for images rendered
       |  # by the 'img' shortcode.
       |  geekdocImageLazyLoading = true
       |
       |  # (Optional, default false) Set HTMl <base> to .Site.BaseURL if enabled. It might be required
       |  # if a subdirectory is used within Hugos BaseURL.
       |  # See https://developer.mozilla.org/de/docs/Web/HTML/Element/base.
       |  geekdocOverwriteHTMLBase = false
       |
       |  # (Optional, default false) Auto-decrease brightness of images and add a slightly grayscale to avoid
       |  # bright spots while using the dark mode.
       |  geekdocDarkModeDim = true
       |
       |  # (Optional, default true) Display a "Back to top" link in the site footer.
       |  geekdocBackToTop = true
       |
       |  # (Optional, default false) Enable or disable adding tags for post pages automatically to the navigation sidebar.
       |  geekdocTagsToMenu = true
       |
       |  # (Optional, default 'title') Configure how to sort file-tree menu entries. Possible options are 'title', 'linktitle',
       |  # 'date', 'publishdate', 'expirydate' or 'lastmod'. Every option can be used with a reverse modifier as well
       |  # e.g. 'title_reverse'.
       |  geekdocFileTreeSortBy = "title"
       |
       |""".stripMargin
  }
}
