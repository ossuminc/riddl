package com.yoppworks.riddl.translator.hugo

import java.nio.file.Path
import scala.collection.mutable

case class MarkdownWriter(filePath: Path) {

  private val sb: StringBuilder = new mutable.StringBuilder()

  def nl: this.type = {sb.append("\n"); this}

  def fileHead(name: String, weight: Option[Int], desc: Option[String],): this
    .type = {
    val headTemplate =
      s"""---
         |title: "$name"
         |type: "${typ.getOrElse("page")}"
         |weight: ${weight.getOrElse("0")}
         |description
         |---
         |""".stripMargin
    sb.append(headTemplate)
    this
  }

  def h1(heading: String): this.type = {
    sb.append(s"# $heading")
    this
  }

  def h2(heading: String): this.type = {
    sb.append(s"## $heading")
    this
  }

  def list[T](items: Seq[T]): this.type = {
    for {item <- items} {
      item match {
        case (prefix: String, body: String) =>
          sb.append(s"* _${prefix}_: $body\n")
        case body: String =>
          sb.append(s"* $body")
        case x: Any =>
          sb.append(s"* ${x.toString}")
      }
    }
    this
  }
}

