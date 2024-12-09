/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo.themes

import com.ossuminc.riddl.commands.hugo.HugoPass
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.*
import com.ossuminc.riddl.passes.symbols.*
import com.ossuminc.riddl.diagrams.mermaid.*
import com.ossuminc.riddl.passes.diagrams.{DiagramsPass, DiagramsPassOutput}
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext}

import java.nio.file.Path

trait ThemeGenerator(using pc: PlatformContext) extends UseCaseDiagramSupport {

  def input: PassInput

  def options: HugoPass.Options

  def outputs: PassesOutput

  val messages: Messages.Accumulator
  lazy val inputFile: Option[Path] = options.inputFile
  lazy val commonOptions: CommonOptions = pc.options
  lazy val refMap: ReferenceMap = outputs.outputOf[ResolutionOutput](ResolutionPass.name).get.refMap

  lazy val symbolsOutput: SymbolsOutput = outputs.symbols
  lazy val usage: Usages = outputs.usage
  lazy val diagrams: DiagramsPassOutput =
    outputs.outputOf[DiagramsPassOutput](DiagramsPass.name).getOrElse(DiagramsPassOutput())
  lazy val passesResult: PassesResult = PassesResult(input, outputs)

  def makeDocLink(definition: Definition, parents: Seq[String]): String

  def makeDocAndParentsLinks(definition: Definition): String

  def makeSourceLink(definition: Definition): String

  def makeTomlFile(options: HugoPass.Options, author: Option[Author]): String

  protected def makeParents(parents: Parents): Seq[Definition] = {
    // The stack goes from most nested to highest. We don't want to change the
    // stack (its mutable) so we copy it to a Seq first, then reverse it, then
    // drop all the root containers (file includes) to finally end up at a domin
    // and then map to just the name of that domain.
    parents.reverse.dropWhile(_.isRootContainer)
  }

  def makeStringParents(parents: Parents): Seq[String] = {
    makeParents(parents).map(_.id.format)
  }

  def makeDocLink(definition: Definition): String = {
    val parents = makeStringParents(outputs.symbols.parentsOf(definition))
    makeDocLink(definition, parents)
  }

  /** Generate a string that is the file path portion of a url including the line number.
    */
  def makeFilePath(definition: Definition): Option[String] = {
    Some(definition.loc.source.root.toExternalForm)
  }

  def makeFullName(definition: Definition): String = {
    val defs = outputs.symbols.parentsOf(definition).reverse :+ definition
    defs.map(_.id.format).mkString(".")
  }

  def makeBreadCrumbs(parents: Parents): String = {
    parents
      .map { defn =>
        val link = makeDocLink(defn, parents.map(_.id.value))
        s"[${defn.id.value}]($link)"
      }
      .mkString("/")
  }

  def pathRelativeToRepo(path: Path): Option[String] = {
    options.inputFile match {
      case Some(inFile) =>
        val pathAsString = path.toAbsolutePath.toString
        options.withInputFile { inputFile =>
          Right(inputFile.getParent.toAbsolutePath.toString)
        } match {
          case Right(inDirAsString) =>
            if pathAsString.startsWith(inDirAsString) then {
              val result = pathAsString.drop(inDirAsString.length + 1)
              Some(result)
            } else {
              Option.empty[String]
            }
          case Left(messages) =>
            messages.appendedAll(messages)
            Option.empty[String]
        }
      case None => Option.empty[String]
    }
  }
}

object ThemeGenerator {
  def apply(
    options: HugoPass.Options,
    inputs: PassInput,
    outputs: PassesOutput,
    messages: Messages.Accumulator
  )(using PlatformContext): ThemeGenerator = {
    options.hugoThemeName match {
      case None                            => GeekDocGenerator(options, inputs, outputs, messages)
      case Some(GeekDocWriter.name) | None => GeekDocGenerator(options, inputs, outputs, messages)
      case Some(DotdockWriter.name)        => DotdockGenerator(options, inputs, outputs, messages)
      case Some(s) =>
        messages.addWarning((0, 0), s"Hugo theme named '$s' is not supported, using GeekDoc ")
        GeekDocGenerator(options, inputs, outputs, messages)
    }
  }
}
