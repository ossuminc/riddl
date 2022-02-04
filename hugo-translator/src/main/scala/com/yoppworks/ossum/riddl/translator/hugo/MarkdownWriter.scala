package com.yoppworks.ossum.riddl.translator.hugo

import com.yoppworks.ossum.riddl.language.AST
import com.yoppworks.ossum.riddl.language.AST._

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

  def fileHead(name: String, weight: Int, desc: Option[String]): this
    .type = {
    val headTemplate =
      s"""---
         |title: "$name"
         |weight: $weight
         |description: "${desc.getOrElse("")}"
         |---
         |""".stripMargin
    sb.append(headTemplate)
    this
  }

  def h1(heading: String): this.type = {
    sb.append(s"\n# $heading\n")
    this
  }

  def h2(heading: String): this.type = {
    sb.append(s"\n## $heading\n")
    this
  }

  def p(paragraph: String): this.type = {
    sb.append(paragraph)
    nl
    this
  }

  def title(title: String): this.type = {
    sb.append("\n<p style=\"text-align: center;\">\n")
      .append(s"# $title\n")
      .append("</p>\n")
    this
  }

  def italic(phrase: String): this.type = {
    sb.append(s"_${phrase}_")
    this
  }

  def bold(phrase: String): this.type = {
    sb.append(s"*$phrase*")
    this
  }

  def list[T](typeOfThing: String, items: Seq[T], emptyMessage: String): this.type = {
    h2(typeOfThing)
    if (items.isEmpty) {
      p(emptyMessage)
    } else {
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
    }
    this
  }

  def toc(kindOfThing: String, contents: Map[String, Seq[String]]): this.type = {
    val items = contents.map { case (label, partial) =>
      s"[$label]" -> partial.mkString("(", "/", ")")
    }.toSeq
    list[(String, String)](kindOfThing, items, s"No $kindOfThing")
  }

  private def mkTocMap(list: Seq[Definition], prefix: Seq[String]): Map[String, Seq[String]] = {
    list.map(c => (c.identify, prefix :+ c.id.format)).toMap
  }

  def emitBriefly(d: Definition with BrieflyDescribedValue, prefix: Seq[String]): this.type = {
    h2("Briefly")
    p(d.brief.map(_.s).getOrElse("Brief description missing.\n"))
    val path = (prefix :+ d.id.format).mkString(".")
    italic(s"Location").p(s": $path at ${d.loc}")
  }

  def emitDetails(d: Option[Description]): this.type = {
    h2("Details")
    val description = d.map(_.lines.map(_.s)).getOrElse(Seq("No description"))
    description.foreach(p)
    this
  }

  def emitOptions[OT <: OptionValue](options: Seq[OT]): this.type = {
    h2("Options")
    list("Options",
      options.map(_.format), "No Options"
    )
    this
  }

  def emitTypes(types: Seq[Type]): this.type = {
    list[(String, String)]("Types",
      types.map { t =>
        (t.id.format, AST.kind(t.typ) + t.description
          .map(_.lines.map(_.s))
          .getOrElse(Seq.empty[String])
          .mkString("\n  ", "\n  ", ""))
      }, emptyMessage = "No Types"
    )
  }

  def emitDomain(cont: Domain, prefix: Seq[String]): this.type = {
    fileHead(cont.id.format, 10,
      Some(cont.brief.map(_.s).getOrElse(cont.id.format + " has no brief description.")))
    title(cont.id.format)
    emitBriefly(cont, prefix)
    if (cont.author.nonEmpty) {
      val a = cont.author.get
      val items = Seq(
        "Name" -> a.name.s,
        "Email" -> a.email.s,
      ) ++ a.organization.map(ls => Seq("Organization" -> ls.s))
        .getOrElse(Seq.empty[(String, String)]) ++
        a.title.map(ls => Seq("Title" -> ls.s)).getOrElse(Seq.empty[(String, String)])
      list("Author", items, "")
    }

    emitTypes(cont.types)
    toc("Contexts", mkTocMap(cont.contexts, prefix))
    toc("Stories", mkTocMap(cont.stories, prefix))
    toc("Plants", mkTocMap(cont.plants, prefix))
    toc("Subdomains", mkTocMap(cont.domains, prefix))
    h2("Details")
    val description = cont.description.map(_.lines.map(_.s))
      .getOrElse(Seq("No description"))
    description.foreach(p)
    this
  }

  def emitContext(cont: Context, prefix: Seq[String]): this.type = {
    fileHead(cont.id.format, weight = 10,
      Some(cont.brief.map(_.s).getOrElse(cont.id.format + " has no brief description.")))
    title(cont.id.format)
    emitBriefly(cont, prefix)
    emitOptions(cont.options)
    emitTypes(cont.types)
    toc("Adaptors", mkTocMap(cont.adaptors, prefix))
    toc("Entities", mkTocMap(cont.entities, prefix))
    toc("Functions", mkTocMap(cont.functions, prefix))
    toc("Sagas", mkTocMap(cont.sagas, prefix))
    emitDetails(cont.description)
    this
  }

}

