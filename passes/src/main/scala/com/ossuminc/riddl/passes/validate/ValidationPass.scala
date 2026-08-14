/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{Keyword, PredefType}
import com.ossuminc.riddl.language.{Contents, Finder, Messages, *}
import com.ossuminc.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.utils.PlatformContext
import com.ossuminc.riddl.utils.SeqHelpers.*
import com.ossuminc.riddl.utils.*

import scala.collection.mutable
import scala.collection.immutable.Seq

/** Controls which validation checks are performed.
  *   - Full: all checks including streaming analysis and handler classification (suitable for
  *     CI/final validation)
  *   - Quick: skips expensive postProcess checks for faster interactive feedback (suitable for
  *     JS/LSP)
  */
enum ValidationMode:
  case Full, Quick
end ValidationMode

object ValidationPass extends PassInfo[PassOptions] {
  val name: String = "Validation"
  def creator(options: PassOptions = PassOptions.empty)(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => ValidationPass(in, out)
  }
  def quickCreator(options: PassOptions = PassOptions.empty)(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => ValidationPass(in, out, ValidationMode.Quick)
  }
}

/** A Pass for validating the content of a RIDDL model. This pass can produce many warnings and
  * errors about the model.
  * @param input
  *   Input from previous passes
  * @param outputs
  *   The outputs from prior passes (symbols & resolution)
  */
case class ValidationPass(
  input: PassInput,
  outputs: PassesOutput,
  mode: ValidationMode = ValidationMode.Full
)(using PlatformContext)
    extends Pass(input, outputs)
    with StreamingValidation {

  requires(SymbolsPass)
  requires(ResolutionPass)

  override def name: String = ValidationPass.name

  lazy val resolution: ResolutionOutput =
    outputs.outputOf[ResolutionOutput](ResolutionPass.name).get
  lazy val symbols: SymbolsOutput = outputs.outputOf[SymbolsOutput](SymbolsPass.name).get

  /** Accumulated handler-to-parent mappings collected during processing */
  private val handlerParents: mutable.ListBuffer[(Handler, Definition)] =
    mutable.ListBuffer.empty

  /** Accumulated projectors for cross-cutting completeness checks */
  private val collectedProjectors: mutable.ListBuffer[(Projector, Parents)] =
    mutable.ListBuffer.empty

  /** Accumulated contexts for cross-cutting type checks */
  private val collectedContexts: mutable.ListBuffer[Context] =
    mutable.ListBuffer.empty

  /** Accumulated invariants for usage checks */
  private val collectedInvariants: mutable.ListBuffer[(Invariant, Parents)] =
    mutable.ListBuffer.empty

  /** Accumulated `tell` statements paired with their resolved target processor, for the A6
    * connector-reachability check performed after all connectors are accumulated.
    */
  private val collectedTells: mutable.ListBuffer[(TellStatement, Processor[?])] =
    mutable.ListBuffer.empty

  /** Generate the output of this Pass. This will only be called after all the calls to process have
    * completed.
    *
    * @return
    *   an instance of the output type
    */
  override def result(root: PassRoot): ValidationOutput = {
    ValidationOutput(
      root,
      messages.toMessages,
      inlets.toSeq,
      outlets.toSeq,
      connectors.toSeq,
      // `ValidationOutput.streamlets` is public API typed `Seq[Streamlet]` and keeps its exact
      // meaning: the Streamlet definitions in the model. The graph's node buffer is now wider
      // (every Processor kind), so narrow it back here rather than changing what callers get.
      processors.collect { case s: Streamlet => s }.toSeq,
      computedHandlerCompleteness
    )
  }

  private var computedHandlerCompleteness: Seq[HandlerCompleteness] =
    Seq.empty

  /** Every message type something in the model emits. Empty until `postProcess` fills it — the
    * question is model-wide, so it cannot be answered while visiting one definition.
    */
  private var emittedEventTypes: mutable.Set[Type] = mutable.Set.empty

  override def postProcess(root: PassRoot): Unit = {
    checkOverloads()
    checkTermConsistency()
    if mode == ValidationMode.Full then
      checkStreaming(root)
      checkTellReachability()
      // MUST precede checkCompletenessPostProcess, which asks whether each event is emitted.
      emittedEventTypes = emittedMessageTypes(root)
      computedHandlerCompleteness = classifyHandlers()
      checkCompletenessPostProcess()
    end if
  }

  /** A70: warn when a correlation folds an event that NOTHING in the model emits. Such a fold can
    * never run, so the correlation can never complete — the same class of defect as a required
    * field no fold sets, found from the other direction.
    *
    * Answered here rather than by depending on `MessageFlowPass`: adding that dependency would
    * reorder the standard passes, and the question only needs the set of emitted types, which one
    * sweep of the root supplies. The sweep is GATED on a correlation existing, so a model without
    * one pays nothing.
    *
    * An event is considered emitted when some `send`/`tell`/`yield`/`reply` names it, or when an
    * `Outlet` is DECLARED to carry it. The outlet case matters: a source whose body is `???` but
    * which declares `outlet o is event Shipped` has said it produces `Shipped`, and warning there
    * would be reasoning from an unwritten body — exactly what the `???` ruling forbids. Adaptor
    * translations deliberately do not count; an adaptor is routing, not an origin.
    */
  /** Every message [[Type]] that something in the model EMITS.
    *
    * Emission is a `send`, `tell`, `yield` or `reply` naming the type, or an [[Outlet]] DECLARED to
    * carry it. The outlet case matters: a source whose body is `???` but which declares
    * `outlet o is event Shipped` has said it produces `Shipped`, and reporting it would be
    * reasoning from a body the author already said is absent. Adaptor translations deliberately do
    * NOT count — an adaptor is routing, not an origin.
    *
    * Computed ONCE over the whole root, and compared by resolved-definition identity rather than by
    * name: two contexts may each declare a `Paid`, and emitting one says nothing about the other.
    */
  private def emittedMessageTypes(root: PassRoot): mutable.Set[Type] = {
    val finder = Finder(root.contents)
    val emitted: mutable.Set[Type] = mutable.Set.empty
    def note(t: Option[Type]): Unit = t.foreach(emitted.addOne)
    finder.recursiveFindByType[SendStatement].foreach(s => note(operandType(s.msg)))
    finder.recursiveFindByType[TellStatement].foreach(s => note(operandType(s.msg)))
    finder.recursiveFindByType[YieldStatement].foreach(s => note(operandType(s.msg)))
    finder.recursiveFindByType[ReplyStatement].foreach(s => note(operandType(s.msg)))
    finder
      .recursiveFindByType[Outlet]
      .foreach(o => note(resolution.refMap.definitionOf[Type](o.type_.pathId)))
    emitted
  }

  /** A message declared in an `external` context, or in one whose body is `???`, is produced by
    * something the model does not describe, so its absence is not evidence of anything.
    */
  private def isUnwrittenOrigin(typ: Type): Boolean =
    symbols.parentsOf(typ).exists {
      // `isExternalContext` asks BOTH spellings; see its comment for why asking one is a bug that
      // has been made three times.
      case c: Context => isExternalContext(c) || c.isEmpty
      case _          => false
    }

  /** A6: `tell <msg> to <procRef>` is sugar for a send on the outlet connected to the target's
    * inlet. Warn when the resolved target processor has no inlet reached by any modeled connector.
    * Direct-connector reachability only (transitive reachability is a later refinement). Targets
    * that did not resolve are skipped — another check already reports the missing reference.
    */
  private def checkTellReachability(): Unit = {
    val liveConnectors = connectors.filterNot(_.isEmpty).toSeq
    collectedTells.foreach { case (ts, target) =>
      val reachable = liveConnectors.exists { conn =>
        val connParents = symbols.parentsOf(conn)
        resolvePath[Inlet](conn.to.pathId, connParents).exists { inlet =>
          symbols.parentOf(inlet).exists(_ eq target)
        }
      }
      if !reachable then
        messages.addWarning(
          ts.loc,
          s"'tell' target '${ts.processorRef.pathId.format}' is not reachable via any connector; " +
            s"a connector to one of its inlets is required for delivery",
          suggestion =
            s"Add a connector whose 'to' inlet belongs to '${ts.processorRef.pathId.format}' so the " +
              s"told message can be delivered."
        )
      end if
    }
  }

  /** A49: the same glossary term NAME (case-insensitive) defined at two scopes with DIFFERENT
    * definition text is a contradiction. A redefinition with identical text is fine. Emit a
    * StyleWarning per conflicting definition (the first-seen text vs each later differing text).
    */
  private def checkTermConsistency(): Unit = {
    collectedTerms
      .groupBy(_.id.value.toLowerCase)
      .foreach { case (_, terms) =>
        // Deduplicate by definition text, preserving first-seen order. Identical redefinitions
        // collapse to one entry (no conflict); only differing texts remain as separate entries.
        val distinctByText = mutable.LinkedHashMap.empty[String, Term]
        terms.foreach { t =>
          val text = t.definition.map(_.s).mkString(" ").trim
          distinctByText.getOrElseUpdate(text, t)
        }
        val entries = distinctByText.toSeq
        if entries.size > 1 then {
          val (baseText, _) = entries.head
          entries.tail.foreach { case (text, term) =>
            messages.addStyle(
              term.loc,
              s"term '${term.id.value}' is defined inconsistently: '$baseText' vs '$text'",
              suggestion =
                s"Use a single consistent definition for term '${term.id.value}' across all scopes."
            )
          }
        }
      }
  }

  private def checkCompletenessPostProcess(): Unit = {
    // Completeness 4e: handlers that are empty or prompt-only
    computedHandlerCompleteness.foreach { hc =>
      // WALKS UP to the enclosing Context; `hc.parent` alone is not enough. A handler inside an
      // ENTITY has the Entity as its parent, so a `case c: Context` on `hc.parent` matched only
      // handlers declared directly in a context -- and an entity's handler, which is the common
      // case, was never exempt no matter how the context was marked. Found 2026-08-12 by a test
      // written to prove the exemption worked; the corpus had not shown it, because widening the
      // exemption removes warnings and a corpus A/B only shows what CHANGED.
      //
      // `isExternalContext` asks both spellings of external -- the intention (`external context
      // Foo`, what models write) and the legacy option.
      val isExternal = (hc.parent +: symbols.parentsOf(hc.handler)).exists {
        case c: Context => isExternalContext(c)
        case _          => false
      }
      // The predefined terminators' handlers are intentionally behavior-free: consuming
      // everything and producing nothing are implemented by the runtime, not modelled. They are
      // library definitions, not model content, so completeness does not apply to them.
      if !isExternal && !isPredefined(hc.handler) then {
        hc.category match {
          case BehaviorCategory.Empty =>
            messages.addCompleteness(
              hc.handler.errorLoc,
              s"${hc.handler.identify} in ${hc.parent.identify} has no executable statements",
              suggestion =
                "Add executable statements (tell, send, set, morph, become, reply) to the handler's on-clauses."
            )
          // A REPOSITORY is exempt (Reid, 2026-08-12). Most of a repository's on-clauses legitimately
          // hold a single `do` standing in for the SQL that will implement them -- naming the
          // persistence step IS the modelling, and there is no further executable statement to add.
          // Nagging them had a real cost: reactive-bbq added 97 `set` statements to repository
          // handlers purely to silence this, which is what made `set` look like something a
          // repository does. Do not reinstate this without also un-banning `set` there.
          case BehaviorCategory.PromptOnly if !hc.parent.isInstanceOf[Repository] =>
            messages.addCompleteness(
              hc.handler.errorLoc,
              // `do`, NOT `prompt`. `do` is the canonical spelling and `prompt` is a deprecated
              // synonym, so naming `prompt` sent authors looking for something their model does not
              // contain. (`prompt(…)` with parens is a VALUE, a different thing again.)
              s"${hc.handler.identify} in ${hc.parent.identify} contains only 'do' statements; " +
                "executable statements (tell, send, morph, set, etc.) are needed",
              suggestion =
                "Add executable statements (tell, send, set, morph) alongside the 'do' statements so the handler does real work."
            )
          case BehaviorCategory.PromptOnly => () // a Repository: see above
          case BehaviorCategory.Executable => ()
        }
      }
    }
    // Completeness: type pairing checks across all contexts
    collectedContexts.foreach { context =>
      val contextTypes = context.types
      val commands = contextTypes.filter { t =>
        t.typEx match {
          case auc: AggregateUseCaseTypeExpression => auc.usecase == AggregateUseCase.CommandCase
          case _                                   => false
        }
      }
      val events = contextTypes.filter { t =>
        t.typEx match {
          case auc: AggregateUseCaseTypeExpression => auc.usecase == AggregateUseCase.EventCase
          case _                                   => false
        }
      }
      val queries = contextTypes.filter { t =>
        t.typEx match {
          case auc: AggregateUseCaseTypeExpression => auc.usecase == AggregateUseCase.QueryCase
          case _                                   => false
        }
      }
      val results = contextTypes.filter { t =>
        t.typEx match {
          case auc: AggregateUseCaseTypeExpression => auc.usecase == AggregateUseCase.ResultCase
          case _                                   => false
        }
      }
      // #16: Command types with no fields (skip ??? placeholders)
      commands.foreach { cmd =>
        cmd.typEx match {
          case auc: AggregateUseCaseTypeExpression if auc.fields.isEmpty && !cmd.isEmpty =>
            messages.addMissing(
              cmd.errorLoc,
              s"${cmd.identify} is a command with no fields; commands should carry data",
              suggestion =
                s"Add fields to ${cmd.identify}, e.g. 'command X is { someField: Type }'."
            )
          case _ => ()
        }
      }
      // #17: an event nothing in the model emits. Answered from `emittedEventTypes`, which is
      // computed ONCE over the whole root in postProcess; see `emittedMessageTypes`.
      //
      // Rewritten 2026-08-12 (Reid). The original asked the question four ways wrong, and every
      // one of them produced FALSE positives on correct models:
      //   - it scanned only entity and state handlers IN THIS CONTEXT, so an event emitted from
      //     another context read as unproduced;
      //   - it counted only `send` and `tell`, so a `yield event X` -- the canonical spelling for
      //     an event-sourced entity -- read as unproduced;
      //   - it matched by NAME, so two contexts each declaring `Paid` silenced each other;
      //   - it was gated on `context.entities.exists(_.nonEmpty)`, so a context with no entities
      //     was never checked at all (a false NEGATIVE, and the reason the gate is gone).
      // It also now exempts an event whose origin is an `external` or `???` context, for the same
      // reason every other check does.
      //
      // This subsumed a correlation-scoped twin added the day before, which reported the same fact
      // about a folded event in different words -- two messages, one defect. That check is gone;
      // A70's "handled events that nothing emits" rule is satisfied by this one.
      events.foreach { evt =>
        if !emittedEventTypes.exists(_ eq evt) && !isUnwrittenOrigin(evt) then {
          messages.addCompleteness(
            evt.errorLoc,
            s"${evt.identify} is defined but nothing in the model emits it",
            suggestion = s"Emit ${evt.identify} with a 'send', 'tell', 'yield' or 'reply' from the " +
              s"definition that produces it, declare an outlet carrying it, or remove the unused event."
          )
        }
      }
      // #18: Query without corresponding result (and vice versa)
      if queries.nonEmpty && results.isEmpty then {
        messages.addCompleteness(
          context.errorLoc,
          s"${context.identify} defines queries but no result types",
          suggestion =
            s"Add a result type to ${context.identify}, e.g. 'type XResult = result { ??? }'."
        )
      }
      if results.nonEmpty && queries.isEmpty then {
        messages.addCompleteness(
          context.errorLoc,
          s"${context.identify} defines results but no query types",
          suggestion =
            s"Add a query type to ${context.identify}, e.g. 'type XQuery = query { ??? }'."
        )
      }
    }
    // #23: an invariant that can never run.
    //
    // Rewritten 2026-08-04. It used to warn about EVERY invariant no `require` named, which is now
    // wrong for the common case: an invariant is applied IMPLICITLY across its declaring scope
    // (§15.2), so not being named is the norm rather than a defect. Only the one form that cannot
    // be implicit -- `requires <type>`, whose value ambient scope cannot supply -- is inert when
    // nothing invokes it.
    //
    // The old version matched `ir.pathId.value.lastOption`, i.e. by LAST-COMPONENT NAME. That is
    // the pattern A54 was burned by (it let `garbage.nonsense.realField` validate) and which
    // CLAUDE.md now forbids; two same-named invariants under different parents were
    // indistinguishable, so referencing one silenced the warning for both. Resolution goes through
    // the refMap, by identity.
    if collectedInvariants.nonEmpty then {
      val applied = mutable.Set.empty[Invariant]
      handlerParents.foreach { case (handler, handlerParents) =>
        handler.clauses.foreach { clause =>
          walkStatements(clause.contents) {
            case RequireStatement(_, ir: InvariantRef, _) =>
              resolution.refMap
                .definitionOf[Invariant](ir.pathId, handler)
                .foreach(applied.add)
            case _ => ()
          }
        }
      }
      collectedInvariants.foreach { case (inv, _) =>
        if inv.nonEmpty && !inv.isImplicit && !applied.exists(_ eq inv) then {
          messages.addUsage(
            inv.errorLoc,
            s"${inv.identify} declares 'requires <type>' so it is never applied implicitly, and " +
              "no 'require invariant' statement applies it either — it will never be checked",
            suggestion = s"Apply it with 'require invariant ${inv.id.value} with <expr>', or drop " +
              "its 'requires' clause so it applies implicitly across its scope."
          )
        }
      }
    }
  }

  def process(value: RiddlValue, parents: ParentStack): Unit = {
    val parentsAsSeq: Parents = parents.toParents
    // Task 10 (A32): shape/arity ascription check runs for EVERY processor kind
    // (context/entity/projector/repository/adaptor/streamlet), so it is dispatched here
    // rather than duplicated in each per-kind validate method.
    //
    // Registration into the streaming graph rides the same dispatch, and for the same reason:
    // ports and stream shape live on Processor, so every kind is a potential node of a stream
    // path. Registering here rather than in validateStreamlet is what stops the graph seeing
    // only one of the six kinds and reporting a sink downstream of an adaptor or entity as
    // having no upstream source.
    value match {
      case p: Processor[?] =>
        validateProcessorShape(p)
        addProcessor(p)
      case _ => ()
    }
    // A53: a scope may declare AT MOST ONE version. Dispatched generically here so all five
    // version-bearing scopes (Root, Module, Domain, Context, Entity) are covered in one place.
    value match {
      case wv: (WithVersion[?] & Definition) => checkSingleVersion(wv)
      case _                                 => ()
    }
    // A47: a scope may declare AT MOST ONE copyright, on the same terms as a version. Dispatched
    // generically so all nine copyright-bearing scopes are covered in one place.
    value match {
      case wc: (WithCopyright[?] & Definition) => checkSingleCopyright(wc)
      case _                                   => ()
    }
    // A25/A54: validate `foreach` collection scoping and value expressions once per statement-bearing
    // container (on-clause or function). checkStatementScopes recurses through nested statement
    // bodies threading `let` scope, so invoking it at the container root covers every statement at
    // any depth exactly once.
    //
    // A70/self: SagaStep joined this dispatch (was previously the one statement-bearing container
    // with NO checkStatementScopes call at all -- its do/undoStatements sat completely outside
    // value validation, including ValueRef resolution and, notably, the `self` legality check this
    // gap was found auditing). No `ss +: parentsAsSeq`: SagaStep is a `Leaf`, not a `Branch`
    // (`Parents = Seq[Branch[?]]` cannot hold it), and it is deliberately never pushed onto the
    // parent stack elsewhere either (see `Pass.traverse`'s SagaStep case) -- `parents.head` stays
    // the Saga, which is the correct resolution scope for a step's statements.
    value match {
      case oc: OnClause =>
        checkStatementScopes(oc.statements, Seq.empty[LetStatement], oc +: parentsAsSeq)
      case fn: Function =>
        checkStatementScopes(fn.statements, Seq.empty[LetStatement], fn +: parentsAsSeq)
      case ss: SagaStep =>
        checkStatementScopes(
          ss.doStatements.toSeq.collect { case s: Statement => s },
          Seq.empty[LetStatement],
          parentsAsSeq
        )
        checkStatementScopes(
          ss.undoStatements.toSeq.collect { case s: Statement => s },
          Seq.empty[LetStatement],
          parentsAsSeq
        )
      // Review round 1 (Task 7), Important #2: `Correlation.timeoutStatements` is a FIELD, exactly
      // like `SagaStep`'s `do`/`undoStatements` above -- it was reached by generic traversal (see
      // `Pass.traverse`'s `Correlation` case, which pushes the correlation and walks
      // `timeoutStatements` right alongside `contents`, so each statement DOES reach
      // `validateStatement`) but was never handed to `checkStatementScopes`, so every check that
      // lives ONLY there (checkInitiate/checkTerminate arity+type, let-scope threading, ValueRef
      // resolution, tell addressing) silently never ran for a timeout block. `c +: parentsAsSeq`
      // mirrors `oc +: parentsAsSeq` above: `traverse` calls `process` (which is where this dispatch
      // runs) BEFORE `parents.push(correlation)`, so prepending `c` here reproduces the SAME
      // `parents.head` traversal would install a moment later -- verified empirically (see
      // task-7-report.md) rather than assumed: `parents.head` is the `Correlation`, and no
      // `Function` sits in its ancestor chain, so `checkInstanceEffectScope`'s two bans stay
      // correctly false for a timeout block, which is REQUIRED (§6.7 -- the block exists to have an
      // effect).
      case c: Correlation =>
        checkStatementScopes(
          c.timeoutStatements.toSeq.collect { case s: Statement => s },
          Seq.empty[LetStatement],
          c +: parentsAsSeq
        )
      case _ => ()
    }
    value match {
      case f: AggregateValue =>
        f match {
          case f: Field  => validateField(f, parentsAsSeq)
          case m: Method => validateMethod(m, parentsAsSeq)
        }
      case t: Type =>
        validateType(t, parentsAsSeq)
      case e: Enumerator =>
        validateEnumerator(e, parentsAsSeq)
      case i: Invariant =>
        collectedInvariants.addOne((i, parentsAsSeq))
        validateInvariant(i, parentsAsSeq)
      case t: Term =>
        validateTerm(t)
      case sa: User =>
        validateUser(sa, parentsAsSeq)
      case omc: OnMessageLikeClause => // OnMessageClause and OnEventClause
        validateOnMessageClause(omc, parentsAsSeq)
      case oic: OnInitializationClause =>
        checkDefinition(parentsAsSeq, oic)
      case otc: OnTerminationClause =>
        checkDefinition(parentsAsSeq, otc)
        checkOnTermLeadingParameter(otc, parentsAsSeq)
      case oac: OnActivationClause =>
        checkDefinition(parentsAsSeq, oac)
      case opc: OnPassivationClause =>
        checkDefinition(parentsAsSeq, opc)
      case ooc: OnOtherClause =>
        checkDefinition(parentsAsSeq, ooc)
        checkOnOtherBinding(ooc, parentsAsSeq) // A57
        if ooc.statements.isEmpty then {
          messages.addCompleteness(
            ooc.errorLoc,
            "Empty 'on other' clause will silently discard unhandled messages",
            suggestion =
              "Add statements to the 'on other' clause (e.g. log or error), or remove it if discarding is intentional."
          )
        }
      case statement: Statement =>
        validateStatement(statement, parentsAsSeq)
      case h: Handler =>
        validateHandler(h, parentsAsSeq)
        parentsAsSeq.headOption.foreach { parent =>
          handlerParents.addOne((h, parent))
        }
      case c: Constant =>
        validateConstant(c, parentsAsSeq)
      case s: State =>
        validateState(s, parentsAsSeq)
      case c: Correlation =>
        validateCorrelation(c, parentsAsSeq)
      case f: Function =>
        validateFunction(f, parentsAsSeq)
      case i: Inlet =>
        validateInlet(i, parentsAsSeq)
      case o: Outlet =>
        validateOutlet(o, parentsAsSeq)
      case c: Connector =>
        validateConnector(c, parentsAsSeq)
      case a: Author =>
        validateAuthor(a, parentsAsSeq)
      case s: SagaStep =>
        validateSagaStep(s, parentsAsSeq)
      case e: Entity =>
        validateEntity(e, parentsAsSeq)
      case a: Adaptor =>
        validateAdaptor(a, parentsAsSeq)
      case s: Streamlet =>
        validateStreamlet(s, parentsAsSeq)
      case p: Projector =>
        collectedProjectors.addOne((p, parentsAsSeq))
        validateProjector(p, parentsAsSeq)
      case r: Repository =>
        validateRepository(r, parentsAsSeq)
      case s: Saga =>
        validateSaga(s, parentsAsSeq)
      case c: Context =>
        collectedContexts.addOne(c)
        validateContext(c, parentsAsSeq)
      case d: Domain =>
        validateDomain(d, parentsAsSeq)
      case e: Epic =>
        validateEpic(e, parentsAsSeq)
      case uc: UseCase =>
        validateUseCase(uc, parentsAsSeq)
      case grp: Group =>
        validateGroup(grp, parentsAsSeq)
      case in: Input =>
        validateInput(in, parentsAsSeq)
      case out: Output =>
        validateOutput(out, parentsAsSeq)
      case cg: ContainedGroup =>
        validateContainedGroup(cg, parentsAsSeq)
      case root: Root =>
        checkContents(root, parentsAsSeq)
      case include: Include[?] =>
        validateInclude(include)
      case bi: BASTImport =>
        validateBASTImport(bi, parentsAsSeq)
      case s: Schema =>
        validateSchema(s, parentsAsSeq)
      case r: Relationship =>
        validateRelationship(r, parentsAsSeq)
      case _: MatchCase           => () // Validated through MatchStatement
      case _: MatchPattern        => () // A29: validated through MatchStatement
      case _: Definition          => () // abstract type
      case _: NonDefinitionValues => () // We only validate definitions
      // NOTE: Never put a catch-all here, every Definition type must be handled
    }
  }
  private def validateOnClause(onClause: OnClause): Unit =
    if onClause.statements.isEmpty then
      messages.add(
        missing(
          s"${onClause.identify} should have statements",
          onClause.loc,
          suggestion =
            s"Add one or more statements to ${onClause.identify} (use '???' as a placeholder if needed)."
        )
      )
    // A23: refusals (require/error) must precede any effect within each linear statement list.
    checkRefusalsFirst(onClause.statements)
  end validateOnClause

  private def validateOnMessageClause(omc: OnMessageLikeClause, parents: Parents): Unit = {
    checkDefinition(parents, omc)
    validateOnClause(omc)
    val maybeEntity: Option[Entity] = parents.collectFirst { case e: Entity => e }
    // Shadows the inherited method name deliberately -- this is the boolean for THIS clause. It
    // now asks both spellings of external, so an entity inside an `external context` is exempt
    // from the command->event and query->result completeness checks as intended.
    val isExternalContext: Boolean = parents
      .collectFirst { case c: Context => c }
      .exists(c => this.isExternalContext(c))
    if omc.msg.nonEmpty then {
      checkMessageRef(omc.msg, parents, Seq(omc.msg.messageKind))
      // Command→event and query→result checks apply only to entities
      if maybeEntity.isDefined && !isExternalContext then {
        val entity = maybeEntity.get
        omc.msg.messageKind match {
          case AggregateUseCase.CommandCase =>
            // Refusing a command IS processing it: the clause decided, it declined, and there is
            // nothing to record, so there is no event to send. Without this the rule was inverted —
            // it flagged the honest refusal-only clause, and was SILENCED by adding a send after
            // the refusal, which A23's refusals-before-effects ordering makes unreachable. It was
            // rewarding exactly the dead code a modeller should avoid.
            //
            // Checked on EVERY path, for the same reason as `checkYieldConformance` — this check
            // shared the identical "anywhere in the clause" weakness, so a conditional refusal
            // silenced it too. See `dischargesOnEveryPath`.
            val emitted = dischargesOnEveryPath(omc.contents) {
              case _: ErrorStatement | _: RequireStatement => true
              case s: SendStatement  => operandMessageKind(s.msg).contains(AggregateUseCase.EventCase)
              case t: TellStatement  => operandMessageKind(t.msg).contains(AggregateUseCase.EventCase)
              case y: YieldStatement => operandMessageKind(y.msg).contains(AggregateUseCase.EventCase)
              case _                 => false
            }
            if !emitted then
              messages.addCompleteness(
                omc.errorLoc,
                s"Command processing in ${entity.identify} should result in sending an event",
                suggestion =
                  "Send, tell, or yield an event from this command handler, e.g. 'send event SomethingHappened to outlet ...' or 'yield event SomethingHappened'."
              )
          case AggregateUseCase.QueryCase =>
            val finder = Finder(omc.contents)
            val sends: Seq[SendStatement] = finder.recursiveFindByType[SendStatement]
            val tells: Seq[TellStatement] = finder.recursiveFindByType[TellStatement]
            // REPLIES, not yields. A query answers with `reply` as of 2.0, so looking only for
            // YieldStatement here made the canonical spelling invisible and reported a
            // well-formed handler as incomplete. Both are accepted: a `yield result` is already
            // an Error from `checkResponsePairing`, and reporting it twice helps nobody.
            val replies: Seq[Statement] =
              finder.recursiveFindByType[ReplyStatement] ++
                finder.recursiveFindByType[YieldStatement]
            val foundSend = sends.nonEmpty &&
              sends.exists(s => operandMessageKind(s.msg).contains(AggregateUseCase.ResultCase))
            val foundTell = tells.nonEmpty &&
              tells.exists(t => operandMessageKind(t.msg).contains(AggregateUseCase.ResultCase))
            val foundReply = replies.exists { st =>
              val operand = st match
                case r: ReplyStatement => r.msg
                case y: YieldStatement => y.msg
                case _                 => MessageRef.empty
              operandMessageKind(operand).contains(AggregateUseCase.ResultCase)
            }
            if !(foundSend || foundTell || foundReply) then
              messages.addCompleteness(
                omc.errorLoc,
                s"Query processing in ${entity.identify} should result in a reply or sending a result",
                suggestion =
                  "Yield a result or send a result type from this query handler, e.g. 'reply result QueryResult'."
              )
          case _ =>
        }
      }
      // A19↔A22 conformance applies to any context (not only entities) whose handled message is a
      // command/query with a `yields` contract.
      checkYieldConformance(omc, parents)
    } else {}
    omc.from.foreach { (_: Option[Identifier], ref: Reference[Definition]) =>
      checkRef[Definition](ref, parents)
    }
    omc.binding.foreach(id => checkLocalName(id, "on-clause binding", parents))
    // A55: a binding may legally collide with a field of the message or the entity state — bare
    // `foo` is the binding and `foo.foo` reaches the field — but that overload is easy to misread.
    omc.binding.foreach { id =>
      if fieldsInScope(omc +: parents).exists(_.id.value == id.value) then
        messages.addWarning(
          id.loc,
          s"on-clause binding '${id.value}' has the same name as a field of the handled message " +
            "or entity state",
          suggestion =
            s"Rename the binding, or write '${id.value}.${id.value}' to reach the field " +
              s"— bare '${id.value}' is the binding."
        )
    }
  }

  /** A55: shared checks for a LOCAL name — an on-clause message binding or a `let`. A local should
    * BEGIN with a lowercase letter (a StyleWarning, not an Error, so camelCase like `myCounter`
    * stays legal), and shadowing an outer definition is legal but worth a Warning because a reader
    * cannot tell which one a bare name means.
    */
  private def checkLocalName(id: Identifier, what: String, parents: Parents): Unit =
    if id.value.nonEmpty && !id.value.head.isLower then
      messages.addStyle(
        id.loc,
        s"$what '${id.value}' should begin with a lowercase letter",
        suggestion = s"Local names are conventionally lowerCamelCase; rename it to " +
          s"'${id.value.head.toLower.toString + id.value.drop(1)}'."
      )
    if symbols.lookup[Definition](Seq(id.value)).nonEmpty then
      messages.addWarning(
        id.loc,
        s"$what '${id.value}' shadows a definition of the same name",
        suggestion = s"Rename the local so a bare '${id.value}' is unambiguous to a reader."
      )
    end if
  end checkLocalName

  /** A56: the message [[Type]] behind any operand shape, when it can be resolved.
    *
    * For a [[ValueRef]] this is the on-clause binding's type: `ResolutionPass.resolveValueRef` has
    * already keyed the binding's path to the handled message's Type, so no new lookup rule is
    * needed here — only the extra case.
    */
  private def operandType(m: MessageRef | Constructor | ValueRef): Option[Type] = m match
    case mr: MessageRef => resolution.refMap.definitionOf[Type](mr.pathId)
    case c: Constructor => resolution.refMap.definitionOf[Type](c.ref.pathId)
    case vr: ValueRef   => resolution.refMap.definitionOf[Type](vr.path)

  /** A54/A56: the [[AggregateUseCase]] of a widened message operand — a bare ref, a constructor
    * whose ref names the constructed message/record, or a binding named by the enclosing on-clause.
    *
    * **Optional on purpose.** A keyword-led ref carries its kind syntactically, but a binding's kind
    * is only known once resolved, and [[AggregateUseCase]] has no "unknown" member to fall back to.
    * Returning a wrong kind here would silently mis-answer the event-sourcing rules, so an
    * unresolved binding answers `None` and every caller's `contains` reads it as "not that kind".
    */
  private def operandMessageKind(m: MessageRef | Constructor | ValueRef): Option[AggregateUseCase] =
    m match
      case mr: MessageRef => Some(mr.messageKind)
      case c: Constructor => Some(c.ref.messageKind)
      case _: ValueRef =>
        operandType(m).flatMap(_.typEx match
          case auc: AggregateUseCaseTypeExpression => Some(auc.usecase)
          case _                                   => None
        )

  /** `yield` emits an EVENT; `reply` answers with a RESULT. Enforce the pairing.
    *
    * RIDDL has two message pairings -- command/event and query/result -- and until 2.0 `yield`
    * spelled both while `reply` was a deprecated synonym for it. Reid split them (2026-08-08) so a
    * handler body says which half of the language it is in, and so `ask` has something to name:
    * the value an `ask` produces is the one a `reply` provides.
    *
    * Checked here rather than in the parser because the two statements are structurally identical
    * -- only the message KIND differs -- and `operandMessageKind` reads that kind from the ref
    * subclass (`EventRef`/`ResultRef`/...), so no resolution is needed but the message can still
    * name both halves. A `Constructor` operand carries its kind through `c.ref.messageKind`, so
    * this covers both operand shapes.
    *
    * `None` means the kind is not recoverable (a ValueRef whose type has not resolved); stay
    * silent there rather than guess -- other checks report the unresolved reference.
    */
  private def checkResponsePairing(
    msg: MessageRef | Constructor,
    keyword: String,
    wanted: AggregateUseCase
  ): Unit =
    operandMessageKind(msg).foreach { actual =>
      if actual != wanted then
        val other = if wanted == AggregateUseCase.EventCase then Keyword.reply else Keyword.yield_
        val otherWanted =
          if wanted == AggregateUseCase.EventCase then AggregateUseCase.ResultCase
          else AggregateUseCase.EventCase
        messages.addError(
          msg match { case mr: MessageRef => mr.loc; case c: Constructor => c.loc },
          s"`$keyword` takes ${article(wanted.useCase)}, but ${msg.format} is " +
            s"${article(actual.useCase)}",
          suggestion =
            s"Use `$keyword` for a ${wanted.useCase} and `$other` for a ${otherWanted.useCase} — " +
              s"a command yields an event, a query replies a result."
        )
      end if
    }
  end checkResponsePairing

  /** A57: the envelope type named by the nearest `option message_envelope` in scope, if any.
    *
    * The option is SCOPE-INHERITED: declared on a context it covers every entity in it, so this
    * walks UP the parent chain and takes the FIRST it finds, letting an inner declaration override
    * an outer one the same way every other walked-up option behaves.
    */
  private def envelopeInScope(parents: Parents): Option[String] =
    parents.iterator
      .collectFirst {
        case wo: WithMetaData if wo.getOptionValue("message_envelope").nonEmpty =>
          wo.getOptionValue("message_envelope").get
      }
      .flatMap(_.args.headOption.map(_.s))

  /** A57: the two rules for `on other as x [: <envelope>]`, both of which Reid specified.
    *
    * The binding is only meaningful when an envelope exists to type it, and the ascription is an
    * optional RESTATEMENT of the option rather than a per-clause override — a clause that could
    * contradict its scope would mean reading one clause tells you nothing about its siblings, which
    * is exactly what scope inheritance exists to prevent.
    */
  private def checkOnOtherBinding(ooc: OnOtherClause, parents: Parents): Unit =
    val inScope = envelopeInScope(parents)
    (ooc.binding, ooc.envelopeType, inScope) match
      case (None, None, _) => () // plain `on other`, unchanged by A57
      case (_, Some(t), None) =>
        messages.addError(
          t.loc,
          s"'on other' names the envelope type '${t.pathId.format}', but no " +
            s"'option message_envelope' is in scope, so there is no envelope to type",
          suggestion = s"Declare 'option message_envelope(\"${t.pathId.format}\")' on this " +
            s"definition or an enclosing one, or drop the ': ${t.pathId.format}' ascription."
        )
      case (Some(b), None, None) =>
        messages.addError(
          ooc.loc,
          s"'on other as ${b.value}' has no envelope to bind: no 'option message_envelope' is in " +
            s"scope, and without one '${b.value}' would have no type",
          suggestion = "Declare 'option message_envelope(\"Riddl.Envelope\")' on this definition " +
            "or an enclosing one, or drop the binding and write 'on other'."
        )
      case (_, Some(t), Some(named)) if t.pathId.format != named =>
        messages.addError(
          t.loc,
          s"'on other' names the envelope type '${t.pathId.format}', but 'option " +
            s"message_envelope' in scope names '$named'; the ascription restates the option, it " +
            s"does not override it",
          suggestion = s"Change the ascription to ': $named', or drop it — it is optional and is " +
            s"inferred from the option."
        )
      case _ => () // binding with an envelope in scope, agreeing ascription, or no binding

  /** Task 3: `on term` is the destructor, and unlike `on init` it is invoked from OUTSIDE the
    * instance — so the caller must say which one. The rule is grammar-shaped (a leading `Id(...)`
    * parameter) but can only be checked here: the parser sees a bare parameter list and cannot
    * know which processor encloses it, or whether a resolved `UniqueId` names that processor.
    *
    * A missing parameter list and a wrong leading type are reported with the SAME message — both
    * are "no correctly-typed leading id parameter" — which is what `otc.parameters.headOption`
    * naturally collapses them into.
    */
  private def checkOnTermLeadingParameter(otc: OnTerminationClause, parents: Parents): Unit =
    val enclosing = parents.collectFirst { case p: Processor[?] => p }
    enclosing.foreach { p =>
      val ok = otc.parameters.headOption.exists { a =>
        a.typeEx match
          case uid: UniqueId => uid.entityPath.value.lastOption.contains(p.id.value)
          case _             => false
      }
      check(
        ok,
        s"'on term' in ${p.identify} must declare its first parameter as Id(${p.id.value}) — " +
          "the id of the instance to terminate",
        Error,
        otc.loc,
        suggestion = s"Write 'on term(id: Id(${p.id.value}), …) is { … }'."
      )
    }
  end checkOnTermLeadingParameter

  /** A56: check a bound `tell`/`send` operand — `tell p to entity F`.
    *
    * `p` must name a binding introduced by an enclosing on-clause, which `ResolutionPass` has keyed
    * to the handled message's Type. Nothing else can supply a message value, so an unresolved name
    * here is an Error rather than a warning: the statement names a message that does not exist.
    *
    * This check is owned by validation, not the resolver, for the reason recorded in
    * `ResolutionPass.quietly` — a ValueRef may legitimately fail to resolve there (a `let`-local is
    * lexical and invisible to the symbol table), so the resolver stays quiet and the diagnostic is
    * issued here where the operand's meaning is known.
    */
  private def checkBoundMessageOperand(vr: ValueRef, statement: String): Unit =
    if resolution.refMap.definitionOf[Type](vr.path).isEmpty then
      messages.addError(
        vr.loc,
        s"'${vr.path.format}' in this '$statement' does not name a message bound by an enclosing " +
          s"'on' clause, so there is no message to deliver",
        suggestion = s"Bind the handled message first, e.g. " +
          s"'on ${vr.path.format}: command SomeCommand is { $statement ${vr.path.format} to … }', " +
          s"or name the message explicitly, e.g. '$statement command SomeCommand to …'."
      )

  /** A54/A56: the NAME of the message an operand denotes. For a ref or constructor that is the last
    * path component; for a binding it is the resolved Type's id, since the binding's own path names
    * the local (`p`), not the message.
    */
  private def operandMessageName(m: MessageRef | Constructor | ValueRef): String = m match
    case vr: ValueRef => operandType(vr).map(_.id.value).getOrElse("")
    case other: (MessageRef | Constructor) =>
      operandPathId(other).value.lastOption.getOrElse("")

  /** A54: the [[PathIdentifier]] of a widened message operand (the bare ref's, or the constructor
    * ref's).
    */
  private def operandPathId(m: MessageRef | Constructor): PathIdentifier = m match
    case mr: MessageRef => mr.pathId
    case c: Constructor => c.ref.pathId

  /** A statement that WRITES state is legal only where the container owns state (Reid, 2026-08-12).
    *
    * An [[AST.Entity]] owns its [[AST.State]]; a [[AST.Projector]] owns the read-model record its
    * folds build, and A70's correlations REQUIRE `set` there. Everything else owns nothing:
    * Computational Model §3.5 puts a Context's state in its contained entities, repositories and
    * projectors "never in the Context itself", and §9.5 says a saga's state is housekeeping with
    * "no domain-specific value".
    *
    * A REPOSITORY is banned despite 97 uses across reactive-bbq. Those were added to silence
    * "contains only prompt statements", which is a riddlc defect now fixed by exempting
    * repositories from that warning — so they are evidence about the warning, not about what a
    * repository does. Do not re-admit `set` here without re-reading that ruling.
    *
    * A [[AST.Function]] is deliberately not reported: A26 already rejects `set` in a function body
    * at the keyword, so a second message here would double-report the same mistake.
    */
  private def checkSetScope(ss: SetStatement, parents: Parents): Unit =
    enclosingWriteScope(parents) match
      case Some(_: Entity) | Some(_: Projector) => () // owns the data being written
      case Some(_: Function)                    => () // A26 already rejected it at the keyword
      case Some(owner) =>
        messages.addError(
          ss.loc,
          s"'set' is not allowed in ${owner.identify}, which owns no state to write",
          suggestion = owner match
            case _: Saga =>
              "A saga coordinates by sending commands; 'tell' the command to the entity that owns " +
                "the state, so the step's compensation can reverse it."
            case _: Context =>
              "State lives in a context's entities, repositories and projectors, never in the " +
                "context itself; move the 'set' into the entity's handler."
            case _: Repository =>
              "A repository's on-clause describes persistence — a 'do' statement standing in for " +
                "the storage operation is the modelling, and needs no 'set'."
            case _ =>
              "Move the 'set' into the handler of the entity that owns the state."
        )
      case None => () // no enclosing processor at all; nothing meaningful to say
  end checkSetScope

  /** A70/§4.6: `get from state` reads an entity's state directly, so it is legal ONLY inside the
    * entity that owns that state.
    *
    * Two distinct wrongs, one rule. Outside any entity there is no state to read — and in a saga
    * step this is the rule the `ask` ban already states (§9.5: a saga must not depend on dynamic
    * state), which reading state directly would otherwise bypass by spelling it differently.
    * Inside a DIFFERENT entity it crosses §4.6's encapsulation rule: an entity's data "is 100%
    * encapsulated by the entity and acted upon only by the entity's handlers", so only a message
    * may cross that boundary.
    *
    * The second half is why this rule cannot live in the parser: it needs the resolved [[AST.State]]
    * and its owner, neither of which exists at parse time.
    */
  private def checkStateReadScope(statement: Statement, parents: Parents): Unit =
    val reads = statementValues(statement).flatMap(stateReadsIn)
    if reads.nonEmpty then
      val enclosingEntity: Option[Entity] = parents.collectFirst { case e: Entity => e }
      reads.foreach { (gv, sr) =>
        enclosingEntity match
          case None =>
            messages.addError(
              gv.loc,
              s"'${gv.format}' is not allowed here; state may be read only inside the entity that " +
                "owns it",
              suggestion = "Send a message to the entity that owns the state and let its handler " +
                "reply, rather than reading the state directly."
            )
          case Some(entity) =>
            resolution.refMap.definitionOf[State](sr.pathId).foreach { state =>
              if !symbols.parentsOf(state).exists(_ eq entity) then
                messages.addError(
                  gv.loc,
                  s"'${gv.format}' reads ${state.identify}, which ${entity.identify} does not own; " +
                    "an entity's state is encapsulated by that entity",
                  suggestion = s"Send a message to the entity owning ${state.identify} and let its " +
                    "handler reply, rather than reading its state directly."
                )
            }
      }
  end checkStateReadScope

  /** A70/instance-identity Task 7: `initiate` and `terminate` are EFFECTS -- one mints a processor
    * instance, the other destroys one -- so they are subject to the same effect bans as
    * `tell`/`send`/`set`/etc. Two of the three banned contexts are enforced here:
    *
    *   - a FUNCTION body, which is pure (A26) and may not create or destroy instances any more than
    *     it may mutate state or send a message;
    *   - an `on activate`/`on passivate` clause, which must be side-effect-free for the same reason
    *     outbound messaging is already banned there (`messagingStatements` in `StatementParser`).
    *
    * The THIRD banned context -- a correlation fold -- is deliberately NOT checked here. Task 5
    * already banned `terminate` there (`validateCorrelation`'s `walkStatements` over
    * `correlation.handlers`), and that check has been extended to catch `initiate` too rather than
    * duplicated as a second predicate in this function. Two sites reporting the SAME defect would
    * double-report a `terminate` (or `initiate`) written inside a fold; keeping the fold rule in
    * exactly one place is what keeps that from happening. A fold's `parents.head` here is the
    * `OnEventClause`/`OnMessageClause` inside the correlation's handler -- neither an
    * `OnActivationClause`/`OnPassivationClause` nor (absent an enclosing Function) a `Function` --
    * so the two predicates below never fire for it regardless.
    *
    * Called from [[checkStatementScopes]], the single entry point invoked at every container root
    * AND recursively for when/match/foreach bodies -- so a banned statement nested at any depth is
    * still reached, mirroring [[checkTerminate]]'s and `checkTellAddressing`'s reachability. It is
    * NOT called from `validateStatement`: that dispatch never sees statements held in a FIELD
    * (`WhenStatement.thenStatements`, `MatchCase.statements`, `ForeachStatement.doStatements`), the
    * same gap `checkStateReadScope`'s placement there is a known, filed defect for (see BACKLOG.md).
    */
  private def checkInstanceEffectScope(statement: Statement, parents: Parents): Unit =
    val offenders: Seq[(At, String)] =
      (statement match
        case ts: TerminateStatement => Seq(ts.loc -> "terminate")
        case _                      => Seq.empty
      ) ++ statementValues(statement).flatMap(initiatesIn).map(init => init.loc -> "initiate")

    if offenders.nonEmpty then
      val banned: Option[String] = parents.head match
        case _: OnActivationClause | _: OnPassivationClause =>
          Some("an activation or passivation clause, which must be side-effect-free")
        case _ =>
          if parents.exists(_.isInstanceOf[Function]) then
            Some("a function body, which is pure and may not create or destroy instances")
          else None

      banned.foreach { where =>
        offenders.foreach { case (loc, kw) =>
          messages.addError(
            loc,
            s"'$kw' is not allowed in $where",
            suggestion = s"Move the '$kw' into an ordinary handler clause."
          )
        }
      }
  end checkInstanceEffectScope

  /** The innermost enclosing [[Processor]], the same "instance" `self` names -- `None` when no
    * Processor encloses the reference, OR when the nearest enclosing scope is one of the two kinds
    * that deliberately do NOT carry the instance identity of whatever Processor happens to
    * lexically contain them. Both are TERMINATING cases, not merely absent from the match --
    * mirroring `enclosingWriteScope`'s exact pattern, so `collectFirst` stops at the boundary
    * instead of walking past it to an outer Processor:
    *
    *   - `Function`: A25's `call function F(...)` carries no processor operand, so a pure function
    *     has no bound instance even when it is lexically nested inside a Context/Entity (A24
    *     functions commonly are, for organization).
    *   - `Saga`: a Saga is a `VitalDefinition`, not a `Processor` -- it has no instance identity of
    *     its own -- and the CM calls a saga step "a phase of a saga execution instance" rather than
    *     an instance in its own right. This one is NOT merely a formality: the grammar DOES nest a
    *     Saga inside a Context (`context_definition` includes `saga`, and `Saga` is in
    *     `OccursInContext`), so without this case a saga step's `self` would silently resolve to
    *     the enclosing Context's identity instead of being rejected.
    */
  private def enclosingProcessorOf(parents: Parents): Option[Processor[?]] =
    parents
      .collectFirst {
        case p: Processor[?] => Some(p)
        case _: Function      => None
        case _: Saga          => None
      }
      .flatten

  /** The fully-qualified [[PathIdentifier]] naming `p`, in the natural root-to-leaf written order
    * (`Dom.Ctx.Order`). [[SymbolsOutput.pathOf]] returns the SAME chain leaf-to-root (it is a
    * symbol-table lookup key, not a path to render), so it is reversed here. No prior caller
    * needed to build a path FROM a definition -- every other [[PathIdentifier]] in the codebase is
    * either parsed from source or split from a dotted string -- so this is written fresh for
    * [[SelfValue.aggregation]]'s synthesized `Id(...)` field.
    */
  private def pathOf(p: Processor[?]): PathIdentifier =
    PathIdentifier(At.empty, symbols.pathOf(p).reverse)

  private def validateStatement(
    statement: Statement,
    parents: Parents
  ): Unit =
    val onClause: Branch[?] = parents.head
    // Scope rules that apply to EVERY statement kind, checked before the per-kind match so a new
    // statement carrying a value cannot skip them.
    checkStateReadScope(statement, parents)
    statement match
      case PromptStatement(loc, what) =>
        checkNonEmptyValue(
          what,
          "prompt statement",
          onClause,
          loc,
          MissingWarning,
          required = true
        )
      case ErrorStatement(loc, message) =>
        checkNonEmptyValue(
          message,
          "error description",
          onClause,
          loc,
          MissingWarning,
          required = true
        )
      case ss @ SetStatement(loc, field, value) =>
        checkSetScope(ss, parents)
        field match
          case fr: FieldRef => checkRef[Field](fr, parents)
          case sr: StateRef => checkRef[State](sr, parents)
        // Only a LiteralString can answer "are you empty?" meaningfully: `isEmpty` means NO
        // CONTENTS and every other Value kind is a non-container, so it always says yes. Asking
        // unguarded reported `set field S.flag to true` as an empty value. Non-literal values get
        // their real validation (resolution + type check) in `checkStatementScopes`.
        value match
          case ls: LiteralString =>
            checkNonEmptyValue(ls, "value to set", onClause, loc, MissingWarning, required = true)
          case _ => ()
      case SendStatement(_, msg, portlet) =>
        // A54: a bare ref is checked here; a Constructor is validated in checkStatementScopes (needs
        // the threaded `let` scope for its args).
        msg match
          case ref: MessageRef => checkRef[Type](ref, parents)
          case _: Constructor  => ()
          case vr: ValueRef    => checkBoundMessageOperand(vr, "send") // A56
        checkRef[Portlet](portlet, parents)
      case MorphStatement(_, entity, state, value) =>
        checkRef[Entity](entity, parents)
        checkRef[State](state, parents)
        value match
          case ref: RecordRef => checkRef[Type](ref, parents)
          case _: Constructor => ()
      case BecomeStatement(_, entityRef, handlerRef) =>
        checkRef[Entity](entityRef, parents).foreach { entity =>
          checkCrossContextReference(entityRef.pathId, entity, onClause, parents)
        }
        checkRef[Handler](handlerRef, parents).foreach { handler =>
          checkCrossContextReference(handlerRef.pathId, handler, onClause, parents)
        }
      case ts @ TellStatement(_, msg, processorRef, _) =>
        val maybeProc = checkRef[Processor[?]](processorRef, parents)
        maybeProc.foreach { entity =>
          checkCrossContextReference(processorRef.pathId, entity, onClause, parents)
          collectedTells.addOne((ts, entity))
        }
        // A54: a bare ref is checked here; a Constructor is validated in checkStatementScopes.
        msg match
          case ref: MessageRef =>
            val maybeType = checkRef[Type](ref, parents)
            maybeType.foreach { typ =>
              checkCrossContextReference(ref.pathId, typ, onClause, parents)
            }
          case _: Constructor => ()
          case vr: ValueRef   => checkBoundMessageOperand(vr, "tell") // A56
      case WhenStatement(loc, condition, thenStatements, elseStatements, _) =>
        condition match {
          case ls: LiteralString =>
            checkNonEmptyValue(ls, "condition", onClause, loc, MissingWarning, required = true)
          case id: Identifier =>
            checkNonEmptyValue(id, "condition", onClause, loc, MissingWarning, required = true)
          case _: ValueRef          => () // A17: resolved + boolean-checked in checkStatementScopes
          case _: BooleanExpression => () // A28: type-checked in checkStatementScopes
          case pv: PromptValue      =>
            // An AI-evaluated condition: nothing to type-check, but an empty prompt says nothing
            // for an AI to act on, so it gets the same emptiness check the bare string had.
            checkNonEmptyValue(
              pv.prompt,
              "condition",
              onClause,
              loc,
              MissingWarning,
              required = true
            )
        }
        checkNonEmpty(
          thenStatements.toSeq,
          "statements",
          onClause,
          loc,
          MissingWarning,
          required = true
        )
      // elseStatements is optional, so no check needed
      case MatchStatement(loc, expression, cases, default) =>
        // A29: only the legacy LiteralString subject/pattern get a structural non-empty check here;
        // structured subjects/patterns are resolved + type-checked in checkStatementScopes.
        expression match
          case ls: LiteralString =>
            checkNonEmptyValue(ls, "expression", onClause, loc, MissingWarning, required = true)
          case _ => ()
        checkNonEmpty(cases, "cases", onClause, loc, MissingWarning, required = true)
        cases.foreach { mc =>
          mc.pattern match
            case lp: LiteralPattern =>
              checkNonEmptyValue(
                lp.literal,
                "case pattern",
                onClause,
                mc.loc,
                MissingWarning,
                required = true
              )
            case _ => ()
        }
      case LetStatement(loc, identifier, _, expression) =>
        check(
          identifier.value.length >= 3,
          s"Identifier '${identifier.value}' is too short",
          MissingWarning,
          identifier.loc,
          suggestion = "Use an identifier of at least 3 characters in the 'let' statement."
        )
        // See the `set` case above: only a LiteralString's emptiness is a real question. Asking
        // unguarded reported every `let x = call/record/ref/true` in the language as empty.
        expression match
          case ls: LiteralString =>
            checkNonEmptyValue(ls, "expression", onClause, loc, MissingWarning, required = true)
          case _ => ()
      case CodeStatement(loc, language, body) =>
        checkNonEmptyValue(language, "language", onClause, loc, MissingWarning, required = true)
        check(
          body.nonEmpty,
          "Code statement body cannot be empty",
          MissingWarning,
          loc,
          suggestion = "Provide a non-empty code body, or remove the empty code statement."
        )
      case RequireStatement(loc, condition, argument) =>
        condition match {
          case ls: LiteralString =>
            checkNonEmptyValue(
              ls,
              "require condition",
              onClause,
              loc,
              MissingWarning,
              required = true
            )
          case ir: InvariantRef =>
            checkRef[Invariant](ir, parents).foreach { inv =>
              checkRequireArgument(inv, argument, loc)
            }
          case _: BooleanExpression => () // A28: type-checked in checkStatementScopes
        }
      case YieldStatement(_, msg) =>
        // A54: a bare ref is checked here; a Constructor is validated in checkStatementScopes.
        msg match
          case ref: MessageRef => checkRef[Type](ref, parents)
          case _: Constructor  => ()
        checkResponsePairing(msg, Keyword.yield_, AggregateUseCase.EventCase)
      case ReplyStatement(_, msg) =>
        // Mirrors YieldStatement: a bare ref is checked here, a Constructor in
        // checkStatementScopes. The pairing check is what keeps the two spellings honest.
        msg match
          case ref: MessageRef => checkRef[Type](ref, parents)
          case _: Constructor  => ()
        checkResponsePairing(msg, Keyword.reply, AggregateUseCase.ResultCase)
      case _: PutStatement | _: ReturnStatement | _: TerminateStatement =>
        // A45/A57/A70: value/type/scope validation runs in checkStatementScopes (which threads
        // in-scope `let` locals and reaches nested statements). Nothing to check per-statement
        // here. `TerminateStatement`'s arity/type check against `on term` is `checkTerminate`,
        // called from checkStatementScopes -- see its case there.
        ()
      case ForeachStatement(loc, element, _, _, doStatements) =>
        // Collection scoping/collection-type checks run in checkStatementScopes (which threads
        // in-scope `let` locals). Here we only enforce the local structural checks.
        check(
          element.value.length >= 1,
          "'foreach' element identifier must not be empty",
          MissingWarning,
          element.loc,
          suggestion = "Name the loop element, e.g. 'foreach item in ...'."
        )
        checkNonEmpty(
          doStatements.toSeq,
          "statements",
          onClause,
          loc,
          MissingWarning,
          required = true
        )
    end match
  end validateStatement

  /** A19↔A22 conformance: a `yield`/`reply` statement is the runtime side of a command/query's
    * declarative `yields` clause. Enforce that the two agree:
    *   - a command/query that declares `yields M` must be handled by a clause that yields `M` (same
    *     kind + same resolved Type);
    *   - a yield whose message does not match the declared `yields` is an error;
    *   - a command/query that declares `yields` but whose handler never yields it is an error,
    *     UNLESS that clause refuses the message (`error`/`require`), which discharges the contract
    *     by declining rather than by recording.
    *
    * `yields` is optional (A19): yielding in a handler whose command/query declares no `yields` is
    * allowed and unchecked — conformance is enforced only when the author opts in with a `yields`
    * clause.
    *
    * Skips cleanly when refs don't resolve (those are reported by other checks) and when the
    * handled message is not a command/query (no `yields` contract applies).
    */
  private def checkYieldConformance(omc: OnMessageLikeClause, parents: Parents): Unit = {
    if omc.msg.isEmpty then return
    // Enforce the contract only where a `yield` can actually be WRITTEN. `StatementParser` grants
    // `yieldStatement` to ProcessorKind Entity/Context/Repository and nothing else (`case _ =>
    // base`), so demanding one from a streamlet clause asks for a statement the parser rejects --
    // and `on other` is no escape, because A36 then reports an epic step routed through that
    // streamlet as unwitnessed. There was no satisfiable spelling. These two lists MUST agree: if
    // `yield` is ever granted to another ProcessorKind, add it here too.
    //
    // It is also right on the merits. A streamlet forwarding a command is not the thing that
    // records the event; the entity that owns the state is, and it is still held to the contract.
    //
    // The nearest enclosing Processor, not `parents.head` -- a Handler may sit inside a State
    // inside an Entity.
    val enclosing = parents.collectFirst { case p: Processor[?] => p }
    val canYield = enclosing.exists {
      case _: Entity | _: Context | _: Repository => true
      case _                                      => false
    }
    if !canYield then return
    resolution.refMap.definitionOf[Type](omc.msg.pathId).foreach { handledType =>
      handledType.typEx match {
        case auc: AggregateUseCaseTypeExpression
            if auc.usecase == AggregateUseCase.CommandCase ||
              auc.usecase == AggregateUseCase.QueryCase =>
          val finder = Finder(omc.contents)
          // TWO pairings, parameterised rather than duplicated: a command declares `yields event`
          // and settles with `yield`; a query declares `replies result` and settles with `reply`.
          // Split at 2.0 when `reply` stopped being a deprecated synonym for `yield`.
          val isQuery = auc.usecase == AggregateUseCase.QueryCase
          val declKeyword = if isQuery then Keyword.replies else Keyword.yields
          val stmtKeyword = if isQuery then Keyword.reply else Keyword.yield_
          val verb = if isQuery then "reply" else "yield"
          val responseStmts: Seq[Statement] =
            if isQuery then finder.recursiveFindByType[ReplyStatement]
            else finder.recursiveFindByType[YieldStatement]
          // A clause that REFUSES discharges the contract by declining. `yields` declares what the
          // command records WHEN IT SUCCEEDS, not that every clause mentioning it must record one.
          // Without this, the ordinary event-sourcing shape -- a command accepted in one state and
          // refused in the others -- is unexpressible: each refusing clause would have to yield the
          // very event it just declined to produce. `require` refuses as surely as `error`, so
          // both count.
          //
          // The obligation must be settled on EVERY path, not merely somewhere in the clause: a
          // refusal buried in one branch of a `when` used to exempt the whole clause while the
          // other branch produced nothing. See `dischargesOnEveryPath`.
          // EMITTING ANY MESSAGE settles a path, not just yielding the declared one or refusing
          // with error/require. An event-sourced entity often declines by RECORDING the refusal:
          //
          //   on command RedeemPoints is {           // declares `yields event PointsRedeemed`
          //     when prompt("balance >= points") then
          //       yield event PointsRedeemed
          //     else
          //       send event RedeemPointsRejected to outlet ...
          //     end }
          //
          // (riddl-models reactive-bbq LoyaltyAccount.riddl:579). That `else` has decided and
          // recorded its decision; it has not fallen through. Which message is the RIGHT one is a
          // modelling judgment validation cannot make -- the `yields` type conformance loop below
          // still checks every `yield`. What this predicate exists to catch is a path that does
          // nothing at all, and `set`/`do`/an empty branch still fail it.
          val settled = dischargesOnEveryPath(omc.contents) {
            case _: ErrorStatement | _: RequireStatement => true
            // BOTH response statements settle a path. Counting only the pairing-correct one would
            // report the same mistake twice -- `checkResponsePairing` already names a `yield` in a
            // query clause, and a second "does not reply on every path" adds nothing.
            case _: YieldStatement | _: ReplyStatement                   => true
            case _: SendStatement | _: TellStatement                     => true
            case _                                       => false
          }
          auc.yields match {
            case Some(declaredYield) =>
              val declaredType = resolution.refMap.definitionOf[Type](declaredYield.pathId)
              if !settled then
                messages.addError(
                  omc.errorLoc,
                  s"${handledType.identify} declares '$declKeyword ${declaredYield.format}' " +
                    s"but ${omc.identify} does not $verb it on every path",
                  suggestion =
                    s"Use '$stmtKeyword ${declaredYield.format}' (or refuse with " +
                      "'error'/'require') on every path through this handler. A 'when' with no " +
                      "'else', a 'match' with no 'default', and a 'foreach' all leave a path " +
                      "that does neither."
                )
              end if
              // Independent of `settled`: a clause may discharge by refusing on every path and
              // STILL yield the wrong thing somewhere, which is its own error.
              responseStmts.foreach { ys =>
                  val operand = ys match
                    case y: YieldStatement => y.msg
                    case r: ReplyStatement => r.msg
                    case _                 => MessageRef.empty
                  val kindOk = operandMessageKind(operand).contains(declaredYield.messageKind)
                  val yieldedType = resolution.refMap.definitionOf[Type](operandPathId(operand))
                  val typeOk = (declaredType, yieldedType) match {
                    case (Some(dt), Some(yt)) => dt eq yt
                    case _                    => true // unresolved — reported by other checks
                  }
                  if !(kindOk && typeOk) then
                    messages.addError(
                      ys.loc,
                      s"'${operand.format}' does not match declared '$declKeyword " +
                        s"${declaredYield.format}' of ${handledType.identify}",
                      suggestion =
                        s"Use the declared response: '$stmtKeyword ${declaredYield.format}'."
                    )
                }
            // `yields`/`replies` are OPTIONAL, so producing without a declared clause is allowed.
            // Phase B changes this only for `ask`: asking a query that declares no `replies` is an
            // error at the ASK site, since there is no type for the answer to have.
            case None => ()
          }
        case _ => () // not a command/query, or unresolved — no 'yields' contract to enforce
      }
    }
  }

  private def validateTerm(
    t: Term
  ): Unit = {
    checkIdentifierLength(t)
  }

  private def validateEnumerator(
    e: Enumerator,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, e)
  }

  private def validateField(
    f: Field,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, f)
    if f.id.value.matches("^[^a-z].*") then {
      messages.add(
        Message(
          f.id.loc,
          "Field names should begin with a lower case letter",
          StyleWarning,
          suggestion =
            s"Start the field name with a lower-case letter, e.g. '${f.id.value.take(1).toLowerCase + f.id.value.drop(1)}'."
        )
      )
    }
    checkTypeExpression(f.typeEx, f, parents)
  }
  private def validateMethod(
    m: Method,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, m)
    if m.id.value.matches("^[^a-z].*") then
      messages.add(
        Message(
          m.id.loc,
          "Method names should begin with a lower case letter",
          StyleWarning,
          suggestion =
            s"Start the method name with a lower-case letter, e.g. '${m.id.value.take(1).toLowerCase + m.id.value.drop(1)}'."
        )
      )
    checkTypeExpression(m.typeEx, m, parents)
    for arg <- m.args do
      checkTypeExpression(arg.typeEx, m, parents)
      if arg.name.matches("^[^a-z].*") then
        messages.add(
          Messages.style(
            "Method argument names should begin with a lower case letter",
            arg.loc, // Fixed: use argument location, not method identifier location
            suggestion =
              s"Start the argument name with a lower-case letter, e.g. '${arg.name.take(1).toLowerCase + arg.name.drop(1)}'."
          )
        )
    checkMetadata(m)
  }

  /** A53: exactly one `version` per scope. A second one is a hard Error, reported at the offending
    * (second) declaration so the fix is obvious.
    */
  private def checkSingleVersion(wv: WithVersion[?] & Definition): Unit = {
    val versions = wv.versions
    if versions.sizeIs > 1 then {
      messages.addError(
        versions(1).loc,
        s"${wv.identify} declares ${versions.size} versions; a scope may declare at most one",
        suggestion =
          s"Remove the extra 'version' declarations from ${wv.identify} so exactly one remains."
      )
    }
  }

  /** A47: exactly one `copyright` per scope. A second one is a hard Error, reported at the
    * offending (second) declaration so the fix is obvious.
    */
  private def checkSingleCopyright(wc: WithCopyright[?] & Definition): Unit = {
    val copyrights = wc.copyrights
    if copyrights.sizeIs > 1 then {
      messages.addError(
        copyrights(1).loc,
        s"${wc.identify} declares ${copyrights.size} copyrights; a scope may declare at most one",
        suggestion =
          s"Remove the extra 'copyright' declarations from ${wc.identify} so exactly one remains."
      )
    }
  }

  /** The `with <expr>` argument must be present exactly when the invariant declares a TYPE it
    * cannot get from ambient scope, and absent otherwise.
    *
    * Getting this wrong in either direction is silent otherwise: a missing argument leaves the
    * predicate with nothing to read, and a superfluous one reads as if it were being checked when
    * the invariant never looks at it.
    */
  private def checkRequireArgument(inv: Invariant, argument: Option[Value], loc: At): Unit =
    inv.requires match
      case Some(tr: TypeRef) if argument.isEmpty =>
        messages.addError(
          loc,
          s"${inv.identify} declares 'requires ${tr.format}', so it must be given a value here",
          suggestion = s"Write 'require invariant ${inv.id.value} with <expr>'."
        )
      case Some(_: TypeRef) => () // present, as required
      case _ if argument.nonEmpty =>
        messages.addWarning(
          loc,
          s"${inv.identify} declares no 'requires <type>', so the 'with' value is ignored",
          suggestion = "Remove the 'with' clause, or give the invariant a 'requires <type>'."
        )
      case _ => ()
    end match
  end checkRequireArgument

  private def validateInvariant(
    i: Invariant,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, i)
    checkNonEmpty(i.condition.toList, "Condition", i, Messages.MissingWarning)
    // A28: type-check a structured BooleanExpression condition. A block form DOES have a `let`
    // scope -- that is most of why it exists -- so its statements are threaded, unlike the bare
    // expression form which has none.
    i.condition.foreach {
      case be: BooleanExpression => validateValue(be, parents, Seq.empty[LetStatement])
      case _: LiteralString      => ()
      case blk: InvariantBlock =>
        val lets = blk.statements.toSeq.collect { case l: LetStatement => l }
        validateValue(blk.predicate, parents, lets)
    }
    i.requires.foreach {
      case sr: StateRef => checkRef[State](sr, parents)
      case tr: TypeRef  => checkRef[Type](tr, parents)
    }
    checkInvariantScope(i, parents)
    checkMetadata(i)
  }

  /** The scope rules of §15.2: where an invariant may be declared, and what it may read there.
    *
    * A stateless processor has no ambient data, so an implicit invariant on one could never read
    * anything -- it must declare a type and be handed the value. Saying so is the point: an
    * invariant that can never run is exactly the inert-constraint defect this work removes, and
    * this repo's recurring failure mode is checks that fail by being UNGATED rather than untested.
    */
  private def checkInvariantScope(i: Invariant, parents: Parents): Unit =
    val enclosingEntity = parents.collectFirst { case e: Entity => e }
    val inState = parents.exists(_.isInstanceOf[State])

    // Reid's ruling, 2026-08-11: overloading an invariant name is a WARNING, and the INNERMOST
    // declaration takes precedence. Under §15.2's implicit application an entity-level invariant
    // already applies inside every state, so a state-level one of the same name is a deliberate
    // narrowing often enough to be legal -- but silently shadowing a CHECK is the failure mode
    // this whole area exists to remove, so it must be said out loud.
    //
    // `Entity.invariants` descends only the provenance wrappers, never a State, so this compares
    // entity-level declarations against a state-level one and cannot match `i` against itself.
    if inState then
      enclosingEntity.foreach { entity =>
        entity.invariants.find(_.id.value == i.id.value).foreach { outer =>
          messages.addWarning(
            i.errorLoc,
            s"${i.identify} shadows ${outer.identify} declared on ${entity.identify}; the " +
              s"innermost declaration takes precedence inside this state",
            suggestion = s"Rename one of them if both were meant to apply, or drop the outer one " +
              s"if the state-level '${i.id.value}' is the only rule you want here."
          )
        }
      }
    end if
    i.requires match
      case Some(_: StateRef) if enclosingEntity.isEmpty =>
        messages.addError(
          i.errorLoc,
          s"${i.identify} declares 'requires state', which is only meaningful inside an Entity",
          suggestion = "Move it into an Entity, or declare 'requires <type>' and apply it with " +
            "'require invariant ... with <expr>'."
        )
      case Some(_: StateRef) if inState =>
        messages.addWarning(
          i.errorLoc,
          s"${i.identify} is already scoped to its enclosing State; 'requires state' is redundant",
          suggestion = "Drop the 'requires state' clause."
        )
      case None if enclosingEntity.isEmpty && !inState =>
        // Declared on a Context/Adaptor/Projector/Streamlet/Repository/Module: no state to read,
        // so it can never be applied implicitly and nothing can invoke it either.
        //
        // A USAGE WARNING rather than an Error, deliberately: this is the same defect as the
        // never-applied `requires <type>` case in postProcess (#23) — an inert invariant — and
        // that one is a warning by ruling. Two severities for one defect would be arbitrary, and
        // an Error here would reject existing models whose invariants were already inert under
        // the old require-only rule (`language/input/module/mixed-module.riddl:14`).
        messages.addUsage(
          i.errorLoc,
          s"${i.identify} is declared where there is no state to read, so it cannot be applied " +
            "implicitly and will never be checked",
          suggestion = "Give it 'requires <type>' and apply it with " +
            s"'require invariant ${i.id.value} with <expr>', or move it into an Entity or State."
        )
      case _ => ()
    end match
  end checkInvariantScope

  private def validateInlet(
    inlet: Inlet,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, inlet)
    checkRef[Type](inlet.type_, parents)
    addInlet(inlet)
  }

  private def validateOutlet(
    outlet: Outlet,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, outlet)
    checkRef[Type](outlet.type_, parents)
    addOutlet(outlet)
  }

  /** Within a group the connector intention keywords are mutually exclusive.
    *
    * Reported rather than made a parse error, for the same reason as the entity twin: the model
    * still builds and the author sees BOTH keywords named.
    */
  private def checkConnectorIntentions(connector: Connector): Unit =
    connector.intentions.groupBy(_.group).foreach { case (group, chosen) =>
      if chosen.sizeIs > 1 then
        val names = chosen.map(i => s"'${i.keyword}'").mkString(" and ")
        messages.addError(
          connector.errorLoc,
          s"${connector.identify} declares $names, but $group intentions are mutually exclusive",
          suggestion = s"Keep exactly one $group keyword before 'connector'."
        )
    }
  end checkConnectorIntentions

  private def validateConnector(
    connector: Connector,
    parents: Parents
  ): Unit =
    if connector.nonEmpty then
      addConnector(connector)
      checkConnectorIntentions(connector)
      val maybeOutlet = checkRef[Outlet](connector.from, parents)
      val maybeInlet = checkRef[Inlet](connector.to, parents)

      (maybeOutlet, maybeInlet) match
        case (Some(outlet: Outlet), Some(inlet: Inlet)) =>
          val outletParents: Parents = this.symbols.parentsOf(outlet)
          val outType = resolvePath[Type](outlet.type_.pathId, outletParents)
          val inletParents: Parents = this.symbols.parentsOf(inlet)
          val inType = resolvePath[Type](inlet.type_.pathId, inletParents)
          (outType, inType) match
            case (Some(outletType), Some(inletType)) =>
              // A port typed `Anything` (the dual of `Nothing`) absorbs — or supplies — any
              // message, so it is compatible with every other type. This is what lets the
              // predefined `BottomlessPit`/`ForeverEmpty` terminators, whose ports are typed
              // `Drain is Anything`, terminate a pipeline of ANY message type.
              def isUniversal(t: Type): Boolean = t.typEx.isInstanceOf[Anything]
              if !areSameType(Some(inletType), Some(outletType)) &&
                !isUniversal(inletType) && !isUniversal(outletType)
              then
                messages.addError(
                  inlet.loc,
                  s"Type mismatch in ${connector.identify}: ${inlet.identify} " +
                    s"requires ${inlet.type_.identify} and ${outlet.identify} requires ${outlet.type_.identify} " +
                    s"which are not the same types",
                  suggestion =
                    "Make the inlet and outlet use the same type, or insert a Flow streamlet to transform between them."
                )
              end if
            case _ =>
              if outType.isEmpty then
                messages.addError(
                  outlet.loc,
                  s"Unresolved PathId, ${outlet.type_.pathId.format}, in ${outlet.identify}",
                  suggestion =
                    s"Define the type '${outlet.type_.pathId.value.mkString(".")}', or correct the outlet's type reference."
                )
              end if
              if inType.isEmpty then
                messages.addError(
                  inlet.loc,
                  s"Unresolved PathId, ${inlet.type_.pathId.format}, in ${inlet.identify}",
                  suggestion =
                    s"Define the type '${inlet.type_.pathId.value.mkString(".")}', or correct the inlet's type reference."
                )
              end if
          end match
        case _ => // one of the two didn't resolve, already handled above.
      end match
    end if
  end validateConnector

  private def validateAuthor(
    ai: Author,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, ai)
    checkNonEmptyValue(ai.name, "name", ai, required = true)
    checkNonEmptyValue(ai.email, "email", ai, required = true)
    checkMetadata(ai)
  }

  private def validateType(
    t: Type,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, t)
    val typeName = t.id.value
    check(
      typeName.head.isUpper,
      s"${t.identify} should start with a capital letter",
      StyleWarning,
      t.id.loc,
      suggestion = s"Capitalize the type name, e.g. '${typeName.capitalize}'."
    )
    // Check if the type name exactly matches a predefined type name
    check(
      !PredefType.allPredefTypes.contains(typeName),
      s"${t.identify} redefines built-in type '$typeName'",
      Error,
      t.id.loc,
      suggestion = s"Rename the type to something other than the built-in '$typeName'."
    )
    // Check if the type name is a case-variant of a predefined type
    if !PredefType.allPredefTypes.contains(typeName) then
      val caseMatch =
        PredefType.allPredefTypes.find(pt => pt.equalsIgnoreCase(typeName) && pt != typeName)
      caseMatch.foreach { predef =>
        check(
          false,
          s"${t.identify} is a redundant case-variant of " +
            s"built-in type '$predef'",
          StyleWarning,
          t.id.loc,
          suggestion =
            s"Rename the type so it is not a case-variant of built-in '$predef', or use the built-in '$predef' directly."
        )
      }
    end if
    if !t.typEx.isInstanceOf[AggregateTypeExpression] then {
      checkTypeExpression(t.typEx, t, parents)
    }
    // A19: message types (AUCTE) skip checkTypeExpression above, so validate a `yields` clause here.
    t.typEx match {
      case auc: AggregateUseCaseTypeExpression => checkUseCaseYields(auc, parents)
      case _                                   => ()
    }
  }

  private def validateConstant(
    c: Constant,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, c)
    checkMetadata(c)
  }

  private def validateState(
    s: State,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, s)
    checkMetadata(s)
    // At most one handler in a state may be the initial (live-after-morph) one.
    val initialHandlers = s.handlers.filter(_.isInitial)
    if initialHandlers.sizeIs > 1 then
      messages.addError(
        initialHandlers(1).loc,
        s"${s.identify} marks ${initialHandlers.size} handlers 'initial'; a state has exactly one " +
          s"initial (live) handler",
        suggestion =
          "Mark only one handler in this state 'initial' (or none, to default to the first)."
      )
    checkRefAndExamine[Type](s.typ, parents) { (typ: Type) =>
      typ.typEx match {
        case agg: AggregateTypeExpression =>
          if agg.fields.isEmpty && !s.isEmpty then {
            messages.addError(
              s.typ.loc,
              s"${s.identify} references an empty aggregate but must have " +
                s"at least one field",
              suggestion =
                s"Add at least one field to the aggregate type used by ${s.identify}, e.g. 'field someName: Type'."
            )
          }
        case _ =>
      }
      check(
        typ.id.value != s.id.value,
        s"${s.identify} and ${typ.identify} must not have the same name so path resolution can succeed",
        Messages.Error,
        s.loc,
        suggestion =
          s"Rename either the state or the type so they do not share the name '${s.id.value}'."
      )
    }
  }

  private def validateFunction(
    f: Function,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, f)
    val parent: Branch[?] = parents.headOption.getOrElse(Root.empty)
    check(
      f.contents.filter[Statement].nonEmpty,
      s"${f.identify} in ${parent.identify} should have statements",
      MissingWarning,
      f.errorLoc,
      suggestion =
        s"Add statements to the body of ${f.identify} (use '???' as a placeholder if needed)."
    )
    f.input.foreach(validateRequiresReturns(_, f, parents))
    f.output.foreach(validateRequiresReturns(_, f, parents))
  }

  // A9: `requires`/`returns` reference any named Type (resolved by checkTypeRef — no aggregate
  // restriction), or carry a deprecated inline Aggregation (still validated, plus a Deprecation
  // warning nudging migration to a named type).
  private def validateRequiresReturns(
    value: TypeRef | Aggregation,
    definition: Definition,
    parents: Parents
  ): Unit = value match {
    case tr: TypeRef => checkTypeRef(tr, parents)
    case agg: Aggregation =>
      checkTypeExpression(agg, definition, parents)
      messages.addDeprecation(
        agg.loc,
        s"Inline aggregation on 'requires'/'returns' of ${definition.identify} is deprecated",
        suggestion = "Define a named type (e.g. 'record Args is { ... }') and reference it instead."
      )
  }

  private def validateHandler(
    h: Handler,
    parents: Parents
  ): Unit = {
    checkContainer(parents, h)
    // OnMessageLikeClause covers both OnMessageClause (command/query/result/record) and
    // OnEventClause (event); the kind checks below stay precise via `msg.messageKind`.
    val messageClauses = h.clauses.collect { case omc: OnMessageLikeClause => omc }
    // A21: within a SINGLE handler, warn when two 'on <message>' clauses handle the
    // same message — the later clause shadows the earlier one, which is unreachable.
    // Key by the resolved message type when it resolves, else by messageKind + pathId text.
    // groupBy preserves encounter order within each group, so `dups.tail` are the later clauses.
    messageClauses
      .groupBy { omc =>
        resolution.refMap
          .definitionOf[Type](omc.msg.pathId)
          .map(t => s"resolved:${t.id.value}#${t.loc.offset}")
          .getOrElse(s"${omc.msg.messageKind}:${omc.msg.pathId.format}")
      }
      .foreach { case (_, dups) =>
        if dups.size > 1 then
          dups.tail.foreach { later =>
            messages.addStyle(
              later.loc,
              s"on-clause for '${later.msg.format}' shadows an earlier clause in this handler; " +
                s"the earlier one is unreachable",
              suggestion =
                s"Remove the redundant 'on ${later.msg.format}' clause or merge its statements into the earlier one."
            )
          }
      }
    parents.headOption match {
      case Some(entity: Entity) =>
        if messageClauses.nonEmpty then {
          val handlesCommandOrQuery = messageClauses.exists { omc =>
            omc.msg.messageKind == AggregateUseCase.CommandCase ||
            omc.msg.messageKind == AggregateUseCase.QueryCase
          }
          if !handlesCommandOrQuery then
            messages.addWarning(
              h.errorLoc,
              s"${h.identify} in ${entity.identify} handles no commands or queries; entity handlers typically handle commands and queries",
              suggestion = s"Add 'on command ...' or 'on query ...' clauses to ${h.identify}."
            )
        }
      case Some(repo: Repository) =>
        if messageClauses.nonEmpty then {
          val handlesEvents = messageClauses.exists { omc =>
            omc.msg.messageKind == AggregateUseCase.EventCase
          }
          if handlesEvents then
            messages.addWarning(
              h.errorLoc,
              s"${h.identify} in ${repo.identify} handles events; repositories typically handle commands and queries, not events",
              suggestion =
                "Move event handling to a projector; have the repository handle commands (writes) and queries (reads) instead."
            )
        }
      // NOTE: A projector handling commands or queries is now rejected at PARSE time
      // (projectorHandler is event-only), so the former command/query warning here is dead
      // code and has been removed — the parse error supersedes it.
      case _ => ()
    }
  }

  /** Validate an `Include` node directly.
    *
    * NOTE: This is currently UNREACHED. `ValidationPass` runs with `withIncludes = false`, so
    * `Pass.traverse` never dispatches `Include` nodes to `process` (the `case include: Include[?]`
    * branch above therefore never fires). Include hygiene is instead validated from each
    * container's `checkContents` via `checkIncludeHygiene` (see `DefinitionValidation`), which
    * walks the container's direct `includes` rather than relying on the Include node being
    * processed. This method is retained as the future hook for a `withIncludes = true` validation
    * path; if that path is ever enabled, reconcile these checks with `checkIncludeHygiene` to avoid
    * duplicate messages.
    */
  private def validateInclude[T <: RiddlValue](i: Include[T]): Unit = {
    check(
      i.contents.nonEmpty,
      "Include has no included content",
      Messages.Error,
      i.loc,
      suggestion =
        "Ensure the included file exists and contains valid RIDDL content for this scope."
    )
    check(
      i.origin.nonEmpty,
      "Include has no source provided",
      Messages.Error,
      i.loc,
      suggestion = "Provide a file path to include, e.g. 'include \"entities.riddl\"'."
    )
  }

  // NOTE: avoid "import '" in string literals — ESM shim plugins
  // misinterpret it as an ES module import statement.
  private def validateBASTImport(bi: BASTImport, parents: Parents): Unit = {
    check(
      bi.path.s.nonEmpty,
      "BAST load has no path specified",
      Messages.Error,
      bi.loc,
      suggestion = "Provide a .bast file path to import, e.g. 'import \"model.bast\"'."
    )
    check(
      bi.path.s.endsWith(".bast"),
      s"BAST load path '${bi.path.s}' should end with .bast",
      Messages.Warning,
      bi.loc,
      suggestion = "Give the imported file a '.bast' extension."
    )
    checkImportedDefinitionsMakeSenseHere(bi, parents)
  }

  /** Sense-at-location: a definition plucked out of a `.bast` file must be structurally legal WHERE
    * THE DIRECTIVE SITS — exactly as if it had been written there by hand. Importing an Entity into
    * a Domain, or a Context into the Root, produces a tree the parser would have rejected, and
    * flattening it would make that tree permanent.
    *
    * The placement rule is not restated here: `AST.mayOccurDirectlyIn` answers it from the very
    * contents unions that define each container, so this check widens automatically whenever a
    * container does.
    *
    * A directive whose contents are empty is skipped — it either failed to load or was never
    * loaded, and the load failure is reported elsewhere.
    */
  private def checkImportedDefinitionsMakeSenseHere(bi: BASTImport, parents: Parents): Unit = {
    if bi.contents.nonEmpty then
      parents.headOption.foreach { parent =>
        bi.contents.toSeq.foreach {
          case d: Definition if AST.mayOccurDirectlyIn(parent, d).contains(false) =>
            messages.addError(
              bi.loc,
              s"imported ${d.kind} '${d.id.value}' is not allowed at this location",
              suggestion = s"Move the load directive into a container that may hold a ${d.kind}" +
                s" (a 'module' accepts any top-level definition), or select something that fits" +
                s" here with 'im${"port"} <kind> <id> from \"${bi.path.s}\"'."
            )
          case _ => () // legal here, or no placement rule applies
        }
      }
    end if
  }

  private def validateSchema(
    schema: Schema,
    parents: Parents
  ): Unit = {
    checkIdentifierLength(schema)
    checkMetadata(schema)
    checkNonEmpty(
      schema.data.toSeq,
      "data definitions",
      schema,
      schema.errorLoc,
      MissingWarning,
      required = true
    )
    schema.schemaKind match {
      case RepositorySchemaKind.Flat =>
        if schema.links.nonEmpty then
          messages.addWarning(
            schema.errorLoc,
            s"${schema.identify} is ${schema.schemaKind} and should not define links",
            suggestion =
              s"Remove the links from this ${schema.schemaKind} schema, or change the schema kind to one that supports links."
          )
        if schema.data.size > 1 then
          messages.addWarning(
            schema.errorLoc,
            s"${schema.identify} is flat but defines ${schema.data.size} data nodes; flat schemas typically represent a single table or collection",
            suggestion =
              "Reduce the flat schema to a single data node, or change its kind to one that models multiple tables (e.g. relational)."
          )
      case RepositorySchemaKind.Document | RepositorySchemaKind.Columnar |
          RepositorySchemaKind.Vector =>
        if schema.links.nonEmpty then
          messages.addWarning(
            schema.errorLoc,
            s"${schema.identify} is ${schema.schemaKind} and should not define links",
            suggestion =
              s"Remove the links from this ${schema.schemaKind} schema, or change the schema kind to one that supports links."
          )
      case RepositorySchemaKind.TimeSeries =>
        if schema.indices.isEmpty then
          messages.addWarning(
            schema.errorLoc,
            s"${schema.identify} is a time-series schema but has no indices; time-series schemas should index the time dimension",
            suggestion = "Add an index on the time dimension of the time-series schema."
          )
      case RepositorySchemaKind.Hierarchical =>
        if schema.links.isEmpty && schema.data.size > 1 then
          messages.addWarning(
            schema.errorLoc,
            s"${schema.identify} is hierarchical with ${schema.data.size} data nodes but has no links; consider adding links to define the tree structure",
            suggestion =
              "Add links between data nodes to define the parent/child tree structure of the hierarchical schema."
          )
      case RepositorySchemaKind.Star =>
        if schema.links.isEmpty && schema.data.size > 1 then
          messages.addWarning(
            schema.errorLoc,
            s"${schema.identify} is a star schema with ${schema.data.size} data nodes but has no links; consider adding links from fact table to dimension tables",
            suggestion = "Add links from the fact table to the dimension tables in the star schema."
          )
      case RepositorySchemaKind.Graphical =>
        if schema.links.isEmpty && schema.data.nonEmpty then
          messages.addWarning(
            schema.errorLoc,
            s"${schema.identify} is graphical but has no links (edges)",
            suggestion =
              "Add links to define the edges connecting the nodes of the graphical schema."
          )
      case RepositorySchemaKind.Relational =>
        if schema.links.isEmpty && schema.data.size > 1 then
          messages.addWarning(
            schema.errorLoc,
            s"${schema.identify} is relational with ${schema.data.size} data nodes but has no links; consider adding links to define relationships",
            suggestion =
              "Add links between data nodes to define foreign-key relationships in the relational schema."
          )
        schema.links.values.foreach { case (fromRef, toRef) =>
          val fromType = resolvePath[Field](fromRef.pathId, parents).map(_.typeEx)
          val toType = resolvePath[Field](toRef.pathId, parents).map(_.typeEx)
          (fromType, toType) match {
            case (Some(ft), Some(tt)) =>
              if ft != tt then
                messages.addError(
                  fromRef.loc,
                  s"Link in ${schema.identify} connects fields with incompatible types: ${fromRef.pathId.format} is ${ft.format} but ${toRef.pathId.format} is ${tt.format}",
                  suggestion =
                    "Make the two linked fields share the same type so the relationship is type-consistent."
                )
            case _ => () // unresolved fields already reported elsewhere
          }
        }
      case _ => ()
    }
    if schema.schemaKind == RepositorySchemaKind.Vector && schema.data.size > 1 then
      messages.addWarning(
        schema.errorLoc,
        s"${schema.identify} is a vector schema but defines ${schema.data.size} data nodes; typically only one is expected",
        suggestion = "Keep the vector schema to a single data node."
      )
    schema.data.values.foreach { typeRef =>
      checkRef[Type](typeRef, parents)
    }
    schema.links.values.foreach { case (from, to) =>
      checkRef[Field](from, parents)
      checkRef[Field](to, parents)
    }
    schema.indices.foreach { fieldRef =>
      checkRef[Field](fieldRef, parents)
    }
  }

  private def validateRelationship(
    relationship: Relationship,
    parents: Parents
  ): Unit = {
    checkIdentifierLength(relationship)
    checkRef[Processor[?]](relationship.withProcessor, parents)
    checkMetadata(relationship)
  }

  private def validateEntity(
    entity: Entity,
    parents: Parents
  ): Unit = {
    checkContainer(parents, entity)
    // At most one state may be the entity's initial (starting) state.
    val initialStates = entity.states.filter(_.isInitial)
    if initialStates.sizeIs > 1 then
      messages.addError(
        initialStates(1).loc,
        s"${entity.identify} marks ${initialStates.size} states 'initial'; an entity has exactly " +
          s"one initial (starting) state",
        suggestion = "Mark only one state 'initial' (or none, to default to the first declared)."
      )
    // Entity-scope handlers follow the same rule, at ANY number of states.
    //
    // This used to be guarded by `entity.states.sizeIs <= 1`, so adding a second state made the
    // error disappear. The guard was carried over from the DEFAULTING rule in
    // `EntityParser.defaultEntityInitials`, where single-state genuinely is the right condition --
    // but defaulting and duplicate-detection are different rules and only the first is about state
    // count. It also encoded the pre-2026-08-04 model, in which an entity-scope handler under
    // multiple states was a common part merged into each state's set and `initial` on it meant
    // nothing. Under §17.2 an entity-scope `initial` is the initial handler for every state that
    // does not define one, so an ambiguous marker with several states is WORSE than with one -- it
    // silently picks live behavior for an unbounded set of states. The case the guard admitted was
    // the one where the ambiguity mattered least.
    val initialHandlers = entity.handlers.filter(_.isInitial)
    if initialHandlers.sizeIs > 1 then
      messages.addError(
        initialHandlers(1).loc,
        s"${entity.identify} marks ${initialHandlers.size} handlers 'initial'; only one handler " +
          s"may be the initial (live) one",
        suggestion =
          "Mark only one entity-scope handler 'initial' (or none, to default to the first)."
      )
    if entity.states.isEmpty && !entity.isEmpty then {
      messages.add(
        Message(
          entity.errorLoc,
          s"${entity.identify} must define at least one state",
          Messages.MissingWarning,
          suggestion =
            s"Add a state to ${entity.identify}, e.g. 'state ${entity.id.value}State of ${entity.id.value}Data is { ??? }'."
        )
      )
    }
    if entity.handlers.nonEmpty && entity.handlers.forall(_.clauses.isEmpty) then {
      messages.add(
        Message(
          entity.errorLoc,
          s"${entity.identify} has only empty handlers",
          Messages.MissingWarning,
          suggestion = "Add on-clauses to the entity's handlers, e.g. 'on command DoThing { ??? }'."
        )
      )
    }
    if entity.hasOption("finite-state-machine") && entity.states.sizeIs < 2 then {
      messages.add(
        Message(
          entity.errorLoc,
          s"${entity.identify} is declared as an fsm, but doesn't have at least two states",
          Messages.Error,
          suggestion =
            "Define at least two states for the finite-state-machine entity (a state machine needs states to transition between)."
        )
      )
    }
    if entity.hasOption("finite-state-machine") && entity.states.sizeIs >= 2 then {
      val hasMorphOrBecome = entity.handlers.exists { handler =>
        handler.clauses.exists { clause =>
          val finder = Finder(clause.contents)
          finder.recursiveFindByType[MorphStatement].nonEmpty ||
          finder.recursiveFindByType[BecomeStatement].nonEmpty
        }
      }
      if !hasMorphOrBecome then
        messages.addCompleteness(
          entity.errorLoc,
          s"${entity.identify} is declared as a finite-state-machine but its handlers contain no morph or become statements",
          suggestion =
            "Add 'morph' or 'become' statements so the FSM transitions between its states."
        )
    }
    if entity.states.nonEmpty then {
      val statesWithoutHandlers = entity.states.filter { state =>
        state.handlers.isEmpty && entity.handlers.isEmpty
      }
      for state <- statesWithoutHandlers do
        messages.add(
          Message(
            state.errorLoc,
            s"${state.identify} in ${entity.identify} has no handlers.",
            Messages.Error,
            suggestion =
              s"Add a handler to ${state.identify} (or to ${entity.identify}) to process messages in this state."
          )
        )
      end for
    } else if entity.handlers.isEmpty && !entity.isEmpty then {
      messages.add(
        Message(
          entity.errorLoc,
          s"${entity.identify} has no handlers and no states with handlers. " +
            "Add a handler to the entity or add a state with a handler.",
          Messages.Error,
          suggestion =
            "Add a handler to the entity, or add a state containing a handler, so the entity can process messages."
        )
      )
    }
    // Completeness 4a: each state should have on-init with set statement.
    // NOT for an event-sourced entity: R3 forbids `set` outside an `on event` clause, because
    // initial state must come from replaying an event like any other state change. Asking for a
    // `set` in `on init` there would demand exactly what checkEventSourcing rejects.
    if entity.states.nonEmpty && !entity.isEmpty && !entity.isEventSourced then
      entity.states.foreach { state =>
        val allHandlers = state.handlers ++ entity.handlers
        val onInits = allHandlers.flatMap(_.clauses.collect { case oic: OnInitializationClause =>
          oic
        })
        if onInits.isEmpty then
          messages.addCompleteness(
            state.errorLoc,
            s"${state.identify} in ${entity.identify} has no 'on init' clause to initialize its state",
            suggestion =
              s"Add an 'on init' clause to a handler of ${state.identify} to initialize its fields."
          )
        else
          val hasSet = onInits.exists { oic =>
            val finder = Finder(oic.contents)
            finder.recursiveFindByType[SetStatement].nonEmpty
          }
          if !hasSet then
            messages.addCompleteness(
              state.errorLoc,
              s"${state.identify} in ${entity.identify} has an 'on init' clause but no 'set' statement to initialize state values",
              suggestion =
                "Add 'set' statements in the 'on init' clause to initialize the state's fields."
            )
      }
    // Completeness 4f: entity with no handlers at all
    if entity.nonEmpty && entity.handlers.isEmpty && entity.states.forall(_.handlers.isEmpty) then
      messages.addCompleteness(
        entity.errorLoc,
        s"${entity.identify} has no handlers to process messages",
        suggestion = "Add a handler (on the entity or its state) to process incoming messages."
      )
    // Completeness 4g: entity without query handlers
    if entity.nonEmpty && entity.handlers.nonEmpty then
      val allHandlers = entity.handlers ++ entity.states.flatMap(_.handlers)
      val hasQueryHandler = allHandlers.exists { handler =>
        handler.clauses.exists {
          case omc: OnMessageClause => omc.msg.messageKind == AggregateUseCase.QueryCase
          case _                    => false
        }
      }
      if !hasQueryHandler then
        messages.addCompleteness(
          entity.errorLoc,
          s"${entity.identify} has no 'on query' clause; information cannot be extracted from it",
          suggestion = "Add an 'on query' clause so the entity's state can be read."
        )
    // Completeness 4h: entity without event outlet in parent context
    if entity.nonEmpty then
      parents.headOption.collect { case c: Context => c }.foreach { context =>
        val hasOutlet = context.streamlets.exists(_.outlets.nonEmpty)
        if !hasOutlet then
          messages.addCompleteness(
            entity.errorLoc,
            s"${entity.identify} in ${context.identify} has no outlet streamlet to publish events on",
            suggestion =
              s"Add a Source or Flow streamlet with an outlet to ${context.identify} so ${entity.identify} can publish its events."
          )
      }
    // Completeness: entity Id type placement checks
    if entity.nonEmpty then {
      val parentContext = parents.collectFirst { case c: Context => c }

      // Search all known types via symbols table for Id types referencing this entity
      def isIdForEntity(t: Type): Boolean = t.typEx match {
        case uid: UniqueId =>
          uid.entityPath.value.lastOption.contains(entity.id.value)
        case _ => false
      }

      // Find all Id types for this entity and determine their scope
      val allIdTypes = symbols.parentage.keys.collect {
        case t: Type if isIdForEntity(t) => t
      }.toSeq

      if allIdTypes.isEmpty then {
        // (b) Not defined at all
        messages.addCompleteness(
          entity.errorLoc,
          s"${entity.identify} does not define an Id type for its identity",
          suggestion =
            s"Define an Id type for ${entity.identify} in its context, e.g. 'type Id = Id(${entity.id.value})'."
        )
      } else {
        allIdTypes.foreach { idType =>
          val idParents = symbols.parentsOf(idType)
          val directParent = idParents.headOption
          directParent match {
            case Some(e: Entity) if e == entity =>
              // (a) Id defined inside entity — too narrow
              messages.addCompleteness(
                idType.errorLoc,
                s"${idType.identify} is defined inside ${entity.identify}; " +
                  "move it to the containing context so other entities can reference it",
                suggestion =
                  s"Move ${idType.identify} from ${entity.identify} up to the containing context so other entities can reference it."
              )
            case Some(c: Context) if parentContext.contains(c) =>
            // Correct placement — no warning
            case Some(_: Include[?]) =>
              // Id is in an include — check if the include's context matches
              val idContext = symbols.contextOf(idType)
              if idContext == parentContext then {
                // Correct — in an included file within the same context
              } else {
                // (c) Outside the containing context
                messages.addCompleteness(
                  idType.errorLoc,
                  s"${idType.identify} for ${entity.identify} is defined outside the containing context; " +
                    "constrain it to the context scope and use adaptors for inter-context invocations",
                  suggestion =
                    s"Move ${idType.identify} into ${entity.identify}'s context, and use adaptors for any inter-context references to it."
                )
              }
            case _ =>
              // (c) Defined at domain level or elsewhere — too broad
              val idContext = symbols.contextOf(idType)
              if idContext != parentContext then {
                messages.addCompleteness(
                  idType.errorLoc,
                  s"${idType.identify} for ${entity.identify} is defined outside the containing context; " +
                    "constrain it to the context scope and use adaptors for inter-context invocations",
                  suggestion =
                    s"Move ${idType.identify} into ${entity.identify}'s context, and use adaptors for any inter-context references to it."
                )
              }
          }
        }
      }
    }
    checkEntityIntentions(entity)
    checkEventSourcing(entity)
    // Completeness: an entity should define command and event types, and its
    // handlers should cover each command. These checks were previously emitted
    // as AIHelperPass tips. They are advisory (message types are often defined
    // at context scope rather than inside the entity), so they are emitted only
    // when provideTips is enabled (i.e. `riddlc advise` / `--provide-tips`),
    // each carrying a remediation suggestion.
    if entity.nonEmpty && summon[PlatformContext].options.provideTips then {
      val entityTypes = entity.types
      val commandTypes = entityTypes.filter(_.typEx.isAggregateOf(AggregateUseCase.CommandCase))
      val eventTypes = entityTypes.filter(_.typEx.isAggregateOf(AggregateUseCase.EventCase))
      if commandTypes.isEmpty then
        messages.addCompleteness(
          entity.errorLoc,
          s"${entity.identify} defines no command types; commands are the input messages an entity receives",
          suggestion =
            s"Add a command type, e.g. 'type ${entity.id.value}Command = command { ??? }'."
        )
      if eventTypes.isEmpty then
        messages.addCompleteness(
          entity.errorLoc,
          s"${entity.identify} defines no event types; events record what happened when a command is processed",
          suggestion = s"Add an event type, e.g. 'type ${entity.id.value}Event = event { ??? }'."
        )
      if commandTypes.nonEmpty then
        val allHandlers = entity.handlers ++ entity.states.flatMap(_.handlers)
        val handledCommandNames = allHandlers
          .flatMap(_.clauses)
          .collect {
            case omc: OnMessageClause if omc.msg.messageKind == AggregateUseCase.CommandCase =>
              omc.msg.pathId.value.lastOption.getOrElse("")
          }
          .toSet
        for cmd <- commandTypes if !handledCommandNames.contains(cmd.id.value) do
          messages.addCompleteness(
            cmd.errorLoc,
            s"Command ${cmd.identify} in ${entity.identify} is not handled by any on-clause",
            suggestion = s"Add an on-clause for it, e.g. 'on command ${cmd.id.value} { ??? }'."
          )
        end for
    }
  }

  /** Within a group the intention keywords are mutually exclusive.
    *
    * `event-sourced` is in the persistence group because it IMPLIES persistent -- writing both is
    * redundant rather than additive. Reported rather than made a parse error so the model still
    * builds and the author sees both keywords named.
    */
  private def checkEntityIntentions(entity: Entity): Unit =
    entity.intentions.groupBy(_.group).foreach { case (group, chosen) =>
      if chosen.sizeIs > 1 then
        val names = chosen.map(i => s"'${i.keyword}'").mkString(" and ")
        messages.addError(
          entity.errorLoc,
          s"${entity.identify} declares $names, but $group intentions are mutually exclusive",
          suggestion = s"Keep exactly one $group keyword before 'entity'." +
            (if chosen.contains(EntityIntention.EventSourced) &&
               chosen.contains(EntityIntention.Persistent)
             then " 'event-sourced' already implies 'persistent'."
             else "")
        )
    }
  end checkEntityIntentions

  /** The preconditions without which an entity cannot be event sourced at all.
    *
    * Replay rebuilds state by re-applying the recorded events in order, so the SAME state changes
    * must occur. That requires: every command says what event it produces (R1), every such event
    * has a clause that applies it (R2), and no state change happens anywhere but while handling an
    * event (R3/R4). These are Errors, not warnings: a model failing them is not incompletely
    * described, it is impossible to event-source.
    */
  private def checkEventSourcing(entity: Entity): Unit = {
    if entity.isEmpty || !entity.isEventSourced then return

    val allClauses = (entity.handlers ++ entity.states.flatMap(_.handlers)).flatMap(_.clauses)
    val commandClauses = allClauses.collect {
      case omc: OnMessageClause if omc.msg.messageKind == AggregateUseCase.CommandCase => omc
    }
    val eventClauses = allClauses.collect { case oec: OnEventClause => oec }

    // R1 + R2. The must-handle set comes from the `yields` DECLARATION on each handled command's
    // type -- not from `yield` statements in the body.
    val handledEventTypes: Seq[Type] =
      eventClauses.flatMap(oec => resolution.refMap.definitionOf[Type](oec.msg.pathId))
    commandClauses.foreach { omc =>
      resolution.refMap.definitionOf[Type](omc.msg.pathId).foreach { commandType =>
        commandType.typEx match
          case auc: AggregateUseCaseTypeExpression =>
            auc.yields match
              case None =>
                messages.addError(
                  omc.errorLoc,
                  s"${entity.identify} is event-sourced but ${commandType.identify} declares no " +
                    s"'yields' clause, so there is no event to record",
                  suggestion = s"Declare the event it produces, e.g. " +
                    s"'command ${commandType.id.value} yields event SomethingHappened is { ??? }'."
                )
              case Some(yielded) =>
                val yieldedType = resolution.refMap.definitionOf[Type](yielded.pathId)
                val handled = (yieldedType, handledEventTypes) match
                  case (Some(yt), handledTypes) => handledTypes.exists(_ eq yt)
                  case _                        => true // unresolved: reported elsewhere
                if !handled then
                  messages.addError(
                    omc.errorLoc,
                    s"${entity.identify} is event-sourced and ${commandType.identify} yields " +
                      s"'${yielded.format}', but no 'on event' clause applies it on replay",
                    suggestion = s"Add 'on ${yielded.format} { ??? }' to a handler of " +
                      s"${entity.identify} so the event can be replayed."
                  )
          case _ => () // not an aggregate command type; other checks report that
      }
    }

    // R3 + R4. A mutation is legal only while applying one of the entity's OWN events.
    allClauses.foreach { clause =>
      val ownEventClause = clause match
        case oec: OnEventClause => isDeclaredInEntity(oec.msg.pathId, entity)
        case _                  => false
      if !ownEventClause then
        val why = clause match
          case _: OnEventClause =>
            "an event declared outside it, which must be turned into one of its own events first"
          case _ => "something other than one of its own events"
        walkStatements(clause.contents) { stmt =>
          mutationKeyword(stmt).foreach { kw =>
            messages.addError(
              stmt.loc,
              s"${entity.identify} is event-sourced, so '$kw' may only appear while handling " +
                s"one of its own events; ${clause.identify} handles $why",
              suggestion = clause match
                case _: OnEventClause =>
                  s"Yield one of ${entity.identify}'s own events here and '$kw' in that event's clause."
                case _ =>
                  s"Move the '$kw' into the 'on event' clause for the event this yields, so replay reproduces it."
            )
          }
        }
    }
  }

  /** `set`, `morph` and `become` all change what replay must reproduce. */
  private def mutationKeyword(stmt: Statement): Option[String] = stmt match
    case _: SetStatement    => Some("set")
    case _: MorphStatement  => Some("morph")
    case _: BecomeStatement => Some("become")
    case _                  => None

  /** Is the referenced type declared INSIDE this entity? Walks past any `Include`, whose contents
    * belong to the entity that included them even though it is their direct parent.
    */
  private def isDeclaredInEntity(pathId: PathIdentifier, entity: Entity): Boolean =
    resolution.refMap.definitionOf[Type](pathId).exists { typ =>
      symbols.parentsOf(typ).exists {
        case e: Entity => e eq entity
        case _         => false
      }
    }

  /** A70. The check that earns correlations: every REQUIRED non-key field of the yielded record is
    * `set` by at least one fold.
    *
    * It turns "this correlation can never complete" from a production mystery into a compile-time
    * fact, exactly as the event-sourcing rules did for entities. Completion is TYPE-DERIVED
    * (Computational Model §6.5) — nothing in the source states a completion condition, so nothing
    * can drift out of sync with the record.
    *
    * Key fields are EXEMPT: §6.5 populates them implicitly from the correlation key, so demanding a
    * fold set them would reject every correct correlation. `Optional` (`?`) and `ZeroOrMore` (`*`)
    * fields are not required — both admit "nothing there" — while `OneOrMore` (`+`) is.
    */
  /** A70: warn about a `set` inside a fold that a later `set` to the SAME field overrides on EVERY
    * path. The earlier value can never reach the yielded command, so writing it is dead work and
    * usually a mistake about which event should win.
    *
    * Reported only when the override is CERTAIN. `dischargesOnEveryPathSeq` supplies exactly that
    * standard — a `when` needs both branches, a `match` needs a `default`, and a `foreach` body
    * never counts because it may iterate zero times — so a merely POSSIBLE override stays silent,
    * which is the whole difference between this warning and noise.
    *
    * `continuation` is what executes after `statements` finishes: recursing into a `when`'s branch
    * passes the rest of the enclosing block, so a `set` in a branch that a later statement
    * overrides is caught too. Without it the check would only see straight-line lists.
    *
    * The CROSS-clause case is not here: two different folds writing one field is a race and
    * already an Error (see `raced` above), because arrival order across sources is not guaranteed.
    * This is the within-one-fold complement, where order IS guaranteed and the defect is
    * therefore only dead work.
    */
  private def checkOverriddenSets(
    statements: Seq[RiddlValue],
    continuation: Seq[RiddlValue],
    correlation: Correlation,
    clause: OnClause
  ): Unit =
    statements.zipWithIndex.foreach { (value, index) =>
      lazy val rest: Seq[RiddlValue] = statements.drop(index + 1) ++ continuation
      value match
        case ss @ SetStatement(_, fr: FieldRef, _) =>
          val name = fr.pathId.value.last
          val overridden = dischargesOnEveryPathSeq(rest) {
            case SetStatement(_, other: FieldRef, _) => other.pathId.value.last == name
            case _                                   => false
          }
          if overridden then
            messages.addWarning(
              ss.loc,
              s"'$name' is set here and set again on every path before ${clause.identify} ends, " +
                s"so this value never reaches ${correlation.yields.format}",
              suggestion = s"Remove this 'set', or move the later one into the branch where it " +
                s"should win."
            )
        case w: WhenStatement =>
          checkOverriddenSets(w.thenStatements.toSeq, rest, correlation, clause)
          checkOverriddenSets(w.elseStatements.toSeq, rest, correlation, clause)
        case m: MatchStatement =>
          m.cases.foreach(mc => checkOverriddenSets(mc.statements.toSeq, rest, correlation, clause))
          checkOverriddenSets(m.default.toSeq, rest, correlation, clause)
        case f: ForeachStatement =>
          // The body's own continuation is `rest`: whether the loop repeats or exits, `rest`
          // eventually runs, so a `set` in the body that `rest` overrides is still dead work.
          checkOverriddenSets(f.doStatements.toSeq, rest, correlation, clause)
        case _ => () // every other statement writes no field
      end match
    }
  end checkOverriddenSets

  private def validateCorrelation(
    correlation: Correlation,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, correlation)
    checkMetadata(correlation)

    // The bound is grammar rather than an option (A70), but it needs the SAME test an option's
    // duration gets -- otherwise `times out after "banana"` would compile. There is no "remove it"
    // advice here, unlike for an option: the clause is mandatory.
    checkPreciseDuration(
      correlation.timeout,
      s"The timeout of ${correlation.identify}",
      "Give the correlation a bound greater than zero; a correlation that expires immediately " +
        "can never complete."
    )

    // Every field name reached by a `set` in any fold, plus the two per-clause defects. Uses
    // walkStatements so a `set` nested in when/match/foreach still counts -- it is reachable,
    // which is all these checks ask.
    // `Set[String]` is spelled out: AST.Set (the statement) shadows scala.Set in this file.
    val setFieldNames: scala.collection.immutable.Set[String] =
      val names = scala.collection.mutable.Set.empty[String]
      val writerOf = scala.collection.mutable.Map.empty[String, OnClause]
      val raced = scala.collection.mutable.Map.empty[String, OnClause]
      correlation.handlers.foreach { handler =>
        handler.clauses.foreach { clause =>
          var setsSomething = false
          walkStatements(clause.contents) {
            case SetStatement(_, fr: FieldRef, _) =>
              val name = fr.pathId.value.last
              setsSomething = true
              names += name
              writerOf.get(name) match
                case Some(other) if !(other eq clause) => raced.getOrElseUpdate(name, clause)
                case _                                 => writerOf.put(name, clause)
            case _ => ()
          }
          // A70: within THIS fold, a `set` the rest of the fold certainly overrides is dead work.
          checkOverriddenSets(clause.contents.toSeq, Seq.empty, correlation, clause)
          // A fold that never `set`s contributes nothing to the join. That is always a mistake
          // rather than a style choice, so it is an Error and not a warning.
          check(
            setsSomething,
            s"${clause.identify} in ${correlation.identify} sets no field of " +
              s"${correlation.yields.format}; every fold must terminate in a 'set'",
            Messages.Error,
            clause.loc,
            suggestion = s"End ${clause.identify} with 'set field <name> to <value>', or remove " +
              "the clause if the event contributes nothing to this correlation."
          )
        }
      }
      // Two clauses writing one field make the completed record depend on arrival order, which
      // across sources is not guaranteed. §6.6 REJECTS the race rather than reporting it: the
      // alternative is a model whose result differs between runs over identical events.
      raced.foreach { (name, clause) =>
        messages.addError(
          clause.loc,
          s"Field '$name' of ${correlation.yields.format} is set by more than one clause of " +
            s"${correlation.identify}; the completed record would depend on arrival order",
          suggestion = s"Set '$name' from exactly one event, or give each source its own field."
        )
      }
      names.toSet
    end setFieldNames

    val keyNames: scala.collection.immutable.Set[String] = correlation.keys.map(_.value).toSet

    checkRefAndExamine[Type](correlation.yields, parents) { (typ: Type) =>
      // Same rule, and same reason, as State vs. its record type: sharing the name makes the path
      // ambiguous. The generic message is NOT deficient -- `ResolutionPass` lists every matching
      // definition with its location and suggests fully qualifying the path. This check earns its
      // place by being specific instead: it fires on the DECLARATION rather than on each use, and
      // names the one fix that actually applies here (rename one of the two), where the generic
      // advice to fully-qualify would work but leaves the collision in place.
      check(
        typ.id.value != correlation.id.value,
        s"${correlation.identify} and ${typ.identify} must not have the same name so path " +
          s"resolution can succeed",
        Messages.Error,
        correlation.loc,
        suggestion = s"Rename either the correlation or the record so they do not share the name " +
          s"'${correlation.id.value}'."
      )
      // A70 (Reid, 2026-08-12): the grammar already rejects the wrong KEYWORD -- `yields record R`
      // does not parse -- but only here is the referent resolved, so only here can `yields command
      // Foo` naming an event, result, query or plain record be caught. Validation owns it for the
      // usual reason: a parse-time error() would preempt the whole pass chain, and the evidence
      // (the resolved Type) survives into the AST.
      val yieldsACommand: Boolean = typ.typEx match
        case auc: AggregateUseCaseTypeExpression => auc.usecase == AggregateUseCase.CommandCase
        case _                                   => false
      check(
        yieldsACommand,
        s"${correlation.identify} must yield a command but ${typ.identify} is not one; a " +
          "projector's only output is a change to a repository, and a repository is changed by " +
          "handling a command",
        Messages.Error,
        correlation.yields.pathId.loc,
        suggestion = s"Declare '${typ.id.value}' as a command, or yield a command the repository " +
          s"handles."
      )

      // Gated on the kind: reporting which fields "no fold sets" against a type that was never a
      // valid target compounds one mistake into two, and the second is derived from a wrong premise.
      typ.typEx match
        case agg: AggregateTypeExpression if yieldsACommand =>
          val unset = agg.fields.filter { field =>
            val isOptional = field.typeEx match
              case _: Optional | _: ZeroOrMore => true
              case _                           => false
            !isOptional && !keyNames.contains(field.id.value) &&
            !setFieldNames.contains(field.id.value)
          }
          if unset.nonEmpty then
            messages.addError(
              correlation.errorLoc,
              s"${correlation.identify} can never complete: ${typ.identify} requires " +
                s"${unset.map(_.id.value).mkString("'", "', '", "'")}, which no fold sets",
              suggestion = s"Add an 'on event ... is { set field ${unset.head.id.value} to ... }' " +
                s"clause to ${correlation.identify}, or make the field optional."
            )
          end if
        case _ => () // a non-aggregate target is reported by the record-ref check itself
      end match

      // Every key component must exist on every handled event, or the events bearing it could not
      // be routed to one correlation instance in the first place (§6.6 makes the key the
      // distribution key). Checked against the DECLARED message type of each clause.
      typ.typEx match
        case _: AggregateTypeExpression =>
          correlation.handlers.foreach { handler =>
            handler.clauses.foreach {
              case omc: OnMessageLikeClause if omc.msg.nonEmpty =>
                resolution.refMap.definitionOf[Type](omc.msg.pathId).foreach { msgType =>
                  msgType.typEx match
                    case msgAgg: AggregateTypeExpression =>
                      val present = msgAgg.fields.map(_.id.value).toSet
                      val missing = correlation.keys.map(_.value).filterNot(present.contains)
                      if missing.nonEmpty then
                        messages.addError(
                          omc.loc,
                          s"${omc.identify} in ${correlation.identify} handles " +
                            s"${msgType.identify}, which has no " +
                            s"${missing.mkString("'", "', '", "'")}; every key component must be " +
                            s"present on every handled event",
                          suggestion = s"Add ${missing.mkString("'", "', '", "'")} to " +
                            s"${msgType.identify}, or key the correlation on fields every handled " +
                            "event carries."
                        )
                      end if
                    case _ => () // a non-aggregate message cannot carry a key; reported elsewhere
                  end match
                }
              case _ => () // non-message clauses cannot appear in a fold
            }
          }
        case _ => ()
      end match
    }

    // A70 (Reid, 2026-08-12): the yielded command should be one the projector's repository actually
    // handles. Since `yields` names a COMMAND, this is plain identity -- the earlier design had to
    // INFER acceptance from a command that "held" the yielded record, because a record was not
    // nameable by any `on` clause (A9b). Naming the command deleted the inference.
    //
    // COMPLETENESS, not an Error (Reid's ruling; A70 had specified an Error). A repository lacking
    // the handler is under-specified rather than self-contradictory, and it sits beside the other
    // projector completeness warnings below.
    val yieldedCommand: Option[Type] = resolution.refMap.definitionOf[Type](correlation.yields.pathId)
    parents.collectFirst { case p: Projector => p }.foreach { projector =>
      yieldedCommand.foreach { yielded =>
        projector.repositories.foreach { repoRef =>
          resolution.refMap
            .definitionOf[Repository](repoRef.pathId)
            // `???` says "known to be incomplete", so it earns a Missing warning about its body and
            // nothing else (Reid, 2026-08-11). This mirrors `validateRepository`, which likewise
            // declines to tell an empty repository that it needs a handler.
            .filter(_.nonEmpty)
            .foreach { repo =>
              val handled: Boolean = repo.handlers.exists { handler =>
                handler.clauses.exists {
                  case omc: OnMessageLikeClause
                      if omc.msg.nonEmpty &&
                        omc.msg.messageKind == AggregateUseCase.CommandCase =>
                    // Compared by resolved definition identity, not by name: two contexts may each
                    // declare a `RecordFulfillment`, and only one of them is this one.
                    resolution.refMap.definitionOf[Type](omc.msg.pathId).exists(_ eq yielded)
                  case _ => false
                }
              }
              if !handled then
                messages.addCompleteness(
                  correlation.errorLoc,
                  s"${repo.identify} has no handler for ${yielded.identify}, which " +
                    s"${correlation.identify} yields",
                  suggestion = s"Add an 'on command ${yielded.id.value}' clause to a handler of " +
                    s"${repo.identify}, so the correlation's result is actually stored."
                )
            }
        }
      }
    }

    // Purity of the folds is what makes re-running them safe (§6.5), so an effect inside one is an
    // Error. This binds FOLDS ONLY: the timeout block is an effect block by design (§6.7) and
    // banning effects there would leave it unable to do anything.
    correlation.handlers.foreach { handler =>
      handler.clauses.foreach { clause =>
        walkStatements(clause.contents) { statement =>
          val effect: Option[String] = statement match
            case _: TellStatement      => Some("tell")
            case _: SendStatement      => Some("send")
            case _: YieldStatement     => Some("yield")
            case _: ReplyStatement     => Some("reply")
            case _: PutStatement       => Some("put")
            case _: MorphStatement     => Some("morph")
            case _: BecomeStatement    => Some("become")
            // A70/instance-identity: ending an instance is exactly the kind of effect a re-run of
            // a fold must not repeat.
            case _: TerminateStatement => Some("terminate")
            case _                     => None
          // Task 7: `initiate` is the OTHER instance-identity effect, and unlike `terminate` it is
          // never a Statement of its own -- it is a VALUE, most commonly hiding inside a
          // `let x = initiate ...` (a LetStatement, matched by NONE of the arms above). Detecting it
          // needs a value walk (`initiatesIn`, over `statementValues(statement)`), not a statement
          // match, which is exactly the asymmetry Task 5 left: `terminate` was banned here, and
          // `initiate` was not.
          val offenders: Seq[(At, String)] =
            effect.map(kw => statement.loc -> kw).toSeq ++
              statementValues(statement).flatMap(initiatesIn).map(init => init.loc -> "initiate")
          offenders.foreach { case (loc, kw) =>
            messages.addError(
              loc,
              s"A fold of ${correlation.identify} may not '$kw': folds must be free of effects so " +
                s"re-running them over the same events is safe",
              suggestion = s"Move the '$kw' into the correlation's 'times out after' block, or " +
                "into an ordinary handler on the projector."
            )
          }
        }
      }
    }
  }
  end validateCorrelation

  private def validateProjector(
    projector: Projector,
    parents: Parents
  ): Unit = {
    checkContainer(parents, projector)
    // A70: both checks below predate correlations and assume a projector's folds live in one
    // top-level handler over a record the projector itself declares. A correlating projector does
    // neither -- its folds live inside its Correlations, and `yields record X` names a record that
    // normally lives in the enclosing Context. Left unrelaxed, these two would reject every
    // correlation-only projector, so each is skipped when correlations are present rather than
    // deleted: a projector WITHOUT correlations is validated exactly as before.
    val hasCorrelations = projector.correlations.nonEmpty

    // Reid's ruling, 2026-08-11: a projector's record is the type it SENDS to its repository --
    // `tell command RecordOrder to repository Store` -- and WHERE that type is defined does not
    // affect whether the requirement is met. The old check only ever inspected `projector.types`,
    // so a correct 1-for-1 event->command translator was rejected for not declaring a record it
    // would never use. It was asking the wrong question, not asking too much.
    val repositoriesOf: Seq[Repository] =
      projector.repositories.flatMap(rr => resolution.refMap.definitionOf[Repository](rr.pathId))

    val sentToRepository: Seq[(Type, Repository, Statement)] =
      val found = scala.collection.mutable.ListBuffer.empty[(Type, Repository, Statement)]
      projector.handlers.foreach { handler =>
        handler.clauses.foreach { clause =>
          walkStatements(clause.contents) {
            case ts: TellStatement =>
              resolution.refMap
                .definitionOf[Repository](ts.processorRef.pathId)
                .filter(repo => repositoriesOf.exists(_ eq repo))
                .foreach { repo =>
                  val sentType = ts.msg match
                    case mr: MessageRef => resolution.refMap.definitionOf[Type](mr.pathId)
                    case c: Constructor => resolution.refMap.definitionOf[Type](c.ref.pathId)
                    case _: ValueRef    => None // type comes from the clause; not a declaration here
                  sentType.foreach(t => found += ((t, repo, ts)))
                }
            case _ => ()
          }
        }
      }
      found.toSeq
    end sentToRepository

    check(
      hasCorrelations || sentToRepository.nonEmpty || projector.types.exists { (typ: Type) =>
        typ.typEx match {
          case auc: AggregateUseCaseTypeExpression =>
            auc.usecase == AggregateUseCase.RecordCase
          case _ => false
        }
      },
      s"${projector.identify} lacks a required ${AggregateUseCase.RecordCase.useCase} definition.",
      Messages.Error,
      projector.errorLoc,
      suggestion = s"Send a message to ${projector.identify}'s repository (e.g. 'tell command " +
        s"SomeCommand to repository R'), or add a record type to ${projector.identify}."
    )

    // WHERE the sent type lives is a Warning, not an Error: the type is what populates the
    // database, so it belongs WITH the repository even though defining it elsewhere works.
    // A `???` repository is exempt -- `???` says "known to be incomplete", so it earns a Missing
    // warning about its body and nothing else (Reid, 2026-08-11).
    sentToRepository.foreach { (typ, repo, stmt) =>
      if repo.nonEmpty && !symbols.parentsOf(typ).exists(_ eq repo) then
        messages.addWarning(
          stmt.loc,
          s"${typ.identify} populates ${repo.identify} but is not defined in it",
          suggestion = s"Move ${typ.identify} into ${repo.identify}, so the data that populates " +
            s"the repository is associated with it."
        )
      end if
    }
    check(
      if hasCorrelations then projector.handlers.length <= 1 else projector.handlers.length == 1,
      s"${projector.identify} must have exactly one Handler but has ${projector.handlers.length}",
      Messages.Error,
      projector.errorLoc,
      suggestion = "Define exactly one handler for the projector."
    )
    projector.repositories.foreach { repoRef =>
      checkRef[Repository](repoRef, parents)
    }
    // Completeness 4c: projectors should reference at least one repository
    if projector.repositories.isEmpty && projector.nonEmpty then
      messages.addCompleteness(
        projector.errorLoc,
        s"${projector.identify} does not reference any repository to persist its projection",
        suggestion =
          s"Reference a repository from ${projector.identify}, e.g. 'updates repository SomeRepository'."
      )
    if projector.handlers.nonEmpty then {
      val allClauses = projector.handlers.flatMap(_.clauses).collect {
        case omc: OnMessageLikeClause => omc
      }
      if allClauses.nonEmpty then {
        val handlesEvents = allClauses.exists { omc =>
          omc.msg.messageKind == AggregateUseCase.EventCase
        }
        if !handlesEvents then
          messages.addWarning(
            projector.errorLoc,
            s"${projector.identify} handler does not handle any events; projectors typically handle events to build read models",
            suggestion =
              "Add 'on event ...' clauses to the projector's handler to build its read model."
          )
      }
      // Completeness: projector handlers must tell to a repository
      val allTells = projector.handlers.flatMap { handler =>
        val finder = Finder(handler)
        finder.recursiveFindByType[TellStatement]
      }
      if allTells.isEmpty then {
        messages.addCompleteness(
          projector.errorLoc,
          s"${projector.identify} does not persist its projection; projector handlers should tell messages to a repository",
          suggestion =
            "Add 'tell' statements in the projector's handler to write its read model to a repository."
        )
      }
      // Check each declared repository is actually used in a tell
      if projector.repositories.nonEmpty && allTells.nonEmpty then {
        projector.repositories.foreach { repoRef =>
          val repoName = repoRef.pathId.value.lastOption.getOrElse("")
          val isTold = allTells.exists { tell =>
            tell.processorRef.pathId.value.lastOption.contains(repoName)
          }
          if !isTold then
            messages.addUsage(
              repoRef.loc,
              s"${projector.identify} declares ${repoRef.format} but does not send it any messages",
              suggestion =
                s"Send messages to ${repoRef.format} with 'tell', or remove the unused repository reference."
            )
        }
      }
    }
  }

  private def validateRepository(
    repository: Repository,
    parents: Parents
  ): Unit = {
    checkContainer(parents, repository)
    checkNonEmpty(
      repository.contents.filter[Schema],
      "schema",
      repository,
      repository.errorLoc,
      MissingWarning,
      required = false
    )
    if repository.handlers.isEmpty && repository.nonEmpty then
      messages.addMissing(
        repository.errorLoc,
        s"${repository.identify} should have at least one handler",
        suggestion =
          s"Add a handler to ${repository.identify} to process commands (writes) and queries (reads)."
      )
    if repository.handlers.nonEmpty then {
      val allClauses = repository.handlers.flatMap(_.clauses).collect {
        case omc: OnMessageLikeClause => omc
      }
      if allClauses.nonEmpty then {
        val handlesCommandOrQuery = allClauses.exists { omc =>
          omc.msg.messageKind == AggregateUseCase.CommandCase ||
          omc.msg.messageKind == AggregateUseCase.QueryCase
        }
        if !handlesCommandOrQuery then
          messages.addWarning(
            repository.errorLoc,
            s"${repository.identify} handlers do not handle any commands or queries; repositories typically handle commands (for mutations) and queries (for reads)",
            suggestion =
              "Add 'on command ...' (for mutations) and 'on query ...' (for reads) clauses to the repository's handler."
          )
      }
    }
    checkRepositoryScopePlacement(repository, parents)
  }

  /** Repository scope placement (domain vs context). A repository whose handlers synthesize
    * messages from multiple contexts belongs at domain scope; one confined to a single context
    * belongs at context scope. "Reach" is approximated by resolving each on-clause's handled
    * message to its owning context. (In 2.0, once `tell` becomes a stream send, this can switch to
    * a MessageFlow signal.)
    *
    *   - context-scoped repo reaching another context -> CompletenessWarning (promote)
    *   - domain-scoped repo reaching only one context -> Error (demote; unnecessary)
    */
  private def checkRepositoryScopePlacement(repository: Repository, parents: Parents): Unit = {
    // NOTE: no `Set[Context]` annotation — AST.Set shadows scala.Set; `.toSet`
    // already yields a scala immutable Set.
    val reachedContexts =
      repository.handlers.iterator
        .flatMap(_.clauses)
        .collect { case omc: OnMessageLikeClause if omc.msg.nonEmpty => omc.msg.pathId }
        .flatMap(pid => resolution.refMap.definitionOf[Type](pid).toList)
        .flatMap(msgType => symbols.contextOf(msgType).toList)
        .toSet
    parents.headOption match {
      case Some(_: Domain) =>
        // Verify a domain-scoped repository is actually necessary. If its handlers
        // demonstrably reach only one context it should be at context scope. Zero
        // resolvable contexts means an incomplete/unresolvable model, not proof of
        // single-context reach, so we do not error in that case.
        if reachedContexts.size == 1 then {
          val only = reachedContexts.head
          messages.addError(
            repository.errorLoc,
            s"${repository.identify} is at domain scope but its handlers only reach ${only.identify}; " +
              "a domain-scoped repository must synthesize messages across multiple contexts",
            suggestion =
              s"Move ${repository.identify} into ${only.identify}, or add handlers that " +
                "reference messages from other contexts."
          )
        }
      case Some(enclosing: Context) =>
        // A context-scoped repository whose handlers reach a different context crosses
        // context boundaries and typically belongs at domain scope.
        val foreign = reachedContexts.filterNot(_ == enclosing)
        if foreign.nonEmpty then {
          messages.addCompleteness(
            repository.errorLoc,
            s"${repository.identify} handles messages from other contexts " +
              s"(${foreign.map(_.id.value).mkString(", ")}); a repository whose handlers cross " +
              "context boundaries typically belongs at domain scope",
            suggestion =
              s"Consider moving ${repository.identify} up to the enclosing domain so it " +
                "can synthesize across contexts."
          )
        }
      case _ => ()
    }
  }

  private def validateAdaptor(
    adaptor: Adaptor,
    parents: Parents
  ): Unit = {
    parents.headOption match {
      case Some(c: Context) =>
        checkContainer(parents, adaptor)
        resolvePath(adaptor.referent.pathId, parents).map { (target: Context) =>
          if target == c then {
            val message =
              s"${adaptor.identify} may not specify a target context that is " +
                s"the same as the containing ${c.identify}"
            messages.addError(
              adaptor.errorLoc,
              message,
              suggestion =
                s"Point the adaptor at a different context than its containing ${c.identify}."
            )
          }
        }
        if adaptor.handlers.isEmpty && adaptor.nonEmpty then
          messages.addMissing(
            adaptor.errorLoc,
            s"${adaptor.identify} should have at least one handler",
            suggestion =
              s"Add a handler to ${adaptor.identify} to translate messages between the contexts."
          )
        else if adaptor.handlers.nonEmpty && adaptor.handlers.forall(_.clauses.isEmpty) then
          messages.addMissing(
            adaptor.errorLoc,
            s"${adaptor.identify} has only empty handlers",
            suggestion =
              "Add on-clauses to the adaptor's handlers to translate messages between contexts."
          )
        // Completeness (adaptors): every non-empty handler must include an 'on other' clause so
        // that messages it does not explicitly translate are handled deliberately rather than
        // silently dropped. This is an ERROR (a translation gap is a modeling defect). This
        // presence/completeness check is intended to generalize to other processor kinds later.
        adaptor.handlers.filter(_.clauses.nonEmpty).foreach { handler =>
          if !handler.clauses.exists(_.isInstanceOf[OnOtherClause]) then
            messages.addError(
              handler.errorLoc,
              s"${handler.identify} in ${adaptor.identify} has no 'on other' clause; an adaptor must handle unmatched messages explicitly",
              suggestion =
                "Add an 'on other' clause to the adaptor's handler to handle messages it does not explicitly translate."
            )
        }
        // Check if adaptor handlers reference message types from the adapted context
        resolvePath[Context](adaptor.referent.pathId, parents).foreach { targetContext =>
          val targetMessageTypes = targetContext.types.filter { t =>
            t.typEx match {
              case auc: AggregateUseCaseTypeExpression =>
                auc.usecase == AggregateUseCase.CommandCase ||
                auc.usecase == AggregateUseCase.EventCase ||
                auc.usecase == AggregateUseCase.QueryCase ||
                auc.usecase == AggregateUseCase.ResultCase
              case _ => false
            }
          }
          if targetMessageTypes.nonEmpty && adaptor.handlers.nonEmpty then {
            val allClauses = adaptor.handlers.flatMap(_.clauses).collect {
              case omc: OnMessageLikeClause => omc
            }
            if allClauses.nonEmpty then {
              // Use parent-independent lookup since the resolution
              // pass keyed refs under the on-clause's parent,
              // not the Adaptor's parent
              def resolveClauseType(omc: OnMessageLikeClause): Option[Type] =
                resolution.refMap.definitionOf[Type](omc.msg.pathId)

              val referencesTargetType = allClauses.exists { omc =>
                resolveClauseType(omc).exists { resolvedType =>
                  symbols.parentsOf(resolvedType).exists(_ == targetContext)
                }
              }
              if !referencesTargetType then {
                messages.addWarning(
                  adaptor.errorLoc,
                  s"${adaptor.identify} is ${adaptor.direction.format} ${targetContext.identify} but its handlers do not reference any message types defined in ${targetContext.identify}",
                  suggestion =
                    s"Reference message types from ${targetContext.identify} in the adaptor's on-clauses."
                )
              }
              // Check direction-specific message kind compatibility
              allClauses.foreach { omc =>
                resolveClauseType(omc).foreach { resolvedType =>
                  if symbols.parentsOf(resolvedType).exists(_ == targetContext) then
                    adaptor.direction match {
                      case _: InboundAdaptor =>
                        omc.msg.messageKind match {
                          case AggregateUseCase.CommandCase | AggregateUseCase.QueryCase =>
                            messages.addError(
                              omc.errorLoc,
                              s"Inbound ${adaptor.identify} handles ${omc.msg.messageKind} '${omc.msg.pathId.value.mkString(".")}' from ${targetContext.identify}, but inbound adaptors should handle events and results (the target's output)",
                              suggestion =
                                "Inbound adaptors should handle the target's output (events and results). Move command/query handling to an outbound adaptor."
                            )
                          case _ => ()
                        }
                      case _: OutboundAdaptor =>
                        omc.msg.messageKind match {
                          case AggregateUseCase.EventCase | AggregateUseCase.ResultCase =>
                            messages.addError(
                              omc.errorLoc,
                              s"Outbound ${adaptor.identify} handles ${omc.msg.messageKind} '${omc.msg.pathId.value.mkString(".")}' from ${targetContext.identify}, but outbound adaptors should handle commands and queries (the target's input)",
                              suggestion =
                                "Outbound adaptors should handle the target's input (commands and queries). Move event/result handling to an inbound adaptor."
                            )
                          case _ => ()
                        }
                    }
                }
              }
            }
          }
        }
        // A4: Isolation-seam check. An adaptor bridges exactly two contexts — its parent context
        // `c` and its `referent` context. Every message it traffics in must belong to one of those
        // two contexts (or be a context-less root/shared type). A message owned by any THIRD context
        // crosses the adaptor's isolation seam, since the adaptor is the ONLY sanctioned crossing
        // point between contexts. We inspect both surfaces where the adaptor names a message:
        //   - the `on <message>` clause message (the input it consumes), and
        //   - every `send`/`tell` statement's message target (the translated output it emits).
        // Emitted as an Error: the adaptor is the only sanctioned context crossing, so a
        // third-context reference is a hard modeling error. This replaces the generic cross-context
        // reference check in BasicValidation, which is intentionally disabled inside adaptors so
        // this seam-aware check governs adaptors — avoiding a double-report.
        resolvePath[Context](adaptor.referent.pathId, parents).foreach { referentContext =>
          // Gather every message reference the adaptor traffics in: on-clause messages plus the
          // message targets of send/tell statements (walking nested when/match/foreach bodies).
          val onClauseRefs: Seq[MessageRef] =
            adaptor.handlers.flatMap(_.clauses).collect { case omc: OnMessageLikeClause => omc.msg }
          val sendTellRefs = scala.collection.mutable.ArrayBuffer.empty[MessageRef]
          adaptor.handlers.foreach { handler =>
            handler.clauses.foreach { clause =>
              walkStatements(clause.contents) {
                case SendStatement(_, mr: MessageRef, _) => sendTellRefs.append(mr)
                case SendStatement(_, ctor: Constructor, _) =>
                  ctor.ref match
                    case mr: MessageRef => sendTellRefs.append(mr)
                    case _              => ()
                case TellStatement(_, mr: MessageRef, _, _) => sendTellRefs.append(mr)
                case TellStatement(_, ctor: Constructor, _, _) =>
                  ctor.ref match
                    case mr: MessageRef => sendTellRefs.append(mr)
                    case _              => ()
                case _ => ()
              }
            }
          }
          (onClauseRefs ++ sendTellRefs.toSeq).foreach { msgRef =>
            // Skip unresolved refs — other checks report those; we must not NPE on them.
            resolution.refMap.definitionOf[Type](msgRef.pathId).foreach { resolvedType =>
              // A context-less (root/domain-level shared) type has no owning context — allowed.
              symbols.contextOf(resolvedType).foreach { owningContext =>
                if owningContext != c && owningContext != referentContext then
                  messages.addError(
                    msgRef.loc,
                    s"Adaptor '${adaptor.id.value}' references message '${msgRef.pathId.value
                        .mkString(".")}' from context '${owningContext.id.value}', which is neither " +
                      s"its parent context '${c.id.value}' nor its referent context " +
                      s"'${referentContext.id.value}'; this crosses the adaptor's isolation seam",
                    suggestion = "Keep the adaptor's translation within the two contexts it bridges: reference " +
                      "only messages owned by its parent context or its referent context (or shared " +
                      "root-level types). Route a third context's messages through its own adaptor."
                  )
              }
            }
          }
        }
      case Some(_: Module) =>
        // S61-1: a Module is a FLAT collection of any top-level definition, so an adaptor may sit
        // directly in one. The context-pairing checks above need a parent Context to compare
        // against and simply do not apply here; the adaptor's own internal rules still do.
        checkContainer(parents, adaptor)
      case None | Some(_) =>
        messages.addError(
          adaptor.errorLoc,
          "Adaptor not contained within Context",
          suggestion = "Define the adaptor inside a context or a module."
        )
    }
  }

  /** Task 10 (A32): validate a processor's ascribed stream shape against its arity, and nudge when
    * a ported processor omits an ascription. Runs for every processor kind via `process`.
    *   - `ascribedShape` present but its canonical shape disagrees with the arity-derived shape ->
    *     an Error naming the ascription, the arity, and the derived shape.
    *   - `ascribedShape` absent and the processor declares at least one port -> a suppressible
    *     StyleWarning (gated by `showStyleWarnings` in the message accumulator).
    *   - A portless processor with no ascription emits nothing.
    */
  private def validateProcessorShape(processor: Processor[?]): Unit = {
    val numOutlets = processor.outlets.size
    val numInlets = processor.inlets.size
    processor.ascribedShape match {
      // A portless processor (0 inlets, 0 outlets) is an incomplete placeholder, not a
      // contradiction: it is flagged elsewhere as "should have content". Only compare the
      // ascription against the arity once at least one port is declared.
      case Some(ascribed) if numOutlets + numInlets >= 1 =>
        val derived = processor.arityShape
        // An `error-sink` inlet is infrastructure rather than dataflow, so a processor may be
        // read either way: WITH it (a dedicated `as sink` receiver whose only inlet is the error
        // sink) or WITHOUT it (an `as flow` that also happens to host its domain's sink). Accept
        // whichever the author ascribed. riddl-models had to move api-management's sink to a
        // sibling context because only the first reading was allowed -- there is nothing wrong
        // with an inlet on a flow.
        val derivedWithoutErrorSinks =
          processor.shapeForArity(numOutlets, processor.dataflowInlets.size)
        val matchesEitherReading =
          ascribed.keyword == derived.keyword ||
            ascribed.keyword == derivedWithoutErrorSinks.keyword
        if !matchesEitherReading then
          messages.addError(
            processor.errorLoc,
            s"${processor.identify} is ascribed 'as ${ascribed.keyword}' but its arity " +
              s"($numOutlets outlets, $numInlets inlets) is ${derived.keyword}",
            suggestion =
              s"Change the ascription to 'as ${derived.keyword}', or adjust the inlets/outlets so the " +
                s"arity matches 'as ${ascribed.keyword}'."
          )
      case Some(_) => () // ascribed shape but no ports yet: incomplete, handled elsewhere
      case None =>
        if numOutlets + numInlets >= 1 then
          messages.addStyle(
            processor.errorLoc,
            s"${processor.identify} has ports but no 'as <shape>' ascription; consider adding one " +
              "(it documents intent and is validated)",
            suggestion =
              s"Add 'as ${processor.arityShape.keyword}' to ${processor.identify} to document and " +
                "validate its stream shape."
          )
    }
  }

  private def validateStreamlet(
    streamlet: Streamlet,
    parents: Parents
  ): Unit = {
    // NOT registered into the streaming graph here — `process` does that for every Processor kind.
    checkContainer(parents, streamlet)
    if streamlet.nonEmpty then
      val numInlets = streamlet.inlets.size
      val numOutlets = streamlet.outlets.size
      streamlet.effectiveShape match {
        case _: Source =>
          check(
            numInlets == 0,
            s"${streamlet.identify} is a source but has $numInlets inlets; sources must have none",
            Messages.Error,
            streamlet.errorLoc,
            suggestion = "Remove the inlets from the source; sources only produce data."
          )
          check(
            numOutlets >= 1,
            s"${streamlet.identify} is a source but has no outlets; sources must have at least one",
            Messages.Error,
            streamlet.errorLoc,
            suggestion = "Add at least one outlet to the source so it can emit data."
          )
        case _: Sink =>
          check(
            numInlets >= 1,
            s"${streamlet.identify} is a sink but has no inlets; sinks must have at least one",
            Messages.Error,
            streamlet.errorLoc,
            suggestion = "Add at least one inlet to the sink so it can receive data."
          )
          check(
            numOutlets == 0,
            s"${streamlet.identify} is a sink but has $numOutlets outlets; sinks must have none",
            Messages.Error,
            streamlet.errorLoc,
            suggestion = "Remove the outlets from the sink; sinks only consume data."
          )
        case _: Flow =>
          check(
            numInlets >= 1,
            s"${streamlet.identify} is a flow but has no inlets; flows must have at least one",
            Messages.Error,
            streamlet.errorLoc,
            suggestion = "Add at least one inlet to the flow."
          )
          check(
            numOutlets >= 1,
            s"${streamlet.identify} is a flow but has no outlets; flows must have at least one",
            Messages.Error,
            streamlet.errorLoc,
            suggestion = "Add at least one outlet to the flow."
          )
        case _: Merge =>
          check(
            numInlets >= 2,
            s"${streamlet.identify} is a merge but has $numInlets inlets; merges must have at least two",
            Messages.Error,
            streamlet.errorLoc,
            suggestion = "Give the merge at least two inlets."
          )
          check(
            numOutlets >= 1,
            s"${streamlet.identify} is a merge but has no outlets; merges must have at least one",
            Messages.Error,
            streamlet.errorLoc,
            suggestion = "Add at least one outlet to the merge."
          )
        case _: Split =>
          check(
            numInlets >= 1,
            s"${streamlet.identify} is a split but has no inlets; splits must have at least one",
            Messages.Error,
            streamlet.errorLoc,
            suggestion = "Add at least one inlet to the split."
          )
          check(
            numOutlets >= 2,
            s"${streamlet.identify} is a split but has $numOutlets outlets; splits must have at least two",
            Messages.Error,
            streamlet.errorLoc,
            suggestion = "Give the split at least two outlets."
          )
        case _: Router =>
          check(
            numInlets >= 2,
            s"${streamlet.identify} is a router but has $numInlets inlets; routers must have at least two",
            Messages.Error,
            streamlet.errorLoc,
            suggestion = "Give the router at least two inlets."
          )
          check(
            numOutlets >= 2,
            s"${streamlet.identify} is a router but has $numOutlets outlets; routers must have at least two",
            Messages.Error,
            streamlet.errorLoc,
            suggestion = "Give the router at least two outlets."
          )
        case _: Void => ()
      }
    end if
    if streamlet.handlers.isEmpty && streamlet.nonEmpty then
      messages.addMissing(
        streamlet.errorLoc,
        s"${streamlet.identify} should have a handler",
        suggestion = s"Add a handler to ${streamlet.identify} to process streamed messages."
      )
    // Completeness: Flow/Split/Router handlers should send to their outlets
    if streamlet.nonEmpty && streamlet.handlers.nonEmpty then {
      streamlet.effectiveShape match {
        case _: Flow | _: Split | _: Router =>
          val allSends = streamlet.handlers.flatMap { handler =>
            val finder = Finder(handler)
            finder.recursiveFindByType[SendStatement]
          }
          if allSends.isEmpty then {
            messages.addCompleteness(
              streamlet.errorLoc,
              s"${streamlet.identify} handlers do not send any messages to its outlets",
              suggestion =
                "Add 'send' statements to the handler so the streamlet emits to its outlets."
            )
          }
        case _: Source =>
          // Source has no inlets, so it must generate data via on-init or on-other
          val hasInitOrOther = streamlet.handlers.exists { handler =>
            handler.clauses.exists {
              case _: OnInitializationClause => true
              case _: OnOtherClause          => true
              case _                         => false
            }
          }
          if !hasInitOrOther then {
            messages.addCompleteness(
              streamlet.errorLoc,
              s"${streamlet.identify} is a source but has no 'on init' or 'on other' clause to generate data",
              suggestion = "Add an 'on init' or 'on other' clause so the source generates data."
            )
          }
        case _ => ()
      }
    }
  }

  /** At most ONE `option error-sink` inlet per domain.
    *
    * The option names the inlet that receives hard-error notifications. There is NO predefined
    * receiver -- an `Operations` context belongs in the model that wants one -- so this option is
    * the only thing that says where they go. Two in one domain leave a generator with no way to
    * choose, so it is an ERROR for the same reason duplicate adaptors are.
    *
    * Scoped to the DOMAIN, not the model: several across domains is correct and intended, so that
    * unrelated concerns do not share an alert stream. Deployment multiplicity -- a sink per site or
    * region -- is the generator's and the operator's business, not riddlc's.
    *
    * "Domain" means the NEAREST enclosing one, subdomains included. Counting a subdomain's sink
    * against its parent made the two checks here contradict each other for nested domains -- see
    * [[errorSinksDeclaredIn]].
    */
  /** The `error-sink` inlets a domain declares ITSELF, not counting its subdomains'.
    *
    * A plain recursive find crosses nested `Domain` boundaries, which made the two checks below
    * contradict each other: the missing check named a subdomain as a domain in its own right while
    * the uniqueness check folded that subdomain's sink into the root's count, so a model with
    * nested domains could satisfy neither (reported by riddl-models against 2.0.0-rc.8). A
    * subdomain is a domain; it owns its sink.
    *
    * Descent continues through Include and every other container -- an `include`d file's contents
    * belong to the domain that included them, which is exactly how the reported model is written.
    */
  private def ownContentsOf(domain: Domain): Seq[RiddlValue] =
    def walk[CV <: RiddlValue](values: Contents[CV]): Seq[RiddlValue] =
      values.toSeq.flatMap {
        case _: Domain               => Seq.empty // a subdomain owns its own
        case container: Container[?] => container +: walk(container.contents)
        case value                   => Seq(value)
      }
    walk(domain.contents)
  end ownContentsOf

  private def errorSinksDeclaredIn(domain: Domain): Seq[Inlet] =
    ownContentsOf(domain).collect { case inlet: Inlet if isErrorSink(inlet) => inlet }

  /** Does this domain have anything of its OWN that could produce a hard error?
    *
    * A domain holding only subdomains, types or authors has nowhere to put an inlet without
    * inventing a context, and nothing of its own that can fail at run time, so asking it for an
    * error-sink is noise. Its subdomains are asked individually, which is where the processors
    * actually are.
    */
  private def hasOwnProcessors(domain: Domain): Boolean =
    ownContentsOf(domain).exists(_.isInstanceOf[Processor[?]])

  private def isErrorSink(inlet: Inlet): Boolean =
    inlet.metadata.filter[OptionValue].exists(_.name == "error-sink")

  private def checkErrorSinkUniqueness(domain: Domain, parents: Parents): Unit =
    val sinks = errorSinksDeclaredIn(domain)
    // A domain with no error-sink has nowhere for hard errors to go, and there is no predefined
    // fallback: `option error-sink` names the destination and NOTHING else does. A generator
    // asked to emit a failure path for such a model has to invent one or refuse -- riddl-gen
    // refuses -- so say it here, where it is cheap to fix, rather than at generation time.
    //
    // A MISSING warning, deliberately, not a Completeness one. `isIgnorable` is defined as
    // `severity < CompletenessWarning`, so Completeness asserts the model is structurally
    // incomplete -- unfed inlets, unreachable sinks, that family. A model with no error-sink is
    // not incomplete in that sense; it has simply not SAID where hard errors go, which is the
    // same kind of omission as "has no author" or "should have a description". Raising it to
    // Completeness made thirteen unrelated suites red for models that were otherwise fine, and
    // that was the tell.
    //
    // An ANCESTOR domain's sink satisfies a subdomain. A root that declares one destination for
    // its whole tree is the common case and a reasonable thing to say; requiring every subdomain
    // to declare its own would force four alert destinations on a model that wants one. A
    // subdomain may still declare its own, and then the NEAREST one wins -- ordinary lexical
    // scoping, and unambiguous because each domain permits only one.
    val inheritsSink = parents.exists {
      case ancestor: Domain => errorSinksDeclaredIn(ancestor).nonEmpty
      case _                => false
    }
    // Only a LEAF domain is asked. A domain containing subdomains is a scoping and sharing
    // construct -- it groups, and it holds types the subdomains share -- so the work that can
    // actually fail lives in the leaves, and they are asked individually. Asking a grouping
    // domain as well would double-report the same subtree.
    val isLeafDomain = domain.domains.isEmpty
    if sinks.isEmpty && !inheritsSink && isLeafDomain && hasOwnProcessors(domain) then
      messages.addMissing(
        domain.errorLoc,
        s"${domain.identify} declares no 'error-sink' inlet, so hard errors have no destination",
        suggestion = "Mark the inlet that should receive hard-error notifications with " +
          "'option error-sink', e.g. 'inlet Alerts is command OpsAlert with { option error-sink }'. " +
          "A sink on an enclosing domain covers its subdomains."
      )
    end if
    if sinks.sizeIs > 1 then
      val first = sinks.head
      sinks.tail.foreach { dupe =>
        messages.addError(
          dupe.errorLoc,
          s"${dupe.identify} is a second 'error-sink' in ${domain.identify}; " +
            s"${first.identify} already claims it",
          suggestion = "Keep one error-sink inlet per domain, or move the second to a domain " +
            "of its own. Two leave a generator no way to choose between them."
        )
      }
    end if
    sinks.foreach(checkErrorSinkAcceptsGeneratorError)
  end checkErrorSinkUniqueness

  /** An `error-sink` inlet must accept [[PredefinedModule.generatorError]].
    *
    * That is what a generator SENDS: `GeneratorError` is the shared shape, filled with no knowledge
    * of the model. An inlet marked `error-sink` but typed by something else is a destination a
    * generator cannot deliver to, so it is an ERROR rather than a nudge.
    *
    * An ALTERNATION including `GeneratorError` is equally acceptable, and is the point of allowing
    * one: a model may route its own error messages to the same inlet as the generator's, so the
    * operator has one place to look.
    */
  private def checkErrorSinkAcceptsGeneratorError(inlet: Inlet): Unit =
    resolution.refMap.definitionOf[Type](inlet.type_.pathId) match
      case None => () // an unresolved type is reported elsewhere; do not pile on
      case Some(typ) =>
        val accepts =
          typ.id.value == PredefinedModule.generatorError || (typ.typEx match
            case alt: Alternation =>
              alt.of.toSeq.exists(
                _.pathId.value.lastOption.contains(PredefinedModule.generatorError)
              )
            case _ => false
          )
        check(
          accepts,
          s"${inlet.identify} is marked 'error-sink' but does not accept " +
            s"${PredefinedModule.generatorError}",
          Messages.Error,
          inlet.errorLoc,
          suggestion =
            s"Type the inlet by ${PredefinedModule.generatorError}, or by an alternation " +
              s"that includes it if the inlet also carries the model's own error messages. " +
              s"${PredefinedModule.generatorError} is what generators send."
        )
  end checkErrorSinkAcceptsGeneratorError

  private def validateDomain(
    domain: Domain,
    parents: Parents
  ): Unit = {
    checkContainer(parents, domain)
    checkErrorSinkUniqueness(domain, parents)
    check(
      domain.domains.isEmpty || domain.domains.size > 2,
      "Singly nested domains do not add value",
      StyleWarning,
      domain.errorLoc,
      suggestion =
        "Merge the single nested domain into its parent, or add sibling domains to justify the nesting."
    )
    // A48: a Domain should identify an author, either directly (an author reference or a defined
    // author) or inherited from an enclosing domain. MissingWarning, so it is suppressible via the
    // existing showMissingWarnings / noMinorWarnings gates (no new option). Scoped to Domain only.
    def hasAuthorInfo(d: Domain): Boolean = d.authorRefs.nonEmpty || d.authors.nonEmpty
    val inheritedAuthor = parents.collect { case d: Domain => d }.exists(hasAuthorInfo)
    check(
      hasAuthorInfo(domain) || inheritedAuthor,
      s"${domain.identify} has no author",
      MissingWarning,
      domain.errorLoc,
      suggestion =
        s"Identify an author for ${domain.identify}, e.g. 'by author Name', or define one in an " +
          "enclosing domain."
    )
  }

  private def validateSaga(
    saga: Saga,
    parents: Parents
  ): Unit = {
    checkContainer(parents, saga)
    check(
      saga.nonEmpty && saga.sagaSteps.size >= 2,
      "Sagas must define at least 2 steps",
      Messages.Error,
      saga.errorLoc,
      suggestion =
        "Define at least two saga steps so the saga coordinates a multi-step transaction."
    )
    check(
      saga.nonEmpty && saga.sagaSteps.size >= 2 && saga.sagaSteps.map(_.id.value).allUnique,
      "Saga step names must all be distinct",
      Messages.Error,
      saga.errorLoc,
      suggestion = "Give each saga step a unique name."
    )
    // A9: validate saga requires/returns (previously unvalidated).
    saga.input.foreach(validateRequiresReturns(_, saga, parents))
    saga.output.foreach(validateRequiresReturns(_, saga, parents))
    // A8: a saga may only orchestrate definitions WITHIN its own enclosing domain. Each step's
    // do-statements drive commands/entities/etc; every such reference that resolves to a definition
    // owned by a DIFFERENT domain crosses the saga's domain boundary and is an Error. Unresolved
    // refs are skipped (other checks report those). A referent with no owning domain (a root/domain-
    // level shared definition) is allowed. Mirrors A4's isolation-seam approach (resolve the ref,
    // find the referent's owning domain, compare) using the parent-walk `domainOf` from the
    // connector-placement check.
    domainOf(saga).foreach { ownDomain =>
      saga.sagaSteps.foreach { step =>
        walkStatements(step.doStatements) { stmt =>
          statementReferencedDefs(stmt).foreach { case (pid, referent) =>
            domainOf(referent).foreach { otherDomain =>
              if otherDomain ne ownDomain then
                messages.addError(
                  pid.loc,
                  s"saga '${saga.id.value}' step references '${pid.format}' in domain " +
                    s"'${otherDomain.id.value}', outside its own domain '${ownDomain.id.value}'; " +
                    "a saga may only orchestrate within its enclosing domain",
                  suggestion =
                    s"Reference only definitions within domain '${ownDomain.id.value}', " +
                      s"or move the saga into domain '${otherDomain.id.value}'. Coordinate across " +
                      "domains through their contexts' adaptors rather than a single saga."
                )
            }
          }
        }
      }
    }
  }

  /** A8: the nearest enclosing [[Domain]] of a definition (its owning domain), or None if the
    * definition sits at root/Nebula level with no owning domain. Same parent-walk as the
    * connector-placement check's local `domainOf`.
    */
  private def domainOf(d: Definition): Option[Domain] =
    symbols.parentsOf(d).collectFirst { case dom: Domain => dom }

  /** A8: the definitions a single saga-step statement references, paired with the
    * [[PathIdentifier]] that named each (for error location/text). Covers the message targets of
    * send/tell/yield, the entity/state of morph, the entity/handler of become, the output of put,
    * plus every embedded `call` function and `get from input/state` in the statement's value
    * expressions. Each ref is resolved by its concrete expected type via the (parent-independent)
    * by-path lookup, exactly as A4 resolves adaptor message refs; unresolved refs simply don't
    * appear in the result.
    */
  private def statementReferencedDefs(s: Statement): Seq[(PathIdentifier, Definition)] =
    def msgRefs(m: MessageRef | Constructor | ValueRef): Seq[(PathIdentifier, Definition)] = m match
      case mr: MessageRef =>
        resolution.refMap.definitionOf[Type](mr.pathId).map(mr.pathId -> _).toSeq
      // A56: a bound operand contributes the message Type it resolves to, keyed by the binding's
      // own path -- which is the key ResolutionPass registered for it.
      case vr: ValueRef =>
        resolution.refMap.definitionOf[Type](vr.path).map(vr.path -> _).toSeq
      case c: Constructor =>
        val direct = c.ref match
          case mr: MessageRef => resolution.refMap.definitionOf[Type](mr.pathId).map(mr.pathId -> _)
          case rr: RecordRef  => resolution.refMap.definitionOf[Type](rr.pathId).map(rr.pathId -> _)
        direct.toSeq ++ c.args.flatMap(a => valueReferencedDefs(a.value))
    s match
      case snd: SendStatement  => msgRefs(snd.msg)
      case tel: TellStatement  => msgRefs(tel.msg)
      case yld: YieldStatement => msgRefs(yld.msg)
      case rpl: ReplyStatement => msgRefs(rpl.msg)
      case mor: MorphStatement =>
        resolution.refMap
          .definitionOf[Entity](mor.entity.pathId)
          .map(mor.entity.pathId -> _)
          .toSeq ++
          resolution.refMap
            .definitionOf[State](mor.state.pathId)
            .map(mor.state.pathId -> _)
            .toSeq ++
          valueReferencedDefs(mor.value)
      case bec: BecomeStatement =>
        resolution.refMap
          .definitionOf[Entity](bec.entity.pathId)
          .map(bec.entity.pathId -> _)
          .toSeq ++
          resolution.refMap
            .definitionOf[Handler](bec.handler.pathId)
            .map(bec.handler.pathId -> _)
            .toSeq
      case put: PutStatement =>
        resolution.refMap
          .definitionOf[Output](put.output.pathId)
          .map(put.output.pathId -> _)
          .toSeq ++
          valueReferencedDefs(put.value)
      case set: SetStatement    => valueReferencedDefs(set.value)
      case let: LetStatement    => valueReferencedDefs(let.expression)
      case ret: ReturnStatement => valueReferencedDefs(ret.value)
      // A70/instance-identity: the processor a `terminate` ends is exactly the kind of reference
      // A8 exists to catch -- a saga step ending an instance owned by a DIFFERENT domain crosses
      // the same boundary a cross-domain `tell` does.
      case term: TerminateStatement =>
        resolution.refMap
          .definitionOf[Processor[?]](term.processor.pathId)
          .map(term.processor.pathId -> _)
          .toSeq ++ term.args.flatMap(a => valueReferencedDefs(a.value))
      case _ => Seq.empty
  end statementReferencedDefs

  /** A8: the definitions referenced inside a value expression — each `call function F(…)` (a
    * [[Function]]) and each `get from input/state <ref>` (an [[Input]] or [[State]]) — recursing
    * through constructor arguments and the boolean-expression sub-language. Companion to
    * [[statementReferencedDefs]].
    */
  private def valueReferencedDefs(v: RiddlValue): Seq[(PathIdentifier, Definition)] = v match
    case call: Call =>
      resolution.refMap
        .definitionOf[Function](call.function.pathId)
        .map(call.function.pathId -> _)
        .toSeq ++
        call.args.flatMap(a => valueReferencedDefs(a.value))
    case gv: GetValue =>
      gv.source match
        case ir: InputRef =>
          resolution.refMap.definitionOf[Input](ir.pathId).map(ir.pathId -> _).toSeq
        case sr: StateRef =>
          resolution.refMap.definitionOf[State](sr.pathId).map(sr.pathId -> _).toSeq
    case c: Constructor           => c.args.flatMap(a => valueReferencedDefs(a.value))
    case le: LogicalExpression    => valueReferencedDefs(le.left) ++ valueReferencedDefs(le.right)
    case ne: NotExpression        => valueReferencedDefs(ne.expr)
    case ce: ComparisonExpression => valueReferencedDefs(ce.left) ++ valueReferencedDefs(ce.right)
    case _                        => Seq.empty
  end valueReferencedDefs

  private def validateSagaStep(
    s: SagaStep,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, s)
    checkNonEmpty(s.doStatements.toSeq, "Do Statements", s, MissingWarning)
    checkNonEmpty(s.undoStatements.toSeq, "Revert Statements", s, MissingWarning)
    // A23: refusals must precede any effect in the do-step's statement list (undo/compensation is
    // NOT checked — it has different, compensation semantics and is out of A23's scope).
    checkRefusalsFirst(s.doStatements.toSeq.collect { case st: Statement => st })
    check(
      s.doStatements.nonEmpty == s.undoStatements.nonEmpty,
      "A saga step with do statements must also have revert statements, and vice versa",
      Messages.Error,
      s.errorLoc,
      suggestion =
        "Provide both 'do' and 'revert' statements for the saga step so its action can be compensated on failure."
    )
    if s.doStatements.nonEmpty && s.undoStatements.nonEmpty then {
      val doTargets = mutable.Set.empty[String]
      walkStatements(s.doStatements) {
        case t: TellStatement   => doTargets += t.processorRef.pathId.format
        case snd: SendStatement => doTargets += snd.portlet.pathId.format
        case _                  => ()
      }
      if doTargets.nonEmpty then {
        val undoTargets = mutable.Set.empty[String]
        walkStatements(s.undoStatements) {
          case t: TellStatement   => undoTargets += t.processorRef.pathId.format
          case snd: SendStatement => undoTargets += snd.portlet.pathId.format
          case _                  => ()
        }
        val uncompensated = doTargets.toSet -- undoTargets
        if uncompensated.nonEmpty then
          messages.add(
            Messages.style(
              s"${s.identify} do-step targets ${uncompensated.mkString(", ")} but the undo-step does not target the same; consider adding compensation",
              s.errorLoc,
              suggestion =
                s"Add compensating revert statements targeting ${uncompensated.mkString(", ")} in the saga step's undo block."
            )
          )
      }
    }
    // Completeness: saga steps should have tell command statements
    if s.doStatements.nonEmpty then {
      var hasTellCommand = false
      walkStatements(s.doStatements) {
        case t: TellStatement if operandMessageKind(t.msg).contains(AggregateUseCase.CommandCase) =>
          hasTellCommand = true
        case _ => ()
      }
      if !hasTellCommand then {
        messages.addCompleteness(
          s.errorLoc,
          s"${s.identify} do-statements contain no 'tell command' to effect state changes",
          suggestion =
            "Add a 'tell command' statement to the saga step's do-statements to effect a state change."
        )
      }
    }
    // A12: a saga step's do-block is all-or-nothing (undo assumes all-or-none of it happened), so it
    // must have AT MOST ONE potential failure point. Count statement-level failure points
    // (send/tell/yield/put via Statement.canFail) plus every embedded Call/GetValue in value
    // expressions, across nested when/match/foreach bodies (walkStatements recurses into those).
    // A saga may not 'ask', not even as a value (Reid, 2026-08-10). A saga must not depend on
    // dynamic state, or the same inputs could yield different transaction results at different
    // times; and compensation cannot re-read what the forward action saw. Checked across BOTH
    // do- and undo-statements, since a revert that reads dynamic state has exactly the problem
    // the rule exists to prevent.
    val asks: Seq[Ask] =
      val found = mutable.ListBuffer.empty[Ask]
      walkStatements(s.doStatements) { st => statementValues(st).foreach(v => found ++= asksIn(v)) }
      walkStatements(s.undoStatements) { st =>
        statementValues(st).foreach(v => found ++= asksIn(v))
      }
      found.toSeq
    asks.foreach { ask =>
      messages.addError(
        ask.loc,
        s"saga step '${s.id.value}' may not 'ask'; a saga must not depend on dynamic state, or " +
          "the same inputs could yield different transaction results at different times",
        suggestion =
          "Acquire the value in a handler and pass it into the saga through the saga's 'requires', " +
            "so the saga is closed over its inputs and compensation sees the same data the " +
            "forward action saw."
      )
    }
    // A12: a saga step's do-block is all-or-nothing (undo assumes all-or-none of it happened), so it
    // must have AT MOST ONE potential failure point. Count statement-level failure points
    // (send/tell/yield/put via Statement.canFail) plus every embedded Call/GetValue in value
    // expressions, across nested when/match/foreach bodies (walkStatements recurses into those).
    //
    // SKIPPED when the step contains an 'ask'. An `ask` is itself a failure point, so it always
    // pushes a conforming step over budget -- and this message's remedy ("split into multiple
    // steps") produces an ask-only step, which then fails the mandatory-'tell' rule instead. The
    // advice could not be taken, so the ask error above stands alone.
    if s.doStatements.nonEmpty && asks.isEmpty then {
      var failPoints = 0
      walkStatements(s.doStatements) { st => failPoints += countStatementFailPoints(st) }
      if failPoints > 1 then
        messages.add(
          Messages.warning(
            s"saga step '${s.id.value}' has $failPoints potential failure points in its do-block; " +
              "a step's do/undo is all-or-nothing, so it should have at most one — split into " +
              "multiple steps",
            s.errorLoc,
            suggestion =
              "Split the saga step so each step's do-block has at most one potential failure point " +
                "(send/tell/yield/put, or an embedded call/get)."
          )
        )
    }
    checkMetadata(s)
  }

  /** A37: enforce the intention rules that are local to a single context (shape ascription and the
    * UI-group gate). The connector-dependent rules (external-context persistence and the adaptor
    * advisory) live in [[StreamingValidation.checkExternalContextConnectors]] where connector
    * endpoints are already resolved.
    */
  private def validateIntention(c: Context): Unit = {
    c.intention match
      case Some(Intention.Service) if c.effectiveShape.keyword != "flow" =>
        messages.addError(
          c.errorLoc,
          s"Service context '${c.id.value}' must have a flow shape (1 inlet, 1 outlet) " +
            s"but is ${c.effectiveShape.keyword}",
          suggestion =
            s"Give ${c.identify} exactly one inlet and one outlet (or ascribe 'as flow'); a " +
              s"service exposes a single request/response flow."
        )
      case Some(Intention.Gateway) if c.effectiveShape.keyword != "merge" =>
        messages.addError(
          c.errorLoc,
          s"Gateway context '${c.id.value}' must have a merge shape (>=2 inlets, 1 outlet) " +
            s"but is ${c.effectiveShape.keyword}",
          suggestion =
            s"Give ${c.identify} two or more inlets and a single outlet (or ascribe 'as merge'); " +
              s"a gateway funnels several inputs into one."
        )
      case _ => ()
    end match
    // A41: UI groups (and, transitively, the Inputs/Outputs inside them) require an application
    // intention. This is a hard error for ANY non-application context, including one with no
    // declared intention — a group-bearing context must opt in with 'application context'.
    if c.groups.nonEmpty && !c.intention.contains(Intention.Application) then
      val intentionStr = c.intention.map(_.keyword).getOrElse("none")
      messages.addError(
        c.errorLoc,
        s"Only application-intended contexts may contain UI groups; context '${c.id.value}' " +
          s"has intention $intentionStr",
        suggestion =
          s"Either mark ${c.identify} as an 'application context' or move its UI groups into an " +
            s"application-intended context."
      )
    end if
  }

  /** At most ONE adaptor per (this context, referenced context, DIRECTION).
    *
    * Two adaptors in the same context adapting the same direction to the same foreign context split
    * that context's translation across two places, and nothing says which one handles a given
    * message -- an ERROR, because the ambiguity has no defensible resolution.
    *
    * DIRECTION is part of the key on purpose. The computational model §7.1 is explicit that "a
    * bidirectional relationship is two adaptors", and an [[AST.Adaptor]] carries exactly one
    * direction, so an inbound and an outbound adaptor between the same pair is the SANCTIONED way
    * to say "both ways" -- not duplication. Likewise an adaptor in A referencing B and one in B
    * referencing A are different owning contexts, each defending its own model, and are both legal.
    *
    * Keyed on the RESOLVED context where resolution succeeded, so two different path expressions
    * naming the same context are still caught; unresolved refs fall back to the path text, since a
    * resolution failure is reported elsewhere and should not also suppress this check.
    */
  private def checkAdaptorUniqueness(c: Context): Unit =
    val adaptors = c.adaptors
    if adaptors.sizeIs > 1 then
      adaptors
        .groupBy { a =>
          val target: String = resolution.refMap
            .definitionOf[Context](a.referent.pathId)
            .map(ctx => symbols.pathOf(ctx).mkString("."))
            .getOrElse(a.referent.pathId.format)
          target -> a.direction.format
        }
        .collect { case (_, dupes) if dupes.sizeIs > 1 => dupes }
        .foreach { dupes =>
          val first = dupes.head
          dupes.tail.foreach { dupe =>
            messages.addError(
              dupe.errorLoc,
              s"${dupe.identify} duplicates ${first.identify}: ${c.identify} already adapts " +
                s"${first.direction.format} ${first.referent.format}",
              suggestion = s"Merge the handlers of ${dupe.identify} into ${first.identify}. " +
                "A context may adapt to and from another context, but only once in each " +
                "direction, or it is ambiguous which adaptor handles a given message."
            )
          }
        }
    end if
  end checkAdaptorUniqueness

  private def validateContext(
    c: Context,
    parents: Parents
  ): Unit = {
    checkContainer(parents, c)
    validateIntention(c)
    checkAdaptorUniqueness(c)
    val nonEmptyEntities = c.entities.filter(_.nonEmpty)
    if nonEmptyEntities.nonEmpty && c.nonEmpty then {
      // Completeness 4i: context with entities must have a Sink
      val hasSinkOrInlet = c.streamlets.exists(_.inlets.nonEmpty)
      if !hasSinkOrInlet then {
        messages.addCompleteness(
          c.errorLoc,
          s"${c.identify} has entities but no Sink streamlet to receive and dispatch incoming messages",
          suggestion =
            s"Add a Sink streamlet with an inlet to ${c.identify} to receive and dispatch incoming messages. " +
              "An entity's own inlet does not satisfy this even when a connector drives it: driving " +
              "an entity from outside IS an inbound stream, and it belongs at the context boundary " +
              "where it can be seen, not hidden inside the entity it happens to target."
        )
      }
      // Completeness: a context with entities should persist them. Entities are
      // stateful and generally require durable storage, so a context that has
      // entities but no repository at all is incomplete. This is an always-on
      // completeness warning (gated only by showCompletenessWarnings); the
      // remediation suggestion is surfaced when provideTips is enabled. A
      // placeholder repository (`repository X is { ??? }`) counts as addressed.
      if c.repositories.isEmpty then {
        messages.addCompleteness(
          c.errorLoc,
          s"${c.identify} has entities but no repository to persist them; entities are stateful and should be persisted",
          suggestion =
            s"Add a repository to ${c.identify}, e.g. 'repository ${c.id.value}Repository is { ??? }'."
        )
      }
    }
    if c.streamlets.nonEmpty && nonEmptyEntities.nonEmpty then {
      // Completeness 4b: a SINK's handlers should dispatch to entities via tell.
      //
      // Restricted to Sink deliberately. A sink is the boundary that carries messages out of the
      // stream and into entities, so "you handle messages but never dispatch" is a fair question
      // to ask it. For a split, merge or flow it is not: routing between ports is precisely their
      // job, and a `tell` there would dispatch into an entity IN ADDITION to fanning out,
      // duplicating what the downstream contexts already do. riddl-models had four such warnings
      // with no honest edit available -- the models were right and the check was wrong.
      //
      // The check was dormant until accessors saw through includes (c98e33e5e), because its outer
      // guard keys off `c.entities` and that corpus keeps every entity in an include file. So it
      // had never actually run against a real model.
      //
      // `effectiveShape`, not `ascribedShape`: a shape may be derived from arity rather than
      // written down, and AST.scala:1249 warns consumers off hand-rolling that.
      c.streamlets.foreach { streamlet =>
        val isSink = streamlet.effectiveShape match
          case _: Sink => true
          case _       => false
        if isSink && streamlet.inlets.nonEmpty && streamlet.handlers.nonEmpty then {
          streamlet.handlers.foreach { handler =>
            val messageClauses = handler.clauses.collect { case omc: OnMessageLikeClause => omc }
            if messageClauses.nonEmpty then {
              val finder = Finder(handler)
              val tells = finder.recursiveFindByType[TellStatement]
              if tells.isEmpty then {
                messages.addCompleteness(
                  handler.errorLoc,
                  s"${handler.identify} in ${streamlet.identify} handles messages but does not dispatch to any entity via 'tell'",
                  suggestion =
                    "Add 'tell' statements so the streamlet handler dispatches incoming messages to an entity."
                )
              }
            }
          }
        }
      }
    }
  }

  private def validateEpic(
    epic: Epic,
    parents: Parents
  ): Unit = {
    checkContainer(parents, epic)
    if epic.userStory.isEmpty then
      messages.addMissing(
        epic.errorLoc,
        s"${epic.identify} is missing a user story",
        suggestion =
          s"Add a user story to ${epic.identify}, e.g. 'by user SomeUser I want to ... so that ...'."
      )
    else checkRef[User](epic.userStory.user, parents)
  }

  private def validateGroup(
    grp: Group,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, grp)
    checkMetadata(grp)
  }

  private def validateInput(
    input: Input,
    parents: Parents
  ): Unit = {
    val parentsSeq = parents
    checkDefinition(parentsSeq, input)
    checkTypeRef(input.takeIn, parentsSeq)
    // A44: a selection verb (selects/chooses/picks) expects the acquired type to be a
    // choice among options — an Enumeration or Alternation. If the type resolves and is
    // NOT a choice type, emit a StyleWarning (never an Error). Skip when unresolved so we
    // don't pile onto the error checkTypeRef already reports.
    if UIVerbs.isSelectionVerb(input.verbAlias) && typeRefIsChoice(input.takeIn).contains(false)
    then
      messages.addStyle(
        input.loc,
        s"a selection verb ('${input.verbAlias}') expects the input type to be an " +
          s"enumeration or alternation of choices; '${input.takeIn.pathId.format}' is not",
        suggestion = s"Use an entry verb (e.g. 'acquires') for '${input.takeIn.pathId.format}', " +
          "or make its type an enumeration or a 'one of { ... }' alternation."
      )
    checkMetadata(input)
  }

  /** A44: classify the type a [[TypeRef]] refers to as a "choice among options" (an [[Enumeration]]
    * or [[Alternation]]) or not. Returns `Some(true)` when it resolves to a choice type,
    * `Some(false)` when it resolves (to a user type or a predefined type) but is not a choice type,
    * and `None` when it does not resolve at all (caller should skip). Predefined types (String,
    * Integer, …) are never choice types. Shared by input (A44) selection-verb validation and
    * reusable by output (A46) validation.
    */
  private def typeRefIsChoice(ref: TypeRef): Option[Boolean] =
    val pathId = ref.pathId
    val name = pathId.value.lastOption.getOrElse("")
    if pathId.value.sizeIs == 1 && PredefType.allPredefTypes.contains(name) then Some(false)
    else resolution.refMap.definitionOf[Type](pathId).map(t => isChoiceType(t.typEx))

  /** A44: whether a [[TypeExpression]] is a "choice among options" — an [[Enumeration]] or
    * [[Alternation]], following one level of type alias via the refMap.
    */
  private def isChoiceType(te: TypeExpression): Boolean =
    te match
      case _: Enumeration => true
      case _: Alternation => true
      case ate: AliasedTypeExpression =>
        resolution.refMap.definitionOf[Type](ate.pathId).exists(t => isChoiceType(t.typEx))
      case _ => false

  private def validateOutput(
    output: Output,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, output)
    output.putOut match {
      case typ: TypeRef       => checkTypeRef(typ, parents)
      case const: ConstantRef => checkRef[Constant](const, parents)
      case str: LiteralString => checkNonEmpty(str.s, "string to put out", output, Messages.Error)
    }
    checkMetadata(output)
  }

  private def validateContainedGroup(
    containedGroup: ContainedGroup,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, containedGroup)
    checkRef[Group](containedGroup.group, parents)
    checkMetadata(containedGroup)
  }

  private def validateUser(
    user: User,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, user)
    if user.is_a.isEmpty then {
      messages.addMissing(
        user.loc,
        s"${user.identify} is missing its role kind ('is a')",
        suggestion = s"Specify the user's role, e.g. '${user.id.value} is a \"customer\"'."
      )
    }
    checkMetadata(user)
  }

  private def validateUseCase(
    uc: UseCase,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, uc)
    if uc.userStory.nonEmpty then checkRef[User](uc.userStory.user, parents)
    if uc.contents.nonEmpty then {
      // RECURSIVE over interaction groups. Each container's contents get exactly the validation
      // its siblings get one level out. Before this, the three container arms checked only
      // EMPTINESS and never descended, so a step inside `sequence`/`parallel`/`optional` was
      // never validated -- and, with ResolutionPass dropping the same contents, never resolved
      // either. A model could name definitions that do not exist and validate green. Recursion
      // also covers nesting (a `sequence` inside a `parallel`), which a flat pass would not.
      def validateInteractions(items: Seq[InteractionContainerContents]): Unit = items.foreach {
        case seq: SequentialInteractions =>
          if seq.contents.isEmpty then {
            messages.addMissing(
              seq.loc,
              "Sequential interactions should not be empty",
              suggestion = "Add interactions to the sequential block, or remove the empty block."
            )
          } else validateInteractions(seq.contents.toSeq)
        case par: ParallelInteractions =>
          if par.contents.isEmpty then {
            messages.addMissing(
              par.loc,
              "Parallel interaction should not be empty",
              suggestion = "Add interactions to the parallel block, or remove the empty block."
            )
          } else validateInteractions(par.contents.toSeq)
        case opt: OptionalInteractions =>
          if opt.contents.isEmpty then {
            messages.addMissing(
              opt.loc,
              "Optional interaction should not be empty",
              suggestion = "Add interactions to the optional block, or remove the empty block."
            )
          } else validateInteractions(opt.contents.toSeq)
        case gi: GenericInteraction =>
          // Use comprehensive validateInteraction instead of inline validation. Pass `uc`
          // explicitly: interaction references are keyed in the resolution refMap under the
          // enclosing UseCase, which is NOT present in `parents` while the UseCase is itself
          // being processed (a Branch is not on its own parent stack).
          validateInteraction(uc, gi, parents)
          // Additional checks for specific interaction types
          gi match {
            case is: TwoReferenceInteraction =>
              if is.relationship.isEmpty then {
                messages.addMissing(
                  is.loc,
                  s"Interactions must have a non-empty relationship",
                  suggestion =
                    "Describe the relationship for the interaction, e.g. '... \"places\" order'."
                )
              }
            case _ => // Other interaction types handled by validateInteraction
          }
        case _: BriefDescription | _: Description | _: Term | _: Comment | _: AuthorRef => ()
      }
      validateInteractions(uc.contents.toSeq)
    }
    if uc.nonEmpty then {
      if uc.contents.isEmpty then
        messages.addMissing(
          uc.loc,
          s"${uc.identify} doesn't define any interactions",
          suggestion =
            s"Add interactions to ${uc.identify} describing the steps between users and the system."
        )
    }
    checkMetadata(uc)
  }

  private def validateArbitraryInteraction(
    origin: Option[Definition],
    destination: Option[Definition],
    parents: Parents
  ): Unit = {
    val maybeMessage: Option[Message] = origin match {
      case Some(o) if o.isVital =>
        destination match {
          case Some(d) if d.isInstanceOf[GroupRelated] =>
            d match {
              case output @ Output(loc, _, _, _, putOut, _, _) =>
                putOut match {
                  case typRef: TypeRef =>
                    checkTypeRef(typRef, parents) match {
                      case Some(Type(_, _, typEx, _)) if typEx.isContainer =>
                        typEx match {
                          case ate: AggregateUseCaseTypeExpression
                              if ate.usecase == AggregateUseCase.EventCase || ate.usecase == AggregateUseCase.ResultCase =>
                            None // events and results are permitted
                          case ty: TypeExpression => // everything else is not
                            Some(
                              error(
                                s"${output.identify} showing ${typRef.format} of type ${ty.format} is invalid " +
                                  s" because ${o.identify} is a vital definition which can only send Events and Results",
                                loc,
                                suggestion =
                                  "Show an Event or Result here; vital definitions can only emit events and results."
                              )
                            )
                        }
                      case _ => None //
                    }
                  case constRef: ConstantRef =>
                    checkRef[Constant](constRef, parents)
                    Option.empty[Message]
                  case str: LiteralString =>
                    checkNonEmptyValue(str, "string to put out", parents.head, Messages.Error)
                    Option.empty[Message]
                }
              case _ => None
            }
          case _ => None
        }
      case Some(o) if o.isInstanceOf[GroupRelated] =>
        destination match {
          case Some(d) if d.isVital =>
            o match {
              case input @ Input(_, _, _, _, putIn, _, _) =>
                checkTypeRef(putIn, parents) match {
                  case Some(Type(_, _, typEx, _)) if typEx.isContainer =>
                    typEx match {
                      case ate: AggregateUseCaseTypeExpression
                          if ate.usecase == AggregateUseCase.CommandCase || ate.usecase == AggregateUseCase.QueryCase =>
                        None // commands and queries are permitted
                      case ty: TypeExpression => // everything else is not
                        Some(
                          error(
                            s"${input.identify} sending ${putIn.format} of type ${ty.format} is invalid " +
                              s" because ${d.identify} is a vital definition which can only receive Commands and Queries",
                            suggestion =
                              "Send a Command or Query here; vital definitions can only receive commands and queries."
                          )
                        )
                    }
                  case _ => None
                }
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
    maybeMessage match {
      case Some(m: Message) =>
        messages.add(m)
      case None => ()
    }
  }

  /** A39: is `referent` an application-boundary element? A user (actor) may interact only at the
    * application boundary. A boundary element is either a UI element (an [[Input]], [[Output]], or
    * [[Group]] — these are pinned to an application-intention context by A41) or any definition
    * whose enclosing context has [[Intention.Application]]. Everything else (entities, and
    * definitions in Service/Gateway/External or intention-less contexts) is internal domain and off
    * limits to direct user interaction.
    */
  private def isApplicationBoundary(referent: Definition): Boolean =
    referent match
      case _: Input | _: Output | _: Group => true
      case _ => symbols.contextOf(referent).exists(_.intention.contains(Intention.Application))

  /** A39: resolve an interaction step's participant reference to its [[Definition]] via the symbol
    * table. Interaction references are keyed in the resolution refMap under the enclosing use case
    * (not the parents available here), so this resolves through the symbol table by fully-qualified
    * name instead. Returns the definition only on a unique match; an unresolved or ambiguous
    * reference yields `None` and A39 is skipped (other checks report those).
    */
  private def resolveInteractionParticipant(ref: Reference[Definition]): Option[Definition] =
    if ref.pathId.value.isEmpty then None
    else
      symbols.lookupParentage(ref.pathId.value.reverse) match
        case (d, _) :: Nil => Some(d)
        case _             => None

  /** A39: a User (actor) may interact only at the application boundary. Applied to the two untyped
    * interaction steps ([[ArbitraryInteraction]], [[SendMessageInteraction]]) — the five dedicated
    * user steps hard-type their non-user side to a UI element or URL and are compliant by
    * construction. When exactly one side resolves to a [[User]], the opposite (non-user) referent
    * must satisfy [[isApplicationBoundary]]; otherwise the user is reaching past the application
    * straight into the domain, which is an Error (consistent with A41, the complementary right-edge
    * rule). Unresolved referents are skipped (other checks report those); if neither side (or both
    * sides) is a user, A39 does not apply.
    */
  private def checkUserInteractionBoundary(
    from: Reference[Definition],
    to: Reference[Definition]
  ): Unit =
    val origin = resolveInteractionParticipant(from)
    val destination = resolveInteractionParticipant(to)
    val fromIsUser = origin.exists(_.isInstanceOf[User])
    val toIsUser = destination.exists(_.isInstanceOf[User])
    // A39 applies only when exactly one side is a user (from XOR to).
    if fromIsUser != toIsUser then
      val (otherRef, otherDef) = if fromIsUser then (to, destination) else (from, origin)
      otherDef match
        case Some(d) if !isApplicationBoundary(d) =>
          messages.addError(
            otherRef.loc,
            s"a user may interact only at the application boundary; '${otherRef.pathId.format}' is " +
              "not an application UI element (input/output/group) nor in an application-intention " +
              "context — route user interactions through the application, which then reaches the domain",
            suggestion =
              "Interact with an application UI element (input/output/group) or an element in an " +
                "'application context'; let the application reach domain elements on the user's behalf."
          )
        case _ => () // unresolved (reported elsewhere) or already on the boundary
      end match
    end if
  end checkUserInteractionBoundary

  /** A40: stop words dropped when tokenizing an interaction step's free text into "content words".
    * Two groups, both deliberately tiny and fixed (no NLP, no corpus, no scoring):
    *   1. function words — articles, conjunctions, prepositions, pronouns, auxiliary/modal verbs —
    *      which carry no domain meaning wherever they appear; and 2. contentless placeholder
    *      nouns/adverbs ("thing", "stuff", "somehow", ...) which are the very markers of the
    *      vagueness this check is predicting on.
    *
    * Words of fewer than three characters are dropped by the tokenizer itself, so two-letter
    * function words (a, an, of, to, is, it, by, in, on, ...) need not be listed here.
    */
  private val translatabilityStopWords: scala.collection.immutable.Set[String] =
    // 1. function words
    ("the and but nor for not with without from into onto that this these those then than there " +
      "here their them they was were are been being has have had will shall should would can " +
      "could may might must does did done some any all its his her our your when what who whom " +
      "how why which you via per out off over under about after before each such also just only " +
      "very more most much many both same other " +
      // 2. contentless placeholders — the markers of vagueness itself
      "thing things something somehow someone somewhere anything anyhow stuff whatever etc")
      .split(' ')
      .toSet

  /** A40: the least number of distinct content words a step's prose must contain before its
    * translatability is predicted at all. Below this threshold there is not enough prose to predict
    * FROM, and the check declines to judge rather than emitting a guaranteed false positive.
    */
  private val translatabilityMinimumContentWords: Int = 2

  /** A40: split `text` into lowercase content words: maximal runs of letters/digits, at least three
    * characters long, that are not [[translatabilityStopWords]]. Hand-rolled rather than
    * regex-based so it stays cheap ("quickly predict") and identical on JVM, JS and Native.
    */
  private def contentWordsOf(text: String): scala.collection.immutable.Set[String] =
    val words = mutable.Set.empty[String]
    val sb = new StringBuilder
    def flush(): Unit =
      if sb.nonEmpty then {
        val word = sb.toString.toLowerCase
        if word.length >= 3 && !translatabilityStopWords.contains(word) then words += word
        sb.clear()
      }
    text.foreach { ch =>
      if ch.isLetterOrDigit then sb.append(ch) else flush()
    }
    flush()
    words.toSet
  end contentWordsOf

  /** A40: contribute a definition/term name to the vocabulary, both whole and split into its
    * constituent words on camelCase, '_', '-' and '.' boundaries, so prose like "shopping cart"
    * grounds against a definition named `ShoppingCart` or `shopping_cart`.
    */
  private def nameWordsOf(name: String): scala.collection.immutable.Set[String] =
    val parts = mutable.ListBuffer.empty[String]
    val sb = new StringBuilder
    name.foreach { ch =>
      if ch == '_' || ch == '-' || ch == '.' then {
        parts.addOne(sb.toString); sb.clear()
      } else if ch.isUpper && sb.nonEmpty && !sb.last.isUpper then {
        parts.addOne(sb.toString); sb.clear(); sb.append(ch)
      } else sb.append(ch)
    }
    parts.addOne(sb.toString)
    val whole = name.toLowerCase
    val split = contentWordsOf(parts.mkString(" "))
    if whole.length >= 3 then split + whole else split
  end nameWordsOf

  /** A40: the vocabulary in scope for an interaction step — every word the model has actually
    * defined at or above the step's use case. Built from, for the use case and each of its
    * ancestors: the scope's own name, the names of its direct contents, and, from its metadata,
    * each [[Term]]'s name and definition text and the text of each [[BriefDescription]] /
    * [[BlockDescription]]. The richer the in-scope terminology, the larger this set and the more
    * likely a step's prose is predicted translatable.
    */
  private def inScopeVocabulary(
    useCase: UseCase,
    parents: Parents
  ): scala.collection.immutable.Set[String] =
    val vocabulary = mutable.Set.empty[String]
    (useCase +: parents).foreach { scope =>
      vocabulary ++= nameWordsOf(scope.id.value)
      scope.terms.foreach { t =>
        vocabulary ++= nameWordsOf(t.id.value)
        vocabulary ++= contentWordsOf(t.definition.map(_.s).mkString(" "))
      }
      scope.brief.foreach(bd => vocabulary ++= contentWordsOf(bd.brief.s))
      scope.descriptions.foreach(d => vocabulary ++= contentWordsOf(d.lines.map(_.s).mkString(" ")))
      scope.contents.definitions.foreach(d => vocabulary ++= nameWordsOf(d.id.value))
    }
    vocabulary.toSet
  end inScopeVocabulary

  /** A40: predict, cheaply, whether a free-text interaction step will be AI-translatable into a
    * generated test, and warn when the prediction is negative.
    *
    * Only the two free-text step kinds are predicted on: [[VagueInteraction]] (all three parts are
    * literal prose) and [[ArbitraryInteraction]] (the relationship is literal prose; its two
    * endpoints are references and are grounded by construction). The other step kinds are
    * structurally typed and need no prediction.
    *
    * The heuristic is vocabulary grounding: tokenize the step's prose — its literal text plus any
    * `briefly` / `described as` on the step itself — into content words, and predict TRANSLATABLE
    * if any of them appears in the [[inScopeVocabulary]]. Prose with no grounded word at all is
    * predicted untranslatable and draws a [[CompletenessWarning]] — never an error, because a
    * prediction must not fail a build, and it is silenced with completeness warnings off.
    *
    * A prediction is only made when there is enough prose to predict FROM: fewer than
    * [[translatabilityMinimumContentWords]] content words and the check stays silent. A single bare
    * verb — which is all an [[ArbitraryInteraction]]'s relationship usually is ("presses", "sends",
    * "select") — carries no evidence either way, and bare verbs never appear in a noun-dominated
    * vocabulary, so warning on one is a guaranteed false positive. Declining to judge is the honest
    * answer. [[VagueInteraction]], whose three parts are all prose, is unaffected in practice.
    */
  private def checkInteractionTranslatability(
    step: GenericInteraction,
    prose: String,
    useCase: UseCase,
    parents: Parents
  ): Unit =
    val ownProse: String =
      (step.brief.map(_.brief.s).toSeq ++ step.descriptions.map(_.lines.map(_.s).mkString(" ")))
        .mkString(" ")
    val words = contentWordsOf(prose + " " + ownProse)
    if words.size >= translatabilityMinimumContentWords then
      val vocabulary = inScopeVocabulary(useCase, parents)
      if !words.exists(vocabulary.contains) then
        messages.addCompleteness(
          step.loc,
          s"interaction '${prose.trim}' uses no terms defined in scope, so it is unlikely to be " +
            "translatable into a generated test",
          suggestion = "Define the nouns and verbs it uses as 'term's, or add a 'briefly' / " +
            "'described as' to the step that uses in-scope vocabulary."
        )
      end if
    end if
  end checkInteractionTranslatability

  private def validateInteraction(
    useCase: UseCase,
    interaction: Interaction,
    parents: Parents
  ): Unit = {
    interaction match {
      case SelfInteraction(_, from, _, _) =>
        checkRef[Definition](from, parents)
      case DirectUserToURLInteraction(_, user: UserRef, _, _) =>
        checkRef[User](user, parents)
      case FocusOnGroupInteraction(_, user: UserRef, group: GroupRef, _) =>
        checkRef[Group](group, parents)
        checkRef[User](user, parents)
      case SelectInputInteraction(_, user: UserRef, inputRef: InputRef, _) =>
        checkRef[User](user, parents)
        checkRef[Input](inputRef, parents)
      case TakeInputInteraction(_, user: UserRef, inputRef: InputRef, _) =>
        checkRef[User](user, parents)
        checkRef[Input](inputRef, parents)
      case ai @ ArbitraryInteraction(_, from, relationship, to, _) =>
        checkRef[Definition](from, parents)
        checkRef[Definition](to, parents)
        // Interaction refs are keyed in the refMap under the enclosing UseCase, so resolve
        // with `useCase` as the scope (parents.head is the UseCase's parent here — using it
        // left this resolution permanently None, hence the dead type/direction checks).
        val origin = resolution.refMap.definitionOf[Definition](from.pathId, useCase)
        val destination = resolution.refMap.definitionOf[Definition](to.pathId, useCase)
        validateArbitraryInteraction(origin, destination, parents)
        checkUserInteractionBoundary(from, to)
        // A40: only the relationship is free text; the endpoints are references.
        checkInteractionTranslatability(ai, relationship.s, useCase, parents)
      case ShowOutputInteraction(_, from: OutputRef, _, to: UserRef, _) =>
        checkRef[Output](from, parents)
        checkRef[User](to, parents)
      case RefusalInteraction(_, from, to: UserRef, _, _) =>
        checkRef[Definition](from, parents)
        checkRef[User](to, parents)
      case SendMessageInteraction(_, from, msg, to, _) =>
        checkMessageRef(msg, parents, Seq(msg.messageKind))
        checkRef[Definition](from, parents)
        checkRef[Processor[?]](to, parents)
        checkUserInteractionBoundary(from, to)
      case vi @ VagueInteraction(_, from, relationship, to, _) =>
        // A40: every part of a vague step is free text, so all three contribute prose.
        checkInteractionTranslatability(
          vi,
          s"${from.s} ${relationship.s} ${to.s}",
          useCase,
          parents
        )
      case _: OptionalInteractions | _: ParallelInteractions | _: SequentialInteractions =>
      // These are all just containers of other interactions, not needing further validation
    }
  }

  /** Recursively walk all statements in a contents collection, descending into nested statement
    * containers (WhenStatement, MatchStatement). Calls `f` on each Statement encountered.
    */
  private def walkStatements[CV <: RiddlValue](contents: Contents[CV])(f: Statement => Unit): Unit =
    contents.foreach {
      case s: Statement =>
        f(s)
        s match
          case WhenStatement(_, _, thenStatements, elseStatements, _) =>
            walkStatements(thenStatements)(f)
            walkStatements(elseStatements)(f)
          case MatchStatement(_, _, cases, default) =>
            cases.foreach(mc => walkStatements(mc.statements)(f))
            walkStatements(default)(f)
          case ForeachStatement(_, _, _, _, doStatements) =>
            walkStatements(doStatements)(f)
          case _ => ()
      case _ => () // skip Comments
    }
  end walkStatements

  /** Does EVERY execution path through `contents` settle the clause's obligation?
    *
    * The obligation differs per caller (`settles` says what discharges it), but the shape of the
    * question does not: a clause handling a command must, on every path, either produce what the
    * command declares or refuse it.
    *
    * This replaced a much weaker predicate that asked only "does a refusal appear ANYWHERE in
    * this clause?", via `Finder.recursiveFindByType`. Because that searches the whole nested
    * tree, ONE refusal in ONE branch exempted the entire clause, so this validated clean despite
    * producing nothing on the `amt > 0` path:
    * {{{
    * on command Pay is {            // Pay declares `yields event Paid`
    *   when "amt <= 0" then { error "refused" } end
    * }
    * }}}
    *
    * `exists` is the right combinator over a sequence: execution passes through every statement
    * in it, so one statement that settles the obligation settles the whole block. The nested
    * cases are where "every path" actually bites:
    *
    *   - a `when` needs BOTH branches, and an absent `else` is an escape path, not a discharge;
    *   - a `match` needs every case AND a `default`, since without one an unmatched value
    *     escapes (RIDDL cannot know a pattern set is exhaustive);
    *   - a `foreach` NEVER discharges -- its body may iterate zero times.
    *
    * Making `else`/`default` mandatory in the grammar was considered and rejected (Reid,
    * 2026-08-07): it would break ~56 sites across three repos and would NOT close this hole
    * anyway, since an empty or non-discharging `else` still escapes. The analysis is what
    * closes it.
    */
  private def dischargesOnEveryPath[CV <: RiddlValue](
    contents: Contents[CV]
  )(settles: Statement => Boolean): Boolean =
    dischargesOnEveryPathSeq(contents.toSeq)(settles)

  /** The [[dischargesOnEveryPath]] analysis over a plain `Seq`, so a caller can ask the question of
    * a statement list it BUILT rather than one that exists in the AST.
    *
    * A70's overridden-`set` check needs exactly that: "is this `set` overridden later?" means
    * testing the suffix after it CONCATENATED with the enclosing block's continuation, and no such
    * `Contents` exists anywhere in the tree.
    */
  private def dischargesOnEveryPathSeq(
    statements: Seq[RiddlValue]
  )(settles: Statement => Boolean): Boolean =
    statements.exists {
      case WhenStatement(_, _, thenStatements, elseStatements, _) =>
        dischargesOnEveryPath(thenStatements)(settles) &&
        elseStatements.nonEmpty &&
        dischargesOnEveryPath(elseStatements)(settles)
      case MatchStatement(_, _, cases, default) =>
        cases.forall(mc => dischargesOnEveryPath(mc.statements)(settles)) &&
        default.nonEmpty &&
        dischargesOnEveryPath(default)(settles)
      case _: ForeachStatement => false // the body may iterate ZERO times
      case s: Statement        => settles(s)
      case _                   => false // Comments and the like settle nothing
    }
  end dischargesOnEveryPathSeq

  /** A12: count embedded [[Call]] / [[GetValue]] nodes anywhere within a value-expression subtree.
    * Each `call` (a pure function call — A24) and each `get from input/state` (A45) is its own
    * potential failure point (a call may fail; a get from an absent input or unset state may fail).
    * Because `call`/`get` are [[Value]]s (not [[Statement]]s), they are counted here rather than
    * via `Statement.canFail`. Recurses through [[Constructor]] arguments and the boolean-expression
    * sub-language so nested occurrences (e.g. `yield E(get …)`, `call F(get …)`, `a and get …`) are
    * all found.
    */
  private def countValueFailPoints(v: RiddlValue): Int = v match
    case call: Call               => 1 + call.args.map(a => countValueFailPoints(a.value)).sum
    // A12: the census extends to failure-bearing VALUES, not only statements (Reid, 2026-08-09).
    // An `ask` can fail exactly as a `call` can -- more obviously, since no answer may ever
    // arrive -- and `Call` was already counted here, so omitting `ask` would have let a saga
    // step hide a second failure point behind a `let`.
    case _: Ask                   => 1
    // A70/instance-identity: `initiate` invokes `on init` and mints an instance -- it can fail
    // exactly as `call`/`ask` can, so it counts itself (1) PLUS its argument values, exactly like
    // `call` immediately above.
    case init: Initiate           => 1 + init.args.map(a => countValueFailPoints(a.value)).sum
    case _: GetValue              => 1
    case c: Constructor           => c.args.map(a => countValueFailPoints(a.value)).sum
    case le: LogicalExpression    => countValueFailPoints(le.left) + countValueFailPoints(le.right)
    case ne: NotExpression        => countValueFailPoints(ne.expr)
    case ce: ComparisonExpression => countValueFailPoints(ce.left) + countValueFailPoints(ce.right)
    // A17's ASK form contributes NOTHING of its own -- consulting an invariant is a test, not an
    // action that can fail -- but its `with` operand is a full Value and is counted, exactly as a
    // comparison contributes nothing while its operands count.
    case ic: InvariantCondition   => ic.argument.map(countValueFailPoints).getOrElse(0)
    // A bare message REFERENCE carries no failure point of its own -- the statement holding it
    // does, and that statement is counted by its own arm. Enumerated rather than absorbed by a
    // `case _ => 0`, because that catch-all is precisely how `ask` went uncounted: a new
    // failure-bearing value read as "contributes nothing" instead of failing the build.
    case _: Reference[?]          => 0
    // A name cannot fail; see the note in `stateReadsIn`.
    case _: Identifier            => 0
    // `self`/`self.<field>` is a keyword-anchored value, not an effect -- reading the running
    // instance's own identity cannot fail the way a call, ask, or get can.
    case _: SelfValue             => 0
    case _: LiteralString | _: PromptValue | _: ValueRef | _: BooleanLiteral => 0
    case other =>
      throw new IllegalStateException(
        s"countValueFailPoints has no arm for ${other.getClass.getSimpleName} at ${other.loc}; " +
          "decide whether it can fail rather than assuming it cannot"
      )
  end countValueFailPoints

  /** A12: the number of potential failure points contributed by a SINGLE statement (not recursing
    * into nested when/match/foreach bodies — the caller walks those via `walkStatements`). This is
    * the statement-level `canFail` (send/tell/yield/put) PLUS every embedded [[Call]]/[[GetValue]]
    * in the statement's value expression(s).
    */
  private def countStatementFailPoints(s: Statement): Int =
    (if s.canFail then 1 else 0) + statementValues(s).map(countValueFailPoints).sum
  end countStatementFailPoints

  /** The value expression(s) a single statement evaluates, NOT recursing into nested when/match/
    * foreach bodies — callers walk those with `walkStatements`.
    *
    * Factored out so the A12 failure-point census and the saga `ask` prohibition ask the same
    * question of a statement. When they each carried their own copy of this mapping, a statement
    * kind added to one and missed in the other would silently go unexamined by the other check.
    */
  private def statementValues(s: Statement): Seq[RiddlValue] =
    s match
      case set: SetStatement     => Seq(set.value)
      case let: LetStatement     => Seq(let.expression)
      case put: PutStatement     => Seq(put.value)
      case ret: ReturnStatement  => Seq(ret.value)
      case snd: SendStatement    => Seq(snd.msg)
      case tel: TellStatement    => Seq(tel.msg)
      case yld: YieldStatement   => Seq(yld.msg)
      case rpl: ReplyStatement   => Seq(rpl.msg)
      case mor: MorphStatement   => Seq(mor.value)
      // Review round 1: `req.argument` (the `with <expr>` operand) is a full Value -- `require`
      // is legal in both a function body and an activation clause (`guardStatements` in
      // `StatementParser` suppresses it only under `EventClause`), and `initiateValue` is a
      // production of the same `value` rule the operand is parsed with -- so `require X with
      // initiate entity Order` could hide an `initiate` from every walk that consumes
      // `statementValues` (state-reads, asks, this task's instance-effect ban, the A12
      // fail-point census) unless the operand is included here too.
      case req: RequireStatement  => Seq(req.condition) ++ req.argument.toSeq
      case whn: WhenStatement     => Seq(whn.condition)
      // Review round 1: a `MatchCase.guard` is the SAME shape as `req.argument` -- a full
      // `BooleanExpression | ValueRef` value that was never fed to any of these walks, even
      // though `validateMatch` already resolves/type-checks it independently via `validateValue`.
      // `mat.expression` (the subject) is unaffected; this only adds each case's guard.
      case mat: MatchStatement    => Seq(mat.expression) ++ mat.cases.flatMap(_.guard.toSeq)
      // A70/instance-identity: `terminate`'s arguments are full Values, exactly like a
      // constructor's or `initiate`'s, so a `get from state`/`ask`/nested call-fail-point can
      // hide inside one and must be counted rather than silently skipped.
      case term: TerminateStatement => term.args.map(_.value)
      case _                        => Seq.empty
  end statementValues

  /** Every [[Ask]] embedded in a value expression, at any depth.
    *
    * Enumerated over the same arms as `countValueFailPoints` rather than absorbed by a catch-all:
    * a new value kind that can CONTAIN an ask must fail the build here, not quietly hide one
    * inside a saga.
    */
  /** Every `get from state` embedded in a value expression, at any depth.
    *
    * `get from input` is deliberately EXCLUDED: reading a UI input is not a state read, and it is
    * already confined to application contexts indirectly (A41 pins UI groups there, so the
    * reference cannot resolve elsewhere). [[AST.GetValue.source]] separates the two cleanly.
    *
    * Enumerated over the same arms as `asksIn` and for the same reason: a new value kind that can
    * CONTAIN a state read must fail the build here rather than quietly hide one.
    */
  private def stateReadsIn(v: RiddlValue): Seq[(GetValue, StateRef)] = v match
    case gv: GetValue =>
      gv.source match
        case sr: StateRef => Seq(gv -> sr)
        case _: InputRef  => Seq.empty
    case call: Call               => call.args.toSeq.flatMap(a => stateReadsIn(a.value))
    case c: Constructor           => c.args.toSeq.flatMap(a => stateReadsIn(a.value))
    // `initiate`'s arguments are full Values, exactly like a constructor's or a call's, so a
    // `get from state` can hide inside one and this must recurse rather than stop.
    case init: Initiate           => init.args.toSeq.flatMap(a => stateReadsIn(a.value))
    case le: LogicalExpression    => stateReadsIn(le.left) ++ stateReadsIn(le.right)
    case ne: NotExpression        => stateReadsIn(ne.expr)
    case ce: ComparisonExpression => stateReadsIn(ce.left) ++ stateReadsIn(ce.right)
    // A17's ASK form: `when invariant Limit with <expr>`. The `with` operand is a full Value, so it
    // CAN hold a state read and this must recurse rather than stop. `ref` needs no arm -- an
    // InvariantRef is a Reference and the arm below covers it.
    case ic: InvariantCondition   => ic.argument.toSeq.flatMap(stateReadsIn)
    // An `ask` holds only a QueryRef and a ProcessorRef -- no nested value -- so it cannot contain
    // a state read. (A saga's `ask` is separately banned outright; see `asksIn`.)
    case _: Ask                   => Seq.empty
    case _: Reference[?]          => Seq.empty
    // An IDENTIFIER is a NAME, not an expression: `when !isValid` binds the legacy negated-
    // identifier form, whose condition is a bare `Identifier` naming a let-local or a field. A name
    // has no sub-structure, so it can contain nothing -- decided deliberately, as the throw below
    // instructs, not defaulted.
    //
    // It is here because `statementValues` yields a domain WIDER than `Value`:
    // `WhenStatement.condition` is `LiteralString | Identifier | ValueRef | BooleanExpression |
    // PromptValue`, and `Identifier` is in none of the other members. Auditing `Value` alone (as
    // the InvariantCondition fix did on 2026-08-12) misses exactly this, which is how
    // `when !isValid` -- documented syntax that validated on rc.11 -- came to throw on rc.13.
    case _: Identifier            => Seq.empty
    // `self`/`self.<field>` holds no nested value -- an optional bare field Identifier, not a
    // sub-expression -- so it cannot contain a state read.
    case _: SelfValue             => Seq.empty
    case _: LiteralString | _: PromptValue | _: ValueRef | _: BooleanLiteral => Seq.empty
    case other =>
      throw new IllegalStateException(
        s"stateReadsIn has no arm for ${other.getClass.getSimpleName} at ${other.loc}; " +
          "decide whether it can contain a 'get from state' rather than assuming it cannot"
      )
  end stateReadsIn

  /** Every [[Initiate]] embedded in a value expression, at any depth -- the top-level `Initiate`
    * itself PLUS any nested inside its own arguments (`initiate entity Order(x = initiate entity
    * Foo)`), exactly as `stateReadsIn` recurses into `Initiate.args` looking for a `get from state`.
    *
    * Enumerated over the same arms as `stateReadsIn`/`asksIn` and for the same reason: a new value
    * kind that can CONTAIN an `initiate` must fail the build here rather than quietly hide one. This
    * is what lets `checkInstanceEffectScope` and `validateCorrelation`'s fold-purity check see
    * `initiate` wherever it hides -- most importantly inside a `let x = initiate ...`, which is a
    * [[LetStatement]], not a `TerminateStatement`-shaped statement a simple `case` match would catch.
    */
  private def initiatesIn(v: RiddlValue): Seq[Initiate] = v match
    case init: Initiate           => Seq(init) ++ init.args.toSeq.flatMap(a => initiatesIn(a.value))
    case call: Call               => call.args.toSeq.flatMap(a => initiatesIn(a.value))
    case c: Constructor           => c.args.toSeq.flatMap(a => initiatesIn(a.value))
    case le: LogicalExpression    => initiatesIn(le.left) ++ initiatesIn(le.right)
    case ne: NotExpression        => initiatesIn(ne.expr)
    case ce: ComparisonExpression => initiatesIn(ce.left) ++ initiatesIn(ce.right)
    case ic: InvariantCondition   => ic.argument.toSeq.flatMap(initiatesIn)
    // A `get from state`/`get from input` holds only a StateRef/InputRef -- no nested value -- so
    // it cannot contain an `initiate`.
    case _: GetValue              => Seq.empty
    // An `ask` holds only a QueryRef and a ProcessorRef -- no nested value.
    case _: Ask                   => Seq.empty
    case _: Reference[?]          => Seq.empty
    // A name contains nothing; see the note in `stateReadsIn`.
    case _: Identifier            => Seq.empty
    // `self`/`self.<field>` holds no nested value; see the same note in `stateReadsIn`.
    case _: SelfValue             => Seq.empty
    case _: LiteralString | _: PromptValue | _: ValueRef | _: BooleanLiteral => Seq.empty
    case other =>
      throw new IllegalStateException(
        s"initiatesIn has no arm for ${other.getClass.getSimpleName} at ${other.loc}; " +
          "decide whether it can contain an 'initiate' rather than assuming it cannot"
      )
  end initiatesIn

  /** The definition whose data a `set` in this statement position would write — the INNERMOST
    * enclosing processor, since `parents` runs innermost-first.
    *
    * A `State`'s handlers resolve to the enclosing [[AST.Entity]], and a [[AST.Correlation]]'s
    * folds to the enclosing [[AST.Projector]], which is what makes both legal without a special
    * case. [[AST.Function]] is listed so a function body reports as a Function rather than falling
    * through to whatever contains it — though `set` never reaches here from one, since A26 rejects
    * it at the keyword (`StatementParser.setStatements`).
    */
  private def enclosingWriteScope(parents: Parents): Option[Definition] =
    parents.collectFirst {
      case e: Entity     => e
      case p: Projector  => p
      case r: Repository => r
      case s: Saga       => s
      case a: Adaptor    => a
      case st: Streamlet => st
      case f: Function   => f
      case c: Context    => c
    }

  private def asksIn(v: RiddlValue): Seq[Ask] = v match
    case ask: Ask                 => Seq(ask)
    case call: Call               => call.args.toSeq.flatMap(a => asksIn(a.value))
    case c: Constructor           => c.args.toSeq.flatMap(a => asksIn(a.value))
    // `initiate`'s arguments are full Values, exactly like a constructor's or a call's, so an
    // `ask` can hide inside one -- and a saga step is exactly where that must not go unnoticed.
    case init: Initiate           => init.args.toSeq.flatMap(a => asksIn(a.value))
    case le: LogicalExpression    => asksIn(le.left) ++ asksIn(le.right)
    case ne: NotExpression        => asksIn(ne.expr)
    case ce: ComparisonExpression => asksIn(ce.left) ++ asksIn(ce.right)
    // A17's ASK form. Same reasoning as `stateReadsIn`: the `with` operand is a full Value, so an
    // `ask` can hide inside one -- and a saga step is exactly where that must not go unnoticed.
    case ic: InvariantCondition   => ic.argument.toSeq.flatMap(asksIn)
    case _: GetValue              => Seq.empty
    case _: Reference[?]          => Seq.empty
    // A name contains nothing; see the note in `stateReadsIn`.
    case _: Identifier            => Seq.empty
    // `self`/`self.<field>` holds no nested value; see the same note in `stateReadsIn`.
    case _: SelfValue             => Seq.empty
    case _: LiteralString | _: PromptValue | _: ValueRef | _: BooleanLiteral => Seq.empty
    case other =>
      throw new IllegalStateException(
        s"asksIn has no arm for ${other.getClass.getSimpleName} at ${other.loc}; " +
          "decide whether it can contain an 'ask' rather than assuming it cannot"
      )
  end asksIn

  /** A23: is `s` an EFFECT statement — one that mutates state, changes behavior, or emits a
    * message? This is the shared A23 effect set: A26's pure-function bans
    * (`set`/`morph`/`become`/`send`/ `tell`/`yield`) plus A45's `put` and A70/instance-identity's
    * `terminate` (ends an instance — as much an effect as `tell`). It deliberately EXCLUDES the
    * refusals themselves (`require`/`error`) and the opaque `CodeStatement` (unclassifiable — not
    * treated as an effect).
    */
  private def isEffectStatement(s: Statement): Boolean = s match
    case _: SetStatement | _: MorphStatement | _: BecomeStatement | _: SendStatement |
        _: TellStatement | _: YieldStatement | _: PutStatement | _: TerminateStatement =>
      true
    case _ => false

  /** A23 ("refusals first"): within a single linear statement list, no EFFECT statement may appear
    * before a REFUSAL (`require`/`error`). Performing effects before refusing would leave partial
    * changes, so every refusal must precede every effect in its list.
    *
    * Each statement list is checked independently in source order (Option A — per-list): the
    * clause/step body is one list, and each `when`/`match` branch body and each `foreach` body is
    * its OWN list, recursed into with a fresh effect-seen state. "Before" therefore only means
    * "earlier in the same list" — an effect at top level does NOT conflict with a refusal nested in
    * a branch. Modeled on `checkStatementScopes` (per-list order), NOT `walkStatements` (which
    * flattens and loses per-list position).
    */
  private def checkRefusalsFirst(stmts: Seq[Statement]): Unit =
    var firstEffect: Option[Statement] = None
    stmts.foreach {
      case s if isEffectStatement(s) =>
        if firstEffect.isEmpty then firstEffect = Some(s)
      case r @ (_: RequireStatement | _: ErrorStatement) =>
        firstEffect.foreach { eff =>
          messages.addError(
            r.loc,
            s"a refusal (require/error) must come before any effect; the effect '${eff.kind}' at " +
              s"${eff.loc.toShort} precedes this refusal — performing effects before refusing leaves " +
              "partial changes",
            suggestion =
              "Move all refusals (require/error preconditions) ahead of any effect statements " +
                "(set/morph/become/send/tell/yield/put) in this statement list."
          )
        }
      case ws: WhenStatement =>
        checkRefusalsFirst(ws.thenStatements.toSeq.collect { case s: Statement => s })
        checkRefusalsFirst(ws.elseStatements.toSeq.collect { case s: Statement => s })
      case ms: MatchStatement =>
        ms.cases.foreach { mc =>
          checkRefusalsFirst(mc.statements.toSeq.collect { case s: Statement => s })
        }
        checkRefusalsFirst(ms.default.toSeq.collect { case s: Statement => s })
      case fs: ForeachStatement =>
        checkRefusalsFirst(fs.doStatements.toSeq.collect { case s: Statement => s })
      case _ => () // let/prompt/return/code/comment: neither effect nor refusal
    }
  end checkRefusalsFirst

  /** A25: is `te` a collection type — i.e. iterable by `foreach`? Covers the collection type
    * expressions (Sequence/Set/Graph/Table/Replica/Mapping) and the multiplicative cardinalities
    * (ZeroOrMore/OneOrMore/SpecificRange). Aliased types are resolved and checked transitively.
    */
  private def isCollectionType(te: TypeExpression): Boolean =
    te match
      case _: Sequence | _: AST.Set | _: Graph | _: Table | _: Replica | _: Mapping => true
      case _: ZeroOrMore | _: OneOrMore | _: SpecificRange                          => true
      case ate: AliasedTypeExpression =>
        resolution.refMap.definitionOf[Type](ate.pathId).exists(t => isCollectionType(t.typEx))
      case _ => false

  /** A25: the ELEMENT type a collection yields, the dual of [[isCollectionType]].
    *
    * Needed so a `foreach` element is not merely in scope but TYPED: without it `line` resolves
    * and `line.sku` still does not, which is the whole point of iterating. `Mapping` yields None
    * because a map has no single element type -- it is DESTRUCTURED into two names instead, by
    * [[foreachBindings]], which reads `from` and `to` directly. Guessing `to` here would silently
    * mistype every key access.
    */
  private def collectionElementType(te: TypeExpression): Option[TypeExpression] =
    te match
      case s: Sequence           => Some(s.of)
      case s: AST.Set            => Some(s.of)
      case g: Graph              => Some(g.of)
      case t: Table              => Some(t.of)
      case r: Replica            => Some(r.of)
      case z: ZeroOrMore         => Some(z.typeExp)
      case o: OneOrMore          => Some(o.typeExp)
      case sr: SpecificRange     => Some(sr.typeExp)
      case _: Mapping            => None
      case ate: AliasedTypeExpression =>
        resolution.refMap.definitionOf[Type](ate.pathId).flatMap(t => collectionElementType(t.typEx))
      case _ => None

  /** Follow [[AliasedTypeExpression]] hops to the type expression underneath. An unresolvable alias
    * returns itself rather than None, so callers see "some type I cannot see through" instead of
    * "no type", which are different facts.
    */
  private def dealias(te: TypeExpression): TypeExpression =
    te match
      case ate: AliasedTypeExpression =>
        resolution.refMap.definitionOf[Type](ate.pathId).map(t => dealias(t.typEx)).getOrElse(te)
      case other => other

  /** The COLLECTION's own type expression — what a `foreach` is about to iterate. Distinct from
    * [[collectionElementType]], which answers what that collection yields. The arity rule needs the
    * container itself, since only a `Mapping` takes two names.
    */
  private def foreachCollectionType(
    fs: ForeachStatement,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression],
    parents: Parents
  ): Option[TypeExpression] =
    val raw: Option[TypeExpression] = fs.collection match
      case fr: FieldRef  => resolution.refMap.definitionOf[Field](fr.pathId).map(_.typeEx)
      case id: Identifier =>
        val idx = letIndexOf(id.value, lets)
        if idx >= 0 then letType(lets(idx), lets.take(idx), parents).map(_.typEx)
        else elements.get(id.value)
    raw.map(dealias)
  end foreachCollectionType

  /** The names a `foreach` binds over its body, with their types, and the arity diagnostics.
    *
    * Arity is STRICT both ways — exactly two names for a mapping, exactly one otherwise — and is
    * checked HERE rather than in the parser, because a parse-time `error()` preempts the whole pass
    * chain, and because only this pass knows the collection's type. Letting one name stand for a
    * mapping is what used to bind it to `Anything` and wave `e.whatever` through; that hole is the
    * reason the destructuring form exists.
    *
    * A collection that did not resolve binds every name to `Anything` and reports NO arity error:
    * the header's own failure is already reported, and a second message about the shape of a type
    * nobody could find would blame the author twice for one mistake.
    */
  private def foreachBindings(
    fs: ForeachStatement,
    collectionType: Option[TypeExpression]
  ): Map[String, TypeExpression] =
    val anything = Anything(fs.loc)
    collectionType match
      case Some(m: Mapping) =>
        fs.valueElement match
          case Some(v) => Map(fs.element.value -> m.from, v.value -> m.to)
          case None =>
            messages.addError(
              fs.loc,
              s"'foreach' over a mapping binds a key AND a value, so it needs two names, " +
                s"but only '${fs.element.value}' was given",
              suggestion = s"Write 'foreach ${fs.element.value}, <value> in ...' — the first name " +
                "binds the key, the second the value."
            )
            Map(fs.element.value -> m.from)
      case Some(other) =>
        val elementType = collectionElementType(other).getOrElse(anything)
        fs.valueElement match
          case None => Map(fs.element.value -> elementType)
          case Some(v) =>
            messages.addError(
              v.loc,
              s"'foreach' binds a second name only over a mapping, and ${other.format} is not one",
              suggestion =
                s"Drop the second name: 'foreach ${fs.element.value} in ...'."
            )
            Map(fs.element.value -> elementType, v.value -> anything)
      case None =>
        Map(fs.element.value -> anything) ++ fs.valueElement.map(_.value -> anything)
  end foreachBindings

  /** The fields directly in scope at a statement: those of the enclosing entity's state record(s),
    * of the handled message, and of the enclosing function's `requires` input.
    *
    * This is a NAMING aid only — it answers "would a reader take this bare name for a field?", which
    * is what the on-clause binding's shadow warning asks. It is NOT an allow-list. It once gated
    * `foreach ... in field <path>` by identity, which rejected `foreach line in field order.lines`
    * for no better reason than that `lines` belongs to `Order` rather than to the message directly.
    * Cardinality is the whole of that question: if the path resolves and lands on a collection, it
    * is iterable, wherever it sits.
    */
  private def fieldsInScope(parents: Parents): Seq[Field] =
    def aggFields(t: Type): Seq[Field] =
      t.typEx match
        case ate: AggregateTypeExpression => ate.fields
        case _                            => Seq.empty[Field]
    val stateFields: Seq[Field] =
      parents.collectFirst { case e: Entity => e }.toSeq.flatMap { e =>
        e.states.flatMap { st =>
          resolution.refMap.definitionOf[Type](st.typ.pathId).toSeq.flatMap(aggFields)
        }
      }
    val messageFields: Seq[Field] =
      parents
        .collectFirst { case omc: OnMessageLikeClause if omc.msg.nonEmpty => omc }
        .toSeq
        .flatMap { omc =>
          resolution.refMap.definitionOf[Type](omc.msg.pathId).toSeq.flatMap(aggFields)
        }
    val functionFields: Seq[Field] =
      parents.collectFirst { case f: Function => f }.toSeq.flatMap { f =>
        f.input.toSeq.flatMap {
          case tr: TypeRef =>
            resolution.refMap.definitionOf[Type](tr.pathId).toSeq.flatMap(aggFields)
          case agg: Aggregation => agg.fields
        }
      }
    stateFields ++ messageFields ++ functionFields
  end fieldsInScope

  /** A25: validate a single `foreach` collection against the in-scope `let` locals and foreach
    * element names threaded to this point.
    */
  private def validateForeachCollection(
    fs: ForeachStatement,
    inScopeLets: Seq[LetStatement],
    inScopeElements: Map[String, TypeExpression],
    parents: Parents
  ): Unit =
    fs.collection match
      case id: Identifier =>
        // A bare identifier names a `let`-bound local (or an enclosing foreach element).
        if inScopeElements.contains(id.value) then () // an outer foreach element; accepted
        else
          val idx = letIndexOf(id.value, inScopeLets)
          if idx >= 0 then
            val ls = inScopeLets(idx)
            // A55: the type may be DECLARED (`let x: T = …`) or INFERRED from the bound
            // expression, so the "no declared type" complaint below is now reached only when
            // neither is available.
            letType(ls, inScopeLets.take(idx), parents) match
              case Some(typ) if !isCollectionType(typ.typEx) =>
                messages.addError(
                  fs.loc,
                  s"'foreach' local '${id.value}' is not a collection; its type " +
                    s"'${typ.id.value}' is not iterable",
                  suggestion =
                    "Iterate a local whose 'let' type is a collection, e.g. 'let batch: many Order = ...'."
                )
              case Some(_) => () // resolves to a collection
              case None =>
                messages.addError(
                  fs.loc,
                  s"'foreach' local '${id.value}' has no declared or inferable type, so it cannot " +
                    "be verified as a collection",
                  suggestion =
                    s"Declare the local's collection type, e.g. 'let ${id.value}: many Order = ...'."
                )
          else
            messages.addError(
              fs.loc,
              s"'foreach' collection '${id.value}' is not a 'let'-bound local in scope",
              suggestion =
                "Bind the collection with a 'let' before the loop, or use 'field <path>' to iterate a field."
            )
          end if
      case fr: FieldRef =>
        resolution.refMap.definitionOf[Field](fr.pathId) match
          case Some(field) =>
            if !isCollectionType(field.typeEx) then
              messages.addError(
                fs.loc,
                s"'foreach' field '${fr.pathId.format}' is not a collection type",
                suggestion =
                  "Iterate a collection-typed field (Sequence/Set/Graph/Table/Replica/Mapping or a " +
                    "'many'/'1+'/range cardinality)."
              )
            end if
            // There is deliberately NO second check that the field be a DIRECT field of the entity
            // state, handled message, or function input. Cardinality is the whole question: if the
            // path resolves and the type it lands on is a collection, it is iterable. Where the
            // field sits is the resolver's business, and it has already answered.
          case None => () // unresolved field — ResolutionPass already reported it
  end validateForeachCollection

  // A54's `valueAllowedFields` is gone: a ValueRef's scope is no longer matched by hand here. A55
  // routes every ValueRef through ResolutionPass, whose `valueScopeField` supplies the same three
  // sources as an ANCHOR for the ordinary path walk. See `valueRefDefinition`.

  /** A54: the named [[Type]] a [[Value]] denotes, or `None` when it is untyped (a pseudo-code
    * [[LiteralString]]) or cannot be determined. Used for best-effort type-compatibility checks;
    * `None` means "skip the check", so type errors are only raised when both sides resolve.
    */
  /** The [[Type]] an `ask` answers with: the query's declared `replies result X`.
    *
    * Two hops, both through the refMap: the [[QueryRef]] names a query [[Type]], whose
    * [[AggregateUseCaseTypeExpression.yields]] holds the declared result reference. `None` only
    * when the query does not resolve or declares no `replies` -- both of which `validateAsk`
    * reports, so a caller seeing `None` has already been told why.
    */
  private def askResultType(ask: Ask): Option[Type] =
    resolution.refMap.definitionOf[Type](ask.query.pathId).flatMap { queryType =>
      queryType.typEx match
        case auc: AggregateUseCaseTypeExpression =>
          auc.yields.flatMap(r => resolution.refMap.definitionOf[Type](r.pathId))
        case _ => None
    }

  /** The three ways an `ask` can be wrong, all of them decidable here.
    *
    * `ask` declares a correlation between two halves of one interaction, so validation's job is to
    * check the interaction can actually happen: the thing asked must be answerable, must say what
    * it answers with, and must be asked of something that handles it.
    */
  private def validateAsk(ask: Ask, parents: Parents): Unit =
    // 1. The target must resolve, and it must be a query. The ref TYPE makes the kind structural
    //    -- a QueryRef cannot name a command -- so this catches an unresolved or mis-kinded path.
    val queryType = checkRef[Type](ask.query, parents)
    val target = checkRef[Processor[?]](ask.processor, parents)

    // 3. The processor asked must actually handle the query, or the ask can never be answered.
    //    Checked directly rather than through UseCaseWitnessPass's `handledBy` index, which is
    //    built in a later pass and is not reachable from here.
    //
    //    Deliberately CONSERVATIVE: an `on other` clause handles every message, and a State's
    //    handlers count as the entity's (an Entity may hold its handlers under a State). Silent
    //    when either side is unresolved -- ref-integrity already reports that, and piling a
    //    "does not handle" error on top of a "not resolved" one helps nobody.
    for
      qt <- queryType
      proc <- target
    do
      val stateHandlers = proc match
        case e: Entity => e.states.flatMap(_.handlers)
        case _         => Seq.empty
      val clauses = (proc.handlers ++ stateHandlers).flatMap(_.clauses)
      val handlesAnything = clauses.exists(_.isInstanceOf[OnOtherClause])
      val handlesThis = clauses.exists {
        case omc: OnMessageLikeClause if omc.msg.nonEmpty =>
          resolution.refMap.definitionOf[Type](omc.msg.pathId).exists(_ eq qt)
        case _ => false
      }
      if !handlesThis && !handlesAnything then
        messages.addError(
          ask.loc,
          s"${proc.identify} has no clause handling ${qt.identify}, so this `ask` cannot be " +
            "answered",
          suggestion = s"Add `on query ${ask.query.pathId.format} is { … }` to ${proc.identify}, " +
            s"or ask a processor that handles it."
        )
      end if
    end for

    queryType.foreach { qt =>
      qt.typEx match
        case auc: AggregateUseCaseTypeExpression if auc.usecase == AggregateUseCase.QueryCase =>
          // 2. It must declare what it answers with, or the answer has no type. `replies` is
          //    OPTIONAL in general -- this is the one place that makes it mandatory, which is why
          //    the requirement lives at the ASK site rather than on every query.
          if auc.yields.isEmpty then
            messages.addError(
              ask.loc,
              s"${qt.identify} declares no `${Keyword.replies}`, so `ask` has no answer to bind",
              suggestion = s"Declare what it answers with — `query ${qt.id.value} " +
                s"${Keyword.replies} result <SomeResult> is { … }` — or use `tell` if no answer " +
                "is expected."
            )
        case _ =>
          messages.addError(
            ask.query.loc,
            s"`ask` takes a query, but ${qt.identify} is not one",
            suggestion = "Ask a query. A command, event, result or record is not answerable — " +
              "use `tell` to deliver one."
          )
    }
  end validateAsk

  /** The [[MethodArgument]]s a Processor's `on init` clause declares, or `Seq.empty` when it
    * declares none (including when it has no `on init` clause at all -- indistinguishable from
    * this function's point of view, and `checkInitiate` treats an unresolved target the same way
    * via `checkRef`'s silence). Entity state handlers are folded in exactly as `validateAsk` does
    * for `on query`/`on other`: `on init` commonly lives inside a `State` rather than directly on
    * the entity (state handlers apply to the entity, per `WithHandlers`'s literal `contents`
    * filter not descending into `State`).
    */
  private def initClauseParameters(p: Processor[?]): Seq[MethodArgument] =
    val stateHandlers = p match
      case e: Entity => e.states.flatMap(_.handlers)
      case _         => Seq.empty
    (p.handlers ++ stateHandlers)
      .flatMap(_.clauses)
      .collectFirst { case oic: OnInitializationClause => oic.parameters }
      .getOrElse(Seq.empty)
  end initClauseParameters

  /** A70/instance-identity: validate `initiate <processor>(args)` against the target's declared
    * `on init` parameters -- arity, then best-effort per-argument type compatibility via the SAME
    * helper a constructor and a call use ([[checkArgumentTypes]]; see its scaladoc for why a
    * second copy is not written). `on init` declares [[MethodArgument]]s, not [[Field]]s, so they
    * are adapted rather than the helper forked.
    *
    * Silent when the target does not resolve -- `ResolutionPass` already reported that (mirrors
    * `validateAsk`'s target resolution). Recurses into argument values exactly as
    * `validateConstructor`/`validateCall` do, so a nested constructor/get/ask inside an argument
    * is still checked.
    */
  private def checkInitiate(
    init: Initiate,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression] = Map.empty
  ): Unit =
    checkRef[Processor[?]](init.processor, parents).foreach { p =>
      val declared = initClauseParameters(p)
      def count(n: Int, word: String): String = s"$n $word${if n == 1 then "" else "s"}"
      if declared.isEmpty && init.args.nonEmpty then
        messages.addError(
          init.loc,
          s"${p.identify} declares 'on init' with no parameters, but " +
            s"${count(init.args.size, "argument")} supplied",
          suggestion = s"Write 'initiate ${init.processor.format}' with no parentheses."
        )
      else if declared.size != init.args.size then
        messages.addError(
          init.loc,
          s"${p.identify} declares 'on init' with ${count(declared.size, "parameter")}, but " +
            s"${count(init.args.size, "argument")} supplied",
          suggestion =
            s"Supply ${declared.size}: ${declared.map(a => s"${a.name}: ${a.typeEx.format}").mkString(", ")}."
        )
      else
        // Reuse the EXISTING per-argument helper (`checkArgumentTypes`) rather than writing a
        // second one — its scaladoc records that two hand-written copies were free to drift, so a
        // rule tightened for constructors would silently not apply here. It wants Seq[Field], and
        // `on init` declares Seq[MethodArgument], so adapt rather than fork:
        val asFields: Seq[Field] = declared.map { a =>
          Field(a.loc, Identifier(a.loc, a.name), a.typeEx)
        }
        checkArgumentTypes(init.args, asFields, "parameter", parents, lets, elements)
    }
    init.args.foreach(arg => validateValue(arg.value, parents, lets, elements))
  end checkInitiate

  /** The [[MethodArgument]]s a Processor's `on term` clause declares, or `Seq.empty` when it
    * declares none (including when it has no `on term` clause at all). Mirrors
    * [[initClauseParameters]] exactly, including folding in entity state handlers.
    */
  private def termClauseParameters(p: Processor[?]): Seq[MethodArgument] =
    val stateHandlers = p match
      case e: Entity => e.states.flatMap(_.handlers)
      case _         => Seq.empty
    (p.handlers ++ stateHandlers)
      .flatMap(_.clauses)
      .collectFirst { case otc: OnTerminationClause => otc.parameters }
      .getOrElse(Seq.empty)
  end termClauseParameters

  /** A70/instance-identity: validate `terminate <processor>(args)` against the target's declared
    * `on term` parameters. Mirror of [[checkInitiate]] -- see its scaladoc for why
    * [[checkArgumentTypes]] is reused rather than forked.
    */
  private def checkTerminate(
    term: TerminateStatement,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression] = Map.empty
  ): Unit =
    checkRef[Processor[?]](term.processor, parents).foreach { p =>
      val declared = termClauseParameters(p)
      def count(n: Int, word: String): String = s"$n $word${if n == 1 then "" else "s"}"
      if declared.isEmpty && term.args.nonEmpty then
        messages.addError(
          term.loc,
          s"${p.identify} declares 'on term' with no parameters, but " +
            s"${count(term.args.size, "argument")} supplied",
          suggestion = s"Write 'terminate ${term.processor.format}' with no parentheses."
        )
      else if declared.size != term.args.size then
        messages.addError(
          term.loc,
          s"${p.identify} declares 'on term' with ${count(declared.size, "parameter")}, but " +
            s"${count(term.args.size, "argument")} supplied",
          suggestion =
            s"Supply ${declared.size}: ${declared.map(a => s"${a.name}: ${a.typeEx.format}").mkString(", ")}."
        )
      else
        val asFields: Seq[Field] = declared.map { a =>
          Field(a.loc, Identifier(a.loc, a.name), a.typeEx)
        }
        checkArgumentTypes(term.args, asFields, "parameter", parents, lets, elements)
    }
    term.args.foreach(arg => validateValue(arg.value, parents, lets, elements))
  end checkTerminate

  /** A70/instance-identity task 6: derive which INSTANCE a `tell` addresses -- the message's field
    * typed `Id(target)`, found without annotation. `by <field>` disambiguates when more than one
    * field qualifies.
    *
    * Uniform across processor kinds -- an `Id(projector Foo)` field is used if present -- but the
    * "no address" DIAGNOSTIC is entity-only, because an entity is the only multiply-instantiated
    * processor (Reid, 2026-08-13). A repository is reached by path and has nothing to distinguish.
    *
    * Called from [[checkStatementScopes]]'s `TellStatement` case, the single entry point invoked at
    * every container root AND recursively for when/match/foreach bodies, so a `tell` nested at any
    * depth is still reached -- mirrors [[checkTerminate]]'s reachability.
    *
    * Guarded on the RESOLVED field list being non-empty, not on `mt.isEmpty`. `Type.isEmpty` is
    * `Container.isEmpty` over `Type.contents`, which returns `Seq.empty` for anything that is not
    * directly an `Aggregation`/`AggregateUseCaseTypeExpression`/`Enumeration` -- in particular for
    * an ALIAS-declared message (`command Ship is Shipment`), whose `contents` is always empty
    * regardless of how many fields `Shipment` has. Gating on `mt.isEmpty` therefore treated every
    * alias-declared message as a `???` stub and silently skipped this whole check for it.
    * `aggregateFieldsOf` already follows the alias chain (as `checkOnOtherBinding` and friends
    * rely on elsewhere), so gating on ITS result lets aliases through while still catching the
    * real stub shape: a `command Foo is { ??? }` body parses to the SAME empty-aggregate AST as an
    * explicit `{ }` (both hit `TypeParser`'s `undefined(Seq.empty[AggregateContents])`
    * alternative), so "zero fields after resolving" is exactly the stub condition the standing
    * `???` ruling asks us to exempt -- its absent fields must not be read as "no Id(target)
    * field".
    */
  /** A55-style: the aggregate [[Field]]s of a message [[Type]], each paired with the [[Type]] node
    * that actually DECLARES it -- itself for a direct aggregate, or (following the alias chain) the
    * aliased-to `Type` for `command Ship is Shipment`. Needed because [[ResolutionPass]] resolves a
    * field's `UniqueId` type expression while that OWNING Type is `parents.head` (Pass.scala pushes
    * a `Branch` -- which `Type` is -- onto the parent stack for its own children's resolution, so a
    * `Type`'s fields resolve with the Type ITSELF, not its enclosing Context, as the refMap key's
    * parent). Looking a field's resolution up again later requires the SAME parent it was recorded
    * under, so the owning Type must travel with the field, not just `mt`.
    */
  private def fieldsWithOwner(t: Type): Seq[(Field, Type)] =
    t.typEx match
      case ate: AggregateTypeExpression => ate.fields.map(f => f -> t)
      case ate: AliasedTypeExpression =>
        resolution.refMap.definitionOf[Type](ate.pathId).toSeq.flatMap(fieldsWithOwner)
      case _ => Seq.empty[(Field, Type)]

  /** Does [[Field]] `f`, declared on message-Type `owner`, name Processor `p` as its
    * `Id(target)`? Resolved-identity comparison (`eq`), never by name -- looks the field's
    * `UniqueId.entityPath` up in the refMap using `owner` as the key's parent, which is the SAME
    * parent [[ResolutionPass]] recorded it under (see [[fieldsWithOwner]]).
    */
  private def isAddressFieldFor(f: Field, owner: Type, p: Processor[?]): Boolean =
    f.typeEx match
      case uid: UniqueId =>
        resolution.refMap.definitionOf[Processor[?]](uid.entityPath, owner).exists(_ eq p)
      case _ => false

  private def checkTellAddressing(ts: TellStatement, parents: Parents): Unit =
    checkRef[Processor[?]](ts.processorRef, parents).foreach { p =>
      operandType(ts.msg).foreach { mt =>
        val fieldsAndOwners = fieldsWithOwner(mt)
        if fieldsAndOwners.nonEmpty then
          // Match candidates by RESOLVED IDENTITY, not by the last path segment's NAME (Reid,
          // 2026-08-13, overriding the brief's Step 4 pseudocode). Two entities named `Order` in
          // different contexts must not collide: a field typed `Id(A.Order)` is not an address
          // for `entity B.Order`, no matter what its path's last segment reads. Verified: with the
          // name-match version, adding a second same-named entity in a different context turned a
          // legal model's tell into a false ambiguity Error (see the "NOT be fooled by a foreign
          // field..." and "NOT report a false ambiguity..." cases in `TellAddressingTest`).
          val candidates = fieldsAndOwners.collect {
            case (f, owner) if isAddressFieldFor(f, owner, p) => f
          }
          ts.by match
            case Some(name) =>
              check(
                candidates.exists(_.id.value == name.value),
                s"'by ${name.value}' must name a field of ${mt.identify} typed " +
                  s"'Id(${p.id.value})'; candidates are " +
                  (if candidates.isEmpty then "none"
                   else candidates.map(_.id.value).mkString(", ")),
                Error,
                name.loc,
                suggestion = s"Add a field typed 'Id(${p.id.value})' to ${mt.identify}."
              )
            case None =>
              if candidates.size > 1 then
                messages.addError(
                  ts.loc,
                  s"${mt.identify} carries ${candidates.size} fields typed 'Id(${p.id.value})' " +
                    s"(${candidates.map(_.id.value).mkString(", ")}), so which instance this " +
                    s"addresses is ambiguous",
                  suggestion = s"Add 'by ${candidates.head.id.value}' to choose one."
                )
              else if candidates.isEmpty && p.isInstanceOf[Entity] then
                messages.addCompleteness(
                  ts.loc,
                  s"${mt.identify} carries no field typed 'Id(${p.id.value})', so which " +
                    s"${p.id.value} instance this addresses is unspecified",
                  suggestion =
                    s"Add a field typed 'Id(${p.id.value})' to ${mt.identify} and populate it."
                )
      }
    }
  end checkTellAddressing

  private def valueType(v: Value, parents: Parents, lets: Seq[LetStatement]): Option[Type] =
    v match
      case _: LiteralString => None // pseudo-code, untyped
      case _: PromptValue   => None // AI-computed, untyped
      case c: Constructor   => resolution.refMap.definitionOf[Type](c.ref.pathId)
      case call: Call       =>
        // A24: a call's type is the called function's `output` Type (None for an inline-aggregate
        // output or a function with no output — best-effort, so the check is skipped).
        resolution.refMap.definitionOf[Function](call.function.pathId).flatMap { fn =>
          fn.output match
            case Some(tr: TypeRef) => resolution.refMap.definitionOf[Type](tr.pathId)
            case _                 => None
        }
      case ask: Ask =>
        // ALWAYS derivable, never unknown: resolve the query, read the `replies result X` it
        // declares, resolve THAT. A query with no `replies` has no answer type -- which is why
        // `validateValue` reports it as an Error at the ask site rather than leaving a `let`
        // untyped. Mirrors the Call arm, which reads a function's declared `output`.
        askResultType(ask)
      case vr: ValueRef => valueRefType(vr, parents, lets)
      case gv: GetValue =>
        gv.source match
          case ir: InputRef =>
            resolution.refMap
              .definitionOf[Input](ir.pathId)
              .flatMap(in => resolution.refMap.definitionOf[Type](in.takeIn.pathId))
          case sr: StateRef =>
            resolution.refMap
              .definitionOf[State](sr.pathId)
              .flatMap(st => resolution.refMap.definitionOf[Type](st.typ.pathId))
      case _: BooleanExpression => None // A28: a boolean expression denotes no named Type
      // `self`'s type is a SYNTHESIZED Aggregation, not a named Type -- there is no declaration to
      // return here. `valueTypeExpr` computes the real TypeExpression (see its `SelfValue` arm);
      // this arm exists only so the match stays exhaustive.
      case _: SelfValue         => None
      // `initiate`'s type is a SYNTHESIZED UniqueId, not a named Type -- same reasoning as `self`,
      // immediately above. `valueTypeExpr` computes it (see its `Initiate` arm).
      case _: Initiate          => None

  /** A28: the broad category of a [[Value]] for best-effort boolean/comparison checks: `"boolean"`,
    * `"numeric"`, or `"string"`; `None` when it cannot be determined (skip the check). A
    * [[BooleanExpression]] is always boolean; otherwise the value's named [[Type]] is classified by
    * its underlying [[TypeExpression]], following one level of type alias.
    */
  private def valueCategory(v: Value, parents: Parents, lets: Seq[LetStatement]): Option[String] =
    v match
      case _: BooleanExpression => Some("boolean")
      case _ => valueType(v, parents, lets).flatMap(t => typeExprCategory(t.typEx))

  private def typeExprCategory(te: TypeExpression): Option[String] =
    te match
      case _: Bool        => Some("boolean") // Bool <: NumericType, so it must precede NumericType
      case _: NumericType => Some("numeric")
      case _: String_     => Some("string")
      case ate: AliasedTypeExpression =>
        resolution.refMap.definitionOf[Type](ate.pathId).flatMap(t => typeExprCategory(t.typEx))
      case _ => None

  /** A55: the definition a [[ValueRef]] resolved to, straight out of the refMap. `ResolutionPass`
    * walks a ValueRef's path with the SAME engine as every other reference, so a Field, a Type (the
    * whole message named by an on-clause binding) or a Constant can come back. Before A55 this was
    * hand-rolled here by matching only `path.value.last` against the in-scope fields, which is why
    * `garbage.nonsense.conditionRed` used to validate. The refMap key is the path plus the
    * enclosing on-clause/function — the same `parents.head` ResolutionPass keyed it under.
    */
  private def valueRefDefinition(vr: ValueRef, parents: Parents): Option[Definition] =
    parents.headOption.flatMap(p => resolution.refMap.anyDefinitionOf(vr.path, p))

  /** A55: the aggregate [[Field]]s of a [[TypeExpression]], following one level of alias. */
  private def aggregateFieldsOf(te: TypeExpression): Seq[Field] =
    te match
      case ate: AggregateTypeExpression => ate.fields
      case ate: AliasedTypeExpression =>
        resolution.refMap
          .definitionOf[Type](ate.pathId)
          .toSeq
          .flatMap(t => aggregateFieldsOf(t.typEx))
      case _ => Seq.empty[Field]

  /** A55: walk `names` from a starting [[TypeExpression]] through its aggregate fields. Used ONLY
    * for `let`-headed paths — a `let` is not a Definition, so ResolutionPass cannot anchor there
    * and cannot walk the rest for us. Everything else comes from the refMap.
    */
  private def typeExprOfPath(start: TypeExpression, names: Seq[String]): Option[TypeExpression] =
    names.foldLeft(Option(start)) { (acc, n) =>
      acc.flatMap(te => aggregateFieldsOf(te).find(_.id.value == n).map(_.typeEx))
    }

  /** A55: the named [[Type]] a `let`-local denotes — its `let x: T = …` annotation when present,
    * otherwise INFERRED from the bound expression. Inference is required for `let bar = foo;
    * bar.a`, since the annotation is optional and only it was consulted before A55. `priorLets`
    * holds only the locals declared BEFORE `ls`, so a `let` can never see itself and the recursion
    * strictly decreases — a cycle is impossible.
    */
  private def letType(
    ls: LetStatement,
    priorLets: Seq[LetStatement],
    parents: Parents
  ): Option[Type] =
    ls.typeRef
      .flatMap(tr => resolution.refMap.definitionOf[Type](tr.pathId))
      .orElse(valueType(ls.expression, parents, priorLets))

  /** A55: the index in `lets` of the innermost `let` binding `name`, or -1. `let`-locals are the
    * one thing NOT resolved by ResolutionPass: a `let` is not a Definition and is statement-ORDERED
    * (visible only after its declaration, shadowed by inner blocks), which the symbol table does
    * not model. They stay lexical, threaded by [[checkStatementScopes]].
    */
  private def letIndexOf(name: String, lets: Seq[LetStatement]): Int =
    lets.lastIndexWhere(_.identifier.value == name)

  /** A55: the [[TypeExpression]] a [[ValueRef]] denotes — from the lexical `let` scope when its
    * head names a local (which SHADOWS any outer definition of the same name), otherwise from the
    * definition ResolutionPass put in the refMap.
    */
  private def valueRefTypeExpr(
    vr: ValueRef,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression] = Map.empty
  ): Option[TypeExpression] =
    val names = vr.path.value
    if names.isEmpty then None
    // A25: a `foreach` element is typed by the collection it iterates, and the remaining path
    // components walk that type exactly as they walk a `let`'s. Checked BEFORE lets so an element
    // shadows an outer local of the same name, matching the lexical rule `let` already follows.
    else if elements.contains(names.head) then
      typeExprOfPath(elements(names.head), names.tail)
    else
      val idx = letIndexOf(names.head, lets)
      if idx >= 0 then
        val ls = lets(idx)
        val priorLets = lets.take(idx)
        letType(ls, priorLets, parents)
          .map(_.typEx)
          .orElse(valueTypeExpr(ls.expression, parents, priorLets))
          .flatMap(te => typeExprOfPath(te, names.tail))
      else
        valueRefDefinition(vr, parents).flatMap {
          case f: Field    => Some(f.typeEx)
          case t: Type     => Some(t.typEx)
          case k: Constant => Some(k.typeEx)
          case _           => None
        }
      end if
  end valueRefTypeExpr

  /** A55: the [[TypeExpression]] any [[Value]] denotes, for the cases where a named [[Type]] is too
    * narrow (a directly-typed `flag: Boolean` field has no named Type).
    */
  private def valueTypeExpr(
    v: Value,
    parents: Parents,
    lets: Seq[LetStatement]
  ): Option[TypeExpression] =
    v match
      case vr: ValueRef => valueRefTypeExpr(vr, parents, lets)
      // A55/`self`: the SYNTHESIZED Aggregation is the only place `self`'s type is materialized.
      // `let me = self` then `me.id` reaches this ARM through `valueRefTypeExpr`'s
      // `valueTypeExpr(ls.expression, …)` fallback, walked by `typeExprOfPath` exactly like any
      // other let's inferred type -- no special casing needed there.
      case sv: SelfValue =>
        enclosingProcessorOf(parents).map { p =>
          val agg = SelfValue.aggregation(p, pathOf(p))
          sv.field match
            case None    => agg
            case Some(f) => agg.fields.find(_.id.value == f.value).map(_.typeEx).getOrElse(agg)
        }
      // A70/instance-identity: `initiate`'s type is the newly minted `Id(P)` -- a SYNTHESIZED
      // UniqueId, mirroring `self`'s synthesized Aggregation immediately above. `pathOf(p)` (not
      // `init.processor.pathId`, which may be an as-written relative/short path) mirrors
      // `SelfValue.aggregation`'s id field for the same reason: the fully-qualified path is what
      // identifies the resolved processor. `kindKeyword` is left `None`, exactly as the `self.id`
      // field's `UniqueId` is: it disambiguates WRITTEN syntax, and this type is synthesized, not
      // written.
      case init: Initiate =>
        resolution.refMap
          .definitionOf[Processor[?]](init.processor.pathId, parents.head)
          .map(p => UniqueId(At.empty, pathOf(p)))
      case _            => valueType(v, parents, lets).map(_.typEx)

  /** A54/A55: the named [[Type]] a [[ValueRef]] resolves to, if determinable. A bare on-clause
    * binding denotes the whole message, so it yields the message's Type directly; a field yields
    * the Type its (aliased) declaration names; a `let` yields its declared or inferred type.
    */
  private def valueRefType(
    vr: ValueRef,
    parents: Parents,
    lets: Seq[LetStatement]
  ): Option[Type] =
    val names = vr.path.value
    if names.isEmpty then None
    else
      val idx = letIndexOf(names.head, lets)
      if idx >= 0 && names.sizeIs == 1 then letType(lets(idx), lets.take(idx), parents)
      else
        valueRefDefinition(vr, parents) match
          case Some(t: Type) if idx < 0 => Some(t) // the whole message named by a binding
          case _ =>
            valueRefTypeExpr(vr, parents, lets).flatMap {
              case ate: AliasedTypeExpression => resolution.refMap.definitionOf[Type](ate.pathId)
              case _                          => None
            }
      end if
  end valueRefType

  /** A55: whether a [[ValueRef]] resolves — either lexically to a `let`-local in scope, or through
    * the refMap to a definition ResolutionPass walked to. Because the resolver genuinely walks
    * EVERY component, `a.b.c.field` no longer passes on last-component luck.
    */
  private def valueRefResolves(
    vr: ValueRef,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression] = Map.empty
  ): Boolean =
    val names = vr.path.value
    names.nonEmpty && {
      elements.get(names.head) match
        // A25: the head naming an element is NOT enough -- the REST of the path must walk that
        // element's type, or `line.nosuch` resolves as happily as `line.sku`. That is the
        // last-component-matching defect A54 removed, and it would have been reintroduced here.
        //
        // `Anything` is the deliberate exception: it is what the element binds to when the
        // collection itself did not resolve, and that error is already reported at the loop
        // header. Demanding a walk through an unknown type would blame the body for it.
        case Some(_: Anything) => true
        case Some(te)          => typeExprOfPath(te, names.tail).nonEmpty
        case None =>
          letIndexOf(names.head, lets) >= 0 || valueRefDefinition(vr, parents).nonEmpty
    }

  /** A54: validate a [[Value]] — recurse constructors, and confirm value references resolve. Get
    * sources are checked for existence via [[checkRef]].
    */
  private def validateValue(
    v: Value,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression] = Map.empty
  ): Unit =
    v match
      case _: LiteralString => ()
      case _: PromptValue   => () // literal AI prompt, nothing to resolve
      case c: Constructor   => validateConstructor(c, parents, lets, elements)
      case call: Call       => validateCall(call, parents, lets)
      case ask: Ask         => validateAsk(ask, parents)
      case init: Initiate   => checkInitiate(init, parents, lets, elements)
      case vr: ValueRef =>
        if !valueRefResolves(vr, parents, lets, elements) then
          messages.addError(
            vr.loc,
            s"Value reference '${vr.path.format}' is not a 'let'-local, a field of the handled " +
              "message or entity state, or a function input in scope",
            suggestion =
              "Bind it with a 'let', or reference a field of the on-clause message, entity state, " +
                "or the function's 'requires' input."
          )
      case gv: GetValue =>
        gv.source match
          case ir: InputRef => checkRef[Input](ir, parents)
          case sr: StateRef => checkRef[State](sr, parents)
      // `self` is legal only where a Processor encloses it, and only `id`/`version` exist on it.
      // Checked HERE, not from `validateStatement`, so nested occurrences (inside `when`/`match`/
      // `foreach` bodies) are covered too: `validateValue` is reached at any depth via
      // `checkStatementScopes`'s recursion, whereas `validateStatement` only sees the statements
      // the generic Pass dispatcher visits directly -- which does NOT descend into a
      // WhenStatement's `thenStatements`/`elseStatements`, a MatchCase's `statements`, or a
      // ForeachStatement's `doStatements`: those are FIELDS, not `contents`, the same hazard
      // SagaStep/Correlation needed a traversal special-case for (see `Pass.traverse`).
      //
      // A Saga is NOT a Processor -- it is a `VitalDefinition`, not a `Processor`, and its
      // execution identity is deliberately out of scope (see `enclosingProcessorOf`) -- so `self`
      // in a saga step is an Error naming that reason rather than silently resolving to whatever
      // Processor happens to enclose the Saga's Context.
      case sv: SelfValue =>
        enclosingProcessorOf(parents) match
          case None =>
            messages.addError(
              sv.loc,
              "'self' names the running processor instance, so it is only meaningful inside a " +
                "processor (context, entity, projector, repository, streamlet or adaptor)",
              suggestion = "Remove the 'self' reference, or move this into a processor's handler."
            )
          case Some(_) =>
            sv.field.foreach { f =>
              if !SelfValue.fieldNames.contains(f.value) then
                messages.addError(
                  f.loc,
                  s"'self' has no field '${f.value}'; it carries " +
                    SelfValue.fieldNames.map("'" + _ + "'").mkString(" and "),
                  suggestion = s"Use ${SelfValue.fieldNames.map("self." + _).mkString(" or ")}."
                )
            }
      case ic: InvariantCondition =>
        // The invariant must exist; naming an unknown one is an Error rather than becoming a
        // reference to a value that does not exist.
        checkRef[Invariant](ic.ref, parents)
        ic.argument.foreach(a => validateValue(a, parents, lets))
      // NOT checked: whether the invariant declares `requires <type>` and whether a `with` was
      // supplied. Author's ruling 2026-08-04 — a CONDITION asks whether the rule holds and is
      // never rejected either way, unlike `require invariant X`, which APPLIES the rule and so
      // must be handed what the rule reads (`checkRequireArgument`).
      case _: BooleanLiteral        => ()
      case ce: ComparisonExpression =>
        // A28: operands are ref-only Comparands; validate each resolves, then enforce type-safety.
        validateComparand(ce.left, parents, lets)
        validateComparand(ce.right, parents, lets)
        checkComparison(ce, parents, lets)
      case le: LogicalExpression =>
        validateValue(le.left, parents, lets)
        validateValue(le.right, parents, lets)
        checkBooleanOperand(le.left, s"'${le.op.symbol}'", parents, lets)
        checkBooleanOperand(le.right, s"'${le.op.symbol}'", parents, lets)
      case ne: NotExpression =>
        validateValue(ne.expr, parents, lets)
        checkBooleanOperand(ne.expr, "'not'", parents, lets)

  /** A28: require a logical/`not` operand to be boolean. Emits an Error only when the operand's
    * category is clearly non-boolean; an undetermined category is skipped (best-effort).
    */
  private def checkBooleanOperand(
    v: Value,
    what: String,
    parents: Parents,
    lets: Seq[LetStatement]
  ): Unit =
    valueCategory(v, parents, lets) match
      case Some("boolean") => ()
      case Some(other) =>
        messages.addError(
          v.loc,
          s"Operand of $what must be a boolean but is $other",
          suggestion = "Use a comparison, a boolean field, or a boolean literal (true/false)."
        )
      case None => () // undetermined — skip

  /** A17/A55: the broad category (`"boolean"`/`"numeric"`/`"string"`) of a bare [[ValueRef]] used
    * as a `when` condition, or `None` when it cannot be determined. It classifies the reference's
    * [[TypeExpression]] DIRECTLY via [[typeExprCategory]], which is broader than
    * [[valueCategory]]/[[valueRefType]] — those only classify a field whose type is an ALIASED
    * type, and a directly-typed `flag: Boolean` field must classify too. The reference itself is
    * resolved by [[valueRefTypeExpr]] (lexical `let` scope, then the refMap), so — unlike before
    * A55 — every component of the path is walked, not just the last.
    */
  private def whenValueRefCategory(
    vr: ValueRef,
    parents: Parents,
    lets: Seq[LetStatement]
  ): Option[String] =
    valueRefTypeExpr(vr, parents, lets).flatMap(typeExprCategory)

  /** A17: a bare boolean value reference used as a `when` condition must resolve to a Boolean-typed
    * value — a boolean field of the handled message/entity-state/function-input, a boolean
    * `let`-local, or a boolean `constant`. Emits an Error only when the reference's category is
    * clearly non-boolean; an undetermined category (unresolved ref, or a type we cannot classify)
    * is skipped — best-effort, mirroring [[checkBooleanOperand]].
    */
  private def checkWhenValueRef(
    vr: ValueRef,
    parents: Parents,
    lets: Seq[LetStatement]
  ): Unit =
    whenValueRefCategory(vr, parents, lets) match
      case Some("boolean") => ()
      case Some(other) =>
        messages.addError(
          vr.loc,
          s"A 'when' condition must be a Boolean value; '${vr.path.format}' has type $other",
          suggestion =
            "Reference a Boolean field or constant, or use a comparison/logical expression " +
              "(e.g. 'a > b', 'x and y', 'not z')."
        )
      case None => () // undetermined — skip (best-effort)

  // A28's `constantOf` is gone. Comparisons may still compare a field/local against a named
  // `constant` written as a bare path (`count > MaxCount`), but A55 resolves that path in
  // ResolutionPass like every other ValueRef, so the Constant arrives via `valueRefDefinition`
  // instead of a separate, unscoped symbol-table lookup here.

  /** A28: the broad category of a comparison operand ([[Comparand]]). A [[ConstantRef]] resolves
    * via the refMap to a [[Constant]] whose declared `typeEx` is classified; a bare [[ValueRef]]
    * (including one naming a constant) is classified by [[valueRefTypeExpr]], which sees a
    * directly-typed field as well as an aliased one.
    */
  private def comparandCategory(
    c: Comparand,
    parents: Parents,
    lets: Seq[LetStatement]
  ): Option[String] =
    c match
      case cr: ConstantRef =>
        resolution.refMap.definitionOf[Constant](cr.pathId).flatMap(k => typeExprCategory(k.typeEx))
      case gv: GetValue => valueCategory(gv, parents, lets)
      case vr: ValueRef =>
        valueCategory(vr, parents, lets).orElse(whenValueRefCategory(vr, parents, lets))

  /** A28: validate a comparison operand ([[Comparand]]) resolves. A [[ConstantRef]]/[[GetValue]] is
    * checked via [[checkRef]]; a bare [[ValueRef]] must be a `let`-local, an in-scope field, or a
    * named [[Constant]].
    */
  private def validateComparand(c: Comparand, parents: Parents, lets: Seq[LetStatement]): Unit =
    c match
      case cr: ConstantRef => checkRef[Constant](cr, parents)
      case gv: GetValue    => validateValue(gv, parents, lets)
      case vr: ValueRef =>
        if !valueRefResolves(vr, parents, lets) then
          messages.addError(
            vr.loc,
            s"Value reference '${vr.path.format}' is not a 'let'-local, a field of the handled " +
              "message or entity state, a function input, or a constant in scope",
            suggestion =
              "Bind it with a 'let'; reference a field of the on-clause message, entity state, or " +
                "the function's 'requires' input; or declare and reference a 'constant'."
          )

  /** A28: enforce type-safe comparisons. Equality (`==`/`!=`) requires both operands to share a
    * category (identity comparison); ordering (`<`/`>`/`<=`/`>=`) requires an ORDERED type —
    * conservatively, numeric — on both operands. Undetermined categories are skipped (best-effort;
    * an unresolved ref is reported by [[validateComparand]]).
    */
  private def checkComparison(
    ce: ComparisonExpression,
    parents: Parents,
    lets: Seq[LetStatement]
  ): Unit =
    val lc = comparandCategory(ce.left, parents, lets)
    val rc = comparandCategory(ce.right, parents, lets)
    ce.op match
      case ComparisonOperator.EQ | ComparisonOperator.NE =>
        (lc, rc) match
          case (Some(a), Some(b)) if a != b =>
            messages.addError(
              ce.loc,
              s"Cannot compare a $a value to a $b value with '${ce.op.symbol}'",
              suggestion = "Compare operands of the same type (both numeric, both strings, etc.)."
            )
          case _ => ()
      case _ =>
        def requireNumeric(cat: Option[String], operand: Comparand): Unit =
          cat match
            case Some("numeric") => ()
            case Some(other) =>
              messages.addError(
                operand.loc,
                s"Ordering operator '${ce.op.symbol}' requires a numeric operand but got a $other value",
                suggestion =
                  "Order only numeric operands; use '=='/'!=' for equality of non-numeric values."
              )
            case None => ()
        requireNumeric(lc, ce.left)
        requireNumeric(rc, ce.right)

  /** A29: the named [[Type]] a [[MatchSubject]] denotes, or `None` when undeterminable. A bare
    * [[ValueRef]] resolves through the same four-source machinery as any other value; a legacy
    * [[LiteralString]] subject is untyped.
    */
  private def matchSubjectType(
    subject: MatchSubject,
    parents: Parents,
    lets: Seq[LetStatement]
  ): Option[Type] =
    subject match
      case vr: ValueRef     => valueRefType(vr, parents, lets)
      case gv: GetValue     => valueType(gv, parents, lets)
      case _: LiteralString => None

  /** A29: the broad category (`"boolean"`/`"numeric"`/`"string"`) of a [[MatchSubject]] used as the
    * implicit left operand of a [[ComparisonPattern]]. A bare [[ValueRef]] uses the broad
    * [[whenValueRefCategory]] (which classifies directly-typed fields, not just aliased ones).
    */
  private def matchSubjectCategory(
    subject: MatchSubject,
    parents: Parents,
    lets: Seq[LetStatement]
  ): Option[String] =
    subject match
      case vr: ValueRef     => whenValueRefCategory(vr, parents, lets)
      case gv: GetValue     => valueCategory(gv, parents, lets)
      case _: LiteralString => None

  /** A29: the member [[Definition]]s of a CLOSED subject type — the enumerators of an `any of {…}`
    * [[Enumeration]] or the resolved alternant Types of an `one of {…}` [[Alternation]] — following
    * one level of type alias. `None` for open/primitive types (exhaustiveness/closed-membership is
    * intractable there). Membership is later tested by identity (`eq`) against these, so a foreign
    * definition that merely shares a name is not accepted.
    */
  private def closedMemberDefs(t: Type): Option[Seq[Definition]] =
    def ofTypeExpr(te: TypeExpression): Option[Seq[Definition]] = te match
      case e: Enumeration => Some(e.enumerators.toSeq)
      case a: Alternation =>
        Some(a.of.toSeq.flatMap(alt => resolution.refMap.definitionOf[Type](alt.pathId)))
      case ate: AliasedTypeExpression =>
        resolution.refMap.definitionOf[Type](ate.pathId).flatMap(t2 => ofTypeExpr(t2.typEx))
      case _ => None
    ofTypeExpr(t.typEx)

  /** A29: validate a [[MatchStatement]] — resolve the subject; for each case, resolve/type-check
    * the pattern against the subject and validate any guard; then, for a CLOSED subject with no
    * `default`, warn (StyleWarning) about uncovered members. All checks are best-effort: an
    * undeterminable subject type skips type-compat and exhaustiveness entirely.
    */
  private def validateMatch(ms: MatchStatement, parents: Parents, lets: Seq[LetStatement]): Unit =
    // Subject must resolve (a ValueRef reports out-of-scope; a GetValue source is checked).
    validateValue(ms.expression, parents, lets)
    val subjType = matchSubjectType(ms.expression, parents, lets)
    val subjCat = matchSubjectCategory(ms.expression, parents, lets)
    val memberDefs: Option[Seq[Definition]] = subjType.flatMap(closedMemberDefs)
    ms.cases.foreach { mc =>
      mc.pattern match
        case tp: TypePattern => validateTypePattern(tp, subjType, memberDefs)
        case cp: ComparisonPattern =>
          validateComparand(cp.comparand, parents, lets)
          checkPatternComparison(cp, subjCat, parents, lets)
        case _: LiteralPattern => () // legacy pseudo-code, untyped
      // A29: a guard is a structured BooleanExpression (validated as a value) or a bare
      // boolean-typed ValueRef (checked Boolean-typed, mirroring A17's `when`).
      mc.guard.foreach {
        case be: BooleanExpression => validateValue(be, parents, lets)
        case vr: ValueRef          => checkWhenValueRef(vr, parents, lets)
      }
    }
    // Exhaustiveness — StyleWarning, CLOSED subjects only, and only when there is no `default`. Only
    // UNGUARDED type-cases count toward coverage (a guarded case may not fire).
    if ms.default.isEmpty then
      memberDefs.foreach { members =>
        val memberNames = members.map(_.id.value)
        val covered: scala.collection.immutable.Set[String] = ms.cases
          .collect {
            case mc if mc.guard.isEmpty =>
              mc.pattern match
                case tp: TypePattern => Some(tp.typeRef.pathId.value.lastOption.getOrElse(""))
                case _               => None
          }
          .flatten
          .toSet
        val uncovered = memberNames.filterNot(covered.contains)
        if memberNames.nonEmpty && uncovered.nonEmpty then
          messages.addStyle(
            ms.loc,
            s"match on ${subjType.get.identify} is not exhaustive; uncovered: " +
              uncovered.mkString(", "),
            suggestion =
              "Add a case for each uncovered member, or add a 'default' branch to handle the rest."
          )
      }
  end validateMatch

  /** A29: validate a type-case pattern. The name is resolved as a SYMBOL (a Type, an Enumerator, a
    * message type, …) — NOT via `resolveARef[Type]`, which false-errors on an Enumerator. An
    * unresolvable name is an Error. For a CLOSED subject, the resolved symbol must be one of the
    * subject's members BY IDENTITY (so a foreign same-named definition is rejected).
    */
  private def validateTypePattern(
    tp: TypePattern,
    subjType: Option[Type],
    memberDefs: Option[Seq[Definition]]
  ): Unit =
    val name = tp.typeRef.pathId.value.lastOption.getOrElse("")
    val resolved: List[Definition] =
      if tp.typeRef.pathId.value.nonEmpty then
        symbols.lookup[Definition](tp.typeRef.pathId.value.reverse)
      else List.empty[Definition]
    if resolved.isEmpty then
      messages.addError(
        tp.loc,
        s"Unknown type-case '$name'; it does not name a known type, enumerator, or message",
        suggestion =
          "Name a type, an enumerator of the subject's enumeration, an alternant of its " +
            "alternation, or a message subtype; or use a comparison pattern / 'default'."
      )
    else
      memberDefs.foreach { members =>
        // Identity membership: the resolved symbol must be one of the subject's actual members —
        // a foreign definition that only shares the name does not count.
        val isMember = resolved.exists(sym => members.exists(_ eq sym))
        if members.nonEmpty && !isMember then
          messages.addError(
            tp.loc,
            s"Pattern '$name' is not a member of ${subjType.get.identify}; expected one of: " +
              members.map(_.id.value).mkString(", "),
            suggestion =
              "Match an alternant/enumerator of the subject's type, or use 'default' for other cases."
          )
      }
  end validateTypePattern

  /** A29: type-check a [[ComparisonPattern]] with the match subject as the implicit LEFT operand.
    * Mirrors [[checkComparison]]: equality (`==`/`!=`) requires the subject and comparand to share
    * a category; ordering (`<`/`>`/`<=`/`>=`) requires numeric on both. Undetermined categories are
    * skipped (best-effort).
    */
  private def checkPatternComparison(
    cp: ComparisonPattern,
    subjCat: Option[String],
    parents: Parents,
    lets: Seq[LetStatement]
  ): Unit =
    val rc = comparandCategory(cp.comparand, parents, lets)
    cp.op match
      case ComparisonOperator.EQ | ComparisonOperator.NE =>
        (subjCat, rc) match
          case (Some(a), Some(b)) if a != b =>
            messages.addError(
              cp.loc,
              s"Cannot compare a $a subject to a $b value with '${cp.op.symbol}'",
              suggestion = "Compare the subject against a value of the same type."
            )
          case _ => ()
      case _ =>
        def requireNumeric(cat: Option[String], loc: At, what: String): Unit =
          cat match
            case Some("numeric") => ()
            case Some(other) =>
              messages.addError(
                loc,
                s"Ordering operator '${cp.op.symbol}' requires a numeric $what but got a $other value",
                suggestion =
                  "Order only numeric operands; use '=='/'!=' for equality of non-numeric values."
              )
            case None => ()
        requireNumeric(subjCat, cp.loc, "subject")
        requireNumeric(rc, cp.comparand.loc, "operand")
  end checkPatternComparison

  /** A54: best-effort type-compatibility check for a [[Value]] against an expected [[Type]]. Only
    * fires when both the expected type and the value's type resolve; otherwise skipped (an
    * unresolved side is reported elsewhere, and untyped values — literals/prompts — carry no type
    * to check).
    */
  private def checkValueType(
    expected: Option[Type],
    v: Value,
    parents: Parents,
    lets: Seq[LetStatement],
    loc: At,
    what: String
  ): Unit =
    (expected, valueType(v, parents, lets)) match
      case (Some(e), Some(a)) if !(e eq a) =>
        messages.addError(
          loc,
          s"$what value has type ${a.identify} but ${e.identify} is expected",
          suggestion = s"Supply a value of type ${e.identify}."
        )
      case _ => ()
  end checkValueType

  /** Best-effort per-argument type compatibility, shared by a constructor and a call.
    *
    * Both bind arguments to the fields of an aggregate by the same rule — a named argument to the
    * field of that name, a positional one to the field at its index — and both check compatibility
    * only where the field's declared type and the argument's value each resolve to a [[Type]].
    * Written out once per caller, the two copies were free to drift: a rule tightened for
    * constructors would silently not apply to calls.
    *
    * Only the noun differs, and it is a parameter: a constructor fills a `field`, a call fills an
    * `input`.
    */
  private def checkArgumentTypes(
    args: Seq[ConstructorArg],
    fields: Seq[Field],
    fieldNoun: String,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression] = Map.empty
  ): Unit =
    args.zipWithIndex.foreach { case (arg, idx) =>
      val fieldOpt: Option[Field] = arg.name match
        case Some(id) => fields.find(_.id.value == id.value)
        case None     => if idx < fields.size then Some(fields(idx)) else None
      fieldOpt.foreach { field =>
        field.typeEx match
          case ate: AliasedTypeExpression =>
            val expected = resolution.refMap.definitionOf[Type](ate.pathId)
            val actual = valueType(arg.value, parents, lets)
            (expected, actual) match
              case (Some(e), Some(a)) if !(e eq a) =>
                messages.addError(
                  arg.loc,
                  s"Argument for $fieldNoun '${field.id.value}' has type ${a.identify} but " +
                    s"${field.id.value} expects ${e.identify}",
                  suggestion = s"Supply a value of type ${e.identify} for '${field.id.value}'."
                )
              case _ => ()
          case _ => () // primitive/other field type — literals accepted, no check
      }
    }
  end checkArgumentTypes

  /** A54: validate a [[Constructor]] — arg ordering (positional before named), named-arg field
    * existence, arity, and best-effort per-argument type compatibility against the target
    * aggregate's fields. Recurses into argument values.
    */
  private def validateConstructor(
    c: Constructor,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression] = Map.empty
  ): Unit =
    resolution.refMap.definitionOf[Type](c.ref.pathId) match
      case Some(typ) =>
        val fields: Seq[Field] = typ.typEx match
          case ate: AggregateTypeExpression => ate.fields
          case _                            => Seq.empty[Field]
        // Ordering: positional args must precede named args.
        val firstNamed = c.args.indexWhere(_.name.isDefined)
        if firstNamed >= 0 && c.args.drop(firstNamed).exists(_.name.isEmpty) then
          messages.addError(
            c.loc,
            s"In constructor of ${typ.identify}, positional arguments must precede named arguments",
            suggestion =
              "Reorder so all positional arguments come before any 'name = value' argument."
          )
        // Named args must reference real fields.
        c.args.foreach { arg =>
          arg.name.foreach { id =>
            if !fields.exists(_.id.value == id.value) then
              messages.addError(
                arg.loc,
                s"'${id.value}' is not a field of ${typ.identify}",
                suggestion =
                  s"Use one of the fields of ${typ.identify}: ${fields.map(_.id.value).mkString(", ")}."
              )
          }
        }
        // Arity.
        def count(n: Int, word: String): String = s"$n $word${if n == 1 then "" else "s"}"
        if c.args.sizeIs > fields.size then
          messages.addError(
            c.loc,
            s"Constructor of ${typ.identify} has ${count(c.args.size, "argument")} but the type " +
              s"has only ${count(fields.size, "field")}",
            suggestion = s"Supply at most ${count(fields.size, "argument")}."
          )
        // NO `nonEmpty` guard: an EMPTY argument list is a positional arity of zero, and must
        // still match. `command Checkout()` is legal syntax — a constructor of a message with no
        // fields — but against a type that HAS fields it is a mistake, and guarding this branch on
        // `nonEmpty` let exactly that case through silently. Named arguments are exempt because
        // they may legitimately supply a subset.
        else if c.args.forall(_.name.isEmpty) && c.args.sizeIs != fields.size
        then
          messages.addError(
            c.loc,
            s"Constructor of ${typ.identify} has ${count(c.args.size, "positional argument")} but " +
              s"the type has ${count(fields.size, "field")}",
            suggestion =
              s"Supply exactly ${count(fields.size, "positional argument")}, or use named arguments for a subset."
          )
        checkArgumentTypes(c.args, fields, "field", parents, lets, elements)
        // Recurse into argument values (nested constructors, value refs), CARRYING the foreach
        // elements: `send event Shipped(sku = line.sku)` is the shape the whole feature exists
        // for, and dropping them here left the element unresolvable exactly where it is used.
        c.args.foreach(arg => validateValue(arg.value, parents, lets, elements))
      case None => () // unresolved constructor ref reported by ResolutionPass
  end validateConstructor

  /** A24: validate a [[Call]] — resolve the called [[Function]], bind arguments to the fields of
    * its `input` aggregate (positional-before-named ordering, named-field existence, arity,
    * best-effort per-argument type compatibility), and require the function to have an `output` (a
    * call is used to obtain a result). Mirrors [[validateConstructor]]; recurses into argument
    * values.
    */
  private def validateCall(
    call: Call,
    parents: Parents,
    lets: Seq[LetStatement]
  ): Unit =
    resolution.refMap.definitionOf[Function](call.function.pathId) match
      case Some(fn) =>
        val fields: Seq[Field] = fn.input match
          case Some(tr: TypeRef) =>
            resolution.refMap.definitionOf[Type](tr.pathId) match
              case Some(typ) =>
                typ.typEx match
                  case ate: AggregateTypeExpression => ate.fields
                  case _                            => Seq.empty[Field]
              case None => Seq.empty[Field]
          case Some(agg: Aggregation) => agg.fields
          case None                   => Seq.empty[Field]
        // A call is used to obtain a result; a function with no output cannot produce one.
        if fn.output.isEmpty then
          messages.addError(
            call.loc,
            s"${fn.identify} has no 'returns' output, so a call to it produces no value",
            suggestion =
              s"Give ${fn.identify} a 'returns' clause, or do not use its call as a value."
          )
        // Ordering: positional args must precede named args.
        val firstNamed = call.args.indexWhere(_.name.isDefined)
        if firstNamed >= 0 && call.args.drop(firstNamed).exists(_.name.isEmpty) then
          messages.addError(
            call.loc,
            s"In call of ${fn.identify}, positional arguments must precede named arguments",
            suggestion =
              "Reorder so all positional arguments come before any 'name = value' argument."
          )
        // Named args must reference real input fields.
        call.args.foreach { arg =>
          arg.name.foreach { id =>
            if !fields.exists(_.id.value == id.value) then
              messages.addError(
                arg.loc,
                s"'${id.value}' is not an input field of ${fn.identify}",
                suggestion = if fields.isEmpty then s"${fn.identify} takes no input arguments."
                else
                  s"Use one of the input fields of ${fn.identify}: ${fields.map(_.id.value).mkString(", ")}."
              )
          }
        }
        // Arity.
        def count(n: Int, word: String): String = s"$n $word${if n == 1 then "" else "s"}"
        if call.args.sizeIs > fields.size then
          messages.addError(
            call.loc,
            s"Call of ${fn.identify} has ${count(call.args.size, "argument")} but the function " +
              s"takes ${count(fields.size, "input field")}",
            suggestion = s"Supply at most ${count(fields.size, "argument")}."
          )
        else if call.args.nonEmpty && call.args.forall(
            _.name.isEmpty
          ) && call.args.sizeIs != fields.size
        then
          messages.addError(
            call.loc,
            s"Call of ${fn.identify} has ${count(call.args.size, "positional argument")} but the " +
              s"function takes ${count(fields.size, "input field")}",
            suggestion =
              s"Supply exactly ${count(fields.size, "positional argument")}, or use named arguments for a subset."
          )
        checkArgumentTypes(call.args, fields, "input", parents, lets)
        // Recurse into argument values (nested constructors, calls, value refs).
        call.args.foreach(arg => validateValue(arg.value, parents, lets))
      case None => () // unresolved function ref reported by ResolutionPass
  end validateCall

  /** A45: validate a `put` — the value, the output target's existence, and best-effort type
    * compatibility of the value against the resolved [[Output.putOut]].
    */
  private def validatePut(ps: PutStatement, parents: Parents, lets: Seq[LetStatement]): Unit =
    validateValue(ps.value, parents, lets)
    checkRef[Output](ps.output, parents).foreach { output =>
      val expected: Option[Type] = output.putOut match
        case tr: TypeRef      => resolution.refMap.definitionOf[Type](tr.pathId)
        case _: ConstantRef   => None
        case _: LiteralString => None
      val actual = valueType(ps.value, parents, lets)
      (expected, actual) match
        case (Some(e), Some(a)) if !(e eq a) =>
          messages.addError(
            ps.loc,
            s"'put' value has type ${a.identify} but ${output.identify} expects ${e.identify}",
            suggestion = s"Publish a value of type ${e.identify} to ${output.identify}."
          )
        case _ => ()
    }
  end validatePut

  /** A57: validate a `return` — the value and best-effort type compatibility against the enclosing
    * [[Function.output]].
    */
  private def validateReturn(rs: ReturnStatement, parents: Parents, lets: Seq[LetStatement]): Unit =
    validateValue(rs.value, parents, lets)
    parents.collectFirst { case f: Function => f }.foreach { fn =>
      val expected: Option[Type] = fn.output match
        case Some(tr: TypeRef) => resolution.refMap.definitionOf[Type](tr.pathId)
        case _                 => None
      val actual = valueType(rs.value, parents, lets)
      (expected, actual) match
        case (Some(e), Some(a)) if !(e eq a) =>
          messages.addError(
            rs.loc,
            s"'return' value has type ${a.identify} but function '${fn.id.value}' returns ${e.identify}",
            suggestion =
              s"Return a value of type ${e.identify}, or change the function's 'returns'."
          )
        case _ => ()
    }
  end validateReturn

  /** A25/A54: recursively walk `stmts` in lexical order, threading the set of in-scope `let` locals
    * and enclosing `foreach` element names, validating each `foreach` collection AND each value
    * expression (`put`/`return`, and the values inside them) as it is reached. A `let` is visible
    * to later siblings and to statements nested under them; a `foreach` element is visible only
    * within that loop's body. Value validation (constructor arity/names/types, four-source value
    * refs, and put/return type checks) is done here — not in [[validateStatement]] — because it
    * needs the threaded `let` scope, and this walk reaches every statement (top-level and nested)
    * exactly once.
    */
  private def checkStatementScopes(
    stmts: Seq[Statement],
    inScopeLets: Seq[LetStatement],
    parents: Parents,
    // A25: `foreach` elements in scope, WITH their element type. It was a Set[String] -- names
    // only -- which was enough for the one consumer that existed (a nested `foreach` over an outer
    // element) but left the body unable to dereference: `line` was known, `line.sku` was not.
    inScopeElements: Map[String, TypeExpression] = Map.empty
  ): Unit =
    var lets = inScopeLets
    stmts.foreach { stmt =>
      // A70/instance-identity Task 7: initiate/terminate are effects banned in a function body and
      // in an on-activate/on-passivate clause. Checked for EVERY statement in this list, ahead of
      // the per-kind match below -- mirrors `checkStateReadScope`'s placement ahead of
      // `validateStatement`'s per-kind match, but here rather than there, because THIS function
      // (not `validateStatement`) is what recurses into nested when/match/foreach bodies, so this
      // is the placement that reaches a banned statement at any nesting depth.
      checkInstanceEffectScope(stmt, parents)
      stmt match {
      case ls: LetStatement =>
        // A54: validate the bound expression with the scope BEFORE this let (a let can't see itself),
        // then check its type against a declared `let x: T = …`.
        validateValue(ls.expression, parents, lets, inScopeElements)
        checkLocalName(ls.identifier, "'let' local", parents) // A55
        ls.typeRef.foreach { tr =>
          val expected = resolution.refMap.definitionOf[Type](tr.pathId)
          checkValueType(
            expected,
            ls.expression,
            parents,
            lets,
            ls.loc,
            s"'let ${ls.identifier.value}'"
          )
        }
        lets = lets :+ ls
      case ss: SetStatement =>
        // A54: validate the value expression, then check it against the target field/state type.
        validateValue(ss.value, parents, lets, inScopeElements)
        val expected: Option[Type] = ss.field match
          case fr: FieldRef =>
            resolution.refMap.definitionOf[Field](fr.pathId).flatMap { f =>
              f.typeEx match
                case ate: AliasedTypeExpression => resolution.refMap.definitionOf[Type](ate.pathId)
                case _                          => None
            }
          case sr: StateRef =>
            resolution.refMap
              .definitionOf[State](sr.pathId)
              .flatMap(st => resolution.refMap.definitionOf[Type](st.typ.pathId))
        checkValueType(expected, ss.value, parents, lets, ss.loc, s"'set ${ss.field.format}'")
      case s: SendStatement =>
        s.msg match { case c: Constructor => validateValue(c, parents, lets, inScopeElements); case _ => () }
      case s: TellStatement =>
        s.msg match { case c: Constructor => validateValue(c, parents, lets, inScopeElements); case _ => () }
        // A70/instance-identity task 6: reached at ANY depth (this function is the single entry
        // point invoked at every container root AND recursively for when/match/foreach bodies) --
        // mirrors checkTerminate's reachability, immediately below.
        checkTellAddressing(s, parents)
      case s: YieldStatement =>
        s.msg match { case c: Constructor => validateValue(c, parents, lets, inScopeElements); case _ => () }
      // Mirrors YieldStatement, immediately above: `validateStatement`'s ReplyStatement case
      // claims "a Constructor is validated in checkStatementScopes", which was untrue until this
      // arm existed -- a `reply result Foo(x = self.id)` Constructor argument reached NOTHING,
      // found auditing `self`'s coverage (a self reference there was silently unchecked).
      case s: ReplyStatement =>
        s.msg match { case c: Constructor => validateValue(c, parents, lets, inScopeElements); case _ => () }
      case s: MorphStatement =>
        s.value match { case c: Constructor => validateValue(c, parents, lets, inScopeElements); case _ => () }
      case fs: ForeachStatement =>
        validateForeachCollection(fs, lets, inScopeElements, parents)
        // Bind the loop's name(s) to their TYPES for the body's scope -- not merely the names.
        // Without the types `line` resolves and `line.sku` does not, which is the whole point of
        // iterating. An unresolvable collection still binds the names (to `Anything`), because the
        // header's error is already reported and piling "unknown value reference" on top of it
        // would blame the body for a defect above it.
        val collType = foreachCollectionType(fs, lets, inScopeElements, parents)
        checkStatementScopes(
          fs.doStatements.toSeq.collect { case s: Statement => s },
          lets,
          parents,
          inScopeElements ++ foreachBindings(fs, collType)
        )
      case ws: WhenStatement =>
        // A28: type-check a structured BooleanExpression condition (with in-scope `let` locals);
        // the LiteralString/Identifier forms have no expression to check here. A17: a bare boolean
        // ValueRef condition must resolve to a Boolean-typed value.
        ws.condition match
          case be: BooleanExpression => validateValue(be, parents, lets, inScopeElements)
          case vr: ValueRef          => checkWhenValueRef(vr, parents, lets) // A17
          case _                     => ()
        checkStatementScopes(
          ws.thenStatements.toSeq.collect { case s: Statement => s },
          lets,
          parents,
          inScopeElements
        )
        checkStatementScopes(
          ws.elseStatements.toSeq.collect { case s: Statement => s },
          lets,
          parents,
          inScopeElements
        )
      case rs: RequireStatement =>
        // A28: type-check a structured BooleanExpression condition (with in-scope `let` locals);
        // the LiteralString/InvariantRef forms are checked in validateStatement.
        rs.condition match
          case be: BooleanExpression => validateValue(be, parents, lets, inScopeElements)
          case _                     => ()
      case ms: MatchStatement =>
        validateMatch(
          ms,
          parents,
          lets
        ) // A29: subject/pattern/guard resolution + type-compat + exhaustiveness
        ms.cases.foreach { mc =>
          checkStatementScopes(
            mc.statements.toSeq.collect { case s: Statement => s },
            lets,
            parents,
            inScopeElements
          )
        }
        checkStatementScopes(
          ms.default.toSeq.collect { case s: Statement => s },
          lets,
          parents,
          inScopeElements
        )
      case ps: PutStatement    => validatePut(ps, parents, lets)
      case rs: ReturnStatement => validateReturn(rs, parents, lets)
      // A70/instance-identity: reached at ANY depth (this function is the single entry point
      // invoked at every container root AND recursively for when/match/foreach bodies), which is
      // exactly what the nested-`terminate` regression test requires -- mirrors `checkInitiate`'s
      // reachability via `validateValue`.
      case ts: TerminateStatement => checkTerminate(ts, parents, lets, inScopeElements)
      case _                      => ()
      }
    }
  end checkStatementScopes

  /** Classify all collected handlers by behavioral completeness. A handler is:
    *   - Executable: has at least one executable statement (tell, send, morph, set, become, error,
    *     code)
    *   - PromptOnly: has only prompt statements
    *   - Empty: has no statements or only uses ???
    */
  private def classifyHandlers(): Seq[HandlerCompleteness] = {
    handlerParents.toSeq.map { case (handler, parent) =>
      var executableCount = 0
      var promptCount = 0

      handler.clauses.foreach { clause =>
        walkStatements(clause.contents) {
          // `reply` is as executable as `yield`: it answers a query with its declared result,
          // which is exactly the work a query handler exists to do. Omitting it after the 2.0
          // yield/reply split produced 27 false warnings across 22 riddl-models models -- in two
          // flavours the arithmetic predicts exactly: a `do`+`reply` handler counted as
          // PromptOnly, a `reply`-only handler as Empty. The Empty branch's own suggestion
          // already names `reply` as a fix, so a user could follow the advice and still be warned.
          case _: TellStatement | _: SendStatement | _: YieldStatement | _: ReplyStatement |
              _: MorphStatement | _: SetStatement | _: BecomeStatement | _: ErrorStatement |
              _: CodeStatement | _: PutStatement | _: TerminateStatement =>
            // A45: `put` publishes to a UI output — an executable effect. A70/instance-identity:
            // `terminate` ends an instance -- as executable an effect as `tell`. (ReturnStatement
            // is not added here: it only occurs in function bodies, which are classified by
            // validateFunction's statement-non-empty check, not classifyHandlers.)
            executableCount += 1
          case _: PromptStatement =>
            promptCount += 1
          // ENUMERATED, not a catch-all. These are the statements that are neither an effect nor
          // a prompt: control flow, binding, refusal, and `return` (function bodies only, checked
          // by validateFunction instead). Listing them means a NEW statement kind breaks this
          // build under -Werror rather than silently counting as neither -- which is exactly how
          // `reply` produced 27 false warnings after the 2.0 yield/reply split. `Statement` is
          // sealed, so the compiler can hold this promise.
          case _: WhenStatement | _: MatchStatement | _: ForeachStatement | _: LetStatement |
              _: RequireStatement | _: ReturnStatement =>
            ()
        }
      }

      val totalClauses = handler.clauses.size

      val category =
        if executableCount > 0 then BehaviorCategory.Executable
        else if promptCount > 0 then BehaviorCategory.PromptOnly
        else BehaviorCategory.Empty

      HandlerCompleteness(
        handler = handler,
        parent = parent,
        category = category,
        executableCount = executableCount,
        promptCount = promptCount,
        totalClauses = totalClauses
      )
    }
  }

}
