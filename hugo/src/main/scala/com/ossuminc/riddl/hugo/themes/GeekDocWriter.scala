package com.ossuminc.riddl.hugo.themes

import com.ossuminc.riddl.hugo.HugoPass
import com.ossuminc.riddl.hugo.writers.MarkdownWriter
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{CommonOptions, Messages}
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}

import java.nio.file.Path
import java.net.URL

object GeekDocWriter {
  val name: String = "GeekDoc"
}

/** Theme extension to the MardownWriter for the Hugo GeekDoc theme */
case class GeekDocWriter(
  filePath: Path,
  input: PassInput,
  outputs: PassesOutput,
  options: HugoPass.Options,
  commonOptions: CommonOptions
) extends MarkdownWriter {

  val generator: ThemeGenerator = ThemeGenerator(options, input, outputs, messages)

  private val geekDoc_version = "v0.44.1"
  private val geekDoc_file = "hugo-geekdoc.tar.gz"
  val geekDoc_url: URL =
    java.net.URI
      .create(
        s"https://github.com/thegeeklab/hugo-geekdoc/releases/download/$geekDoc_version/$geekDoc_file"
      )
      .toURL

  def themeName: String = GeekDocWriter.name

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

  def containerHead(cont: Parent): Unit = {
    val brief: String = 
      cont match
        case p: Parent if p.contents.filter[BriefDescription].nonEmpty => 
          p.contents.filter[BriefDescription].foldLeft("")((x,y) => x + y.brief.s)
        case d: WithDescriptives => d.briefString 
        case _ => cont.id.format + " has no brief description"
      end match
    fileHead(
      cont.id.format,
      containerWeight,
      Some(brief),
      Map(
        "geekdocCollapseSection" -> "true"
        // FIXME: "geekdocFilePath" -> s"${generator.makeFilePath(cont).getOrElse("no-such-file")}"
      )
    )
  }

  def leafHead(definition: LeafDefinition, weight: Int): Unit = {
    fileHead(
      s"${definition.id.format}: ${definition.getClass.getSimpleName}",
      weight,
      Option(definition.briefString)
    )
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

  def codeBlock(items: Seq[Statements]): Unit = {
    if items.nonEmpty then
      sb.append(s"```")
      sb.append(items.map(_.format).mkString(new_line, new_line, new_line))
      sb.append(s"```$new_line")
    else
      mono("No statements defined.")
  }

  def processorIndex(processor: Processor[?]): Unit = {
    if processor.authorRefs.nonEmpty then toc("Authors", processor.authorRefs.map(_.format))
    h2("Index")
    p("{{< toc-tree >}}")
  }
}
