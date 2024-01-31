/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.commands.TranslatingState
import com.ossuminc.riddl.diagrams.{DiagramsPass, DiagramsPassOutput}
import com.ossuminc.riddl.diagrams.mermaid.*
import com.ossuminc.riddl.language.*
import com.ossuminc.riddl.language.AST.{Include, *}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.{Pass, PassCreator, PassInfo, PassInput, PassOutput, PassesOutput, PassesResult}
import com.ossuminc.riddl.passes.resolve.{ReferenceMap, ResolutionOutput, ResolutionPass, Usages}
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.ossuminc.riddl.passes.symbols.Symbols.ParentStack
import com.ossuminc.riddl.passes.validate.ValidationPass
import com.ossuminc.riddl.stats.StatsPass
import com.ossuminc.riddl.utils.{PathUtils, Tar, Timer, TreeCopyFileVisitor, Zip}

import java.io.File
import java.net.URL
import java.nio.file.*
import scala.collection.mutable

object HugoPass extends PassInfo {
  val name: String = "hugo"
  val creator: PassCreator = { (in: PassInput, out: PassesOutput) => HugoPass(in, out, HugoCommand.Options()) }
  private val geekDoc_version = "v0.44.1"
  private val geekDoc_file = "hugo-geekdoc.tar.gz"
  val geekDoc_url: URL = java.net.URI
    .create(
      s"https://github.com/thegeeklab/hugo-geekdoc/releases/download/$geekDoc_version/$geekDoc_file"
    )
    .toURL
}

case class HugoOutput(
  messages: Messages = Messages.empty
) extends PassOutput

case class HugoPass(
  input: PassInput,
  outputs: PassesOutput,
  options: HugoCommand.Options
) extends Pass(input, outputs)
    with PassUtilities
    with TranslatingState[MarkdownWriter]
    with UseCaseDiagramSupport {

  requires(SymbolsPass)
  requires(ResolutionPass)
  requires(ValidationPass)
  requires(StatsPass)

  require(
    options.outputRoot.getFileName.toString.nonEmpty,
    "Output path is empty"
  )

  val root: Root = input.root
  val name: String = HugoPass.name

  lazy val commonOptions: CommonOptions = input.commonOptions
  lazy val refMap: ReferenceMap = outputs.outputOf[ResolutionOutput](ResolutionPass.name).get.refMap

  lazy val symbolsOutput: SymbolsOutput = outputs.symbols
  lazy val usage: Usages = outputs.usage
  lazy val diagrams: DiagramsPassOutput =
    outputs.outputOf[DiagramsPassOutput](DiagramsPass.name).getOrElse(DiagramsPassOutput())
  lazy val passesResult: PassesResult = PassesResult(input, outputs)

  options.inputFile match {
    case Some(inFile) =>
      if Files.exists(inFile) then makeDirectoryStructure(options.inputFile)
      else messages.addError((0, 0), "The input-file does not exist")
    case None =>
      messages.addWarning((0, 0), "The input-file option was not provided")
  }

  private val maybeAuthor = root.authors.headOption.orElse { root.domains.headOption.flatMap(_.authors.headOption) }
  writeConfigToml(options, maybeAuthor)

  private def addFile(parents: Seq[String], fileName: String): MarkdownWriter = {
    val parDir = parents.foldLeft(options.contentRoot) { (next, par) =>
      next.resolve(par)
    }
    val path = parDir.resolve(fileName)
    val mdw = MarkdownWriter(path, commonOptions, symbolsOutput, refMap, usage, this)
    addFile(mdw)
    mdw
  }

  override def process(value: AST.RiddlValue, parents: ParentStack): Unit = {
    val stack = parents.toSeq
    value match {
      case c: Connector =>
        val (md: MarkdownWriter, parents) = setUpLeaf(c, stack)
        md.emitConnector(c, parents)
      case u: User =>
        val (md: MarkdownWriter, parents) = setUpLeaf(u, stack)
        md.emitUser(u, parents)
      case container: Definition =>
        // Everything else is a container and definitely needs its own page
        // and glossary entry.
        val (mkd: MarkdownWriter, parents) = setUpContainer(container, stack)

        container match {
          case a: Application => mkd.emitApplication(a, stack)
          case t: Type        => mkd.emitType(t, stack)
          case s: State =>
            val maybeType = refMap.definitionOf[Type](s.typ.pathId, s)
            maybeType match {
              case Some(typ: AggregateTypeExpression) =>
                mkd.emitState(s, typ.fields, stack)
              case Some(_) =>
                mkd.emitState(s, Seq.empty[Field], stack)
              case None =>
                mkd.emitState(s, Seq.empty[Field], stack)
            }
          case h: Handler => mkd.emitHandler(h, parents)
          // These are all handled in emitHandler
          case f: Function => mkd.emitFunction(f, parents)
          case e: Entity   => mkd.emitEntity(e, parents)
          case c: Context =>
            val maybeDiagram = diagrams.contextDiagrams.get(c).map(data => ContextMapDiagram(c, data))
            mkd.emitContext(c, stack, maybeDiagram)
          case d: Domain =>
            val diagram = DomainMapDiagram(d)
            val summary_link: Option[String] = for {
              summary <- makeMessageSummary(d)
              fileName = summary.filePath.getFileName.toString.dropRight(3).toLowerCase
            } yield {
              makeDocLink(d) + "/" + fileName
            }
            mkd.emitDomain(d, parents, summary_link, diagram)

          case a: Adaptor    => mkd.emitAdaptor(a, parents)
          case s: Streamlet  => mkd.emitStreamlet(s, stack)
          case p: Projector  => mkd.emitProjector(p, parents)
          case r: Repository => mkd.emitRepository(r, parents)
          case s: Saga       => mkd.emitSaga(s, parents)
          case e: Epic       => mkd.emitEpic(e, stack)
          case uc: UseCase =>
            mkd.emitUseCase(uc, stack, this)

          case _: OnOtherClause | _: OnInitClause | _: OnMessageClause | _: OnTerminationClause | _: Author |
              _: Enumerator | _: Field | _: Method | _: Term | _: Constant | _: Invariant | _: Inlet | _: Outlet |
              _: Connector | _: SagaStep | _: User | _: Interaction | _: Root | _: Include[Definition] @unchecked |
              _: Output | _: Input | _: Group | _: ContainedGroup =>
          // All of these are handled above in their containers content contribution
        }
      case _: AST.NonDefinitionValues =>
      // These aren't definitions so don't count for documentation generation (no names)
    }
  }

  override def postProcess(root: AST.Root): Unit = {
    close(root)
  }

  override def result: HugoOutput = HugoOutput(messages.toMessages)

  private def deleteAll(directory: File): Boolean = {
    if !directory.isDirectory then false
    else
      Option(directory.listFiles) match {
        case Some(files) =>
          for file <- files do {
            deleteAll(file)
          }
          directory.delete
        case None =>
          false
      }
  }

  private def loadATheme(from: URL, destDir: Path): Unit = {
    val fileName = PathUtils.copyURLToDir(from, destDir)
    if fileName.nonEmpty then {
      val zip_path = destDir.resolve(fileName)
      if Files.isRegularFile(zip_path) then {
        fileName match {
          case name if name.endsWith(".zip") =>
            Zip.unzip(zip_path, destDir)
            zip_path.toFile.delete()
          case name if name.endsWith(".tar.gz") =>
            Tar.untar(zip_path, destDir)
            zip_path.toFile.delete()
          case _ =>
            require(false, "Can only load a theme from .tar.gz or .zip file")
        }
      } else {
        require(false, s"The downloaded theme is not a regular file: $zip_path")
      }
    }
  }

  private def loadThemes(options: HugoCommand.Options): Unit = {
    for (name, url) <- options.themes if url.nonEmpty do {
      val destDir = options.themesRoot.resolve(name)
      loadATheme(url.getOrElse(java.net.URI.create("").toURL), destDir)
    }
  }

  private def loadStaticAssets(
    inputPath: Option[Path],
    options: HugoCommand.Options
  ): Unit = {
    inputPath match {
      case Some(path) =>
        val inputRoot: Path = path.toAbsolutePath
        val sourceDir: Path = inputRoot.getParent.resolve("static")

        val targetDir = options.staticRoot
        if Files.exists(sourceDir) && Files.isDirectory(sourceDir) then {
          val img = sourceDir
            .resolve(options.siteLogoPath.getOrElse("images/logo.png"))
            .toAbsolutePath
          Files.createDirectories(img.getParent)
          if !Files.exists(img) then {
            copyResource(img, "hugo/static/images/RIDDL-Logo.ico")
          }
          // copy source to target using Files Class
          val visitor = TreeCopyFileVisitor(sourceDir, targetDir)
          Files.walkFileTree(sourceDir, visitor)
        }
      case None => ()
    }
  }

  private def copyResource(destination: Path, src: String = ""): Unit = {
    val name = if src.isEmpty then destination.getFileName.toString else src
    PathUtils.copyResource(name, destination)
  }

  private def manuallyMakeNewHugoSite(path: Path): Unit = {
    Files.createDirectories(path)
    Files.createDirectories(path.resolve("archetypes"))
    Files.createDirectories(path.resolve("content"))
    Files.createDirectories(path.resolve("public"))
    Files.createDirectories(path.resolve("data"))
    Files.createDirectories(path.resolve("layouts"))
    Files.createDirectories(path.resolve("public"))
    Files.createDirectories(path.resolve("static"))
    Files.createDirectories(path.resolve("themes"))
    val resourceDir = "hugo/"
    val resources = Seq(
      "archetypes/default.md",
      "layouts/partials/head/custom.html",
      "layouts/shortcodes/faq.html",
      "static/custom.css",
      "static/images/RIDDL-Logo.ico",
      "static/images/popup-link-icon.svg"
    )
    resources.foreach { resource =>
      val resourcePath = resourceDir + resource
      val destination =
        path.resolve(resource) // .replaceAll("/", File.pathSeparator))
      Files.createDirectories(destination.getParent)
      PathUtils.copyResource(resourcePath, destination)
    }
  }

  private def makeDirectoryStructure(
    inputPath: Option[Path]
  ): Unit = {
    val outDir = options.outputRoot.toFile
    if outDir.exists() then { if options.eraseOutput then { deleteAll(outDir) } }
    else { outDir.mkdirs() }

    val parent = outDir.getParentFile
    require(
      parent.isDirectory,
      "Parent of output directory is not a directory!"
    )
    if commonOptions.verbose then { println(s"Generating output to: $outDir") }
    manuallyMakeNewHugoSite(outDir.toPath)
    loadThemes(options)
    loadStaticAssets(inputPath, options)
  }

  private def makeIndex(root: Root): Unit = {
    Timer.time("Index Creation") {

      val mdw = addFile(Seq.empty[String], "_index.md")
      mdw.fileHead("Index", 10, Option("The main index to the content"))
      mdw.h2("Root Overview")
      val diagram = RootOverviewDiagram(root)
      mdw.emitMermaidDiagram(diagram.generate)
      mdw.h2("Domains")
      val domains = root.domains
        .sortBy(_.id.value)
        .map(d => s"[${d.id.value}](${d.id.value.toLowerCase}/)")
      mdw.list(domains)
      mdw.h2("Indices")
      val glossary =
        if options.withGlossary then { Seq("[Glossary](glossary)") }
        else { Seq.empty[String] }
      val todoList = {
        if options.withTODOList then { Seq("[To Do List](todolist)") }
        else { Seq.empty[String] }
      }
      val statistics = {
        if options.withStatistics then { Seq("[Statistics](statistics)") }
        else { Seq.empty[String] }
      }
      mdw.list(glossary ++ todoList ++ statistics)
      mdw.emitIndex("Full", root, Seq.empty[String])
    }
  }

  private val glossaryWeight = 970
  private val toDoWeight = 980
  private val statsWeight = 990

  private def makeStatistics(): Unit = {
    if options.withStatistics then {
      Timer.time("Make Statistics") {
        val mdw = addFile(Seq.empty[String], fileName = "statistics.md")
        mdw.emitStatistics(statsWeight)
      }
    }
  }

  private def makeGlossary(): Unit = {
    if options.withGlossary then {
      Timer.time("Make Glossary") {
        val mdw = addFile(Seq.empty[String], "glossary.md")
        outputs.outputOf[GlossaryOutput](GlossaryPass.name) match {
          case Some(go) =>
            mdw.emitGlossary(glossaryWeight, go.entries)
          case None =>
            mdw.emitGlossary(glossaryWeight, Seq.empty)
        }
      }
    }
  }

  private def makeToDoList(): Unit = {
    if options.withTODOList then
      Timer.time("Make ToDo List") {
        outputs.outputOf[ToDoListOutput](ToDoListPass.name) match {
          case Some(output) =>
            val mdw = addFile(Seq.empty[String], "todolist.md")
            mdw.emitToDoList(toDoWeight, output.collected)
          case None =>
          // do nothing
        }
      }
  }

  private def makeMessageSummary(forDomain: Domain): Option[MarkdownWriter] = {
    if options.withMessageSummary then {
      Timer.time("Make Messages Summary") {
        outputs.outputOf[MessageOutput](MessagesPass.name) match {
          case Some(mo) =>
            val fileName = s"${forDomain.id.value}-messages.md"
            val infos = mo.collected.filter(_.link.contains(forDomain.id.value.toLowerCase))
            val mdw = {
              addFile(Seq(forDomain.id.value), fileName)
            }
            mdw.emitMessageSummary(forDomain, infos)
            Some(mdw)
          case None =>
            // just skip
            None
        }
      }
    } else None
  }

  private def makeSystemLandscapeView: Seq[String] = {
    val rod = new RootOverviewDiagram(root)
    rod.generate
  }

  private def close(root: Root): Unit = {
    makeIndex(root)
    makeGlossary()
    makeToDoList()
    makeStatistics()
    Timer.time(s"Writing ${this.files.size} Files") {
      writeFiles(commonOptions.verbose || commonOptions.debug)
    }
  }

  private def writeConfigToml(
    options: HugoCommand.Options,
    author: Option[Author]
  ): Unit = {
    import java.nio.charset.StandardCharsets
    import java.nio.file.Files
    val content = configTemplate(options, author)
    val outFile = options.configFile
    Files.write(outFile, content.getBytes(StandardCharsets.UTF_8))
  }

  private def setUpContainer(
    c: Definition,
    stack: Seq[Definition]
  ): (MarkdownWriter, Seq[String]) = {
    addDir(c.id.format)
    val pars = makeStringParents(stack)
    addFile(pars :+ c.id.format, "_index.md") -> pars
  }

  private def setUpLeaf(
    d: Definition,
    stack: Seq[Definition]
  ): (MarkdownWriter, Seq[String]) = {
    val pars = makeStringParents(stack)
    addFile(pars, d.id.format + ".md") -> pars
  }

  // scalastyle:off method.length
  private def configTemplate(
    options: HugoCommand.Options,
    author: Option[Author]
  ): String = {
    val auth: Author = author.getOrElse(
      Author(
        1 -> 1,
        id = Identifier(1 -> 1, "unknown"),
        name = LiteralString(1 -> 1, "Not Provided"),
        email = LiteralString(1 -> 1, "somebody@somewere.tld")
      )
    )
    val themes: String = {
      options.themes.map(_._1).mkString("[ \"", "\", \"", "\" ]")
    }
    val baseURL: String = options.baseUrl
      .fold("https://example.prg/")(_.toString)
    val srcURL: String = options.sourceURL.fold("")(_.toString)
    val editPath: String = options.editPath.getOrElse("")
    val siteLogoPath: String = options.siteLogoPath.getOrElse("images/logo.png")
    val legalPath: String = "/legal"
    val privacyPath: String = "/privacy"
    val siteTitle = options.siteTitle.getOrElse("Unspecified Site Title")
    val siteName = options.projectName.getOrElse("Unspecified Project Name")
    val siteDescription = options.siteDescription
      .getOrElse("Unspecified Project Description")

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
       |    homepage = "${auth.url.getOrElse(java.net.URI.create("https://example.org/").toURL)}"
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
       |  # (Optional, default false) Enable or disable adding tags for post pages automatically to the
       |  # navigation sidebar.
       |  geekdocTagsToMenu = true
       |
       |  # (Optional, default 'title') Configure how to sort file-tree menu entries. Possible options are 'title',
       |  # 'linktitle', 'date', 'publishdate', 'expirydate' or 'lastmod'. Every option can be used with a reverse
       |  # modifier as well e.g. 'title_reverse'.
       |  geekdocFileTreeSortBy = "title"
       |
       |""".stripMargin
  }
}
