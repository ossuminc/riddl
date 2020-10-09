package com.yoppworks.ossum.riddl.translator.hugo

import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.AST

import scala.collection.mutable

/** File writing utilities for Hugo Formatter */
case class HugoFile(
  container: AST.Container,
  path: Path,
  var indentLevel: Int = 0,
  lines: StringBuilder = new mutable.StringBuilder()) {

  private final val q = "\""
  private final val nl = "\n"

  def spc: String = { " ".repeat(indentLevel) }

  def add(str: String): this.type = {
    lines.append(s"$str")
    this
  }

  def add(strings: Seq[AST.LiteralString]): this.type = {
    if (strings.length > 1) {
      lines.append("\n")
      strings.foreach(s => lines.append(s"""$spc"${s.s}"$nl"""))
    } else { strings.foreach(s => lines.append(s""" "${s.s}" """)) }
    this
  }

  def add[T](opt: Option[T])(map: T => String): this.type = {
    opt match {
      case None => this
      case Some(t) =>
        lines.append(map(t))
        this
    }
  }

  def addIndent(): this.type = {
    lines.append(s"$spc")
    this
  }

  def addIndent(str: String): this.type = {
    lines.append(s"$spc$str")
    this
  }

  def addLine(str: String): this.type = {
    lines.append(s"$spc$str\n")
    this
  }

  def open(str: String): this.type = {
    lines.append(s"$spc$str\n")
    this.indent
  }

  /*
  def close(definition: AST.Definition): this.type = {
    this.outdent.add(s"$spc}").visitDescription(definition.description).add(nl)
  }
   */

  def indent: this.type = {
    require(indentLevel < 80, "runaway indents")
    indentLevel = indentLevel + 2
    this
  }

  def outdent: this.type = {
    require(indentLevel > 1, "unmatched indents")
    indentLevel = indentLevel - 2
    this
  }

  def close(): Unit = {
    import java.nio.file.Files
    import java.nio.charset.StandardCharsets
    Files.write(path, lines.result().getBytes(StandardCharsets.UTF_8))
  }

}
