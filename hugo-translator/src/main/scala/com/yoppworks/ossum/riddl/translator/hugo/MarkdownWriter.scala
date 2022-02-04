package com.yoppworks.ossum.riddl.translator.hugo

import com.yoppworks.ossum.riddl.language.AST.{Container, Definition}

import java.io.PrintWriter
import java.nio.file.Path
import scala.collection.mutable

case class MarkdownWriter(filePath: Path) {

  private val sb: StringBuilder = new mutable.StringBuilder()

  override def toString: String = sb.toString

  private def mkDirs(): Unit = {
    val dirFile = filePath.getParent.toFile
    if (!dirFile.exists) {dirFile.mkdirs()}
  }

  def write(): Unit = {
    mkDirs()
    val printer = new PrintWriter(filePath.toFile)
    try {
      printer.write(sb.toString())
      printer.flush()
    } finally {printer.close()}
    sb.clear() // release memory because content written to file
  }

  def nl: this.type = {sb.append("\n"); this}

  def fileHead(name: String, weight: Option[Int], desc: Option[String]): this
    .type = {
    val headTemplate =
      s"""---
         |title: "$name"
         |weight: ${weight.getOrElse("0")}
         |description: "${desc.getOrElse("")}"
         |---
         |""".stripMargin
    sb.append(headTemplate);
    this
  }

  def h1(heading: String): this.type = {
    sb.append(s"# $heading\n");
    this
  }

  def h2(heading: String): this.type = {
    sb.append(s"## $heading\n");
    this
  }

  def p(paragraph: String): this.type = {
    sb.append(paragraph);
    nl;
    this
  }

  def title(title: String): this.type = {
    sb.append("<p style=\"text-align: center;\">\n")
      .append(s"# $title\n")
      .append("</p>\n")
    this
  }

  def list[T](items: Seq[T]): this.type = {
    for {item <- items} {
      item match {
        case (prefix: String, body: String) =>
          if (prefix.startsWith("[") && body.startsWith("(")) {
            sb.append(s"* $prefix$body\n")
          } else {
            sb.append(s"* _${prefix}_: $body\n")
          }
        case body: String =>
          sb.append(s"* $body\n")
        case x: Any =>
          sb.append(s"* ${x.toString}\n")
      }
    }
    this
  }

  def italic(phrase: String): this.type = {
    sb.append(s"_${phrase}_");
    this
  }

  def bold(phrase: String): this.type = {
    sb.append(s"*$phrase*");
    this
  }

  def toc(contents: Map[String, Seq[String]]): this.type = {
    val items = contents.map { case (label, partial) =>
      s"[$label]" -> partial.mkString("(", "/", ")")
    }.toSeq
    list[(String, String)](items)
  }

  def emitContainer(cont: Container[Definition], prefix: Seq[String]): this.type = {
    val contents = cont.contents.map(d => (d.identify, prefix :+ d.id.format))
    fileHead(cont.id.format, None,
      Some(cont.brief.map(_.s).getOrElse(cont.id.format + " has no brief description.")))
      .title(cont.id.format)
      .h2("Briefly")
      .p(cont.brief.map(_.s).getOrElse("Brief description missing.\n"))
      .h2("Contents")
      .toc(contents.toMap)
      .h2("Description")
    val description = cont.description.map(_.lines.map(_.s))
      .getOrElse(Seq("No description"))
    description.foreach(p)
    this
  }
}

