/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.{Entity, *}
import com.ossuminc.riddl.language.parsing.{Keyword, PredefTypes}
import com.ossuminc.riddl.language.{At, Contents, Messages, *}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.symbols.Symbols.*
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

case class ResolutionOutput(
  root: PassRoot = Root.empty,
  messages: Messages.Messages = Messages.empty,
  refMap: ReferenceMap = ReferenceMap.empty,
  usage: Usages = Usages.empty
) extends PassOutput {}

object ResolutionPass extends PassInfo[PassOptions] {
  val name: String = "Resolution"
  def creator(options: PassOptions = PassOptions.empty)(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => ResolutionPass(in, out)
  }
}

/** The Reference Resolution Pass. This pass traverses the entire model and resolves every reference
  * it finds into the `refmap` in its output. See [[ReferenceMap]] for details. This resolution must
  * be done before validation to make sure there are no cycles in the references. While it is at it,
  * it also tracks which definition uses which other definition. See [[Usages]] for details. It also
  * keeps a `kindMap`. See [[KindMap]] for details.
  *
  * Reference Resolution is the process of turning a
  * [[com.ossuminc.riddl.language.AST.PathIdentifier]] into the
  * [[com.ossuminc.riddl.language.AST.Definition]] that is referenced by the
  * [[com.ossuminc.riddl.language.AST.PathIdentifier]]. There are several ways to resolve a
  * reference:
  *
  *   1. If its already in the [[ReferenceMap]] then use that resolution
  *   1. A single identifier in the path is looked up in the symbol table and if it uniquely matches
  *      only one definition then that definition is the resolved definition.
  *   1. If there are multiple identifiers in the [[com.ossuminc.riddl.language.AST.PathIdentifier]]
  *      then we attempt to anchor the search using the first identifier. Anchoring is done by (a)
  *      checking to see if it is the "Root" node in which case that is the anchor, (b) checking to
  *      see if the first identifier is the name of one of the parent nodes from the location of the
  *      reference, and finally (c) looking up the first identifier in the symbol table and if it is
  *      unique then using that as the anchor. Once the anchor is determined, it is simply a matter
  *      of walking down tree of nodes from the anchor, one name at a time.
  *
  * @param input
  *   The input to the original pass.
  * @param outputs
  *   THe outputs from preceding passes, which should only be the
  *   [[com.ossuminc.riddl.passes.symbols.SymbolsPass]] output.
  */
case class ResolutionPass(input: PassInput, outputs: PassesOutput)(using io: PlatformContext)
    extends Pass(input, outputs)
    with UsageResolution {

  override def name: String = ResolutionPass.name

  requires(SymbolsPass)

  val refMap: ReferenceMap = ReferenceMap(messages)
  val kindMap: KindMap = KindMap()
  val symbols: SymbolsOutput = outputs.outputOf[SymbolsOutput](SymbolsPass.name).get

  override def result(root: PassRoot): ResolutionOutput =
    ResolutionOutput(
      root,
      messages.toMessages,
      refMap,
      Usages(uses, usedBy, usesInPath, usedInPathBy)
    )

  override def close(): Unit = ()

  override def postProcess(root: PassRoot): Unit = {
    // A55: ValueRefs are resolved LAST, after every other reference is in the refMap. Their
    // value-scope anchors are reached THROUGH other references (an on-clause's `msg`, a `state`'s
    // record, a function's `requires`), and the pass visits definitions in source order — so a
    // handler written above the state it reads would otherwise see an unresolved record.
    deferredValueRefs.foreach { case (vr, parents) => resolveValueRef(vr, parents) }
    deferredValueRefs.clear()
    checkUnused()
  }

  def process(value: RiddlValue, parentsStack: ParentStack): Unit =
    val parents: Parents =
      value match
        case p: Branch[?] =>
          kindMap.add(p)
          p +: parentsStack.toParents
        case _ => parentsStack.toParents
      end match
    // Resolve the AuthorRefs in metadata of definitions
    value match {
      case d: Definition => resolveAuthorRefs(d, parents)
      case _             => ()
    }

    value match
      case av: AggregateValue => // Field, Method
        val resolution = resolveTypeExpression(av, av.typeEx, parents)
        associateUsage[Type](av, resolution)
      // Task 3: an `on init`/`on term` parameter. `MethodArgument` is not a Definition (it can't
      // own usages of its own), so `parents.head` -- the enclosing on-clause, pushed by Pass.scala's
      // OnInitializationClause/OnTerminationClause traverse cases -- is the anchor/user instead.
      // This is what makes `on init(x: Nonexistent)` produce a "not resolved" message rather than
      // validating clean: `Id(...)` resolves as a Processor via the UniqueId arm below, and every
      // other type expression resolves the ordinary way.
      case ma: MethodArgument =>
        associateUsage[Type](parents.head, resolveTypeExpression(parents.head, ma.typeEx, parents))
      case t: Type =>
        associateUsage[Type](t, resolveType(t, parents))
      case mc: OnMessageLikeClause => // OnMessageClause and OnEventClause both carry a msg ref
        resolveOnMessageClause(mc, parents)
      case statement: Statement =>
        resolveStatement(statement, parents)
      case _: OnInitializationClause => ()
      case _: OnTerminationClause    => ()
      case _: OnActivationClause     => ()
      case _: OnPassivationClause    => ()
      case _: OnOtherClause          => ()
      case e: Entity =>
        addEntity(e, parents)
      case s: State =>
        // A9b: state.typ is a RecordRef; resolve generically (record-kind check is in validation).
        associateUsage(s, resolveARef[Type](s.typ, parents))
      case c: Correlation =>
        // A70: `yields` is a FIELD, so nothing resolves it as part of traversing contents -- the
        // same reason State.typ is resolved here. Without this the completeness check could not
        // reach the target record's fields at all.
        associateUsage(c, resolveARef[Type](c.yields, parents))
      case f: Function =>
        resolveFunction(f, parents)
      case i: Inlet =>
        associateUsage(i, resolveATypeRef(i.type_, parents))
      case o: Outlet =>
        val resolution = resolveATypeRef(o.type_, parents)
        associateUsage(o, resolution)
      case c: Connector =>
        associateUsage(c, resolveARef[Outlet](c.from, parents))
        associateUsage(c, resolveARef[Inlet](c.to, parents))
      case c: Constant =>
        associateUsage(c, resolveTypeExpression(c, c.typeEx, parents))
      case a: Adaptor =>
        associateUsage(a, resolveARef[Context | Group](a.referent, parents))
      case s: Streamlet =>
      case p: Projector =>
        p.repositories.foreach { ref => associateUsage(p, resolveARef[Repository](ref, parents)) }
      case r: Repository =>
        addRepository(r, parents)
      case s: Saga =>
        // A9: resolve saga requires/returns (previously unresolved).
        s.input.foreach(resolveRequiresReturns(s, _, parents))
        s.output.foreach(resolveRequiresReturns(s, _, parents))
      case r: Relationship =>
        resolveARef[Processor[?]](r.withProcessor, parents)
      case m: Module  =>
      case d: Domain  =>
      case c: Context =>
      case e: Epic    =>
      case uc: UseCase =>
        if uc.userStory.nonEmpty then
          associateUsage[User](uc, resolveARef(uc.userStory.user, parents))
        val interactions = uc.contents.filter[Interaction]
        if interactions.nonEmpty then resolveInteractions(uc, interactions, parents)
      case in: Input =>
        associateUsage(in, resolveATypeRef(in.takeIn, parents))
      case out: Output =>
        out.putOut match {
          case typ: TypeRef       => associateUsage(out, resolveATypeRef(typ, parents))
          case const: ConstantRef => associateUsage(out, resolveARef[Constant](const, parents))
          case _: LiteralString   => () // not a reference
        }
      case cg: ContainedGroup =>
        associateUsage(cg, resolveARef[Group](cg.group, parents))
      // A schema's references live in FIELDS (`data`, `links`, `indices`), not in `contents`, so
      // nothing resolved them and `Schema` had no case of its own — it fell through to the
      // catch-all `case _: Definition` below. Two consequences: a type the model demonstrably
      // persists was reported unused, and `of <name> as type <T>` was never checked at all.
      //
      // Resolved STRICTLY, on purpose. The syntax says `as type`, so `T` must BE a type: a path
      // that lands on an Entity is a semantic error even though it parses, and it stays an error
      // unless a Type of that name genuinely exists. Models that wrote `of orders as type Order`
      // against an entity were relying on a check that never ran.
      //
      // Must stay ABOVE the NonDefinitionValues arm: Schema is a Leaf but also a member of that
      // union, so a later case would shadow this one.
      case sc: Schema =>
        sc.data.values.foreach(tr => associateUsage(sc, resolveATypeRef(tr, parents)))
      // `links` and `indices` are deliberately NOT resolved here yet. They hold FieldRefs, and
      // resolving them surfaces references that have never been checked: `language/input/
      // everything_full.riddl` alone has `link relationship as field agg.time to field agg.ident`
      // where `agg` has no `ident` field. Turning that into a new class of error belongs in its
      // own change, with the corpus checked first — not folded into a fix for unused-type false
      // positives.
      case inv: Invariant =>
        // A28: resolve operand refs inside a structured BooleanExpression condition (a LiteralString
        // condition has none). A block condition carries BOTH statements and a predicate, and the
        // statements are where a `let` or a `call` puts references — resolving only the predicate
        // would leave those dangling.
        inv.condition.foreach {
          case be: BooleanExpression => resolveValue(be, parents)
          case _: LiteralString      => ()
          case blk: InvariantBlock =>
            blk.statements.toSeq.foreach {
              case st: Statement => resolveStatement(st, parents)
              case _: Comment    => () // a comment holds no references
            }
            resolveValue(blk.predicate, parents)
        }
        // `requires state S` / `requires <type>` name real definitions and must resolve, or the
        // scope the invariant claims is unchecked.
        inv.requires.foreach {
          case sr: StateRef => resolveARef[State](sr, parents)
          case tr: TypeRef  => resolveATypeRef(tr, parents)
        }
      // A BASTImport holds no references of its own. Its .bast file was already read by
      // BASTLoader at parse time (TopLevelParser.loadBASTImports) and its contents are traversed
      // as if they sat in the enclosing container, so the imported definitions resolve normally.
      case _: BASTImport => ()
      case _: MatchCase => () // MatchCase statements contain references handled in resolveStatement
      case _: MatchPattern => () // A29: pattern refs are resolved in resolveMatchParts
      case _: NonReferencableDefinitions => () // These can't be referenced
      case _: NonDefinitionValues        => () // Neither can these values
      case _: Definition                 => () // abstract definition, can't be referenced
      // case _ => () // NOTE: Never have this catchall! Want compile time errors!
    end match
  end process

  private def resolveAuthorRefs(definition: Definition, parents: Parents): Unit =
    definition.authorRefs.foreach { item =>
      associateUsage(definition, resolveARef[Author](item, parents))
    }
  end resolveAuthorRefs

  private def resolveFunction(f: Function, parents: Parents): Unit = {
    addFunction(f, parents)
    f.authorRefs.foreach { item => associateUsage[Author](f, resolveARef[Author](item, parents)) }
    f.input.foreach(resolveRequiresReturns(f, _, parents))
    f.output.foreach(resolveRequiresReturns(f, _, parents))
  }

  // A9: `requires`/`returns` name a Type (resolve the ref and record usage) or, deprecated,
  // carry an inline Aggregation (resolve it as a type expression).
  private def resolveRequiresReturns(
    user: Definition,
    value: TypeRef | Aggregation,
    parents: Parents
  ): Unit = value match {
    case tr: TypeRef      => associateUsage[Type](user, resolveATypeRef(tr, parents))
    case agg: Aggregation => resolveTypeExpression(user, agg, parents)
  }

  private def resolveType(typ: Type, parents: Parents): Resolution[Type] = {
    addType(typ, parents)
    resolveTypeExpression(typ, typ.typEx, parents)
  }

  private def resolveTypeExpression(
    user: Definition,
    typ: TypeExpression,
    parents: Parents
  ): Resolution[Type] = {
    typ match {
      case UniqueId(_, entityPath, _) =>
        // Id(P) now names any Processor, not only an Entity -- widen the resolution to match,
        // or a Repository/Adaptor/etc. target never even gets a refMap entry and TypeValidation's
        // checkPathRef[Processor[?]] reports "not resolved" despite being individually correct.
        val resolution = resolveAPathId[Processor[?]](entityPath, parents)
        associateUsage[Processor[?]](user, resolution)
        None
      case AliasedTypeExpression(_, _, pathId) =>
        associateUsage[Type](user, resolveAPathId[Type](pathId, parents))
      case auc: AggregateUseCaseTypeExpression =>
        auc.fields.foreach { (fld: Field) =>
          associateUsage[Type](fld, resolveTypeExpression(fld, fld.typeEx, parents))
        }
        // A19: a `yields <messageRef>` clause references a message type; register it as a usage so
        // a message referenced only by a `yields` clause is not flagged as unused.
        auc.yields.foreach { y =>
          associateUsage[Type](user, resolveARef[Type](y, parents))
        }
        None
      case agg: AggregateTypeExpression =>
        agg.fields.foreach { (fld: Field) =>
          associateUsage[Type](fld, resolveTypeExpression(fld, fld.typeEx, parents))
        }
        None
      case EntityReferenceTypeExpression(_, entity) =>
        associateUsage[Entity](user, resolveAPathId[Entity](entity, parents))
        None
      case Alternation(_, of) =>
        of.foreach(resolveTypeExpression(user, _, parents))
        None
      case Sequence(_, of) =>
        resolveTypeExpression(user, of, parents)
      case Mapping(_, from, to) =>
        // BOTH halves must resolve. `to` was discarded here until 2026-08-10, so a mapping's VALUE
        // type reference never entered the refMap: `mapping from Integer to Nonexistent` validated
        // clean while the same name in the key position errored, and nothing downstream could see
        // through the alias to the record it named. Found because `foreach k, v` typed `v` from
        // `to` and worked only when some OTHER field happened to reference the same type -- the
        // refMap is keyed by path, so one resolved occurrence silently covered for the missing one.
        //
        // The `from` resolution is what is RETURNED, unchanged: the caller associates usage with
        // it, and widening that is a separate question from resolving the reference at all.
        resolveTypeExpression(user, to, parents)
        resolveTypeExpression(user, from, parents)
      case Set(_, of) =>
        resolveTypeExpression(user, of, parents)
      case Graph(_, of) =>
        resolveTypeExpression(user, of, parents)
      case Table(_, of, _) =>
        resolveTypeExpression(user, of, parents)
      case Replica(_, of) =>
        resolveTypeExpression(user, of, parents)
      case c: Cardinality =>
        associateUsage[Type](user, resolveTypeExpression(user, c.typeExp, parents))
      case _: Enumeration | _: NumericType | _: PredefinedType => None // no references
    }
  }

  private def resolveOnMessageClause(mc: OnMessageLikeClause, parents: Parents): Unit = {
    val resolution = resolveARef[Type](mc.msg, parents)
    associateUsage[Type](mc, resolution)
    mc.from match
      case None => ()
      case Some(_, reference) =>
        val resolution = resolveARef[Definition](reference, parents)
        associateUsage[Definition](mc, resolution)
  }

  /** A70: the [[Field]] a bare `set field <name>` denotes inside a [[Correlation]]'s fold.
    *
    * A70 chose the bare form deliberately, and its rationale is the scoping: the enclosing
    * Correlation says which record the name belongs to. That matters most where a qualified name
    * could not help — two correlations in one projector yielding the SAME record type keyed
    * differently are told apart by which correlation the fold sits in, not by the field's path.
    *
    * Only a SINGLE-component path is claimed here. A qualified `set field Some.Other.field` keeps
    * the ordinary symbol-table route, so nothing that resolved before resolves differently, and a
    * bare name that is not a field of the target falls through to the ordinary route too — where it
    * gets the usual "not resolved" diagnostic rather than being silently accepted.
    */
  private def correlationTargetField(fr: FieldRef, parents: Parents): Option[Field] =
    if fr.pathId.value.sizeIs != 1 then None
    else
      parents.collectFirst { case c: Correlation => c }.flatMap { correlation =>
        refMap.definitionOf[Type](correlation.yields.pathId).flatMap { typ =>
          typ.typEx match
            case ate: AggregateTypeExpression => ate.fields.find(_.id.value == fr.pathId.value.head)
            case _                            => None
        }
      }
  end correlationTargetField

  private def resolveStatement(statement: Statement, parents: Parents): Unit = {
    statement match {
      case SetStatement(_, field, value) =>
        field match
          case fr: FieldRef =>
            correlationTargetField(fr, parents) match
              case Some(target) => refMap.add[Field](fr.pathId, parents.head, target)
              case None => associateUsage[Field](parents.head, resolveARef[Field](fr, parents))
          case sr: StateRef => associateUsage[State](parents.head, resolveARef[State](sr, parents))
        // A54: resolve the value expression (constructor refs, get sources).
        resolveValue(value, parents)
      case BecomeStatement(_, entity, handler) =>
        associateUsage[Entity](parents.head, resolveARef[Entity](entity, parents))
        associateUsage[Handler](parents.head, resolveARef[Handler](handler, parents))
      case SendStatement(_, msg, portlet) =>
        resolveMessageOperand(msg, parents)
        associateUsage[Portlet](parents.head, resolveARef[Portlet](portlet, parents))
      case MorphStatement(_, entity, state, message) =>
        associateUsage[Entity](parents.head, resolveARef[Entity](entity, parents))
        associateUsage[State](parents.head, resolveARef[State](state, parents))
        resolveMessageOperand(message, parents)
      case TellStatement(_, msg, target, _) =>
        // Enumerated, not wildcarded, for the same reason `forward` below is: the union has exactly
        // two members and a wildcard would silently stop resolving a third.
        resolveMessageOperand(msg, parents)
        target match
          case processor: ProcessorRef[?] =>
            associateUsage(parents.head, resolveARef[Processor[?]](processor, parents))
          case value: Value =>
            // A value target names WHICH INSTANCE to tell. Resolving the value is what puts its
            // field (and so its `Id(entity E)` type) in the refMap, which is what lets every later
            // pass answer "which processor" with one lookup.
            resolveValue(value, parents)
      case ForwardStatement(_, msg, target) =>
        // `forward` takes BOTH transmission shapes, so the target is resolved as whichever it is.
        // Enumerated rather than given a wildcard: the union has exactly two members, and a
        // wildcard here would silently stop resolving a third if one were ever added.
        resolveMessageOperand(msg, parents)
        target match
          case portlet: PortletRef[?] =>
            associateUsage[Portlet](parents.head, resolveARef[Portlet](portlet, parents))
          case processor: ProcessorRef[?] =>
            associateUsage(parents.head, resolveARef[Processor[?]](processor, parents))
      case _: PromptStatement => () // no references
      case _: ErrorStatement  => () // no references
      case rs: RequireStatement =>
        rs.condition match {
          case _: LiteralString      => () // no references
          case ir: InvariantRef      => resolveARef[Invariant](ir, parents)
          case be: BooleanExpression => resolveValue(be, parents) // A28: resolve operand refs
        }
      case YieldStatement(_, msg) =>
        resolveMessageOperand(msg, parents)
      case ReplyStatement(_, msg) =>
        resolveMessageOperand(msg, parents)
      case ws: WhenStatement =>
        // A28: a BooleanExpression condition may carry operand refs; the LiteralString/Identifier
        // forms have none. A17: a bare boolean ValueRef condition is resolved via resolveValue (its
        // four-source resolution is deferred to validation, like every other bare ValueRef). A nested
        // foreach's field ref must also be resolved so validation can find it in the refMap.
        ws.condition match {
          case be: BooleanExpression => resolveValue(be, parents)
          case vr: ValueRef          => resolveValue(vr, parents) // A17
          case _                     => ()
        }
        resolveForeachFieldRefs(ws.thenStatements, parents)
        resolveForeachFieldRefs(ws.elseStatements, parents)
      case ms: MatchStatement =>
        // A29: resolve the subject, each pattern's TypeRef/comparand refs, and each guard's operand
        // refs; then resolve any nested foreach field refs in the case/default bodies.
        resolveMatchParts(ms, parents)
        ms.cases.foreach(mc => resolveForeachFieldRefs(mc.statements, parents))
        resolveForeachFieldRefs(ms.default, parents)
      case fs: ForeachStatement =>
        // A25: resolve the collection's FieldRef (a bare Identifier collection is a local, resolved
        // at validation time). The pass framework does not descend into nested statement bodies, so
        // recurse to reach any foreach nested under this one.
        fs.collection match
          case fr: FieldRef  => associateUsage[Field](parents.head, resolveARef[Field](fr, parents))
          case _: Identifier => ()
        resolveForeachFieldRefs(fs.doStatements, parents)
      case ls: LetStatement =>
        // Defect 2 (2026-08-15): a predefined type keyword (`let x: Natural = …`) parses into an
        // ordinary TypeRef via the same grammar a user-declared alias uses, but predefined types
        // are deliberately never entered into the symbol table (see `PredefinedModule`'s note on
        // why the standard module stays out of the shared maps) -- so `resolveARef` could never
        // find one and every such ascription failed with "not resolved". Skip resolution for the
        // keywords `PredefTypes.typeExpressionFor` can construct without arguments;
        // `ValidationPass.letType`/`checkStatementScopes` special-case the same set directly. A
        // keyword that NEEDS arguments (`Currency`, `Decimal`, …) still goes through the ordinary
        // path and is unaffected -- a bare `let x: Currency = …` is incomplete regardless.
        ls.typeRef.foreach { tr =>
          val isBarePredefinedKeyword = tr.pathId.value.sizeIs == 1 &&
            PredefTypes.typeExpressionFor(tr.pathId.value.head, tr.loc).isDefined
          if !isBarePredefinedKeyword then resolveARef[Type](tr, parents)
        }
        // A54: resolve the bound value expression (constructor refs, get sources).
        resolveValue(ls.expression, parents)
      case PutStatement(_, v, output) =>
        // A45: resolve the value expression and the output target.
        resolveValue(v, parents)
        associateUsage[Output](parents.head, resolveARef[Output](output, parents))
      case ReturnStatement(_, v) =>
        // A57: resolve the value expression (typed against Function.output at validation time).
        resolveValue(v, parents)
      case TerminateStatement(_, target, args) =>
        // A70/instance-identity: `target` is a VALUE typed `Id(entity E)` since 2026-08-15, not a
        // processor ref, so it resolves through `resolveValue` like any other value expression.
        // The Entity being terminated is DERIVED from the target's type in ValidationPass, which
        // is the only place that has both that type and the resolved `on term`.
        resolveValue(target, parents)
        args.foreach(arg => resolveValue(arg.value, parents))
      case _: CodeStatement => () // no references (code body is a string)
    }
  }

  /** A54: resolve the references inside a [[Value]] so validation can find them in the refMap.
    * Constructor refs (message/record Types) and GetValue sources (Input/State) resolve here; a
    * [[ValueRef]] is queued for [[resolveValueRef]], which runs in `postProcess`. Recurses into
    * constructor arguments.
    */
  private def resolveValue(v: Value, parents: Parents): Unit =
    v match
      case _: LiteralString => () // no references
      case lv: LookupValue =>
        resolveValue(lv.collection, parents); lv.indices.foreach(i => resolveValue(i, parents))
      case ev: EmptyValue =>
        // Same lesson the PromptValue arm below records: an ascription carries a real
        // TypeExpression whose PathIdentifier must RESOLVE, or `empty Nonexistent*` validates clean
        // while naming a type that need not exist.
        ev.typeEx.foreach(te => resolveTypeExpression(parents.head, te, parents))
      case pv: PromptValue =>
        // A20: the prompt TEXT is literal (nothing to resolve), but an optional `as <type>`
        // ascription carries a real TypeExpression that may hold a PathIdentifier -- e.g.
        // `prompt("x") as Nonexistent`. Until this arm, NOTHING resolved it: the comment here used
        // to say "no references", true before A20 and false since, and the consequence was that an
        // ascription naming a type that does not exist validated clean instead of reporting an
        // unresolved path -- the exact "validates clean while naming definitions that need not
        // exist" class CLAUDE.md's Total Dispatch section warns about. `resolveTypeExpression` is
        // the SAME total dispatch every other TypeExpression position (a Field's type, a Sequence's
        // element, …) already routes through, so it recurses through the four Cardinality wrappers
        // for free (`prompt("x") as OrderId?`) and registers the resolved Type in `usedBy` via its
        // own `associateUsage` call -- a Type named ONLY by an ascription is therefore NOT
        // wrongly flagged unused by `UsageResolution.checkUnused`.
        pv.typeEx.foreach(te => resolveTypeExpression(parents.head, te, parents))
      case c: Constructor =>
        associateUsage[Type](parents.head, resolveARef[Type](c.ref, parents))
        c.args.foreach(arg => resolveValue(arg.value, parents))
      case call: Call =>
        // A24: resolve the called function and recurse into argument values.
        associateUsage[Function](parents.head, resolveARef[Function](call.function, parents))
        call.args.foreach(arg => resolveValue(arg.value, parents))
      case ask: Ask =>
        // Both halves of the correlation: the QUERY being asked and the PROCESSOR being asked of.
        // The answer's type is NOT resolved here -- it is the query's declared `replies result X`,
        // read in ValidationPass.valueType where every other value's type is decided.
        associateUsage[Type](parents.head, resolveARef[Type](ask.query, parents))
        associateUsage[Processor[?]](
          parents.head,
          resolveARef[Processor[?]](ask.processor, parents)
        )
      case init: Initiate =>
        // A70/instance-identity: resolve the target processor and recurse into argument values,
        // exactly as a Constructor/Call does. `init`'s type (the newly minted `Id(P)`) is
        // synthesized entirely in ValidationPass.valueTypeExpr, mirroring `self`.
        associateUsage[Processor[?]](
          parents.head,
          resolveARef[Processor[?]](init.processor, parents)
        )
        init.args.foreach(arg => resolveValue(arg.value, parents))
      case vr: ValueRef => deferValueRef(vr, parents)
      case gv: GetValue =>
        gv.source match
          case ir: InputRef => associateUsage[Input](parents.head, resolveARef[Input](ir, parents))
          case sr: StateRef => associateUsage[State](parents.head, resolveARef[State](sr, parents))
      // `self`/`self.<field>` names no PathIdentifier -- it is a keyword, not a reference -- so
      // there is nothing here for the resolver to resolve. Its type is synthesized entirely in
      // ValidationPass (see SelfValue.aggregation), which is the whole point of the design: no
      // resolution rule needs to know `self` exists.
      case _: SelfValue           => ()
      case ic: InvariantCondition =>
        // `when invariant X [with <expr>]` — resolve the named invariant, and the handed value if
        // there is one. Without this the reference would sit unresolved and validation could only
        // report it as an unknown VALUE name, which is the mis-parse this construct replaced.
        associateUsage[Invariant](parents.head, resolveARef[Invariant](ic.ref, parents))
        ic.argument.foreach(a => resolveValue(a, parents))
      // A28: recurse into boolean-expression operands so any nested ValueRef/GetValue/Constructor
      // atoms resolve (a BooleanLiteral has no references). A NumericLiteral likewise holds no
      // references -- it is raw text, not resolvable.
      case _: BooleanLiteral        => ()
      case _: NumericLiteral        => ()
      case ce: ComparisonExpression =>
        // A28, widened 2026-08-14: operands are Comparands (refs, or a bare NumericLiteral);
        // resolve each.
        resolveComparand(ce.left, parents); resolveComparand(ce.right, parents)
      case le: LogicalExpression => resolveValue(le.left, parents); resolveValue(le.right, parents)
      case ne: NotExpression     => resolveValue(ne.expr, parents)

  /** A28: resolve a comparison operand ([[Comparand]] = ValueRef | GetValue | ConstantRef |
    * NumericLiteral). A `ConstantRef` and a `GetValue` source resolve here (into the refMap); a
    * bare [[ValueRef]] is queued for [[resolveValueRef]], like `resolveValue`. A [[NumericLiteral]]
    * holds no references -- it is raw text, not resolvable -- mirroring the `ComparisonExpression`
    * case above.
    */
  private def resolveComparand(c: Comparand, parents: Parents): Unit =
    c match
      case cr: ConstantRef   => associateUsage(parents.head, resolveARef[Constant](cr, parents))
      case gv: GetValue      => resolveValue(gv, parents)
      case vr: ValueRef      => deferValueRef(vr, parents)
      case lv: LookupValue   => resolveValue(lv, parents)
      case _: NumericLiteral => ()

  /** A29: resolve the reference-bearing parts of a [[MatchStatement]] — the subject (a GetValue
    * source; a bare ValueRef is queued for [[resolveValueRef]]), each pattern (a [[TypePattern]]'s
    * TypeRef, a [[ComparisonPattern]]'s comparand), and each optional guard's operand refs. The
    * case/default statement bodies are handled separately via [[resolveForeachFieldRefs]].
    */
  private def resolveMatchParts(ms: MatchStatement, parents: Parents): Unit =
    ms.expression match
      case gv: GetValue     => resolveValue(gv, parents)
      case vr: ValueRef     => deferValueRef(vr, parents)
      case _: LiteralString => () // legacy pseudo-code, no references
    ms.cases.foreach { mc =>
      mc.pattern match
        // A TypePattern is resolved LENIENTLY at validation time by name against the subject's
        // closed member set — a type-case may name an Enumerator (not a Type), so calling
        // resolveARef[Type] here would spuriously error. Skip it (like a deferred bare ValueRef).
        case _: TypePattern        => ()
        case cp: ComparisonPattern => resolveComparand(cp.comparand, parents)
        case _: LiteralPattern     => () // legacy pseudo-code, no references
      mc.guard.foreach(g => resolveValue(g, parents))
    }

  ////////////////////////////////////////////////////////////////////////////////// A55 VALUE REFS

  /** A55: [[ValueRef]]s awaiting resolution in `postProcess`, each with the `parents` in force
    * where it was written (its head is the enclosing on-clause or function, matching the refMap
    * keys ValidationPass looks up).
    */
  private val deferredValueRefs: mutable.ArrayBuffer[(ValueRef, Parents)] =
    mutable.ArrayBuffer.empty

  private def deferValueRef(vr: ValueRef, parents: Parents): Unit =
    if parents.nonEmpty then deferredValueRefs.append(vr -> parents)

  /** A55: message suppression while a [[ValueRef]] is resolved. A ValueRef is resolved by the same
    * walker as every other reference, but it may legitimately fail here: its head can name a
    * `let`-local, which is lexical and statement-ORDERED (visible only after its declaration,
    * shadowed by inner blocks) and therefore not a Definition the symbol table models at all. Only
    * ValidationPass threads that lexical scope, so it owns the diagnostic and the walk runs
    * quietly.
    */
  private var quiet: Boolean = false

  private def quietly[T](body: => T): T =
    quiet = true
    try body
    finally quiet = false

  /** A55: resolve a [[ValueRef]]'s path into the refMap using the SAME engine every other reference
    * uses — [[resolvePathFromAnchor]] and its [[findMatchingCandidate]] walk. Only the ANCHOR is
    * chosen differently, because a ValueRef's leading name is not a global symbol: it names
    * something in the VALUE scope. In order:
    *
    *   1. the on-clause's message BINDING (`on foo: command Foo`) — bare `foo` denotes the whole
    *      message Type, and `foo.field` anchors at the clause, whose arm in
    *      [[findMatchingCandidate]] already pushes the message's members;
    *   1. a field of the handled message, of the enclosing entity's state record(s), or of the
    *      enclosing function's `requires` input — the anchor is that [[Field]], from which
    *      `findMatchingCandidate`'s `case field: Field` arm walks any further components;
    *   1. otherwise the ORDINARY reference route ([[resolveAPathId]]), which covers a qualified
    *      path anchored at a real definition (`GState.active`) and a bare `constant` name.
    *
    * A `let`-local matches none of these and fails quietly — by design; see [[quiet]]. No new
    * traversal machinery is written here: every hop already existed for ordinary references.
    */
  /** A57: the path of the envelope type an `on other` binding denotes — the clause's own ascription
    * when it wrote one, else the type named by the nearest `option message_envelope` in scope.
    *
    * Returns None when neither exists, which is not silent: `ValidationPass.checkOnOtherBinding`
    * reports a binding with no envelope in scope as an Error, so the resolver simply has nothing to
    * do here.
    */
  private def envelopePathFor(ooc: OnOtherClause, parents: Parents): Option[PathIdentifier] =
    ooc.envelopeType.map(_.pathId).orElse {
      parents.iterator
        .collectFirst {
          case wo: WithMetaData if wo.getOptionValue("message_envelope").nonEmpty =>
            wo.getOptionValue("message_envelope").get
        }
        .flatMap(_.args.headOption)
        .map(ls => PathIdentifier(ls.loc, ls.s.split('.').toSeq))
    }

  private def resolveValueRef(vr: ValueRef, parents: Parents): Unit =
    val names = vr.path.value
    if names.nonEmpty && parents.nonEmpty then
      quietly {
        val head = names.head
        val parent = parents.head
        parents.collectFirst {
          case omc: OnMessageLikeClause if omc.binding.exists(_.value == head) => omc
        } match
          case Some(omc) =>
            if names.sizeIs == 1 then
              // The bare binding denotes the WHOLE message, so it resolves to the message's Type.
              refMap.definitionOf[Type](omc.msg.pathId, omc).foreach { t =>
                refMap.add[Type](vr.path, parent, t)
                associateUsage(parent, t)
              }
            else
              resolvePathFromAnchor[Definition](
                vr.path,
                parents,
                omc,
                parents.dropWhile(_ ne omc).drop(1)
              )
            end if
          case None =>
            // A57: an `on other as x [: <envelope>]` binding. `x` denotes the message's ENVELOPE,
            // whose type is the clause's ascription when written, else the one `option
            // message_envelope` names in scope. Resolving to that Type makes both `x` and
            // `x.source` work through the machinery already here.
            parents.collectFirst {
              case ooc: OnOtherClause if ooc.binding.exists(_.value == head) => ooc
            } match
              case Some(ooc) =>
                envelopePathFor(ooc, parents).flatMap(refMap.definitionOf[Type](_, ooc)).foreach {
                  t =>
                    if names.sizeIs == 1 then
                      refMap.add[Type](vr.path, parent, t)
                      associateUsage(parent, t)
                    else
                      resolvePathFromAnchor[Definition](vr.path, parents, t, symbols.parentsOf(t))
                    end if
                }
              // Task 3 / final review: an `on init(...)`/`on term(...)` PARAMETER. Like a `let`,
              // and unlike every other source here, it is LEXICAL: a `MethodArgument` is not a
              // `Definition`, so there is nothing for the symbol table or the refMap to hold, and
              // `ValidationPass.clauseParameterScope` owns both its scope and its diagnostic.
              //
              // The arm is not empty, though — falling through to `resolveAPathId` would let a
              // parameter name that HAPPENS to match some unrelated definition bind to it and
              // record a false usage edge. Claiming the name here is what makes the parameter
              // shadow, which is the whole point of a lexical scope.
              case None if enclosingClauseParameter(head, parents) => ()
              case None =>
                valueScopeField(head, parents) match
                  case Some(field) if names.sizeIs == 1 =>
                    refMap.add[Field](vr.path, parent, field)
                    associateUsage(parent, field)
                  case Some(field) =>
                    resolvePathFromAnchor[Definition](
                      vr.path,
                      parents,
                      field,
                      symbols.parentsOf(field)
                    )
                  case None => resolveAPathId[Definition](vr.path, parents)
        end match
      }
    end if
  end resolveValueRef

  /** A55: the value-scope [[Field]] a [[ValueRef]]'s leading name may denote — a field of the
    * enclosing entity's state record(s), of the handled on-clause message, or of the enclosing
    * function's `requires` input, in that order. Mirrors ValidationPass's `foreachAllowedFields` so
    * both passes see the same fields (membership there is tested by identity).
    */
  /** Task 3: does an enclosing `on init(...)`/`on term(...)` clause declare a parameter named
    * `name`? See the call site in [[resolveValueRef]] for why the answer is used to STOP resolving
    * rather than to resolve.
    */
  private def enclosingClauseParameter(name: String, parents: Parents): Boolean =
    parents.exists {
      case oic: OnInitializationClause => oic.parameters.exists(_.name == name)
      case otc: OnTerminationClause    => otc.parameters.exists(_.name == name)
      case _                           => false
    }

  private def valueScopeField(name: String, parents: Parents): Option[Field] =
    def aggFields(t: Type): Seq[Field] =
      t.typEx match
        case ate: AggregateTypeExpression => ate.fields
        case _                            => Seq.empty[Field]
    val stateFields: Seq[Field] =
      parents.collectFirst { case e: Entity => e }.toSeq.flatMap { e =>
        e.states.flatMap(st => refMap.definitionOf[Type](st.typ.pathId).toSeq.flatMap(aggFields))
      }
    val messageFields: Seq[Field] =
      parents
        .collectFirst { case omc: OnMessageLikeClause if omc.msg.nonEmpty => omc }
        .toSeq
        .flatMap(omc => refMap.definitionOf[Type](omc.msg.pathId).toSeq.flatMap(aggFields))
    val functionFields: Seq[Field] =
      parents.collectFirst { case f: Function => f }.toSeq.flatMap { f =>
        f.input.toSeq.flatMap {
          case tr: TypeRef      => refMap.definitionOf[Type](tr.pathId).toSeq.flatMap(aggFields)
          case agg: Aggregation => agg.fields
        }
      }
    (stateFields ++ messageFields ++ functionFields).find(_.id.value == name)
  end valueScopeField

  /** A54: resolve a message/record operand — a bare ref (resolved as a Type) or a [[Constructor]]
    * (resolved via [[resolveValue]]). Shared by send/tell/yield (message) and morph (record).
    */
  private def resolveMessageOperand(
    m: MessageRef | RecordRef | Constructor | ValueRef,
    parents: Parents
  ): Unit =
    m match
      case ref: (MessageRef | RecordRef) =>
        associateUsage[Type](parents.head, resolveARef[Type](ref, parents))
      case c: Constructor => resolveValue(c, parents)
      // A56: a `tell`/`send` operand naming an on-clause binding. Deferred like every other
      // ValueRef rather than resolved here: its anchor is the enclosing on-clause, and
      // `resolveValueRef` already knows how to reach the handled message's Type from there.
      case vr: ValueRef => deferValueRef(vr, parents)

  /** Resolve the collection FieldRefs of every [[ForeachStatement]] nested anywhere within `stmts`.
    * `parents` is held constant (its head is the enclosing on-clause/function) so the refMap keys
    * match those validation uses — when/match/foreach nesting introduces no named scope of its own.
    */
  private def resolveForeachFieldRefs(stmts: Contents[Statements], parents: Parents): Unit =
    stmts.foreach {
      case fs: ForeachStatement =>
        fs.collection match
          case fr: FieldRef  => associateUsage[Field](parents.head, resolveARef[Field](fr, parents))
          case _: Identifier => ()
        resolveForeachFieldRefs(fs.doStatements, parents)
      case ws: WhenStatement =>
        resolveForeachFieldRefs(ws.thenStatements, parents)
        resolveForeachFieldRefs(ws.elseStatements, parents)
      case ms: MatchStatement =>
        resolveMatchParts(ms, parents) // A29: resolve nested match subject/pattern/guard refs
        ms.cases.foreach(mc => resolveForeachFieldRefs(mc.statements, parents))
        resolveForeachFieldRefs(ms.default, parents)
      // A45/A57: put/return may nest under a foreach/when/match — resolve their value refs too.
      case PutStatement(_, v, output) =>
        resolveValue(v, parents)
        associateUsage[Output](parents.head, resolveARef[Output](output, parents))
      case ReturnStatement(_, v) =>
        resolveValue(v, parents)
      // A54: widened operands may nest too — resolve the constructor/value they carry.
      case SetStatement(_, _, v) => resolveValue(v, parents)
      case ls: LetStatement      => resolveValue(ls.expression, parents)
      // A nested `send`/`tell` gets the SAME treatment as a top-level one. Only the constructor
      // operand used to be resolved here, so the portlet/processor and the message were never
      // entered in the refMap at all — and MessageFlowPass, which finds nested statements
      // recursively, then reported perfectly good references as unresolvable. Eight such warnings
      // in riddl-models were the only ones left in the corpus.
      // Resolved QUIETLY, as A55 resolves a ValueRef. The goal is to POPULATE the refMap so
      // downstream passes can look these up — MessageFlowPass finds nested statements recursively
      // and was reporting perfectly good references as unresolvable. It is NOT to start policing
      // references that have never been checked: `language/input/everything_full.riddl` has
      // `send event Inebriated to outlet APlant.Source.Commands`, which does not resolve, and
      // promoting that to an error here would fail models that validate today. Whether such paths
      // are genuinely wrong is worth settling, but on its own terms and after checking the corpus.
      case s: SendStatement =>
        quietly {
          resolveMessageOperand(s.msg, parents)
          associateUsage[Portlet](parents.head, resolveARef[Portlet](s.portlet, parents))
        }
      case s: TellStatement =>
        quietly {
          resolveMessageOperand(s.msg, parents)
          s.target match
            case processor: ProcessorRef[?] =>
              associateUsage(parents.head, resolveARef[Processor[?]](processor, parents))
            case value: Value => resolveValue(value, parents)
        }
      case s: YieldStatement =>
        s.msg match { case c: Constructor => resolveValue(c, parents); case _ => () }
      case s: ReplyStatement =>
        s.msg match { case c: Constructor => resolveValue(c, parents); case _ => () }
      case s: MorphStatement =>
        s.value match { case c: Constructor => resolveValue(c, parents); case _ => () }
      // A70/instance-identity: a nested `terminate` gets the SAME treatment as a top-level one --
      // the generic traversal does not descend into a when/match/foreach body (those are FIELDS,
      // not `contents`), so without this case the target and args would never be entered in the
      // refMap for a nested occurrence, and validation would report "not resolved" instead of (or
      // on top of) the intended type/arity diagnostic. Mirrors the regression this exact gap
      // produced for `initiate` (see `LetStatement`'s case above, which already covered
      // `initiate` because it is a VALUE wrapped in a `let`).
      case s: TerminateStatement =>
        resolveValue(s.target, parents)
        s.args.foreach(arg => resolveValue(arg.value, parents))
      case _ => ()
    }

  private def resolveInteractions(
    useCase: UseCase,
    interactions: Seq[Interaction],
    parentsAsSeq: Parents
  ): Unit = {
    for interaction <- interactions do {
      interaction match {
        case ArbitraryInteraction(_, from, _, to, _) =>
          associateUsage[Definition](useCase, resolveARef[Definition](from, parentsAsSeq))
          associateUsage[Definition](useCase, resolveARef[Definition](to, parentsAsSeq))
        case fi: FocusOnGroupInteraction =>
          associateUsage[Definition](useCase, resolveARef[User](fi.from, parentsAsSeq))
          associateUsage[Definition](useCase, resolveARef[Group](fi.to, parentsAsSeq))
        case fou: DirectUserToURLInteraction =>
          associateUsage[Definition](useCase, resolveARef[User](fou.from, parentsAsSeq))
        case ti: ShowOutputInteraction =>
          associateUsage[Definition](useCase, resolveARef[User](ti.to, parentsAsSeq))
          associateUsage[Definition](useCase, resolveARef[Output](ti.from, parentsAsSeq))
        case si: SelectInputInteraction =>
          associateUsage[Definition](useCase, resolveARef[User](si.from, parentsAsSeq))
          associateUsage[Definition](useCase, resolveARef[Input](si.to, parentsAsSeq))
        case pi: TakeInputInteraction =>
          associateUsage[Definition](useCase, resolveARef[User](pi.from, parentsAsSeq))
          associateUsage[Definition](useCase, resolveARef[Input](pi.to, parentsAsSeq))
        case ri: RefusalInteraction =>
          associateUsage[Definition](useCase, resolveARef[Definition](ri.from, parentsAsSeq))
          associateUsage[Definition](useCase, resolveARef[User](ri.to, parentsAsSeq))
          // A38: an invariant-named reason is a REFERENCE and must resolve like every other one —
          // that is the entire point of admitting it. Left unresolved it would name an invariant
          // that need not exist while the model validates clean, and the Invariant it does name
          // would be reported unused. Prose has nothing to resolve.
          ri.reason match
            case ir: InvariantRef =>
              associateUsage(useCase, resolveARef[Invariant](ir, parentsAsSeq))
            case _: LiteralString => ()
        case si: SelfInteraction =>
          associateUsage[Definition](useCase, resolveARef[Definition](si.from, parentsAsSeq))
        case SendMessageInteraction(_, from, message, to, _) =>
          associateUsage[Definition](useCase, resolveARef[Definition](from, parentsAsSeq))
          associateUsage[Definition](useCase, resolveAMessageRef(message, parentsAsSeq))
          associateUsage[Definition](useCase, resolveARef[Definition](to, parentsAsSeq))
        case _: VagueInteraction      => () // no references
        case ic: InteractionContainer =>
          // These three used to be `() // no references`, and that comment was FALSE in the way
          // that matters: the CONTAINER carries none, but its CONTENTS do, and returning unit
          // dropped every one of them. A step inside `sequence`/`parallel`/`optional` was never
          // resolved at all, so a model could name commands, entities, groups and users that DO
          // NOT EXIST and validate green -- exit 0, zero diagnostics -- while the identical step
          // one level out errored correctly. Reported by ossum.tech 2026-08-08, whose docs-fence
          // gate was reporting hollow passes because of it.
          //
          // `InteractionContainer` is a `Container` but NOT a `Branch` (its base `Interaction` is
          // a RiddlValue, not a Definition), so the generic traversal cannot descend into it
          // either -- the same shape as the SagaStep hole. Recursion here handles nesting
          // (a `sequence` inside a `parallel`) for free.
          //
          // `useCase` stays the anchor rather than the container: interaction refs are keyed in
          // the refMap under the enclosing UseCase (see 55d5dc6d9), and nesting must not change
          // that key or validation's lookups would miss.
          resolveInteractions(useCase, ic.contents.filter[Interaction], parentsAsSeq)
      }
    }
  }

  private def resolveARef[T <: Definition: ClassTag](
    ref: Reference[T],
    parents: Parents
  ): Resolution[T] = {
    resolveAPathId[T](ref.pathId, parents)
  }

  private def isSameKind[DEF <: WithIdentifier: ClassTag](d: WithIdentifier): Boolean = {
    val clazz = classTag[DEF].runtimeClass
    clazz.isAssignableFrom(d.getClass)
  }

  private def isSameKindAndHasDifferentPathsToSameNode[T <: WithIdentifier: ClassTag](
    list: List[SymTabItem]
  ): Boolean = {
    list.forall { item => isSameKind[T](item._1) } &&
    list
      .map { item =>
        item._2.filterNot(_.isAnonymous)
      }
      .forall(_ == list.head)
  }

  private def handleSymbolTableResults[T <: Definition: ClassTag](
    list: List[SymTabItem],
    pathId: PathIdentifier,
    parents: Parents
  ): Resolution[T] =
    parents.headOption match
      case None =>
        // shouldn't happen
        notResolved[T](pathId, parents.headOption, "there are no parents of the found symbol")
        None
      case Some(parent) =>
        list match
          case Nil =>
            // List is empty so this is the NotFound case
            notResolved[T](
              pathId,
              parents.headOption,
              s"the sought name, '${pathId.value.last}', was not found in the symbol table,"
            )
            None
          case (d, pars) :: Nil if isSameKind[T](d) => // exact match
            // List just has one component and the types are the same so this is the Resolved case
            resolved[T](pathId, parent, d)
            Some(d.asInstanceOf[T] -> pars)
          case (d, _) :: Nil =>
            // List has one component but it's the wrong type
            wrongType[T](pathId, parent, d)
            None
          case (d, pars) :: _ if isSameKindAndHasDifferentPathsToSameNode(list) =>
            // List has multiple elements
            resolved[T](pathId, parent, d)
            Some(d.asInstanceOf[T] -> pars)
          case list =>
            ambiguous[T](pathId, list)
            None
        end match
    end match
  end handleSymbolTableResults

  private def searchSymbolTable[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Parents
  ): Resolution[T] = {
    val symTabCompatibleNameSearch = pathId.value.reverse
    val list = symbols.lookupParentage(symTabCompatibleNameSearch)
    handleSymbolTableResults[T](list, pathId, parents)
  }

  private sealed trait AnchorCase
  private case class AnchorNotFoundInSymTab(topName: String) extends AnchorCase
  private case class AnchorNotFoundInParents(topName: String) extends AnchorCase
  private case class AnchorNotFoundAnywhere(topName: String) extends AnchorCase
  private case class AnchorIsAmbiguous(topName: String, list: List[SymTabItem]) extends AnchorCase
  private case class AnchorFoundInSymTab(anchor: Definition, anchor_parents: Parents)
      extends AnchorCase
  private case class AnchorFoundInParents(anchor: Definition, anchor_parents: Parents)
      extends AnchorCase
  private case class AnchorIsRoot(anchor: Definition, anchor_parents: Parents) extends AnchorCase

  private def findAnchorInParents(
    topName: String,
    parents: Parents
  ): AnchorCase = {
    // The anchor is the matching name closest to the PathId location
    parents.find(_.id.value == topName) match {
      case Some(anchor) =>
        // We want to simulate a symtab find here which returns the node of
        // interest and that node's parents. Since there is a node in common
        // we can get it by dropping nodes until we find it.
        val anchor_parents = parents.dropWhile(_ != anchor).drop(1)
        AnchorFoundInParents(anchor, anchor_parents)
      case None =>
        AnchorNotFoundInParents(topName)
    }
  }

  private def findAnchorInSymTab(
    topName: String
  ): AnchorCase = {
    // Let's see if we can find it uniquely in the symbol table
    symbols.lookupParentage(Seq(topName)) match {
      case Nil =>
        AnchorNotFoundInSymTab(topName)
      case (anchor: Definition, anchor_parents: Parents) :: Nil =>
        // it is unique
        // Found the top node uniquely in the symbol table
        // now just run down the children and see if all levels of the
        // pathId can be satisfied
        AnchorFoundInSymTab(anchor, anchor_parents)
      case list =>
        AnchorIsAmbiguous(topName, list)
    }
  }

  private val internalErrorSuggestion =
    "This is an internal RIDDL resolver error; please report it with the model that triggered it."

  private def findAnchor[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Parents
  ): AnchorCase = {
    pathId.value.headOption match
      case Some(topName) if topName == "Root" =>
        // We anchor at the root of the model so anything possible
        AnchorIsRoot(parents.last, parents.dropRight(1))
      case Some(topName) =>
        // First, determine whether the anchor node is one of
        // the names in the parents above the location the PathId is used.
        findAnchorInParents(topName, parents) match
          case afip: AnchorFoundInParents => afip
          case _: AnchorNotFoundInParents =>
            // It's not an ancestor so let's try the symbol table
            findAnchorInSymTab(topName) match
              case afis: AnchorFoundInSymTab     => afis
              case anfis: AnchorNotFoundInSymTab => anfis
              case aia: AnchorIsAmbiguous        => aia
              case anfis: AnchorCase =>
                messages.addSevere(
                  pathId.loc,
                  s"Invalid result from findAnchorInSymTab($topName, $parents): $anfis",
                  suggestion = internalErrorSuggestion
                )
                anfis
          case anfis: AnchorCase =>
            messages.addSevere(
              pathId.loc,
              s"Invalid result from findAnchorInParents($topName, $parents): $anfis",
              suggestion = internalErrorSuggestion
            )
            anfis
      case None =>
        messages.addSevere(
          pathId.loc,
          "PathId is empty; this should already be checked in resolveAPathId",
          suggestion = internalErrorSuggestion
        )
        AnchorNotFoundAnywhere("<unknown>")
  }

  private def resolvePathFromAnchor[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Parents,
    anchor: Definition,
    anchor_parents: Parents
  ): Resolution[T] = {
    val stack = DefinitionStack.empty
    val parents_to_add = anchor_parents.reverse
    if anchor_parents.nonEmpty && anchor_parents.last.isRootContainer then
      stack.pushAll(parents_to_add.drop(1))
    else stack.pushAll(parents_to_add)
    stack.push(anchor)
    val pathIdStart = pathId.value.drop(1) // we already resolved the anchor
    // The anchor is the first component of a multi-element path; it's an
    // intermediate (not the final target), so record it as a path usage —
    // unless the anchor is itself an ancestor of the authoring site
    // (e.g., `state AState of record fooBar.fields` inside `entity fooBar`),
    // in which case the reference is internal to the definition itself
    // and should not count as external usage.
    if pathIdStart.nonEmpty && parents.nonEmpty &&
      !parents.exists(_ eq anchor)
    then associatePathUsage(parents.head, anchor)
    var continue: Boolean = true
    var resolution: Resolution[T] = None
    var elementCounter: Int = pathIdStart.length
    for { soughtName: String <- pathIdStart if continue } do
      // Because names in a PathId are not unique, we can't use comparison against
      // the last name to determine if we're at the end of the names. Instead, we
      // count down the number of elements remaining
      elementCounter -= 1
      val isLastPathElement = elementCounter <= 0
      // Find matching item at head of stack and return the candidates derived
      // from it for the next loop. If nothing is returned, the head of stack
      // didn't match the sought name.
      findMatchingCandidate(soughtName, stack, pathId) match
        case None =>
          // None of the candidates match the name we're seeking, so this PathId doesn't match the
          // model. When the head is a field whose cardinality is what stopped the walk, say THAT:
          // the name is in the type, and "not found" sends the author hunting a typo that is not
          // there.
          cardinalityRefusal(stack.headOption, soughtName) match
            case Some((why, fix)) =>
              notResolved[T](pathId, stack.headOption, why, Some(fix))
            case None =>
              notResolved[T](
                pathId,
                stack.headOption,
                s"the name '$soughtName' was not found in ${stack.head.identify}"
              )
          continue = false
        case Some(definition) =>
          if isLastPathElement then
            // The soughtName is the last one in the pathId, no point continuing the loop
            continue = false
            checkPrivateNestedFunction(pathId, definition, stack.headOption, parents)
            // Since we are on the last element, let's try to find the match
            resolution = checkMatch[T](pathId, definition, parents)
          else
            // We have matched the current element and found some candidates for
            // the next round, so we must push and continue. This is an
            // intermediate path component — record as path usage.
            if parents.nonEmpty then associatePathUsage(parents.head, definition)
            stack.push(definition)
          end if
      end match
    end for
    if !continue then
      // return the resolution
      resolution
    else
      stack.headOption match
        case Some(_: Root) if stack.size == 1 =>
          // then pop it off because RootContainers don't count, and we want to
          // rightfully return an empty sequence for "not found"
          stack.pop()
          // Convert parent stack to immutable sequence
          Some(stack.head.asInstanceOf[T] -> stack.tail.toSeq.asInstanceOf[Seq[Branch[?]]])
        case Some(definition: T) =>
          // Not the root, just convert the result to immutable Seq
          Some(definition -> stack.tail.toSeq.asInstanceOf[Seq[Branch[?]]])
        case Some(_) =>
          None
        case None =>
          None
      end match
    end if
  }

  private def resolved[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    pidDirectParent: Branch[?],
    definition: Definition
  ): T =
    // A candidate was found, and it has the same type as expected
    val t = definition.asInstanceOf[T]
    refMap.add[T](pathId, pidDirectParent, t)
    associateUsage(pidDirectParent, t)
    if io.options.debug then
      messages.add(
        Messages.info(
          s"Path Identifier ${pathId.format} in ${pidDirectParent.identify} resolved to ${definition.identify}",
          pathId.loc
        )
      )
    end if
    if io.options.debug then println(s"Resolved: ${pathId.format} ==> ${t.identify}")
    t
  end resolved

  private def wrongType[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    container: Definition,
    foundDef: WithIdentifier
  ): Unit =
    val referTo = classTag[T].runtimeClass.getSimpleName
    val message = s"Path '${pathId.value.mkString(".")}' resolved to ${foundDef.identifyWithLoc}," +
      s" in ${container.identify}, but ${article(referTo)} was expected"
    if !quiet then
      messages.addError(
        pathId.loc,
        message,
        suggestion =
          s"'${pathId.value.mkString(".")}' points at the wrong kind of definition. Point it at ${article(referTo)} " +
            s"instead, or rename the reference to match the intended $referTo."
      )
    if io.options.debug then
      println(
        s"WrongType: ${pathId.format} ==> ${foundDef.identifyWithLoc} not ${article(referTo)}"
      )
    end if

  end wrongType

  /** A function nested inside another function is that function's PRIVATE IMPLEMENTATION.
    *
    * Reaching one by path from outside its enclosing function resolves -- it is a real definition
    * and the model still works -- but it couples a caller to the internals of something that chose
    * to hide them, which is what makes it a style problem rather than an error (Reid, 2026-08-08).
    *
    * Deliberately scoped to calls from OUTSIDE. A function calling its own nested helper is the
    * entire point of nesting; warning on that would make the feature unusable. "Outside" is decided
    * by whether the enclosing function appears in the REFERENCE SITE's parents, not by name, so a
    * same-named function elsewhere cannot suppress it.
    *
    * Runs under the same `quiet` guard as `notResolved`: the A55 ValueRef walk resolves
    * speculatively, and a style nag from a probe that was never a real reference is noise.
    */
  private def checkPrivateNestedFunction(
    pathId: PathIdentifier,
    resolved: Definition,
    enclosing: Option[Definition],
    parents: Parents
  ): Unit =
    if quiet then return
    (resolved, enclosing) match
      case (nested: Function, Some(owner: Function)) if !parents.exists(_ eq owner) =>
        messages.addStyle(
          pathId.loc,
          s"${nested.identify} is nested inside ${owner.identify} and is private to it; " +
            s"calling it from outside couples this caller to that function's implementation",
          suggestion =
            s"Move '${nested.id.value}' out to the enclosing context if it is meant to be called " +
              s"from elsewhere, or call '${owner.id.value}' instead and let it use its own helper."
        )
      case _ => ()
    end match
  end checkPrivateNestedFunction

  private def notResolved[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    container: Option[Definition],
    why: String = "",
    // When the cause is known precisely (see `cardinalityRefusal`), the generic "define one, or
    // correct the path" advice is wrong -- the path names a real field and defining another would
    // not help. Overriding it keeps the suggestion actionable.
    suggestionOverride: Option[String] = None
  ): Unit =
    val tc = classTag[T].runtimeClass
    val message = container match
      case None =>
        s"Path '${pathId.value.mkString(".")}' is not resolvable, because it has no container"
      case Some(definition) =>
        s"Path '${pathId.value.mkString(".")}' was not resolved, in ${definition.identify}${
            if why.isEmpty then "\n"
            else "\nbecause " + why + "\n"
          }"

    val referTo = tc.getSimpleName
    if !quiet then
      messages.addError(
        pathId.loc,
        message + {
          if referTo.nonEmpty then s"and it should refer to ${article(referTo)}"
          else ""
        },
        suggestion = suggestionOverride.getOrElse(
          s"Define ${article(referTo)} named by '${pathId.value.mkString(".")}', or correct the path so it names " +
            s"an existing $referTo reachable from this scope (try a fully-qualified path like 'Domain.Context.Name')."
        )
      )
    if io.options.debug then println(s"Unresolved: ${pathId.format} ==> ???")
  end notResolved

  private def checkMatch[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    definition: Definition,
    parents: Parents
  ): Resolution[T] =
    parents.headOption match
      case Some(parent) =>
        if isSameKind[T](definition) then
          // we found a matching definition in both name and type
          val t: T = resolved[T](pathId, parent, definition)
          // return the resolution
          Some(t -> symbols.parentsOf(t))
        else
          // the name matches, the type does not, emit error
          wrongType[T](pathId, parent, definition)
          None
        end if
      case None =>
        // No parent of the node, shouldn't happen!
        notResolved[T](pathId, None, s"because ${definition.identify} does have parents!")
        None
    end match
  end checkMatch

  private def findMatchingCandidate(
    soughtName: String,
    defStack: DefinitionStack,
    pathId: PathIdentifier
  ): Option[Definition] =
    require(defStack.nonEmpty, "No stack to consider in findCandidates")
    defStack.headOption match
      case None =>
        Option.empty[Definition] // nothing to search to provide candidates
      case Some(head) =>
        val candidates: Definitions =
          head match
            case st: State =>
              // A State's OWN contents (handlers, invariants) AND the fields of the record it
              // is `of`. The comment here previously promised exactly this and the code
              // returned only the record's members, so a qualified path to a handler declared
              // inside a state -- `Order.Active.Strict` -- descended into the record and failed
              // with "not found in Record 'OpenState'", pointing the author at the wrong
              // definition entirely. The relative form (`handler Strict`) always worked, which
              // is why it went unnoticed. Reported by riddl-generator against 2.0.0-rc.6.
              //
              // State's own definitions come FIRST, so a handler shadows a same-named record
              // field rather than the other way round: the nearer declaration wins.
              st.contents.directDefinitions ++
                candidatesFromPathIdentifier[Type](st.typ.pathId, defStack)
            case omc: OnMessageLikeClause if omc.msg.nonEmpty =>
              // We found an on-clause (message or event); its message's members are the
              // candidates, so push that message's path on the name stack. A55: the guard was
              // `omc.msg.id.nonEmpty`, but `Reference.id` is the OPTIONAL LOCAL NAME of a
              // reference (what `from di: context C` sets) and no MessageRef ever carries one —
              // so this arm was unreachable. `nonEmpty` on the reference itself (a non-empty
              // pathId) is the intended test, and it is what makes an `on foo: command Foo`
              // binding resolve `foo.someField` as an ordinary path walk.
              candidatesFromPathIdentifier[Type](omc.msg.pathId, defStack)
            case field: Field =>
              candidatesFromTypeExpression(field.typeEx, defStack)
            case constant: Constant =>
              candidatesFromTypeExpression(constant.typeEx, defStack)
            case typ: Type =>
              candidatesFromTypeExpression(typ.typEx, defStack)
            case inlet: Inlet =>
              candidatesFromPathIdentifier[Type](inlet.type_.pathId, defStack)
            case outlet: Outlet =>
              candidatesFromPathIdentifier[Type](outlet.type_.pathId, defStack)
            case include: Include[?] =>
              candidatesFromContents(include.contents.directDefinitions.toContents)
                .asInstanceOf[Definitions]
            case function: Function =>
              // A9: only the deprecated inline Aggregation form contributes inline Field candidates;
              // a TypeRef's fields live in the referenced type, resolved separately.
              //
              // `getOrElse(Seq.empty)`, NOT `asInstanceOf[Definitions]`. `Function.input`/`output`
              // are `Option[TypeRef | Aggregation]`, so `.collect` yields an OPTION, and casting an
              // Option to `Definitions` (= `Seq[Definition]`) threw ClassCastException for ANY path
              // descending into a Function -- reliably, before any name comparison, which is why a
              // nonexistent target failed identically to a real one. It surfaced as a bare
              // `[severe] empty(1:1->1):` with no text at all (see the NonFatal handler in
              // Pass.scala). Reported by ossum.tech 2026-08-08. No cast is needed in the first
              // place: `filter[Field]` already returns `Seq[Field]`, and Seq is covariant.
              function.input
                .collect { case agg: Aggregation => agg.contents.filter[Field] }
                .getOrElse(Seq.empty) ++
                function.output
                  .collect { case agg: Aggregation => agg.contents.filter[Field] }
                  .getOrElse(Seq.empty) ++
                function.contents.directDefinitions
            // [2.6], RULED 2026-08-16 by Reid ("Yes, it should"): a `.bast`-imported definition
            // must RESOLVE without an explicit flatten. It already READ — the content accessors
            // have been import- and include-transparent since 2026-08-06 — so a model could SEE an
            // imported type and not NAME it, and the failure was the generic "Path 'App.Money' was
            // not resolved", which says nothing about imports at all.
            //
            // These two arms were the whole seam. A `BASTImport` is a Container but NOT a
            // Definition, so it fell through the flatMap's `case _ => Seq.empty` and was excluded
            // by `directDefinitions` — twice, silently, in exactly the shape BACKLOG [2.3] names
            // as its next slice: an empty answer in a RESOLUTION position, read downstream as "no
            // such thing".
            //
            // `definitions` is the transparent accessor and descends BOTH wrappers, which is now
            // what is wanted; `directDefinitions` is the literal one. The asymmetry the backlog
            // recorded as the obstacle — "includes but not imports", which `filterThroughWrappers`
            // cannot express — dissolves with the ruling, because the two wrappers should now be
            // treated alike.
            case vital: VitalDefinition[?] =>
              localsThenImported(vital.contents.toSeq)
            case p: Branch[?] =>
              localsThenImported(p.contents.toSeq)
            case _ =>
              // No match so no candidates
              Seq.empty[Definition]
          end match
        // [4.6], RULED 2026-08-17 by Reid (option C): a LOCAL declaration always wins over an
        // imported one -- regardless of which was written first -- AND the ambiguity is warned,
        // naming EVERY side of it. `candidates` is ordered locals-first by `localsThenImported`,
        // so `.headOption` IS the precedence rule; there is no separate sorting step to keep in
        // step with it.
        //
        // Position was the alternative and was rejected: it would make the same source mean
        // different things before and after an upgrade that reordered an import.
        val matches = candidates.filter(_.id.value == soughtName)
        if matches.sizeIs > 1 then reportShadowedImport(soughtName, defStack.head, pathId, matches)
        matches.headOption
    end match
  end findMatchingCandidate

  /** Definitions reachable from `contents`, **LOCAL ONES FIRST**, then imported ones.
    *
    * [4.6]. An `Include` is semantically transparent -- it is file organization, and its contents
    * ARE the container's -- so an included definition counts as LOCAL. A `BASTImport` brings in
    * definitions from another module, and those yield to a local declaration of the same name.
    *
    * This is the "includes but not imports" distinction the backlog recorded as inexpressible via
    * `filterThroughWrappers`, which is true: it cannot express it, so this walks the wrappers
    * itself and keeps the two apart by construction rather than by a later filter.
    */
  private def localsThenImported(contents: Seq[RiddlValue]): Definitions =
    val locals = mutable.ListBuffer.empty[Definition]
    val imported = mutable.ListBuffer.empty[Definition]
    def walk(values: Seq[RiddlValue], viaImport: Boolean): Unit =
      values.foreach {
        case inc: Include[?] => walk(inc.contents.toSeq, viaImport)
        case bi: BASTImport  => walk(bi.contents.toSeq, viaImport = true)
        case definition: Definition =>
          if viaImport then imported += definition else locals += definition
        case _ => ()
      }
    walk(contents, viaImport = false)
    (locals ++ imported).toSeq
  end localsThenImported

  /** [4.6]: warn that a name resolves to more than one definition because an import collides with
    * something else, naming EVERY side.
    *
    * Reid's ruling is explicit that the warning must name all sides and that **there may be more
    * than two** -- several imports can each carry the name -- so this lists the whole set with
    * locations, in the precedence order actually used, mirroring `ambiguous`'s existing format.
    *
    * Silent when every match is LOCAL: two local definitions sharing a name in one scope is
    * `checkUniqueContent`'s report to make (per-scope duplicate-content-name, in validation), and
    * saying it twice in two different vocabularies helps nobody. This warning exists for the case
    * that check cannot see, where the collision spans a module boundary.
    */
  private def reportShadowedImport(
    soughtName: String,
    container: Definition,
    pathId: PathIdentifier,
    matches: Seq[Definition]
  ): Unit =
    val importedHere: Seq[Definition] =
      container match
        case b: Branch[?] =>
          val locals = mutable.ListBuffer.empty[Definition]
          val imported = mutable.ListBuffer.empty[Definition]
          def walk(values: Seq[RiddlValue], viaImport: Boolean): Unit =
            values.foreach {
              case inc: Include[?] => walk(inc.contents.toSeq, viaImport)
              case bi: BASTImport  => walk(bi.contents.toSeq, viaImport = true)
              case d: Definition   => if viaImport then imported += d else locals += d
              case _               => ()
            }
          walk(b.contents.toSeq, viaImport = false)
          imported.toSeq.filter(_.id.value == soughtName)
        case _ => Seq.empty
    if importedHere.nonEmpty then
      val winner = matches.head
      val sides = matches
        .map(d => s"  ${d.identify} (${d.loc})" + (if d eq winner then "  <-- wins" else ""))
        .mkString("\n")
      messages.addWarning(
        pathId.loc,
        s"The name '$soughtName' in '${pathId.format}' is ambiguous in ${container.identify}: " +
          s"${matches.size} definitions carry it, at least one of them imported. " +
          s"The local declaration wins.\n" + sides
      )
  end reportShadowedImport

  private def resolveAMessageRef(ref: MessageRef, parents: Parents): Resolution[Type] =
    val loc: At = ref.loc
    val pathId: PathIdentifier = ref.pathId
    val kind: AggregateUseCase = ref.messageKind
    val result: Resolution[Type] = resolveAPathId[Type](pathId, parents)
    result match
      case Some((typ: Type, _)) =>
        typ.typEx match
          case AggregateUseCaseTypeExpression(_, usecase, _, _) if usecase == kind =>
            result // success
          case typeEx: Alternation if typeEx.of.forall(_.isAggregateOf(kind)) => result // success
          case typeEx: Alternation =>
            messages.addError(
              loc,
              s"All alternates of `${typeEx.format}` must be ${kind.useCase.dropRight(4)} aggregates",
              suggestion =
                s"Declare every alternative as ${article(kind.useCase.dropRight(4))} aggregate, " +
                  s"e.g. 'type X = ${kind.useCase.dropRight(4)} { ??? }'."
            )
            None
          case typeEx: TypeExpression =>
            messages.addError(
              loc,
              s"Type expression `${typeEx.format}` needs to be an aggregate for `${kind.useCase.dropRight(4)}`",
              suggestion =
                s"Declare the referenced type as ${article(kind.useCase.dropRight(4))} aggregate, " +
                  s"e.g. 'type X = ${kind.useCase.dropRight(4)} { ??? }'."
            )
            None
        end match
      case _ =>
        None // error message should have already been issued
    end match
  end resolveAMessageRef

  private def handleTypeResolution(
    typ: Type,
    useCase: AggregateUseCase,
    resolution: Resolution[Type]
  ): Resolution[Type] =
    typ.typEx match
      case typEx: AggregateUseCaseTypeExpression if typEx.usecase == useCase =>
        resolution // success
      case typeEx: Alternation if typeEx.of.forall(_.isAggregateOf(useCase)) =>
        resolution // success
      case typeEx: Alternation =>
        messages.addError(
          typ.loc,
          s"All alternates of `${typeEx.format}` must be $useCase aggregates",
          suggestion = s"Declare every alternative as ${article(useCase.useCase)} aggregate, " +
            s"e.g. 'type X = ${useCase.useCase} { ??? }'."
        )
        None
      case typEx: AggregateUseCaseTypeExpression =>
        messages.addError(
          typ.loc,
          s"Type expression `${typEx.format}` is not compatible with keyword `$useCase`",
          suggestion =
            s"Declare the type with the matching aggregate use case so it is compatible with " +
              s"`$useCase`, e.g. 'type X = ${useCase.useCase} { ??? }'."
        )
        None
      case typEx: TypeExpression =>
        messages.addError(
          typ.loc,
          s"Type expression `${typEx.format}` needs to be an aggregate for `$useCase`",
          suggestion = s"Declare the type as ${article(useCase.useCase)} aggregate, " +
            s"e.g. 'type X = ${useCase.useCase} { ??? }'."
        )
        None
    end match
  end handleTypeResolution

  private def resolveATypeRef(typeRef: TypeRef, parents: Parents): Resolution[Type] =
    val loc: At = typeRef.loc
    val pathId: PathIdentifier = typeRef.pathId
    val keyword: String = typeRef.keyword
    val resolution: Resolution[Type] = resolveAPathId[Type](pathId, parents)
    resolution match
      case None => None
      case Some((typ: Type, _: Parents)) =>
        keyword match
          case Keyword.type_ | "" => resolution // this is generic, any type so just pass the result
          case Keyword.command =>
            handleTypeResolution(typ, AggregateUseCase.CommandCase, resolution)
          case Keyword.query  => handleTypeResolution(typ, AggregateUseCase.QueryCase, resolution)
          case Keyword.event  => handleTypeResolution(typ, AggregateUseCase.EventCase, resolution)
          case Keyword.result => handleTypeResolution(typ, AggregateUseCase.ResultCase, resolution)
          case Keyword.record => handleTypeResolution(typ, AggregateUseCase.RecordCase, resolution)
          case Keyword.graph =>
            typ.typEx match
              case _: Graph => resolution // success
              case typeEx: Alternation =>
                if typeEx.of.forall(_.getClass == Graph.getClass) then resolution // success
                else
                  messages.addError(
                    typeEx.loc,
                    s"Type expression `${typeEx.format}` needs all elements to be a graph type for keyword `graph` at $loc",
                    suggestion =
                      "Make every alternative a graph type, e.g. 'type X = graph of NodeType'."
                  )
                  None
                end if
              case _ =>
                require(false, "Shouldn't get here")
                None // shouldn't happen
            end match
          case Keyword.table =>
            typ.typEx match
              case _: Table => resolution // success
              case typeEx: Alternation =>
                if typeEx.of.forall(_.getClass == Table.getClass) then resolution // success
                else
                  messages.addError(
                    typ.typEx.loc,
                    s"Type expression `${typ.typEx.format}` needs to be a table for keyword `table` at $loc",
                    suggestion =
                      "Declare the referenced type as a table, e.g. 'type X = table of RowType'."
                  )
                  None
                end if
              case _: TypeExpression =>
                require(false, s"Type $typ is not a")
                None
            end match
        end match
    end match
  end resolveATypeRef

  private def resolveAPathId[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Parents
  ): Resolution[T] =
    if pathId.value.isEmpty then
      // The pathId is empty, can't resolve that
      notResolved[T](pathId, parents.headOption, "the PathId is empty")
      None
    else
      // If we already resolved this one, return it
      val result =
        refMap.definitionOf[T](pathId, parents.head) match
          case Some(definition) =>
            Some(definition -> symbols.parentsOf(definition))
          case None =>
            if pathId.value.size == 1 then
              // Easy case, just search the symbol table and deal with it there.
              // In other words, there really isn't a path to search here, just the
              // symbol table
              searchSymbolTable[T](pathId, parents)
            else
              // Okay, we have multiple names so we first have to find the anchor
              // node from the first name in the PathId. This can be "Root" for the
              // root of the model, a node name directly above, or a node from the
              // symbol table.
              findAnchor[T](pathId, parents) match
                case AnchorNotFoundInParents(topName) =>
                  notResolved(
                    pathId,
                    parents.headOption,
                    s"the PathId is invalid since it's first element, $topName, is not found in PathId ancestors"
                  )
                  None
                case AnchorFoundInSymTab(anchor, anchor_parents) =>
                  // We found the anchor in the
                  resolvePathFromAnchor[T](pathId, parents, anchor, anchor_parents)
                case AnchorFoundInParents(anchor, anchor_parents) =>
                  // We found the anchor in the parents list
                  resolvePathFromAnchor[T](pathId, parents, anchor, anchor_parents)
                case AnchorNotFoundInSymTab(topName) =>
                  notResolved(
                    pathId,
                    parents.headOption,
                    s"the PathId is invalid since it's first element, $topName, does not exist in the model"
                  )
                  None
                case AnchorNotFoundAnywhere(_) =>
                  notResolved(pathId, parents.headOption, "PathID anchor not found")
                  None
                case AnchorIsRoot(anchor, anchor_parents) =>
                  // The first name in the path id was "Root" so start from there
                  resolvePathFromAnchor[T](pathId, parents, anchor, anchor_parents)
                case AnchorIsAmbiguous(_, list) =>
                  // The anchor is ambiguous so generate that message
                  ambiguous[T](
                    pathId,
                    list,
                    Some("The top node in the Path Id is the ambiguous one")
                  )
                  None
              end match
        end match
      result
    end if
  end resolveAPathId

  private def ambiguous[T <: Definition: ClassTag](
    pid: PathIdentifier,
    list: List[SymTabItem],
    context: Option[String] = None
  ): Seq[WithIdentifier] = {
    // Extract all the definitions that were found
    val definitions = list.map(_._1)
    val allDifferent = definitions.map(_.kind).distinct.sizeIs ==
      definitions.size
    val expectedClass = classTag[T].runtimeClass
    definitions.headOption match {
      case Some(head) if head.isAnonymous && allDifferent =>
        // pick the one that is the right type or the first one
        list.find(_._1.getClass == expectedClass) match {
          case Some((definition, parents)) => definition +: parents
          case None                        => list.take(1).map(_._1)
        }
      case _ =>
        val ambiguity = list
          .map { case (definition, parents) =>
            "  " + parents.reverse.map(_.id.value).mkString(".") + "." +
              definition.id.value + " (" + definition.loc + ")"
          }
          .mkString("\n")
        val message =
          s"Path reference '${pid.value.mkString(".")}' is ambiguous. Definitions are:\n$ambiguity" +
            context.map(_ + "\n").getOrElse("")
        if !quiet then
          messages.addError(
            pid.loc,
            message,
            suggestion =
              s"Disambiguate '${pid.value.mkString(".")}' with a more specific, fully-qualified path " +
                "(e.g. 'Domain.Context.Entity.Name') so it matches exactly one definition."
          )
        Seq.empty[WithIdentifier]
    }
  }

  private val vowels: String = "aAeEiIoOuU"

  private def article(thing: String): String = {
    val article = if vowels.contains(thing.head) then "an" else "a"
    s"$article $thing"
  }

  private def candidatesFromPathIdentifier[T <: Definition: ClassTag](
    pid: PathIdentifier,
    defStack: DefinitionStack
  ): Definitions =
    // Recursively resolve this PathIdentifier
    val resolution: Resolution[T] = resolveAPathId[T](pid, defStack.toParentsSeq)
    resolution match
      case None                                             => Seq.empty[Definition]
      case Some((definition: Definition, parents: Parents)) =>
        // if we found the definition
        // Replace the parent stack with the resolved one
        defStack.clear()
        defStack.pushAll(parents.reverse)

        // Return the name and candidates we should next search for
        definition match
          case foundType: Branch[?] =>
            defStack.push(foundType)
            foundType.contents.directDefinitions
          case definition: T =>
            Seq(definition)
        end match
    end match
  end candidatesFromPathIdentifier

  private def candidatesFromTypeExpression(
    typEx: TypeExpression,
    parentStack: DefinitionStack
  ): Definitions = {
    typEx match {
      case a: Aggregation => a.fields
      // if we're at a field composed of more fields, then those fields
      // are what we are looking for
      case Enumeration(_, enumerators) =>
        // if we're at an enumeration type then the numerators are candidates
        enumerators.toSeq
      case a: AggregateUseCaseTypeExpression =>
        // Any kind of Aggregate's fields are candidates for resolution
        a.fields
      case AliasedTypeExpression(_, _, pid) =>
        // if we're at a field that references another type then the candidates
        // are that type's fields. To solve this we need to push
        // that type's path on the name stack to be resolved
        candidatesFromPathIdentifier[Type](pid, parentStack)
      case EntityReferenceTypeExpression(_, entityRef) =>
        candidatesFromPathIdentifier[Entity](entityRef, parentStack)
      case _: Cardinality =>
        // Deliberately NOT descended into. A path reaches through a field to name something
        // inside its type, which is only meaningful when the field denotes EXACTLY ONE value.
        // `?` may be absent, `*`/`+` are many -- there is no single value to reach through.
        // `cardinalityRefusal` turns this empty result into a diagnostic that says so, instead
        // of the caller's generic "the name was not found".
        Seq.empty[Definition]
      case _ =>
        // We cannot descend into any other type expression
        Seq.empty[Definition]
    }
  }

  /** Why a path could not descend through `container`, when the reason is the field's cardinality
    * rather than a missing name.
    *
    * Returns None for every other cause, leaving the ordinary "name not found" wording in place --
    * that message is right whenever the name genuinely is not there.
    */
  private def cardinalityRefusal(
    container: Option[Definition],
    soughtName: String
  ): Option[(String, String)] =
    container match
      case Some(f: Field) =>
        val (why, fix) = f.typeEx match
          case _: Optional =>
            (
              "is optional",
              "establish the value is present before naming something inside it"
            )
          case _: ZeroOrMore | _: OneOrMore =>
            (
              "holds many values",
              "iterate it with 'foreach x in field <path>' and name the field on the element"
            )
          case _: SpecificRange =>
            (
              "holds many values",
              "iterate it with 'foreach x in field <path>' and name the field on the element"
            )
          case _ => ("", "")
        if why.isEmpty then None
        else
          Some(
            s"${f.identify} $why, so there is no single value to descend through to reach " +
              s"'$soughtName'",
            s"A path may only descend through a field that denotes exactly one value. Here, $fix."
          )
      case _ => None
  end cardinalityRefusal

  private def candidatesFromContents(
    contents: Contents[RiddlValue]
  ): Contents[Definition] =
    contents.flatMap {
      case Include(_, _, contents) =>
        // NOTE: An included file can include another file at the same definitional level.
        // NOTE: We need to recursively descend that stack.  An include in a nested definitional level
        // NOTE: will not be picked up by contents.includes because it would be inside another definition.
        // NOTE: So we take the WithIdentifiers from the contents as well as from the includes
        val nested = candidatesFromContents(contents.includes.toContents)
        val current = contents.directDefinitions
        current ++ nested.toSeq
      case definition: Definition =>
        Seq(definition)
      case _ =>
        Seq.empty
    }
  end candidatesFromContents
}
