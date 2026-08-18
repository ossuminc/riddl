/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.utils.URL
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.parsing.{Keyword, Punctuation}
import com.ossuminc.riddl.utils.{FileBuilder, PlatformContext}
import fastparse.ParserInputSource.fromReadable

/** Generates RIDDL in textual format based on the AST */
case class RiddlFileEmitter(url: URL)(using PlatformContext) extends FileBuilder {

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
    // An OnMessageClause's id is a synthetic "keyword path" string (e.g.
    // "command Go"); emit the message reference itself, which is the
    // round-trippable source form, rather than quoting it as an identifier.
    // A55: an optional local binding is emitted as `<binding>: <msg>`.
    val name = definition match
      case omc: OnMessageLikeClause =>
        omc.binding.map(id => s"${id.format}: ").getOrElse("") + omc.msg.format
      case _ => definition.id.format
    // The declaration's meaningful prefix -- a handler's or state's `initial`, a context's
    // intention, an entity's intentions in canonical order. Shared with `AST.Definition.format`
    // so the two surfaces cannot drift; they had, and `format` was the one losing information.
    val prefix = Declaration.prefix(definition)
    // The generic streaming processor emits the canonical `processor` keyword; the deprecated
    // shape keywords (source/sink/flow/…) are normalized away so prettified text re-parses cleanly.
    val kw = definition match
      case _: Streamlet => Keyword.processor
      case _            => keyword(definition)
    // What sits between the identifier and `is`: a processor's ascribed shape, a message type's
    // `yields`, an on-clause's `from`. Shared with `format`, same reason as the prefix above.
    val ascription = Declaration.ascription(definition)
    addIndent(s"$prefix$kw $name$ascription is ")
    if withBrace then
      // STRUCTURAL, not semantic: `isEmpty` is now comment-tolerant, so a comments-only body would
      // self-close here and the comments would be emitted AFTER the closing brace.
      if hasNoChildren(definition) then add("{ ??? }").nl
      else add("{").nl.incr
    else this
    end if
  }

  /** Whether a definition has no children AT ALL — the question a brace-emitter must ask, since
    * `isEmpty` counts a comments-only body as empty.
    */
  private def hasNoChildren(d: Definition): Boolean = d match
    case b: Branch[?] => b.contents.isEmpty
    case _            => true

  private def onlyComments(d: Definition): Boolean = d match
    case b: Branch[?] => b.contents.nonEmpty && b.contents.toSeq.forall(_.isComment)
    case _            => false

  def closeDef(
    definition: Definition
  ): this.type = {
    if !hasNoChildren(definition) then
      // A body holding nothing but COMMENTS is still an undefined body, and `???` is how RIDDL says
      // so. `openDef` self-closes only a body with no children at all, so without this a commented
      // stub came back without its `???` — a marker that records deliberate intent a bare comment
      // does not.
      if onlyComments(definition) then addLine(Punctuation.undefinedMark)
      decr.addIndent("}")
      emitMetaData(definition.metadata)
      if definition.metadata.isEmpty then nl
    else
      // A `???` body still carries metadata, and it must still be written.
      //
      // `openDef` SELF-CLOSED this one as `{ ??? }`, so there is no brace to close — but the old
      // guard skipped `emitMetaData` along with the brace, silently deleting the `with { … }` of
      // every childless container. Prettify is meant to be lossless; this made it lossy in the
      // way that is hardest to notice, since the output still parses and only degrades into
      // "missing description" warnings on the NEXT validate.
      //
      // Fixed here rather than at the call sites because `closeDef` is shared by 13 containers,
      // so all of them had the bug. `closeState` carries a local version of this fix predating
      // the diagnosis (PrettifyVisitor.closeState) — harmless now, since a body-less state takes
      // the `contents.nonEmpty` branch there and never reaches this method.
      if definition.metadata.nonEmpty then
        trimTrailingNewline()
        emitMetaData(definition.metadata)
      end if
    end if
    this
  }

  def emitMetaData(meta: Contents[MetaData]): this.type =
    if meta.nonEmpty then
      add(" with {").nl.incr
      meta.foreach {
        case c: Comment           => emitComment(c)
        case b: BriefDescription  => emitBriefDescription(b)
        case d: Description       => emitDescription(d)
        case t: Term              => emitTerm(t)
        case o: OptionValue       => emitOption(o)
        case a: AuthorRef         => emitAuthorRef(a)
        case fr: FigmaRef         => emitFigmaRef(fr)
        case sa: StringAttachment => emitStringAttachment(sa)
        case fa: FileAttachment   => emitFileAttachment(fa)
        case ua: ULIDAttachment   => emitULIDAttachment(ua)
      }
      decr.addIndent("}").nl
    end if
    this
  end emitMetaData

  def emitComment(comment: Comment): this.type =
    comment match
      case block: InlineComment =>
        // The parser keeps everything between `/*` and `*/` verbatim, including the whitespace
        // that precedes the closing fence. Emitting `$spc` before `*/` therefore appended one
        // indent's worth of blanks on EVERY prettify generation, so a re-prettified file never
        // reached a fixed point. Trim each line and emit a canonical, idempotent layout instead.
        val lines = block.lines.map(_.trim)
        val all = lines.mkString(s"$spc/* ", s"\n$spc   ", " */")
        this.add(all).nl
      case inline: LineComment => this.addLine(inline.format)
    end match
  end emitComment

  private def emitBriefDescription(brief: BriefDescription): this.type =
    addLine(brief.format)
  end emitBriefDescription

  private def emitDescription(description: Description): this.type =
    description match
      case bd: BlockDescription =>
        addLine("described as {")
        incr
        bd.lines.foreach { line =>
          line.s.split("\n").foreach { part =>
            addIndent("|").add(part).nl
          }
        }
        decr
        addLine("}")
      case ud: URLDescription =>
        // Emits the AUTHORED path, so `described in file "X.md"` round-trips to itself instead of
        // to a machine-specific absolute URL.
        //
        // QUOTING DIFFERS BY SCHEME, and getting it uniform either way emits source that will not
        // parse. `described in file` takes a `literalString` (CommonParser:219), so its path MUST
        // be quoted -- emitting it bare produced output that failed to re-parse with
        // `Expected ("\"")`, reported by riddl-examples against rc.10-45. `described at` takes a
        // bare `httpUrl` (NoWhiteSpaceParsers:141), so quoting THAT would break it instead.
        addIndent("described ")
        if ud.path.startsWith("http://") || ud.path.startsWith("https://") then
          add("at ").add(ud.path).nl
        else add("in file ").add("\"" + ud.path + "\"").nl
        end if
      case _ => // ignore
    end match
    this
  end emitDescription

  private def emitTerm(term: Term): this.type =
    addIndent("term ")
    add(term.id.format)
    add(" is ")
    add(term.definition)
    nl
  end emitTerm

  def emitAuthorRef(authorRef: AuthorRef): this.type =
    addIndent("by ").add(authorRef.format).nl
  end emitAuthorRef

  /** `shown by { <url>… }` -- `ShownBy.format` yields only the keywords, so it is not usable here.
    * The URLs are emitted BARE: `shownBy` reads them with `httpUrl`, which does not accept quotes.
    */
  def emitShownBy(shownBy: ShownBy): this.type =
    addIndent("shown by { ")
      .add(shownBy.urls.map(_.toExternalForm).mkString(" "))
      .add(" }")
      .nl
  end emitShownBy

  /** A42: `figma "<fileKey>" node "<nodeId>"` */
  def emitFigmaRef(figmaRef: FigmaRef): this.type =
    addIndent(figmaRef.format).nl
  end emitFigmaRef

  // QUOTING DIFFERS BY FORM, and making it uniform either way emits source that will not parse.
  // `namedAttachmentBody` (CommonParser:372) reads the mime type with `mimeType`, a BARE token, so
  // quoting it is a hard parse error -- and because the ULID branch is tried first, the reported
  // failure is the misleading `Expected ("ULID")`. `ulidAttachmentBody` reads a `literalString`,
  // so that one form does need its quotes. Same trap as `described at` vs `described in file` in
  // `emitDescription` above.
  //
  // All three also lacked a trailing newline, which ran the last attachment onto the closing `}`
  // of the metadata block.
  private def emitStringAttachment(a: StringAttachment): this.type =
    addIndent("attachment " + a.id.format).add(s" is ${a.mimeType} as ${a.value.format}").nl
  end emitStringAttachment

  private def emitFileAttachment(a: FileAttachment): this.type =
    addIndent("attachment " + a.id.format).add(s" is ${a.mimeType} in file ${a.inFile.format}").nl
  end emitFileAttachment

  private def emitULIDAttachment(a: ULIDAttachment): this.type =
    addIndent("attachment " + a.id.format).add(s" is \"${a.ulid.toString}\"").nl
  end emitULIDAttachment

  def trimTrailingNewline(): this.type =
    if sb.length >= new_line.length &&
      sb.substring(sb.length - new_line.length) == new_line
    then sb.setLength(sb.length - new_line.length)
    this
  end trimTrailingNewline

  /** Delegates to `String_.format`, which renders only the NON-default bounds — a bare `String` and
    * `String(0,255)` are the same type, so rendering them differently made two equal models
    * disagree under any round-trip check that compares source.
    *
    * This also retires a bug in the hand-rolled version that stood here: for a min with no max it
    * emitted `String(7)`, which does not parse — `TypeParser.stringType` requires the comma (`"(" ~
    * integer.? ~ "," ~ integer.? ~ ")"`). Prettify must only ever emit source that reads back.
    */
  def emitString(s: String_): this.type = this.add(s.format)

  def emitConstant(constant: Constant): this.type =
    // `constant <id>: <type> = <value>` — the type expression is part of the surface syntax, so
    // it must be emitted for the round trip to re-parse. `is`/`are`/`=`/omission all still parse
    // (`CommonParser.is`) and none warns; prettify picks the colon so a constant reads like a
    // solo field.
    addIndent("constant ")
    add(constant.id.format)
    add(": ")
    emitTypeExpression(constant.typeEx)
    add(" = ")
    emitValue(constant.value)
    emitMetaData(constant.metadata)
  end emitConstant

  /** A20: render a [[Value]] for actual round-trip SOURCE, as opposed to `.format`'s error-message
    * rendering. The reason the two must differ at all is [[PromptValue]]'s optional `as <type>`
    * ascription: `Value.format` renders it via `PromptValue.ascriptionFormat`, a second, narrower
    * dispatch over [[TypeExpression]] that only reliably covers the handful of shapes it was
    * written against (an aliased type and the four `Cardinality` wrappers) — an enumeration, a
    * table, an entity reference, a parameterized predefined type, and others all fell to its
    * `other => other.format` catch-all, which for several of those does not reproduce parseable
    * source (found by code review 2026-08-15: `as any of {…}`, `as table of T of […]`, `as
    * reference to entity E`, and `as Currency(USD)` all mis-emitted). `emitTypeExpression` is
    * already the TOTAL, correct dispatch for a `TypeExpression` — every OTHER type-expression
    * position in this emitter routes through it — so a `PromptValue` ascription routes through it
    * too, rather than patching `ascriptionFormat` a third time.
    *
    * As of 2026-08-15's whole-branch review, `emitValue` is TOTAL over every [[Value]] shape that
    * can CONTAIN a nested `PromptValue`, not merely over `PromptValue` itself — a `Constructor`/
    * `Call`/`Initiate` argument, an `InvariantCondition`'s `with` argument, and a
    * `LogicalExpression`/`NotExpression` operand all recurse back through `emitValue` (via
    * `emitConstructorArg(s)`/`emitLogicalOperand`) rather than falling to `.format`. Every
    * `emitStatement` site whose operand can reach a `PromptValue` — `send`/`tell`/`yield`/`reply`
    * (via a `Constructor` operand), `morph … with`, `put`, `return`, `require … with`, a `when`
    * condition, and a `match`/`case` guard — now routes through `emitValue` too, so prettify never
    * reaches `.format` for a value that might carry an ascription. `ascriptionFormat` remains in
    * `AST.scala` ONLY for `.format`-based error-message rendering, which this emitter never uses
    * for a `Value`.
    *
    * Every OTHER [[Value]] shape (`LiteralString`, `ValueRef`, `GetValue`, `Ask`, `SelfValue`,
    * `NumericLiteral`, `ComparisonExpression`, `BooleanLiteral`) still renders via `.format`: none
    * of them carries a nested `Value`/`TypeExpression` that needs emitter-level rendering, so the
    * fallback arm is byte-identical to the pre-fix behaviour for all of them.
    *
    * `InvariantBlock` (`invariant X is { <stmts> <predicate> }`) is not a `Value` itself, so it is
    * not dispatched here — see [[emitInvariantBlock]] — but closes the same gap: as of Reid's
    * 2026-08-15 ruling, its `statements` route through `emitStatement` and its `predicate` through
    * `emitValue`, so `ascriptionFormat` is unreachable from THAT construct too. With this, prettify
    * never reaches `PromptValue.ascriptionFormat` anywhere — it serves `.format`-based
    * error-message rendering only.
    */
  def emitValue(v: Value): this.type =
    v match
      case pv: PromptValue =>
        add(s"prompt(${pv.prompt.format})")
        pv.typeEx.foreach { te =>
          add(" as "); emitTypeExpression(te)
        }
        this
      case Constructor(_, ref, args) =>
        add(s"${ref.format}(")
        emitConstructorArgs(args)
        add(")")
      case Call(_, function, args) =>
        add(s"call ${function.format}(")
        emitConstructorArgs(args)
        add(")")
      case Initiate(_, processor, args) =>
        add(s"initiate ${processor.format}")
        if args.nonEmpty then add("(").emitConstructorArgs(args).add(")") else this
      case InvariantCondition(_, ref, argument) =>
        add(ref.format)
        argument.foreach { a =>
          add(" with "); emitValue(a)
        }
        this
      case LogicalExpression(_, op, left, right) =>
        emitLogicalOperand(left)
        add(s" ${op.symbol} ")
        emitLogicalOperand(right)
      case NotExpression(_, expr) =>
        expr match
          case _: LogicalExpression => add("not (").emitValue(expr).add(")")
          case _                    => add("not ").emitValue(expr)
      case other => add(other.format)
  end emitValue

  /** A `LogicalExpression` operand is parenthesized when it is itself a `LogicalExpression` — same
    * rule as `LogicalExpression.format`'s private `paren` helper, kept in step by hand since this
    * emitter cannot call that helper (it is private to `AST.scala`).
    */
  private def emitLogicalOperand(v: Value): this.type = v match
    case le: LogicalExpression => add("(").emitValue(le).add(")")
    case _                     => emitValue(v)

  /** A single [[ConstructorArg]]: `<name> = <value>` when named, bare `<value>` when positional.
    * The value recurses through `emitValue` so a `PromptValue` argument's ascription renders
    * correctly no matter how deeply it is nested.
    */
  private def emitConstructorArg(arg: ConstructorArg): this.type =
    arg.name match
      case Some(id) => add(s"${id.format} = ").emitValue(arg.value)
      case None     => emitValue(arg.value)

  /** A comma-separated `ConstructorArg` list, shared by `Constructor`/`Call`/`Initiate` and
    * `TerminateStatement`.
    */
  private def emitConstructorArgs(args: Seq[ConstructorArg]): this.type =
    args.zipWithIndex.foreach { case (arg, idx) =>
      if idx > 0 then add(", ")
      emitConstructorArg(arg)
    }
    this

  /** The `MessageRef | RecordRef | Constructor | ValueRef` operand shared by
    * `SendStatement`/`TellStatement`/`YieldStatement`/`ReplyStatement` (message) and
    * `MorphStatement` (record). Only the `Constructor` arm can carry a nested `PromptValue`; the
    * refs have no nested `Value` to render, so they stay on `.format`.
    */
  private def emitConstructorOperand(
    v: MessageRef | RecordRef | Constructor | ValueRef
  ): this.type =
    v match
      case c: Constructor => emitValue(c)
      case other          => add(other.format)

  /** `InvariantBlock`'s `{ <stmts> <predicate> }` (`invariant X is { … }` block form) — now
    * rendered the same way as every OTHER statement block in this emitter (`emitCodeBlock`, an
    * on-clause body, a `when`/`match` arm): one statement per line, indented, closing brace back at
    * the block's own indent level. `AST.InvariantBlock.format`'s single-line `"{ " + (statements
    * .map(_.format) :+ predicate.format).mkString(" ") + " }"` was never a deliberate layout choice
    * for this construct — RIDDL statements are whitespace-separated everywhere (`pseudo_code_block`
    * has no `;`/`,` separator; disambiguation is the formatter's job, not the grammar's), and every
    * other statement-bearing block already puts one per line. The single-line rendering was the
    * narrow, un-synced SECOND copy of the block dispatch (`InvariantBlock.format` vs.
    * `RiddlFileEmitter`) behaving differently from the other five, not a design decision — Reid,
    * 2026-08-15, ruling on the review that found it. `riddlc` accepts `invariant Inv is { let a = 1
    * a > 0 }` today (verified against the staged binary, with a deliberately-broken negative
    * control so the check is known to report errors), so this is a legibility/consistency fix plus
    * closing the ascription gap below, NOT a grammar fix — the parser is untouched.
    *
    * `statements` route through `emitStatement`, the SAME total dispatch every other statement
    * position uses — so a `let`/`require` here gets the SAME `PromptValue`-ascription fix as
    * everywhere else, for free, with no capture-and-squash machinery needed (that concern only
    * existed to preserve a single-line layout this method no longer attempts). `predicate` — always
    * a `BooleanExpression`, the block's own final expression — routes through `emitValue` on its
    * own indented line, for the same reason.
    */
  def emitInvariantBlock(block: InvariantBlock): this.type =
    add("{").nl.incr
    block.statements.toSeq.foreach(emitStatement)
    addIndent("")
    emitValue(block.predicate)
    nl
    decr.addIndent("}")

  private def emitEnumeration(enumeration: Enumeration): this.type = {
    add(s"any of {").nl.incr
    val enumerators: String = enumeration.enumerators.toSeq
      .map { enumerator =>
        enumerator.id.format + enumerator.enumVal.fold("")(x => s"($x)")
      }
      .mkString(s"$spc", s",$new_line$spc", new_line)
    add(enumerators).decr.addLine("}")
    this
  }

  private def emitAlternation(alternation: Alternation): this.type = {
    add(s"one of {").nl.incr.addIndent("")
    val paths: Seq[String] =
      alternation.of.toSeq.map { (typeEx: AliasedTypeExpression) => typeEx.pathId.format }.toSeq
    add(paths.mkString("", " or ", new_line))
    decr.addIndent("}")
    this
  }

  def emitField(field: Field): this.type =
    add(s"${field.id.format}: ")
    emitTypeExpression(field.typeEx)
    emitMetaData(field.metadata)
    this
  end emitField

  def emitMethod(method: Method): this.type =
    add(s"${method.id.format}(${method.args.map(_.format).mkString(", ")}): ")
    emitTypeExpression(method.typeEx)
    emitMetaData(method.metadata)
    this
  end emitMethod

  /** Emit an aggregate's contents in the order they were authored.
    *
    * This took a `Seq[Field]` until 2026-08-14, and the two callers passed `.fields`. An
    * aggregate's contents are `Field | Method | Comment`, so every `method` and every comment in a
    * record body was dropped -- silently, since the shortened output still parsed and still
    * validated. `emitMethod` had been written for the purpose and had no callers at all.
    *
    * It walks `contents` rather than emitting `fields` and then `methods` because reflectivity
    * means exact AST recovery, sibling order included.
    */
  private def emitAggregateContents(of: Seq[AggregateContents]): this.type = {
    of.headOption match {
      case None => this.add("{ ??? }")
      // The one-line form is deliberately restricted to a lone metadata-free FIELD, which is what
      // it has always rendered. Widening it to any lone member would reformat the corpus for no
      // gain.
      case Some(field: Field) if of.sizeIs == 1 && field.metadata.isEmpty =>
        add(s"{ ")
          .emitField(field)
          .add(" }")
          .nl
      case Some(_) =>
        this.add("{").nl.incr
        of.foreach {
          case f: Field =>
            add(spc).emitField(f)
            if f.metadata.isEmpty then nl
          case m: Method =>
            add(spc).emitMethod(m)
            if m.metadata.isEmpty then nl
          // `emitComment` supplies its own indent and newline.
          case c: Comment => emitComment(c)
        }
        decr.addIndent("}").nl
    }
    this
  }

  def emitAggregation(aggregation: Aggregation): this.type = {
    emitAggregateContents(aggregation.contents.toSeq)
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
    // `table of T of [ d… ]` -- BOTH `of`s are required by `tableType`. Emitting the dimensions
    // straight onto the element type produced `table of T[ d… ]`, which is not merely ugly: it is
    // a hard parse error, so prettify was writing source riddlc rejects.
    this
      .add("table of ")
      .emitTypeExpression(table.of)
      .add(table.dimensions.mkString(" of [ ", ", ", " ]"))
  }

  private def emitReplica(replica: Replica): this.type = {
    this.add("replica of ").emitTypeExpression(replica.of)
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
    this.add(" ").emitAggregateContents(mt.contents.toSeq)
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
      case EntityReferenceTypeExpression(_, er) =>
        add(s"${Keyword.reference} to ${Keyword.entity} ${er.format}")
      case pattern: Pattern     => emitPattern(pattern)
      case uid: UniqueId        => this.add(s"${uid.format} ")
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
      case c: Currency       => this.add(s"Currency(${c.country})")
      case p: PredefinedType => this.add(p.format)
    }
  }

  def emitType(t: Type): this.type = {
    add(s"${spc}type ${t.id.format} is ")
    emitTypeExpression(t.typEx)
    emitMetaData(t.metadata)
    this
  }

  def emitStatement(statement: Statements): Unit =
    statement match
      case WhenStatement(_, cond, thenStatements, elseStatements) =>
        // A20: a `PromptValue` condition routes through `emitValue` (not `.format`) so its
        // ascription — one of the four positions `checkPromptAscription` validates — renders
        // via `emitTypeExpression`'s total dispatch rather than `PromptValue.ascriptionFormat`'s
        // narrower one. Built by chaining rather than a single interpolated string, since
        // `emitValue` appends to the builder rather than returning a `String`.
        addIndent("when ")
        cond match {
          case ls: LiteralString     => add(ls.format)
          case id: Identifier        => add(id.format)
          case vr: ValueRef          => add(vr.format) // A17
          case be: BooleanExpression => emitValue(be) // A28: structured boolean expression
          case pv: PromptValue       => emitValue(pv) // an AI-evaluated condition
        }
        add(" then").nl.incr
        if thenStatements.isEmpty then addLine("???")
        else thenStatements.toSeq.foreach(emitStatement)
        if elseStatements.nonEmpty then
          decr.addLine("else").incr
          elseStatements.toSeq.foreach(emitStatement)
        end if
        decr.addLine("end")
      case MatchStatement(_, expr, cases, default) =>
        addIndent(s"match ${expr.format} {").nl.incr
        cases.foreach { mc =>
          // A29: `case <pattern> [when <guard>] { … }`. The guard is `BooleanExpression | ValueRef`
          // — both are `Value` members — and routes through `emitValue` (not `.format`) so a
          // nested `PromptValue` ascription (e.g. `when prompt("x") as Currency(USD)`) renders
          // correctly rather than falling to `PromptValue.ascriptionFormat`.
          addIndent(s"case ${mc.pattern.format}")
          mc.guard.foreach { g =>
            add(" when "); emitValue(g)
          }
          add(" {").nl.incr
          mc.statements.toSeq.foreach(emitStatement)
          decr.addLine("}")
        }
        if default.nonEmpty then
          addIndent("default {").nl.incr
          default.toSeq.foreach(emitStatement)
          decr.addLine("}")
        end if
        decr.addLine("}")
      case ForeachStatement(_, element, valueElement, collection, doStatements) =>
        val collectionStr = collection match
          case fr: FieldRef   => fr.format
          case id: Identifier => id.format
        val elements = valueElement.fold(element.format)(v => s"${element.format}, ${v.format}")
        addIndent(s"foreach $elements in $collectionStr {").nl.incr
        if doStatements.isEmpty then addLine("???")
        else doStatements.toSeq.foreach(emitStatement)
        decr.addLine("}")
      case LetStatement(_, id, optTypeRef, expr) =>
        // A20: route through `emitValue`, not `.format` — see the `WhenStatement` case above.
        val typeClause = optTypeRef.map(t => s": ${t.format}").getOrElse("")
        addIndent(s"let ${id.format}$typeClause = ")
        emitValue(expr)
        nl
      case SetStatement(_, field, value) =>
        // A20: `set` is the other carrier `checkValueType` validates ascriptions for (alongside
        // `let`), so it needs the same `emitValue` routing. Previously fell to the generic
        // `case statement: Statement => addLine(statement.format)` arm below, which used
        // `SetStatement.format` and therefore `PromptValue.ascriptionFormat` directly.
        addIndent(s"set ${field.format} to ")
        emitValue(value)
        nl
      case PromptStatement(_, what) =>
        // A54: `do` is canonical; the deprecated `prompt` statement normalizes to `do` on emit.
        addLine(s"do ${what.format}")
      case SendStatement(_, msg, portlet) =>
        // A20: `msg` is `MessageRef | Constructor | ValueRef`; a `Constructor` argument can carry a
        // `PromptValue` ascription, so this routes through `emitConstructorOperand` (-> `emitValue`)
        // rather than `.format`.
        addIndent("send ")
        emitConstructorOperand(msg)
        add(s" to ${portlet.format}")
        nl
      case TellStatement(_, msg, processorRef, by) =>
        addIndent("tell ")
        emitConstructorOperand(msg)
        add(s" to ${processorRef.format}${by.map(b => s" by ${b.format}").getOrElse("")}")
        nl
      case YieldStatement(_, msg) =>
        addIndent("yield ")
        emitConstructorOperand(msg)
        nl
      case ReplyStatement(_, msg) =>
        addIndent("reply ")
        emitConstructorOperand(msg)
        nl
      case MorphStatement(_, entity, state, value) =>
        // A54: `value` is `RecordRef | Constructor | ValueRef`; same reasoning as `SendStatement`.
        addIndent(s"morph ${entity.format} to ${state.format} with ")
        emitConstructorOperand(value)
        nl
      case PutStatement(_, value, output) =>
        addIndent("put ")
        emitValue(value)
        add(s" to ${output.format}")
        nl
      case ReturnStatement(_, value) =>
        addIndent("return ")
        emitValue(value)
        nl
      case CodeStatement(_, lang, body) =>
        // The parser captures the body up to (but not including) the closing "```" fence, so it
        // retains the newline and indent that precede that fence. Emitting the body verbatim and
        // THEN adding `nl.addIndent("```")` grew the body by one line on every prettify
        // generation. Strip the body's trailing whitespace so re-emission is idempotent; the
        // closing fence supplies its own newline and indent.
        val trimmed = body.reverse.dropWhile(_.isWhitespace).reverse
        addIndent(s"```${lang.s}").add(trimmed).nl.addIndent("```")
      case RequireStatement(_, condition, argument) =>
        // The `with <expr>` argument is SEMANTIC — it is the value an invariant declaring
        // `requires <type>` is checked against — so dropping it on a round trip would change the
        // model, not merely its formatting. Both `condition` (when a `BooleanExpression`) and
        // `argument` are `Value`-bearing and route through `emitValue`, not `.format`, so a nested
        // `PromptValue` ascription renders correctly.
        addIndent("require ")
        condition match {
          case ls: LiteralString     => add(ls.format)
          case ir: InvariantRef      => add(ir.format)
          case be: BooleanExpression => emitValue(be) // A28
        }
        argument.foreach { a =>
          add(" with "); emitValue(a)
        }
        nl
      case TerminateStatement(_, target, args) =>
        // `target` is a VALUE since 2026-08-15, so it routes through `emitValue` rather than
        // `.format` -- it can be a `PromptValue`, and the whole point of `emitValue` being total
        // is that a nested typed hole ascribes through `emitTypeExpression` instead of falling
        // back to the narrower `ascriptionFormat`. Arguments sit behind `with (...)`, not bare
        // parens, and are omitted entirely when empty.
        addIndent("terminate ")
        emitValue(target)
        if args.nonEmpty then
          add(" with (")
          emitConstructorArgs(args)
          add(")")
        end if
        nl
      case statement: Statement => addLine(statement.format)
      case comment: Comment     => emitComment(comment)
    end match
  end emitStatement

  def emitCodeBlock(statements: Seq[Statements]): this.type = {
    if statements.isEmpty then add(" { ??? }").nl
    else
      add(" {").nl.incr
      statements.foreach(emitStatement)
      decr.addIndent("}").nl
    this
  }

  def emitUndefined(): this.type = { add(" ???") }

  def emitOption(option: OptionValue): this.type =
    addIndent(option.format + new_line)
  end emitOption

  def emitOptions(options: Seq[OptionValue]): this.type =
    if options.nonEmpty then
      options.map { option => option.format + new_line }.foreach(addIndent); this
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
