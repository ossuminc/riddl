/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.utils.URL
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.utils.FileBuilder

/** Generates RIDDL in textual format based on the AST */
case class RiddlFileEmitter(url: URL) extends FileBuilder {

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
    addIndent(s"${keyword(definition)} ${definition.id.format} is ")
    if withBrace then
      if definition.isEmpty then add("{ ??? }").nl
      else add("{").nl.incr
    else this
    end if
  }

  def closeDef(
    definition: Definition
  ): this.type = {
    if definition.nonEmpty then decr.addLine("}")
    definition match
      case wd: WithMetaData => emitDescriptives(wd.descriptions).nl
      case _                    => this.nl
    end match
  }

  def emitDescriptives(descriptives: Seq[MetaData]): this.type =
    if descriptives.nonEmpty then
      add(" with {").nl.incr
      descriptives.foreach {
        case c: Comment          => emitComment(c)
        case b: BriefDescription => emitBriefDescription(b)
        case d: Description      => emitDescription(d)
        case t: Term             => emitTerm(t)
        case a: AuthorRef        => emitAuthorRef(a)
        case sa: StringAttachment => emitStringAttachment(sa)
        case fa: FileAttachment => emitFileAttachment(fa)
        case ua: ULIDAttachment => emitULIDAttachment(ua)
      }
      decr.add("}")
    end if
    this
  end emitDescriptives

  def emitComment(comment: Comment): this.type =
    comment match
      case inline: InlineComment => this.addLine(inline.format)
      case block: LineComment    => this.addLine(block.format)
    end match
  end emitComment

  private def emitBriefDescription(brief: BriefDescription): this.type =
    addLine(brief.format)
  end emitBriefDescription

  def emitDescription(description: Description): this.type =
    description match
      case bd: BlockDescription =>
        addLine("described as {")
        incr
        bd.lines.foreach { line => addIndent("|").add(line.s).nl }
        decr
        addLine("}")
      case URLDescription(_, url) =>
        addIndent("described ")
        url.scheme match
          case "file"           => add("in file ")
          case "http" | "https" => add("at ")
        end match
        add(url.toExternalForm).nl
      case _ => // ignore
    end match
    this
  end emitDescription

  private def emitTerm(term: Term): this.type =
    addIndent("term ")
    add(term.id.format)
    add(" is ")
    add(term.definition)
    emitDescriptives(term.metadata.toSeq)
    nl
  end emitTerm

  def emitAuthorRef(authorRef: AuthorRef): this.type =
    addIndent("by ").add(authorRef.format).nl
  end emitAuthorRef

  private def emitStringAttachment(a: StringAttachment): this.type =
    addIndent(a.identify).add(s" is \"${a.mimeType}\" as \"${a.value}\"")
  end emitStringAttachment

  private def emitFileAttachment(a: FileAttachment): this.type =
    addIndent(a.identify).add(s" is \"${a.mimeType} in file \"${a.inFile}\"")

  private def emitULIDAttachment(ulid: ULIDAttachment): this.type =
    addIndent(ulid.identify)

  def emitString(s: String_): this.type = {
    (s.min, s.max) match {
      case (Some(n), Some(x)) => this.add(s"String($n,$x)")
      case (None, Some(x))    => this.add(s"String(,$x)")
      case (Some(n), None)    => this.add(s"String($n)")
      case (None, None)       => this.add(s"String")
    }
  }

  def emitConstant(constant: Constant): this.type =
    addIndent("constant ")
    add(constant.id.format)
    add(" is ")
    add(constant.value.format)
    emitDescriptives(constant.metadata.toSeq)
    nl
  end emitConstant

  private def emitEnumeration(enumeration: Enumeration): this.type = {
    add(s"any of {").nl.incr
    val enumerators: String = enumeration.enumerators
      .map { enumerator =>
        enumerator.id.value + enumerator.enumVal.fold("")(x => s"($x)")
      }
      .mkString(s"$spc", s",$new_line$spc", new_line)
    add(enumerators).decr.addLine("}")
    this
  }

  private def emitAlternation(alternation: Alternation): this.type = {
    add(s"one of {").nl.incr.addIndent("")
    val paths: Seq[String] =
      alternation.of.map { (typeEx: AliasedTypeExpression) => typeEx.pathId.format }.toSeq
    add(paths.mkString("", " or ", new_line))
    decr.addIndent("}")
    this
  }

  private def emitField(field: Field): this.type =
    add(s"${field.id.value}: ")
    emitTypeExpression(field.typeEx)
    emitDescriptives(field.metadata.toSeq)
    this
  end emitField

  private def emitFields(of: Seq[Field]): this.type = {
    of.headOption match {
      case None => this.add("{ ??? }")
      case Some(field) if of.size == 1 =>
        add(s"{ ")
          .emitField(field)
          .addLine("}")
      case Some(_) =>
        this.add("{").nl.incr
        of.foldLeft(this) { case (s, f) =>
          s.add(spc)
            .emitField(f)
            .add(",")
            .nl
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
    this.add(" ").emitFields(mt.fields)
  }

  private def emitMessageRef(mr: MessageRef): this.type = {
    this.add(mr.format)
  }

  def emitTypeExpression(typEx: TypeExpression): this.type = {
    typEx match {
      case string: String_                 => emitString(string)
      case AliasedTypeExpression(_, _, id) => this.add(id.format)
      case URI(_, scheme)                  => add(s"URL${scheme.fold("")(s => "\"" + s.s + "\"")}")
      case enumeration: Enumeration        => emitEnumeration(enumeration)
      case alternation: Alternation        => emitAlternation(alternation)
      case mapping: Mapping                => emitMapping(mapping)
      case sequence: Sequence              => emitSequence(sequence)
      case set: Set                        => emitSet(set)
      case graph: Graph                    => emitGraph(graph)
      case table: Table                    => emitTable(table)
      case replica: Replica                => emitReplica(replica)
      case RangeType(_, min, max)          => add(s"range($min,$max) ")
      case Decimal(_, whl, frac)           => add(s"Decimal($whl,$frac)")
      case EntityReferenceTypeExpression(_, er) => add(s"${Keyword.reference} to ${Keyword.entity} ${er.format}")
      case pattern: Pattern     => emitPattern(pattern)
      case UniqueId(_, id)      => this.add(s"Id(${id.format}) ")
      case Optional(_, typex)   => emitTypeExpression(typex).add("?")
      case ZeroOrMore(_, typex) => emitTypeExpression(typex).add("*")
      case OneOrMore(_, typex)  => emitTypeExpression(typex).add("+")
      case SpecificRange(_, typex, n, x) =>
        emitTypeExpression(typex).add("{")
        add(n.toString).add(",")
        add(x.toString).add("}")
      case ate: AggregateTypeExpression =>
        ate match {
          case aggr: Aggregation                  => emitAggregation(aggr)
          case mt: AggregateUseCaseTypeExpression => emitMessageType(mt)
        }
      case p: PredefinedType => this.add(p.kind)
    }
  }

  def emitType(t: Type): this.type = {
    add(s"${spc}type ${t.id.value} is ")
    emitTypeExpression(t.typEx)
    if t.metadata.nonEmpty then
      add(" with {").nl.incr.emitDescriptives(t.metadata.toSeq).decr.addLine("}")
    end if
    this
  }

  def emitStatement(statement: Statements): Unit =
    statement match
      case IfThenElseStatement(_, cond, thens, elses) =>
        addIndent(s"if ${cond.format} then").nl.incr
        if (thens.isEmpty) then addLine("???") else thens.foreach(emitStatement)
        decr.addLine("else").incr
        if elses.isEmpty then addLine("???") else elses.foreach(emitStatement)
        decr.addLine("end")
      case ForEachStatement(_, ref, statements) =>
        addIndent(s"foreach ${ref.format} do").incr
        statements.foreach(emitStatement)
        decr.addLine("end")
      case SendStatement(_, msg, portlet) =>
        addLine(s"send ${msg.format} to ${portlet.format}")
      case TellStatement(_, msg, to) =>
        addLine(s"tell ${msg.format} to ${to.format}")
      case statement: Statement => addLine(statement.format)
      case comment: Comment     => emitComment(comment)
    end match
  end emitStatement

  def emitCodeBlock(statements: Seq[Statements]): this.type = {
    if statements.isEmpty then add(" { ??? }").nl
    else
      add(" {").incr.nl
      statements.foreach(emitStatement)
      decr.addIndent("}").nl
    this
  }

  def emitUndefined(): this.type = { add(" ???") }

  def emitOption(option: OptionValue): this.type =
    addIndent(option.format + new_line)
  end emitOption

  def emitOptions(optionDef: WithOptions[?]): this.type =
    if optionDef.options.nonEmpty then
      optionDef.options.map { option => option.format + new_line }.foreach(addIndent); this
    else this
    end if
  end emitOptions

  def emitSchemaKind(schemaKind: RepositorySchemaKind): this.type =
    val str = schemaKind match {
      case RepositorySchemaKind.Other        => "other"
      case RepositorySchemaKind.Flat         => "flat"
      case RepositorySchemaKind.Relational   => "relational"
      case RepositorySchemaKind.TimeSeries   => "time-series"
      case RepositorySchemaKind.Graphical    => "graphical"
      case RepositorySchemaKind.Hierarchical => "hierarchical"
      case RepositorySchemaKind.Star         => "star"
      case RepositorySchemaKind.Document     => "document"
      case RepositorySchemaKind.Columnar     => "columnar"
      case RepositorySchemaKind.Vector       => "vector"
    }
    add(str)
  end emitSchemaKind

}
