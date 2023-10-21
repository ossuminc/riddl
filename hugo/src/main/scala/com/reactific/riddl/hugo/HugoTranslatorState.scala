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
  * @param passesResult
  *   The result of running the passes
  * @param options
  *   The options specific to Hugo Translator
  * @param commonOptions
  *   The common options all commands use
  * @param logger
  *   The logger to log messages to
  */
@SuppressWarnings(Array("org.wartremover.warts.Var"))
case class HugoTranslatorState(
  passesResult: PassesResult,
  options: HugoCommand.Options = HugoCommand.Options(),
  commonOptions: CommonOptions = CommonOptions(),
  logger: Logger = SysLogger()
) extends TranslatingState[MarkdownWriter]
    with PassUtilities
    with SequenceDiagramSupport {
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
  val messagesWeight = 975

  private def makeStatistics(): Unit = {
    if options.withStatistics then {
      val mdw = addFile(Seq.empty[String], fileName = "statistics.md")
      mdw.emitStatistics(statsWeight)
    }
  }

  private def makeGlossary(): Unit = {
    if options.withGlossary then {
      val mdw = addFile(Seq.empty[String], "glossary.md")
      outputs.outputOf[GlossaryOutput](GlossaryPass.name) match {
        case Some(go) =>
          mdw.emitGlossary(glossaryWeight, go.entries)
        case None =>
          mdw.emitGlossary(glossaryWeight, Seq.empty)
      }
    }
  }


  def makeToDoList(root: RootContainer): Unit = {
    if options.withTODOList then
      outputs.outputOf[ToDoListOutput](ToDoListPass.name) match {
        case Some(tdlo) =>
          val mdw = addFile(Seq.empty[String], "todolist.md")
          mdw.emitToDoList(toDoWeight, tdlo.map)
        case None =>
          // do nothing
      }
  }

  def emitMessageSummary(weight: Int, forDomain: Domain): Unit = {
    if options.withMessageSummary then {
      outputs.outputOf[MessageOutput](MessagesPass.name) match {
        case Some(mo) =>
          for {
            messageInfo <- mo.collected.filter(_.definedIn.contains(forDomain))
            fname = forDomain.id.value + "-" + messageInfo.message.id.value + ".md"
            parents = messageInfo.definedIn.dropWhile(_ != forDomain.id.value).dropRight(1).drop(1) if parents.nonEmpty
          } do {
            val mdw = addFile(parents, fname)
            mdw.emitMessageSummary(weight, forDomain, mo.collected)
          }
        case None =>
        // just skip
      }
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
