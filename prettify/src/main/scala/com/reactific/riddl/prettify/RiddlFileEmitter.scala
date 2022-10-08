package com.reactific.riddl.prettify

import java.nio.file.Files
import java.nio.file.Path
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.prettify.PrettifyTranslator.keyword
import com.reactific.riddl.utils.TextFileWriter

import java.nio.charset.StandardCharsets

/** Unit Tests For RiddlFileEmitter */
case class RiddlFileEmitter(filePath: Path) extends TextFileWriter {
  private var indentLevel: Int = 0
  override def toString: String = sb.toString

  def asString: String = sb.toString()

  def spc: String = { " ".repeat(indentLevel) }

  def add(str: String): RiddlFileEmitter = {
    sb.append(str)
    this
  }

  def addSpace(): RiddlFileEmitter = add(spc)

  def add(strings: Seq[LiteralString]): RiddlFileEmitter = {
    if (strings.sizeIs > 1) {
      sb.append("\n")
      strings.foreach(s => sb.append(s"""$spc"${s.s}"$newLine"""))
    } else { strings.foreach(s => sb.append(s""" "${s.s}" """)) }
    this
  }

  def add[T](opt: Option[T])(map: T => String): RiddlFileEmitter = {
    opt match {
      case None => this
      case Some(t) =>
        sb.append(map(t))
        this
    }
  }

  def addIndent(): RiddlFileEmitter = {
    sb.append(s"$spc")
    this
  }

  def addIndent(str: String): RiddlFileEmitter = {
    sb.append(s"$spc$str")
    this
  }

  def addLine(str: String): RiddlFileEmitter = {
    sb.append(s"$spc$str\n")
    this
  }
  def indent: RiddlFileEmitter = { indentLevel = indentLevel + 2; this }

  def outdent: RiddlFileEmitter = {
    require(indentLevel > 1, "unmatched indents")
    indentLevel = indentLevel - 2
    this
  }

  def openDef(
    definition: Definition,
    withBrace: Boolean = true
  ): RiddlFileEmitter = {
    addSpace().add(s"${keyword(definition)} ${definition.id.format} is ")
    if (withBrace) {
      if (definition.isEmpty) { add("{ ??? }") }
      else { add("{\n").indent }
    }
    this
  }

  def closeDef(
    definition: Definition,
    withBrace: Boolean = true
  ): RiddlFileEmitter = {
    if (withBrace && !definition.isEmpty) { outdent.addIndent("}") }
    emitBrief(definition.brief)
    emitDescription(definition.description).add("\n")
  }

  def emitBrief(brief: Option[LiteralString]): RiddlFileEmitter = {
    brief.foldLeft(this) { (s, ls: LiteralString) =>
      s.add(s" briefly ${ls.format}")
    }
  }

  def emitDescription(description: Option[Description]): RiddlFileEmitter = {
    description.foldLeft(this) { (s, desc: Description) =>
      val s2 = s.add(" described as {\n").indent
      desc.lines.foldLeft(s2) { case (s3, line) =>
        s3.add(s3.spc + "|" + line.s + "\n")
      }.outdent.addLine("}")
    }
  }

  def emitString(s: Strng): RiddlFileEmitter = {
    (s.min, s.max) match {
      case (Some(n), Some(x)) => this.add(s"String($n,$x")
      case (None, Some(x))    => this.add(s"String(,$x")
      case (Some(n), None)    => this.add(s"String($n)")
      case (None, None)       => this.add(s"String")
    }
  }

  def mkEnumeratorDescription(description: Option[Description]): String = {
    description match {
      case Some(desc) => " described as { " + {
          desc.lines.map(_.format).mkString("", s"\n$spc", " }\n")
        }
      case None => ""
    }
  }

  def emitEnumeration(enumeration: Enumeration): RiddlFileEmitter = {
    val head = this.add(s"any of {\n").indent
    val enumerators: String = enumeration.enumerators.map { enumerator =>
      enumerator.id.value + enumerator.enumVal.fold("")(x => s"($x)") +
        mkEnumeratorDescription(enumerator.description)
    }.mkString(s"$spc", s",\n$spc", s"\n")
    head.add(enumerators).outdent.addLine("}")
  }

  def emitAlternation(alternation: Alternation): RiddlFileEmitter = {
    add(s"one of {\n").indent.addIndent("")
      .emitTypeExpression(alternation.of.head)
    val s5 = alternation.of.tail.foldLeft(this) { (s4, te) =>
      s4.add(" or ").emitTypeExpression(te)
    }
    s5.add("\n").outdent.addIndent("}")
  }

  def emitField(field: Field): RiddlFileEmitter = {
    this.add(s"${field.id.value}: ").emitTypeExpression(field.typeEx)
      .emitDescription(field.description)
  }

  def emitFields(of: Seq[Field]): RiddlFileEmitter = {
    if (of.isEmpty) { this.add("{ ??? }") }
    else if (of.sizeIs == 1) {
      val f: Field = of.head
      add(s"{ ").emitField(f).add(" }").emitDescription(f.description)
    } else {
      this.add("{\n").indent
      val result = of.foldLeft(this) { case (s, f) =>
        s.add(spc).emitField(f).emitDescription(f.description).add(",\n")
      }
      result.sb.deleteCharAt(result.sb.length - 2)
      result.outdent.add(s"$spc} ")
    }
  }

  def emitAggregation(aggregation: Aggregation): RiddlFileEmitter = {
    emitFields(aggregation.fields)
  }

  def emitMapping(mapping: Mapping): RiddlFileEmitter = {
    this.add(s"mapping from ").emitTypeExpression(mapping.from).add(" to ")
      .emitTypeExpression(mapping.to)
  }

  def emitPattern(pattern: Pattern): RiddlFileEmitter = {
    val line =
      if (pattern.pattern.sizeIs == 1) {
        "Pattern(\"" + pattern.pattern.head.s + "\"" + s") "
      } else {
        s"Pattern(\n" + pattern.pattern.map(l => spc + "  \"" + l.s + "\"\n")
        s"\n) "
      }
    this.add(line)
  }

  def emitMessageType(mt: MessageType): RiddlFileEmitter = {
    this.add(mt.messageKind.kind.toLowerCase).add(" ").emitFields(mt.fields)
  }

  def emitMessageRef(mr: MessageRef): RiddlFileEmitter = { this.add(mr.format) }

  def emitTypeRef(tr: TypeRef): RiddlFileEmitter = { this.add(tr.format) }

  def emitTypeExpression(typEx: TypeExpression): RiddlFileEmitter = {
    typEx match {
      case string: Strng                => emitString(string)
      case b: Bool                      => this.add(b.kind)
      case n: Number                    => this.add(n.kind)
      case i: Integer                   => this.add(i.kind)
      case d: Decimal                   => this.add(d.kind)
      case r: Real                      => this.add(r.kind)
      case d: Date                      => this.add(d.kind)
      case t: Time                      => this.add(t.kind)
      case dt: DateTime                 => this.add(dt.kind)
      case ts: TimeStamp                => this.add(ts.kind)
      case ll: LatLong                  => this.add(ll.kind)
      case n: Nothing                   => this.add(n.kind)
      case AliasedTypeExpression(_, id) => this.add(id.format)
      case URL(_, scheme) => this
          .add(s"URL${scheme.fold("")(s => "\"" + s.s + "\"")}")
      case enumeration: Enumeration => emitEnumeration(enumeration)
      case alternation: Alternation => emitAlternation(alternation)
      case aggregation: Aggregation => emitAggregation(aggregation)
      case mapping: Mapping         => emitMapping(mapping)
      case RangeType(_, min, max)   => this.add(s"range($min,$max) ")
      case EntityReferenceTypeExpression(_, er) => this
          .add(s"${Keywords.reference} to ${er.format}")
      case pattern: Pattern     => emitPattern(pattern)
      case mt: MessageType      => emitMessageType(mt)
      case UniqueId(_, id)      => this.add(s"Id(${id.format}) ")
      case Optional(_, typex)   => this.emitTypeExpression(typex).add("?")
      case ZeroOrMore(_, typex) => this.emitTypeExpression(typex).add("*")
      case OneOrMore(_, typex)  => this.emitTypeExpression(typex).add("+")
      case x: TypeExpression =>
        require(requirement = false, s"Unknown type $x")
        this
    }
  }

  def emitType(t: Type): RiddlFileEmitter = {
    this.add(s"${spc}type ${t.id.value} is ").emitTypeExpression(t.typ)
      .emitDescription(t.description).add("\n")
  }

  def emitCondition(
    condition: Condition
  ): RiddlFileEmitter = { this.add(condition.format) }

  def emitAction(
    action: Action
  ): RiddlFileEmitter = { this.add(action.format) }

  def emitActions(actions: Seq[Action]): RiddlFileEmitter = {
    actions.foldLeft(this)((s, a) => s.emitAction(a))
  }

  def emitGherkinStrings(strings: Seq[LiteralString]): RiddlFileEmitter = {
    strings.size match {
      case 0 => add("\"\"")
      case 1 => add(strings.head.format)
      case _ =>
        indent.add("\n")
        strings.foreach { fact => addLine(fact.format) }
        outdent
    }
  }

  def emitAGherkinClause(ghc: GherkinClause): RiddlFileEmitter = {
    ghc match {
      case GivenClause(_, strings)  => emitGherkinStrings(strings)
      case WhenClause(_, condition) => emitCondition(condition)
      case ThenClause(_, action)    => emitAction(action)
      case ButClause(_, action)     => emitAction(action)
    }
  }

  def emitGherkinClauses(
    kind: String,
    clauses: Seq[GherkinClause]
  ): RiddlFileEmitter = {
    clauses.size match {
      case 0 => this
      case 1 => addIndent(kind).add(" ").emitAGherkinClause(clauses.head)
      case _ =>
        add("\n").addIndent(kind).add(" ").emitAGherkinClause(clauses.head)
        clauses.tail.foldLeft(this) { (next, clause) =>
          next.nl.addIndent("and ").emitAGherkinClause(clause)
        }
    }
  }

  def emitExample(example: Example): RiddlFileEmitter = {
    if (!example.isImplicit) { openDef(example) }
    emitGherkinClauses("given ", example.givens)
      .emitGherkinClauses("when", example.whens)
      .emitGherkinClauses("then", example.thens)
      .emitGherkinClauses("but", example.buts)
    if (!example.isImplicit) { closeDef(example) }
    this
  }

  def emitExamples(examples: Seq[Example]): RiddlFileEmitter = {
    examples.foreach(emitExample)
    this
  }

  def emitUndefined(): RiddlFileEmitter = { add(" ???") }

  def emitOptions(optionDef: WithOptions[?]): RiddlFileEmitter = {
    if (optionDef.options.nonEmpty) this.addLine(optionDef.format) else this
  }

  def emit(): Path = {
    Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8)
    filePath
  }

}
