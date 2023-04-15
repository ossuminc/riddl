/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.prettify

import java.nio.file.Files
import java.nio.file.Path
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.prettify.PrettifyPass.keyword
import com.reactific.riddl.utils.TextFileWriter

import java.nio.charset.StandardCharsets
import com.reactific.riddl.language.parsing.Terminals.*

/** Unit Tests For RiddlFileEmitter */
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
    } else {strings.foreach(s => sb.append(s""" "${s.s}" """))}
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

  def indent: this.type = {indentLevel = indentLevel + 2; this}

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
      if definition.isEmpty then {add("{ ??? }")}
      else {add("{\n").indent}
    }
    this
  }

  def closeDef(
    definition: Definition,
    withBrace: Boolean = true
  ): this.type = {
    if withBrace && !definition.isEmpty then {outdent.addIndent("}")}
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
      case (None, Some(x)) => this.add(s"String(,$x)")
      case (Some(n), None) => this.add(s"String($n)")
      case (None, None) => this.add(s"String")
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
    add(s"one of {\n").indent
      .addIndent("")
      .emitTypeExpression(alternation.of.head)
    val s5 = alternation.of.tail.foldLeft(this) { (s4, te) =>
      s4.add(" or ").emitTypeExpression(te)
    }
    s5.add("\n").outdent.addIndent("}")
    this
  }

  def emitField(field: Field): this.type = {
    this
      .add(s"${field.id.value}: ")
      .emitTypeExpression(field.typeEx)
      .emitDescription(field.description)
  }

  def emitFields(of: Seq[Field]): this.type = {
    if of.isEmpty then {this.add("{ ??? }")}
    else if of.sizeIs == 1 then {
      val f: Field = of.head
      add(s"{ ").emitField(f).add(" }").emitDescription(f.description)
    } else {
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
    val line =
      if pattern.pattern.sizeIs == 1 then {
        "Pattern(\"" + pattern.pattern.head.s + "\"" + s") "
      } else {
        s"Pattern(\n" + pattern.pattern.map(l => spc + "  \"" + l.s + "\"\n")
        s"\n) "
      }
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
      case string: Strng => emitString(string)
      case AliasedTypeExpression(_, id) => this.add(id.format)
      case URL(_, scheme) =>
        this
          .add(s"URL${scheme.fold("")(s => "\"" + s.s + "\"")}")
      case enumeration: Enumeration => emitEnumeration(enumeration)
      case alternation: Alternation => emitAlternation(alternation)
      case mapping: Mapping => emitMapping(mapping)
      case sequence: Sequence => emitSequence(sequence)
      case set: Set                 => emitSet(set)
      case RangeType(_, min, max)   => this.add(s"range($min,$max) ")
      case Decimal(_, whl, frac)    => this.add(s"Decimal($whl,$frac)")
      case EntityReferenceTypeExpression(_, er) =>
        this
          .add(s"${Keywords.reference} to ${er.format}")
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

  def emitCondition(
    condition: Condition
  ): this.type = {
    add(condition.format)
  }

  def emitAction(
    action: Action
  ): this.type = {
    add(action.format)
  }

  def emitActions(actions: Seq[Action]): this.type = {
    actions.foreach { (a: Action) => emitAction(a) }
    this
  }

  def emitGherkinStrings(strings: Seq[LiteralString]): this.type = {
    strings.size match {
      case 0 => add("\"\"")
      case 1 => add(strings.head.format)
      case _ =>
        indent.add("\n")
        strings.foreach { fact => addLine(fact.format) }
        outdent
    }
  }

  def emitAGherkinClause(ghc: GherkinClause): this.type = {
    ghc match {
      case GivenClause(_, strings) => emitGherkinStrings(strings)
      case WhenClause(_, condition) => emitCondition(condition)
      case ThenClause(_, action) => emitAction(action)
      case ButClause(_, action) => emitAction(action)
    }
  }

  def emitGherkinClauses(
    kind: String,
    clauses: Seq[GherkinClause]
  ): this.type = {
    clauses.size match {
      case 0 => this
      case 1 => addIndent(kind).add(" ").emitAGherkinClause(clauses.head).nl
      case _ =>
        addIndent(kind).add(" ").emitAGherkinClause(clauses.head).nl
        clauses.tail.foreach { clause =>
          addIndent("and ").emitAGherkinClause(clause).nl
        }
        this
    }
  }

  def emitExample(example: Example): this.type = {
    if !example.isImplicit then {
      openDef(example)
    }
    emitGherkinClauses("given ", example.givens)
      .emitGherkinClauses("when", example.whens)
      .emitGherkinClauses("then", example.thens)
      .emitGherkinClauses("but", example.buts)
    if !example.isImplicit then {
      closeDef(example)
    }
    this
  }

  def emitExamples(examples: Seq[Example]): this.type = {
    examples.foreach(emitExample)
    this
  }

  def emitUndefined(): this.type = {add(" ???")}

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
