package com.ossuminc.riddl.hugo.themes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{CommonOptions, Messages}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.*
import com.ossuminc.riddl.passes.symbols.*
import com.ossuminc.riddl.analyses.*
import com.ossuminc.riddl.hugo.HugoCommand
import com.ossuminc.riddl.hugo.diagrams.mermaid.UseCaseDiagramSupport

import java.nio.file.Path
import scala.reflect.ClassTag

trait ThemeGenerator extends UseCaseDiagramSupport {

  def input: PassInput

  def options: HugoCommand.Options

  def outputs: PassesOutput

  val messages: Messages.Accumulator
  lazy val inputFile: Option[Path] = options.inputFile
  lazy val commonOptions: CommonOptions = input.commonOptions
  lazy val refMap: ReferenceMap = outputs.outputOf[ResolutionOutput](ResolutionPass.name).get.refMap

  lazy val symbolsOutput: SymbolsOutput = outputs.symbols
  lazy val usage: Usages = outputs.usage
  lazy val diagrams: DiagramsPassOutput =
    outputs.outputOf[DiagramsPassOutput](DiagramsPass.name).getOrElse(DiagramsPassOutput())
  lazy val passesResult: PassesResult = PassesResult(input, outputs)

  def makeDocLink(definition: NamedValue, parents: Seq[String]): String

  def makeDocAndParentsLinks(definition: NamedValue): String
  
  def makeSourceLink(definition: Definition): String

  def makeTomlFile(options: HugoCommand.Options, author: Option[Author]): String

  protected def makeParents(parents: Symbols.Parents): Seq[Definition] = {
    // The stack goes from most nested to highest. We don't want to change the
    // stack (its mutable) so we copy it to a Seq first, then reverse it, then
    // drop all the root containers (file includes) to finally end up at a domin
    // and then map to just the name of that domain.
    parents.reverse.dropWhile(_.isRootContainer)
  }

  def makeStringParents(parents: Symbols.Parents): Seq[String] = {
    makeParents(parents).map(_.id.format)
  }

  def makeDocLink(definition: NamedValue): String = {
    val parents = makeStringParents(outputs.symbols.parentsOf(definition))
    makeDocLink(definition, parents)
  }

  /** Generate a string that is the file path portion of a url including the line number.
    */
  def makeFilePath(definition: Definition): Option[String] = {
    definition.loc.source.path.map(_.toString)
  }

  def makeFullName(definition: Definition): String = {
    val defs = outputs.symbols.parentsOf(definition).reverse :+ definition
    defs.map(_.id.format).mkString(".")
  }

  def makeBreadCrumbs(parents: Symbols.Parents): String = {
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
  def apply(options: HugoCommand.Options, inputs: PassInput, outputs: PassesOutput, messages: Messages.Accumulator): ThemeGenerator = {
    options.hugoThemeName match {
      case None                            => GeekDocGenerator(options, inputs, outputs, messages)
      case Some(GeekDocWriter.name) | None => GeekDocGenerator(options, inputs, outputs, messages)
      case Some(DotdockWriter.name)         => DotdockGenerator(options, inputs, outputs, messages)
      case Some(s) =>
        messages.addWarning((0, 0), s"Hugo theme named '$s' is not supported, using GeekDoc ")
        GeekDocGenerator(options, inputs, outputs, messages)
    }
  }
}
