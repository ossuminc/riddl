package com.ossuminc.riddl.hugo.themes

import com.ossuminc.riddl.hugo.writers.MarkdownWriter
import com.ossuminc.riddl.hugo.HugoPass
import com.ossuminc.riddl.language.{AST, CommonOptions}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}

import java.nio.file.Path

object DotdockWriter {
  val name = "DotDock"
}

/** Theme extensions to the Markdown writer for the Dotdock Hugo theme
  *
  * @param filePath
  *   Path to the file being written
  * @param input:
  *   PassInput The input to the passes run
  * @param outputs:
  *   PassOutput The outputs from the pass runs
  * @param options
  *   The HugoCommand options
  */
case class DotdockWriter(
  filePath: Path,
  input: PassInput,
  outputs: PassesOutput,
  options: HugoPass.Options,
  commonOptions: CommonOptions
) extends MarkdownWriter {
  final val name: String = DotdockWriter.name

  lazy val generator: ThemeGenerator = ThemeGenerator(options, input, outputs, messages)

  def themeName: String = DotdockWriter.name

  // Members declared in com.ossuminc.riddl.hugo.themes.DotdockTheme
  def fileHead(
    title: String,
    weight: Int,
    desc: Option[String],
    extras: Map[String, String] = Map.empty[String, String]
  ): Unit = ???

  def containerHead(cont: AST.Definition): Unit = ???

  def leafHead(definition: Definition, weight: Int): Unit = ???

  def codeBlock(items: Seq[Statements]): Unit = ???

  def notAvailable(thing: String, title: String = "Unavailable"): Unit = ???

  def emitMermaidDiagram(lines: Seq[String]): Unit = ???

  def processorIndex(processor: Processor[?]): Unit = ???

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
