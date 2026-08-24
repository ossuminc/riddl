/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{Keyword, PredefType, PredefTypes}
import com.ossuminc.riddl.language.{Contents, Finder, Messages, *}
import com.ossuminc.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.utils.PlatformContext
import com.ossuminc.riddl.utils.SeqHelpers.*
import com.ossuminc.riddl.utils.*

import scala.collection.mutable
import scala.collection.immutable.Seq
import com.ossuminc.riddl.passes.TellTarget

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
      // [4.1], RULED 2026-08-17: a streamlet is any processor with a NON-ZERO PORTLET COUNT. The
      // graph's node buffer is already every Processor kind, so the old
      // `.collect { case s: Streamlet => s }` narrowing is gone -- but the portlet test is real,
      // not a formality: a processor with no ports is not in a stream.
      processors.toSeq.filter(_.ports.nonEmpty),
      computedHandlerCompleteness,
      deliverableTypes.toMap
    )
  }

  /** The message [[Type]] each `send`/`tell` delivers, filled by [[checkStatementScopes]] — see
    * [[ValidationOutput.deliverableTypes]] for why this pass is the only one that can answer it.
    * Keyed by the statement value: statements are ordinary case classes (only [[Definition]]
    * overrides `equals` structurally-without-contents), and every statement carries its own `At`,
    * so two distinct statements can never collide.
    */
  private val deliverableTypes: mutable.HashMap[Statement, Type] = mutable.HashMap.empty

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
      checkTellDeliverability()
      checkInletsAreReceived(root)
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
    *
    * **[1.2], CLOSED 2026-08-17.** This used to stay on the NARROW `operandType` and could not see
    * through a `ValueRef` at all, because a single flat sweep across the whole root has no notion
    * of which clause a `send`/`tell` came from, let alone that clause's `let` scope. The stated
    * consequence was real: an event emitted ONLY via a `let`-local, state field, function result or
    * `ask` result drew `checkCorrelationEventSources`'s "nothing in the model emits it" while
    * something plainly did.
    *
    * The entry judged the fix to be "walking the root container-by-container the way
    * `checkStatementScopes` already does". That turned out to be unnecessary:
    * `checkStatementScopes` ALREADY does that walk, and since [4.3] it records what each operand
    * resolved to. So the sweep stays flat -- the right shape for a whole-root question -- and only
    * the lookup changed. `EmittedViaLetLocalTest` pins it, and fails with exactly the old message
    * when reverted.
    */
  private def emittedMessageTypes(root: PassRoot): mutable.Set[Type] = {
    val finder = Finder(root.contents)
    val emitted: mutable.Set[Type] = mutable.Set.empty
    def note(t: Option[Type]): Unit = t.foreach(emitted.addOne)
    // [1.2], CLOSED 2026-08-17. The flat sweep stays -- it is the right shape for a whole-root
    // question -- but the RESOLUTION no longer has to be narrow. `deliverableTypes` was filled by
    // `checkStatementScopes` during the traversal, which visits one clause at a time WITH its `let`
    // scope and `foreach` bindings, so a widened operand is already resolved and merely has to be
    // looked up here. This is filled before `postProcess` runs, which is where this is called.
    //
    // That closes the limitation this function's own scaladoc named: an event emitted only via a
    // `let`-local, state field, function result or `ask` result used to be invisible, so a
    // correlation folding it could draw "nothing emits this event" while something plainly did.
    // The fallback to `operandType` remains for the narrow shapes it always handled correctly.
    def noteStatement(stmt: Statement, msg: MessageRef | Constructor | ValueRef): Unit =
      note(deliverableTypes.get(stmt).orElse(operandType(msg)))
    finder.recursiveFindByType[SendStatement].foreach(s => noteStatement(s, s.msg))
    finder.recursiveFindByType[TellStatement].foreach(s => noteStatement(s, s.msg))
    finder.recursiveFindByType[YieldStatement].foreach(s => noteStatement(s, s.msg))
    finder.recursiveFindByType[ReplyStatement].foreach(s => noteStatement(s, s.msg))
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
  /** A message told to a processor that declares no clause able to receive it (Reid, 2026-08-22).
    *
    * *"You can't generate code from something that is underspecified. In fact, this ought to be
    * something found by riddlc."* Reported by riddl-generator, which lowers a cross-processor
    * `tell` into one consumer per (message, sender) pair: with no clause to dispatch to, the
    * consumer's body has nothing to BE, so it emits a hole that no later AI pass can close, because
    * the answer is not in the model. 15 such holes in one generated file from
    * `logistics/warehousing/inventory-control`, which validated CLEANLY under rc.20.
    *
    * **CompletenessWarning, not an Error** — the model is under-specified rather than
    * self-contradictory, and either remedy is legitimate: add the clause, or drop the `tell`. The
    * message names both, because a diagnostic that states only one of two valid fixes pushes
    * authors toward the wrong one.
    *
    * **This is the SENDING end.** Its twin, an inlet whose type nothing receives, is the same defect
    * seen from the RECEIVING end and is `checkInletsAreReceived`. They are not redundant: this one
    * needs a delivery to exist, that one fires on a declared entrance whether or not anything sends
    * to it.
    *
    * **Note the severity asymmetry with `validateAsk`, which Errors on the same shape.** That is
    * deliberate, not drift: an `ask` states a correlation between two halves of one interaction, so
    * an unanswerable ask contradicts itself, while an unreceived `tell` merely goes nowhere.
    *
    * Silent when the message type does not resolve (ref-integrity reports that already), when the
    * target's body is `???` (the standing ruling: a stub has said "do not expect much"), and for
    * the predefined terminators.
    */
  /** An inlet whose type the owning processor receives nowhere (Reid, 2026-08-22).
    *
    * **The question this answers is not the one it was first asked as.** riddl-generator filed it
    * as "an inlet no handler consumes", which relates two things that are never directly related:
    * handlers do not consume, they CONTAIN `on` clauses, and an `on` clause names a MESSAGE TYPE,
    * never an inlet. `Inlet` knows only its own type; nothing in the AST links an inlet to a
    * handler. The relation is INDIRECT, through the type — which is why the check compares the
    * inlet's resolved type against the clauses of the processor that OWNS the inlet.
    *
    * So: `P` declares `inlet I is type T`, a connector may deliver a `T` to it, and `P` has no
    * clause that receives a `T`. `P` declares an arrival point and never says what arriving means.
    *
    * **`on other` satisfies it** (ruled explicitly, and it is the hinge): it states a policy for
    * anything unmatched, which is saying what arriving means, and it is the idiom
    * `Riddl.BottomlessPit` is built from. Requiring a named clause would flag every deliberate
    * generic catch. The accepted cost is a much smaller yield — `on other` is pervasive.
    *
    * **This is the converse of the 2026-08-18 ruling** that an entity which HANDLES messages must
    * declare an inlet; this says one that DECLARES an inlet should handle something.
    *
    * Distinct from `checkUnattachedOutlets`, which asks whether anything is CONNECTED to the
    * portlet — a different question about the same declaration, and already answered.
    */
  private def checkInletsAreReceived(root: PassRoot): Unit = {
    Finder(root.contents).recursiveFindByType[Inlet].foreach { inlet =>
      if !isPredefined(inlet) then
        symbols.parentOf(inlet) match
          // A processor declaring NO handlers at all is a different defect, and one already
          // reported -- `${streamlet.identify} should have a handler`. Firing here too would
          // double-report it, so this check speaks only to the genuinely new case: handlers EXIST
          // and none of them receives what this inlet admits. Note `checkTellDeliverability` does
          // NOT make the same exclusion, and the asymmetry is deliberate: that check is driven by
          // an actual delivery, so naming the specific message that cannot land is actionable even
          // when the target is empty-handed.
          case Some(proc: Processor[?]) if !proc.isEmpty && proc.handlers.nonEmpty =>
            resolution.refMap.definitionOf[Type](inlet.type_.pathId).foreach { inletType =>
              val unreceived = unreceivedMembers(proc, inletType)
              if unreceived.nonEmpty then
                // NAME the members that have no clause (Reid, 2026-08-22). A union inlet is the
                // corpus norm -- `type XEvent is one of {...}` for streaming -- and "declares no
                // handler clause" is both untrue and useless when the processor handles four of a
                // union's nine members. The author needs to know WHICH four are unhandled to
                // decide about them; a bare count would send them to re-derive it by hand.
                val members = alternationMembers(inletType)
                val isUnion = members.size > 1 || !members.headOption.exists(_ eq inletType)
                val names = unreceived.map(_.id.value).sorted.mkString(", ")
                val what =
                  if isUnion then
                    s"declares no handler clause for ${unreceived.size} of its ${members.size} " +
                      s"members ($names), so nothing happens when one of those arrives"
                  else "declares no handler clause that receives it, so nothing happens when one " +
                    "arrives"
                messages.addCompleteness(
                  inlet.errorLoc,
                  s"${inlet.identify} admits ${inletType.identify} but ${proc.identify} $what",
                  suggestion =
                    (if isUnion then s"Add an `on` clause for each of $names to one of "
                     else s"Add an `on` clause for ${inletType.identify} to one of ") +
                      s"${proc.identify}'s handlers, add an `on other` clause if anything arriving " +
                      s"should be handled generically, or remove ${inlet.identify}."
                )
              end if
            }
          case _ => ()
        end match
      end if
    }
  }

  private def checkTellDeliverability(): Unit = {
    collectedTells.foreach { case (ts, target) =>
      if !target.isEmpty && !isPredefined(target) then
        operandType(ts.msg).foreach { msgType =>
          if !receivesMessageType(target, msgType) then
            messages.addCompleteness(
              ts.loc,
              s"${target.identify} is told ${msgType.identify} but declares no handler clause " +
                "that receives it, so the message cannot be delivered",
              suggestion = s"Add an `on` clause for ${msgType.identify} to one of " +
                s"${target.identify}'s handlers, or remove the `tell`."
            )
          end if
        }
      end if
    }
  }

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
          s"'tell' target '${tellTargetLabel(ts)}' is not reachable via any connector; " +
            s"a connector to one of its inlets is required for delivery",
          suggestion =
            s"Add a connector whose 'to' inlet belongs to '${tellTargetLabel(ts)}' so the " +
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
          // A SINK is exempt for the same reason, and it became necessary the moment
          // `checkInletsAreReceived` landed (2026-08-22). That check tells a sink declaring an inlet
          // to say what arriving means; the only honest thing a DISCARDING sink can say is
          // `on other is { do "discard" }` -- there is no executable statement, because discarding
          // is doing nothing, and a sink has no outlet to send on. Without this exemption the two
          // checks form a demand no legal spelling satisfies: fix the first and you trip the second.
          // `Riddl.BottomlessPit` is written in exactly this shape and escapes only by being
          // predefined. Same trap as the adaptor advisory fixed in c075f1af0.
          case BehaviorCategory.PromptOnly
              if !hc.parent.isInstanceOf[Repository] && !isDiscardingSink(hc.parent) =>
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
            suggestion =
              s"Emit ${evt.identify} with a 'send', 'tell', 'yield' or 'reply' from the " +
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
            suggestion =
              s"Apply it with 'require invariant ${inv.id.value} with <expr>', or drop " +
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
        checkStatementScopes(
          oc.statements,
          Seq.empty[LetStatement],
          oc +: parentsAsSeq,
          clauseParameterScope(oc)
        )
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
      // Task 3 / final review: an `on init`/`on term` PARAMETER's type expression reached NO
      // type checking at all -- it was traversed (Pass.scala walks the `parameters` field) and
      // RESOLVED (ResolutionPass's MethodArgument arm), but nothing ever called
      // `checkTypeExpression` on it, so a parameter skipped every cardinality, pattern, range and
      // `Id(kind …)` keyword check a Field of the same type gets. `parentsAsSeq.head` is the
      // on-clause, which is the SAME parent ResolutionPass keyed the parameter's references under
      // (`Pass.traverse` pushes the clause before walking `parameters`).
      case ma: MethodArgument =>
        parentsAsSeq.headOption.collect { case d: Definition => d }.foreach { owner =>
          checkTypeExpression(ma.typeEx, owner, parentsAsSeq)
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
    checkBlockTerminal(onClause.statements)
    checkNoSetAfterMorph(onClause.statements)
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
            val emitted = dischargesOnEveryPath(omc.contents) { (stmt, curLets) =>
              stmt match
                case _: ErrorStatement | _: RequireStatement => true
                // send/tell resolve their operand WIDENED (a state field/let-local/function
                // result/ask result counts as emitting the event, not only a keyword-led ref) --
                // yield's operand is still MessageRef | Constructor only until Task 2, so it stays
                // on the narrow (but here structurally sufficient) operandMessageKind.
                case s: SendStatement =>
                  widenedOperandMessageKind(s.msg, parents, curLets).contains(
                    AggregateUseCase.EventCase
                  )
                case t: TellStatement =>
                  widenedOperandMessageKind(t.msg, parents, curLets).contains(
                    AggregateUseCase.EventCase
                  )
                case y: YieldStatement =>
                  operandMessageKind(y.msg).contains(AggregateUseCase.EventCase)
                case _ => false
            }
            if !emitted then
              messages.addCompleteness(
                omc.errorLoc,
                s"Command processing in ${entity.identify} should result in sending an event",
                suggestion =
                  "Send, tell, or yield an event from this command handler, e.g. 'send event SomethingHappened to outlet ...' or 'yield event SomethingHappened'."
              )
          case AggregateUseCase.QueryCase =>
            // EVERY path, and a refusal counts (Reid, 2026-08-16: "queries SHOULD be answered,
            // however, it is possible to let them refuse as well"). This makes the query rule
            // exactly parallel to the command rule above rather than stricter than it.
            //
            // It used to ask whether a reply appeared ANYWHERE in the clause, so
            // `when ready then reply result R end` -- with no `else` -- was accepted while
            // answering nothing on the other branch. That is not a style matter: `ask` is defined
            // as taking the value a `reply` provides, so an unanswered path leaves the caller
            // waiting. Same weakness `checkYieldConformance` and the command check both had.
            //
            // REPLIES, not yields, is the canonical spelling as of 2.0 -- but both are accepted
            // here, because a `yield result` is already an Error from `checkResponsePairing` and
            // reporting it twice helps nobody.
            val answered = dischargesOnEveryPath(omc.contents) { (stmt, curLets) =>
              stmt match
                case _: ErrorStatement | _: RequireStatement => true
                case s: SendStatement =>
                  widenedOperandMessageKind(s.msg, parents, curLets)
                    .contains(AggregateUseCase.ResultCase)
                case t: TellStatement =>
                  widenedOperandMessageKind(t.msg, parents, curLets)
                    .contains(AggregateUseCase.ResultCase)
                // WIDENED for replies too, since 2026-08-16. The comment that used to stand here
                // said reply/yield operands "stay MessageRef | Constructor only until Task 2" --
                // Task 2 has landed, `ReplyStatement.msg` is `MessageRef | Constructor | ValueRef`,
                // and leaving these narrow made the CANONICAL spelling invisible: riddl-models
                // writes `let r: type X.Result = prompt(…)` then `reply r`, which the narrow
                // `operandMessageKind` cannot resolve. That produced 10 false "should result in a
                // reply" warnings across 6 models on handlers that plainly do reply.
                case r: ReplyStatement =>
                  widenedOperandMessageKind(r.msg, parents, curLets)
                    .contains(AggregateUseCase.ResultCase)
                case y: YieldStatement =>
                  widenedOperandMessageKind(y.msg, parents, curLets)
                    .contains(AggregateUseCase.ResultCase)
                case _ => false
            }
            if !answered then
              messages.addCompleteness(
                omc.errorLoc,
                s"Query processing in ${entity.identify} should result in a reply or sending a result",
                suggestion =
                  "Reply with a result on every path, or refuse, e.g. 'reply result QueryResult' or 'error \"not available\"'."
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
    * For a [[ValueRef]] this ONLY resolves an on-clause binding — the refMap key
    * `ResolutionPass.resolveValueRef` records for one. It deliberately does NOT resolve a
    * state-record field, `let`-local, function result or `ask` result, even though
    * `checkMessageOperandSource` accepts those as legal `send`/`tell` operands (the
    * message-value-source widening, 2026-08-14): most of this function's callers
    * (`checkResponsePairing` via `operandMessageKind`, the `yields`/`replies` conformance loop)
    * take a `yield`/`reply` operand, whose AST type is still `MessageRef | Constructor` — a
    * `ValueRef` is structurally impossible there until Task 2 — so widening THIS function would
    * thread `parents`/`lets` through call sites that can never exercise the new arm. Callers that
    * DO need the widened resolution for a `send`/`tell` operand use [[widenedOperandType]] /
    * [[widenedOperandMessageKind]] instead, which take the scope explicitly.
    */
  private def operandType(m: MessageRef | Constructor | ValueRef): Option[Type] = m match
    case mr: MessageRef => resolution.refMap.definitionOf[Type](mr.pathId)
    case c: Constructor => resolution.refMap.definitionOf[Type](c.ref.pathId)
    case vr: ValueRef   => resolution.refMap.definitionOf[Type](vr.path)

  /** A54/A56: the [[AggregateUseCase]] of a widened message operand — a bare ref, a constructor
    * whose ref names the constructed message/record, or a binding named by the enclosing on-clause.
    *
    * **Optional on purpose.** A keyword-led ref carries its kind syntactically, but a binding's
    * kind is only known once resolved, and [[AggregateUseCase]] has no "unknown" member to fall
    * back to. Returning a wrong kind here would silently mis-answer the event-sourcing rules, so an
    * unresolved binding answers `None` and every caller's `contains` reads it as "not that kind".
    *
    * Same narrow-on-purpose note as [[operandType]] applies to its `ValueRef` arm — see
    * [[widenedOperandMessageKind]] for the `send`/`tell`-specific widened version.
    */
  private def operandMessageKind(m: MessageRef | Constructor | ValueRef): Option[AggregateUseCase] =
    m match
      case mr: MessageRef => Some(mr.messageKind)
      case c: Constructor => Some(c.ref.messageKind)
      case _: ValueRef =>
        operandType(m).flatMap(_.typEx match
          case auc: AggregateUseCaseTypeExpression => Some(auc.usecase)
          case _                                   => None)

  /** Task-1-review-round-1: the `send`/`tell`-aware counterpart to [[operandType]] — resolves a
    * `ValueRef` operand through [[valueRefType]], the SAME A55/lifecycle-parameter walk
    * `checkMessageOperandSource` uses, so a state-record field, `let`-local, function result or
    * `ask` result resolves here exactly as it resolves there. A `MessageRef`/`Constructor` operand
    * is unaffected — delegated straight to `operandType`, preserving its syntactic-keyword
    * semantics unchanged.
    *
    * Exists because [[operandType]] itself stays narrow for callers that structurally cannot
    * receive a widened `ValueRef` (see its doc); this is for the ones that CAN: `send`/`tell`
    * completeness checks and `checkTellAddressing`.
    *
    * `elements` (`foreach` bindings) IS threaded, and the claim that used to stand here — "none of
    * this function's three call sites resolve an operand from inside a `foreach` body, so there is
    * no position at which an element binding could be in scope" — was FALSE. `checkTellAddressing`
    * is called from `checkStatementScopes`, which recurses into `when`/`match`/`foreach` bodies
    * precisely so it can thread those bindings; its own call site says it is "reached at ANY
    * depth". So `foreach s in field batch.ships { tell s to entity Order }` resolved its operand to
    * nothing, and every addressing check — the `by`/ambiguity Errors and the three completeness
    * checks — skipped it in silence.
    *
    * The parameter is DEFAULTED because the remaining call sites are walks that genuinely have no
    * element scope to offer (`classifyHandlers`' event/result sweeps, `checkYieldConformance`);
    * passing `Map.empty` there is the truth, not a shortcut. Only the `checkTellAddressing` path
    * has real bindings to hand over.
    */
  private def widenedOperandType(
    m: MessageRef | Constructor | ValueRef,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression] = Map.empty[String, TypeExpression]
  ): Option[Type] = m match
    case vr: ValueRef => valueRefType(vr, parents, lets, elements)
    case other        => operandType(other)

  /** `forward` is legal ONLY where there is something to delegate (author's ruling, 2026-08-19):
    * a command that declares `yields`, or a query that declares `replies`.
    *
    * **You cannot delegate an event or a result.** Those record what happened; they owe no answer,
    * so there is no obligation for a `forward` to discharge and the statement would be claiming to
    * pass on a responsibility that does not exist. A command or query with NO declaration is
    * rejected for the same reason -- nothing is owed.
    *
    * The operand's TYPE must match the handled message. Values are not compared: a handler may
    * adjust field contents -- incrementing a counter, say -- and still be forwarding "the same
    * message". Alias chains are followed on both sides, so the corpus's `type X is Y.Z` house
    * style is not condemned.
    */
  private def checkForward(
    f: ForwardStatement,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    val handled: Option[Type] = parents
      .collectFirst { case omc: OnMessageClause if omc.msg.nonEmpty => omc }
      .flatMap(omc => resolution.refMap.definitionOf[Type](omc.msg.pathId))

    val delegable: Boolean = handled.exists { h =>
      h.typEx match
        case auc: AggregateUseCaseTypeExpression =>
          (auc.usecase == AggregateUseCase.CommandCase ||
            auc.usecase == AggregateUseCase.QueryCase) && auc.yields.isDefined
        case _ => false
    }

    if !delegable then
      messages.addError(
        f.loc,
        "'forward' is only allowed in a clause handling a command that declares 'yields' or a " +
          "query that declares 'replies'; there is no response obligation here to delegate",
        suggestion = "Use 'send' or 'tell' to transmit a message that discharges nothing. " +
          "'forward' says the declared response is produced by whatever handles this message " +
          "downstream, so it needs a declared response to speak about -- an event or a result " +
          "records what happened and owes no answer."
      )
    else
      // Shape, not contents. Both sides expand through aliases; `expandType` guards cycles by
      // reference identity for the reason recorded at its definition.
      for
        h <- handled
        sent <- widenedOperandType(f.msg, parents, lets, elements)
      do
        val handledSide = expandType(h, throughAlternations = false, Nil)
        val sentSide = expandType(sent, throughAlternations = false, Nil)
        if !sentSide.exists(x => handledSide.exists(_ eq x)) then
          messages.addError(
            f.loc,
            s"'forward' must pass on the message this clause handles: ${h.identify} is handled " +
              s"but ${sent.identify} is forwarded",
            suggestion = s"Forward the handled message, or a value of ${h.identify}. To transmit " +
              "something else use 'send' or 'tell' -- that is a different message, not a delegation."
          )
        end if
      end for
    end if
  end checkForward

  /** Expand a type to everything it stands for, following alias chains and — when asked —
    * alternation members.
    *
    * REFERENCE IDENTITY throughout (`eq`, a `List`, never a `Set`). `Definition` overrides `equals`
    * structurally, so a `Set` would fuse two DISTINCT types that happen to have identical contents
    * and silently truncate a legitimate chain. Same reason `fieldsWithOwner` carries a visited list
    * rather than a `Set`.
    *
    * The cycle guard is not hypothetical: `type A is B` / `type B is A` killed the stack in rc.14.
    */
  private def expandType(t: Type, throughAlternations: Boolean, seen: List[Type]): List[Type] =
    if seen.exists(_ eq t) then Nil
    else
      val deeper: List[Type] = t.typEx match
        case ate: AliasedTypeExpression =>
          resolution.refMap
            .definitionOf[Type](ate.pathId)
            .toList
            .flatMap(next => expandType(next, throughAlternations, t :: seen))
        case alt: Alternation if throughAlternations =>
          alt.of.toSeq.toList.flatMap { member =>
            resolution.refMap
              .definitionOf[Type](member.pathId)
              .toList
              .flatMap(next => expandType(next, throughAlternations, t :: seen))
          }
        case _ => Nil
      t :: deeper

  /** A `send`'s message must be ADMITTED by the portlet's DECLARED type (riddl-generator, measured
    * 2026-08-19; author ruled Error the same day).
    *
    * It was 299 of 386 remaining javac errors in reactive-bbq — 77% — across 55 message types sent
    * to outlets that do not admit them, in a model validating 100% clean. The declared type is the
    * contract the connector and every downstream consumer are built on: riddlg lowers an
    * alternation to a sealed interface, so an outlet becomes `Emitter<BarEvent>` and a non-member
    * `send` has no lowering at all.
    *
    * ERROR, not a warning: unlike the non-persistent cross-context connector, there is no reading
    * under which this is a deployment knowingly accepting a weaker guarantee. The consumer on the
    * far end is typed by the portlet's type and cannot receive the value.
    *
    * BOTH portlet kinds, because `send` names either and both declare a type. This is NOT the
    * symmetric inlet-side check riddl-generator also asked about — the author ruled that delivery
    * is matched to a handler's `on` clause and an unmatched message is a no-op, and there is no
    * `receive X from inlet Foo` to attach such a check to. That implicitness is deliberate: it is
    * what keeps generator implementations free to choose how delivery happens.
    *
    * SILENT when either side is not statically determinable — an operand whose type cannot be
    * resolved reports nothing rather than guessing, the same conservative rule `checkTerminate`
    * follows. Both sides are expanded through aliases (reactive-bbq types its ports as
    * `type SupplierEvent is SupplierSystem.ShipmentDelivered`), so the corpus's ordinary shape is
    * admitted rather than newly condemned.
    */
  private def checkTransmittedPortletType(
    loc: At,
    msg: MessageRef | Constructor | ValueRef,
    portlet: PortletRef[?],
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    // TWO-ARG lookup, and the parent is not optional. The refMap keys on (pathId, PARENT), so the
    // single-argument form -- which is what `operandType` can use for a message, because a message
    // ref is registered globally -- answers None for a portlet every time. Instrumented, not
    // reasoned: `portletDef=None` on all six fixtures while the message side resolved fine. Same
    // trap CLAUDE.md already records for an adaptor's `referent` and for `resolveIdTarget`.
    val declaredRef: Option[TypeRef] =
      parents.headOption
        .flatMap(p => resolution.refMap.definitionOf[Portlet](portlet.pathId, p))
        .flatMap {
          case i: Inlet  => Some(i.type_)
          case o: Outlet => Some(o.type_)
        }
    for
      sent <- widenedOperandType(msg, parents, lets, elements)
      ref <- declaredRef
      declared <- resolution.refMap.definitionOf[Type](ref.pathId)
    do
      val admitted = expandType(declared, throughAlternations = true, Nil)
      val offered = expandType(sent, throughAlternations = false, Nil)
      if !offered.exists(o => admitted.exists(_ eq o)) then
        messages.addError(
          loc,
          s"${portlet.format} is declared as ${declared.identify}, which does not admit " +
            s"${sent.identify}",
          suggestion = s"Transmit a message the portlet's declared type admits, or widen " +
            s"${declared.identify} — if it is an alternation, add ${sent.identify} as a member. " +
            "The consumer on the other end of this connection is typed by the declared type and " +
            "cannot receive anything else."
        )
      end if
    end for
  end checkTransmittedPortletType

  /** Publish what a `send`/`tell` delivers, so a LATER pass does not have to re-derive it — and,
    * for a `ValueRef` operand naming a `let`-local, could not.
    *
    * Recorded HERE, in `checkStatementScopes`, because this is the single point reached at any
    * depth (`when`/`match`/`foreach` bodies included) WITH the lexical `let` scope and `foreach`
    * element bindings in hand. A pass that finds statements by a flat sweep — as `MessageFlowPass`
    * does — has the statement but not the scope, which is exactly why its own lookup could only
    * ever see operands the refMap already held.
    *
    * Silence is meaningful: an operand whose type is not statically determinable records nothing,
    * so a consumer can tell "not determinable" from "no such statement". It never records a guess.
    */
  private def recordDeliverableType(
    s: Statement,
    msg: MessageRef | Constructor | ValueRef,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    widenedOperandType(msg, parents, lets, elements).foreach(t => deliverableTypes.put(s, t))

  /** The `widenedOperandType`-based counterpart to [[operandMessageKind]] — see its doc for why the
    * split exists. Filters through [[typeExprMessageKind]] so a widened resolution that lands on a
    * Record/Type/Graph/Table (not one of the four real messages) reads as `None`, matching
    * `checkMessageOperandSource`'s own admission rule.
    */
  private def widenedOperandMessageKind(
    m: MessageRef | Constructor | ValueRef,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression] = Map.empty[String, TypeExpression]
  ): Option[AggregateUseCase] = m match
    case vr: ValueRef =>
      valueRefType(vr, parents, lets, elements)
        .flatMap(t => typeExprMessageKind(t.typEx))
    case other => operandMessageKind(other)

  /** `yield` emits an EVENT; `reply` answers with a RESULT. Enforce the pairing.
    *
    * RIDDL has two message pairings -- command/event and query/result -- and until 2.0 `yield`
    * spelled both while `reply` was a deprecated synonym for it. Reid split them (2026-08-08) so a
    * handler body says which half of the language it is in, and so `ask` has something to name: the
    * value an `ask` produces is the one a `reply` provides.
    *
    * Checked here rather than in the parser because the two statements are structurally identical
    * -- only the message KIND differs -- and `operandMessageKind` reads that kind from the ref
    * subclass (`EventRef`/`ResultRef`/...), so no resolution is needed but the message can still
    * name both halves. A `Constructor` operand carries its kind through `c.ref.messageKind`, so
    * this covers both operand shapes.
    *
    * `None` means the kind is not recoverable (a ValueRef whose type has not resolved); stay silent
    * there rather than guess -- other checks report the unresolved reference.
    */
  /** Task 2: the operand may now be a `ValueRef`. `operandMessageKind` answers `None` for one it
    * cannot resolve narrowly, and `foreach` then skips -- so a `let`-bound or state-field operand
    * is simply not PAIRING-checked here rather than being wrongly reported. Its type is still
    * checked, by `checkYieldConformance`, which has the parents needed to resolve it widely, and
    * "is it a message at all" is `checkMessageOperandSource`'s job.
    */
  private def checkResponsePairing(
    msg: MessageRef | Constructor | ValueRef,
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
          msg match
            case mr: MessageRef => mr.loc
            case c: Constructor => c.loc
            case vr: ValueRef   => vr.loc
          ,
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

  /* REMOVED 2026-08-14 -- `checkOnTermLeadingParameter`, which required `on term`'s first
   * parameter to be `Id(<enclosing processor>)`.
   *
   * The rule reasoned that `on term` is invoked from OUTSIDE the instance, so the caller must say
   * which one. True, but it does not follow that the CLAUSE must declare it: `self` is in scope
   * for the whole body and stays live to the very end of it, so `self.id` already names the
   * instance being terminated. The requirement therefore made the author restate what the
   * language supplies, and made the argumentless `on term` -- the form Reid expects to be
   * COMMON -- a hard Error.
   *
   * It is removed, not relaxed to "if a parameter is present it must be an Id": a termination
   * reason is an ordinary thing to pass and has no business being an id. Arity and per-argument
   * types are still checked, by `checkLifecycleInvocation`, which already handled the
   * zero-declared/zero-supplied case correctly.
   *
   * The knock-on is recorded at `StatementParser.terminateStatement`: this requirement was the
   * SOLE reason a no-argument `terminate` was unreachable, and it was why the bare `terminate P`
   * form had been removed. Both came back together.
   *
   * The resolved-identity lesson the check carried (`eq` against a refMap lookup, never the
   * path's last segment) is NOT lost with it -- it lives on in `isAddressFieldFor`, where
   * `TellAddressingTest` pins it with the same foreign-same-named-entity fixture.
   */

  /** A56 (widened by the message-value-source design, 2026-08-14): check a `tell`/`send` operand
    * that names a VALUE rather than a keyword-led message ref — `tell p to entity F`, `send
    * order.lastEvent to outlet Bar`.
    *
    * Originally A56 asked only whether `vr` was an on-clause binding, via the same refMap key
    * `ResolutionPass` uses for one (`refMap.definitionOf[Type](vr.path)`). That missed every other
    * legal source: a state-record field, a `let`-local, a function result, or an `ask` result. Each
    * of those resolves through [[valueRefTypeExpr]] — the SAME A55/lifecycle-parameter walk every
    * other bare `ValueRef` uses (elements, then lets, then the refMap) — not through the on-clause
    * binding's Type key alone. So the rule is now one probe covering every source: `vr` must
    * resolve to a [[TypeExpression]] that IS, or ALIASES to (see [[typeExprMessageKind]]), a
    * command/event/query/result [[AggregateUseCaseTypeExpression]]. Nothing else can supply a
    * message value, so an unresolved or wrongly-shaped operand is an Error, not a warning.
    *
    * `self` is special-cased FIRST. It is a synthesized Aggregation (`id`/`version`, see
    * [[SelfValue]]), not a message, and would otherwise fall through to the generic "does not name
    * a message value" Error — true, but not the reason, and not what helps the author fix it.
    * Guarded on `elements`/`lets` so a local that happens to be named `self` (shadowing) is not
    * misreported.
    *
    * This check is owned by validation, not the resolver, for the reason recorded in
    * `ResolutionPass.quietly` — a ValueRef may legitimately fail to resolve there (a `let`-local is
    * lexical and invisible to the symbol table), so the resolver stays quiet and the diagnostic is
    * issued here where the operand's meaning is known.
    *
    * Scope: `send` and `tell` only. `yield`/`reply` compare their operand against the clause's
    * declared `yields`/`replies` and `morph` against a record, not a message — both are widened
    * separately.
    */
  private def checkMessageOperandSource(
    vr: ValueRef,
    statement: String,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    val names = vr.path.value
    if names.sizeIs == 1 && names.head == "self" &&
      !elements.contains("self") && letIndexOf("self", lets) < 0
    then
      messages.addError(
        vr.loc,
        s"'self' in this '$statement' is the synthesized instance record (its 'id' and " +
          "'version'), not a message, so there is no message to deliver",
        suggestion = s"Send an actual message, e.g. '$statement event SomethingHappened(...) " +
          s"to …', or send one of its fields, e.g. '$statement self.id to …', if a value (not a " +
          "message) is what the target expects."
      )
    else
      valueRefTypeExpr(vr, parents, lets, elements) match
        case Some(te) if typeExprMessageKind(te).nonEmpty => () // a legal message value
        case Some(te) =>
          messages.addError(
            vr.loc,
            s"'${vr.path.format}' in this '$statement' names a value of type '${te.format}', not " +
              "a command, event, query or result, so there is no message to deliver",
            suggestion = s"Name a value whose type is a command, event, query or result, or name " +
              s"the message explicitly, e.g. '$statement command SomeCommand to …'."
          )
        case None =>
          messages.addError(
            vr.loc,
            s"'${vr.path.format}' in this '$statement' does not name a message value — legal " +
              "sources are a state-record field, an on-clause binding, a 'let'-local, a function " +
              "result, or an 'ask' result",
            suggestion = s"Bind the handled message first, e.g. " +
              s"'on ${vr.path.format}: command SomeCommand is { $statement ${vr.path.format} to " +
              s"… }', declare a 'let ${vr.path.format} = …' local naming a message value, or " +
              s"name the message explicitly, e.g. '$statement command SomeCommand to …'."
          )
  end checkMessageOperandSource

  /** Task 2: the record-side counterpart of [[checkMessageOperandSource]], for `morph … with <v>`.
    *
    * The admission rule is DIFFERENT and deliberately so: a morph carries the record that types the
    * target state (A9b), not a message, so the message-kind test would reject every correct use.
    * What is checked instead is that the name resolves at all, and -- when the target state's
    * record type is known -- that the value has THAT type. The second half is the morph analogue of
    * `checkYieldConformance`: without it `morph … to state S with <any resolvable value>` would
    * validate, which is how a generator ends up writing the wrong record into a state.
    */
  private def checkMorphOperandSource(
    vr: ValueRef,
    ms: MorphStatement,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    valueRefTypeExpr(vr, parents, lets, elements) match
      case None =>
        messages.addError(
          vr.loc,
          s"'${vr.path.format}' in this 'morph' does not name a value in scope",
          suggestion = "Name a value whose type is the target state's record — a state field, a " +
            "'let' local, a function result — or name the record explicitly, e.g. " +
            "'morph … with record SomeRecord'."
        )
      case Some(te) =>
        val wanted: Option[Type] = resolution.refMap
          .definitionOf[State](ms.state.pathId)
          .flatMap(st => resolution.refMap.definitionOf[Type](st.typ.pathId))
        wanted.foreach { want =>
          // Compared by resolved type expression, not by name: two contexts may each declare a
          // record called `Data`. An unresolved `te` is left to the branch above.
          val ok = te match
            case ate: AggregateTypeExpression => ate == want.typEx
            case ate: AliasedTypeExpression =>
              resolution.refMap.definitionOf[Type](ate.pathId).exists(_ eq want)
            case other => other == want.typEx
          if !ok then
            messages.addError(
              vr.loc,
              s"'${vr.path.format}' has type '${te.format}', but state '${ms.state.format}' is " +
                s"typed by ${want.identify}",
              suggestion =
                s"Morph with a value of ${want.identify}, or morph to a state typed by " +
                  s"'${te.format}'."
            )
          end if
        }

  /** Task 4 of the message-value-source design: a BARE keyword-led operand — `send event Bar to …`,
    * `tell command Ship to …`, `yield event Bar`, `reply result Res`, `morph … with record Data` —
    * names the message or record TYPE and says NOTHING about where the value comes from. riddlg
    * measured the consequence on reactive-bbq: 659 of 1088 `AI FILL` holes (60.6%) are exactly this
    * shape, and 98.2% counting the `morph` record analogue. Each becomes a `null` in generated code
    * — worse than a missing one, because it runs.
    *
    * **An ERROR as of 2026-08-14 — this is design D3's end state, reached.** It shipped as a
    * [[CompletenessWarning]] because riddl-models held 14,730 bare refs and ZERO uses of the
    * constructor form, so an Error would have invalidated every message-sending statement in all
    * 189 models at once. Reid lifted the block once riddl-models began building against this
    * branch's staged build: *"riddl-models is working on it from the same riddl version we are
    * using; yes, make the flip, riddl-models should be corrected soon."* Shipping the Error is what
    * gives them the diagnostics to migrate against.
    *
    * **So the corpus tests are RED until riddl-models lands its migration, deliberately** — on top
    * of the two already red by design. Do not read a red corpus as a regression while both are
    * outstanding, and do not soften this check to green them.
    *
    * **A FIELD-LESS message is exempt** (design Q1, ruled 2026-08-14). `event Started is { }` has
    * no data, so the type fully determines the value and there is nothing for the author to source;
    * warning on it is the noise the standing `???` ruling exists to prevent. The exemption falls
    * out of the same observation `checkTellAddressing` records: a `???` body parses to the SAME
    * empty aggregate as an explicit `{ }`, so "zero fields after resolving" covers the stub shape
    * too.
    *
    * Resolution goes through [[aggregateFieldsOf]], which FOLLOWS the alias chain, because
    * `command Ship is Shipment` is riddl-models' house style — reading its (absent) direct fields
    * would exempt the majority of the corpus by accident. An operand whose type does not resolve at
    * all is left alone: `validateStatement`'s `checkRef[Type]` already reports that, and piling a
    * completeness warning on top of an unresolved reference blames the author twice for one defect.
    */
  private def checkBareMessageOperand(ref: AggregateRef, statement: String): Unit =
    resolution.refMap.definitionOf[Type](ref.pathId).foreach { t =>
      val fields = aggregateFieldsOf(t.typEx)
      if fields.nonEmpty then
        val what = if ref.messageKind == AggregateUseCase.RecordCase then "record" else "message"
        messages.addError(
          ref.loc,
          s"'${ref.format}' in this '$statement' names a $what type, not a value, so nothing says " +
            s"where its ${fields.size} field(s) come from",
          suggestion = s"Construct the $what in place, e.g. '$statement ${ref.format}(" +
            s"${fields.head.id.value} = …)', or name a value of that type — an on-clause " +
            "binding, a state field, a 'let' local, a function result or an 'ask' result."
        )
      end if
    }
  end checkBareMessageOperand

  /** A54/A56: the NAME of the message an operand denotes. For a ref or constructor that is the last
    * path component; for a binding it is the resolved Type's id, since the binding's own path names
    * the local (`p`), not the message.
    */
  private def operandMessageName(m: MessageRef | Constructor | ValueRef): String = m match
    case vr: ValueRef => operandType(vr).map(_.id.value).getOrElse("")
    case other: (MessageRef | Constructor) =>
      operandPathId(other).value.lastOption.getOrElse("")

  /** A54: the [[PathIdentifier]] of a KEYWORD-LED message operand (the bare ref's, or the
    * constructor ref's).
    *
    * Stays narrow on purpose. A `ValueRef` operand has a path too, but it names a VALUE rather than
    * the message type, so feeding it to the same `refMap.definitionOf[Type]` lookup would answer
    * with the wrong definition or with nothing. Callers that must handle a widened operand resolve
    * it with `widenedOperandType` instead — see `checkYieldConformance`.
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
    * state), which reading state directly would otherwise bypass by spelling it differently. Inside
    * a DIFFERENT entity it crosses §4.6's encapsulation rule: an entity's data "is 100%
    * encapsulated by the entity and acted upon only by the entity's handlers", so only a message
    * may cross that boundary.
    *
    * The second half is why this rule cannot live in the parser: it needs the resolved
    * [[AST.State]] and its owner, neither of which exists at parse time.
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
                  suggestion =
                    s"Send a message to the entity owning ${state.identify} and let its " +
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
    * same gap `checkStateReadScope`'s placement there is a known, filed defect for (see
    * BACKLOG.md).
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

  /** Task 5 of the message-value-source plan (Reid, 2026-08-14: *"no further task is needed, just
    * build it"*): `let x = initiate entity Order(…)` whose `x` is NEVER referenced afterwards.
    *
    * `initiate` is the ONLY way an `Id(P)` value comes into being, so binding one and dropping it
    * usually means the author meant to address the new instance and forgot. It is a plain
    * [[Warning]] — on by default, NOT behind `showCompletenessWarnings` — because unlike a missing
    * address this is decidable from the clause body alone, with nothing elsewhere in the model able
    * to change the answer.
    *
    * **It is a Warning and not an Error because a self-terminating worker legitimately has an
    * unused id**: nothing ever needs to address a fire-and-forget instance. `initiate` is a VALUE,
    * not a statement, so there is no argument-less spelling to steer such an author toward — the
    * `let` IS how you write it, and the warning is simply expected there.
    *
    * **Usage is decided from the RENDERED body, deliberately, and it is the conservative choice.**
    * The obvious alternative — enumerate the escape routes (`set` into state, an operand of
    * `tell`/`send`/`reply`/`yield`, a `terminate` argument, a constructor or call argument, a
    * `when` condition, a `foreach` collection …) — is a walk that must stay total over BOTH the
    * statement kinds and every value-bearing FIELD each one carries, and this file has already been
    * bitten twice by exactly that (`statementValues` silently dropped `RequireStatement.argument`
    * and `MatchCase.guard`, hiding an `initiate` from four checks at once). A missed route there is
    * a FALSE warning on correct code.
    *
    * `format` cannot miss one, because RIDDL is fully reflective by mandate: anything that parses
    * is emitted, a nesting statement's `format` renders its whole body, and a `format` that dropped
    * an operand would already be failing a prettify round-trip test. The cost is that it
    * OVER-counts: a name mentioned inside a `do "restart worker"` string reads as a use, so the
    * warning stays silent. That is the safe direction — "when in doubt, treat it as used".
    *
    * `scope` is the statement list the `let` was declared in, which is exactly its lexical extent:
    * a `let` in a `when` body is invisible outside it, and [[checkStatementScopes]] recurses with
    * that inner list, so each nesting level asks about its own scope.
    */
  private def checkUnusedInitiateId(ls: LetStatement, scope: Seq[Statement]): Unit =
    ls.expression match
      case _: Initiate =>
        val name = ls.identifier.value
        val used = scope.exists(s => !(s eq ls) && mentionsName(s.format, name))
        if !used then
          messages.addWarning(
            ls.loc,
            s"'$name' holds the identity of the instance this 'initiate' creates, but nothing " +
              "else in this clause refers to it",
            suggestion = s"Refer to '$name' — address the new instance ('tell … to …'), keep it " +
              s"('set field … to $name'), pass it in a message, or 'terminate' it — or leave it " +
              "as is if the new instance is self-terminating and never needs addressing."
          )
        end if
      // Every other bound expression: nothing to say. An unused `let` in general is a separate
      // question from this one and is deliberately NOT raised here.
      case _ => ()
  end checkUnusedInitiateId

  /** Whole-word containment: does `text` mention `name` other than as part of a longer identifier?
    *
    * Hand-rolled rather than a regex because `.r` is one of the constructs the Native rows avoid,
    * and this runs on all three platforms. A neighbouring `.` is deliberately NOT an identifier
    * character, so both `x.field` (a use) and `Foo.x` (a path that merely ends in the same name)
    * count — the second is an over-count, in the safe direction. See [[checkUnusedInitiateId]].
    */
  private def mentionsName(text: String, name: String): Boolean =
    def isIdentChar(c: Char): Boolean = c.isLetterOrDigit || c == '_'
    var found = false
    var i = text.indexOf(name)
    while i >= 0 && !found do
      val beforeOk = i == 0 || !isIdentChar(text.charAt(i - 1))
      val after = i + name.length
      val afterOk = after >= text.length || !isIdentChar(text.charAt(after))
      if beforeOk && afterOk then found = true
      else i = text.indexOf(name, i + 1)
    end while
    found
  end mentionsName

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
    parents.collectFirst {
      case p: Processor[?] => Some(p)
      case _: Function     => None
      case _: Saga         => None
    }.flatten

  /** The fully-qualified [[PathIdentifier]] naming `p`, in the natural root-to-leaf written order
    * (`Dom.Ctx.Order`). [[SymbolsOutput.pathOf]] returns the SAME chain leaf-to-root (it is a
    * symbol-table lookup key, not a path to render), so it is reversed here. No prior caller needed
    * to build a path FROM a definition -- every other [[PathIdentifier]] in the codebase is either
    * parsed from source or split from a dotted string -- so this is written fresh for
    * [[SelfValue.aggregation]]'s synthesized `Id(...)` field.
    */
  private def pathOf(p: Processor[?]): PathIdentifier =
    PathIdentifier(At.empty, symbols.pathOf(p).reverse)

  /** The PathIdentifier a `tell` writes for its target, when it writes one.
    *
    * `None` for a value target: `to self.id` and `to order.siteId` name an INSTANCE, and there is
    * no processor path in the source to report. Callers that need a path for the resolved processor
    * build one with [[pathOf]] instead of reasoning from its absence.
    */
  private def tellTargetPath(ts: TellStatement): Option[PathIdentifier] = ts.target match
    case pr: ProcessorRef[?] => Some(pr.pathId)
    case _: Value            => None

  /** How to NAME a tell's target in a diagnostic.
    *
    * A static target renders as its bare PATH, not `ProcessorRef.format` -- the latter prepends the
    * keyword (`entity E`), which silently changed every existing message from "target 'E'" to
    * "target 'entity E'". A value target has no path and renders as itself.
    */
  private def tellTargetLabel(ts: TellStatement): String =
    tellTargetPath(ts).map(_.format).getOrElse(ts.target.format)

  /** The processor a `tell` addresses, for either target shape. See [[TellTarget]] for why the
    * INSTANCE is deliberately not resolved — no check needs it, and the kind is what `Id(entity E)`
    * names.
    */
  private def tellTargetProcessor(ts: TellStatement, parents: Parents): Option[Processor[?]] =
    TellTarget.processorOf(ts.target, parents, resolution.refMap, symbols)

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
        // A54: a bare MessageRef is checked here; a Constructor AND a bare ValueRef are validated in
        // checkStatementScopes (both need the threaded `let`/element scope — A56/message-value-source).
        msg match
          case ref: MessageRef => checkRef[Type](ref, parents)
          case _: Constructor  => ()
          case _: ValueRef     => ()
        checkRef[Portlet](portlet, parents)
      case ForwardStatement(_, msg, target) =>
        // Same operand split as send/tell: a bare MessageRef is checked here, while a Constructor
        // and a bare ValueRef need the threaded `let`/element scope only `checkStatementScopes`
        // has. Everything specific to `forward` -- which clauses may hold it, whether the operand
        // matches the handled message, what may follow it -- lives there too, for the same reason.
        msg match
          case ref: MessageRef => checkRef[Type](ref, parents)
          case _: Constructor  => ()
          case _: ValueRef     => ()
        target match
          case portlet: PortletRef[?]     => checkRef[Portlet](portlet, parents)
          case processor: ProcessorRef[?] => checkRef[Processor[?]](processor, parents)
      case MorphStatement(_, entity, state, value) =>
        checkRef[Entity](entity, parents)
        checkRef[State](state, parents)
        // Same split as send/tell/yield/reply: a bare RecordRef is checked here; a Constructor and
        // a bare ValueRef are validated in checkStatementScopes, which threads the `let`/element
        // scope they need.
        value match
          case ref: RecordRef => checkRef[Type](ref, parents)
          case _: Constructor => ()
          case _: ValueRef    => ()
      case BecomeStatement(_, entityRef, handlerRef) =>
        checkRef[Entity](entityRef, parents).foreach { entity =>
          checkCrossContextReference(entityRef.pathId, entity, onClause, parents)
        }
        checkRef[Handler](handlerRef, parents).foreach { handler =>
          checkCrossContextReference(handlerRef.pathId, handler, onClause, parents)
        }
      case ts @ TellStatement(_, msg, target, _) =>
        // A ProcessorRef goes through `checkRef`, which REPORTS an unresolved or mis-kinded path.
        // A value target is resolved instead of checked here: whether it is a legal addressee --
        // that its type is an `Id(entity E)` at all -- is `checkTellTargetValue`'s question, and
        // asking it twice would double-report.
        val maybeProc = target match
          case pr: ProcessorRef[?] => checkRef[Processor[?]](pr, parents)
          case _: Value            => tellTargetProcessor(ts, parents)
        maybeProc.foreach { entity =>
          // The seam check needs a path. A value target writes none, so the resolved processor's
          // own path stands in -- the alternative, skipping, would silently exempt instance-
          // addressed tells from context isolation.
          val path = tellTargetPath(ts).getOrElse(pathOf(entity))
          checkCrossContextReference(path, entity, onClause, parents)
          collectedTells.addOne((ts, entity))
        }
        // A54: a bare MessageRef is checked here; a Constructor AND a bare ValueRef are validated in
        // checkStatementScopes (both need the threaded `let`/element scope — A56/message-value-source).
        msg match
          case ref: MessageRef =>
            val maybeType = checkRef[Type](ref, parents)
            maybeType.foreach { typ =>
              checkCrossContextReference(ref.pathId, typ, onClause, parents)
            }
          case _: Constructor => ()
          case _: ValueRef    => ()
      case WhenStatement(loc, condition, thenStatements, elseStatements) =>
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
        // A54: a bare ref is checked here; a Constructor AND a bare ValueRef are validated in
        // checkStatementScopes (both need the threaded `let`/element scope).
        msg match
          case ref: MessageRef => checkRef[Type](ref, parents)
          case _: Constructor  => ()
          case _: ValueRef     => ()
        checkResponsePairing(msg, Keyword.yield_, AggregateUseCase.EventCase)
      case ReplyStatement(_, msg) =>
        // Mirrors YieldStatement: a bare ref is checked here, a Constructor and a ValueRef in
        // checkStatementScopes. The pairing check is what keeps the two spellings honest.
        msg match
          case ref: MessageRef => checkRef[Type](ref, parents)
          case _: Constructor  => ()
          case _: ValueRef     => ()
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
          val settled = dischargesOnEveryPath(omc.contents) { (stmt, _) =>
            stmt match
              case _: ErrorStatement | _: RequireStatement => true
              // BOTH response statements settle a path. Counting only the pairing-correct one would
              // report the same mistake twice -- `checkResponsePairing` already names a `yield` in a
              // query clause, and a second "does not reply on every path" adds nothing.
              case _: YieldStatement | _: ReplyStatement => true
              // `forward` settles by DELEGATING: whatever handles the message downstream produces
              // the declared response.
              case _: ForwardStatement => true
              case _                   => false
          }
          auc.yields match {
            case Some(declaredYield) =>
              val declaredType = resolution.refMap.definitionOf[Type](declaredYield.pathId)
              if !settled then
                messages.addError(
                  omc.errorLoc,
                  s"${handledType.identify} declares '$declKeyword ${declaredYield.format}' " +
                    s"but ${omc.identify} does not $verb it on every path",
                  suggestion = s"Use '$stmtKeyword ${declaredYield.format}' (or refuse with " +
                    "'error'/'require') on every path through this handler. A 'when' with no " +
                    "'else', a 'match' with no 'default', and a 'foreach' all leave a path " +
                    "that does neither."
                )
              end if
              // Independent of `settled`: a clause may discharge by refusing on every path and
              // STILL yield the wrong thing somewhere, which is its own error.
              // Task 2: the operand may be a bare `ValueRef`, so it is resolved through
              // `widenedOperandType`/`widenedOperandMessageKind` -- the same A55 walk
              // `checkMessageOperandSource` uses -- and `scopedStatements` supplies each
              // statement's OWN lexical `let` scope rather than the clause's outermost one.
              //
              // This comparison is exactly what `yield`/`reply` were held back from A56 to
              // protect. It did not need protecting: it compares RESOLVED TYPES, and a ValueRef
              // supplies one just as a MessageRef does. Keeping it working across the widening is
              // the whole point, so both directions are pinned by
              // `YieldReplyMorphValueOperandTest`.
              val scopedResponses = scopedStatements(omc.contents, Seq.empty[LetStatement])
                .collect {
                  case (y: YieldStatement, curLets) if !isQuery => (y: Statement, y.msg, curLets)
                  case (r: ReplyStatement, curLets) if isQuery  => (r: Statement, r.msg, curLets)
                }
              scopedResponses.foreach { (ys, operand, curLets) =>
                // `omc +: parents`, NOT `parents`. A ValueRef resolves through
                // `refMap.anyDefinitionOf(path, parents.head)`, and ResolutionPass keyed these
                // against the ON-CLAUSE -- which `validateOnMessageClause` does not include in
                // the `parents` it passes here, so `parents.head` is the Handler and every
                // lookup missed. Verified by instrumenting both spellings: Handler gave None,
                // the on-clause gave Some(Event). `checkStatementScopes` never hit this because
                // it is invoked with the clause already on the stack.
                val operandParents = omc +: parents
                val kindOk = widenedOperandMessageKind(operand, operandParents, curLets)
                  .contains(declaredYield.messageKind)
                val yieldedType = widenedOperandType(operand, operandParents, curLets)
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
            // `yields`/`replies` are OPTIONAL, so producing without a declared clause PARSES and
            // is not an error. But a clause that answers should be handling a message that says
            // what it answers with, and until 2026-08-19 nothing said so.
            //
            // A19 makes the declaration the CONTRACT: a generator derives the handler's return
            // type from it and never from the body, because inferring from the body would let a
            // body silently redefine the interface. With no declaration the generated method is
            // `void` and the `reply` becomes a `return x;` inside it, which does not compile --
            // and the modeller learns that from javac several steps away rather than from riddlc.
            //
            // **StyleWarning, by the author's ruling**: *"a reply should be symmetric with the
            // replies clause, but not having that symmetry doesn't rise to the level of an
            // error"*. The model is untidy rather than self-contradictory -- it answers, it just
            // never declared that it does.
            //
            // The asymmetry that made this findable: `forward` ALREADY requires the declaration
            // and Errors without it (`checkForward`), so the strictest of the three response
            // statements checked a precondition the two ordinary ones did not.
            //
            // The CONVERSE is already an Error in all four combinations -- declaring and then not
            // producing ("does not yield/reply it on every path"), and producing the wrong type
            // ("does not match declared …") -- verified 2026-08-19 rather than assumed, so no
            // duplicate check was added for it.
            case None =>
              val answered: Seq[Statement] =
                if isQuery then finder.recursiveFindByType[ReplyStatement]
                else finder.recursiveFindByType[YieldStatement]
              answered.headOption.foreach { stmt =>
                messages.addStyle(
                  stmt.loc,
                  s"this '$verb' answers for ${handledType.identify}, which declares no " +
                    s"'$declKeyword' clause",
                  suggestion = s"Declare what it answers with — '$declKeyword <type>' on " +
                    s"${handledType.identify} — so the response is part of its contract rather " +
                    "than something a reader has to infer from this body."
                )
              }
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
      case Some(tr: TypeRef) =>
        // Present, as required. A20: the `with` value may be an ascribed hole, and the type it must
        // restate is the one `requires` NAMES. The TypeRef is used as written rather than resolved,
        // which is what the syntactic comparison wants -- no refMap lookup is needed or wanted here.
        argument.foreach {
          case pv: PromptValue =>
            checkPromptAscription(pv, Some(AliasedTypeExpression(At.empty, "type", tr.pathId)))
          case _ => ()
        }
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
      case be: BooleanExpression =>
        validateValue(be, parents, Seq.empty[LetStatement], Map.empty)
      case _: LiteralString => ()
      case blk: InvariantBlock =>
        val lets = blk.statements.toSeq.collect { case l: LetStatement => l }
        validateValue(blk.predicate, parents, lets, Map.empty)
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
    //
    // The SAME skip is why the duplicate-field check is wired here rather than left in
    // `checkAggregation`/`checkAggregateUseCase`. Those run only for a NESTED inline aggregate,
    // reached through `checkTypeExpression` -- and the guard above excludes every
    // `AggregateTypeExpression`, which is the shape a top-level `command`/`type` declaration has,
    // i.e. essentially every aggregate a model actually writes. Putting the check with its
    // siblings looked right and fired on nothing; this is the seam where a top-level aggregate is
    // reachable at all. The calls in TypeValidation stay, and cover the nested case.
    t.typEx match {
      case auc: AggregateUseCaseTypeExpression =>
        checkUseCaseYields(auc, parents)
        checkDuplicateFieldNames(auc.fields, t.identify)
      case agg: Aggregation => checkDuplicateFieldNames(agg.fields, t.identify)
      case _                => ()
    }
  }

  private def validateConstant(
    c: Constant,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, c)
    checkMetadata(c)
    c.value match
      case nl: NumericLiteral => checkNumericLiteralConformance(nl, c.typeEx)
      // A20: a typed hole's ascription restates the constant's declared type; it never overrides
      // it. `c.typeEx` is ALWAYS present for a Constant (unlike `let`, which may be unascribed),
      // so a Constant never draws the seam CompletenessWarning -- the constant itself supplies
      // the type, per the ruling's table.
      case pv: PromptValue => checkPromptAscription(pv, Some(c.typeEx))
      case _               => ()
  }

  /** A literal's value is statically known where a reference's is not, so a literal is held to a
    * STRICTER standard than the surrounding assignment rules. `NumericType.isAssignmentCompatible`
    * (`AST.scala:1912`) deliberately lets ANY numeric accept any other, and that stays true for
    * references — `let x: Natural = someRealField` is unchanged. Only literals are checked here.
    *
    * The fractional-value arm must precede the `Natural`/`Whole` range arms: both are
    * [[IntegerTypeExpression]]s, and reporting a range violation for `1.5` would be true and
    * useless — the fraction is the more specific defect. Range arms are guarded on `isInteger` so a
    * real-form literal is never range-checked as an integer. `Bool` is excluded explicitly even
    * though it extends [[IntegerTypeExpression]]: a Boolean-typed constant is not "a whole number
    * with a fractional part", it is a different kind entirely, and telling it so would be a
    * nonsense message.
    *
    * The range arms compare via `asBigDecimal`, never `asLong`: the parser accepts unbounded digit
    * runs (`99999999999999999999` is a legal `Natural` literal syntactically), and `asLong` is
    * `text.toLong` — it throws `NumberFormatException` on overflow, INSIDE a match guard, which
    * surfaces as `[severe] Exception Thrown` with no line number instead of a diagnostic.
    * `BigDecimal` has no such bound.
    */
  private def checkNumericLiteralConformance(
    nl: NumericLiteral,
    expected: TypeExpression
  ): Unit =
    expected match
      case _: Bool => () // Boolean, not integer-range — see the doc above.
      case _: IntegerTypeExpression if !nl.isInteger =>
        messages.addError(
          nl.loc,
          s"${expected.format} requires a whole number, but ${nl.text} has a fractional part",
          suggestion = s"Remove the fractional part, or declare the type as Real or Decimal."
        )
      case _: Natural if nl.isInteger && nl.asBigDecimal < BigDecimal(1) =>
        messages.addError(
          nl.loc,
          s"Natural is a positive whole number, but ${nl.text} is not greater than zero",
          suggestion = "Use Whole to admit zero, or Integer to admit negative values."
        )
      case _: Whole if nl.isInteger && nl.asBigDecimal < BigDecimal(0) =>
        messages.addError(
          nl.loc,
          s"Whole is a non-negative whole number, but ${nl.text} is negative",
          suggestion = "Use Integer to admit negative values."
        )
      case _ => ()
  end checkNumericLiteralConformance

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
    // Completeness 4h/4i: an entity's OWN portlets, not its context's.
    //
    // **Reid, 2026-08-18: "inlets are needed to receive, outlets to transmit/publish."** A message
    // reaches a processor through THAT processor's inlet -- not a sibling's, and not its
    // container's. `tell` is no exception: it is the same operation as `send` unless a generator
    // can lower it more efficiently while keeping RIDDL's semantics, so it too requires the target
    // to have an inlet. (An "inbox" is a lowering detail with no presence at the RIDDL design
    // level; do not reason about one here.)
    //
    // Both checks used to read the CONTEXT's ports, and 4h did not ask about the entity at all.
    // That could never be right: a projector's inlet does not make an entity reachable, and an
    // entity cannot publish on its context's outlet. Getting a message OUT of a context goes
    // entity outlet -> connector -> context inlet -> handler -> context outlet, and the first step
    // is the entity's own outlet, so no context-level port substitutes for it.
    //
    // Each is gated on the entity actually doing the thing: an entity that handles no message
    // needs no inlet, and one that emits nothing needs no outlet. A `???` body is exempt via
    // `entity.nonEmpty`, per the standing rule that a stub earns at most a Missing warning.
    if entity.nonEmpty then {
      // Fold STATE handlers in, exactly as `validateAsk` and the four checks above do. An entity's
      // clauses commonly live inside a `State` rather than directly on the entity, so
      // `entity.handlers` alone under-reports badly -- it saw 24 of the corpus's entities where the
      // folded form sees far more.
      val allHandlers = entity.handlers ++ entity.states.flatMap(_.handlers)
      val receivesMessages = allHandlers.exists(_.clauses.exists {
        case _: OnMessageClause => true
        case _: OnEventClause   => true
        case _                  => false
      })
      if receivesMessages && entity.inlets.isEmpty then
        messages.addCompleteness(
          entity.errorLoc,
          s"${entity.identify} handles messages but declares no inlet to receive them on",
          suggestion =
            s"Declare an inlet on ${entity.identify} typed with the messages it handles. A " +
              "processor receives only through its OWN inlet -- a port on its context or on a " +
              "sibling does not deliver to it."
        )
      end if

      var emits = false
      allHandlers.foreach { handler =>
        handler.clauses.foreach { clause =>
          walkStatements(clause.contents) {
            case _: SendStatement | _: TellStatement | _: YieldStatement | _: ReplyStatement =>
              emits = true
            case _ => ()
          }
        }
      }
      if emits && entity.outlets.isEmpty then
        messages.addCompleteness(
          entity.errorLoc,
          s"${entity.identify} sends or publishes messages but declares no outlet to transmit " +
            s"them on",
          suggestion =
            s"Declare an outlet on ${entity.identify} for the messages it emits. Publishing goes " +
              "out the entity's OWN outlet; its context's outlet is reached only by connecting " +
              "the entity's outlet onward within the context."
        )
      end if
    }
    // Completeness: entity Id type placement checks
    if entity.nonEmpty then {
      val parentContext = parents.collectFirst { case c: Context => c }

      // Search all known types via symbols table for Id types referencing this entity.
      //
      // By RESOLVED IDENTITY, never by the path's last segment. Reid overruled name matching twice
      // on the instance-identity branch -- for `isAddressFieldFor` and for the `on term` leading
      // parameter -- and this predated both, so it survived that sweep. It matters more since
      // `Id(P)` widened from Entity to any Processor: a `type X is Id(Other.Order)` in a model with
      // two same-named entities silenced this warning for BOTH, and symmetrically an
      // `Id(repository Foo)` whose last segment happened to match an entity name was counted as
      // that entity's identity type.
      //
      // `uniqueIdReferent` is the existing encapsulation of this lookup, including the detail that
      // the refMap key's parent is the OWNING Type -- reuse it rather than writing a third variant.
      def isIdForEntity(t: Type): Boolean = t.typEx match {
        case uid: UniqueId =>
          uniqueIdReferent(uid.entityPath, t, parents).exists(_ eq entity)
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
    checkSnapshotsOption(entity)
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
  /** `option snapshots` says journal-derived snapshots are taken, so rehydration replays only the
    * entries after the newest one. It means nothing without a journal, so it is an ERROR on an
    * entity that is not event-sourced.
    *
    * Error rather than the registry's parent-kind style nudge, for the reason a misplaced
    * `persistent` is an Error: the option asserts something the entity cannot provide, and there is
    * no reading under which it is a weaker-but-legitimate choice. The registry restricts it to
    * `Entity`; this narrows it to the entities where it can mean anything at all.
    *
    * **Absence is NOT "unspecified" — it is the default, and it means take no snapshots and replay
    * the whole log** (author's ruling, 2026-08-19). Many entities see fewer than a hundred events
    * in their lifespan, and an ephemeral one goes through a handful of transitions before it
    * terminates; snapshotting those spends storage and write volume to save a replay that was
    * never expensive. So there is deliberately no diagnostic for an event-sourced entity that
    * omits it.
    *
    * Reads the INTENTION, which is the only spelling that reaches here: `EntityParser` consumes any
    * deprecated `option event-sourced` into `EntityIntention.EventSourced` and drops it from the
    * metadata, so an entity carrying the old spelling arrives indistinguishable from one written
    * the current way.
    */
  private def checkSnapshotsOption(entity: Entity): Unit = {
    if !entity.isEventSourced then
      entity.options.find(_.name == "snapshots").foreach { opt =>
        messages.addError(
          opt.loc,
          s"${entity.identify} declares 'option snapshots' but is not event-sourced; there is no " +
            "event journal to snapshot",
          suggestion = "Write 'event-sourced entity' if its state really is rebuilt by replaying " +
            "events, or remove the option. Snapshots bound the cost of replaying a journal, so " +
            "they mean nothing where there is no journal."
        )
      }
    end if
  }

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
    * The CROSS-clause case is not here: two different folds writing one field is a race and already
    * an Error (see `raced` above), because arrival order across sources is not guaranteed. This is
    * the within-one-fold complement, where order IS guaranteed and the defect is therefore only
    * dead work.
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
          val overridden = dischargesOnEveryPathSeq(rest) { (stmt, _) =>
            stmt match
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
              suggestion =
                s"Add an 'on event ... is { set field ${unset.head.id.value} to ... }' " +
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
    val yieldedCommand: Option[Type] =
      resolution.refMap.definitionOf[Type](correlation.yields.pathId)
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
            case _: TellStatement   => Some("tell")
            case _: SendStatement   => Some("send")
            case _: YieldStatement  => Some("yield")
            case _: ReplyStatement  => Some("reply")
            case _: PutStatement    => Some("put")
            case _: MorphStatement  => Some("morph")
            case _: BecomeStatement => Some("become")
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
              tellTargetPath(ts)
                .flatMap(resolution.refMap.definitionOf[Repository](_))
                .filter(repo => repositoriesOf.exists(_ eq repo))
                .foreach { repo =>
                  // Resolve the operand's TYPE rather than matching on how it was written -- a
                  // `ValueRef` (e.g. an on-clause binding) carries the same statically-known type
                  // as a bare `MessageRef`; only the spelling differs. See `operandType`'s doc.
                  val sentType = operandType(ts.msg)
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
            tellTargetPath(tell).flatMap(_.value.lastOption).contains(repoName)
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
    checkQueriedWithoutIndex(repository)
    checkRepositoryScopePlacement(repository, parents)
  }

  /** A repository that ANSWERS QUERIES but declares no index at all.
    *
    * Reid, 2026-08-18, on riddlg's request. Reading a store by a value it does not index is a
    * sequential scan by construction, and the cost grows with the table rather than with the
    * answer: benchmarked on PostgreSQL 16 at 20k rows, an indexed containment query ran in
    * 0.053 ms against 2.728 ms unindexed -- 51x, widening. So a queried schema with no index is
    * worth saying out loud.
    *
    * **It deliberately does NOT name a field, and that is a limit of what the model says, not
    * timidity.** The obvious richer check -- "this query compares field F, so F should be
    * indexed" -- cannot be derived today, measured rather than assumed across riddl-models +
    * riddl-examples:
    *
    *   - Every one of the 406 repository `on query` bodies in the corpus is `prompt(...)` or
    *     `do "..."`. ZERO contain a comparison, so the predicate is prose, by design -- a
    *     repository on-clause is allowed to be a single `do` standing in for SQL.
    *   - Taking the QUERY TYPE's own fields as the comparison operands instead (they are, in
    *     principle) does not rescue it: of 284 query fields reachable from a repository's
    *     on-query, exactly 1 maps to a stored record field BY NAME and 19 BY TYPE (6%). The
    *     correspondence between a query's parameters and the storage it filters has simply never
    *     been required of authors, so it is not in the models.
    *
    * Making that derivable needs the correspondence STATED -- prose in the query type would move
    * the ambiguity, not remove it. Filed as a language question rather than guessed at here.
    *
    * A CompletenessWarning, not an Error: an unindexed store is under-specified, not
    * self-contradictory, and the author may know the table is small. Silent for a `???` stub, for
    * a repository with no schema (nothing to index), and for one that answers no queries -- a
    * write-only sink legitimately needs no index.
    */
  private def checkQueriedWithoutIndex(repository: Repository): Unit = {
    if repository.nonEmpty then {
      val schemas = repository.contents.filter[Schema]
      val answersQueries = repository.handlers
        .flatMap(_.clauses)
        .collect { case omc: OnMessageLikeClause if omc.msg.nonEmpty => omc }
        .exists(_.msg.messageKind == AggregateUseCase.QueryCase)
      if answersQueries && schemas.nonEmpty && schemas.forall(_.indices.isEmpty) then
        messages.addCompleteness(
          repository.errorLoc,
          s"${repository.identify} answers queries but its schema declares no index, so every " +
            "query reads the whole collection",
          suggestion =
            s"Add 'index on field <Record>.<field>' to the schema of ${repository.identify} for " +
              "the fields its queries filter on. A generator emits the access method from the " +
              "field's type and the target dialect; the model states only that the field is queried."
        )
      end if
    }
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
        resolvePath[Context](adaptor.referent.pathId, parents).map { (target: Context) =>
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
        // silently dropped. This is an ERROR (a translation gap is a modeling defect).
        //
        // ADAPTORS ONLY -- do NOT generalize this to other processor kinds. Reid ruled
        // 2026-08-14 that `on other` is necessary to the LANGUAGE, not required in every
        // handler: "If there is nothing to do for a message that is not otherwise handled,
        // then it can be omitted and that is fine. It's better than an `on other { do
        // "nothing" }` kind of nonsense construct, even if that would be good validation."
        // (An earlier version of this comment promised to generalize "later"; that is
        // declined. A5 in ../RIDDL-Tools-To-Do-List.md; see BACKLOG.md for the corpus
        // measurement -- 1,295 of 3,606 handlers legitimately omit the clause.)
        //
        // The adaptor rule is not an exception to that ruling, it is an APPLICATION of it:
        // an adaptor is a translator, and it "must translate everything, including messages
        // it is not designed to translate! Even if that translation is 'Sorry, I can't
        // translate that'. Doing nothing on an unknown message is to silently omit from an
        // inter-context conversation" (Reid, 2026-08-14). So for an adaptor there is never
        // "nothing to do" on the fall-through path, which is exactly why the clause is
        // required here and nowhere else.
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
          //
          // A56/Fix-A sweep (2026-08-15): audited whether a send/tell `ValueRef` operand (an
          // on-clause binding) needs the same widening the populates-repository check received.
          // It does NOT, and this is verified rather than assumed: `operandType`'s ValueRef arm
          // only ever resolves the BARE on-clause binding name (`ResolutionPass.resolveValueRef`
          // registers a Type in the refMap only for `names.sizeIs == 1`), and a bare binding always
          // types identically to the enclosing on-clause's own `msg` -- which `onClauseRefs` below
          // already reports. A compound path (`p.field`) resolves to a `Field`, not a `Type`, so
          // `operandType` answers `None` for it -- confirmed empirically: a fixture with
          // `PaymentCompleted is { detail: ShippingContext.QueueShipment }` and
          // `tell p.detail to context ShippingContext` produces ZERO seam errors whether or not this
          // arm is added. Adding it would therefore only DOUBLE-REPORT the violation `onClauseRefs`
          // already catches, never catch a new one -- so it is deliberately left as `MessageRef`/
          // `Constructor` only. Revisit if `valueRefType`/`operandType` ever grow field-path
          // resolution to a `Type`.
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
        // ONE reading, not two (Reid, 2026-08-16): an `error-sink` inlet is infrastructure, never
        // dataflow, so `arityShape` already excludes it. The dual acceptance that used to live
        // here is gone -- accepting either reading let an infrastructure inlet justify whatever
        // shape the author had written, which is the distortion the ruling removes rather than a
        // flexibility worth keeping.
        val derived = processor.arityShape
        // THE ONE ALLOWANCE (Reid's option B). A processor whose only inlets are error sinks has
        // no dataflow ports at all, so it derives as `void` -- but it genuinely IS a sink, of
        // errors, and `void` describes it less well than `sink` does. Accept both for exactly that
        // shape. Deliberately narrow: it applies only when there is no dataflow whatsoever, so it
        // cannot excuse a flow ascribed as a merge, which is the case the ruling exists to catch.
        val isPureErrorReceiver =
          processor.dataflowInlets.isEmpty && numOutlets == 0 && numInlets >= 1
        val acceptable: Seq[String] =
          if isPureErrorReceiver then Seq(derived.keyword, Sink(At.empty).keyword)
          else Seq(derived.keyword)
        if !acceptable.contains(ascribed.keyword) then
          messages.addError(
            processor.errorLoc,
            s"${processor.identify} is ascribed 'as ${ascribed.keyword}' but its DATAFLOW arity " +
              s"($numOutlets outlets, ${processor.dataflowInlets.size} inlets, excluding " +
              s"${numInlets - processor.dataflowInlets.size} error-sink) is ${derived.keyword}",
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
            case _ => false)
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
      // A70/instance-identity: the entity a `terminate` ends is exactly the kind of reference A8
      // exists to catch -- a saga step ending an instance owned by a DIFFERENT domain crosses the
      // same boundary a cross-domain `tell` does. Since 2026-08-15 the entity is named by the
      // `Id(entity E)` TYPE of the target value rather than by a ref, so the crossing is found
      // through the target's references (`valueReferencedDefs`) plus the entity the `Id` names.
      case term: TerminateStatement =>
        valueReferencedDefs(term.target) ++
          term.args.flatMap(a => valueReferencedDefs(a.value))
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
    checkBlockTerminal(s.doStatements.toSeq.collect { case st: Statement => st })
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
        case t: TellStatement   => doTargets += tellTargetLabel(t)
        case snd: SendStatement => doTargets += snd.portlet.pathId.format
        case _                  => ()
      }
      if doTargets.nonEmpty then {
        val undoTargets = mutable.Set.empty[String]
        walkStatements(s.undoStatements) {
          case t: TellStatement   => undoTargets += tellTargetLabel(t)
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
      // scopedStatements (not walkStatements), so a widened ValueRef operand -- a state
      // field/let-local/function-result/ask-result-sourced command, not only a keyword-led one --
      // resolves with its own lexical `let` scope (task-1 review, round 1). Before this fix, such
      // a `tell` was invisible to this check, so a saga step whose only effect was a widened-source
      // command tell was wrongly reported as having none.
      val hasTellCommand = scopedStatements(s.doStatements, Seq.empty[LetStatement]).exists {
        case (t: TellStatement, curLets) =>
          widenedOperandMessageKind(t.msg, parents, curLets).contains(AggregateUseCase.CommandCase)
        case _ => false
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
      // Completeness 4i was a CONTEXT-level inlet check ("has entities but no Sink streamlet to
      // receive and dispatch") and is GONE, superseded by the per-entity rule in `validateEntity`.
      // It asked whether anything in the context had an inlet, which answers a different question
      // than the one it reported: a sibling's inlet never made an entity reachable. Reid,
      // 2026-08-18 -- a processor receives only through its own inlet.
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
    // Same narrowing as the loop below, so the gate and the body agree about what a streamlet is.
    if c.streamlets.exists(_.isInstanceOf[Streamlet]) && nonEmptyEntities.nonEmpty then {
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
      // GUARD ON THE CASE CLASS, not on the shape. [4.1] widened `c.streamlets` to every
      // port-bearing processor, and a Repository or Projector with one inlet and no outlets
      // therefore DERIVES the shape `Sink` from its arity — so this asked every such definition to
      // dispatch to an entity. 25 false positives in riddl-examples, 3 in riddl-models, reported
      // 2026-08-18 as an rc.16 regression. It is the same false positive the comment above already
      // records for split/merge/flow, one level further out: a repository is the boundary into
      // STORAGE, not into entities, and the event it receives was emitted BY the entity, so
      // telling that entity back would invert the flow.
      //
      // `collect { case s: Streamlet => s }` is the idiom `WithStreamlets.streamlets` itself
      // recommends for exactly this. Guarding on the case class rather than excluding Repository
      // and Projector by name means a future port-bearing processor kind cannot silently inherit
      // the bug — which is how this one arrived.
      c.streamlets.collect { case s: Streamlet => s }.foreach { streamlet =>
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
          case WhenStatement(_, _, thenStatements, elseStatements) =>
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

  /** [[walkStatements]], but pairing each [[Statement]] with the LEXICAL `let` scope in force AT
    * its position — the same accumulation [[checkStatementScopes]] performs while it validates,
    * exposed here as data for a caller that (unlike `checkStatementScopes`) is not itself doing the
    * walking and needs the scope AFTER the fact, to resolve a widened `ValueRef` message operand
    * ([[widenedOperandType]] / [[widenedOperandMessageKind]]). Added for the message-value-source
    * widening (task-1 review, round 1): `validateOnMessageClause`'s query→result completeness check
    * and `checkTellAddressing`'s addressing checks both inspect statements found by a flat sweep,
    * where the sweep itself has no notion of "what `let`s are visible here".
    *
    * Descends into a `foreach` body (unlike [[dischargesOnEveryPathSeq]], which deliberately does
    * not, since a foreach body may never execute and this function is answering "does this appear
    * ANYWHERE", not "on every path") — matching what `Finder.recursiveFindByType` already reaches,
    * so replacing a Finder-based sweep with this one changes resolution, not which statements are
    * found.
    *
    * `elements` (`foreach` bindings) is NOT threaded into that descent, for the same reason
    * `dischargesOnEveryPathSeq` does not thread them: no current caller resolves an operand sourced
    * from a loop variable, so a statement inside a `foreach` body sees its ENCLOSING elements only.
    * Named explicitly rather than left silently narrow.
    */
  private def scopedStatements[CV <: RiddlValue](
    contents: Contents[CV],
    lets: Seq[LetStatement]
  ): Seq[(Statement, Seq[LetStatement])] =
    var curLets = lets
    val out = mutable.ListBuffer.empty[(Statement, Seq[LetStatement])]
    contents.toSeq.foreach {
      case ls: LetStatement =>
        out += ((ls, curLets))
        curLets = curLets :+ ls
      case ws: WhenStatement =>
        out += ((ws, curLets))
        out ++= scopedStatements(ws.thenStatements, curLets)
        out ++= scopedStatements(ws.elseStatements, curLets)
      case ms: MatchStatement =>
        out += ((ms, curLets))
        ms.cases.foreach(mc => out ++= scopedStatements(mc.statements, curLets))
        out ++= scopedStatements(ms.default, curLets)
      case fs: ForeachStatement =>
        out += ((fs, curLets))
        out ++= scopedStatements(fs.doStatements, curLets)
      case s: Statement => out += ((s, curLets))
      case _            => () // skip Comments
    }
    out.toSeq
  end scopedStatements

  /** Does EVERY execution path through `contents` settle the clause's obligation?
    *
    * The obligation differs per caller (`settles` says what discharges it), but the shape of the
    * question does not: a clause handling a command must, on every path, either produce what the
    * command declares or refuse it.
    *
    * This replaced a much weaker predicate that asked only "does a refusal appear ANYWHERE in this
    * clause?", via `Finder.recursiveFindByType`. Because that searches the whole nested tree, ONE
    * refusal in ONE branch exempted the entire clause, so this validated clean despite producing
    * nothing on the `amt > 0` path:
    * {{{
    * on command Pay is {            // Pay declares `yields event Paid`
    *   when "amt <= 0" then { error "refused" } end
    * }
    * }}}
    *
    * `exists` is the right combinator over a sequence: execution passes through every statement in
    * it, so one statement that settles the obligation settles the whole block. The nested cases are
    * where "every path" actually bites:
    *
    *   - a `when` needs BOTH branches, and an absent `else` is an escape path, not a discharge;
    *   - a `match` needs every case AND a `default`, since without one an unmatched value escapes
    *     (RIDDL cannot know a pattern set is exhaustive);
    *   - a `foreach` NEVER discharges -- its body may iterate zero times.
    *
    * Making `else`/`default` mandatory in the grammar was considered and rejected (Reid,
    * 2026-08-07): it would break ~56 sites across three repos and would NOT close this hole anyway,
    * since an empty or non-discharging `else` still escapes. The analysis is what closes it.
    */
  private def dischargesOnEveryPath[CV <: RiddlValue](
    contents: Contents[CV]
  )(settles: (Statement, Seq[LetStatement]) => Boolean): Boolean =
    dischargesOnEveryPathSeq(contents.toSeq, Seq.empty[LetStatement])(settles)

  /** The [[dischargesOnEveryPath]] analysis over a plain `Seq`, so a caller can ask the question of
    * a statement list it BUILT rather than one that exists in the AST.
    *
    * A70's overridden-`set` check needs exactly that: "is this `set` overridden later?" means
    * testing the suffix after it CONCATENATED with the enclosing block's continuation, and no such
    * `Contents` exists anywhere in the tree.
    */
  private def dischargesOnEveryPathSeq(
    statements: Seq[RiddlValue]
  )(settles: (Statement, Seq[LetStatement]) => Boolean): Boolean =
    dischargesOnEveryPathSeq(statements, Seq.empty[LetStatement])(settles)

  /** `lets`-threading variant, added for the message-value-source widening (Task 1 review round 1):
    * `settles` may need to resolve a widened `ValueRef` message operand (the same
    * `checkMessageOperandSource`/`valueRefType` resolution), which needs the LEXICAL `let` scope in
    * force at each statement's position, not merely the statement itself. Mirrors
    * [[checkStatementScopes]]'s accumulation (`var lets`, appended on every [[LetStatement]]
    * encountered, threaded into `when`/`match` recursion) rather than duplicating it via a shared
    * cache, since the two callers that don't need `lets` (the yield/reply-settled check and the A70
    * overridden-`set` check) are unaffected by simply ignoring the second `settles` parameter.
    *
    * `elements` (`foreach` loop bindings) is deliberately NOT threaded — `_: ForeachStatement =>
    * false` immediately below never calls `settles` from inside a foreach body, so there is no
    * position at which an element binding could matter to this analysis.
    */
  private def dischargesOnEveryPathSeq(
    statements: Seq[RiddlValue],
    lets: Seq[LetStatement]
  )(settles: (Statement, Seq[LetStatement]) => Boolean): Boolean =
    var curLets = lets
    statements.exists {
      case ls: LetStatement =>
        curLets = curLets :+ ls
        false // a `let` itself never settles the obligation
      case WhenStatement(_, _, thenStatements, elseStatements) =>
        dischargesOnEveryPathSeq(thenStatements.toSeq, curLets)(settles) &&
        elseStatements.nonEmpty &&
        dischargesOnEveryPathSeq(elseStatements.toSeq, curLets)(settles)
      case MatchStatement(_, _, cases, default) =>
        cases.forall(mc => dischargesOnEveryPathSeq(mc.statements.toSeq, curLets)(settles)) &&
        default.nonEmpty &&
        dischargesOnEveryPathSeq(default.toSeq, curLets)(settles)
      case _: ForeachStatement => false // the body may iterate ZERO times
      case s: Statement        => settles(s, curLets)
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
    case call: Call => 1 + call.args.map(a => countValueFailPoints(a.value)).sum
    // A12: the census extends to failure-bearing VALUES, not only statements (Reid, 2026-08-09).
    // An `ask` can fail exactly as a `call` can -- more obviously, since no answer may ever
    // arrive -- and `Call` was already counted here, so omitting `ask` would have let a saga
    // step hide a second failure point behind a `let`.
    case _: Ask => 1
    // A70/instance-identity: `initiate` invokes `on init` and mints an instance -- it can fail
    // exactly as `call`/`ask` can, so it counts itself (1) PLUS its argument values, exactly like
    // `call` immediately above.
    case init: Initiate => 1 + init.args.map(a => countValueFailPoints(a.value)).sum
    case _: GetValue    => 1
    case lv: LookupValue =>
      countValueFailPoints(lv.collection) + lv.indices.map(countValueFailPoints).sum
    case c: Constructor           => c.args.map(a => countValueFailPoints(a.value)).sum
    case le: LogicalExpression    => countValueFailPoints(le.left) + countValueFailPoints(le.right)
    case ne: NotExpression        => countValueFailPoints(ne.expr)
    case ce: ComparisonExpression => countValueFailPoints(ce.left) + countValueFailPoints(ce.right)
    // A17's ASK form contributes NOTHING of its own -- consulting an invariant is a test, not an
    // action that can fail -- but its `with` operand is a full Value and is counted, exactly as a
    // comparison contributes nothing while its operands count.
    case ic: InvariantCondition => ic.argument.map(countValueFailPoints).getOrElse(0)
    // A bare message REFERENCE carries no failure point of its own -- the statement holding it
    // does, and that statement is counted by its own arm. Enumerated rather than absorbed by a
    // `case _ => 0`, because that catch-all is precisely how `ask` went uncounted: a new
    // failure-bearing value read as "contributes nothing" instead of failing the build.
    case _: EmptyValue   => 0 // An `empty` is a LITERAL: its only payload is a TypeExpression, which holds no values.
    case _: Reference[?] => 0
    // A name cannot fail; see the note in `stateReadsIn`.
    case _: Identifier => 0
    // `self`/`self.<field>` is a keyword-anchored value, not an effect -- reading the running
    // instance's own identity cannot fail the way a call, ask, or get can.
    case _: SelfValue => 0
    case _: LiteralString | _: PromptValue | _: ValueRef | _: BooleanLiteral | _: NumericLiteral =>
      0
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
      case set: SetStatement    => Seq(set.value)
      case let: LetStatement    => Seq(let.expression)
      case put: PutStatement    => Seq(put.value)
      case ret: ReturnStatement => Seq(ret.value)
      case snd: SendStatement   => Seq(snd.msg)
      case tel: TellStatement   => Seq(tel.msg)
      case yld: YieldStatement  => Seq(yld.msg)
      case rpl: ReplyStatement  => Seq(rpl.msg)
      case mor: MorphStatement  => Seq(mor.value)
      // Review round 1: `req.argument` (the `with <expr>` operand) is a full Value -- `require`
      // is legal in both a function body and an activation clause (`guardStatements` in
      // `StatementParser` suppresses it only under `EventClause`), and `initiateValue` is a
      // production of the same `value` rule the operand is parsed with -- so `require X with
      // initiate entity Order` could hide an `initiate` from every walk that consumes
      // `statementValues` (state-reads, asks, this task's instance-effect ban, the A12
      // fail-point census) unless the operand is included here too.
      case req: RequireStatement => Seq(req.condition) ++ req.argument.toSeq
      case whn: WhenStatement    => Seq(whn.condition)
      // Review round 1: a `MatchCase.guard` is the SAME shape as `req.argument` -- a full
      // `BooleanExpression | ValueRef` value that was never fed to any of these walks, even
      // though `validateMatch` already resolves/type-checks it independently via `validateValue`.
      // `mat.expression` (the subject) is unaffected; this only adds each case's guard.
      case mat: MatchStatement => Seq(mat.expression) ++ mat.cases.flatMap(_.guard.toSeq)
      // A70/instance-identity: `terminate`'s TARGET and arguments are full Values, exactly like a
      // constructor's or `initiate`'s, so a `get from state`/`ask`/nested call-fail-point can
      // hide inside one and must be counted rather than silently skipped. The target joined this
      // list on 2026-08-15 when it replaced the old `ProcessorRef`; omitting it would blind every
      // walk built on `statementValues` at once, which is the documented shape of this defect.
      case term: TerminateStatement => term.target +: term.args.map(_.value)
      case _                        => Seq.empty
  end statementValues

  /** Every [[Ask]] embedded in a value expression, at any depth.
    *
    * Enumerated over the same arms as `countValueFailPoints` rather than absorbed by a catch-all: a
    * new value kind that can CONTAIN an ask must fail the build here, not quietly hide one inside a
    * saga.
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
    case lv: LookupValue => stateReadsIn(lv.collection) ++ lv.indices.flatMap(stateReadsIn)
    case call: Call      => call.args.toSeq.flatMap(a => stateReadsIn(a.value))
    case c: Constructor  => c.args.toSeq.flatMap(a => stateReadsIn(a.value))
    // `initiate`'s arguments are full Values, exactly like a constructor's or a call's, so a
    // `get from state` can hide inside one and this must recurse rather than stop.
    case init: Initiate           => init.args.toSeq.flatMap(a => stateReadsIn(a.value))
    case le: LogicalExpression    => stateReadsIn(le.left) ++ stateReadsIn(le.right)
    case ne: NotExpression        => stateReadsIn(ne.expr)
    case ce: ComparisonExpression => stateReadsIn(ce.left) ++ stateReadsIn(ce.right)
    // A17's ASK form: `when invariant Limit with <expr>`. The `with` operand is a full Value, so it
    // CAN hold a state read and this must recurse rather than stop. `ref` needs no arm -- an
    // InvariantRef is a Reference and the arm below covers it.
    case ic: InvariantCondition => ic.argument.toSeq.flatMap(stateReadsIn)
    // An `ask` holds only a QueryRef and a ProcessorRef -- no nested value -- so it cannot contain
    // a state read. (A saga's `ask` is separately banned outright; see `asksIn`.)
    case _: Ask          => Seq.empty
    case _: EmptyValue   => Seq.empty // An `empty` is a LITERAL: its only payload is a TypeExpression, which holds no values.
    case _: Reference[?] => Seq.empty
    // An IDENTIFIER is a NAME, not an expression: `when isValid` can bind a bare `Identifier`
    // naming a let-local or a field. A name has no sub-structure, so it can contain nothing --
    // decided deliberately, as the throw below instructs, not defaulted. (This arm predates, and is
    // unrelated to, the retired `WhenStatement.negated` flag -- `when !isValid` has parsed to a
    // `NotExpression` since the 2026-08-14 not/! synonymy ruling, not to a bare `Identifier`; the
    // parser's bare-name path already routes through `ValueRef`, per A17, and this `Identifier` arm
    // is kept only for AST/API back-compat, e.g. a directly-constructed or older-BAST condition.)
    //
    // It is here because `statementValues` yields a domain WIDER than `Value`:
    // `WhenStatement.condition` is `LiteralString | Identifier | ValueRef | BooleanExpression |
    // PromptValue`, and `Identifier` is in none of the other members. Auditing `Value` alone (as
    // the InvariantCondition fix did on 2026-08-12) misses exactly this, which is how
    // `when !isValid` -- documented syntax that validated on rc.11 -- came to throw on rc.13.
    case _: Identifier => Seq.empty
    // `self`/`self.<field>` holds no nested value -- an optional bare field Identifier, not a
    // sub-expression -- so it cannot contain a state read.
    case _: SelfValue => Seq.empty
    case _: LiteralString | _: PromptValue | _: ValueRef | _: BooleanLiteral | _: NumericLiteral =>
      Seq.empty
    case other =>
      throw new IllegalStateException(
        s"stateReadsIn has no arm for ${other.getClass.getSimpleName} at ${other.loc}; " +
          "decide whether it can contain a 'get from state' rather than assuming it cannot"
      )
  end stateReadsIn

  /** Every [[Initiate]] embedded in a value expression, at any depth -- the top-level `Initiate`
    * itself PLUS any nested inside its own arguments (`initiate entity Order(x = initiate entity
    * Foo)`), exactly as `stateReadsIn` recurses into `Initiate.args` looking for a
    * `get from state`.
    *
    * Enumerated over the same arms as `stateReadsIn`/`asksIn` and for the same reason: a new value
    * kind that can CONTAIN an `initiate` must fail the build here rather than quietly hide one.
    * This is what lets `checkInstanceEffectScope` and `validateCorrelation`'s fold-purity check see
    * `initiate` wherever it hides -- most importantly inside a `let x = initiate ...`, which is a
    * [[LetStatement]], not a `TerminateStatement`-shaped statement a simple `case` match would
    * catch.
    */
  private def initiatesIn(v: RiddlValue): Seq[Initiate] = v match
    case init: Initiate           => Seq(init) ++ init.args.toSeq.flatMap(a => initiatesIn(a.value))
    case lv: LookupValue          => initiatesIn(lv.collection) ++ lv.indices.flatMap(initiatesIn)
    case call: Call               => call.args.toSeq.flatMap(a => initiatesIn(a.value))
    case c: Constructor           => c.args.toSeq.flatMap(a => initiatesIn(a.value))
    case le: LogicalExpression    => initiatesIn(le.left) ++ initiatesIn(le.right)
    case ne: NotExpression        => initiatesIn(ne.expr)
    case ce: ComparisonExpression => initiatesIn(ce.left) ++ initiatesIn(ce.right)
    case ic: InvariantCondition   => ic.argument.toSeq.flatMap(initiatesIn)
    // A `get from state`/`get from input` holds only a StateRef/InputRef -- no nested value -- so
    // it cannot contain an `initiate`.
    case _: GetValue => Seq.empty
    // An `ask` holds only a QueryRef and a ProcessorRef -- no nested value.
    case _: Ask          => Seq.empty
    case _: EmptyValue   => Seq.empty // An `empty` is a LITERAL: its only payload is a TypeExpression, which holds no values.
    case _: Reference[?] => Seq.empty
    // A name contains nothing; see the note in `stateReadsIn`.
    case _: Identifier => Seq.empty
    // `self`/`self.<field>` holds no nested value; see the same note in `stateReadsIn`.
    case _: SelfValue => Seq.empty
    case _: LiteralString | _: PromptValue | _: ValueRef | _: BooleanLiteral | _: NumericLiteral =>
      Seq.empty
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
    case ask: Ask        => Seq(ask)
    case lv: LookupValue => asksIn(lv.collection) ++ lv.indices.flatMap(asksIn)
    case call: Call      => call.args.toSeq.flatMap(a => asksIn(a.value))
    case c: Constructor  => c.args.toSeq.flatMap(a => asksIn(a.value))
    // `initiate`'s arguments are full Values, exactly like a constructor's or a call's, so an
    // `ask` can hide inside one -- and a saga step is exactly where that must not go unnoticed.
    case init: Initiate           => init.args.toSeq.flatMap(a => asksIn(a.value))
    case le: LogicalExpression    => asksIn(le.left) ++ asksIn(le.right)
    case ne: NotExpression        => asksIn(ne.expr)
    case ce: ComparisonExpression => asksIn(ce.left) ++ asksIn(ce.right)
    // A17's ASK form. Same reasoning as `stateReadsIn`: the `with` operand is a full Value, so an
    // `ask` can hide inside one -- and a saga step is exactly where that must not go unnoticed.
    case ic: InvariantCondition => ic.argument.toSeq.flatMap(asksIn)
    case _: GetValue            => Seq.empty
    case _: EmptyValue   => Seq.empty // An `empty` is a LITERAL: its only payload is a TypeExpression, which holds no values.
    case _: Reference[?]        => Seq.empty
    // A name contains nothing; see the note in `stateReadsIn`.
    case _: Identifier => Seq.empty
    // `self`/`self.<field>` holds no nested value; see the same note in `stateReadsIn`.
    case _: SelfValue => Seq.empty
    case _: LiteralString | _: PromptValue | _: ValueRef | _: BooleanLiteral | _: NumericLiteral =>
      Seq.empty
    case other =>
      throw new IllegalStateException(
        s"asksIn has no arm for ${other.getClass.getSimpleName} at ${other.loc}; " +
          "decide whether it can contain an 'ask' rather than assuming it cannot"
      )
  end asksIn

  /** A23: is `s` an effect for the "refusals first" rule — one that transforms THIS instance's
    * state before a refusal could abandon it?
    *
    * **NARROWED 2026-08-19 (author's ruling) to LOCAL state transformation only.** The set was
    * `set`/`morph`/`become`/`send`/`tell`/`yield`/`put`/`terminate`, borrowed wholesale from A26's
    * pure-function bans — but A26 asks "is this pure?" and A23 asks "would refusing now leave a
    * partial change?", and those are different questions. A transmission leaves nothing partial
    * HERE. It may cause a state change somewhere else, later, when something receives it, and the
    * author's ruling is that this remote "maybe" is acceptable: it is *"locally immutable"*, which
    * is what A23 is actually about.
    *
    * Out, and why:
    *   - `send`/`tell` — transmissions; any state they cause is elsewhere and later.
    *   - `yield` — also a transmission. In an event-sourced entity the state change is applied by
    *     the entity's OWN `on event` clause, and R3/R4 already FORCE the `set` to live there
    *     rather than beside the `yield`, so the yield itself transforms nothing locally.
    *   - `become` — a BEHAVIOR transition, not a state transition (author's ruling, explicit).
    *   - `put` — delivers to a UI Output; a transmission, not this instance's state.
    *
    * In, and why: `set` and `morph` transform the state record; `terminate` ends the instance,
    * which is the most complete state change available. Refusing after any of those really does
    * leave the partial change A23 exists to prevent.
    *
    * **This narrowing is what makes the `error`-is-terminal rule migratable.** The corpus idiom is
    * "refuse AND publish a rejection event", 268 sites in reactive-bbq; with `error` terminal the
    * `send` must move BEFORE it, and under the old set that was itself an A23 error — so neither
    * order was legal and the idiom became inexpressible. Now it is a reordering.
    *
    * Still EXCLUDES the refusals themselves (`require`/`error`) and the opaque `CodeStatement`.
    */
  private def isEffectStatement(s: Statement): Boolean = s match
    case _: SetStatement | _: MorphStatement | _: TerminateStatement => true
    case _                                                           => false

  /** What ended a block, and WHY.
    *
    * `error` and `terminate` both make everything after them unreachable, but for DIFFERENT
    * reasons: an `error` REFUSES, a `terminate` DESTROYS the instance. The reason travels with the
    * location so the message can state the one that actually applies.
    *
    * Reusing `error`'s wording for `terminate` would have been the smaller change and would have
    * produced a true diagnostic with a false explanation — telling an author their `terminate`
    * "refuses", and pointing them at `require` as the conditional alternative, which is not a
    * conditional `terminate` at all. That is the same trap A23 fell into by borrowing A26's effect
    * set: a check inherited wholesale stops answering its own question.
    */
  private case class BlockEnder(loc: At, keyword: String, why: String, advice: String)

  private object BlockEnder {
    def refusal(loc: At): BlockEnder = BlockEnder(
      loc,
      "error",
      "refuses",
      "Move this statement BEFORE the 'error' — a transmission there is legal, since A23 bans " +
        "only local state changes ahead of a refusal — or remove it. An 'error' is unconditional; " +
        "use 'require' if the refusal is conditional and later statements should still run."
    )

    def termination(loc: At): BlockEnder = BlockEnder(
      loc,
      "terminate",
      "destroys the instance",
      "Move this statement BEFORE the 'terminate', where the instance still exists, or remove it. " +
        "Nothing can read or change an instance that no longer exists. If the work belongs to the " +
        "end of the instance's life, put it in an 'on term' clause — that clause runs BECAUSE of " +
        "the termination, so it is not 'after' it in this block."
    )
  }

  /** `error` and `terminate` are TERMINAL in their block: a statement after either is unreachable.
    *
    * Two author's rulings, a day apart, and the second is why this check is no longer named for
    * `error` alone. **`error` refuses** unconditionally (2026-08-19), so nothing after it can run;
    * riddl-generator lowers it to a terminal `return`, which made every following statement
    * unreachable Java at 268 sites in reactive-bbq. **`terminate` destroys the instance**
    * (2026-08-20), so nothing after it can read or change one — *"having a set state, or any
    * statement after a terminate is something riddlc should error about (because the statements
    * must be ignored)"*. The alternative reading for `error`, that following statements "record the
    * refusal and carry on", was rejected outright: it *"suggests 'throw out control flow, it's not
    * important!' which is ridiculous"*.
    *
    * **The asymmetry lasted one release and was found BY EYE, not by a check.** rc.19 shipped the
    * `error` half and reordered 268 corpus statements for exactly this reason, while a `set state`
    * sitting after a `terminate` in reactive-bbq's `TableOrder` survived that pass and every
    * validation since — because the rule matched `ErrorStatement` alone. riddl-models noticed it
    * while adding `on term`. **When a rule is about unreachability, ask what ELSE ends a block**;
    * enumerating one terminator is how the next one stays invisible.
    *
    * **`require` is deliberately NOT terminal.** `require X` refuses only when X fails and
    * continues when it holds, so statements after it are ordinary. This is why the check matches
    * `ErrorStatement` rather than the refusal pair A23 uses — and note A23 asks a DIFFERENT
    * question from this one (*would refusing now leave a partial change?*), so its effect set is
    * not this one's terminator set.
    *
    * **`on term` needs no exemption**, and this is the question riddl-models raised. The check runs
    * per statement LIST, and an `on term` clause is a different list — it runs BECAUSE of the
    * termination, so it is never "after" the `terminate` in the same block. All three corpus
    * `terminate` sites are followed by a sibling `on term` clause and none is affected.
    *
    * Recursion follows `checkRefusalsFirst`: each `when` branch, `match` case and `foreach` body is
    * its own list, because a terminator inside a branch says nothing about statements following the
    * branch, which may not be taken.
    */
  /** No `set` of any kind may follow a `morph` in the same clause (Reid, 2026-08-24).
    *
    * *"Morph statements should be followed by set statements because the state has changed and the
    * subsequent state changes either should have been part of the morph statement's record
    * constructor or placed in the new state's handler's on clauses."*
    *
    * **This rule is what makes the current-state check LEXICAL.** riddl-generator warned that a
    * lexical "the state named must be the enclosing state" rule would produce 36 false positives in
    * reactive-bbq, because the corpus writes `morph` and `set state` as a pair and the `morph` has
    * already transitioned. Under this rule that pair is illegal outright, so no `set` can ever run
    * after a transition — the current state is ALWAYS the enclosing state, and the flow-sensitive
    * tracking (with branch joins) they asked for is unnecessary. The 36 sites become errors either
    * way; they are simply reported for the clearer reason.
    *
    * **A `morph` inside a `when`/`match` arm poisons everything after the branch** (confirmed with
    * Reid). The branch may have executed, so a following `set` may be writing the wrong state's
    * record; treating "may have morphed" as "has morphed" is the conservative direction and the
    * only one that cannot be wrong.
    */
  /** `set state S` may only name the state the entity is actually in (Reid, 2026-08-24).
    *
    * *"A set statement can only ever change the values in the current state's record. To attempt
    * to do `set state S` when the state is not S is an error that riddl should catch."*
    *
    * **Lexical, and only because `checkNoSetAfterMorph` exists.** riddl-generator measured that a
    * lexical rule would misfire on 36 reactive-bbq sites where a `morph` earlier in the clause had
    * already transitioned, and asked for flow-sensitive tracking with branch joins. Banning `set`
    * after `morph` removes that case entirely: no `set` can follow a transition, so the current
    * state is always the enclosing one. The expensive analysis was made unnecessary by the simpler
    * rule rather than implemented.
    *
    * **An entity-level handler (no enclosing state) is NOT reported here.** With more than one
    * state the entity could be in any of them, which is a real restriction — riddl-generator's
    * criterion 3, that such a handler may only touch fields common to every state record. That is
    * a separate check about FIELDS and is not attempted by this one; a single-state entity, which
    * is all the corpus has in this shape, is unambiguous either way.
    */
  private def checkSetStateIsCurrent(ss: SetStatement, parents: Parents): Unit =
    ss.field match
      case sr: StateRef =>
        val enclosing = parents.collectFirst { case st: State => st }
        for
          named <- resolution.refMap.definitionOf[State](sr.pathId, parents.head)
          current <- enclosing
        do
          // Reference identity: `Definition.equals` is structural, so two same-named states in
          // different entities would otherwise compare equal.
          if !(named eq current) then
            messages.addError(
              ss.loc,
              s"${named.identify} is not the state this entity is in here; a 'set' may only " +
                s"change the record of the CURRENT state, which is ${current.identify}",
              suggestion = s"Name ${current.identify}, or use 'morph entity <E> to state " +
                s"${named.id.value} with record <R>(…)' to transition -- noting that no 'set' " +
                "may follow a morph."
            )
          end if
      case _ => () // a FieldRef target is not about states
  end checkSetStateIsCurrent

  /** Does this statement morph, ANYWHERE inside it — including in a `when`/`match` arm?
    *
    * A morph nested in a branch may have run, so everything after the enclosing statement is
    * suspect. Reading only the top level was a real defect: `KitchenTicket.riddl:665` morphs inside
    * a `when … then … end` and the `set state` after it was reported TWICE — once truly by
    * `checkNoSetAfterMorph`, which does look inside branches, and once falsely by
    * `checkSetStateIsCurrent`, which named the enclosing state as current when the branch may have
    * changed it.
    */
  private def containsMorph(stmt: Statement): Boolean = stmt match
    case _: MorphStatement => true
    case ws: WhenStatement =>
      (ws.thenStatements.toSeq ++ ws.elseStatements.toSeq).exists {
        case st: Statement => containsMorph(st)
        case _             => false
      }
    case ms: MatchStatement =>
      (ms.cases.flatMap(_.statements.toSeq) ++ ms.default.toSeq).exists {
        case st: Statement => containsMorph(st)
        case _             => false
      }
    case fs: ForeachStatement =>
      fs.doStatements.toSeq.exists {
        case st: Statement => containsMorph(st)
        case _             => false
      }
    case _ => false
  end containsMorph

  private def checkNoSetAfterMorph(stmts: Seq[Statement]): Unit =
    var morphedAt: Option[At] = None
    stmts.foreach { stmt =>
      // Report BEFORE noting this statement, so a `morph` does not flag itself.
      morphedAt match
        case Some(mLoc) =>
          stmt match
            case _: SetStatement =>
              messages.addError(
                stmt.loc,
                s"a 'set' may not follow the 'morph' at ${mLoc.toShort}: the entity is in a " +
                  "different state by now, so this writes a record that is no longer current",
                suggestion = "Move these values into the 'morph' statement's own record " +
                  "constructor, or handle them in an `on` clause of the state being morphed TO."
              )
            case _ => ()
        case None => ()
      stmt match
        case m: MorphStatement => morphedAt = Some(m.loc)
        // A morph inside a branch may have run, so everything after the branch is suspect.
        case ws: WhenStatement =>
          // Each arm is its own list, walked on its own terms.
          checkNoSetAfterMorph(ws.thenStatements.toSeq.collect { case st: Statement => st })
          checkNoSetAfterMorph(ws.elseStatements.toSeq.collect { case st: Statement => st })
          val branches = (ws.thenStatements.toSeq ++ ws.elseStatements.toSeq).collect {
            case st: Statement => st
          }
          if branches.exists(_.isInstanceOf[MorphStatement]) then morphedAt = Some(ws.loc)
        case ms: MatchStatement =>
          val arms = ms.cases.flatMap(_.statements.toSeq) ++ ms.default.toSeq
          val armStmts = arms.collect { case st: Statement => st }
          ms.cases.foreach(mc =>
            checkNoSetAfterMorph(mc.statements.toSeq.collect { case st: Statement => st })
          )
          checkNoSetAfterMorph(ms.default.toSeq.collect { case st: Statement => st })
          if armStmts.exists(_.isInstanceOf[MorphStatement]) then morphedAt = Some(ms.loc)
        case fs: ForeachStatement =>
          checkNoSetAfterMorph(fs.doStatements.toSeq.collect { case st: Statement => st })
        case _ => ()
    }
  end checkNoSetAfterMorph

  private def checkBlockTerminal(stmts: Seq[Statement]): Unit =
    var endedBy: Option[BlockEnder] = None
    stmts.foreach { stmt =>
      endedBy match
        case Some(ender) =>
          messages.addError(
            stmt.loc,
            s"this statement is unreachable: the '${ender.keyword}' at ${ender.loc.toShort} " +
              s"${ender.why}, which ends the block",
            suggestion = ender.advice
          )
        case None =>
          stmt match
            case e: ErrorStatement     => endedBy = Some(BlockEnder.refusal(e.loc))
            case t: TerminateStatement => endedBy = Some(BlockEnder.termination(t.loc))
            case _                     => ()
      end match
      // Nested bodies are their OWN lists, exactly as in `checkRefusalsFirst`.
      stmt match
        case ws: WhenStatement =>
          checkBlockTerminal(ws.thenStatements.toSeq.collect { case s: Statement => s })
          checkBlockTerminal(ws.elseStatements.toSeq.collect { case s: Statement => s })
        case ms: MatchStatement =>
          ms.cases.foreach(mc =>
            checkBlockTerminal(mc.statements.toSeq.collect { case s: Statement => s })
          )
          checkBlockTerminal(ms.default.toSeq.collect { case s: Statement => s })
        case fs: ForeachStatement =>
          checkBlockTerminal(fs.doStatements.toSeq.collect { case s: Statement => s })
        case _ => ()
    }
  end checkBlockTerminal

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
    * Needed so a `foreach` element is not merely in scope but TYPED: without it `line` resolves and
    * `line.sku` still does not, which is the whole point of iterating. `Mapping` yields None
    * because a map has no single element type -- it is DESTRUCTURED into two names instead, by
    * [[foreachBindings]], which reads `from` and `to` directly. Guessing `to` here would silently
    * mistype every key access.
    */
  /** What `<collection> at <index>` YIELDS, paired with how many indices it takes.
    *
    * Separate from [[collectionElementType]] because the two answer opposite questions. That one is
    * "what does ITERATING this produce", which is why it returns `None` for a `Mapping` (iterating
    * a mapping yields pairs) and `Some` for a `Set`/`Graph`. Indexing reverses both: a `Mapping` is
    * the motivating case, and a `Set`/`Graph` has no index at all.
    *
    * Arity is 1 for a `Mapping` (its key) and a `Sequence` (an ordinal), and one per dimension for
    * a `Table` (Reid, 2026-08-17).
    */
  private def lookupResultType(te: TypeExpression): Option[(TypeExpression, Int)] =
    te match
      case m: Mapping  => Some(m.to -> 1)
      case s: Sequence => Some(s.of -> 1)
      case t: Table    => Some(t.of -> math.max(1, t.dimensions.size))
      case ate: AliasedTypeExpression =>
        resolution.refMap.definitionOf[Type](ate.pathId).flatMap(t => lookupResultType(t.typEx))
      case _ => None // Set and Graph have no index; anything else is not a collection

  /** The type an INDEX must have: a `Mapping`'s declared key type, or a whole number for the
    * ordinal of a `Sequence`/`Table`.
    */
  private def lookupIndexType(te: TypeExpression): Option[TypeExpression] =
    te match
      case m: Mapping  => Some(m.from)
      case s: Sequence => Some(Whole(s.loc))
      case t: Table    => Some(Whole(t.loc))
      case ate: AliasedTypeExpression =>
        resolution.refMap.definitionOf[Type](ate.pathId).flatMap(t => lookupIndexType(t.typEx))
      case _ => None

  /** Validate `<collection> at <index>[, <index>…]` — indexable, right number of indices, right
    * kind of index. Each check exists because its absence is a silent wrong answer rather than a
    * loud one.
    *
    * STAYS SILENT when the collection's type is not determinable, the same conservative rule
    * `checkTerminate` follows: reporting there would be reasoning from absence.
    */
  private def validateLookup(
    lv: LookupValue,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    valueTypeExpr(lv.collection, parents, lets, elements).foreach { te =>
      lookupResultType(te) match
        case None =>
          messages.addError(
            lv.loc,
            s"'at' requires a mapping, sequence or table, but ${lv.collection.format} is " +
              s"'${te.format}'",
            suggestion = "Index a mapping by its key, or a sequence/table by ordinal."
          )
        case Some((_, arity)) =>
          if lv.indices.sizeIs != arity then
            messages.addError(
              lv.loc,
              s"${lv.collection.format} takes $arity " +
                s"${if arity == 1 then "index" else "indices"}, but ${lv.indices.size} given",
              suggestion = if arity == 1 then "Supply exactly one index."
              else
                s"Supply one index per dimension, e.g. 'at ${List.fill(arity)("0").mkString(", ")}'."
            )
          else
            lookupIndexType(te).foreach { expected =>
              lv.indices.foreach { idx =>
                // Only LITERAL indices are judged here -- their value is visible, and they are what
                // a modeller most often gets wrong. A reference goes through the ordinary type
                // machinery, which is the same split `checkNumericLiteralConformance` already makes.
                (idx, expected) match
                  case (nl: NumericLiteral, ite: IntegerTypeExpression) =>
                    checkNumericLiteralConformance(nl, ite)
                  case (_: NumericLiteral, _) =>
                    messages.addError(
                      idx.loc,
                      s"index ${idx.format} is a number, but ${lv.collection.format} is keyed by " +
                        s"'${expected.format}'",
                      suggestion = s"Supply an index of type '${expected.format}'."
                    )
                  case (_: LiteralString, _: IntegerTypeExpression) =>
                    messages.addError(
                      idx.loc,
                      s"index ${idx.format} is a string, but ${lv.collection.format} is indexed " +
                        "by ordinal",
                      suggestion = "Supply a whole number."
                    )
                  case _ => ()
              }
            }
    }
  end validateLookup

  private def collectionElementType(te: TypeExpression): Option[TypeExpression] =
    te match
      case s: Sequence       => Some(s.of)
      case s: AST.Set        => Some(s.of)
      case g: Graph          => Some(g.of)
      case t: Table          => Some(t.of)
      case r: Replica        => Some(r.of)
      case z: ZeroOrMore     => Some(z.typeExp)
      case o: OneOrMore      => Some(o.typeExp)
      case sr: SpecificRange => Some(sr.typeExp)
      case _: Mapping        => None
      case ate: AliasedTypeExpression =>
        resolution.refMap
          .definitionOf[Type](ate.pathId)
          .flatMap(t => collectionElementType(t.typEx))
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
      case fr: FieldRef => resolution.refMap.definitionOf[Field](fr.pathId).map(_.typeEx)
      case id: Identifier =>
        val idx = letIndexOf(id.value, lets)
        if idx >= 0 then letType(lets(idx), lets.take(idx), parents, elements).map(_.typEx)
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
              suggestion =
                s"Write 'foreach ${fs.element.value}, <value> in ...' — the first name " +
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
              suggestion = s"Drop the second name: 'foreach ${fs.element.value} in ...'."
            )
            Map(fs.element.value -> elementType, v.value -> anything)
      case None =>
        Map(fs.element.value -> anything) ++ fs.valueElement.map(_.value -> anything)
  end foreachBindings

  /** The fields directly in scope at a statement: those of the enclosing entity's state record(s),
    * of the handled message, and of the enclosing function's `requires` input.
    *
    * This is a NAMING aid only — it answers "would a reader take this bare name for a field?",
    * which is what the on-clause binding's shadow warning asks. It is NOT an allow-list. It once
    * gated `foreach ... in field <path>` by identity, which rejected
    * `foreach line in field order.lines` for no better reason than that `lines` belongs to `Order`
    * rather than to the message directly. Cardinality is the whole of that question: if the path
    * resolves and lands on a collection, it is iterable, wherever it sits.
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
            letType(ls, inScopeLets.take(idx), parents, inScopeElements) match
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
  /** Does `proc` have a clause that receives a message of type `tpe`?
    *
    * One helper for two questions that were the same question: `ask`'s "the thing asked must
    * answer" and `tell`'s "the thing told must receive". Writing it twice is the pattern this
    * codebase keeps getting bitten by — two copies of one dispatch drift, and the tested copy says
    * nothing about the other.
    *
    * Three properties, all deliberate and all inherited from `validateAsk`, which had them first:
    *   - **An `on other` clause counts.** It states a policy for anything unmatched, which IS
    *     receiving the message. `Riddl.BottomlessPit` is built from exactly this.
    *   - **A State's handlers count as the Entity's.** An entity commonly holds its clauses inside
    *     a `State`, so `proc.handlers` alone cannot see them — the same fold five neighbouring
    *     checks make.
    *   - **Identity, not name.** `eq` on the resolved `Type`, because two contexts may each declare
    *     a `Created`.
    */
  /** A `sink` whose clauses are all `on other` — the deliberate-discard shape.
    *
    * Narrow on purpose: a sink that names the messages it drops, or does real work, is not this.
    * Only the catch-everything form is exempted, because that is the one with nothing executable
    * left to say.
    */
  private def isDiscardingSink(d: Definition): Boolean = d match
    // `effectiveShape`, not the `Streamlet` class: sink-ness is ARITY (no outlets, >=1 inlet), and
    // it is derived when not ascribed. Asking the class would miss `processor X as sink`.
    case p: Processor[?] if p.effectiveShape.isInstanceOf[Sink] =>
      val clauses = p.handlers.flatMap(_.clauses)
      clauses.nonEmpty && clauses.forall(_.isInstanceOf[OnOtherClause])
    case _ => false
  end isDiscardingSink

  /** The concrete message types a [[Type]] stands for: an alternation's MEMBERS, else itself.
    *
    * Follows declared aliases and nests, so `type AccountEvent is one of { Opened or Closed }` and
    * an alias to it both expand to the same two members.
    *
    * **The visited list is reference identity and is not optional.** `type A is B` / `type B is A`
    * sent an earlier alias walk into infinite recursion and killed the stack; a `Set`/`contains`
    * guard would be wrong here because `Definition.equals` is structural and would fuse two
    * distinct identical declarations, truncating a legitimate chain.
    */
  private def alternationMembers(t: Type, visited: Seq[Type] = Nil): Seq[Type] =
    if visited.exists(_ eq t) then Nil
    else
      val seen = visited :+ t
      def expand(te: TypeExpression): Seq[Type] = te match
        case alt: Alternation =>
          alt.of.toSeq.flatMap(a =>
            resolution.refMap.definitionOf[Type](a.pathId).toSeq.flatMap(m =>
              alternationMembers(m, seen)
            )
          )
        case ate: AliasedTypeExpression =>
          resolution.refMap
            .definitionOf[Type](ate.pathId)
            .toSeq
            .flatMap(a => alternationMembers(a, seen))
        case _ => Seq(t)
      expand(t.typEx)
  end alternationMembers

  /** Does `proc` have a clause that receives a message of type `tpe`?
    *
    * One helper for two questions that were the same question: `ask`'s "the thing asked must
    * answer", `tell`'s "the thing told must receive", and an inlet's "what arrives must be handled".
    * Writing it three times is the pattern this codebase keeps getting bitten by.
    *
    * Four properties, all deliberate:
    *   - **An `on other` clause counts.** It states a policy for anything unmatched, which IS
    *     receiving the message. `Riddl.BottomlessPit` is built from exactly this.
    *   - **A State's handlers count as the Entity's.** An entity commonly holds its clauses inside
    *     a `State`, so `proc.handlers` alone cannot see them.
    *   - **Identity, not name.** `eq` on the resolved `Type`, because two contexts may each declare
    *     a `Created`.
    *   - **ALTERNATIONS EXPAND ON BOTH SIDES**, which the first version of this check got wrong in
    *     both directions and ossum.tech caught. An inlet typed `one of { A or B }` is satisfied by
    *     clauses for A AND B (every member must be handled -- handling only A leaves a B arriving
    *     with nothing to do); and a clause naming `one of { A or B }` receives a `tell` of A. The
    *     identity-only version demanded, for the corpus's near-universal `type XEvent is one of
    *     {...}` inlet idiom, something no legal spelling could satisfy except `on other` -- the
    *     same unsatisfiable-demand trap the discard-sink exemption exists to avoid.
    */
  private def unreceivedMembers(proc: Processor[?], tpe: Type): Seq[Type] =
    val stateHandlers = proc match
      case e: Entity => e.states.flatMap(_.handlers)
      case _         => Seq.empty
    val clauses = (proc.handlers ++ stateHandlers).flatMap(_.clauses)
    if clauses.exists(_.isInstanceOf[OnOtherClause]) then Nil
    else
      // What the clauses receive, each expanded through any alternation it names.
      val received: Seq[Type] = clauses.flatMap {
        case omc: OnMessageLikeClause if omc.msg.nonEmpty =>
          resolution.refMap
            .definitionOf[Type](omc.msg.pathId)
            .toSeq
            .flatMap(c => c +: alternationMembers(c))
        case _ => Seq.empty
      }
      // What must be received: EVERY member the type admits. `alternationMembers` returns the type
      // itself when it is not an alternation, so the ordinary case is a one-element list. An empty
      // result means nothing was determinable, which is reported rather than passed silently.
      alternationMembers(tpe) match
        case Nil     => Seq(tpe)
        case members => members.filterNot(w => received.exists(_ eq w))
  end unreceivedMembers

  private def receivesMessageType(proc: Processor[?], tpe: Type): Boolean =
    unreceivedMembers(proc, tpe).isEmpty
  end receivesMessageType

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
      if !receivesMessageType(proc, qt) then
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

  /** The [[MethodArgument]]s a Processor's `on init`/`on term` clause declares, or `Seq.empty` when
    * it declares none (including when it has no such clause at all -- indistinguishable from this
    * function's point of view, and [[checkLifecycleInvocation]] treats an unresolved target the
    * same way via `checkRef`'s silence). Entity state handlers are folded in exactly as
    * `validateAsk` does for `on query`/`on other`: these clauses commonly live inside a `State`
    * rather than directly on the entity (state handlers apply to the entity, per `WithHandlers`'s
    * literal `contents` filter not descending into `State`).
    */
  private def lifecycleClauseParameters(
    p: Processor[?]
  )(select: PartialFunction[OnClause, Seq[MethodArgument]]): Seq[MethodArgument] =
    val stateHandlers = p match
      case e: Entity => e.states.flatMap(_.handlers)
      case _         => Seq.empty
    (p.handlers ++ stateHandlers).flatMap(_.clauses).collectFirst(select).getOrElse(Seq.empty)
  end lifecycleClauseParameters

  /** A70/instance-identity: validate `initiate <processor>(args)` / `terminate <processor>(args)`
    * against the target's declared `on init`/`on term` parameters -- arity, then best-effort
    * per-argument type compatibility via the SAME helper a constructor and a call use
    * ([[checkArgumentTypes]]; see its scaladoc for why a second copy is not written). The clauses
    * declare [[MethodArgument]]s, not [[Field]]s, so they are adapted rather than the helper
    * forked.
    *
    * `initiate` and `terminate` shared this body VERBATIM, down to a duplicated local `count`
    * helper, until the final review of this branch. They are one function for exactly the reason
    * [[checkArgumentTypes]] is reused rather than forked one level down: two copies drift, and a
    * rule tightened for one would silently not apply to the other.
    *
    * Silent when the target does not resolve -- `ResolutionPass` already reported that (mirrors
    * `validateAsk`'s target resolution) -- and silent when the target's body is a `???` STUB, per
    * the standing ruling: a definition that has said "don't expect much" earns a Missing warning
    * about its body and nothing else, so reasoning from what an unwritten body does NOT declare is
    * exactly the inference the ruling forbids. `checkTellAddressing` gates the same way; these two
    * did not, so `initiate entity Order(x = "1")` against `entity Order is { ??? }` drew a hard
    * Error. (A `???` body and an explicitly empty one parse to the same empty `contents`, which is
    * what `p.isEmpty` reads.)
    *
    * Argument VALUES are validated either way: they belong to the CALL site, and the callee being a
    * stub says nothing about whether the names written here exist.
    */
  private def checkLifecycleInvocation(
    loc: At,
    // The RESOLVED target processor. Since 2026-08-15 the two callers reach it differently --
    // `initiate` still resolves a written `ProcessorRef`, while `terminate` DERIVES it from the
    // `Id(entity E)` type of its target value -- so this takes the processor itself rather than a
    // ref, and each caller owns its own "does the target exist" reporting.
    p: Processor[?],
    args: Seq[ConstructorArg],
    // "on init" / "on term" -- the CLAUSE being invoked, which is what the diagnostic is about.
    clauseKeyword: String,
    // The no-argument spelling of the statement AS THE AUTHOR WOULD WRITE IT, e.g.
    // `initiate Order` or `terminate order.id`. Passed ready-made because the two statements no
    // longer have the same shape: `initiate` drops its parentheses, `terminate` drops a whole
    // `with (...)` clause.
    noArgSpelling: String,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  )(select: PartialFunction[OnClause, Seq[MethodArgument]]): Unit =
    locally {
      if p.nonEmpty then
        val declared = lifecycleClauseParameters(p)(select)
        def count(n: Int, word: String): String = s"$n $word${if n == 1 then "" else "s"}"
        if declared.isEmpty && args.nonEmpty then
          messages.addError(
            loc,
            s"${p.identify} declares '$clauseKeyword' with no parameters, but " +
              s"${count(args.size, "argument")} supplied",
            suggestion = s"Write '$noArgSpelling' with no arguments."
          )
        else if declared.size != args.size then
          messages.addError(
            loc,
            s"${p.identify} declares '$clauseKeyword' with ${count(declared.size, "parameter")}, but " +
              s"${count(args.size, "argument")} supplied",
            suggestion =
              s"Supply ${declared.size}: ${declared.map(a => s"${a.name}: ${a.typeEx.format}").mkString(", ")}."
          )
        else
          // Reuse the EXISTING per-argument helper (`checkArgumentTypes`) rather than writing a
          // second one — its scaladoc records that two hand-written copies were free to drift, so a
          // rule tightened for constructors would silently not apply here. It wants Seq[Field], and
          // these clauses declare Seq[MethodArgument], so adapt rather than fork:
          val asFields: Seq[Field] = declared.map { a =>
            Field(a.loc, Identifier(a.loc, a.name), a.typeEx)
          }
          checkArgumentTypes(args, asFields, "parameter", parents, lets, elements)
        end if
      end if
    }
    args.foreach(arg => validateValue(arg.value, parents, lets, elements))
  end checkLifecycleInvocation

  /** The message both lifecycle statements use when their target is a processor that has no
    * instances to create or destroy.
    *
    * **A singleton's `Id` is legal and useful** (Reid, 2026-08-15): it exists so messages can be
    * SENT to the singleton, denoting its singular DEPLOYMENT rather than a shard or partition --
    * addressing it means "select the right shard/partition and forward", the singleton being
    * treated as a whole despite a clustered arrangement. What is not permitted is a LIFECYCLE
    * operation on one. So this restriction is checked here by hand and is deliberately NOT
    * expressed by narrowing which processors may appear in an `Id(...)`.
    */
  private def reportNotInstantiable(loc: At, p: Processor[?], statementKeyword: String): Unit =
    messages.addError(
      loc,
      s"'$statementKeyword' is not allowed on ${p.identify}: only an entity has instances to " +
        s"create or destroy",
      suggestion = "A singleton's lifecycle is a deployment concern, outside the model. Its " +
        "Id(...) may still be used to send it messages."
    )

  /** A70/instance-identity: `initiate <processor>[(args)]`. See [[checkLifecycleInvocation]]. */
  private def checkInitiate(
    init: Initiate,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    checkRef[Processor[?]](init.processor, parents).foreach {
      case e: Entity =>
        checkLifecycleInvocation(
          init.loc,
          e,
          init.args,
          s"${Keyword.on} ${Keyword.init}",
          s"${Keyword.initiate} ${init.processor.format}",
          parents,
          lets,
          elements
        ) { case oic: OnInitializationClause => oic.parameters }
      case p =>
        reportNotInstantiable(init.loc, p, Keyword.initiate)
        init.args.foreach(arg => validateValue(arg.value, parents, lets, elements))
    }
  end checkInitiate

  /** A70/instance-identity: `terminate <target> [with (args)]`, where `target` is a VALUE typed
    * `Id(entity E)` (Reid, 2026-08-15). See [[AST.TerminateStatement]] for the design.
    *
    * The entity is DERIVED from the target's type rather than named by a second reference, so there
    * is no ref/id pair that could contradict and nothing here checks for one.
    *
    * **Silent when the target's type cannot be determined.** `valueTypeExpr` yields `None` for a
    * literal and for an unascribed `prompt(...)` typed hole; reporting those would be reasoning
    * from absence, and the A20 ruling is that an unwired position stays quiet rather than guessing.
    * A target that resolves to a type which is simply NOT an `Id` is a different matter -- that is
    * determinable, and it is an Error.
    */
  private def checkTerminate(
    term: TerminateStatement,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    validateValue(term.target, parents, lets, elements)
    valueTypeExpr(term.target, parents, lets, elements) match
      case None => // undeterminable -- see scaladoc
        term.args.foreach(arg => validateValue(arg.value, parents, lets, elements))
      case Some(te) =>
        uniqueIdOf(te, Nil) match
          case None =>
            messages.addError(
              term.loc,
              s"'${Keyword.terminate}' requires a value of type 'Id(entity ...)', but " +
                s"${term.target.format} is '${te.format}'",
              suggestion = "Terminate names the INSTANCE to end, e.g. 'terminate self.id' or " +
                "'terminate <field typed Id(entity ...)>'."
            )
            term.args.foreach(arg => validateValue(arg.value, parents, lets, elements))
          case Some((uid, owner)) =>
            resolveIdTarget(uid, owner, parents) match
              case Some(e: Entity) =>
                checkLifecycleInvocation(
                  term.loc,
                  e,
                  term.args,
                  s"${Keyword.on} ${Keyword.term}",
                  s"${Keyword.terminate} ${term.target.format}",
                  parents,
                  lets,
                  elements
                ) { case otc: OnTerminationClause => otc.parameters }
              case Some(p) =>
                reportNotInstantiable(term.loc, p, Keyword.terminate)
                term.args.foreach(arg => validateValue(arg.value, parents, lets, elements))
              case None => // unresolved Id target -- ResolutionPass already reported it
                term.args.foreach(arg => validateValue(arg.value, parents, lets, elements))
  end checkTerminate

  /** The [[UniqueId]] a type expression denotes, paired with the [[Type]] that DECLARED it when it
    * arrived through an alias chain (`type OrderId is Id(Order)`) -- riddl-models' documented house
    * style names an `Id` that way, and all 227 of its `Id(...)` uses do, so a check matching a bare
    * `UniqueId` alone would miss essentially every real model.
    *
    * The owner travels with the id for the same reason `fieldsWithOwner` carries one: the path
    * inside the `Id(...)` was written in the ALIAS's scope, so resolving it needs that scope as the
    * refMap parent, not the scope of whatever statement is asking. See [[resolveIdTarget]].
    *
    * Carries a `seen` list guarded by reference identity (`eq`, never `contains`): `type A is B` /
    * `type B is A` otherwise recurses until the stack dies, and [[Definition]] compares
    * structurally, so a `Set` would fuse two distinct identical alias declarations and truncate a
    * legitimate chain. Same guard, same reason, as `fieldsWithOwner`.
    */
  private def uniqueIdOf(te: TypeExpression, seen: List[Type]): Option[(UniqueId, Option[Type])] =
    te match
      case uid: UniqueId => Some(uid -> seen.headOption)
      case ate: AliasedTypeExpression =>
        resolveTypeAlias(ate).flatMap { aliased =>
          if seen.exists(_ eq aliased) then None
          else uniqueIdOf(aliased.typEx, aliased :: seen)
        }
      case _ => None

  /** The [[Processor]] an `Id(...)` names.
    *
    * **Two lookups, because two kinds of path reach here and only one is in the refMap.** The
    * refMap records what [[ResolutionPass]] actually resolved, so it holds paths that were WRITTEN.
    * But `valueTypeExpr` also SYNTHESIZES `UniqueId`s -- for `initiate` (the new instance's id) and
    * for `self.id` -- and those carry a fully-qualified `pathOf(p)` that was never a written
    * reference and therefore has no refMap entry at all. Looking only in the refMap made every
    * `terminate` whose target came from `initiate` or `self` resolve to `None` and skip its checks
    * in silence, which is exactly the "no diagnostic at all" outcome this whole feature exists to
    * remove.
    *
    * So: refMap under the alias's owner (most precise -- disambiguates two same-named entities),
    * then refMap under the asking scope, then the symbol table by fully-qualified name.
    */
  private def resolveIdTarget(
    uid: UniqueId,
    owner: Option[Type],
    parents: Parents
  ): Option[Processor[?]] =
    owner
      .flatMap(o => resolution.refMap.definitionOf[Processor[?]](uid.entityPath, o))
      .orElse(resolution.refMap.definitionOf[Processor[?]](uid.entityPath, parents.head))
      .orElse(symbols.lookup[Processor[?]](uid.entityPath.value.reverse).headOption)

  /** The one-line alias resolution step shared by `fieldsWithOwner`, `aggregateFieldsOf`,
    * `typeExprCategory` and `typeExprMessageKind`: given an [[AliasedTypeExpression]] (`command
    * Ship is Shipment`'s `Shipment` reference), the [[Type]] it names. Extracted per the task-1
    * review (round 1), which flagged `typeExprMessageKind` as a FOURTH near-identical copy of this
    * exact expression — each of the four otherwise differs (return shape, terminal case), so this
    * is the one line actually worth sharing rather than the whole alias-following recursion, which
    * each function still writes for itself around its own terminal cases.
    */
  private def resolveTypeAlias(ate: AliasedTypeExpression): Option[Type] =
    resolution.refMap.definitionOf[Type](ate.pathId)

  /** A55-style: the aggregate [[Field]]s of a message [[Type]], each paired with the [[Type]] node
    * that actually DECLARES it -- itself for a direct aggregate, or (following the alias chain) the
    * aliased-to `Type` for `command Ship is Shipment`. Needed because [[ResolutionPass]] resolves a
    * field's `UniqueId` type expression while that OWNING Type is `parents.head` (Pass.scala pushes
    * a `Branch` -- which `Type` is -- onto the parent stack for its own children's resolution, so a
    * `Type`'s fields resolve with the Type ITSELF, not its enclosing Context, as the refMap key's
    * parent). Looking a field's resolution up again later requires the SAME parent it was recorded
    * under, so the owning Type must travel with the field, not just `mt`.
    */
  private def fieldsWithOwner(t: Type, seen: List[Type] = Nil): Seq[(Field, Type)] =
    if seen.exists(_ eq t) then Seq.empty[(Field, Type)]
    else
      t.typEx match
        case ate: AggregateTypeExpression => ate.fields.map(f => f -> t)
        case ate: AliasedTypeExpression =>
          resolveTypeAlias(ate).toSeq.flatMap(fieldsWithOwner(_, t :: seen))
        case _ => Seq.empty[(Field, Type)]

  /** The cycle guard shared by [[fieldsWithOwner]] and [[isAddressTypeExpression]], and the reason
    * both carry a `seen` list: `type A is B` / `type B is A` otherwise recurses forever. This was a
    * real crash in rc.14 (`java.lang.StackOverflowError ... at ValidationPass.fieldsWithOwner`,
    * reproduced against the released binary), which surfaces to the author as
    * `[severe] Exception Thrown` with no line number -- strictly worse than any wrong message,
    * since it takes the whole pass chain down with it.
    *
    * Reference identity (`eq`), NOT `contains`: [[Definition]] overrides `equals` structurally, so
    * a `Set`/`contains` guard would treat two DISTINCT but identical alias declarations as the same
    * node and silently truncate a legitimate chain. Alias chains are a handful of links, so the
    * linear scan is free.
    *
    * A cycle is a modelling error in its own right and something may eventually want to REPORT it;
    * terminating is a separate obligation from diagnosing, and this only does the former.
    */
  private def isAddressFieldFor(f: Field, owner: Type, p: Processor[?]): Boolean =
    isAddressTypeExpression(f.typeEx, owner, p, Nil)

  /** Does type expression `te`, declared on `owner`, denote `Id(p)`?
    *
    * Resolved-identity comparison (`eq`), never by name -- looks the `UniqueId.entityPath` up in
    * the refMap using `owner` as the key's parent, which is the SAME parent [[ResolutionPass]]
    * recorded it under (see [[fieldsWithOwner]]).
    *
    * The alias arm is riddl-models' rc.14 report: a field typed by the named alias `type OrderId is
    * Id(entity Order)` was not recognised, because this matched `UniqueId` alone and fell to
    * `false` for everything else. That alias is the DOCUMENTED house style, so the check caught
    * only the rare inline spelling and misfired on the common one -- 72 of 86 distinct findings in
    * reactive-bbq were false, and it aborted riddl-models' `checkAll`. [[fieldsWithOwner]] already
    * followed aliases for the MESSAGE type; this is the same step for the FIELD's type, which is
    * why the two now share a shape and a guard.
    *
    * `owner` must become the resolved alias `Type` on the way down: a `Type`'s own type expression
    * is resolved with that `Type` as `parents.head`, so looking `entityPath` up under the ORIGINAL
    * owner would miss (see [[fieldsWithOwner]]'s note on the same point).
    */
  private def isAddressTypeExpression(
    te: TypeExpression,
    owner: Type,
    p: Processor[?],
    seen: List[Type]
  ): Boolean =
    te match
      case uid: UniqueId =>
        resolution.refMap.definitionOf[Processor[?]](uid.entityPath, owner).exists(_ eq p)
      case ate: AliasedTypeExpression =>
        resolveTypeAlias(ate).exists { aliased =>
          !seen.exists(_ eq aliased) &&
          isAddressTypeExpression(aliased.typEx, aliased, p, aliased :: seen)
        }
      case _ => false

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
    * `aggregateFieldsOf` already follows the alias chain (as `checkOnOtherBinding` and friends rely
    * on elsewhere), so gating on ITS result lets aliases through while still catching the real stub
    * shape: a `command Foo is { ??? }` body parses to the SAME empty-aggregate AST as an explicit
    * `{ }` (both hit `TypeParser`'s `undefined(Seq.empty[AggregateContents])` alternative), so
    * "zero fields after resolving" is exactly the stub condition the standing `???` ruling asks us
    * to exempt -- its absent fields must not be read as "no Id(target) field".
    *
    * `lets` added for the message-value-source widening (task-1 review, round 1): the operand's
    * TYPE now comes from [[widenedOperandType]], not the narrow [[operandType]], so a `tell`
    * addressing a state field/`let`-local/function-result/`ask`-result-sourced message is checked
    * for its `by`/ambiguity/missing-address obligations exactly as a keyword-led or bound one is --
    * before this fix, a widened operand made this whole function silently see no message at all, so
    * its Errors never fired. Threading `lets` costs nothing new here: `checkStatementScopes`'s
    * `TellStatement` case already has it, at the one call site below.
    */
  /** A4 completion: the cross-context `tell` isolation seam (Reid, 2026-08-13).
    *
    * A `tell` into a DIFFERENT Context is an Error unless the message type is declared in a Domain
    * ancestral to BOTH. Across domains an adaptor is always required, so the exemption cannot
    * apply. A4 already rejects naming a foreign context's message TYPES outside adaptor scope; this
    * extends the same seam to foreign processor TARGETS, which A4 left open.
    *
    * **`send` is deliberately not covered.** `SendStatement.portlet` is a `PortletRef`, so `send`
    * names an Inlet or Outlet and structurally cannot name a foreign processor. A message crossing
    * a boundary by `send` travels through a CONNECTOR, which is the sanctioned mediator for
    * streaming exactly as an adaptor is for direct messaging.
    *
    * **An Adaptor is exempt**, because A4 makes it the one sanctioned place to name another
    * context's messages -- an adaptor that could not tell across the boundary could not do its job.
    *
    * **Shipped straight as an Error, skipping this repo's usual warn-then-flip**, on evidence: a
    * resolution-based census of 188 corpus models found 18 crossings in 7,537 tells (0.24%), all in
    * two models. The text heuristic that argued for a staged rollout claimed 5,301 (64%) and was
    * wrong by 294x -- a dotted path means the author QUALIFIED the target, not that it crosses
    * anything.
    */
  /** A value target must actually name an instance — its type has to be an `Id(...)`.
    *
    * Mirrors [[checkTerminate]], with ONE deliberate difference: `terminate` additionally requires
    * an `Id(entity E)`, because only an entity is multiply-instantiated and so only an entity can
    * be ended. `tell` has no such restriction — `Id(context C)` is a perfectly good addressee.
    * *"A singleton's `Id` is how you SEND IT MESSAGES"* (Reid, 2026-08-15): it denotes the singular
    * deployment, and addressing it means select the right shard and forward. So this check asks
    * only that the value be an `Id` of SOMETHING.
    *
    * **Silent when the type is undeterminable** (a bare `let n = 5`, an unascribed `prompt(…)`),
    * the same conservative rule `checkTerminate` follows: reporting there would be reasoning from
    * absence.
    */
  private def checkTellTargetValue(
    ts: TellStatement,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    ts.target match
      case _: ProcessorRef[?] => () // a static target is checked by `checkRef` in validateStatement
      case v: Value =>
        validateValue(v, parents, lets, elements)
        valueTypeExpr(v, parents, lets, elements).foreach { te =>
          if uniqueIdOf(te, Nil).isEmpty then
            messages.addError(
              ts.loc,
              s"'${Keyword.tell}' to a value requires a value of type 'Id(...)' naming the " +
                s"instance to tell, but ${v.format} is '${te.format}'",
              suggestion = "Address an instance with a value whose type is 'Id(entity ...)' — " +
                "'tell <msg> to self.id' or a field typed 'Id(entity ...)' — or name a processor " +
                "statically with a keyword, e.g. 'to entity Order'."
            )
          end if
        }
    end match
  end checkTellTargetValue

  private def checkTellIsolation(
    ts: TellStatement,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    val tellingCtx: Option[Context] = parents.collectFirst { case c: Context => c }
    // The adaptor exemption is checked on the PARENTS rather than the enclosing context, because an
    // adaptor sits inside a context and would otherwise be judged by it.
    val insideAdaptor = parents.exists(_.isInstanceOf[Adaptor])
    if !insideAdaptor then
      (
        tellingCtx,
        tellTargetProcessor(ts, parents)
      ).match
        case (Some(fromCtx), Some(target)) =>
          val toCtx: Option[Context] = target match
            case c: Context => Some(c)
            case other      => symbols.parentsOf(other).collectFirst { case c: Context => c }
          toCtx.foreach { targetCtx =>
            if !(targetCtx eq fromCtx) then
              val fromDomains = symbols.parentsOf(fromCtx).collect { case d: Domain => d }
              val toDomains = symbols.parentsOf(targetCtx).collect { case d: Domain => d }
              // Reference identity: `Definition.equals` is structural, so two same-named domains
              // in different trees would otherwise fuse and manufacture a shared ancestor.
              val shared = fromDomains.filter(fd => toDomains.exists(_ eq fd))
              val msgType: Option[Type] = ts.msg match
                case mr: MessageRef => resolution.refMap.definitionOf[Type](mr.pathId, parents.head)
                case c: Constructor =>
                  resolution.refMap.definitionOf[Type](c.ref.pathId, parents.head)
                case vr: ValueRef => valueRefType(vr, parents, lets, elements)
              // The IMMEDIATE parent, not the ancestor chain. `parentsOf` returns every ancestor,
              // so a type declared inside the TARGET's context still lists the shared domain
              // among its ancestors -- as does everything else in the tree -- and the exemption
              // would swallow the whole rule. The ruling says the type must be DECLARED IN a
              // domain ancestral to both, which is a statement about where it is written.
              val exempt = msgType.exists { mt =>
                symbols.parentOf(mt).exists(p => shared.exists(_ eq p))
              }
              if shared.isEmpty then
                messages.addError(
                  ts.loc,
                  s"'tell' crosses a DOMAIN boundary from ${fromCtx.identify} to " +
                    s"${targetCtx.identify}, which the context isolation seam forbids: an " +
                    "adaptor is always required across domains",
                  suggestion = s"Route this through an adaptor in ${fromCtx.id.value}."
                )
              else if !exempt then
                messages.addError(
                  ts.loc,
                  s"'tell' crosses the context isolation seam from ${fromCtx.identify} to " +
                    s"${targetCtx.identify}: ${ts.msg.format} is not declared in a domain " +
                    "ancestral to both",
                  suggestion = s"Declare the message type in ${shared.head.identify}, or route " +
                    "this through an adaptor."
                )
          }
        case _ => () // unresolved target -- ResolutionPass already reported it
  end checkTellIsolation

  private def checkTellAddressing(
    ts: TellStatement,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    // A VALUE target is skipped entirely, and that is the point of the feature rather than a gap.
    // This check exists because a statically-named `tell` does not say WHICH instance it addresses,
    // so the address has to be recovered structurally from a message field typed `Id(target)`. A
    // value target states the address outright. Running the search anyway would demand an address
    // field that the statement has made unnecessary, and report an ambiguity between two fields
    // neither of which is being used.
    val staticTarget: Option[ProcessorRef[Processor[?]]] = ts.target match
      case pr: ProcessorRef[?] => Some(pr.asInstanceOf[ProcessorRef[Processor[?]]])
      case _: Value            => None
    staticTarget.flatMap(pr => checkRef[Processor[?]](pr, parents)).foreach { p =>
      widenedOperandType(ts.msg, parents, lets, elements).foreach { mt =>
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

  /** The LEXICAL-SCOPE THREADING INVARIANT, which this function and its ~20 relatives share:
    * **wherever `lets` goes, `elements` goes with it.** The two are one scope written as two
    * parameters — `lets` holds the statement-ordered `let` locals, `elements` holds the
    * name-to-type bindings that are NOT statements (`foreach` elements, and since the final review
    * of the instance-identity branch, `on init`/`on term` parameters).
    *
    * Six validators took `lets` alone and defaulted `elements` to empty: `validateComparand`,
    * `checkWhenValueRef`, `validateMatch`, `validatePut`, `validateReturn` and `validateCall`. So
    * `when seed > 5` inside `on init(seed: Integer)` was a false Error, and so — since A25 shipped
    * — was `line.qty > 5` inside a `foreach`: the loop element resolved in the body's statements
    * but not in any comparison, `when`, `match`, `put`, `return` or call argument within it.
    *
    * **The `= Map.empty` defaults were deleted from every one of these signatures**, which is what
    * makes the invariant hold: a caller that forgets the scope is now a compile error rather than a
    * silent narrowing. The genuinely scope-less callers (an `invariant` condition, which has no
    * enclosing statement list) pass `Map.empty` EXPLICITLY, so the absence is a written decision.
    * Do not reintroduce a default here.
    */
  private def valueType(
    v: Value,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Option[Type] =
    v match
      case _: LiteralString => None // pseudo-code, untyped
      // An `empty T*` names no single Type -- `T*` is a cardinality wrapper, not a declaration --
      // so the TypeExpression-level answer in `valueTypeExpr` is the one with content here.
      case _: EmptyValue    => None
      case _: LookupValue   => None // element type is a TypeExpression; see valueTypeExpr
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
      case vr: ValueRef => valueRefType(vr, parents, lets, elements)
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
      case _: SelfValue => None
      // `initiate`'s type is a SYNTHESIZED UniqueId, not a named Type -- same reasoning as `self`,
      // immediately above. `valueTypeExpr` computes it (see its `Initiate` arm).
      case _: Initiate => None
      // A numeric literal denotes no NAMED Type -- it is a raw literal, not a reference to a
      // declaration. Best-effort numeric type-compatibility checking (matching it against a
      // field's declared numeric type) is Task 5's job and reads `.isInteger`/`.asBigDecimal`
      // directly rather than through this named-Type lookup.
      case _: NumericLiteral => None

  /** A28: the broad category of a [[Value]] for best-effort boolean/comparison checks: `"boolean"`,
    * `"numeric"`, or `"string"`; `None` when it cannot be determined (skip the check). A
    * [[BooleanExpression]] is always boolean; otherwise the value's named [[Type]] is classified by
    * its underlying [[TypeExpression]], following one level of type alias.
    */
  private def valueCategory(
    v: Value,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Option[String] =
    v match
      case _: BooleanExpression => Some("boolean")
      case _ => valueType(v, parents, lets, elements).flatMap(t => typeExprCategory(t.typEx))

  private def typeExprCategory(te: TypeExpression): Option[String] =
    te match
      case _: Bool        => Some("boolean") // Bool <: NumericType, so it must precede NumericType
      case _: NumericType => Some("numeric")
      case _: String_     => Some("string")
      case ate: AliasedTypeExpression =>
        resolveTypeAlias(ate).flatMap(t => typeExprCategory(t.typEx))
      case _ => None

  /** A9b: the four [[AggregateUseCase]]s that are actual MESSAGES — the same set [[MessageRef]]'s
    * four subtypes (`CommandRef`/`EventRef`/`QueryRef`/`ResultRef`) restrict a keyword-led operand
    * to. `RecordCase` is deliberately excluded: a Record is `morph`'s shape, not a message's.
    */
  private val messageUseCases: scala.collection.immutable.Set[AggregateUseCase] =
    scala.collection.immutable.Set(
      AggregateUseCase.CommandCase,
      AggregateUseCase.EventCase,
      AggregateUseCase.QueryCase,
      AggregateUseCase.ResultCase
    )

  /** A56 (widened): the [[AggregateUseCase]] a resolved [[TypeExpression]] denotes, following alias
    * chains exactly as [[typeExprCategory]] does immediately above — answering "which message kind"
    * instead of "boolean/numeric/string". Used by [[checkMessageOperandSource]] to decide whether a
    * bare `ValueRef` operand names a legal `send`/`tell` message value: `Some` only for a
    * command/event/query/result aggregate (see [[messageUseCases]]), `None` for a Record (a `morph`
    * shape, not a message), a Type/Graph/Table aggregate, or any non-aggregate type.
    */
  private def typeExprMessageKind(te: TypeExpression): Option[AggregateUseCase] =
    te match
      case auc: AggregateUseCaseTypeExpression => Some(auc.usecase).filter(messageUseCases.contains)
      case ate: AliasedTypeExpression =>
        resolveTypeAlias(ate).flatMap(t => typeExprMessageKind(t.typEx))
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

  /** A55: the aggregate [[Field]]s of a [[TypeExpression]], following the alias chain.
    *
    * `seen` is the SAME cycle guard [[fieldsWithOwner]] carries and for the same reason: `type A is
    * B` / `type B is A` otherwise recurses until the stack dies, which surfaces to the author as
    * `[severe] Exception Thrown` with no line number and takes the whole pass chain down. It was
    * missing here — the defect was diagnosed once for `fieldsWithOwner` (rc.14) and its sibling
    * missed, the same "fix the instance, not the shape" miss the flaky-benchmark round recorded. It
    * was latent only because no caller reached a cyclic alias; Task 4's bare-operand warning does,
    * and `passes`'s own `CheckMessagesTest` corpus reproduced it on the first full run.
    *
    * Reference identity (`eq`), NOT `contains`: [[Definition]] overrides `equals` structurally, so
    * a `Set` would fuse two distinct but identical alias declarations and truncate a legitimate
    * chain.
    */
  private def aggregateFieldsOf(te: TypeExpression, seen: List[Type] = Nil): Seq[Field] =
    te match
      case ate: AggregateTypeExpression => ate.fields
      case ate: AliasedTypeExpression =>
        resolveTypeAlias(ate).toSeq.flatMap { t =>
          if seen.exists(_ eq t) then Seq.empty[Field]
          else aggregateFieldsOf(t.typEx, t :: seen)
        }
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
    parents: Parents,
    elements: Map[String, TypeExpression]
  ): Option[Type] =
    ls.typeRef
      .flatMap(tr => resolution.refMap.definitionOf[Type](tr.pathId))
      .orElse(valueType(ls.expression, parents, priorLets, elements))

  /** The [[TypeExpression]] a `let`'s DECLARED ascription names when that ascription is a bare
    * predefined keyword (`let n: Integer = …`).
    *
    * [[letType]] cannot answer this: it returns a named [[Type]] Definition, and predefined types
    * are deliberately never entered into the symbol table (see `PredefTypes.typeExpressionFor`'s
    * doc, and `PredefinedModule`'s note on why the standard module stays out of the shared maps).
    * So a `let` ascribed with a predefined keyword yielded `None` from every "what type is this
    * value" query, and the type the author had WRITTEN OUT was invisible — while the alias spelling
    * (`type Nat is Natural`) worked. That is a gap in what we LOOKED AT, not in what is knowable,
    * which is why it deserves an answer rather than the silence reserved for genuinely
    * undeterminable types.
    *
    * The `sizeIs == 1` guard and the keyword set are shared with `ResolutionPass`, which skips
    * `resolveARef` for exactly these, and with `checkStatementScopes`. A keyword needing arguments
    * (`Currency`, `Decimal`, …) is not in the set and is unaffected: a bare `let x: Currency = …`
    * is incomplete regardless.
    */
  private def letDeclaredPredefinedType(ls: LetStatement): Option[TypeExpression] =
    ls.typeRef
      .filter(_.pathId.value.sizeIs == 1)
      .flatMap(tr => PredefTypes.typeExpressionFor(tr.pathId.value.head, tr.loc))

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
    elements: Map[String, TypeExpression]
  ): Option[TypeExpression] =
    val names = vr.path.value
    if names.isEmpty then None
    // A25: a `foreach` element is typed by the collection it iterates, and the remaining path
    // components walk that type exactly as they walk a `let`'s. Checked BEFORE lets so an element
    // shadows an outer local of the same name, matching the lexical rule `let` already follows.
    else if elements.contains(names.head) then typeExprOfPath(elements(names.head), names.tail)
    else
      val idx = letIndexOf(names.head, lets)
      if idx >= 0 then
        val ls = lets(idx)
        val priorLets = lets.take(idx)
        letType(ls, priorLets, parents, elements)
          .map(_.typEx)
          // A DECLARED predefined ascription outranks inference from the expression, exactly as a
          // declared named Type does -- it is the same fact, written with a keyword instead of an
          // alias. Ordered before the inference fallback for that reason, not for precedence
          // between two guesses.
          .orElse(letDeclaredPredefinedType(ls))
          .orElse(valueTypeExpr(ls.expression, parents, priorLets, elements))
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
  /** Does `te` admit an empty value — is its MINIMUM cardinality zero?
    *
    * This one rule is what lets a single `empty` literal serve both the absent optional and the
    * empty collection: they are the same inhabitant under different upper bounds. Total over the
    * four cardinality wrappers, so a bare `T` (exactly one) and `T+` (at least one) correctly
    * answer false rather than falling through a catch-all.
    */
  private def admitsEmpty(te: TypeExpression): Boolean = te match
    case _: Optional       => true
    case _: ZeroOrMore     => true
    case sr: SpecificRange => sr.min == 0
    case _                 => false

  private def valueTypeExpr(
    v: Value,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Option[TypeExpression] =
    v match
      // `let e = empty T*` infers `T*` -- the ascription IS the type, which is the whole point of
      // the ascribed form. A bare `empty` has no type of its own; the position supplies it.
      case ev: EmptyValue => ev.typeEx
      case vr: ValueRef   => valueRefTypeExpr(vr, parents, lets, elements)
      // A55/`self`: the SYNTHESIZED Aggregation is the only place `self`'s type is materialized.
      // `let me = self` then `me.id` reaches this ARM through `valueRefTypeExpr`'s
      // `valueTypeExpr(ls.expression, …)` fallback, walked by `typeExprOfPath` exactly like any
      // other let's inferred type -- no special casing needed there.
      case sv: SelfValue =>
        enclosingProcessorOf(parents).map { p =>
          val agg = SelfValue.aggregation(pathOf(p))
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
      // A20: an ASCRIBED typed hole states its type as plainly as a literal does -- that is what
      // the ascription is FOR. An UNASCRIBED hole still yields None here, so A20's conservative
      // rule is untouched: the silence belongs to the form that says nothing, not to every
      // `prompt(...)`. Note this reports the type the author WROTE; whether that ascription agrees
      // with the position's own expected type is `checkPromptAscription`'s separate question.
      case pv: PromptValue => pv.typeEx
      // What indexing yields. Needed HERE rather than only in `valueType` because an element type
      // is often written directly (`to Integer`) and so has no named Type to return.
      case lv: LookupValue =>
        valueTypeExpr(lv.collection, parents, lets, elements).flatMap(lookupResultType).map(_._1)
      case _ => valueType(v, parents, lets, elements).map(_.typEx)

  /** A54/A55: the named [[Type]] a [[ValueRef]] resolves to, if determinable. A bare on-clause
    * binding denotes the whole message, so it yields the message's Type directly; a field yields
    * the Type its (aliased) declaration names; a `let` yields its declared or inferred type.
    */
  private def valueRefType(
    vr: ValueRef,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Option[Type] =
    val names = vr.path.value
    if names.isEmpty then None
    else
      val idx = letIndexOf(names.head, lets)
      if idx >= 0 && names.sizeIs == 1 then letType(lets(idx), lets.take(idx), parents, elements)
      else
        valueRefDefinition(vr, parents) match
          case Some(t: Type) if idx < 0 => Some(t) // the whole message named by a binding
          case _ =>
            valueRefTypeExpr(vr, parents, lets, elements).flatMap {
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
    elements: Map[String, TypeExpression]
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
    elements: Map[String, TypeExpression]
  ): Unit =
    v match
      case _: LiteralString => ()
      case lv: LookupValue =>
        validateValue(lv.collection, parents, lets, elements)
        lv.indices.foreach(i => validateValue(i, parents, lets, elements))
        validateLookup(lv, parents, lets, elements)
      case ev: EmptyValue =>
        // The ascribed form is checkable with no context at all: whatever type it names must be one
        // that HAS an empty inhabitant. The bare form is checked where an expected type is wired --
        // see `checkValueType`.
        ev.typeEx.foreach { te =>
          if !admitsEmpty(te) then
            messages.addError(
              ev.loc,
              s"'empty' requires a type whose minimum cardinality is zero, but '${te.format}' " +
                "requires at least one value",
              suggestion = "Use 'empty' with an optional ('T?'), a collection ('T*') or a range " +
                "starting at zero ('T{0,n}'). A 'T+' or a bare 'T' always has at least one value."
            )
          end if
        }
      case _: PromptValue => () // literal AI prompt, nothing to resolve
      case c: Constructor => validateConstructor(c, parents, lets, elements)
      case call: Call     => validateCall(call, parents, lets, elements)
      case ask: Ask       => validateAsk(ask, parents)
      case init: Initiate => checkInitiate(init, parents, lets, elements)
      case vr: ValueRef =>
        if !valueRefResolves(vr, parents, lets, elements) then
          messages.addError(
            vr.loc,
            s"Value reference '${vr.path.format}' is not a 'let'-local, an 'on init'/'on term' " +
              "parameter, a 'foreach' element, a field of the handled message or entity state, " +
              "or a function input in scope",
            suggestion =
              "Bind it with a 'let', or reference an 'on init'/'on term' parameter, a 'foreach' " +
                "element, or a field of the on-clause message, entity state, or the function's " +
                "'requires' input."
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
        ic.argument.foreach(a => validateValue(a, parents, lets, elements))
      // NOT checked: whether the invariant declares `requires <type>` and whether a `with` was
      // supplied. Author's ruling 2026-08-04 — a CONDITION asks whether the rule holds and is
      // never rejected either way, unlike `require invariant X`, which APPLIES the rule and so
      // must be handed what the rule reads (`checkRequireArgument`).
      case _: BooleanLiteral        => ()
      case _: NumericLiteral        => ()
      case ce: ComparisonExpression =>
        // A28, widened 2026-08-14: operands are Comparands (refs or a bare NumericLiteral);
        // validate each resolves, then enforce type-safety.
        validateComparand(ce.left, parents, lets, elements)
        validateComparand(ce.right, parents, lets, elements)
        checkComparison(ce, parents, lets, elements)
      case le: LogicalExpression =>
        validateValue(le.left, parents, lets, elements)
        validateValue(le.right, parents, lets, elements)
        checkBooleanOperand(le.left, s"'${le.op.symbol}'", parents, lets, elements)
        checkBooleanOperand(le.right, s"'${le.op.symbol}'", parents, lets, elements)
      case ne: NotExpression =>
        validateValue(ne.expr, parents, lets, elements)
        checkBooleanOperand(ne.expr, "'not'", parents, lets, elements)

  /** A28: require a logical/`not` operand to be boolean. Emits an Error only when the operand's
    * category is clearly non-boolean; an undetermined category is skipped (best-effort).
    */
  private def checkBooleanOperand(
    v: Value,
    what: String,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    valueCategory(v, parents, lets, elements) match
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
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Option[String] =
    valueRefTypeExpr(vr, parents, lets, elements).flatMap(typeExprCategory)

  /** A17: a bare boolean value reference used as a `when` condition must resolve to a Boolean-typed
    * value — a boolean field of the handled message/entity-state/function-input, a boolean
    * `let`-local, or a boolean `constant`. Emits an Error only when the reference's category is
    * clearly non-boolean; an undetermined category (unresolved ref, or a type we cannot classify)
    * is skipped — best-effort, mirroring [[checkBooleanOperand]].
    */
  private def checkWhenValueRef(
    vr: ValueRef,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    whenValueRefCategory(vr, parents, lets, elements) match
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
    * directly-typed field as well as an aliased one. A [[NumericLiteral]] is always `"numeric"` —
    * it needs no resolution, unlike the three ref cases.
    */
  private def comparandCategory(
    c: Comparand,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Option[String] =
    c match
      case cr: ConstantRef =>
        resolution.refMap.definitionOf[Constant](cr.pathId).flatMap(k => typeExprCategory(k.typeEx))
      case gv: GetValue   => valueCategory(gv, parents, lets, elements)
      case _: LookupValue => None
      case vr: ValueRef =>
        valueCategory(vr, parents, lets, elements)
          .orElse(whenValueRefCategory(vr, parents, lets, elements))
      case _: NumericLiteral => Some("numeric")

  /** A28: validate a comparison operand ([[Comparand]]) resolves. A [[ConstantRef]]/[[GetValue]] is
    * checked via [[checkRef]]; a bare [[ValueRef]] must be a `let`-local, an in-scope field, or a
    * named [[Constant]]. A [[NumericLiteral]] always resolves (it names nothing), but draws a
    * StyleWarning — see the doc on [[Comparand]].
    */
  private def validateComparand(
    c: Comparand,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    c match
      case cr: ConstantRef => checkRef[Constant](cr, parents)
      case gv: GetValue    => validateValue(gv, parents, lets, elements)
      case lv: LookupValue => validateValue(lv, parents, lets, elements)
      case vr: ValueRef =>
        if !valueRefResolves(vr, parents, lets, elements) then
          messages.addError(
            vr.loc,
            s"Value reference '${vr.path.format}' is not a 'let'-local, an 'on init'/'on term' " +
              "parameter, a 'foreach' element, a field of the handled message or entity state, " +
              "a function input, or a constant in scope",
            suggestion =
              "Bind it with a 'let'; reference an 'on init'/'on term' parameter, a 'foreach' " +
                "element, or a field of the on-clause message, entity state, or the function's " +
                "'requires' input; or declare and reference a 'constant'."
          )
      case nl: NumericLiteral =>
        // A28's original rule made this unconstructible; Reid reversed that 2026-08-14 and the
        // intent survives as advice. The population starts at ZERO -- `count > 5` is a parse
        // error before this change, so no existing model can contain one.
        messages.addStyle(
          nl.loc,
          s"Comparison against the literal ${nl.text} would read better as a named constant",
          suggestion = s"Declare `constant SomeName is <type> = ${nl.text}` and compare against it."
        )

  /** A28: enforce type-safe comparisons. Equality (`==`/`!=`) requires both operands to share a
    * category (identity comparison); ordering (`<`/`>`/`<=`/`>=`) requires an ORDERED type —
    * conservatively, numeric — on both operands. Undetermined categories are skipped (best-effort;
    * an unresolved ref is reported by [[validateComparand]]).
    */
  private def checkComparison(
    ce: ComparisonExpression,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    val lc = comparandCategory(ce.left, parents, lets, elements)
    val rc = comparandCategory(ce.right, parents, lets, elements)
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
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Option[Type] =
    subject match
      case vr: ValueRef     => valueRefType(vr, parents, lets, elements)
      case gv: GetValue     => valueType(gv, parents, lets, elements)
      case _: LiteralString => None

  /** A29: the broad category (`"boolean"`/`"numeric"`/`"string"`) of a [[MatchSubject]] used as the
    * implicit left operand of a [[ComparisonPattern]]. A bare [[ValueRef]] uses the broad
    * [[whenValueRefCategory]] (which classifies directly-typed fields, not just aliased ones).
    */
  private def matchSubjectCategory(
    subject: MatchSubject,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Option[String] =
    subject match
      case vr: ValueRef     => whenValueRefCategory(vr, parents, lets, elements)
      case gv: GetValue     => valueCategory(gv, parents, lets, elements)
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
  private def validateMatch(
    ms: MatchStatement,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    // Subject must resolve (a ValueRef reports out-of-scope; a GetValue source is checked).
    validateValue(ms.expression, parents, lets, elements)
    val subjType = matchSubjectType(ms.expression, parents, lets, elements)
    val subjCat = matchSubjectCategory(ms.expression, parents, lets, elements)
    val memberDefs: Option[Seq[Definition]] = subjType.flatMap(closedMemberDefs)
    ms.cases.foreach { mc =>
      mc.pattern match
        case tp: TypePattern => validateTypePattern(tp, subjType, memberDefs)
        case cp: ComparisonPattern =>
          validateComparand(cp.comparand, parents, lets, elements)
          checkPatternComparison(cp, subjCat, parents, lets, elements)
        case _: LiteralPattern => () // legacy pseudo-code, untyped
      // A29: a guard is a structured BooleanExpression (validated as a value) or a bare
      // boolean-typed ValueRef (checked Boolean-typed, mirroring A17's `when`).
      mc.guard.foreach {
        case be: BooleanExpression => validateValue(be, parents, lets, elements)
        case vr: ValueRef          => checkWhenValueRef(vr, parents, lets, elements)
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
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    val rc = comparandCategory(cp.comparand, parents, lets, elements)
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
    elements: Map[String, TypeExpression],
    loc: At,
    what: String
  ): Unit =
    // A NumericLiteral never resolves to a named Type (valueType returns None for it — see its
    // NumericLiteral arm), so the general (Some, Some) identity check below never fires for one.
    // This is the OTHER site — besides Constant validation — where an expected type is already in
    // hand for a literal, so it is where the literal-only range/fraction check belongs.
    (v, expected) match
      case (nl: NumericLiteral, Some(e)) => checkNumericLiteralConformance(nl, e.typEx)
      // A BARE `empty` takes its type from the position, so this is the only place its
      // minimum-cardinality rule can be enforced. The ASCRIBED form is checked context-free in
      // `validateValue`; checking it again here would double-report, so it is skipped.
      case (ev: EmptyValue, Some(e)) if ev.typeEx.isEmpty =>
        if !admitsEmpty(e.typEx) then
          messages.addError(
            ev.loc,
            s"'empty' is not a value of ${e.identify}: '${e.typEx.format}' requires at least one " +
              "value",
            suggestion = "Only an optional ('T?'), a collection ('T*') or a range starting at zero " +
              s"('T{0,n}') has an empty value. Give ${e.identify} such a type, or supply a real " +
              "value here."
          )
        end if
      // A20: `let`/`set` are the two carriers `checkValueType` serves, and both wire the
      // restate/contradict check for free by living here rather than being duplicated at each
      // call site. `expected` is already the RESOLVED Type the position declares, so it is
      // re-wrapped as a self-named [[AliasedTypeExpression]] purely so
      // [[checkPromptAscription]]'s syntactic name comparison has a [[TypeExpression]] on both
      // sides -- see that method's doc for why the comparison is syntactic, not identity-based.
      case (pv: PromptValue, _) => checkPromptAscription(pv, expected.map(selfNamedTypeExpression))
      case _                    => ()
    (expected, valueType(v, parents, lets, elements)) match
      case (Some(e), Some(a)) if !(e eq a) =>
        messages.addError(
          loc,
          s"$what value has type ${a.identify} but ${e.identify} is expected",
          suggestion = s"Supply a value of type ${e.identify}."
        )
      case _ => ()
  end checkValueType

  /** A20: the [[TypeExpression]] standing for a resolved [[Type]]'s own identity, for comparing
    * against a typed hole's ascription. `checkValueType`'s `expected` is already resolved to the
    * named Type a `let`/`set` position declares (there is exactly one Type in play, found via its
    * own [[TypeRef]]), so re-wrapping its OWN name as a single-segment path lets
    * [[checkPromptAscription]] treat "the type this position resolved to" the same way it treats
    * "the type this position was WRITTEN as". A `constant`'s declared type is NOT run through this:
    * `c.typeEx` may be an unresolvable [[PredefinedType]] like `Real`, so `validateConstant` passes
    * it directly.
    */
  private def selfNamedTypeExpression(t: Type): TypeExpression =
    AliasedTypeExpression(At.empty, "type", PathIdentifier(At.empty, Seq(t.id.value)))

  /** A20: the name a [[TypeExpression]] was WRITTEN as, for [[checkPromptAscription]]'s syntactic
    * comparison. An [[AliasedTypeExpression]] names a declared [[Type]] by its path; every other
    * [[TypeExpression]] (a [[PredefinedType]] like `Real`, or a parameterized one like
    * `Decimal(10,2)`) has no path and is named by its `kind`.
    *
    * **Recurses through the four `Cardinality` wrappers** (`Optional`/`ZeroOrMore`/`OneOrMore`/
    * `SpecificRange`), discarding the wrapper rather than folding its symbol into the name —
    * `TypeParser.cardinality` wraps ANY type alternative, including an aliased one, so `prompt(…)
    * as OrderId?` parses today. Before this recursion existed, `Optional`/etc. matched the `_`
    * catch-all and were named by `te.kind` (`"Optional"`), which broke in BOTH directions: `let x:
    * OrderId = prompt("d") as OrderId?` compared `"Optional"` to `"OrderId"` and reported a false
    * contradiction on legal code (the position expects `OrderId`; ascribing its `?`-wrapped form
    * still restates the same named type — cardinality itself is not part of this comparison, same
    * as `checkOnOtherBinding`'s), while `constant G: OrderId? = prompt("g") as SomethingElse?`
    * compared `"Optional"` to `"Optional"` on BOTH sides and missed a real contradiction between
    * two DIFFERENT aliased types sharing the same wrapper. Found by code review 2026-08-15.
    *
    * **Only the LAST path segment is used**, not [[PathIdentifier.format]]'s full dotted form.
    * `selfNamedTypeExpression`'s `expected` side is always a bare, single-segment name (the
    * resolved Type's own `id.value` — it has no knowledge of how the position's `TypeRef` was
    * qualified), so comparing it against an ascription's FULL path made every qualified restatement
    * a false contradiction: `let x: Common.OrderId = prompt("d") as Common.OrderId` compared
    * `"OrderId"` to `"Common.OrderId"`. Using the last segment on both sides makes the two
    * consistent. This does trade away one thing: two DIFFERENT types that happen to share a simple
    * name in different scopes (`Common.OrderId` vs `Other.OrderId`) now compare equal here, exactly
    * as `checkOnOtherBinding`'s own syntactic, non-resolving comparison already accepts for
    * envelope names — a known, documented limitation of staying syntactic rather than resolving
    * through the symbol table, not a new one this fix introduces.
    */
  private def typeAscriptionName(te: TypeExpression): String = te match
    case ate: AliasedTypeExpression    => ate.pathId.value.lastOption.getOrElse("")
    case Optional(_, typex)            => typeAscriptionName(typex)
    case ZeroOrMore(_, typex)          => typeAscriptionName(typex)
    case OneOrMore(_, typex)           => typeAscriptionName(typex)
    case SpecificRange(_, typex, _, _) => typeAscriptionName(typex)
    case _                             => te.kind

  /** A20: a typed hole's ascription (`prompt("…") as T`) RESTATES the type its position already
    * supplies — it never overrides it, mirroring A57's `on other as x: <envelope>` rule exactly
    * (see [[checkOnOtherBinding]]). Silent when the position carries no expected type: nothing to
    * restate against, and nothing to warn about here either — an untyped position that ALSO has no
    * ascription is the seam-CompletenessWarning's job (wired only at the one conservative site the
    * ruling names: an unascribed `let`), not this method's. Silent when the ascription agrees. An
    * Error when it names a DIFFERENT type: a contradiction, not an omission.
    *
    * The comparison is purely SYNTACTIC — by [[typeAscriptionName]], not by resolving through alias
    * chains — exactly as [[checkOnOtherBinding]] compares `t.pathId.format` against the option's
    * stored name rather than resolving either side. `type Score is Real` is a DIFFERENT declared
    * type than bare `Real`, even though one is defined in terms of the other, so
    * `constant G: Real = prompt(…) as Score` is a contradiction despite `Score` resolving to `Real`
    * underneath — the ruling's table pins exactly this case as an Error. (`Currency` is NOT a
    * usable example here: it is a predefined type requiring a `country` argument — bare `as
    * Currency` does not parse, only `as Currency(USD)` does — and it does not resolve to `Real` or
    * to anything else; it is its own distinct [[PredefinedType]].)
    */
  private def checkPromptAscription(pv: PromptValue, expected: Option[TypeExpression]): Unit =
    (pv.typeEx, expected) match
      case (Some(ascribed), Some(exp)) if typeAscriptionName(ascribed) != typeAscriptionName(exp) =>
        messages.addError(
          ascribed.loc,
          s"'prompt(...) as ${typeAscriptionName(ascribed)}' contradicts the expected type " +
            s"${typeAscriptionName(exp)}: the ascription restates the position's type, it does " +
            "not override it",
          suggestion = s"Change the ascription to 'as ${typeAscriptionName(exp)}', or drop it " +
            "-- it is optional and inferred from the position."
        )
      case _ => () // agreement, no expectation to restate against, or no ascription written
  end checkPromptAscription

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
    elements: Map[String, TypeExpression]
  ): Unit =
    args.zipWithIndex.foreach { case (arg, idx) =>
      val fieldOpt: Option[Field] = arg.name match
        case Some(id) => fields.find(_.id.value == id.value)
        case None     => if idx < fields.size then Some(fields(idx)) else None
      fieldOpt.foreach { field =>
        // A20: the ONE wiring point for four of the seven ascription positions -- a constructor
        // argument, a call argument, and (through `checkLifecycleInvocation`, which adapts
        // `MethodArgument`s to `Field`s precisely so it can reuse this helper) an `initiate` and a
        // `terminate` argument. Wiring each separately would have been four more copies of the
        // drift this helper's scaladoc exists to prevent. `field.typeEx` is passed DIRECTLY, not
        // through `selfNamedTypeExpression`: it is already the type as WRITTEN, which is the side
        // the syntactic comparison needs -- the same reason `validateConstant` passes `c.typeEx`.
        arg.value match
          case pv: PromptValue => checkPromptAscription(pv, Some(field.typeEx))
          case _               => ()
        field.typeEx match
          case ate: AliasedTypeExpression =>
            val expected = resolution.refMap.definitionOf[Type](ate.pathId)
            val actual = valueType(arg.value, parents, lets, elements)
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
    elements: Map[String, TypeExpression]
  ): Unit =
    resolution.refMap.definitionOf[Type](c.ref.pathId) match
      case Some(typ) =>
        // FOLLOW THE ALIAS CHAIN. `command Ship is Shipment` is riddl-models' house style, and
        // reading only the DIRECT fields of `Ship` finds none -- so every named argument was
        // rejected as "not a field", and the arity check compared against zero. It made the
        // constructor form unusable for exactly the declaration shape the corpus uses most, which
        // is why `TellAddressingTest` had to write a bare `tell command Ship` and say so in a
        // comment. `aggregateFieldsOf` is the shared walk (cycle-guarded by reference identity,
        // because `Definition.equals` is structural and a `Set` would fuse two distinct identical
        // alias declarations and truncate a legitimate chain).
        val fields: Seq[Field] = aggregateFieldsOf(typ.typEx)
        // The call-site half of the duplicate-field defect riddl-examples reported (2026-08-18):
        // `Cmd(alpha = "a", alpha = "b")` was as silent as a doubly-declared field, and their
        // migration produced BOTH at once -- renaming a field to a name already present duplicated
        // the declaration AND every constructor argument that supplied it. Same reasoning for
        // Error: two values for one field means a consumer picks one silently.
        c.args
          .filter(_.name.isDefined)
          .groupBy(_.name.get.value)
          .collect { case (name, as) if as.sizeIs > 1 => name -> as.sortBy(_.loc.offset) }
          .toSeq
          .sortBy { case (_, as) => as.head.loc.offset }
          .foreach { case (name, as) =>
            val first = as.head
            as.tail.foreach { dupe =>
              messages.addError(
                dupe.loc,
                s"Argument '$name' is supplied more than once in constructor of " +
                  s"${typ.identify}; the first is at ${first.loc.format}",
                suggestion = s"Supply '$name' once. Two values for one field leave it ambiguous " +
                  "which the constructed message carries."
              )
            }
          }
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
        // A bare `empty` argument must be checked against the FIELD's cardinality (riddl-models,
        // 2026-08-24). This is the position models actually write `empty` in, and it was the one
        // place the rc.23 check could not see: `checkValueType` takes an expected *named* Type, and
        // a field typed `TimeStamp` or `OrderLine+` names none. But the field itself is right here
        // -- `fields` is already resolved for the arity and name checks above -- so the cardinality
        // is one lookup away, and an earlier claim that constructor arguments carry no expected
        // type was too pessimistic: what they lack is a *named Type*, not the type.
        //
        // The ASCRIBED form is skipped: `validateConstructor` runs alongside `validateValue`, which
        // checks an ascription context-free, and reporting both would double up on one mistake.
        def fieldForArg(arg: ConstructorArg, idx: Int): Option[Field] = arg.name match
          case Some(id) => fields.find(_.id.value == id.value)
          case None     => fields.lift(idx) // positional; arity is reported separately
        c.args.zipWithIndex.foreach { case (arg, idx) =>
          arg.value match
            case ev: EmptyValue if ev.typeEx.isEmpty =>
              fieldForArg(arg, idx).foreach { f =>
                if !admitsEmpty(f.typeEx) then
                  messages.addError(
                    ev.loc,
                    s"'empty' is not a value of field '${f.id.value}' in ${typ.identify}: " +
                      s"'${f.typeEx.format}' requires at least one value",
                    suggestion = "Only an optional ('T?'), a collection ('T*') or a range starting " +
                      s"at zero ('T{0,n}') has an empty value. Supply a real value for " +
                      s"'${f.id.value}', or give it a type that can be empty."
                  )
                end if
              }
            case _ => ()
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
        // EVERY field must be supplied explicitly (Reid, 2026-08-24). *"If the constructor does
        // not explicitly set every field, it is invalid. We don't want to guess what the default
        // should be nor do we want to let old state values creep through."*
        //
        // Ruled for ALL constructors, not just state records, so there is no exception to remember
        // -- asked explicitly, and the long-windedness was accepted. The reason bites hardest on a
        // state record, where an omitted field means either an invented default or a silent
        // carry-forward, but one rule with no exceptions is more defensible than a split.
        //
        // **This is only sayable because `empty` exists.** Before rc.23 an optional field had no
        // spelling for "absent", so omission was not a shortcut -- it was the ONLY way to say it.
        // Naming the missing fields follows the same principle as the union-inlet diagnostic: a
        // count would send the author to re-derive by hand what the checker already knows.
        // EVERY field must be supplied explicitly (Reid, 2026-08-24). *"If the constructor does
        // not explicitly set every field, it is invalid. We don't want to guess what the default
        // should be nor do we want to let old state values creep through."*
        //
        // Ruled for ALL constructors, not just state records, so there is no exception to remember
        // -- asked explicitly, and the long-windedness accepted. The reason bites hardest on a
        // state record, where an omitted field means either an invented default or a silent
        // carry-forward, but one rule with no exceptions is more defensible than a split.
        //
        // **This is only sayable because `empty` exists.** Before rc.23 an optional field had no
        // spelling for "absent", so omission was not a shortcut -- it was the ONLY way to say it.
        //
        // This REPLACES the former positional-arity branch, whose comment recorded the superseded
        // rule ("Named arguments are exempt because they may legitimately supply a subset"). Naming
        // the missing fields is strictly more useful than an arity count, and follows the
        // union-inlet precedent: a count sends the author to re-derive what the checker knows.
        // `scala.collection.immutable.Set`, qualified: `AST.Set` shadows it, which CLAUDE.md
        // records as having caused three compile errors in one day.
        val suppliedNames: scala.collection.immutable.Set[String] =
          c.args.flatMap(_.name.map(_.value)).toSet
        val positionalCount = c.args.count(_.name.isEmpty)
        val missing: Seq[Field] = fields.zipWithIndex.collect {
          case (f, idx) if idx >= positionalCount && !suppliedNames.contains(f.id.value) => f
        }
        if missing.nonEmpty then
          messages.addError(
            c.loc,
            s"Constructor of ${typ.identify} does not supply " +
              s"${count(missing.size, "field")}: ${missing.map(_.id.value).mkString(", ")}",
            suggestion = "Supply every field explicitly. An omitted field would have to take an " +
              "invented default or carry an old value forward, and neither is stated by the " +
              "model. An optional or collection field that should have no value is written " +
              "'<field> = empty'."
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
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
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
        checkArgumentTypes(call.args, fields, "input", parents, lets, elements)
        // Recurse into argument values (nested constructors, calls, value refs).
        call.args.foreach(arg => validateValue(arg.value, parents, lets, elements))
      case None => () // unresolved function ref reported by ResolutionPass
  end validateCall

  /** A45: validate a `put` — the value, the output target's existence, and best-effort type
    * compatibility of the value against the resolved [[Output.putOut]].
    */
  private def validatePut(
    ps: PutStatement,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    validateValue(ps.value, parents, lets, elements)
    checkRef[Output](ps.output, parents).foreach { output =>
      val expected: Option[Type] = output.putOut match
        case tr: TypeRef      => resolution.refMap.definitionOf[Type](tr.pathId)
        case _: ConstantRef   => None
        case _: LiteralString => None
      // A20: `expected` here is a RESOLVED Type, so it is re-wrapped as a self-named alias to give
      // the syntactic comparison a name on both sides -- the same adaptation `checkValueType` makes.
      ps.value match
        case pv: PromptValue => checkPromptAscription(pv, expected.map(selfNamedTypeExpression))
        case _               => ()
      val actual = valueType(ps.value, parents, lets, elements)
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
  private def validateReturn(
    rs: ReturnStatement,
    parents: Parents,
    lets: Seq[LetStatement],
    elements: Map[String, TypeExpression]
  ): Unit =
    validateValue(rs.value, parents, lets, elements)
    parents.collectFirst { case f: Function => f }.foreach { fn =>
      val expected: Option[Type] = fn.output match
        case Some(tr: TypeRef) => resolution.refMap.definitionOf[Type](tr.pathId)
        case _                 => None
      // A20: as in `validatePut` -- a resolved Type re-wrapped as a self-named alias.
      rs.value match
        case pv: PromptValue => checkPromptAscription(pv, expected.map(selfNamedTypeExpression))
        case _               => ()
      val actual = valueType(rs.value, parents, lets, elements)
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
  /** Task 3 / final review: the LEXICAL name scope an `on init(...)`/`on term(...)` parameter list
    * introduces for the clause's body.
    *
    * Without this the feature was declare-only: the parameters parsed, resolved and prettified, but
    * READING one from the body was an Error ("Value reference 'seed' is not a 'let'-local, a field
    * of the handled message or entity state, or a function input in scope"). A parameter resolved
    * only by COINCIDENCE, when its name happened to collide with a state field — which is exactly
    * what the original `language/input/lifecycle-parameters.riddl` did, and why the gap survived a
    * task-scoped review.
    *
    * Parameters are threaded HERE rather than taught to `ResolutionPass` for the same reason a
    * `let` is: they are lexical and statement-scoped, and a [[MethodArgument]] is not a
    * [[Definition]], so the symbol table cannot hold one. They ride the existing
    * name-to-[[TypeExpression]] scope map (`inScopeElements`), which is precisely the shape needed
    * — [[typeExprOfPath]] then walks `buyer.tier` through the parameter's type with no new
    * machinery. See [[checkStatementScopes]] for how an inner `let` shadows a parameter.
    */
  private def clauseParameterScope(oc: OnClause): Map[String, TypeExpression] =
    val parameters = oc match
      case oic: OnInitializationClause => oic.parameters
      case otc: OnTerminationClause    => otc.parameters
      // Enumerated rather than caught by `case _`: the other clause kinds genuinely have no
      // parameter list (a message clause's local name is A55's `binding`, taken from the handled
      // message), and enumerating is what makes a seventh clause kind a compile error here.
      case _: OnMessageLikeClause | _: OnActivationClause | _: OnPassivationClause |
          _: OnOtherClause =>
        Seq.empty[MethodArgument]
    parameters.map(a => a.name -> a.typeEx).toMap
  end clauseParameterScope

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
    // Whether a `morph` has already run in THIS statement list. `checkNoSetAfterMorph` reports the
    // `set` itself; this flag exists so `checkSetStateIsCurrent` STAYS QUIET there, because after a
    // morph its explanation would be false -- it would name the enclosing state as "the state this
    // entity is in", which the morph has just changed. One defect, one message, and the true one.
    var morphed = false
    // A parameter/element scope is a MAP threaded by value, but a `let` declared partway through
    // this list shadows a same-named clause parameter or enclosing `foreach` element from that
    // point on -- the local is the inner binding. `valueRefTypeExpr` consults `elements` BEFORE
    // `lets`, so the shadowing has to be expressed by DROPPING the name here rather than by
    // consultation order.
    var elements = inScopeElements
    // `forward` is NOT terminal (author's ruling): statements may follow it. But the response has
    // been delegated, so producing it here too is a contradiction, and anything else after it is
    // usually a sign the forward wanted to be last. Tracked per statement LIST -- a nested
    // when/match body recurses with its own tracker, which is right: a `forward` in one branch
    // says nothing about the other.
    var forwardedAt: Option[At] = None
    stmts.foreach { stmt =>
      forwardedAt.foreach { fLoc =>
        stmt match
          case y: YieldStatement =>
            messages.addError(
              y.loc,
              "'yield' after a 'forward' in the same clause: the response was delegated, so this " +
                "clause cannot also produce it",
              suggestion = s"Remove the 'yield', or remove the 'forward' at ${fLoc.format}. " +
                "Exactly one of them answers for this message."
            )
          case r: ReplyStatement =>
            messages.addError(
              r.loc,
              "'reply' after a 'forward' in the same clause: the response was delegated, so this " +
                "clause cannot also produce it",
              suggestion = s"Remove the 'reply', or remove the 'forward' at ${fLoc.format}. " +
                "Exactly one of them answers for this message."
            )
          case _: SendStatement | _: TellStatement =>
            messages.addStyle(
              stmt.loc,
              "a 'forward' should generally be the last statement in its clause; transmitting " +
                "again after delegating is legal but usually unintended",
              suggestion = s"Move the 'forward' at ${fLoc.format} after this statement, unless " +
                "transmitting after delegating is deliberate."
            )
          case _ => ()
      }
      stmt match
        case f: ForwardStatement => if forwardedAt.isEmpty then forwardedAt = Some(f.loc)
        case _                   => ()
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
          validateValue(ls.expression, parents, lets, elements)
          checkLocalName(ls.identifier, "'let' local", parents) // A55
          checkUnusedInitiateId(ls, stmts) // Task 5
          ls.typeRef.foreach { tr =>
            val expected = resolution.refMap.definitionOf[Type](tr.pathId)
            expected match
              case Some(_) =>
                checkValueType(
                  expected,
                  ls.expression,
                  parents,
                  lets,
                  elements,
                  ls.loc,
                  s"'let ${ls.identifier.value}'"
                )
              case None =>
                // Defect 2 (2026-08-15): a predefined type keyword (`Natural`, `Integer`, …) used as
                // the ascription resolves to no `Type` Definition -- predefined types are deliberately
                // never entered into the symbol table (see `PredefTypes.typeExpressionFor`'s doc) --
                // so `checkValueType`'s Type-identity comparison has nothing to compare against and
                // would otherwise silently skip the ascription entirely. Run the two checks that
                // operate on a bare [[TypeExpression]] instead of a resolved [[Type]] -- the same
                // ones `checkValueType`'s first match arm runs when a Type WAS found.
                if tr.pathId.value.sizeIs == 1 then
                  PredefTypes.typeExpressionFor(tr.pathId.value.head, tr.loc).foreach {
                    expectedTe =>
                      ls.expression match
                        case nl: NumericLiteral => checkNumericLiteralConformance(nl, expectedTe)
                        case pv: PromptValue    => checkPromptAscription(pv, Some(expectedTe))
                        case _                  => ()
                  }
          }
          // A20: the ONE seam-CompletenessWarning site, per the ruling's conservative table. An
          // unascribed `let x = prompt(…)` has NOTHING that supplies a type -- not a `let x: T`
          // annotation, not a `prompt(…) as T` ascription -- so the hole is genuinely untyped. Every
          // other position (a `let` WITH either half present, `set`, a constructor argument, `when`,
          // …) stays silent: "we have not wired this position" is not the same fact as "the
          // language cannot type it", and this is the single position the corpus measurement (0 of
          // 288 uses) showed was actually unascribed in the wild.
          if ls.typeRef.isEmpty then
            ls.expression match
              case pv: PromptValue if pv.typeEx.isEmpty =>
                messages.addCompleteness(
                  pv.loc,
                  s"'let ${ls.identifier.value}' binds an untyped 'prompt(…)' with no type anywhere " +
                    "to check it against",
                  suggestion = "Add a type: 'let " + ls.identifier.value +
                    ": T = prompt(…)', or ascribe the hole itself: 'prompt(…) as T'."
                )
              case _ => ()
          lets = lets :+ ls
          elements = elements - ls.identifier.value
        case ss: SetStatement =>
          if !morphed then checkSetStateIsCurrent(ss, parents)
          // A54: validate the value expression, then check it against the target field/state type.
          validateValue(ss.value, parents, lets, elements)
          val expected: Option[Type] = ss.field match
            case fr: FieldRef =>
              resolution.refMap.definitionOf[Field](fr.pathId).flatMap { f =>
                f.typeEx match
                  case ate: AliasedTypeExpression =>
                    resolution.refMap.definitionOf[Type](ate.pathId)
                  case _ => None
              }
            case sr: StateRef =>
              resolution.refMap
                .definitionOf[State](sr.pathId)
                .flatMap(st => resolution.refMap.definitionOf[Type](st.typ.pathId))
          checkValueType(
            expected,
            ss.value,
            parents,
            lets,
            elements,
            ss.loc,
            s"'set ${ss.field.format}'"
          )
        case s: SendStatement =>
          s.msg match
            case c: Constructor => validateValue(c, parents, lets, elements)
            // A56/message-value-source: a bare operand needs the threaded `let`/element scope
            // `checkMessageOperandSource` resolves it against — unavailable in `validateStatement`.
            case vr: ValueRef   => checkMessageOperandSource(vr, "send", parents, lets, elements)
            case mr: MessageRef => checkBareMessageOperand(mr, "send") // Task 4
          recordDeliverableType(s, s.msg, parents, lets, elements)
          checkTransmittedPortletType(s.loc, s.msg, s.portlet, parents, lets, elements)
        case s: ForwardStatement =>
          s.msg match
            case c: Constructor => validateValue(c, parents, lets, elements)
            case vr: ValueRef   => checkMessageOperandSource(vr, "forward", parents, lets, elements)
            case mr: MessageRef => checkBareMessageOperand(mr, "forward")
          recordDeliverableType(s, s.msg, parents, lets, elements)
          checkForward(s, parents, lets, elements)
          // A `forward` is still a transmission, so rc.18's portlet-admits rule applies unchanged
          // -- but only to the portlet shape; a processor target has no single declared type.
          s.target match
            case portlet: PortletRef[?] =>
              checkTransmittedPortletType(s.loc, s.msg, portlet, parents, lets, elements)
            case _: ProcessorRef[?] => ()
        case s: TellStatement =>
          s.msg match
            case c: Constructor => validateValue(c, parents, lets, elements)
            case vr: ValueRef   => checkMessageOperandSource(vr, "tell", parents, lets, elements)
            case mr: MessageRef => checkBareMessageOperand(mr, "tell") // Task 4
          recordDeliverableType(s, s.msg, parents, lets, elements)
          // A70/instance-identity task 6: reached at ANY depth (this function is the single entry
          // point invoked at every container root AND recursively for when/match/foreach bodies) --
          // mirrors checkTerminate's reachability, immediately below.
          checkTellTargetValue(s, parents, lets, elements)
          checkTellAddressing(s, parents, lets, elements)
          checkTellIsolation(s, parents, lets, elements)
        // Task 2: the `case _ => ()` these three carried is now ENUMERATED. It was correct while the
        // operand could only be a MessageRef; the moment a ValueRef became legal it would have
        // silently accepted `yield garbage` -- the exact shape of fall-through this repo forbids.
        case s: YieldStatement =>
          s.msg match
            case c: Constructor => validateValue(c, parents, lets, elements)
            case vr: ValueRef   => checkMessageOperandSource(vr, "yield", parents, lets, elements)
            case mr: MessageRef => checkBareMessageOperand(mr, "yield") // Task 4
          recordDeliverableType(s, s.msg, parents, lets, elements) // [1.2]
        // Mirrors YieldStatement, immediately above: `validateStatement`'s ReplyStatement case
        // claims "a Constructor is validated in checkStatementScopes", which was untrue until this
        // arm existed -- a `reply result Foo(x = self.id)` Constructor argument reached NOTHING,
        // found auditing `self`'s coverage (a self reference there was silently unchecked).
        case s: ReplyStatement =>
          recordDeliverableType(s, s.msg, parents, lets, elements) // [1.2]
          s.msg match
            case c: Constructor => validateValue(c, parents, lets, elements)
            case vr: ValueRef   => checkMessageOperandSource(vr, "reply", parents, lets, elements)
            case mr: MessageRef => checkBareMessageOperand(mr, "reply") // Task 4
        case s: MorphStatement =>
          s.value match
            case c: Constructor => validateValue(c, parents, lets, elements)
            // NOT checkMessageOperandSource: a morph carries the RECORD that types the target state
            // (A9b), so demanding a command/event/query/result here would reject every correct use.
            case vr: ValueRef => checkMorphOperandSource(vr, s, parents, lets, elements)
            // Task 4: the record side of the same warning -- `morph … with record R` names R's TYPE.
            case rr: RecordRef => checkBareMessageOperand(rr, "morph")
        case fs: ForeachStatement =>
          validateForeachCollection(fs, lets, elements, parents)
          // Bind the loop's name(s) to their TYPES for the body's scope -- not merely the names.
          // Without the types `line` resolves and `line.sku` does not, which is the whole point of
          // iterating. An unresolvable collection still binds the names (to `Anything`), because the
          // header's error is already reported and piling "unknown value reference" on top of it
          // would blame the body for a defect above it.
          val collType = foreachCollectionType(fs, lets, elements, parents)
          checkStatementScopes(
            fs.doStatements.toSeq.collect { case s: Statement => s },
            lets,
            parents,
            elements ++ foreachBindings(fs, collType)
          )
        case ws: WhenStatement =>
          // A28: type-check a structured BooleanExpression condition (with in-scope `let` locals);
          // the LiteralString/Identifier forms have no expression to check here. A17: a bare boolean
          // ValueRef condition must resolve to a Boolean-typed value.
          ws.condition match
            case be: BooleanExpression => validateValue(be, parents, lets, elements)
            case vr: ValueRef          => checkWhenValueRef(vr, parents, lets, elements) // A17
            // A20: a `when` condition's position IMPLIES Boolean -- a constant expected type, not
            // something threaded through call sites. This is the ruling's one MANDATORY wire.
            // `checkPromptAscription` never WARNS on an absent ascription (only the `let`-no-
            // ascription site does that, above), so an unascribed `when prompt(…)` -- 15 of them in
            // the corpus -- stays silent with or without this arm; what this arm buys is
            // CONTRADICTION detection: `when prompt(…) as Score` would otherwise fall to the
            // catch-all below and silently accept an ascription that can never be a legal condition.
            case pv: PromptValue => checkPromptAscription(pv, Some(Bool(At.empty)))
            case _               => ()
          checkStatementScopes(
            ws.thenStatements.toSeq.collect { case s: Statement => s },
            lets,
            parents,
            elements
          )
          checkStatementScopes(
            ws.elseStatements.toSeq.collect { case s: Statement => s },
            lets,
            parents,
            elements
          )
        case rs: RequireStatement =>
          // A28: type-check a structured BooleanExpression condition (with in-scope `let` locals);
          // the LiteralString/InvariantRef forms are checked in validateStatement.
          rs.condition match
            case be: BooleanExpression => validateValue(be, parents, lets, elements)
            case _                     => ()
        case ms: MatchStatement =>
          validateMatch(
            ms,
            parents,
            lets,
            elements
          ) // A29: subject/pattern/guard resolution + type-compat + exhaustiveness
          ms.cases.foreach { mc =>
            checkStatementScopes(
              mc.statements.toSeq.collect { case s: Statement => s },
              lets,
              parents,
              elements
            )
          }
          checkStatementScopes(
            ms.default.toSeq.collect { case s: Statement => s },
            lets,
            parents,
            elements
          )
        case ps: PutStatement    => validatePut(ps, parents, lets, elements)
        case rs: ReturnStatement => validateReturn(rs, parents, lets, elements)
        // A70/instance-identity: reached at ANY depth (this function is the single entry point
        // invoked at every container root AND recursively for when/match/foreach bodies), which is
        // exactly what the nested-`terminate` regression test requires -- mirrors `checkInitiate`'s
        // reachability via `validateValue`.
        case ts: TerminateStatement => checkTerminate(ts, parents, lets, elements)
        case _                      => ()
      }
      // Conservative and deliberately AFTER the arms: a morph anywhere in this statement --
      // including inside a `when`/`match` arm that may have run -- makes the enclosing state no
      // longer reliably current, so `checkSetStateIsCurrent` must stay quiet from here on.
      if containsMorph(stmt) then morphed = true
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
          case _: TellStatement | _: SendStatement | _: ForwardStatement | _: YieldStatement |
              _: ReplyStatement | _: MorphStatement | _: SetStatement | _: BecomeStatement |
              _: ErrorStatement | _: CodeStatement | _: PutStatement | _: TerminateStatement =>
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
