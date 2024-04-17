package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}

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

  def containerHead(cont: Definition, titleSuffix: String): Unit

  def leafHead(definition: Definition, weight: Int): Unit

  def codeBlock(items: Seq[Statement]): Unit

  def notAvailable(thing: String, title: String = "Unavailable"): Unit

  def emitMermaidDiagram(lines: Seq[String]): Unit

  def makeDocLink(definition: NamedValue, parents: Seq[String]): String

  def makeDocAndParentsLinks(definition: NamedValue): String

}
