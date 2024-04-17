package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.hugo.writers.{MarkdownWriter, ThemeWriter}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils.TextFileWriter

trait MarkdownBasics extends TextFileWriter with ThemeWriter { this: MarkdownWriter =>

  protected val containerWeight: Int = 2 * 5

  protected def tbd(definition: Definition): Unit = {
    if definition.isEmpty then {
      p("TBD: To Be Defined")
    }
  }

  def heading(heading: String, level: Int = 2): this.type = {
    level match {
      case 1 => h1(heading)
      case 2 => h2(heading)
      case 3 => h3(heading)
      case 4 => h4(heading)
      case 5 => h5(heading)
      case 6 => h6(heading)
      case _ => h2(heading)
    }
  }

  def h1(heading: String): this.type = {
    sb.append(s"\n# ${bold(heading)}\n")
    this
  }

  def h2(heading: String): this.type = {
    sb.append(s"\n## ${bold(heading)}\n")
    this
  }

  def h3(heading: String): this.type = {
    sb.append(s"\n### ${italic(heading)}\n")
    this
  }

  def h4(heading: String): this.type = {
    sb.append(s"\n#### $heading\n")
    this
  }

  def h5(heading: String): this.type = {
    sb.append(s"\n##### $heading\n")
    this
  }

  def h6(heading: String): this.type = {
    sb.append(s"\n###### $heading\n")
    this
  }

  def p(paragraph: String): this.type = {
    sb.append(paragraph)
    nl
    this
  }

  def italic(phrase: String): String = {
    s"_${phrase}_"
  }

  def bold(phrase: String): String = {
    s"*$phrase*"
  }

  def mono(phrase: String): String = {
    s"`$phrase`"
  }

  def list[T](items: Seq[T]): Unit = {
    def emitPair(prefix: String, body: String): Unit = {
      if prefix.startsWith("[") && body.startsWith("(") then {
        sb.append(s"* $prefix$body\n")
      } else {
        sb.append(s"* ${italic(prefix)}: $body\n")
      }
    }

    for item <- items do {
      item match {
        case body: String     => sb.append(s"* $body\n")
        case rval: RiddlValue => sb.append(s"* ${rval.format}")
        case (
              prefix: String,
              description: String,
              sublist: Seq[String] @unchecked,
              _: Option[Description] @unchecked
            ) =>
          emitPair(prefix, description)
          sublist.foreach(s => sb.append(s"    * $s\n"))
        case (
              prefix: String,
              definition: String,
              description: Option[Description] @unchecked
            ) =>
          emitPair(prefix, definition)
          listDesc(description, true, 4)
        case (
              prefix: String,
              definition: String,
              briefly: Option[LiteralString] @unchecked,
              description: Option[Description] @unchecked
            ) =>
          emitPair(
            prefix,
            definition ++ " - " ++ briefly.map(_.s).getOrElse("{no brief}")
          )
          listDesc(description, true, 4)
        case (prefix: String, body: String) => emitPair(prefix, body)
        case (prefix: String, docBlock: Seq[String] @unchecked) =>
          sb.append(s"* $prefix\n")
          docBlock.foreach(s => sb.append(s"    * $s\n"))
        case x: Any => sb.append(s"* ${x.toString}\n")
      }
    }
  }

  def list[T](typeOfThing: String, items: Seq[T], level: Int = 2): Unit = {
    if items.nonEmpty then {
      heading(typeOfThing, level)
      list(items)
    }
  }

  def listOf[T <: NamedValue](
    kind: String,
    items: Seq[T],
    level: Int = 2
  ): Unit = {
    heading(kind, level)
    val refs = items.map { definition =>
      makeDocAndParentsLinks(definition)
    }
    list(refs)
  }

  def listDesc(maybeDescription: Option[Description], isListItem: Boolean, indent: Int): Unit = {
    maybeDescription match
      case None => ()
      case Some(description) =>
        val ndnt = " ".repeat(indent)
        val listItem = {
          if isListItem then "* " else ""
        }
        sb.append(description.lines.map(line => s"$ndnt$listItem${line.s}\n"))
  }
  
  def definitionToc(kindOfThing: String, list:Seq[Definition], level:Int = 2): Unit = {
    val strList = list.map(c => c.id.value)
    toc(kindOfThing, strList, level)
  }

  def toc(
    kindOfThing: String,
    contents: Seq[String],
    level: Int = 2
  ): Unit = {
    if contents.nonEmpty then {
      val items = contents.map { name =>
        s"[$name]" -> s"(${name.toLowerCase})"
      }
      list[(String, String)](kindOfThing, items, level)
    }
  }

  def emitTableHead(columnTitles: Seq[(String, Char)]): Unit = {
    sb.append(columnTitles.map(_._1).mkString("| ", " | ", " |\n"))
    val dashes = columnTitles.map { case (s, c) =>
      c match {
        case 'C' => ":---:" ++ " ".repeat(Math.max(s.length - 5, 0))
        case 'L' => ":---" ++ " ".repeat(Math.max(s.length - 4, 0))
        case 'R' => " ".repeat(Math.max(s.length - 4, 0)) ++ "---:"
      }
    }
    sb.append(dashes.mkString("| ", " | ", " |\n"))
  }

  def emitTableRow(firstCol: String, remainingCols: String*): Unit = {
    val row = firstCol +: remainingCols
    sb.append(row.mkString("| ", " | ", " |\n"))
  }

}
