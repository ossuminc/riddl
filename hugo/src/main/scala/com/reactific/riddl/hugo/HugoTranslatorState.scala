/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.hugo

import com.reactific.riddl.commands.TranslatingState
import com.reactific.riddl.diagrams.mermaid.{MermaidDiagramsPlugin, SequenceDiagramSupport}
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.Accumulator
import com.reactific.riddl.language.{AST, CommonOptions}
import com.reactific.riddl.language.parsing.FileParserInput
import com.reactific.riddl.passes.{Finder, PassesOutput, PassesResult}
import com.reactific.riddl.passes.resolve.{ReferenceMap, Usages}
import com.reactific.riddl.passes.symbols.SymbolsOutput
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
@SuppressWarnings(Array("org.wartremover.warts.Var"))
case class HugoTranslatorState(
  passesResult: PassesResult,
  options: HugoCommand.Options = HugoCommand.Options(),
  commonOptions: CommonOptions = CommonOptions(),
  logger: Logger = SysLogger()
) extends TranslatingState[MarkdownWriter] with PassUtilities with SequenceDiagramSupport {
  final def symbolTable: SymbolsOutput = passesResult.symbols
  final def refMap: ReferenceMap = passesResult.refMap
  final def root: RootContainer = passesResult.root // base class compliance
  final def usage: Usages = passesResult.usage
  final def outputs: PassesOutput = passesResult.outputs
  final val messages: Accumulator = Accumulator(commonOptions)

  def addFile(parents: Seq[String], fileName: String): MarkdownWriter = {
    val parDir = parents.foldLeft(options.contentRoot) { (next, par) =>
      next.resolve(par)
    }
    val path = parDir.resolve(fileName)
    val mdw = MarkdownWriter(path, this)
    addFile(mdw)
    mdw
  }

  def makeLinkFor(definition: Definition): String = makeDocLink(definition)

  def makeDocAndParentsLinks(definition: Definition): String = {
    val parents = symbolTable.parentsOf(definition)
    val docLink = makeDocLink(definition, makeParents(parents))
    if parents.isEmpty then { s"[${definition.identify}]($docLink)" }
    else {
      parents.headOption match
        case None =>
          logger.error(s"No parents found for definition '${definition.identify}")
          ""
        case Some(parent: Definition) =>
          val parentLink = makeDocLink(parent, makeParents(parents.drop(1)))
          s"[${definition.identify}]($docLink) in [${parent.identify}]($parentLink)"
    }
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
    val domains = root.contents
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

  val glossaryWeight = 970
  val toDoWeight = 980
  val statsWeight = 990

  def makeStatistics(): Unit = {
    if options.withStatistics then {
      val mdw = addFile(Seq.empty[String], fileName = "statistics.md")
      mdw.emitStatistics(statsWeight)
    }
  }

  def makeGlossary(): Unit = {
    if options.withGlossary then {
      val mdw = addFile(Seq.empty[String], "glossary.md")
      passesResult.outputs.outputOf[GlossaryOutput](GlossaryPass.name) match {
        case Some(go) =>
          mdw.emitGlossary(glossaryWeight, go.entries)
        case None =>
          mdw.emitGlossary(glossaryWeight, Seq.empty)
      }
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  private def mkAuthor(authors: Seq[AuthorRef], parents: Seq[Definition]): String = {
    if authors.isEmpty then "Unspecified Author"
    else
      parents.headOption match {
        case None => "Unspecified Author"
        case Some(parent: Definition) =>
          authors
            .map { (ref: AuthorRef) =>
              refMap.definitionOf[Author](ref.pathId, parent)
            }
            .filterNot(_.isEmpty)
            .map(_.get)
            .map(x => s"${x.name.s} &lt;${x.email.s}&gt;")
            .mkString(", ")
      }
  }

  def makeToDoList(root: RootContainer): Unit = {
    if options.withTODOList then
      val finder: Finder = Finder(root)
      val items: Seq[(String, String, String, String)] = {
        for {
          (defn: Definition, pars: Seq[Definition]) <- finder.findEmpty
          item = defn.identify
          authors = AST.findAuthors(defn, pars)
          author = mkAuthor(authors, pars)
          parents = makeParents(pars)
          path = parents.mkString(".")
          link = makeDocLink(defn, parents)
        } yield (item, author, path, link)
      }

      val map = items
        .groupBy(_._2)
        .view
        .mapValues(_.map { case (item, _, path, link) =>
          s"[$item In $path]($link)"
        })
        .toMap
      val mdw = addFile(Seq.empty[String], "todolist.md")
      mdw.emitToDoList(toDoWeight, map)
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
