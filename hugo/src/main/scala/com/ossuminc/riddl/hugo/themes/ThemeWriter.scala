package com.ossuminc.riddl.hugo.themes

import com.ossuminc.riddl.hugo.HugoPass
import com.ossuminc.riddl.hugo.writers.MarkdownWriter
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{CommonOptions, Messages}
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}

import java.nio.file.Path

trait ThemeWriter {
  this: MarkdownWriter =>

  def themeName: String

  protected val messages: Messages.Accumulator = Messages.Accumulator.empty

  def fileHead(
    title: String,
    weight: Int,
    desc: Option[String],
    extras: Map[String, String] = Map.empty[String, String]
  ): Unit

  def containerHead(cont: Definition): Unit

  def leafHead(definition: Definition, weight: Int): Unit

  def codeBlock(items: Seq[Statement]): Unit

  def notAvailable(thing: String, title: String = "Unavailable"): Unit

  def emitMermaidDiagram(lines: Seq[String]): Unit
}

object ThemeWriter {
  def apply(
    path: Path,
    input: PassInput,
    outputs: PassesOutput,
    options: HugoPass.Options,
    commonOptions: CommonOptions
  ): MarkdownWriter = {
    options.hugoThemeName match {
      case None                            => GeekDocWriter(path, input, outputs, options, commonOptions)
      case Some(GeekDocWriter.name) | None => GeekDocWriter(path, input, outputs, options, commonOptions)
      case Some(DotdockWriter.name)        => DotdockWriter(path, input, outputs, options, commonOptions)
      case Some(s)                         => GeekDocWriter(path, input, outputs, options, commonOptions)
    }
  }
}
