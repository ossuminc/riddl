package com.ossuminc.riddl.hugo.themes

import com.ossuminc.riddl.hugo.HugoCommand
import com.ossuminc.riddl.hugo.writers.MarkdownWriter
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}

import java.nio.file.Path
import java.net.URL

object GeekDocTheme {
  val name: String = "GeekDoc"
}

/** Theme extension to the MardownWriter for the Hugo GeekDoc theme */
case class GeekDocTheme(
  filePath: Path,
  input: PassInput,
  outputs: PassesOutput,
  options: HugoCommand.Options
) extends MarkdownWriter {

  def themeName: String = GeekDocTheme.name

  private val geekDoc_version = "v0.44.1"
  private val geekDoc_file = "hugo-geekdoc.tar.gz"
  val geekDoc_url: URL =
    java.net.URI
      .create(
        s"https://github.com/thegeeklab/hugo-geekdoc/releases/download/$geekDoc_version/$geekDoc_file"
      )
      .toURL

  def fileHead(
    title: String,
    weight: Int,
    desc: Option[String],
    extras: Map[String, String] = Map.empty[String, String]
  ): Unit = {
    val adds: String = extras
      .map { case (k: String, v: String) => s"$k: $v" }
      .mkString("\n")
    val headTemplate =
      s"""---
         |title: "$title"
         |weight: $weight
         |draft: "false"
         |description: "${desc.getOrElse("")}"
         |geekdocAnchor: true
         |geekdocToC: 4
         |$adds
         |---
         |""".stripMargin
    sb.append(headTemplate)
  }

  def containerHead(cont: Definition, titleSuffix: String): Unit = {

    fileHead(
      cont.id.format + s": $titleSuffix",
      containerWeight,
      Option(
        cont.brief.fold(cont.id.format + " has no brief description.")(_.s)
      ),
      Map(
        "geekdocCollapseSection" -> "true",
        "geekdocFilePath" ->
          s"${makeFilePath(cont).getOrElse("no-such-file")}"
      )
    )
  }

  def leafHead(definition: Definition, weight: Int): Unit = {
    fileHead(
      s"${definition.id.format}: ${definition.getClass.getSimpleName}",
      weight,
      Option(
        definition.brief
          .fold(definition.id.format + " has no brief description.")(_.s)
      )
    )
    tbd(definition)
  }

  def notAvailable(thing: String, title: String = "Unavailable"): Unit = {
    sb.append(s"\n{{< hint type=note icon=gdoc_error_outline title=\"$title\" >}}\n")
    sb.append(thing)
    sb.append("{{< /hint >}}")
  }

  def emitMermaidDiagram(lines: Seq[String]): Unit = {
    p("{{< mermaid class=\"text-center\">}}")
    lines.foreach(p)
    p("{{< /mermaid >}}")
    if commonOptions.debug then {
      p("```")
      lines.foreach(p)
      p("```")
    }
  }

  def codeBlock(items: Seq[Statement]): Unit = {
    if items.nonEmpty then {
      sb.append(s"```")
      sb.append(items.map(_.format).mkString(new_line, new_line, new_line))
      sb.append(s"```$new_line")
    }
  }

  def processorIndex(processor: Processor[?, ?]): Unit = {
    if processor.authorRefs.nonEmpty then toc("Authors", processor.authorRefs.map(_.format))
    h2("Index")
    p("{{< toc-tree >}}")
  }
}
