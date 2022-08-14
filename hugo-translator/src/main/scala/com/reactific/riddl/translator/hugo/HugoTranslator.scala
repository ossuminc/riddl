/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.translator.hugo

import com.reactific.riddl.language.AST._
import com.reactific.riddl.language._
import com.reactific.riddl.utils.{PathUtils, Tar, Zip}

import java.io.{File, IOException}
import java.net.URL
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import scala.collection.mutable

case class HugoTranslatingOptions(
  inputFile: Option[Path] = None,
  outputDir: Option[Path] = None,
  eraseOutput: Boolean = false,
  projectName: Option[String] = None,
  baseUrl: Option[URL] = Option(new URL("https://example.com/")),
  themes: Seq[(String, Option[URL])] =
    Seq("hugo-geekdoc" -> Option(HugoTranslator.geekDoc_url)),
  sourceURL: Option[URL] = Some(new URL("http://localhost:1313/")),
  editPath: Option[String] = None,
  siteLogo: Option[URL] = None,
  siteLogoPath: Option[String] = Some("images/logo.png"),
  withGlossary: Boolean = true,
  withTODOList: Boolean = true,
  withGraphicalTOC: Boolean = false)
    extends TranslatingOptions {
  def outputRoot: Path = outputDir.getOrElse(Path.of("")).toAbsolutePath
  def contentRoot: Path = outputRoot.resolve("content")
  def staticRoot: Path = outputRoot.resolve("static")
  def themesRoot: Path = outputRoot.resolve("themes")
  def configFile: Path = outputRoot.resolve("config.toml")
}

case class HugoTranslatorState(options: HugoTranslatingOptions) {
  val files: mutable.ListBuffer[MarkdownWriter] = mutable.ListBuffer
    .empty[MarkdownWriter]
  val dirs: mutable.Stack[Path] = mutable.Stack[Path]()
  dirs.push(options.contentRoot)

  def parentDirs: Path = dirs.foldRight(Path.of("")) { case (nm, path) =>
    path.resolve(nm)
  }

  def addDir(name: String): Path = {
    dirs.push(Path.of(name))
    parentDirs
  }

  def addFile(parents: Seq[String], fileName: String): MarkdownWriter = {
    val parDir = parents.foldLeft(options.contentRoot) { (next, par) =>
      next.resolve(par)
    }
    val path = parDir.resolve(fileName)
    val mdw = MarkdownWriter(path)
    files.append(mdw)
    mdw
  }

  var terms = Seq.empty[GlossaryEntry]

  def addToGlossary(
    d: Definition,
    parents: Seq[String]
  ): HugoTranslatorState = {
    if (options.withGlossary) {
      val entry = GlossaryEntry(
        d.id.value,
        d.kind,
        d.brief.map(_.s).getOrElse("--"),
        parents :+ d.id.value
      )
      terms = terms :+ entry
    }
    this
  }

  val lastFileWeight = 999

  def makeIndex(root: RootContainer): Unit = {
    val mdw = addFile(Seq.empty[String], "_index.md")
    mdw.fileHead("Top Index", 10, Option("The main index to the content"))
    mdw.h2("Domains")
    val domains = root.contents.sortBy(_.id.value)
      .map(d => s"[${d.id.value}](${d.id.value.toLowerCase}/)")
    mdw.list(domains)
    mdw.h2("Indices")
    val glossary =
      if (options.withGlossary) { Seq("[Glossary](glossary)") }
      else { Seq.empty[String] }
    val todoList =
      if (options.withTODOList) { Seq("[To Do List](todolist)") }
      else { Seq.empty[String] }
    mdw.list(glossary ++ todoList)
  }

  def makeGlossary(): Unit = {
    if (options.withGlossary) {
      val mdw = addFile(Seq.empty[String], "glossary.md")
      mdw.fileHead(
        "Glossary Of Terms",
        lastFileWeight - 1,
        Option("A list of definitions needing more work")
      )
      mdw.emitGlossary(lastFileWeight, terms)
    }
  }

  def makeToDoList(root: RootContainer): Unit = {
    if (options.withTODOList) {
      val finder = Finder(root)
      val items = for {
        list <- finder.findEmpty
        item = list._1.identify
        parents = list._2.dropRight(1).reverse
        path = parents.map(_.id.value).mkString(".")
        link = parents.map(_.id.value).mkString("/") + list._1.id.value
      } yield { s"[$item At $path]($link)" }
      val mdw = addFile(Seq.empty[String], "todolist.md")
      mdw.fileHead(
        "To Do List",
        lastFileWeight - 1,
        Option("A list of definitions needing more work")
      )
      mdw.h2("Definitions With Missing Content")
      mdw.list(items)
    }
  }

  def close(root: RootContainer): Seq[Path] = {
    makeIndex(root)
    makeGlossary()
    makeToDoList(root)
    files.foreach(_.write())
    files.map(_.filePath).toSeq
  }
}

object HugoTranslator extends Translator[HugoTranslatingOptions] {

  val geekdoc_dest_dir = "hugo-geekdoc"
  val geekDoc_version = "v0.34.1"
  val geekDoc_file = "hugo-geekdoc.tar.gz"
  val geekDoc_url = new URL(
    s"https://github.com/thegeeklab/hugo-geekdoc/releases/download/$geekDoc_version/$geekDoc_file"
  )

  val sitemap_xsd = "https://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd"

  def deleteAll(directory: File): Boolean = {
    val maybeFiles = Option(directory.listFiles)
    if (maybeFiles.nonEmpty) {
      for (file <- maybeFiles.get) { deleteAll(file) }
    }
    directory.delete
  }

  def loadATheme(from: Option[URL], destDir: Path): Unit = {
    if (from.isDefined) {
      val fileName = PathUtils.copyURLToDir(from.get, destDir)
      val zip_path = destDir.resolve(fileName)
      fileName match {
        case name if name.endsWith(".zip") =>
          Zip.unzip(zip_path, destDir)
          zip_path.toFile.delete()
        case name if name.endsWith(".tar.gz") =>
          Tar.untar(zip_path, destDir)
          zip_path.toFile.delete()
        case _ =>
          throw new IllegalArgumentException("Can only load a theme from .tar.gz or .zip file")
      }
    }
  }

  def loadThemes(options: HugoTranslatingOptions): Unit = {
    for ((name, url) <- options.themes) {
      val destDir = options.themesRoot.resolve(name)
      loadATheme(url, destDir)
    }
  }

  case class TreeCopyFileVisitor(log: Logger, source: Path, target: Path)
      extends SimpleFileVisitor[Path] {

    @throws[IOException]
    override def preVisitDirectory(
      dir: Path,
      attrs: BasicFileAttributes
    ): FileVisitResult = {
      val resolve = target.resolve(source.relativize(dir))
      if (Files.notExists(resolve)) { Files.createDirectories(resolve) }
      FileVisitResult.CONTINUE
    }

    @throws[IOException]
    override def visitFile(
      file: Path,
      attrs: BasicFileAttributes
    ): FileVisitResult = {
      val resolve = target.resolve(source.relativize(file))
      if (!file.getFileName.startsWith(".")) {
        Files.copy(file, resolve, StandardCopyOption.REPLACE_EXISTING)
      }
      FileVisitResult.CONTINUE
    }

    override def visitFileFailed(
      file: Path,
      exc: IOException
    ): FileVisitResult = {
      log.error(s"Unable to copy: $file: $exc\n")
      FileVisitResult.CONTINUE
    }
  }

  def loadStaticAssets(
    inputPath: Path,
    log: Logger,
    options: HugoTranslatingOptions
  ): Unit = {
    val inputRoot: Path = inputPath.toAbsolutePath
    val sourceDir: Path = inputRoot.getParent.resolve("static")

    val targetDir = options.staticRoot
    if (Files.exists(sourceDir) && Files.isDirectory(sourceDir)) {
      // copy source to target using Files Class
      val visitor = TreeCopyFileVisitor(log, sourceDir, targetDir)
      Files.walkFileTree(sourceDir, visitor)
    }
  }

  def loadSiteLogo(options: HugoTranslatingOptions): Path = {
    options.siteLogo match {
      case Some(url) =>
        val fileName = PathUtils.copyURLToDir(url, options.staticRoot)
        options.staticRoot.resolve(fileName)
      case None =>
        options.staticRoot.resolve("logo.png")
    }
  }

  def copyResource(destination: Path):Unit = {
    import java.nio.file.Files
    import java.nio.file.StandardCopyOption
    val name = destination.getFileName.toString
    val src = this.getClass.getClassLoader.getResourceAsStream(name)
    require(src != null, s"Cannot find resource named '$name' ")
    Files.copy(src, destination, StandardCopyOption.REPLACE_EXISTING)
  }

  def manuallyMakeNewHugoSite(where: File): Unit = {
    val path = where.toPath
    if (Files.isDirectory(path)) {
      Files.createDirectories(path.resolve("archetypes"))
      Files.createDirectories(path.resolve("content"))
      Files.createDirectories(path.resolve("data"))
      Files.createDirectories(path.resolve("layouts"))
      Files.createDirectories(path.resolve("public"))
      Files.createDirectories(path.resolve("static"))
      Files.createDirectories(path.resolve("themes"))
      copyResource(path.resolve("config.toml"))
      copyResource(path.resolve("archetypes").resolve("default.md"))
    }
  }

  def makeDirectoryStructure(
    inputPath: Path,
    log: Logger,
    options: HugoTranslatingOptions
  ): HugoTranslatingOptions = {
    val outDir = options.outputRoot.toFile
    if (outDir.exists()) { if (options.eraseOutput) { deleteAll(outDir) } }
    else { outDir.mkdirs() }
    val parent = outDir.getParentFile
    require(
      parent.isDirectory,
      "Parent of output directory is not a directory!"
    )
    manuallyMakeNewHugoSite(outDir)
    loadThemes(options)
    loadStaticAssets(inputPath, log, options)
    val logoPath = loadSiteLogo(options).relativize(options.staticRoot).toString
    options.copy(siteLogoPath = Option(logoPath))
  }

  def writeConfigToml(
    options: HugoTranslatingOptions,
    author: Option[AuthorInfo]
  ): Unit = {
    import java.nio.charset.StandardCharsets
    import java.nio.file.Files
    val content = configTemplate(options, author)
    val outFile = options.configFile
    Files.write(outFile, content.getBytes(StandardCharsets.UTF_8))
  }

  def parents(stack: Seq[ParentDefOf[Definition]]): Seq[String] = {
    // The stack goes from most nested to highest. We don't want to change the
    // stack (its mutable) so we copy it to a Seq first, then reverse it, then
    // drop all the root containers (file includes) to finally end up at a domin
    // and then map to just the name of that domain.
    val result = stack.reverse.dropWhile(_.isRootContainer).map(_.id.format)
    result
  }

  def setUpContainer(
    c: ParentDefOf[Definition],
    state: HugoTranslatorState,
    stack: Seq[ParentDefOf[Definition]]
  ): (MarkdownWriter, Seq[String]) = {
    state.addDir(c.id.format)
    val pars = parents(stack)
    state.addFile(pars :+ c.id.format, "_index.md") -> pars
  }

  def setUpDefinition(
    d: Definition,
    state: HugoTranslatorState,
    stack: Seq[ParentDefOf[Definition]]
  ): (MarkdownWriter, Seq[String]) = {
    val pars = parents(stack)
    state.addFile(pars, d.id.format + ".md") -> pars
  }

  override def translateImpl(
    root: AST.RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: HugoTranslatingOptions
  ): Seq[Path] = {
    require(options.inputFile.nonEmpty, "An input path was not provided.")
    require(options.outputDir.nonEmpty, "An output path was not provided.")
    require(options.outputRoot.getNameCount > 2, "Output path is too shallow")
    require(
      options.outputRoot.getFileName.toString.nonEmpty,
      "Output path is empty"
    )
    val newOptions = makeDirectoryStructure(options.inputFile.get, log, options)
    val maybeAuthor = root.contents.headOption match {
      case Some(domain) => domain.author
      case None         => Option.empty[AuthorInfo]
    }
    writeConfigToml(newOptions, maybeAuthor)
    val state = HugoTranslatorState(options)
    val parentStack = mutable.Stack[ParentDefOf[Definition]]()

    val newState = Folding.foldLeftWithStack(state, parentStack)(root) {
      case (st, e: AST.Entity, stack) =>
        val (mkd, parents) = setUpContainer(e, st, stack)
        mkd.emitEntity(e, parents)
        st.addToGlossary(e, parents)
        st
      case (st, f: AST.Function, stack) =>
        val (mkd, parents) = setUpContainer(f, st, stack)
        mkd.emitFunction(f, parents)
        st.addToGlossary(f, parents)
      case (st, c: AST.Context, stack) =>
        val (mkd, parents) = setUpContainer(c, st, stack)
        mkd.emitContext(c, parents)
        st.addToGlossary(c, parents)
      case (st, a: AST.Adaptor, stack) =>
        val (mkd, parents) = setUpContainer(a, st, stack)
        mkd.emitAdaptor(a, parents)
        st.addToGlossary(a, parents)
      case (st, s: AST.Saga, stack) =>
        val (mkd, parents) = setUpContainer(s, st, stack)
        mkd.emitSaga(s, parents)
        st.addToGlossary(s, parents)
      case (st, s: AST.Story, stack) =>
        val (mkd, parents) = setUpContainer(s, st, stack)
        mkd.emitStory(s, parents)
        st.addToGlossary(s, parents)
      case (st, p: AST.Plant, stack) =>
        val (mkd, parents) = setUpContainer(p, st, stack)
        mkd.emitPlant(p, parents)
        st.addToGlossary(p, parents)
      case (st, p: AST.Processor, stack) =>
        val (mkd, parents) = setUpContainer(p, st, stack)
        mkd.emitProcessor(p, parents)
        st.addToGlossary(p, parents)
      case (st, d: AST.Domain, stack) =>
        val (mkd, parents) = setUpContainer(d, st, stack)
        mkd.emitDomain(d, parents)
        st.addToGlossary(d, parents)
      case (st, a: AST.Adaptation, stack) =>
        val (mkd, parents) = setUpDefinition(a, st, stack)
        mkd.emitAdaptation(a, parents)
        st
      case (st, p: AST.Pipe, stack) =>
        val (mkd, parents) = setUpDefinition(p, st, stack)
        mkd.emitPipe(p, parents)
        st.addToGlossary(p, parents)
      case (st, t: AST.Term, stack)  => st.addToGlossary(t, parents(stack))
      case (st, _: RootContainer, _) =>
        // skip, not needed
        st
      case (st, _, _) => // skip, handled by the MarkdownWriter
        st
    }
    newState.close(root)
  }

  // scalastyle:off method.length
  def configTemplate(
    options: HugoTranslatingOptions,
    author: Option[AuthorInfo]
  ): String = {
    val auth: AuthorInfo = author.getOrElse(AuthorInfo(
      1 -> 1,
      name = LiteralString(1 -> 1, "Not Provided"),
      email = LiteralString(1 -> 1, "somebody@somewere.tld")
    ))
    val themes: String = {
      options.themes.map(_._1).mkString("[ \"", "\", \"", "\" ]")
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
       |description = "${options.projectName
      .getOrElse("Unspecified Project Description")}"
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
       |  geekdocToC = false
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
