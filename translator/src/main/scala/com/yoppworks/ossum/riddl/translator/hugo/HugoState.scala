package com.yoppworks.ossum.riddl.translator.hugo

import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.AST
import com.yoppworks.ossum.riddl.language.Folding

import scala.collection.mutable

/** Translation state for the Hugo Translator */
case class HugoState(
  config: HugoConfig,
  root: AST.RootContainer,
  contextStack: mutable.Stack[AST.Container] = mutable.Stack[AST.Container](),
  generatedFiles: mutable.ListBuffer[HugoFile] = mutable.ListBuffer.empty)
    extends Folding.State[HugoState] {

  def step(f: HugoState => HugoState): HugoState = f(this)

  def pushContext(container: AST.Container): Unit = {
    contextStack.push(container)
  }

  def popContext(): Unit = { contextStack.pop() }

  def openFile(name: String): HugoFile = {
    val hFile = HugoFile(current, contextPath.resolve(name))
    generatedFiles.append(hFile)
    hFile
  }

  def current: AST.Container = contextStack.head

  def contextPath: Path = {
    contextStack.foldRight(config.basePath) { case (elem, base) =>
      base.resolve(elem.id.value)
    }
  }

  /*
  def visitDescription(description: Option[AST.Description]): this.type = {
    description.foldLeft(this) { (state, desc: AST.Description) =>
      state.step { s: HugoState =>
        val s = current
        s.add(" described as {\n").indent.addIndent("brief {").indent
          .add(desc.brief).outdent.add("}\n").addLine(s"details {").indent
      }.step { s: HugoState =>
        desc.details.foldLeft(s) { case (s, line) =>
          current.add(current.spc + "|" + line.s + "\n")
        }.outdent.addLine(s"}\n${current.spc}items")
          .add[AST.LiteralString](desc.nameOfItems)(ls =>
            "(\"" + ls.s + "\") {"
          ).indent
      }.step { s: HugoState =>
        desc.items.foldLeft(s) { case (s, (id, desc)) =>
          current.addLine(s"$id: " + q + desc + q)
        }.outdent.add(s"$spc}\n").indent
      }.step { s: HugoState =>
        desc.citations.foldLeft(s) { case (s, cite) =>
          s.add(s"${spc}see " + q + cite.s + q + nl)
        }.outdent.add(s"$spc}\n")
      }
    }
  }

  def emitString(s: AST.Strng): this.type = {
    (s.min, s.max) match {
      case (Some(n), Some(x)) => this.add(s"String($n,$x")
      case (None, Some(x))    => this.add(s"String(,$x")
      case (Some(n), None)    => this.add(s"String($n)")
      case (None, None)       => this.add(s"String")
    }
  }

  def emitEnumeration(enumeration: AST.Enumeration): this.type = {
    this.add(s"any of {\n")
    enumeration.of.foldLeft(this) { (s, e) =>
      s.add(e.id.value)
      e.typeRef.map(visitTypeRef).getOrElse(s)
    }.visitDescription(enumeration.description)
  }

  def emitAlternation(alternation: AST.Alternation): this.type = {
    val s = this.add(s"one of {\n").visitTypeExpr(alternation.of.head)
    alternation.of.tail.foldLeft(s) { (s, te) =>
      s.add(" or ").visitTypeExpr(te)
    }.visitDescription(alternation.description)
  }

  def emitAggregation(aggregation: AST.Aggregation): this.type = {
    val of = aggregation.fields
    if (of.isEmpty) { this.add("{}") }
    else if (of.size == 1) {
      val f: AST.Field = of.head
      this.add(s"{ ${f.id.value}: ").visitTypeExpr(f.typeEx).add(" ")
        .visitDescription(f.description)
    } else {
      this.add("{\n")
      val result = of.foldLeft(this) { case (s, f) =>
        s.add(s"$spc  ${f.id.value}: ").visitTypeExpr(f.typeEx).add(" ")
          .visitDescription(f.description).add(",")
      }
      result.lines.deleteCharAt(result.lines.length - 1)
      result.visitDescription(aggregation.description)
    }
  }

  def emitMapping(mapping: AST.Mapping): this.type = {
    this.add(s"mapping from ").visitTypeExpr(mapping.from).add(" to ")
      .visitTypeExpr(mapping.to).visitDescription(mapping.description)
  }

  def emitPattern(pattern: AST.Pattern): this.type = {
    val line =
      if (pattern.pattern.size == 1) {
        "Pattern(\"" + pattern.pattern.head.s + "\"" + s") "
      } else {
        s"Pattern(\n" + pattern.pattern.map(l => spc + "  \"" + l.s + "\"\n")
        s"\n) "
      }
    this.add(line).visitDescription(pattern.description)
  }

  def visitTypeRef(typeRef: AST.TypeRef): this.type = {
    this.add(s" is type ${typeRef.id.value}")
  }

  def visitTypeExpr(typEx: AST.TypeExpression): this.type = {
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
      case AST.URL(_, scheme) => this
          .add(s"URL${scheme.map(s => "\"" + s.s + "\"").getOrElse("")}")
      case enumeration: AST.Enumeration => emitEnumeration(enumeration)
      case alternation: AST.Alternation => emitAlternation(alternation)
      case aggregation: AST.Aggregation => emitAggregation(aggregation)
      case mapping: AST.Mapping         => emitMapping(mapping)
      case AST.RangeType(_, min, max, desc) => this
          .add(s"range from $min to $max ").visitDescription(desc)
      case AST.ReferenceType(_, id, desc) => this.add(s"reference to $id")
          .visitDescription(desc)
      case pattern: AST.Pattern => emitPattern(pattern)
      case AST.UniqueId(_, id, desc) => this.add(s"Id(${id.value}) ")
          .visitDescription(desc)
      case AST.Optional(_, typex)   => this.visitTypeExpr(typex).add("?")
      case AST.ZeroOrMore(_, typex) => this.visitTypeExpr(typex).add("*")
      case AST.OneOrMore(_, typex)  => this.visitTypeExpr(typex).add("+")
      case x: AST.TypeExpression =>
        require(requirement = false, s"Unknown type $x")
        this
    }
  }

   */

}
