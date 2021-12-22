package com.yoppworks.ossum.riddl.generation.hugo.rendering

import com.yoppworks.ossum.riddl.generation.hugo._

sealed trait HugoContent {
  def outputFilePath: RelativePath
  def fileContents: String
}

final case class HugoTemplate(
  template: TemplateFile,
  outputFilePath: RelativePath,
  replacements: Map[String, String])
    extends HugoContent {
  def fileContents: String = template.replaceAll(replacements.toSeq: _*).toString
}

final case class TypesPlaceholder(relativePath: RelativePath) extends HugoContent {
  val outputFilePath = relativePath / "_index.md"
  def fileContents: String = PrinterEndo.empty.println("___").println("title: \"**Types**\"")
    .println("weight: -5").println("geekdocCollapseSection: true").println("---").toString
}

final case class ContextsPlaceholder(relativePath: RelativePath) extends HugoContent {
  val outputFilePath = relativePath / "_index.md"
  def fileContents: String = PrinterEndo.empty.println("---").println("title: \"**Contexts**\"")
    .println("weight: 0").println("---").toString
}
