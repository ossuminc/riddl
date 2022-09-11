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

package com.reactific.riddl.hugo

import com.reactific.riddl.language.AST.{Include, *}
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.{AST, *}
import com.reactific.riddl.utils.{Logger, PathUtils, Tar, TextFileWriter, TreeCopyFileVisitor, Zip}

import java.io.File
import java.net.URL
import java.nio.file.*
import scala.collection.mutable

object HugoTranslator extends Translator[HugoCommand.Options] {

  val geekDoc_version = "v0.34.2"
  val geekDoc_file = "hugo-geekdoc.tar.gz"
  val geekDoc_url = new URL(
    s"https://github.com/thegeeklab/hugo-geekdoc/releases/download/$geekDoc_version/$geekDoc_file"
  )

  def deleteAll(directory: File): Boolean = {
    val maybeFiles = Option(directory.listFiles)
    if (maybeFiles.nonEmpty) {
      for (file <- maybeFiles.get) { deleteAll(file) }
    }
    directory.delete
  }

  def loadATheme(from: URL, destDir: Path): Unit = {
    val fileName = PathUtils.copyURLToDir(from, destDir)
    if (fileName.nonEmpty) {
      val zip_path = destDir.resolve(fileName)
      if (Files.isRegularFile(zip_path)) {
        fileName match {
          case name if name.endsWith(".zip") =>
            Zip.unzip(zip_path, destDir)
            zip_path.toFile.delete()
          case name if name.endsWith(".tar.gz") =>
            Tar.untar(zip_path, destDir)
            zip_path.toFile.delete()
          case _ => throw new IllegalArgumentException(
            "Can only load a theme from .tar.gz or .zip file"
          )
        }
      } else {
        throw new IllegalStateException(
          s"The downloaded theme is not a regular file: $zip_path"
        )
      }
    }
  }

  def loadThemes(options: HugoCommand.Options): Unit = {
    for (
      (name, url) <- options.themes if url.nonEmpty
    ) {
      val destDir = options.themesRoot.resolve(name)
      loadATheme(url.get, destDir)
    }
  }

  def loadStaticAssets(
    inputPath: Path,
    log: Logger,
    options: HugoCommand.Options
  ): Unit = {
    val inputRoot: Path = inputPath.toAbsolutePath
    val sourceDir: Path = inputRoot.getParent.resolve("static")

    val targetDir = options.staticRoot
    if (Files.exists(sourceDir) && Files.isDirectory(sourceDir)) {
      val img = sourceDir
        .resolve(options.siteLogoPath.getOrElse("images/logo.png"))
        .toAbsolutePath
      Files.createDirectories(img.getParent)
      if (!Files.exists(img)) { copyResource(img, "RIDDL-Logo.ico") }
      // copy source to target using Files Class
      val visitor = TreeCopyFileVisitor(log, sourceDir, targetDir)
      Files.walkFileTree(sourceDir, visitor)
    }
  }

  def copyResource(destination: Path, src: String = ""):Unit = {
    val name = if (src.isEmpty) destination.getFileName.toString else src
    TextFileWriter.copyResource(name, destination)
  }

  def manuallyMakeNewHugoSite(path: Path): Unit = {
    Files.createDirectories(path)
    Files.createDirectories(path.resolve("archetypes"))
    Files.createDirectories(path.resolve("content"))
    Files.createDirectories(path.resolve("data"))
    Files.createDirectories(path.resolve("template/layouts"))
    Files.createDirectories(path.resolve("public"))
    Files.createDirectories(path.resolve("static"))
    Files.createDirectories(path.resolve("themes"))
    copyResource(path.resolve("archetypes").resolve("default.md"))
  }

  def makeDirectoryStructure(
    inputPath: Path,
    log: Logger,
    options: HugoCommand.Options
  ): Unit = {
    val outDir = options.outputRoot.toFile
    if (outDir.exists()) {if (options.eraseOutput) {deleteAll(outDir)}}
    else {outDir.mkdirs()}
    val parent = outDir.getParentFile
    require(
      parent.isDirectory,
      "Parent of output directory is not a directory!"
    )
    manuallyMakeNewHugoSite(outDir.toPath)
    loadThemes(options)
    loadStaticAssets(inputPath, log, options)
  }

  def writeConfigToml(
    options: HugoCommand.Options,
    author: Option[Author]
  ): Unit = {
    import java.nio.charset.StandardCharsets
    import java.nio.file.Files
    val content = configTemplate(options, author)
    val outFile = options.configFile
    Files.write(outFile, content.getBytes(StandardCharsets.UTF_8))
  }

  def setUpContainer(
    c: Definition,
    state: HugoTranslatorState,
    stack: Seq[Definition]
  ): (MarkdownWriter, Seq[String]) = {
    state.addDir(c.id.format)
    val pars = state.makeParents(stack)
    state.addFile(pars :+ c.id.format, "_index.md") -> pars
  }

  def setUpLeaf(
    d: Definition,
    state: HugoTranslatorState,
    stack: Seq[Definition]
  ): (MarkdownWriter, Seq[String]) = {
    val pars = state.makeParents(stack)
    state.addFile(pars, d.id.format + ".md") -> pars
  }

  override def translate(
    root: AST.RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: HugoCommand.Options
  ): Either[Messages, Unit] = {
    doTranslation(root, log, commonOptions, options)
    Right(())
  }

  def doTranslation(
    root: AST.RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: HugoCommand.Options
  ): Seq[Path] = {
    require(options.outputRoot.getNameCount > 2, "Output path is too shallow")
    require(
      options.outputRoot.getFileName.toString.nonEmpty,
      "Output path is empty"
    )
    makeDirectoryStructure(options.inputFile.get, log, options)
    val someAuthors = root.contents.headOption match {
      case Some(domain) => domain.authors
      case None         => Seq.empty[Author]
    }
    writeConfigToml(options, someAuthors.headOption)
    val symtab = SymbolTable(root)
    val state = HugoTranslatorState(root, symtab, options, commonOptions)
    val parentStack = mutable.Stack[Definition]()

    Folding
      .foldLeftWithStack(state, parentStack)(root)(processingFolder)
      .close(root)
  }

  def processingFolder(
    state: HugoTranslatorState,
    defn: Definition,
    stack: Seq[Definition]
  ): HugoTranslatorState  = {
    defn match {
      case f: Field =>
        state.addToGlossary(f, stack)
      case i: Invariant =>
        state.addToGlossary(i, stack)
      case e: Enumerator =>
        state.addToGlossary(e, stack)
      case ss: SagaStep =>
        state.addToGlossary(ss, stack)
      case t: Term       =>
        state.addToGlossary(t, stack)
      case _: Example | _: Inlet | _:Outlet | _: InletJoint | _: OutletJoint |
           _: Author | _: OnClause | _: Include | _: RootContainer =>
        // All these cases do not generate a file as their content contributes
        // to the content of their parent container
        state
      case leaf: LeafDefinition =>
        // These are leaf nodes, they get their own file or other special
        // handling.
        leaf match {
            // handled by definition that contains the term
          case p: Pipe         =>
            val (mkd, parents) = setUpLeaf(leaf, state, stack)
            mkd.emitPipe(p, parents)
            state.addToGlossary(p, stack)
          case _ =>
            require(requirement=false, "Failed to handle LeafDefinition")
            state
        }
      case container: Definition =>
        // Everything else is a container and definitely needs its own page
        // and glossary entry.
        val (mkd, parents) = setUpContainer(container, state, stack)
        container match {
          case t: Type       => mkd.emitType(t, stack)
          case s: State      => mkd.emitState(s, stack)
          case h: Handler    => mkd.emitHandler(h, parents)
          case f: Function   => mkd.emitFunction(f, parents)
          case e: Entity     => mkd.emitEntity(e, parents)
          case c: Context    => mkd.emitContext(c, stack)
          case d: Domain     => mkd.emitDomain(d, parents)
          case a: Adaptor    => mkd.emitAdaptor(a, parents)
          case p: Processor  => mkd.emitProcessor(p, parents)
          case p: Projection => mkd.emitProjection(p, parents)
          case s: Saga       => mkd.emitSaga(s, parents)
          case s: Story      => mkd.emitStory(s, parents)
          case p: Plant      => mkd.emitPlant(p, parents)
          case a: Adaptation => mkd.emitAdaptation(a, parents)
        }
        state.addToGlossary(container, stack)
      }
    }

  // scalastyle:off method.length
  def configTemplate(
    options: HugoCommand.Options,
    author: Option[Author]
  ): String = {
    val auth: Author = author.getOrElse(Author(
      1 -> 1,
      id = Identifier(1->1,"unknown"),
      name = LiteralString(1 -> 1, "Not Provided"),
      email = LiteralString(1 -> 1, "somebody@somewere.tld")
    ))
    val themes: String = {
      options.themes.map(_._1).mkString("[ \"", "\", \"", "\" ]")
    }
    val baseURL: String = options.baseUrl.fold("https://example.prg/")(_.toString)
    val srcURL: String = options.sourceURL.fold("")(_.toString)
    val editPath: String = options.editPath.getOrElse("")
    val siteLogoPath: String = options.siteLogoPath.getOrElse("images/logo.png")
    val legalPath: String = "/legal"
    val privacyPath: String = "/privacy"
    val siteTitle = options.siteTitle.getOrElse("Unspecified Site Title")
    val siteName = options.projectName.getOrElse("Unspecified Project Name")
    val siteDescription = options.siteDescription.getOrElse("Unspecified Project Description")

    s"""######################## Hugo Configuration ####################
       |
       |# Configure GeekDocs
       |baseUrl = "$baseURL"
       |languageCode = "en-us"
       |title = "$siteTitle"
       |name = "$siteName"
       |description = "$siteDescription"
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
