package com.ossuminc.riddl.hugo.themes

import com.ossuminc.riddl.hugo.writers.MarkdownWriter
import com.ossuminc.riddl.hugo.{HugoCommand, PassUtilities}
import com.ossuminc.riddl.language.{AST, CommonOptions}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}
import com.ossuminc.riddl.passes.resolve.{ReferenceMap, Usages}
import com.ossuminc.riddl.passes.symbols.SymbolsOutput

import java.nio.file.Path

object DotdockTheme {
  val name = "DotDock"
}

/** Theme extensions to the Markdown writer for the Dotdock Hugo theme
  *
  * @param filePath
  *   Path to the file being written
  * @param input: PassInput
  *   The input to the passes run
  * @param outputs: PassOutput
  *  The outputs from the pass runs
  * @param options
  *  The HugoCommand options
  */
case class DotdockTheme(
  filePath: Path,
  input: PassInput,
  outputs: PassesOutput,
  options: HugoCommand.Options
) extends MarkdownWriter {
  final val name: String = DotdockTheme.name

  def themeName: String = DotdockTheme.name

  // Members declared in com.ossuminc.riddl.hugo.themes.DotdockTheme 
  def fileHead(
    title: String,
    weight: Int,
    desc: Option[String],
    extras: Map[String, String] = Map.empty[String, String]
  ): Unit = ???

  def containerHead(cont: AST.Definition, titleSuffix: String): Unit = ???

  def leafHead(definition: Definition, weight: Int): Unit = ???

  def codeBlock(items: Seq[Statement]): Unit = ???

  def notAvailable(thing: String, title: String = "Unavailable"): Unit = ???

  def emitMermaidDiagram(lines: Seq[String]): Unit = ???
  
  def processorIndex(processor: Processor[?,?]): Unit = ???


  // Members declared in com.ossuminc.riddl.hugo.themes.ThemeWriter 
  
  def emitHeaderPage: Unit = {
    // Create a _header.md page in content folder. Its content is what you get in the logo placeholder (top left of the screen).
  }

  def emitConfigMenuItems(): Unit = {
    // You can define additional menu entries in the navigation menu without any link to content.
    //
    // Edit the website configuration config.toml and add a [[menu.shortcuts]] entry for each link your want to add.
    //
    // Example from the current website, note the pre param which allows you to insert HTML code and used here to separate content’s menu from this “static” menu
    // [[menu.shortcuts]]
    // pre = "<h3>More</h3>"
    // name = "<i class='fa fa-github'></i> Github repo"
    // identifier = "ds"
    // url = "https://github.com/vjeantet/hugo-theme-docdock"
    // weight = 1
    //
    // [[menu.shortcuts]]
    // name = "<i class='fa fa-bookmark'></i> Hugo Documentation"
    // identifier = "hugodoc"
    // url = "https://gohugo.io/"
    // weight = 2
  }
}
