/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.PredefType
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
      streamlets.toSeq,
      computedHandlerCompleteness
    )
  }

  private var computedHandlerCompleteness: Seq[HandlerCompleteness] =
    Seq.empty

  override def postProcess(root: PassRoot): Unit = {
    checkOverloads()
    if mode == ValidationMode.Full then
      checkStreaming(root)
      checkTellReachability()
      computedHandlerCompleteness = classifyHandlers()
      checkCompletenessPostProcess()
    end if
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

  private def checkCompletenessPostProcess(): Unit = {
    // Completeness 4e: handlers that are empty or prompt-only
    computedHandlerCompleteness.foreach { hc =>
      val isExternal = hc.parent match {
        case c: Context => c.hasOption("external")
        case _          => false
      }
      if !isExternal then {
        hc.category match {
          case BehaviorCategory.Empty =>
            messages.addCompleteness(
              hc.handler.errorLoc,
              s"${hc.handler.identify} in ${hc.parent.identify} has no executable statements",
              suggestion =
                "Add executable statements (tell, send, set, morph, become, reply) to the handler's on-clauses."
            )
          case BehaviorCategory.PromptOnly =>
            messages.addCompleteness(
              hc.handler.errorLoc,
              s"${hc.handler.identify} in ${hc.parent.identify} contains only prompt statements; " +
                "executable statements (tell, send, morph, set, etc.) are needed",
              suggestion =
                "Add executable statements (tell, send, set, morph) alongside the 'prompt' statements so the handler does real work."
            )
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
      // #17: Event type not produced by any command handler
      if events.nonEmpty && context.entities.exists(_.nonEmpty) then {
        val allHandlers = context.entities.flatMap { e =>
          e.handlers ++ e.states.flatMap(_.handlers)
        }
        val producedEventNames = mutable.Set.empty[String]
        allHandlers.foreach { handler =>
          handler.clauses.foreach { clause =>
            val finder = Finder(clause.contents)
            val sends = finder.recursiveFindByType[SendStatement]
            val tells = finder.recursiveFindByType[TellStatement]
            sends
              .filter(s => operandMessageKind(s.msg) == AggregateUseCase.EventCase)
              .foreach(s =>
                producedEventNames += operandPathId(s.msg).value.lastOption.getOrElse("")
              )
            tells
              .filter(t => operandMessageKind(t.msg) == AggregateUseCase.EventCase)
              .foreach(t =>
                producedEventNames += operandPathId(t.msg).value.lastOption.getOrElse("")
              )
          }
        }
        events.foreach { evt =>
          if !producedEventNames.contains(evt.id.value) then {
            messages.addCompleteness(
              evt.errorLoc,
              s"${evt.identify} is defined but no handler produces it",
              suggestion =
                s"Send or tell ${evt.identify} from a command handler so the event is produced, or remove the unused event."
            )
          }
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
    // #23: Invariants not referenced by any require statement
    if collectedInvariants.nonEmpty then {
      // Collect all invariant refs from require statements across all handlers
      val referencedInvariantNames = mutable.Set.empty[String]
      handlerParents.foreach { case (handler, _) =>
        handler.clauses.foreach { clause =>
          walkStatements(clause.contents) {
            case RequireStatement(_, ir: InvariantRef) =>
              referencedInvariantNames += ir.pathId.value.lastOption.getOrElse("")
            case _ => ()
          }
        }
      }
      collectedInvariants.foreach { case (inv, _) =>
        if inv.nonEmpty && !referencedInvariantNames.contains(inv.id.value) then {
          messages.addUsage(
            inv.errorLoc,
            s"${inv.identify} is defined but not referenced by any 'require invariant' statement",
            suggestion =
              s"Reference ${inv.identify} from a handler with 'require invariant ${inv.id.value}', or remove it if unused."
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
    value match {
      case p: Processor[?] => validateProcessorShape(p)
      case _               => ()
    }
    // A25/A54: validate `foreach` collection scoping and value expressions once per statement-bearing
    // container (on-clause or function). checkStatementScopes recurses through nested statement
    // bodies threading `let` scope, so invoking it at the container root covers every statement at
    // any depth exactly once.
    value match {
      case oc: OnClause =>
        checkStatementScopes(oc.statements, Seq.empty[LetStatement], oc +: parentsAsSeq)
      case fn: Function =>
        checkStatementScopes(fn.statements, Seq.empty[LetStatement], fn +: parentsAsSeq)
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
      case oac: OnActivationClause =>
        checkDefinition(parentsAsSeq, oac)
      case opc: OnPassivationClause =>
        checkDefinition(parentsAsSeq, opc)
      case ooc: OnOtherClause =>
        checkDefinition(parentsAsSeq, ooc)
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
  end validateOnClause

  private def validateOnMessageClause(omc: OnMessageLikeClause, parents: Parents): Unit = {
    checkDefinition(parents, omc)
    validateOnClause(omc)
    val maybeEntity: Option[Entity] = parents.collectFirst { case e: Entity => e }
    val isExternalContext: Boolean = parents
      .collectFirst { case c: Context => c }
      .exists(_.hasOption("external"))
    if omc.msg.nonEmpty then {
      checkMessageRef(omc.msg, parents, Seq(omc.msg.messageKind))
      // Command→event and query→result checks apply only to entities
      if maybeEntity.isDefined && !isExternalContext then {
        val entity = maybeEntity.get
        omc.msg.messageKind match {
          case AggregateUseCase.CommandCase =>
            val finder = Finder(omc.contents)
            val sends: Seq[SendStatement] = finder.recursiveFindByType[SendStatement]
            val tells: Seq[TellStatement] = finder.recursiveFindByType[TellStatement]
            val foundSend = sends.nonEmpty &&
              sends.exists(s => operandMessageKind(s.msg) == AggregateUseCase.EventCase)
            val foundTell = tells.nonEmpty &&
              tells.exists(t => operandMessageKind(t.msg) == AggregateUseCase.EventCase)
            if !(foundSend || foundTell) then
              messages.addCompleteness(
                omc.errorLoc,
                s"Command processing in ${entity.identify} should result in sending an event",
                suggestion =
                  "Send or tell an event from this command handler, e.g. 'send event SomethingHappened to outlet ...'."
              )
          case AggregateUseCase.QueryCase =>
            val finder = Finder(omc.contents)
            val sends: Seq[SendStatement] = finder.recursiveFindByType[SendStatement]
            val tells: Seq[TellStatement] = finder.recursiveFindByType[TellStatement]
            val yields: Seq[YieldStatement] = finder.recursiveFindByType[YieldStatement]
            val foundSend = sends.nonEmpty &&
              sends.exists(s => operandMessageKind(s.msg) == AggregateUseCase.ResultCase)
            val foundTell = tells.nonEmpty &&
              tells.exists(t => operandMessageKind(t.msg) == AggregateUseCase.ResultCase)
            val foundYield = yields.nonEmpty &&
              yields.exists(y => operandMessageKind(y.msg) == AggregateUseCase.ResultCase)
            if !(foundSend || foundTell || foundYield) then
              messages.addCompleteness(
                omc.errorLoc,
                s"Query processing in ${entity.identify} should result in a reply or sending a result",
                suggestion =
                  "Yield a result or send a result type from this query handler, e.g. 'yield result QueryResult'."
              )
          case _ =>
        }
      }
      // A19↔A22 conformance applies to any context (not only entities) whose handled message is a
      // command/query with a `yields` contract.
      checkYieldConformance(omc)
    } else {}
    omc.from.foreach { (_: Option[Identifier], ref: Reference[Definition]) =>
      checkRef[Definition](ref, parents)
    }
  }

  /** A54: the [[AggregateUseCase]] of a widened message operand — a bare ref or a constructor whose
    * ref names the constructed message/record.
    */
  private def operandMessageKind(m: MessageRef | Constructor): AggregateUseCase = m match
    case mr: MessageRef => mr.messageKind
    case c: Constructor => c.ref.messageKind

  /** A54: the [[PathIdentifier]] of a widened message operand (the bare ref's, or the constructor
    * ref's).
    */
  private def operandPathId(m: MessageRef | Constructor): PathIdentifier = m match
    case mr: MessageRef => mr.pathId
    case c: Constructor => c.ref.pathId

  private def validateStatement(
    statement: Statement,
    parents: Parents
  ): Unit =
    val onClause: Branch[?] = parents.head
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
      case SetStatement(loc, field, value) =>
        field match
          case fr: FieldRef => checkRef[Field](fr, parents)
          case sr: StateRef => checkRef[State](sr, parents)
        checkNonEmptyValue(value, "value to set", onClause, loc, MissingWarning, required = true)
      case SendStatement(_, msg, portlet) =>
        // A54: a bare ref is checked here; a Constructor is validated in checkStatementScopes (needs
        // the threaded `let` scope for its args).
        msg match
          case ref: MessageRef => checkRef[Type](ref, parents)
          case _: Constructor  => ()
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
      case ts @ TellStatement(_, msg, processorRef) =>
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
      case WhenStatement(loc, condition, thenStatements, elseStatements, _) =>
        condition match {
          case ls: LiteralString =>
            checkNonEmptyValue(ls, "condition", onClause, loc, MissingWarning, required = true)
          case id: Identifier =>
            checkNonEmptyValue(id, "condition", onClause, loc, MissingWarning, required = true)
          case _: BooleanExpression => () // A28: type-checked in checkStatementScopes
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
        checkNonEmptyValue(expression, "expression", onClause, loc, MissingWarning, required = true)
        checkNonEmpty(cases, "cases", onClause, loc, MissingWarning, required = true)
        cases.foreach { mc =>
          checkNonEmptyValue(
            mc.pattern,
            "case pattern",
            onClause,
            mc.loc,
            MissingWarning,
            required = true
          )
        }
      case LetStatement(loc, identifier, _, expression) =>
        check(
          identifier.value.length >= 3,
          s"Identifier '${identifier.value}' is too short",
          MissingWarning,
          identifier.loc,
          suggestion = "Use an identifier of at least 3 characters in the 'let' statement."
        )
        checkNonEmptyValue(expression, "expression", onClause, loc, MissingWarning, required = true)
      case CodeStatement(loc, language, body) =>
        checkNonEmptyValue(language, "language", onClause, loc, MissingWarning, required = true)
        check(
          body.nonEmpty,
          "Code statement body cannot be empty",
          MissingWarning,
          loc,
          suggestion = "Provide a non-empty code body, or remove the empty code statement."
        )
      case RequireStatement(loc, condition) =>
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
            checkRef[Invariant](ir, parents)
          case _: BooleanExpression => () // A28: type-checked in checkStatementScopes
        }
      case YieldStatement(_, msg) =>
        // A54: a bare ref is checked here; a Constructor is validated in checkStatementScopes.
        msg match
          case ref: MessageRef => checkRef[Type](ref, parents)
          case _: Constructor  => ()
      case _: PutStatement | _: ReturnStatement =>
        // A45/A57: value/type/scope validation runs in checkStatementScopes (which threads in-scope
        // `let` locals and reaches nested statements). Nothing to check per-statement here.
        ()
      case ForeachStatement(loc, element, _, doStatements) =>
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
    *   - a command/query that declares `yields` but whose handler never yields it is an error.
    *
    * `yields` is optional (A19): yielding in a handler whose command/query declares no `yields` is
    * allowed and unchecked — conformance is enforced only when the author opts in with a `yields`
    * clause.
    *
    * Skips cleanly when refs don't resolve (those are reported by other checks) and when the
    * handled message is not a command/query (no `yields` contract applies).
    */
  private def checkYieldConformance(omc: OnMessageLikeClause): Unit = {
    if omc.msg.isEmpty then return
    resolution.refMap.definitionOf[Type](omc.msg.pathId).foreach { handledType =>
      handledType.typEx match {
        case auc: AggregateUseCaseTypeExpression
            if auc.usecase == AggregateUseCase.CommandCase ||
              auc.usecase == AggregateUseCase.QueryCase =>
          val yieldStmts = Finder(omc.contents).recursiveFindByType[YieldStatement]
          auc.yields match {
            case Some(declaredYield) =>
              val declaredType = resolution.refMap.definitionOf[Type](declaredYield.pathId)
              if yieldStmts.isEmpty then
                messages.addError(
                  omc.errorLoc,
                  s"${handledType.identify} declares 'yields ${declaredYield.format}' but " +
                    s"${omc.identify} never yields it",
                  suggestion = s"Add a 'yield ${declaredYield.format}' statement to this handler."
                )
              else
                yieldStmts.foreach { ys =>
                  val kindOk = operandMessageKind(ys.msg) == declaredYield.messageKind
                  val yieldedType = resolution.refMap.definitionOf[Type](operandPathId(ys.msg))
                  val typeOk = (declaredType, yieldedType) match {
                    case (Some(dt), Some(yt)) => dt eq yt
                    case _                    => true // unresolved — reported by other checks
                  }
                  if !(kindOk && typeOk) then
                    messages.addError(
                      ys.loc,
                      s"yielded '${ys.msg.format}' does not match declared 'yields " +
                        s"${declaredYield.format}' of ${handledType.identify}",
                      suggestion = s"Yield the declared response: 'yield ${declaredYield.format}'."
                    )
                }
            case None => () // `yields` is optional; yielding without a declared clause is allowed
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

  private def validateInvariant(
    i: Invariant,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, i)
    checkNonEmpty(i.condition.toList, "Condition", i, Messages.MissingWarning)
    // A28: type-check a structured BooleanExpression condition (invariants have no `let` scope).
    i.condition.foreach {
      case be: BooleanExpression => validateValue(be, parents, Seq.empty[LetStatement])
      case _: LiteralString      => ()
    }
    checkMetadata(i)
  }

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

  private def validateConnector(
    connector: Connector,
    parents: Parents
  ): Unit =
    if connector.nonEmpty then
      addConnector(connector)
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
              if !areSameType(Some(inletType), Some(outletType)) then
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

  // FIXME: This should be used:
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
    // When the entity has a single state, its entity-scope handlers follow the same rule.
    if entity.states.sizeIs <= 1 then
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
    // Completeness 4a: each state should have on-init with set statement
    if entity.states.nonEmpty && !entity.isEmpty then
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
    // Completeness: event-sourced entity must emit events on every command
    if entity.nonEmpty && entity.hasOption("event-sourced") then {
      val allHandlers = entity.handlers ++ entity.states.flatMap(_.handlers)
      val commandClauses = allHandlers.flatMap(_.clauses).collect {
        case omc: OnMessageClause if omc.msg.messageKind == AggregateUseCase.CommandCase => omc
      }
      commandClauses.foreach { omc =>
        val finder = Finder(omc.contents)
        val sends = finder.recursiveFindByType[SendStatement]
        val tells = finder.recursiveFindByType[TellStatement]
        val emitsEvent =
          (sends.nonEmpty && sends.exists(s =>
            operandMessageKind(s.msg) == AggregateUseCase.EventCase
          )) ||
            (tells.nonEmpty && tells.exists(t =>
              operandMessageKind(t.msg) == AggregateUseCase.EventCase
            ))
        if !emitsEvent then {
          messages.addCompleteness(
            omc.errorLoc,
            s"${entity.identify} is event-sourced but this command handler does not emit an event",
            suggestion =
              "Send or tell an event from this command handler so the event-sourced entity records its state change."
          )
        }
      }
    }
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

  private def validateProjector(
    projector: Projector,
    parents: Parents
  ): Unit = {
    checkContainer(parents, projector)
    check(
      projector.types.exists { (typ: Type) =>
        typ.typEx match {
          case auc: AggregateUseCaseTypeExpression =>
            auc.usecase == AggregateUseCase.RecordCase
          case _ => false
        }
      },
      s"${projector.identify} lacks a required ${AggregateUseCase.RecordCase.useCase} definition.",
      Messages.Error,
      projector.errorLoc,
      suggestion =
        s"Add a record type to ${projector.identify}, e.g. 'type ${projector.id.value}Record = record { ??? }'."
    )
    check(
      projector.handlers.length == 1,
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
                case TellStatement(_, mr: MessageRef, _) => sendTellRefs.append(mr)
                case TellStatement(_, ctor: Constructor, _) =>
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
      case None | Some(_) =>
        messages.addError(
          adaptor.errorLoc,
          "Adaptor not contained within Context",
          suggestion = "Define the adaptor inside a context."
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
        if ascribed.keyword != derived.keyword then
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
    addStreamlet(streamlet)
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

  private def validateDomain(
    domain: Domain,
    parents: Parents
  ): Unit = {
    checkContainer(parents, domain)
    check(
      domain.domains.isEmpty || domain.domains.size > 2,
      "Singly nested domains do not add value",
      StyleWarning,
      domain.errorLoc,
      suggestion =
        "Merge the single nested domain into its parent, or add sibling domains to justify the nesting."
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
  }

  private def validateSagaStep(
    s: SagaStep,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, s)
    checkNonEmpty(s.doStatements.toSeq, "Do Statements", s, MissingWarning)
    checkNonEmpty(s.undoStatements.toSeq, "Revert Statements", s, MissingWarning)
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
        case t: TellStatement if operandMessageKind(t.msg) == AggregateUseCase.CommandCase =>
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

  private def validateContext(
    c: Context,
    parents: Parents
  ): Unit = {
    checkContainer(parents, c)
    validateIntention(c)
    val nonEmptyEntities = c.entities.filter(_.nonEmpty)
    if nonEmptyEntities.nonEmpty && c.nonEmpty then {
      // Completeness 4i: context with entities must have a Sink
      val hasSinkOrInlet = c.streamlets.exists(_.inlets.nonEmpty)
      if !hasSinkOrInlet then {
        messages.addCompleteness(
          c.errorLoc,
          s"${c.identify} has entities but no Sink streamlet to receive and dispatch incoming messages",
          suggestion =
            s"Add a Sink streamlet with an inlet to ${c.identify} to receive and dispatch incoming messages."
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
      // Completeness 4b: streamlet handlers should dispatch to entities via tell
      c.streamlets.foreach { streamlet =>
        if streamlet.inlets.nonEmpty && streamlet.handlers.nonEmpty then {
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
    checkMetadata(input)
  }

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
      uc.contents.foreach {
        case seq: SequentialInteractions =>
          if seq.contents.isEmpty then {
            messages.addMissing(
              seq.loc,
              "Sequential interactions should not be empty",
              suggestion = "Add interactions to the sequential block, or remove the empty block."
            )
          }
        case par: ParallelInteractions =>
          if par.contents.isEmpty then {
            messages.addMissing(
              par.loc,
              "Parallel interaction should not be empty",
              suggestion = "Add interactions to the parallel block, or remove the empty block."
            )
          }
        case opt: OptionalInteractions =>
          if opt.contents.isEmpty then {
            messages.addMissing(
              opt.loc,
              "Optional interaction should not be empty",
              suggestion = "Add interactions to the optional block, or remove the empty block."
            )
          }
        case gi: GenericInteraction =>
          // Use comprehensive validateInteraction instead of inline validation
          validateInteraction(gi, parents)
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

  // FIXME: This should be used
  private def validateInteraction(interaction: Interaction, parents: Parents): Unit = {
    val useCase = parents.head
    // checkMetadata(useCase.identify, interaction, interaction.loc)
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
      case ArbitraryInteraction(_, from, _, to, _) =>
        checkRef[Definition](from, parents)
        checkRef[Definition](to, parents)
        val origin = resolution.refMap.definitionOf[Definition](from.pathId, parents.head)
        val destination = resolution.refMap.definitionOf[Definition](to.pathId, parents.head)
        validateArbitraryInteraction(origin, destination, parents)
      case ShowOutputInteraction(_, from: OutputRef, _, to: UserRef, _) =>
        checkRef[Output](from, parents)
        checkRef[User](to, parents)
      case SendMessageInteraction(_, from, msg, to, _) =>
        checkMessageRef(msg, parents, Seq(msg.messageKind))
        checkRef[Definition](from, parents)
        checkRef[Processor[?]](to, parents)
      case _: VagueInteraction =>
      // Nothing else to validate
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
          case ForeachStatement(_, _, _, doStatements) =>
            walkStatements(doStatements)(f)
          case _ => ()
      case _ => () // skip Comments
    }
  end walkStatements

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

  /** A25: the set of fields a `foreach ... in field <path>` may legally iterate — the fields of the
    * enclosing entity's state record(s), of the handled message, and of the enclosing function's
    * `requires` input. Membership is tested by identity against the resolved field.
    */
  private def foreachAllowedFields(parents: Parents): Seq[Field] =
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
  end foreachAllowedFields

  /** A25: validate a single `foreach` collection against the in-scope `let` locals and foreach
    * element names threaded to this point.
    */
  private def validateForeachCollection(
    fs: ForeachStatement,
    inScopeLets: Seq[LetStatement],
    inScopeElements: scala.collection.immutable.Set[String],
    parents: Parents
  ): Unit =
    fs.collection match
      case id: Identifier =>
        // A bare identifier names a `let`-bound local (or an enclosing foreach element).
        if inScopeElements.contains(id.value) then () // an outer foreach element; accepted
        else
          inScopeLets.reverse.find(_.identifier.value == id.value) match
            case Some(ls) =>
              ls.typeRef match
                case Some(tr) =>
                  resolution.refMap.definitionOf[Type](tr.pathId) match
                    case Some(typ) if !isCollectionType(typ.typEx) =>
                      messages.addError(
                        fs.loc,
                        s"'foreach' local '${id.value}' is not a collection; its declared type " +
                          s"'${tr.pathId.format}' is not iterable",
                        suggestion =
                          "Iterate a local whose 'let' type is a collection, e.g. 'let batch: many Order = ...'."
                      )
                    case _ => () // resolves to a collection, or unresolved (reported elsewhere)
                case None =>
                  messages.addError(
                    fs.loc,
                    s"'foreach' local '${id.value}' has no declared type, so it cannot be verified " +
                      "as a collection",
                    suggestion =
                      s"Declare the local's collection type, e.g. 'let ${id.value}: many Order = ...'."
                  )
            case None =>
              messages.addError(
                fs.loc,
                s"'foreach' collection '${id.value}' is not a 'let'-bound local in scope",
                suggestion =
                  "Bind the collection with a 'let' before the loop, or use 'field <path>' to iterate a field."
              )
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
            else if !foreachAllowedFields(parents).exists(_ eq field) then
              messages.addError(
                fs.loc,
                s"'foreach' field '${fr.pathId.format}' must be a field of the enclosing entity's " +
                  "state, the handled message, or a function input",
                suggestion =
                  "Reference a collection field of the entity state, the on-clause's message, or a " +
                    "function 'requires' input."
              )
          case None => () // unresolved field — ResolutionPass already reported it
  end validateForeachCollection

  /** A54: the fields a [[ValueRef]] may name — the fields of the enclosing entity's state
    * record(s), of the handled on-clause message, and of the enclosing function's `requires` input.
    * This is the same four-source machinery as [[foreachAllowedFields]] (function fields are only
    * present when a Function is an ancestor, so the function-input source is naturally limited to
    * function/return scope).
    */
  private def valueAllowedFields(parents: Parents): Seq[Field] = foreachAllowedFields(parents)

  /** A54: the named [[Type]] a [[Value]] denotes, or `None` when it is untyped (a pseudo-code
    * [[LiteralString]]) or cannot be determined. Used for best-effort type-compatibility checks;
    * `None` means "skip the check", so type errors are only raised when both sides resolve.
    */
  private def valueType(v: Value, parents: Parents, lets: Seq[LetStatement]): Option[Type] =
    v match
      case _: LiteralString => None // pseudo-code, untyped
      case _: PromptValue   => None // AI-computed, untyped
      case c: Constructor   => resolution.refMap.definitionOf[Type](c.ref.pathId)
      case vr: ValueRef     => valueRefType(vr, parents, lets)
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

  /** A54: the named [[Type]] a [[ValueRef]] resolves to, if determinable — from a `let`-local (a
    * single-component path), or a field of the message/state/function-input scope (by the path's
    * last component).
    */
  private def valueRefType(
    vr: ValueRef,
    parents: Parents,
    lets: Seq[LetStatement]
  ): Option[Type] =
    val name = vr.path.value.lastOption.getOrElse("")
    val fromLet: Option[Type] =
      if vr.path.value.sizeIs == 1 then
        lets.reverse
          .find(_.identifier.value == name)
          .flatMap(_.typeRef)
          .flatMap(tr => resolution.refMap.definitionOf[Type](tr.pathId))
      else None
    def fromField: Option[Type] =
      valueAllowedFields(parents).find(_.id.value == name).flatMap { f =>
        f.typeEx match
          case ate: AliasedTypeExpression => resolution.refMap.definitionOf[Type](ate.pathId)
          case _                          => None
      }
    fromLet.orElse(fromField)

  /** A54: whether a [[ValueRef]] resolves to something in scope (a `let`-local or a
    * message/state/function-input field). Used to report out-of-scope references.
    */
  private def valueRefResolves(
    vr: ValueRef,
    parents: Parents,
    lets: Seq[LetStatement]
  ): Boolean =
    val name = vr.path.value.lastOption.getOrElse("")
    val isLet = vr.path.value.sizeIs == 1 && lets.exists(_.identifier.value == name)
    val isField = valueAllowedFields(parents).exists(_.id.value == name)
    isLet || isField

  /** A54: validate a [[Value]] — recurse constructors, and confirm value references resolve. Get
    * sources are checked for existence via [[checkRef]].
    */
  private def validateValue(v: Value, parents: Parents, lets: Seq[LetStatement]): Unit =
    v match
      case _: LiteralString => ()
      case _: PromptValue   => () // literal AI prompt, nothing to resolve
      case c: Constructor   => validateConstructor(c, parents, lets)
      case vr: ValueRef =>
        if !valueRefResolves(vr, parents, lets) then
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
      case _: BooleanLiteral        => ()
      case ce: ComparisonExpression =>
        // A28: recurse operands, then require both to be category-compatible when both resolve.
        validateValue(ce.left, parents, lets)
        validateValue(ce.right, parents, lets)
        (valueCategory(ce.left, parents, lets), valueCategory(ce.right, parents, lets)) match
          case (Some(a), Some(b)) if a != b =>
            messages.addError(
              ce.loc,
              s"Cannot compare a $a value to a $b value with '${ce.op.symbol}'",
              suggestion = "Compare operands of the same kind (both numeric, both strings, etc.)."
            )
          case _ => ()
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

  /** A54: validate a [[Constructor]] — arg ordering (positional before named), named-arg field
    * existence, arity, and best-effort per-argument type compatibility against the target
    * aggregate's fields. Recurses into argument values.
    */
  private def validateConstructor(
    c: Constructor,
    parents: Parents,
    lets: Seq[LetStatement]
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
        else if c.args.nonEmpty && c.args.forall(_.name.isEmpty) && c.args.sizeIs != fields.size
        then
          messages.addError(
            c.loc,
            s"Constructor of ${typ.identify} has ${count(c.args.size, "positional argument")} but " +
              s"the type has ${count(fields.size, "field")}",
            suggestion =
              s"Supply exactly ${count(fields.size, "positional argument")}, or use named arguments for a subset."
          )
        // Best-effort per-argument type compatibility (only when both sides resolve to a Type).
        c.args.zipWithIndex.foreach { case (arg, idx) =>
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
                      s"Argument for field '${field.id.value}' has type ${a.identify} but " +
                        s"${field.id.value} expects ${e.identify}",
                      suggestion = s"Supply a value of type ${e.identify} for '${field.id.value}'."
                    )
                  case _ => ()
              case _ => () // primitive/other field type — literals accepted, no check
          }
        }
        // Recurse into argument values (nested constructors, value refs).
        c.args.foreach(arg => validateValue(arg.value, parents, lets))
      case None => () // unresolved constructor ref reported by ResolutionPass
  end validateConstructor

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
    inScopeElements: scala.collection.immutable.Set[String] =
      scala.collection.immutable.Set.empty[String]
  ): Unit =
    var lets = inScopeLets
    stmts.foreach {
      case ls: LetStatement =>
        // A54: validate the bound expression with the scope BEFORE this let (a let can't see itself),
        // then check its type against a declared `let x: T = …`.
        validateValue(ls.expression, parents, lets)
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
        validateValue(ss.value, parents, lets)
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
        s.msg match { case c: Constructor => validateValue(c, parents, lets); case _ => () }
      case s: TellStatement =>
        s.msg match { case c: Constructor => validateValue(c, parents, lets); case _ => () }
      case s: YieldStatement =>
        s.msg match { case c: Constructor => validateValue(c, parents, lets); case _ => () }
      case s: MorphStatement =>
        s.value match { case c: Constructor => validateValue(c, parents, lets); case _ => () }
      case fs: ForeachStatement =>
        validateForeachCollection(fs, lets, inScopeElements, parents)
        checkStatementScopes(
          fs.doStatements.toSeq.collect { case s: Statement => s },
          lets,
          parents,
          inScopeElements + fs.element.value
        )
      case ws: WhenStatement =>
        // A28: type-check a structured BooleanExpression condition (with in-scope `let` locals);
        // the LiteralString/Identifier forms have no expression to check here.
        ws.condition match
          case be: BooleanExpression => validateValue(be, parents, lets)
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
          case be: BooleanExpression => validateValue(be, parents, lets)
          case _                     => ()
      case ms: MatchStatement =>
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
      case _                   => ()
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
          case _: TellStatement | _: SendStatement | _: YieldStatement | _: MorphStatement |
              _: SetStatement | _: BecomeStatement | _: ErrorStatement | _: CodeStatement |
              _: PutStatement =>
            // A45: `put` publishes to a UI output — an executable effect. (ReturnStatement is not
            // added here: it only occurs in function bodies, which are classified by
            // validateFunction's statement-non-empty check, not classifyHandlers.)
            executableCount += 1
          case _: PromptStatement =>
            promptCount += 1
          case _ => ()
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
