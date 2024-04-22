/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.passes.translate.TranslatingState
import com.ossuminc.riddl.hugo.diagrams.mermaid.*
import com.ossuminc.riddl.hugo.utils.TreeCopyFileVisitor
import com.ossuminc.riddl.language.*
import com.ossuminc.riddl.language.AST.{Include, *}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.{Pass, PassCreator, PassInfo, PassInput, PassOutput, PassesOutput}
import com.ossuminc.riddl.passes.resolve.ResolutionPass
import com.ossuminc.riddl.passes.symbols.SymbolsPass
import com.ossuminc.riddl.passes.symbols.Symbols.ParentStack
import com.ossuminc.riddl.passes.validate.ValidationPass
import com.ossuminc.riddl.analyses.StatsPass
import com.ossuminc.riddl.utils.{PathUtils, Tar, Timer, Zip}
import com.ossuminc.riddl.hugo.themes.{ThemeGenerator, ThemeWriter}
import com.ossuminc.riddl.hugo.writers.MarkdownWriter
import diagrams.mermaid.RootOverviewDiagram

import java.io.File
import java.net.URL
import java.nio.file.*
import scala.collection.mutable
import scala.reflect.ClassTag

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
  options: HugoCommand.Options,
  commonOptions: CommonOptions = CommonOptions()
) extends Pass(input, outputs)
    with TranslatingState[MarkdownWriter]
    with Summarizer {

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

  protected val generator = ThemeGenerator(options, input, outputs, messages)

  options.inputFile match {
    case Some(inFile) =>
      if Files.exists(inFile) then makeDirectoryStructure(options.inputFile)
      else messages.addError((0, 0), "The input-file does not exist")
    case None =>
      messages.addWarning((0, 0), "The input-file option was not provided")
  }

  private val maybeAuthor = root.authors.headOption.orElse { root.domains.headOption.flatMap(_.authors.headOption) }
  writeConfigToml(options, maybeAuthor)

  def makeWriter(parents: Seq[String], fileName: String): MarkdownWriter = {
    val parDir = parents.foldLeft(options.contentRoot) { (next, par) =>
      next.resolve(par)
    }
    val path = parDir.resolve(fileName)
    val mdw: MarkdownWriter = ThemeWriter(path, input, outputs, options, commonOptions)
    addFile(mdw)
    mdw
  }

  override def process(value: AST.RiddlValue, parents: ParentStack): Unit = {
    val stack = parents.toSeq
    value match {
      // We only process containers here since they start their own
      // documentation section. Everything else is a leaf or a detail
      // on the container's index page.
      case container: VitalDefinition[?] =>
        
        // Create the writer for this container
        val mkd: MarkdownWriter = setUpContainer(container, stack)

        container match { // match the processors
          case a: Adaptor     => mkd.emitAdaptor(a, stack)
          case a: Application => mkd.emitApplication(a, stack)
          case c: Context     => mkd.emitContext(c, stack)
          case d: Domain      => mkd.emitDomain(d, stack)
          case e: Entity      => mkd.emitEntity(e, stack)
          case e: Epic        => mkd.emitEpic(e, stack)
          case f: Function    => mkd.emitFunction(f, stack)
          case p: Projector   => mkd.emitProjector(p, stack)
          case r: Repository  => mkd.emitRepository(r, stack)
          case s: Saga        => mkd.emitSaga(s, stack)
          case s: Streamlet   => mkd.emitStreamlet(s, stack)
        }

      case u: UseCase     => setUpContainer(u, stack).emitUseCase(u, stack)
      case c: Connector   => setUpContainer(c, stack).emitConnector(c, stack)

      // ignore the non-processors
      case _: Function | _: Handler | _: State | _: OnOtherClause | _: OnInitClause | _: OnMessageClause |
          _: OnTerminationClause | _: Author | _: Enumerator | _: Field | _: Method | _: Term | _: Constant |
          _: Invariant | _: Inlet | _: Outlet | _: SagaStep | _: User | _: Interaction | _: Root |
          _: Include[Definition] @unchecked | _: Output | _: Input | _: Group | _: ContainedGroup | _: Type =>
        ()
      // All of these are handled above in their containers content output
      case _: AST.NonDefinitionValues => ()
      // These aren't definitions so don't count for documentation generation (no names)
    }
  }

  override def postProcess(root: AST.Root): Unit = {
    summarize()
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

  private def makeSystemLandscapeView: Seq[String] = {
    val rod = new RootOverviewDiagram(root)
    rod.generate
  }

  private def close(root: Root): Unit = {
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
    val content = generator.makeTomlFile(options, author)
    val outFile = options.configFile
    Files.write(outFile, content.getBytes(StandardCharsets.UTF_8))
  }

  private def setUpContainer(
    c: Definition,
    stack: Seq[Definition]
  ): MarkdownWriter = {
    addDir(c.id.format)
    val pars = generator.makeStringParents(stack)
    makeWriter(pars :+ c.id.format, "_index.md")
  }

  private def setUpLeaf(
    d: Definition,
    stack: Seq[Definition]
  ): MarkdownWriter = {
    val pars = generator.makeStringParents(stack)
    makeWriter(pars, d.id.format + ".md")
  }
}
