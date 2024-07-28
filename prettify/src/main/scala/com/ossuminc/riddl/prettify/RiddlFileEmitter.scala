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
import com.ossuminc.riddl.utils.{Logger, TextFileWriter}

import java.nio.charset.StandardCharsets

/** Unit Tests For RiddlFileEmitter */
case class RiddlFileEmitter(filePath: Path) extends TextFileWriter {

  def add(strings: Seq[LiteralString]): this.type = {
    if strings.sizeIs > 1 then {
      nl
      strings.foreach(s => sb.append(s"""$spc"${s.s}"$new_line"""))
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

  def openDef(
    definition: Definition,
    withBrace: Boolean = true
  ): this.type = {
    addIndent().add(s"${keyword(definition)} ${definition.id.format} is ")
    if withBrace then {
      if definition.isEmpty then add("{ ??? }")
      else add("{").nl.incr
    }
    this
  }

  def closeDef(
    definition: Definition,
    withBrace: Boolean = true
  ): this.type = {
    if withBrace && !definition.isEmpty then { decr.addIndent("}") }
    emitBrief(definition.brief)
    emitDescription(definition.description).nl
  }

  def emitBrief(brief: Option[LiteralString]): this.type = {
    brief.map { (ls: LiteralString) => this.add(s" briefly ${ls.format}") }
    this
  }

  def emitDescription(description: Option[Description]): this.type = {
    description.map { (desc: Description) =>
      add(" described as {").nl
      incr
      desc.lines.foreach { line =>
        add(spc + "|" + line.s).nl
      }
      decr
      addIndent("}")
    }
    this
  }

  def emitString(s: String_): this.type = {
    (s.min, s.max) match {
      case (Some(n), Some(x)) => this.add(s"String($n,$x)")
      case (None, Some(x))    => this.add(s"String(,$x)")
      case (Some(n), None)    => this.add(s"String($n)")
      case (None, None)       => this.add(s"String")
    }
  }

  private def mkEnumeratorDescription(description: Option[Description]): String = {
    description match {
      case Some(desc) =>
        " described as { " + {
          desc.lines.map(_.format).mkString("", s"$new_line$spc", s" }$new_line")
        }
      case None => ""
    }
  }

  private def emitEnumeration(enumeration: Enumeration): this.type = {
    val head = this.add(s"any of {").nl.incr
    val enumerators: String = enumeration.enumerators
      .map { enumerator =>
        enumerator.id.value + enumerator.enumVal.fold("")(x => s"($x)") +
          mkEnumeratorDescription(enumerator.description)
      }
      .mkString(s"$spc", s",$new_line", new_line)
    head.add(enumerators).decr.addLine("}")
    this
  }

  private def emitAlternation(alternation: Alternation): this.type = {
    add(s"one of {").nl.incr.addIndent("")
    val paths: Seq[String] = alternation.of.map { (typeEx: AliasedTypeExpression) => typeEx.pathId.format }
    add(paths.mkString("", " or ", new_line))
    decr.addIndent("}")
    this
  }

  private def emitField(field: Field): this.type = {
    this
      .add(s"${field.id.value}: ")
      .emitTypeExpression(field.typeEx)
      .emitDescription(field.description)
  }

  private def emitFields(of: Seq[Field]): this.type = {
    of.headOption match {
      case None => this.add("{ ??? }")
      case Some(field) if of.size == 1 =>
        add(s"{ ").emitField(field).add(" }").emitDescription(field.description)
      case Some(_) =>
        this.add("{").nl.incr
        of.foldLeft(this) { case (s, f) =>
          s.add(spc).emitField(f).emitDescription(f.description).add(",").nl
        }
        sb.deleteCharAt(sb.length - 2)
        decr.add(s"$spc} ")
    }
    this
  }

  private def emitAggregation(aggregation: Aggregation): this.type = {
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

  private def emitGraph(graph: Graph): this.type = {
    this.add("graph of ").emitTypeExpression(graph.of)
  }

  private def emitTable(table: Table): this.type = {
    this.add("table of ").emitTypeExpression(table.of).add(table.dimensions.mkString("[ ", ", ", " ]"))
  }

  private def emitReplica(replica: Replica): this.type = {
    this.add("replica of").emitTypeExpression(replica.of)
  }

  def emitPattern(pattern: Pattern): this.type = {
    val line = pattern.pattern.toList match
      case Nil =>
        ""
      case pat :: Nil =>
        s"Pattern(${pat.format})"
      case pat :: tail =>
        val lines = (pat :: tail).map(_.format).mkString(spc, s"$new_line$spc", new_line)
        s"Pattern($new_line$lines)$new_line"
    this.add(line)
  }

  private def emitMessageType(mt: AggregateUseCaseTypeExpression): this.type = {
    this.add(mt.usecase.useCase.toLowerCase).add(" ").emitFields(mt.fields)
  }

  def emitMessageRef(mr: MessageRef): this.type = {
    this.add(mr.format)
  }

  def emitTypeExpression(typEx: TypeExpression): this.type = {
    typEx match {
      case string: String_                 => emitString(string)
      case AliasedTypeExpression(_, _, id) => this.add(id.format)
      case URL(_, scheme) =>
        this
          .add(s"URL${scheme.fold("")(s => "\"" + s.s + "\"")}")
      case enumeration: Enumeration => emitEnumeration(enumeration)
      case alternation: Alternation => emitAlternation(alternation)
      case mapping: Mapping         => emitMapping(mapping)
      case sequence: Sequence       => emitSequence(sequence)
      case set: Set                 => emitSet(set)
      case graph: Graph             => emitGraph(graph)
      case table: Table             => emitTable(table)
      case replica: Replica         => emitReplica(replica)
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
      .nl
  }

  def emitCodeBlock(statements: Seq[Statement]): this.type = {
    if statements.isEmpty then add(" { ??? }").nl
    else
      add(" {").incr.nl
      statements.map(_.format + new_line).foreach(addIndent)
      decr.addIndent("}").nl
    this
  }

  def emitUndefined(): this.type = { add(" ???") }

  def emitOptions(optionDef: WithOptions): this.type = {
    if optionDef.options.nonEmpty then
      optionDef.options.map{ option => option.format + new_line}.foreach(addIndent); this
    else this
  }

  def emit(): Path = {
    Files.createDirectories(filePath.getParent)
    Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8)
    filePath
  }

  def emitStreamlets(proc: Processor[?]): this.type = {
    proc.inlets.foreach { (inlet: Inlet) =>
      addLine(s"inlet ${inlet.id.format} is ${inlet.type_.format}")
    }
    proc.outlets.foreach { (outlet: Outlet) =>
      addLine(s"outlet ${outlet.id.format} is ${outlet.type_.format}")
    }
    this
  }
}
