/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.project

import com.ossuminc.riddl.language.AST.*
// The trailing `*` matters: `Contents` is an OPAQUE type whose extension methods (`toSeq`,
// `filter`, …) live at PACKAGE level, not on the companion. Importing only the object leaves
// `alt.of.toSeq` unresolvable.
import com.ossuminc.riddl.language.{At, Contents, Messages, *}
import com.ossuminc.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassOutput, PassRoot, PassesOutput}
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable

/** A FLAT, machine-readable projection of a validated model: one record per AST node.
  *
  * **This is deliberately NOT the JSON round-trip surface.** `RiddlLib.root2Json` is a
  * *reflectivity* artifact built for exact AST recovery; its round-trip tests ignore locations, and
  * it carries neither spans nor resolved references. A script asking structural questions needs
  * exactly those two things, so conflating the two would either break round-trip fidelity or leave
  * this projection useless. They answer different questions and are allowed to differ.
  *
  * **Why a Pass rather than a `Finder` call.** `Finder` offers three traversal depths and none is
  * both total and parent-aware: `findByType` sees direct children only, `recursiveFindByType`
  * reaches statements held in FIELDS (`when`/`match`/`foreach` bodies) but returns no parents, and
  * `findWithParents` has parents but walks `contents` only — so a `tell` inside a `when` is
  * invisible to it. `Pass.traverse` is the one traversal that visits every `RiddlValue` with a
  * parent stack AND carries explicit arms for the field-held bodies.
  *
  * @param includeSpans
  *   emit source spans. Off by default because they roughly double the output size and a script
  *   that only counts does not need them.
  * @param resolve
  *   emit the resolved target of references. An unresolved reference is emitted EXPLICITLY as
  *   `{"ref": "X.Y", "resolved": null}` rather than omitted, because "absent" and "did not resolve"
  *   are different facts and conflating them is what hid nine errors behind three parse aborts in
  *   riddl-models' campaign.
  */
case class ProjectionPass(
  input: PassInput,
  outputs: PassesOutput,
  includeSpans: Boolean = true,
  resolve: Boolean = true
)(using PlatformContext)
    extends Pass(input, outputs) {

  requires(SymbolsPass)
  requires(ResolutionPass)

  override def name: String = ProjectionPass.name

  private lazy val resolution: ResolutionOutput =
    outputs.outputOf[ResolutionOutput](ResolutionPass.name).get
  private lazy val symbols: SymbolsOutput =
    outputs.outputOf[SymbolsOutput](SymbolsPass.name).get

  private val nodes: mutable.ListBuffer[ProjectedNode] = mutable.ListBuffer.empty

  override def process(value: RiddlValue, parents: ParentStack): Unit = {
    // The Root is the file parse-root, not model content; emitting it adds a record no query wants.
    if !value.isInstanceOf[Root] then
      val ps = parents.toParents
      nodes.append(ProjectedNode(value, ps, recordFor(value, ps)))
      value match
        case s: Statement => valueReferenceNodes(s, ps).foreach(nodes.append)
        case _            => ()
  }

  override def postProcess(root: PassRoot): Unit = ()

  override def result(root: PassRoot): ProjectionOutput =
    ProjectionOutput(root, messages.toMessages, nodes.toSeq)

  // ---------------------------------------------------------------------------------------------
  // Record construction
  // ---------------------------------------------------------------------------------------------

  private def recordFor(value: RiddlValue, parents: Parents): ujson.Obj = {
    val obj = ujson.Obj("kind" -> ujson.Str(ProjectionPass.kindOf(value)))

    value match
      case wi: WithIdentifier if wi.id.value.nonEmpty => obj("id") = ujson.Str(wi.id.value)
      case _                                          => ()

    // A path exists only for a registered Definition. A statement has none — it is identified by
    // its span and its enclosing definition, which is why both are emitted for every node.
    pathOf(value, parents).foreach(p => obj("path") = ujson.Str(p))
    val ancestry = ancestorPaths(parents)
    if ancestry.nonEmpty then
      obj("parent") = ujson.Str(ancestry.last)
      obj("ancestors") = ujson.Arr.from(ancestry.map(ujson.Str(_)))
    end if

    value.declaringFile.foreach(f => obj("file") = ujson.Str(f))
    if includeSpans then spanOf(value.loc).foreach(s => obj("span") = s)

    value match
      case wm: WithMetaData =>
        wm.brief.foreach(b => obj("brief") = ujson.Str(b.brief.s))
        val opts = wm.options.map(_.name)
        if opts.nonEmpty then obj("options") = ujson.Arr.from(opts.map(ujson.Str(_)))
      case _ => ()

    value match
      case c: Container[?] if c.isEmpty => obj("empty") = ujson.Bool(true)
      case _                            => ()

    addKindFacts(value, obj, parents)
    obj
  }

  /** Per-kind facts — the things a script actually asks about, rather than a generic dump. */
  private def addKindFacts(value: RiddlValue, obj: ujson.Obj, parents: Parents): Unit = value match {
    case p: Processor[?] =>
      obj("shape") = ujson.Str(p.effectiveShape.keyword)
      p.ascribedShape.foreach(s => obj("ascribedShape") = ujson.Str(s.keyword))
      obj("arity") = ujson.Obj("inlets" -> p.inlets.size, "outlets" -> p.outlets.size)
      p match
        case e: Entity if e.intentions.nonEmpty =>
          obj("intentions") = ujson.Arr.from(e.intentions.map(i => ujson.Str(i.toString)))
        case c: Context =>
          c.intention.foreach(i => obj("intention") = ujson.Str(i.toString))
        case _ => ()

    case inlet: Inlet  => obj("type") = typeRefRecord(inlet.type_, parents)
    case outlet: Outlet => obj("type") = typeRefRecord(outlet.type_, parents)

    case conn: Connector =>
      obj("from") = refRecord(conn.from.pathId, parents)
      obj("to") = refRecord(conn.to.pathId, parents)

    case f: Field =>
      obj("type") = ujson.Str(f.typeEx.format)
      obj("cardinality") = ujson.Str(ProjectionPass.cardinalityOf(f.typeEx))
      // Called out explicitly by riddl-models: a script fixing the every-field constructor rule
      // needs to know which missing fields may be written `empty`.
      obj("acceptsEmpty") = ujson.Bool(ProjectionPass.admitsEmpty(f.typeEx))

    case t: Type =>
      obj("type") = ujson.Str(t.typEx.format)
      obj("cardinality") = ujson.Str(ProjectionPass.cardinalityOf(t.typEx))
      messageKindOf(t.typEx, parents).foreach(k => obj("messageKind") = ujson.Str(k))
      alternationMemberRefs(t.typEx, parents).foreach(members => obj("alternation") = members)

    case h: Handler => obj("initial") = ujson.Bool(h.isInitial)

    case omc: OnMessageLikeClause if omc.msg.nonEmpty =>
      obj("message") = refRecord(omc.msg.pathId, parents)
      omc.binding.foreach(b => obj("binding") = ujson.Str(b.value))

    case s: Statement => addStatementFacts(s, obj, parents)

    case _ => ()
  }

  /** One node per VALUE REFERENCE inside a statement (riddl-models, 2026-08-25).
    *
    * The projection gives statements their span, parent and ancestors, but their OPERANDS were
    * opaque text — so consumers regexed them, and riddl-models' attempts went wrong three ways in
    * one afternoon: reading only a statement's first line (missing 6 operands inside a `when`),
    * summing over statement spans (which NEST, so a `when` counted its contents twice — 259 against
    * a true 253), and assuming every state record type ends in `Data` (a house convention, not a
    * language rule).
    *
    * **`resolvedKind` is the point.** riddlc already distinguishes these when it validates — its own
    * error enumerates "a 'let'-local, an 'on init'/'on term' parameter, a 'foreach' element, a field
    * of the handled message or entity state, or a function input" — and then throws the answer away.
    *
    * The classification here is by WHERE the resolved definition lives, which is a structural fact
    * this pass can see.
    *
    * **`let`-locals and `foreach` elements are found LEXICALLY, not through the refMap.** They are
    * not Definitions, so no lookup can find them -- which is why they read as `unresolved` until
    * 2026-08-26 -- but the binding that introduces them is right there in the enclosing clause, and
    * that is as structural as anything else here. The limit worth knowing: the search is by NAME
    * over the clause's whole statement tree, so it does not model statement ORDER or block scoping
    * the way `checkStatementScopes` does. In practice a name that reaches here has already been
    * validated as resolving to something, so a false match would need a shadowing collision that
    * validation accepted.
    *
    * **A LITERAL is not among these, and that is a ruling rather than an omission** (Reid,
    * 2026-08-26): *"While literal is a value, it is not a value-reference."* A literal has no
    * referent, so it is not emitted as a `value-reference` node at all. The same goes for a
    * `prompt(...)` hole. Do not add `literal` or `prompt` to this vocabulary.
    */
  private def valueReferenceNodes(s: Statement, parents: Parents): Seq[ProjectedNode] =
    if !resolve then Seq.empty
    else
      valueRefsOf(s).map { vr =>
        val obj = ujson.Obj(
          "kind" -> ujson.Str("value-reference"),
          "name" -> ujson.Str(vr.path.format),
          "statement" -> ujson.Str(ProjectionPass.kindOf(s))
        )
        parents.headOption.foreach(p => obj("parent") = ujson.Str(symbols.pathOf(p).reverse.mkString(".")))
        val resolved = parents.headOption.flatMap(p => resolution.refMap.anyDefinitionOf(vr.path, p))
        resolved match
          case Some(d) =>
            obj("resolvedTo") = ujson.Str(symbols.pathOf(d).reverse.mkString("."))
            obj("resolvedKind") = ujson.Str(classifyOperand(d, parents))
          case None =>
            obj("resolvedTo") = ujson.Null
            obj("resolvedKind") = ujson.Str(lexicalBinding(vr, parents).getOrElse("unresolved"))
        spanOf(vr.loc).foreach(sp => obj("span") = sp)
        ProjectedNode(vr, parents, obj)
      }

  /** A binding introduced by the enclosing clause rather than by a definition.
    *
    * `let x = …` and `foreach e in …` bind names the symbol table never sees, so a refMap lookup
    * cannot classify them and they fell through to `unresolved`. Both are visible in the clause's
    * own statement tree.
    */
  private def lexicalBinding(vr: ValueRef, parents: Parents): Option[String] =
    val head = vr.path.value.headOption.getOrElse("")
    if head.isEmpty then None
    else
      def walk(stmts: Seq[Statements]): Option[String] =
        stmts.foldLeft(Option.empty[String]) { (found, st) =>
          found.orElse {
            st match
              case ls: LetStatement if ls.identifier.value == head    => Some("let-local")
              case fs: ForeachStatement if fs.element.value == head   => Some("foreach-element")
              case fs: ForeachStatement
                  if fs.valueElement.exists(_.value == head)          => Some("foreach-element")
              case fs: ForeachStatement                               => walk(fs.doStatements.toSeq)
              case ws: WhenStatement                                  =>
                walk(ws.thenStatements.toSeq).orElse(walk(ws.elseStatements.toSeq))
              case ms: MatchStatement                                 =>
                walk(ms.cases.toSeq.flatMap(_.statements.toSeq))
              case _                                                  => None
          }
        }
      parents.headOption.collect { case b: Branch[?] => b }.flatMap { b =>
        walk(b.contents.toSeq.collect { case st: Statements => st })
      }
  end lexicalBinding

  /** Where the operand's definition lives: the fact a consumer actually wants. */
  private def classifyOperand(d: Definition, parents: Parents): String = {
    val owner = symbols.parentOf(d)
    val stateRecords: Seq[Type] = parents
      .collectFirst { case e: Entity => e }
      .toSeq
      .flatMap(_.states)
      .flatMap(st => resolution.refMap.definitionOf[Type](st.typ.pathId))
    // Keyed to the on-clause's OWN PARENT, which is the key ResolutionPass recorded it under. The
    // parent-agnostic overload missed it, and the field then fell through to the generic
    // `field-of-event` arm -- true, but not the distinction a consumer asked for.
    val idx = parents.indexWhere(_.isInstanceOf[OnMessageLikeClause])
    val handledMessage: Option[Type] =
      if idx < 0 then None
      else
        val omc = parents(idx).asInstanceOf[OnMessageLikeClause]
        parents
          .lift(idx + 1)
          .flatMap(p => resolution.refMap.definitionOf[Type](omc.msg.pathId, p))
          .orElse(resolution.refMap.definitionOf[Type](omc.msg.pathId))
    owner match
      case Some(o) if stateRecords.exists(_ eq o)      => "state-field"
      case Some(o) if handledMessage.exists(_ eq o)    => "message-field"
      case Some(_: Function)                           => "function-input"
      case Some(o)                                     => s"field-of-${ProjectionPass.kindOf(o)}"
      case None                                        => "unknown"
  }

  /** Every [[ValueRef]] a statement carries, including those nested in constructor arguments. */
  private def valueRefsOf(s: Statement): Seq[ValueRef] = {
    def fromValue(v: Value): Seq[ValueRef] = v match
      case vr: ValueRef   => Seq(vr)
      case c: Constructor => c.args.toSeq.flatMap(a => fromValue(a.value))
      case _              => Seq.empty
    s match
      case m: MorphStatement =>
        m.value match
          case v: ValueRef    => Seq(v)
          case c: Constructor => fromValue(c)
          case _              => Seq.empty
      case st: SetStatement  => fromValue(st.value)
      case t: TellStatement  => operandRefs(t.msg)
      case sd: SendStatement => operandRefs(sd.msg)
      case y: YieldStatement => operandRefs(y.msg)
      case r: ReplyStatement => operandRefs(r.msg)
      case _                 => Seq.empty
  }

  private def operandRefs(m: MessageRef | Constructor | ValueRef): Seq[ValueRef] = m match
    case vr: ValueRef   => Seq(vr)
    case c: Constructor => c.args.toSeq.flatMap(a => a.value match
        case v: ValueRef    => Seq(v)
        case cc: Constructor => operandRefs(cc)
        case _              => Seq.empty
      )
    case _ => Seq.empty

  private def addStatementFacts(s: Statement, obj: ujson.Obj, parents: Parents): Unit = s match {
    case t: TellStatement =>
      obj("target") = t.target match
        case pr: ProcessorRef[?] => refRecord(pr.pathId, parents)
        case v: Value            => ujson.Obj("value" -> ujson.Str(v.format))
      messageOperand(t.msg, parents).foreach(m => obj("message") = m)
    case sd: SendStatement =>
      obj("target") = refRecord(sd.portlet.pathId, parents)
      messageOperand(sd.msg, parents).foreach(m => obj("message") = m)
    case f: ForwardStatement =>
      obj("target") = f.target match
        case pr: PortletRef[?]   => refRecord(pr.pathId, parents)
        case pr: ProcessorRef[?] => refRecord(pr.pathId, parents)
      messageOperand(f.msg, parents).foreach(m => obj("message") = m)
    case m: MorphStatement =>
      obj("target") = refRecord(m.entity.pathId, parents)
      obj("state") = refRecord(m.state.pathId, parents)
    case st: SetStatement => obj("target") = ujson.Str(st.field.pathId.format)
    case y: YieldStatement => messageOperand(y.msg, parents).foreach(m => obj("message") = m)
    case r: ReplyStatement => messageOperand(r.msg, parents).foreach(m => obj("message") = m)
    case _                 => ()
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  private def messageOperand(
    m: MessageRef | Constructor | ValueRef,
    parents: Parents
  ): Option[ujson.Value] = m match {
    case mr: MessageRef => Some(refRecord(mr.pathId, parents))
    case c: Constructor => Some(refRecord(c.ref.pathId, parents))
    case vr: ValueRef   => Some(ujson.Obj("value" -> ujson.Str(vr.path.format)))
  }

  /** A reference, ALWAYS carrying whether it resolved. See the `resolve` parameter for why an
    * unresolved reference is emitted with an explicit `null` rather than dropped.
    */
  private def refRecord(pid: PathIdentifier, parents: Parents): ujson.Obj = {
    val o = ujson.Obj("ref" -> ujson.Str(pid.format))
    if resolve then
      o("resolved") = lookup(pid, parents) match
        case Some(d) => ujson.Str(symbols.pathOf(d).reverse.mkString("."))
        case None    => ujson.Null
    end if
    o
  }

  /** Resolve a written path to the definition it names.
    *
    * **`anyDefinitionOf`, not `definitionOf[Definition]`.** The single-argument `definitionOf`
    * overload tests `definition.getClass == klass`, which is EXACT class equality and therefore can
    * never match a trait — asking it for a `Definition` returns `None` for everything. The first
    * version of this projection used it and emitted `"resolved": null` on all 5,897 records of a
    * model that resolves perfectly well. The two-argument `definitionOf` is type-correct but ADDS
    * AN ERROR to the message accumulator on a mismatch, which a read-only projection must not do.
    *
    * The symbol-table fallback is the second lookup `resolveIdTarget` documents needing: the refMap
    * is keyed by (path, parent) for paths that were WRITTEN, so a synthesized or differently-scoped
    * path misses it while the symbol table still knows the name.
    */
  private def lookup(pid: PathIdentifier, parents: Parents): Option[Definition] =
    parents.headOption
      .flatMap(p => resolution.refMap.anyDefinitionOf(pid, p))
      .orElse(symbols.lookup[Definition](pid.value.reverse).headOption)

  private def resolveType(pid: PathIdentifier, parents: Parents): Option[Type] =
    lookup(pid, parents).collect { case t: Type => t }

  private def typeRefRecord(tr: TypeRef, parents: Parents): ujson.Obj = {
    val o = refRecord(tr.pathId, parents)
    if resolve then
      resolution.refMap
        .definitionOf[Type](tr.pathId.format)
        .foreach { t =>
          messageKindOf(t.typEx, parents).foreach(k => o("carries") = ujson.Str(k))
          alternationMemberRefs(t.typEx, parents).foreach(members => o("alternation") = members)
        }
    end if
    o
  }

  /** The members an alternation admits, each resolved. `None` when the type is not an alternation,
    * so the key is absent rather than an empty array — "not an alternation" and "an alternation
    * with no members" are different facts.
    */
  private def alternationMemberRefs(te: TypeExpression, parents: Parents): Option[ujson.Arr] =
    te match {
      case alt: Alternation =>
        Some(ujson.Arr.from(alt.of.toSeq.map { a =>
          val o = refRecord(a.pathId, parents)
          // Each member carries its OWN message kind. Without this an alternation-typed port has no
          // `carries` anywhere -- the union itself is not an AggregateUseCaseTypeExpression, so
          // `messageKindOf` answers None for it -- and "every repository inlet whose type resolves
          // to an event" silently returns zero against a corpus where `type XEvent is one of {...}`
          // is the prevailing idiom. That is the same alternation blindness the delivery checks
          // shipped with and had to be corrected for.
          if resolve then
            resolveType(a.pathId, parents)
              .flatMap(t => messageKindOf(t.typEx, parents))
              .foreach(k => o("carries") = ujson.Str(k))
          end if
          o
        }))
      case _ => None
    }

  private def messageKindOf(te: TypeExpression, parents: Parents): Option[String] = te match {
    case auc: AggregateUseCaseTypeExpression => Some(auc.usecase.useCase)
    // An alternation reports a kind only when EVERY member agrees. A mixed union has no single
    // answer, and inventing one would make `-carries event` true of a type that also admits
    // commands -- per-member `carries` is what a caller should read in that case.
    case alt: Alternation =>
      val kinds = alt.of.toSeq
        .flatMap(a => resolveType(a.pathId, parents))
        .flatMap(t => messageKindOf(t.typEx, parents))
        .distinct
      if kinds.sizeIs == 1 && alt.of.toSeq.sizeIs > 0 then kinds.headOption else None
    case ate: AliasedTypeExpression =>
      resolution.refMap.definitionOf[Type](ate.pathId.format).flatMap(t => messageKindOf(t.typEx, parents))
    case _ => None
  }

  /** `Root` is EXCLUDED from every path.
    *
    * `Pass.traverse` pushes the `Root` onto the parent stack, but it is the file parse-root rather
    * than model content — `SymbolsPass` filters it out of the symbol table for the same reason, so
    * a path that included it would not match the one every other part of riddlc reports.
    */
  private def namedAncestors(parents: Parents): Seq[String] =
    parents.reverse.collect { case b: Branch[?] if !b.isInstanceOf[Root] => b.id.value }
      .filter(_.nonEmpty)

  private def pathOf(value: RiddlValue, parents: Parents): Option[String] = value match {
    case _: Root => None
    case d: Definition if d.id.value.nonEmpty =>
      Some((namedAncestors(parents) :+ d.id.value).mkString("."))
    case _ => None
  }

  private def ancestorPaths(parents: Parents): Seq[String] = {
    val names = namedAncestors(parents)
    names.inits.toSeq.reverse.drop(1).map(_.mkString("."))
  }

  /** `At.line`/`col` return **0**, not >= 1, when `positionsKnown` is false — which is the case for
    * a BAST-reconstructed input. Emitting 0 blindly makes every record look broken, so the span is
    * omitted entirely rather than reported as line zero.
    */
  private def spanOf(loc: At): Option[ujson.Obj] =
    if loc.isEmpty || loc.line == 0 then None
    else
      Some(
        ujson.Obj(
          "start" -> ujson.Obj("line" -> loc.line, "col" -> loc.col, "offset" -> loc.offset),
          "end" -> ujson.Obj("line" -> loc.endLine, "offset" -> loc.endOffset)
        )
      )
}

/** One projected node: the AST value, its parent chain, and its JSON record.
  *
  * `dump` needs only the record; `find` needs the value and parents too, because predicates such as
  * `-under-a <kind>` ask about the ANCESTORS' kinds, which a flat record cannot express without
  * duplicating the whole chain into every record. One pass serves both.
  */
case class ProjectedNode(value: RiddlValue, parents: Parents, record: ujson.Obj)

case class ProjectionOutput(
  root: PassRoot,
  messages: Messages.Messages,
  nodes: Seq[ProjectedNode]
) extends PassOutput {
  def records: Seq[ujson.Obj] = nodes.map(_.record)
}

object ProjectionPass {
  val name: String = "Projection"

  /** The `-type`-facing name of a node.
    *
    * `RiddlValue.kind` is a DISPLAY string with spaces (`"Tell Statement"`, `"Numeric Literal"`),
    * which is unusable as a query token, so it is lower-cased and de-spaced here. This is the
    * single source of truth for the vocabulary and `find`'s `-type` will read it, rather than
    * growing a second table that drifts as the AST does.
    */
  def kindOf(value: RiddlValue): String =
    value.kind.trim.toLowerCase.replace(" ", "-")

  def cardinalityOf(te: TypeExpression): String = te match {
    case _: Optional       => "optional"
    case _: ZeroOrMore     => "zero-or-more"
    case _: OneOrMore      => "one-or-more"
    case sr: SpecificRange => s"range(${sr.min},${sr.max})"
    case _                 => "exactly-one"
  }

  /** Whether a type admits an empty value — its MINIMUM cardinality is zero. Mirrors
    * `ValidationPass.admitsEmpty`; kept in step by hand because the two modules cannot share it.
    */
  def admitsEmpty(te: TypeExpression): Boolean = te match {
    case _: Optional       => true
    case _: ZeroOrMore     => true
    case sr: SpecificRange => sr.min == 0
    case _                 => false
  }
}
