/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.hugo

import com.reactific.riddl.commands.TranslatingState
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.{AST, CommonOptions}
import com.reactific.riddl.language.parsing.FileParserInput
import com.reactific.riddl.passes.PassesResult
import com.reactific.riddl.passes.resolve.ReferenceMap
import com.reactific.riddl.passes.symbols.SymbolsOutput
import com.reactific.riddl.passes.Finder
import com.reactific.riddl.utils.{Logger, SysLogger}

import java.nio.file.Path

/** The processing state for the Hugo Translator
  * @param root
  *   RootContainer that was parsed
  * @param symbolTable
  *   A symbolTable for the names of things
  * @param options
  *   The options specific to Hugo Translator
  * @param commonOptions
  *   The common options all commands use
  */
case class HugoTranslatorState(
  result: PassesResult,
  options: HugoCommand.Options = HugoCommand.Options(),
  commonOptions: CommonOptions = CommonOptions(),
  logger: Logger = SysLogger()
)
  extends TranslatingState[MarkdownWriter] {

  final val symbolTable: SymbolsOutput = result.symbols
  final val refMap: ReferenceMap = result.refMap

  final val root: RootContainer = result.root // base class compliance

  def addFile(parents: Seq[String], fileName: String): MarkdownWriter = {
    val parDir = parents.foldLeft(options.contentRoot) { (next, par) =>
      next.resolve(par)
    }
    val path = parDir.resolve(fileName)
    val mdw = MarkdownWriter(path, this)
    addFile(mdw)
    mdw
  }

  var terms = Seq.empty[GlossaryEntry]

  def addToGlossary(
    d: Definition,
    stack: Seq[Definition]
  ): HugoTranslatorState = {
    if options.withGlossary then {
      val parents = makeParents(stack)
      val entry = GlossaryEntry(
        d.id.value,
        d.kind,
        d.brief.map(_.s).getOrElse("-- undefined --"),
        parents :+ d.id.value,
        makeDocLink(d, parents),
        makeSourceLink(d)
      )
      terms = terms :+ entry
    }
    this
  }

  def makeFullName(definition: Definition): String = {
    val defs = symbolTable.parentsOf(definition).reverse :+ definition
    defs.map(_.id.format).mkString(".")
  }

  def makeParents(stack: Seq[Definition]): Seq[String] = {
    // The stack goes from most nested to highest. We don't want to change the
    // stack (its mutable) so we copy it to a Seq first, then reverse it, then
    // drop all the root containers (file includes) to finally end up at a domin
    // and then map to just the name of that domain.
    stack.reverse.dropWhile(_.isRootContainer).map(_.id.format)
  }

  def pathRelativeToRepo(path: Path): Option[String] = {
    options.inputFile match {
      case Some(inFile) =>
        val pathAsString = path.toAbsolutePath.toString
        val inDirAsString = inFile.getParent.toAbsolutePath.toString
        if pathAsString.startsWith(inDirAsString) then {
          val result = pathAsString.drop(inDirAsString.length + 1)
          Some(result)
        } else { Option.empty[String] }
      case None => Option.empty[String]
    }
  }

  /** Generate a string that is the file path portion of a url including the
    * line number.
    */
  def makeFilePath(definition: Definition): Option[String] = {
    definition.loc.source match {
      case FileParserInput(file) =>
        pathRelativeToRepo(file.getAbsoluteFile.toPath)
      case _ => Option.empty[String]
    }
  }

  /** Generate a string that contains the name of a definition that is markdown
    * linked to the definition in its source. For example, given sourceURL
    * option of https://github.com/a/b and for an editPath option of
    * src/main/riddl and for a Location that has Org/org.riddl at line 30, we
    * would generate this URL:
    * `https://github.com/a/b/blob/main/src/main/riddl/Org/org.riddl#L30` Note
    * that that this works through recursive path identifiers to find the first
    * type that is not a reference Note: this only works for github sources
    * @param definition
    *   The definition for which we want the link
    * @return
    *   a string that gives the source link for the definition
    */
  def makeSourceLink(
    definition: Definition
  ): String = {
    options.sourceURL match {
      case Some(url) => options.viewPath match {
          case Some(viewPath) => makeFilePath(definition) match {
              case Some(filePath) =>
                val lineNo = definition.loc.line
                url.toExternalForm ++ "/" ++ Path.of(viewPath, filePath)
                  .toString ++ s"#L$lineNo"
              case _ => ""
            }
          case None => ""
        }
      case None => ""
    }
  }
  def makeDocLink(definition: Definition): String = {
    val parents = makeParents(symbolTable.parentsOf(definition))
    makeDocLink(definition, parents)
  }

  def makeDocAndParentsLinks(definition: Definition): String = {
    val parents = symbolTable.parentsOf(definition)
    val docLink = makeDocLink(definition, makeParents(parents))
    if parents.isEmpty then { s"[${definition.identify}]($docLink)" }
    else {
      val parent = parents.head
      val parentLink = makeDocLink(parent, makeParents(parents.tail))
      s"[${definition.identify}]($docLink) in [${parent.identify}]($parentLink)"
    }
  }

  def makeDocLink(definition: Definition, parents: Seq[String]): String = {
    val pars = ("/" + parents.mkString("/")).toLowerCase
    val result = definition match {
      case _: OnMessageClause | _: OnInitClause | _: OnTerminationClause | _: OnOtherClause|
           _: Inlet | _: Outlet => 
        pars + "#" + definition.id.value.toLowerCase
      case _: Field | _: Enumerator | _: Invariant | _: Author | _: SagaStep |
          _: Include[Definition] @unchecked | _: RootContainer | _: Term => pars
      case _ =>
        if parents.isEmpty then pars + definition.id.value.toLowerCase
        else pars + "/" + definition.id.value.toLowerCase
    }
    // deal with Geekdoc's url processor
    result.replace(" ", "-")
  }

  def makeIndex(root: RootContainer): Unit = {
    val mdw = addFile(Seq.empty[String], "_index.md")
    mdw.fileHead("Index", 10, Option("The main index to the content"))
    makeSystemLandscapeView match {
      case Some(view) =>
        mdw.h2("Landscape View")
        mdw.emitMermaidDiagram(view.split(System.lineSeparator()).toIndexedSeq)
      case None => // nothing
    }
    mdw.h2("Domains")
    val domains = root.contents.sortBy(_.id.value)
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

  val glossaryWeight = 970
  val toDoWeight = 980
  val statsWeight = 990

  def makeStatistics(): Unit = {
    if options.withStatistics then {
      val mdw = addFile(Seq.empty[String], fileName = "statistics.md")
      mdw.emitStatistics(statsWeight, result.root)
    }
  }

  def makeGlossary(): Unit = {
    if options.withGlossary then {
      val mdw = addFile(Seq.empty[String], "glossary.md")
      mdw.emitGlossary(glossaryWeight, terms)
    }
  }

  def makeToDoList(root: RootContainer): Unit = {
    if options.withTODOList then {
      val finder = Finder(root)
      val items: Seq[(String, String, String, String)] = for
        (defn, pars) <- finder.findEmpty
        item = defn.identify
        authors = AST.findAuthors(defn, pars)
        author =
          if authors.isEmpty then { "Unspecified Author" }
          else {
            authors
              .map { (ref: AuthorRef) => refMap.definitionOf[Author](ref
                .pathId, pars.head) }
              .filterNot(_.isEmpty).map(_.get)
              .map(x => s"${x.name.s} &lt;${x.email.s}&gt;").mkString(", ")
          }
        parents = makeParents(pars)
        path = parents.mkString(".")
        link = makeDocLink(defn, parents)
      yield { (item, author, path, link) }

      val map = items.groupBy(_._2).view.mapValues(_.map {
        case (item, _, path, link) => s"[$item In $path]($link)"
      }).toMap
      val mdw = addFile(Seq.empty[String], "todolist.md")
      mdw.emitToDoList(toDoWeight, map)
    }
  }

  def close(root: RootContainer): Seq[Path] = {
    makeIndex(root)
    makeGlossary()
    makeToDoList(root)
    makeStatistics()
    files.foreach(_.write())
    files.map(_.filePath).toSeq
  }

  def makeSystemLandscapeView: Option[String] = {
    val mdp = new MermaidDiagramsPlugin
    val diagram = mdp.makeRootOverview(root)
    Some(diagram)
  }
}
