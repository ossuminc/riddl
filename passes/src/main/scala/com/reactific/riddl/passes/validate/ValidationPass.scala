package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.Messages.{Message, StyleWarning}
import com.reactific.riddl.passes.{Pass, PassInfo, PassInput}
import com.reactific.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.reactific.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.reactific.riddl.utils.SeqHelpers.SeqHelpers

import scala.collection.mutable

object ValidationPass extends PassInfo {
  val name: String = "validation"
}
/** The ValidationPass
 *
 * @param resolution
 * Output of the prior Resolution pass including Symbols and Resolution passes
 */
case class ValidationPass (input: PassInput) extends Pass(input) with StreamingValidation {

  requires(SymbolsPass)
  requires(ResolutionPass)

  override def name: String = ValidationPass.name

  lazy val resolution: ResolutionOutput = input.outputOf[ResolutionOutput](ResolutionPass.name)
  lazy val symbols: SymbolsOutput = input.outputOf[SymbolsOutput](SymbolsPass.name)
  lazy val messages: Messages.Accumulator = Messages.Accumulator(input.commonOptions)

  /**
   * Generate the output of this Pass. This will only be called after all the calls
   * to process have completed.
   *
   * @return an instance of the output type
   */
  override def result: ValidationOutput = {
    ValidationOutput(messages.toMessages, inlets, outlets,
      connectors, streamlets, sends.toMap)
  }

  def postProcess(root: RootContainer): Unit = {
    checkOverloads()
    checkStreaming(root)
  }

  def process(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    val parentsAsSeq: Seq[Definition] = parents.toSeq
    definition match {
      // TODO: generate some frequency statistics and use them to reorganize this list of cases, most frequent first
      case f: Field =>
        validateField(f, parentsAsSeq)
      case t: Type =>
        validateType(t, parentsAsSeq)
      case e: Example =>
        validateExample(e, parentsAsSeq)
      case e: Enumerator =>
        validateEnumerator(e, parentsAsSeq)
      case i: Invariant =>
        validateInvariant(i, parentsAsSeq)
      case t: Term =>
        validateTerm(t, parentsAsSeq)
      case sa: Actor =>
        validateActor(sa, parentsAsSeq)
      case oic: OnInitClause =>
        checkDefinition(parentsAsSeq, oic)
      case otc: OnTermClause =>
        checkDefinition(parentsAsSeq, otc)
      case ooc: OnOtherClause =>
        checkDefinition(parentsAsSeq, ooc)
      case mc: OnMessageClause =>
        validateOnMessageClause(mc, parentsAsSeq)
      case h: Handler =>
        validateHandler(h, parentsAsSeq)
      case c: Constant =>
        validateConstant(c, parentsAsSeq)
      case i: Include[ApplicationDefinition]@unchecked =>
        validateInclude(i)
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
        validateAuthorInfo(a, parentsAsSeq)
      case s: SagaStep =>
        validateSagaStep(s, parentsAsSeq)
      case e: Entity =>
        validateEntity(e, parentsAsSeq)
      case a: Adaptor =>
        validateAdaptor(a, parentsAsSeq)
      case s: Streamlet =>
        validateStreamlet(s, parentsAsSeq)
      case p: Projector =>
        validateProjection(p, parentsAsSeq)
      case r: Repository =>
        validateRepository(r, parentsAsSeq)
      case s: Saga =>
        validateSaga(s, parentsAsSeq)
      case c: Context =>
        validateContext(c, parentsAsSeq)
      case d: Domain =>
        validateDomain(d, parentsAsSeq)
      case s: Epic =>
        validateStory(s, parentsAsSeq)
      case a: Application =>
        validateApplication(a, parentsAsSeq)
      case uc: UseCase =>
        validateUseCase(uc, parentsAsSeq)
      case grp: Group =>
        validateGroup(grp, parentsAsSeq)
      case in: Input =>
        validateInput(in, parentsAsSeq)
      case out: Output =>
        validateOutput(out, parentsAsSeq)
      case i: Interaction => validateInteraction(i, parentsAsSeq)
      case _: RootContainer => ()

      // NOTE: Never put a catch-all here, every Definition type must be handled
    }
  }

  def validateOnMessageClause(omc: OnMessageClause, parents: Seq[Definition]): Unit = {
    checkDefinition(parents, omc)
    if omc.msg.nonEmpty then {
      checkMessageRef(omc.msg, omc, parents, omc.msg.messageKind)
    }
    if omc.from.nonEmpty then {
      checkRef(omc.from.get, omc, parents)
    }
    checkDescription(omc)
  }

  private def validateTerm(
    t: Term,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, t).checkDescription(t)
  }

  private def validateEnumerator(
    e: Enumerator,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, e).checkDescription(e)
  }

  private def validateField(
    f: Field,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, f)
    if f.id.value.matches("^[^a-z].*") then {
      messages.add(
        Message(
          f.id.loc,
          "Field names should begin with a lower case letter",
          StyleWarning
        )
      )
    }
    checkTypeExpression(f.typeEx, f, parents)
    checkDescription(f)
  }

  private def validateExample(
    e: Example,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, e)
    checkExample(e, parents)
    checkDescription(e)
  }

  private def validateInvariant(
    i: Invariant,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, i)
    checkOption(i.expression, "condition", i) { expr =>
      checkExpression(expr, i, parents)
    }
    .checkDescription(i)
  }

  private def validateInlet(
    inlet: Inlet,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, inlet)
    checkRef[Type](inlet.type_, inlet, parents)
  }

  private def validateOutlet(
    outlet: Outlet,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, outlet)
    checkRef[Type](outlet.type_, outlet, parents)
  }

  private def validateConnector(
    connector: Connector,
    parents: Seq[Definition]
  ): Unit = {
    val refParents = connector +: parents
    val maybeOutlet = checkMaybeRef[Outlet](connector.from, connector, refParents)
    val maybeInlet = checkMaybeRef[Inlet](connector.to, connector, refParents)

    (maybeOutlet, maybeInlet) match {
      case (Some(outlet: Outlet), Some(inlet: Inlet)) =>
        val outletType = resolvePath[Type](outlet.type_.pathId, outlet +: refParents)
        val inletType = resolvePath[Type](inlet.type_.pathId, inlet +: refParents)
        if !areSameType(inletType, outletType) then  {
          messages.addError(
            inlet.loc,
            s"Type mismatch in ${connector.identify}: ${inlet.identify} " +
              s"requires ${inlet.type_.identify} and ${outlet.identify} requires ${outlet.type_.identify} which are " +
              s"not the same types"
          )
        }
      case _ =>
        // one of the two didn't resolve, already handled above.
    }
  }

  private def validateAuthorInfo(
    ai: Author,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, ai)
    checkNonEmptyValue(ai.name, "name", ai, required = true)
    checkNonEmptyValue(ai.email, "email", ai, required = true)
    checkDescription(ai)
  }

  private def validateType(
    t: Type,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, t)
    check(
      t.id.value.head.isUpper,
      s"${t.identify} should start with a capital letter",
      StyleWarning,
      t.loc
    )
    if !t.typ.isInstanceOf[AggregateTypeExpression] then {
        checkTypeExpression(t.typ, t, parents)
    }
    checkDescription(t)
  }

  private def validateConstant(
    c: Constant,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, c)
    val expr = c.value.asInstanceOf[Expression]
    val maybeTypEx = getExpressionType(expr, parents)
    if !isAssignmentCompatible(Some(c.typeEx), maybeTypEx) then {
      messages.addError(expr. loc,
        s"Expression value for ${c.identify} is not assignment compatible with declared type ${c.typeEx.format}")
    }
    checkDescription(c)
  }

  private def validateState(
    s: State,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, s)
    checkRefAndExamine[Type](s.typ, s, parents) { (typ: Type) =>
      typ.typ match {
        case agg: Aggregation =>
          if agg.fields.isEmpty && !s.isEmpty then {
            messages.addError(
              s.typ.loc,
              s"${s.identify} references an empty aggregate but must have " +
                s"at least one field"
            )
          }
        case _ =>
      }
    }
    checkDescription(s)
  }

  private def validateFunction(
    f: Function,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, f)
    checkOptions[FunctionOption](f.options, f.loc)
    checkDescription(f)
  }

  private def validateHandler(
    h: Handler,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, h)
    checkDescription(h)
  }


  private def validateInclude[T <: Definition](
    i: Include[T]
  ): Unit = {
    check(i.nonEmpty, "Include has no included content", Messages.Error, i.loc)
    check(i.source.nonEmpty, "Include has no source provided", Messages.Error, i.loc)
  }

  private def validateEntity(
    e: Entity,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, e)
    checkOptions[EntityOption](e.options, e.loc)
    if e.states.isEmpty && !e.isEmpty then {
      messages.add(Message(
        e.loc,
        s"${e.identify} must define at least one state",
        Messages.MissingWarning
      ))
    }
    if e.handlers.nonEmpty && e.handlers.forall(_.clauses.isEmpty) then {
      messages.add(
        Message(e.loc, s"${e.identify} has only empty handlers", Messages.MissingWarning)
      )
    }
    if e.hasOption[EntityIsFiniteStateMachine] && e.states.sizeIs < 2 then {
      messages.add(Message(
        e.loc,
        s"${e.identify} is declared as an fsm, but doesn't have at least two states",
        Messages.Error
      ))
    }
    if e.states.nonEmpty && e.states.forall(_.handlers.isEmpty) && e.handlers.isEmpty then {
      messages.add(
        Message(
          e.loc,
          s"${e.identify} has ${e.states.size} state${
            if e.states.size != 1 then "s"
            else ""
          } but no handlers.",
          Messages.Error
        ))
    }
    checkDescription(e)
  }

  private def validateProjection(
    p: Projector,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, p)
    check(p.types.exists { (typ: Type) =>
      typ.typ match {
        case auc: AggregateUseCaseTypeExpression =>
          auc.usecase == RecordCase
        case _ => false
      }
    },
      s"${p.identify} lacks a required ${RecordCase.format} definition.",
      Messages.Error,
      p.loc
    )
    check(
      p.handlers.length == 1,
      s"${p.identify} must have exactly one Handler but has ${p.handlers.length}",
      Messages.Error,
      p.loc
    )
    checkDescription(p)
  }

  private def validateRepository(
    r: Repository,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, r)
    checkDescription(r)
  }

  private def validateAdaptor(
    a: Adaptor,
    parents: Seq[Definition]
  ): Unit = {
    parents.headOption match {
      case Some(c: Context) =>
        checkContainer(parents, a)
        resolvePath(a.context.pathId, parents).map { (target: Context) =>
          if target == c then {
            val message =
              s"${a.identify} may not specify a target context that is " +
                s"the same as the containing ${c.identify}"
            messages.addError(a.loc, message)
          }
        }
        checkDescription(a)
      case None | Some(_) =>
        messages.addError(a.loc, "Adaptor not contained within Context")
    }
  }

  private def validateStreamlet(
    s: Streamlet,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, s)
    checkStreamletShape(s)
    checkDescription(s)
  }

  private def validateDomain(
    d: Domain,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, d)
    check(
      d.domains.isEmpty || d.domains.size > 2,
      "Singly nested domains do not add value",
      StyleWarning,
      if d.domains.isEmpty then d.loc else d.domains.head.loc
    )
    checkDescription(d)
  }

  private def validateSaga(
    s: Saga,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, s)
    check(
      s.nonEmpty && s.sagaSteps.size >= 2,
      "Sagas must define at least 2 steps",
      Messages.Error,
      s.loc
    )
    check(
      s.nonEmpty && s.sagaSteps.size >= 2 && s.sagaSteps.map(_.id.value).allUnique,
      "Saga step names must all be distinct",
      Messages.Error,
      s.loc
    )
    checkDescription(s)
  }

  private def validateSagaStep(
    s: SagaStep,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, s)
    check(
      s.doAction.getClass == s.undoAction.getClass,
      "The primary action and revert action must be the same shape",
      Messages.Error,
      s.loc
    )
    val parentsSeq = parents.toSeq
    checkExamples(s.doAction, s +: parentsSeq)
    checkExamples(s.undoAction, s +: parentsSeq)
    checkDescription(s)
  }

  private def validateContext(
    c: Context,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, c)
    checkOptions[ContextOption](c.options, c.loc)
    checkDescription(c)
  }

  private def validateStory(
    s: Epic,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, s)
    if s.userStory.isEmpty then {
      messages.addMissing(s.loc, s"${s.identify} is missing a user story")
    }
    checkDescription(s)
  }

  private def validateApplication(
    app: Application,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, app)
    if app.groups.isEmpty then {
      messages.addMissing(app.loc, s"${app.identify} should have a group")
    }
    checkDescription(app)
  }

  private def validateGroup(
    grp: Group,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, grp).checkDescription(grp)
  }

  private def validateInput(
    in: Input,
    parents: Seq[Definition]
  ): Unit = {
    val parentsSeq = parents.toSeq
    checkDefinition(parentsSeq, in)
    checkMessageRef(in.putIn, in, parentsSeq, CommandCase)
    checkDescription(in)
  }

  private def validateOutput(
    out: Output,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, out)
    checkMessageRef(out.putOut, out, parents, ResultCase)
    checkDescription(out)
  }

  private def validateActor(
    actor: Actor,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, actor)
    if actor.is_a.isEmpty then {
      messages.addMissing(
        actor.loc,
        s"${actor.identify} is missing its role kind ('is a')"
      )
    }
    checkDescription(actor)
  }

  private def validateUseCase(
    uc: UseCase,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, uc)
      if uc.contents.nonEmpty then {
        uc.contents.foreach { step =>
          step match {
            case seq: SequentialInteractions =>
              if seq.contents.isEmpty then {
                messages.addMissing(seq.loc,
                  "Sequential interactions should not be empty")
              }
            case par: ParallelInteractions =>
              if par.contents.isEmpty then {
                messages.addMissing(
                  par.loc,
                  "Parallel interaction should not be empty"
                )
              }
            case opt: OptionalInteractions =>
              if opt.contents.isEmpty then {
                messages.addMissing(
                  opt.loc,
                  "Optional interaction should not be empty"
                )
              }
            case is: GenericInteraction =>
              checkPathRef[Definition](is.from.pathId, uc, parents)
              checkPathRef[Definition](is.to.pathId, uc, parents)
              if is.relationship.isEmpty then {
                messages.addMissing(
                  step.loc,
                  s"Interactions must have a non-empty relationship"
                )
              }
          }
        }
      }
      if uc.nonEmpty then {
        if uc.contents.isEmpty then (
          messages.addMissing(
            uc.loc,
            s"${uc.identify} doesn't define any interactions"
          )
        )
      }
      checkDescription(uc)
  }

  private def validateInteraction(interaction: Interaction, parents: Seq[Definition]): Unit = {
    checkDefinition(parents, interaction)
    interaction match {
      case SelfInteraction(_,_,from,_,_,_) => checkRef[Definition](from, interaction, parents)
      case PutInputInteraction(_, _, from, _, to, _, _) =>
        checkRef[Actor](from, interaction, parents)
        checkRef[Input](to, interaction, parents)
      case ArbitraryInteraction(_, _, from, _, to, _, _) =>
        checkRef[Definition](from, interaction, parents)
        checkRef[Definition](to, interaction, parents)
      case TakeOutputInteraction(_, _, from, _, to, _, _) =>
        checkRef[Output](from, interaction, parents)
        checkRef[Actor](to, interaction, parents)
      case _ => ()
        // These are all just containers of other interactions, not needing further validation
        // OptionalInteractions, ParallelInteractions, SequentialInteractions
    }
    checkDescription(interaction)
  }



}
