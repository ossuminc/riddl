package com.yoppworks.ossum.riddl.translator.hugo

import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.AST

import scala.collection.mutable

/** File writing utilities for Hugo Formatter */
case class HugoFile(
  container: AST.Definition,
  path: Path,
  var indentLevel: Int = 0,
  lines: StringBuilder = new mutable.StringBuilder()) {

  def spc: String = { " ".repeat(indentLevel) }

  def add(str: String): HugoFile = {
    lines.append(s"$str")
    this
  }

  def add(strings: Seq[AST.LiteralString]): HugoFile = {
    strings.foreach(s => lines.append(s.s))
    this
  }

  def add[T](opt: Option[T])(map: T => String): HugoFile = {
    opt match {
      case None => this
      case Some(t) =>
        lines.append(map(t))
        this
    }
  }

  def addIndent(): HugoFile = {
    lines.append(s"$spc")
    this
  }

  def addIndent(str: String): HugoFile = {
    lines.append(s"$spc$str")
    this
  }

  def addLine(str: String): HugoFile = {
    lines.append(s"$spc$str\n")
    this
  }

  def addList(strings: Seq[String]): HugoFile = {
    strings.foreach(str => lines.append("* " + str))
    this
  }

  def open(str: String): HugoFile = {
    lines.append(s"$spc$str\n")
    this.indent
  }

  def close(): HugoFile = {
    this.outdent
    lines.append("$spc")
    this
  }

  def indent: HugoFile = {
    require(indentLevel < 80, "runaway indents")
    indentLevel = indentLevel + 2
    this
  }

  def outdent: HugoFile = {
    require(indentLevel > 1, "unmatched indents")
    indentLevel = indentLevel - 2
    this
  }

  def write(): Unit = {
    import java.nio.file.Files
    import java.nio.charset.StandardCharsets
    Files.write(path, lines.result().getBytes(StandardCharsets.UTF_8))
  }

  def emitDescription(maybeDescription: Option[AST.Description]): HugoFile = {
    maybeDescription.map { description =>
      add(description.brief)
      addLine("## Details")
      add(description.details)
      if (description.nameOfItems.nonEmpty) {
        addLine(s"## ${description.nameOfItems.get}")
        for { (head, body) <- description.items } {
          addLine(s"### $head")
          addList(body.map(_.s))
        }
      }
      addLine("## See Also")
      addList(description.citations.map(_.s))
    }
    this
  }

  def emitString(s: AST.Strng): HugoFile = {
    (s.min, s.max) match {
      case (Some(n), Some(x)) => add(s"String($n,$x")
      case (None, Some(x))    => add(s"String(,$x")
      case (Some(n), None)    => add(s"String($n)")
      case (None, None)       => add(s"String")
    }
  }

  def visitTypeRef(typeRef: AST.TypeRef): HugoFile = { this.add(s" is type ${typeRef.id.value}") }

  def emitEnumeration(enumeration: AST.Enumeration): HugoFile = {
    this.add(s"any of {\n")
    enumeration.of.foldLeft(this) { (f, e) =>
      f.add(e.id.value)
      e.typeRef.map(visitTypeRef).getOrElse(f)
    }.emitDescription(enumeration.description)
  }

  def emitAlternation(alternation: AST.Alternation): HugoFile = {
    val s = this.add(s"one of {\n").visitTypeExpr(alternation.of.head)
    alternation.of.tail.foldLeft(s) { (s, te) => s.add(" or ").visitTypeExpr(te) }
      .emitDescription(alternation.description)
  }

  def emitAggregation(aggregation: AST.Aggregation): HugoFile = {
    val of = aggregation.fields
    if (of.isEmpty) { this.add("{}") }
    else if (of.size == 1) {
      val f: AST.Field = of.head
      this.add(s"{ ${f.id.value}: ").visitTypeExpr(f.typeEx).add(" ").emitDescription(f.description)
    } else {
      this.add("{\n")
      val result = of.foldLeft(this) { case (s, f) =>
        s.add(s"$spc  ${f.id.value}: ").visitTypeExpr(f.typeEx).add(" ")
          .emitDescription(f.description).add(",")
      }
      result.lines.deleteCharAt(result.lines.length - 1)
      result.emitDescription(aggregation.description)
    }
  }

  def emitMapping(mapping: AST.Mapping): HugoFile = {
    this.add(s"mapping from ").visitTypeExpr(mapping.from).add(" to ").visitTypeExpr(mapping.to)
      .emitDescription(mapping.description)
  }

  def emitPattern(pattern: AST.Pattern): HugoFile = {
    val line =
      if (pattern.pattern.size == 1) { "Pattern(\"" + pattern.pattern.head.s + "\"" + s") " }
      else {
        s"Pattern(\n" + pattern.pattern.map(l => spc + "  \"" + l.s + "\"\n")
        s"\n) "
      }
    this.add(line).emitDescription(pattern.description)
  }

  def visitTypeExpr(typEx: AST.TypeExpression): HugoFile = {
    typEx match {
      case string: AST.Strng  => emitString(string)
      case AST.Bool(_)        => this.add("Boolean")
      case AST.Number(_)      => this.add("Number")
      case AST.Integer(_)     => this.add("Integer")
      case AST.Decimal(_)     => this.add("Decimal")
      case AST.Date(_)        => this.add("Date")
      case AST.Time(_)        => this.add("Time")
      case AST.DateTime(_)    => this.add("DateTime")
      case AST.TimeStamp(_)   => this.add("TimeStamp")
      case AST.LatLong(_)     => this.add("LatLong")
      case AST.Nothing(_)     => this.add("Nothing")
      case AST.TypeRef(_, id) => this.add(id.value.mkString("."))
      case AST.URL(_, scheme) => this.add(s"URL${scheme.map(s => "\"" + s.s + "\"").getOrElse("")}")
      case enumeration: AST.Enumeration => emitEnumeration(enumeration)
      case alternation: AST.Alternation => emitAlternation(alternation)
      case aggregation: AST.Aggregation => emitAggregation(aggregation)
      case mapping: AST.Mapping         => emitMapping(mapping)
      case AST.RangeType(_, min, max, desc) => this.add(s"range from $min to $max ")
          .emitDescription(desc)
      case AST.ReferenceType(_, id, desc) => this.add(s"reference to $id").emitDescription(desc)
      case pattern: AST.Pattern           => emitPattern(pattern)
      case AST.UniqueId(_, id, desc)      => this.add(s"Id(${id.value}) ").emitDescription(desc)
      case AST.Optional(_, aType)         => this.visitTypeExpr(aType).add("?")
      case AST.ZeroOrMore(_, aType)       => this.visitTypeExpr(aType).add("*")
      case AST.OneOrMore(_, aType)        => this.visitTypeExpr(aType).add("+")
      case x: AST.TypeExpression =>
        require(requirement = false, s"Unknown type $x")
        this
    }
  }
}
