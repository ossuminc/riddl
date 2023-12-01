/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.prettify

import java.nio.file.Files
import java.nio.file.Path
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.prettify.PrettifyPass.keyword
import com.ossuminc.riddl.utils.TextFileWriter

import java.nio.charset.StandardCharsets
/** Unit Tests For RiddlFileEmitter */
@SuppressWarnings(Array("org.wartremover.warts.Var"))
case class RiddlFileEmitter(filePath: Path) extends TextFileWriter {
  private var indentLevel: Int = 0

  override def clear: Unit = {
    super.clear
    indentLevel = 0
  }

  def asString: String = sb.toString()

  def spc: String = { " ".repeat(indentLevel) }

  def add(str: String): this.type = {
    sb.append(str)
    this
  }

  def addSpace(): this.type = add(spc)

  def add(strings: Seq[LiteralString]): this.type = {
    if strings.sizeIs > 1 then {
      sb.append("\n")
      strings.foreach(s => sb.append(s"""$spc"${s.s}"$newLine"""))
    } else { strings.foreach(s => sb.append(s""" "${s.s}" """)) }
    this
  }

  def add[T](opt: Option[T])(map: T => String): this.type = {
    opt match {
      case None => this
      case Some(t) =>
        sb.append(map(t))
        this
    }
  }

  def addIndent(): this.type = {
    sb.append(s"$spc")
    this
  }

  def addIndent(str: String): this.type = {
    sb.append(s"$spc$str")
    this
  }

  def addLine(str: String): this.type = {
    sb.append(s"$spc$str\n")
    this
  }

  def indent: this.type = { indentLevel = indentLevel + 2; this }

  def outdent: this.type = {
    require(indentLevel > 1, "unmatched indents")
    indentLevel = indentLevel - 2
    this
  }

  def openDef(
    definition: Definition,
    withBrace: Boolean = true
  ): this.type = {
    addSpace().add(s"${keyword(definition)} ${definition.id.format} is ")
    if withBrace then {
      if definition.isEmpty then add("{ ??? }")
      else add("{\n").indent
    }
    this
  }

  def closeDef(
    definition: Definition,
    withBrace: Boolean = true
  ): this.type = {
    if withBrace && !definition.isEmpty then { outdent.addIndent("}") }
    emitBrief(definition.brief)
    emitDescription(definition.description).add("\n")
  }

  def emitBrief(brief: Option[LiteralString]): this.type = {
    brief.map { (ls: LiteralString) => this.add(s" briefly ${ls.format}") }
    this
  }

  def emitDescription(description: Option[Description]): this.type = {
    description.map { (desc: Description) =>
      add(" described as {\n")
      indent
      desc.lines.foreach { line =>
        add(spc + "|" + line.s + "\n")
      }
      outdent
      addLine("}")
    }
    this
  }

  def emitString(s: Strng): this.type = {
    (s.min, s.max) match {
      case (Some(n), Some(x)) => this.add(s"String($n,$x)")
      case (None, Some(x))    => this.add(s"String(,$x)")
      case (Some(n), None)    => this.add(s"String($n)")
      case (None, None)       => this.add(s"String")
    }
  }

  def mkEnumeratorDescription(description: Option[Description]): String = {
    description match {
      case Some(desc) =>
        " described as { " + {
          desc.lines.map(_.format).mkString("", s"\n$spc", " }\n")
        }
      case None => ""
    }
  }

  def emitEnumeration(enumeration: Enumeration): this.type = {
    val head = this.add(s"any of {\n").indent
    val enumerators: String = enumeration.enumerators
      .map { enumerator =>
        enumerator.id.value + enumerator.enumVal.fold("")(x => s"($x)") +
          mkEnumeratorDescription(enumerator.description)
      }
      .mkString(s"$spc", s",\n$spc", s"\n")
    head.add(enumerators).outdent.addLine("}")
    this
  }

  def emitAlternation(alternation: Alternation): this.type = {
    add(s"one of {\n").indent.addIndent("")
    val paths: Seq[String] = alternation.of.map { (typeEx: AliasedTypeExpression) => typeEx.pathId.format }
    add(paths.mkString("", " or ", "\n"))
    outdent.addIndent("}")
    this
  }

  def emitField(field: Field): this.type = {
    this
      .add(s"${field.id.value}: ")
      .emitTypeExpression(field.typeEx)
      .emitDescription(field.description)
  }

  def emitFields(of: Seq[Field]): this.type = {
    of.headOption match {
      case None => this.add("{ ??? }")
      case Some(field) if of.size == 1 =>
        add(s"{ ").emitField(field).add(" }").emitDescription(field.description)
      case Some(field) =>
        this.add("{\n").indent
        of.foldLeft(this) { case (s, f) =>
          s.add(spc).emitField(f).emitDescription(f.description).add(",\n")
        }
        sb.deleteCharAt(sb.length - 2)
        outdent.add(s"$spc} ")
    }
    this
  }

  def emitAggregation(aggregation: Aggregation): this.type = {
    emitFields(aggregation.fields)
  }

  private def emitSequence(sequence: Sequence): this.type = {
    this.add("sequence of ").emitTypeExpression(sequence.of)
  }

  private def emitSet(set: Set): this.type = {
    this.add("set of ").emitTypeExpression(set.of)
  }

  private def emitMapping(mapping: Mapping): this.type = {
    this
      .add(s"mapping from ")
      .emitTypeExpression(mapping.from)
      .add(" to ")
      .emitTypeExpression(mapping.to)
  }

  def emitPattern(pattern: Pattern): this.type = {
    val line = pattern.pattern.toList match
      case Nil =>
        ""
      case pat :: Nil =>
        s"Pattern(${pat.format})"
      case pat :: tail =>
        val lines = (pat :: tail).map(_.format).mkString(spc, s"\n$spc", "\n")
        s"Pattern(\n$lines)\n"
    this.add(line)
  }

  def emitMessageType(mt: AggregateUseCaseTypeExpression): this.type = {
    this.add(mt.usecase.kind.toLowerCase).add(" ").emitFields(mt.fields)
  }

  def emitMessageRef(mr: MessageRef): this.type = {
    this.add(mr.format)
  }

  def emitTypeExpression(typEx: TypeExpression): this.type = {
    typEx match {
      case string: Strng                => emitString(string)
      case AliasedTypeExpression(_, _, id) => this.add(id.format)
      case URL(_, scheme) =>
        this
          .add(s"URL${scheme.fold("")(s => "\"" + s.s + "\"")}")
      case enumeration: Enumeration => emitEnumeration(enumeration)
      case alternation: Alternation => emitAlternation(alternation)
      case mapping: Mapping         => emitMapping(mapping)
      case sequence: Sequence       => emitSequence(sequence)
      case set: Set                 => emitSet(set)
      case RangeType(_, min, max)   => this.add(s"range($min,$max) ")
      case Decimal(_, whl, frac)    => this.add(s"Decimal($whl,$frac)")
      case EntityReferenceTypeExpression(_, er) =>
        this
          .add(s"${Keyword.reference} to ${er.format}")
      case pattern: Pattern     => emitPattern(pattern)
      case UniqueId(_, id)      => this.add(s"Id(${id.format}) ")
      case Optional(_, typex)   => this.emitTypeExpression(typex).add("?")
      case ZeroOrMore(_, typex) => this.emitTypeExpression(typex).add("*")
      case OneOrMore(_, typex)  => this.emitTypeExpression(typex).add("+")
      case SpecificRange(_, typex, n, x) =>
        this
          .emitTypeExpression(typex)
          .add("{")
          .add(n.toString)
          .add(",")
          .add(x.toString)
          .add("}")
      case ate: AggregateTypeExpression =>
        ate match {
          case aggr: Aggregation                  => emitAggregation(aggr)
          case mt: AggregateUseCaseTypeExpression => emitMessageType(mt)
        }
      case p: PredefinedType => this.add(p.kind)
    }
  }

  def emitType(t: Type): this.type = {
    this
      .add(s"${spc}type ${t.id.value} is ")
      .emitTypeExpression(t.typ)
      .emitDescription(t.description)
      .add("\n")
  }

  def emitStatement(
    action: Statement
  ): this.type = {
    add(action.format)
  }

  def emitStatements(actions: Seq[Statement]): this.type = {
    actions.foreach { (s: Statement) => emitStatement(s) }
    this
  }

  def emitCodeBlock(statements: Seq[Statement]): this.type = {
    if (statements.isEmpty) then add(" { ??? }\n")
    else
      add(" {").indent.nl
      statements.map(_.format + "\n").foreach(addIndent)
      outdent.addIndent("}").nl
    this
  }

  def emitUndefined(): this.type = { add(" ???") }

  def emitOptions(optionDef: WithOptions[?]): this.type = {
    if optionDef.options.nonEmpty then this.addLine(optionDef.format) else this
  }

  def emit(): Path = {
    Files.createDirectories(filePath.getParent)
    Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8)
    filePath
  }

  def emitStreamlets(proc: Processor[_, _]): this.type = {
    proc.inlets.foreach { (inlet: Inlet) =>
      addLine(s"inlet ${inlet.id.format} is ${inlet.type_.format}")
    }
    proc.outlets.foreach { (outlet: Outlet) =>
      addLine(s"outlet ${outlet.id.format} is ${outlet.type_.format}")
    }
    this
  }
}
