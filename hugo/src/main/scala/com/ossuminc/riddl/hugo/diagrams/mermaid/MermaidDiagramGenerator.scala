package com.ossuminc.riddl.hugo.diagrams.mermaid

import com.ossuminc.riddl.utils.FileBuilder
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.KnownOption.*

/** Common trait for things that generate mermaid diagrams */
trait MermaidDiagramGenerator extends FileBuilder {

  def generate: Seq[String] = toLines

  def title: String

  def kind: String

  protected def frontMatterItems: Map[String, String]

  final protected def frontMatter(): Unit = {
    addLine("---")
    addLine(s"title: $title")
    addLine("init:")
    addLine("    theme: dark")
    addLine(s"$kind:")
    append(frontMatterItems.map(x => x._1 + ": " + x._2).mkString("    ", "\n    ", "\n"))
    addLine("---\n")
  }

  protected def getCssFor(definition: VitalDefinition[?]): String = {
    val maybeStrings: Option[Seq[String]] = definition.getOptionValue(css).map(ov => ov.args.map(_.s))
    maybeStrings.map(_.mkString(",")).getOrElse("")
  }

  protected def getIconFor(definition: VitalDefinition[?]): String = {
    val maybeStrings: Option[Seq[String]] = definition.getOptionValue(faicon).map(ov => ov.args.map(_.s))
    maybeStrings.map(_.mkString(",")).getOrElse("")
  }

  protected def getTechnology(definition: VitalDefinition[?]): String = {
    val maybeStrings: Option[Seq[String]] = definition.getOptionValue(technology).map(ov => ov.args.map(_.s))
    maybeStrings.map(_.mkString(", ")).getOrElse("Arbitrary Technology")
  }
}
