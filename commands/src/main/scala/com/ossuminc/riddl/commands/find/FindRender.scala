/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.find

import com.ossuminc.riddl.commands.project.{ProjectedNode, ProjectionPass}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At

/** Renders a match for each read-only action. */
object FindRender {

  /** `file:line:col`.
    *
    * Built by hand rather than with `At.format`, which renders `file(line:col->line:col)` — the
    * error-message shape, not the colon form editors and `grep -n` consumers parse.
    *
    * **`At.line`/`col` return 0, not >= 1, when positions are unknown** (a BAST-reconstructed
    * input). Rendering `file:0:0` would look like a bug in every tool downstream, so the file alone
    * is emitted in that case.
    */
  def location(n: ProjectedNode): String = {
    val loc = n.value.loc
    val file = n.value.declaringFile.getOrElse(loc.source.origin)
    if loc.line == 0 then file else s"$file:${loc.line}:${loc.col}"
  }

  /** The identity a reader keys on: the dotted path, or — for a node that has none, such as a
    * statement — its location plus kind, which is the only stable way to name it.
    */
  def identity(n: ProjectedNode): String =
    n.record.value.get("path") match
      case Some(s: ujson.Str) => s.str
      case _                  => s"${location(n)} ${ProjectionPass.kindOf(n.value)}"

  /** `-print`: location, then the source line the node begins on. */
  def print(n: ProjectedNode): String = {
    val loc = n.value.loc
    val src = sourceLine(loc)
    if src.isEmpty then location(n) else s"${location(n)}: $src"
  }

  private def sourceLine(loc: At): String =
    if loc.isEmpty || !loc.source.positionsKnown then ""
    else
      try
        // `lineRangeOf` spans the node's FIRST line to its LAST, so for a definition it returns the
        // whole body. `-print` wants the one line the node begins on, as grep -n would show.
        val (start, end) = loc.source.lineRangeOf(loc)
        val text = loc.source.data.substring(start, end)
        text.linesIterator.nextOption().getOrElse("").trim
      catch case _: Exception => ""

  def printf(n: ProjectedNode, fmt: String): String = {
    val loc = n.value.loc
    val sb = new StringBuilder
    var i = 0
    while i < fmt.length do
      fmt.charAt(i) match
        case '%' if i + 1 < fmt.length =>
          fmt.charAt(i + 1) match
            case 'p' => sb.append(identity(n)); i += 2
            case 'k' => sb.append(ProjectionPass.kindOf(n.value)); i += 2
            case 'n' => sb.append(str(n, "id")); i += 2
            case 'f' => sb.append(n.value.declaringFile.getOrElse("")); i += 2
            case 'l' => sb.append(loc.line.toString); i += 2
            case 'c' => sb.append(loc.col.toString); i += 2
            case 's' => sb.append(sizeInLines(n).toString); i += 2
            case 'a' => sb.append(attrs(n)); i += 2
            case '%' => sb.append('%'); i += 2
            case c   => sb.append('%').append(c); i += 2
        case '\\' if i + 1 < fmt.length =>
          fmt.charAt(i + 1) match
            case 'n' => sb.append('\n'); i += 2
            case 't' => sb.append('\t'); i += 2
            case c   => sb.append(c); i += 2
        case c => sb.append(c); i += 1
      end match
    end while
    sb.toString
  }

  /** The `-list` table: kind, identity, size, attrs, location — `ls -l`'s shape, with kind standing
    * in for file type and contents-size for byte size.
    */
  def table(matches: Seq[ProjectedNode]): Seq[String] = {
    if matches.isEmpty then Nil
    else
      val rows = matches.map { n =>
        Seq(
          ProjectionPass.kindOf(n.value),
          identity(n),
          s"${sizeInLines(n)} ln",
          attrs(n),
          location(n)
        )
      }
      val headers = Seq("KIND", "PATH", "SIZE", "ATTRS", "WHERE")
      // Widths are computed, so `f"..."` is unusable here — its width must be a literal. Padding by
      // hand also keeps the LAST column unpadded, which matters: a trailing run of spaces on every
      // line is invisible in a terminal and very visible in a diff.
      val widths = headers.indices.map { i =>
        (headers(i) +: rows.map(_(i))).map(_.length).max
      }
      def line(cells: Seq[String]): String =
        cells.zipWithIndex
          .map { case (c, i) => if i == cells.length - 1 then c else c.padTo(widths(i), ' ') }
          .mkString("  ")
      line(headers) +: rows.map(line)
  }

  private def str(n: ProjectedNode, key: String): String =
    n.record.value.get(key).collect { case s: ujson.Str => s.str }.getOrElse("")

  private def sizeInLines(n: ProjectedNode): Int = {
    val loc = n.value.loc
    if loc.line == 0 then 0 else math.max(1, loc.endLine - loc.line + 1)
  }

  /** The compact attribute field. Carries the declared modifiers, the derived shape, a `stub`
    * marker, and — for a node that is not a definition — what it acts on, so a statement row means
    * something rather than showing a bare kind.
    */
  private def attrs(n: ProjectedNode): String = {
    val b = scala.collection.mutable.ListBuffer.empty[String]
    n.record.value.get("intentions").foreach {
      case a: ujson.Arr => a.arr.foreach { case s: ujson.Str => b.append(s.str); case _ => () }
      case _            => ()
    }
    n.record.value.get("intention").foreach {
      case s: ujson.Str => b.append(s.str)
      case _            => ()
    }
    n.record.value.get("options").foreach {
      case a: ujson.Arr => a.arr.foreach { case s: ujson.Str => b.append(s.str); case _ => () }
      case _            => ()
    }
    n.value match
      case _: Processor[?] =>
        val shape = str(n, "shape")
        if shape.nonEmpty && !b.contains(shape) then b.append(shape)
      case _ => ()
    if n.record.value.get("empty").exists(_ == ujson.Bool(true)) then b.append("stub")
    // A statement's target, or a field's type: the fact that makes the row self-describing.
    n.record.value.get("target").foreach {
      case o: ujson.Obj =>
        o.value.get("resolved").collect { case s: ujson.Str => b.append(s"→ ${s.str}") }
        o.value.get("value").collect { case s: ujson.Str => b.append(s"→ ${s.str}") }
      case _ => ()
    }
    n.value match
      case _: Field => b.append(str(n, "type"))
      case _        => ()
    b.filter(_.nonEmpty).mkString(",")
  }
}
