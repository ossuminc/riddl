package com.reactific.riddl.language.passes.validation

import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.{Message, StyleWarning}
import com.reactific.riddl.language.passes.Pass
import com.reactific.riddl.language.passes.resolution.ResolutionOutput
import com.reactific.riddl.utils.SeqHelpers.SeqHelpers

/** The ValidationPass
 *
 * @param resolution
 * Output of the prior Resolution pass including Symbols and Resolution passes
 */
case class ValidationPass (resolution: ResolutionOutput) extends
  Pass[ResolutionOutput,ValidationOutput](resolution) with StreamingValidation {

  val messages: Messages.Accumulator = Messages.Accumulator(resolution.commonOptions)

  /**
   * Generate the output of this Pass. This will only be called after all the calls
   * to process have completed.
   *
   * @return an instance of the output type
   */
  override def result: ValidationOutput = {
      ValidationOutput(resolution.root, resolution.commonOptions, messages, resolution, inlets, outlets, connectors,
        streamlets, sends.toMap)
  }

  def processLeafDefinition(leaf: LeafDefinition, parents: Seq[Definition]): Unit = {
    leaf match {
      case f: Field => validateField(f, parents)
      case e: Example => validateExample(e, parents)
      case e: Enumerator => validateEnumerator(e, parents)
      case i: Invariant => validateInvariant(i, parents)
      case t: Term => validateTerm(t, parents)
      case i: Inlet => validateInlet(i, parents)
      case o: Outlet => validateOutlet(o, parents)
      case a: Author => validateAuthorInfo( a, parents)
      case sa: Actor => validateActor(sa, parents)
      case sc: UseCase => validateStoryCase(sc, parents)
      case c: Connector => validateConnection(c, parents)
    }
  }

  def processHandlerDefinition(hd: HandlerDefinition, parents: Seq[Definition]): Unit = {
    // TODO: write this methood
  }

  def processApplicationDefinition(ad: ApplicationDefinition, parents: Seq[Definition]): Unit = {
    ad match {
      case typ: Type => validateType(typ, parents)
      case grp: Group => validateGroup(grp, parents)
      case h: Handler => validateHandler(h, parents)
      case in: Input => validateInput(in, parents)
      case out: Output => validateOutput(out, parents)
      case in: Inlet => validateInlet(in, parents)
      case out: Outlet => validateOutlet( out, parents)
      case t: Term => validateTerm( t, parents)
      case c: Constant => validateConstant( c, parents)
      case i: Include[ApplicationDefinition]@unchecked => validateInclude( i)
    }
  }

  def processEntityDefinition(ed: EntityDefinition, parents: Seq[Definition]): Unit = {
    ed match {
      case t: Type => validateType(t, parents)
      case s: State => validateState( s, parents)
      case h: Handler => validateHandler( h, parents)
      case f: Function => validateFunction( f, parents)
      case i: Invariant => validateInvariant( i, parents)
      case t: Term => validateTerm( t, parents)
      case c: Constant => validateConstant( c, parents)
      case i: Inlet => validateInlet( i, parents)
      case o: Outlet => validateOutlet( o, parents)
      case i: Include[EntityDefinition]@unchecked =>
        validateInclude( i)
    }
  }

  def processProjectorDefinition(pd: ProjectorDefinition, parents: Seq[Definition]): Unit = {
    // TODO: write this method
  }
  def processRepositoryDefinition(rd: RepositoryDefinition, parents: Seq[Definition]): Unit= {
    rd match {
      case h: Handler => validateHandler(h, parents)
      case t: Type => validateType(t, parents)
      case c: Constant => validateConstant(c, parents)
      case i: Inlet => validateInlet(i, parents)
      case o: Outlet => validateOutlet(o, parents)
      case t: Term => validateTerm(t, parents)
      case i: Include[RepositoryDefinition]@unchecked =>
        validateInclude(i)
    }
  }

  def processSagaDefinition(sd: SagaDefinition, parents: Seq[Definition]): Unit = {
    sd match {
      case f: Function => validateFunction(f, parents)
      case s: SagaStep => validateSagaStep(s, parents)
      case f: Field => validateField(f, parents)
      case i: Inlet => validateInlet(i, parents)
      case o: Outlet => validateOutlet(o, parents)
    }
  }

  def processContextDefinition(cd: ContextDefinition, parents: Seq[Definition]): Unit = {
    cd match {
      case t: Type => validateType(t, parents)
      case c: Constant => validateConstant(c, parents)
      case h: Handler => validateHandler(h, parents)
      case f: Function => validateFunction(f, parents)
      case e: Entity => validateEntity(e, parents)
      case a: Adaptor => validateAdaptor(a, parents)
      case s: Streamlet => validateStreamlet(s, parents)
      case p: Projector => validateProjection(p, parents)
      case r: Repository => validateRepository(r, parents)
      case t: Term => validateTerm(t, parents)
      case s: Saga => validateSaga(s, parents)
      case i: Inlet => validateInlet(i, parents)
      case o: Outlet => validateOutlet(o, parents)
      case c: Connector => validateConnection(c, parents)
      case i: Include[ContextDefinition]@unchecked =>
        validateInclude(i)
    }
  }

  def processDomainDefinition(dd: DomainDefinition, parents: Seq[Definition]): Unit = {
    dd match {
      case a: Application => validateApplication(a, parents)
      case t: Type => validateType(t, parents)
      case c: Constant => validateConstant(c, parents)
      case c: Context => validateContext(c, parents)
      case d: Domain => validateDomain(d, parents)
      case s: Epic => validateStory(s, parents)
      case t: Term => validateTerm(t, parents)
      case a: Author => validateAuthorInfo(a, parents)
      case a: Actor => validateActor(a, parents)
      case i: Include[DomainDefinition]@unchecked =>
        validateInclude(i)
    }
  }

  def processAdaptorDefinition(ad: AdaptorDefinition, parents: Seq[Definition]): Unit = {
    ad match {
      case h: Handler => validateHandler(h, parents)
      case i: Inlet => validateInlet(i, parents)
      case o: Outlet => validateOutlet(o, parents)
      case t: Type => validateType(t, parents)
      case t: Term => validateTerm(t, parents)
      case c: Constant => validateConstant(c, parents)
      case i: Include[AdaptorDefinition]@unchecked =>
        validateInclude(i)
    }
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
    if (f.id.value.matches("^[^a-z].*")) {
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
    addInlet(inlet)
    checkDefinition(parents, inlet)
    checkRef[Type](inlet.type_, inlet, parents)
  }

  private def validateOutlet(
    outlet: Outlet,
    parents: Seq[Definition]
  ): Unit = {
    addOutlet(outlet)
    checkDefinition(parents, outlet)
    checkRef[Type](outlet.type_, outlet, parents)
  }

  private def validateConnection(
    connector: Connector,
    parents: Seq[Definition]
  ): Unit = {
    checkMaybeRef[Outlet](connector.from, connector, parents)
    checkMaybeRef[Inlet](connector.to, connector, parents)
    addConnection(connector)
    val maybeOutlet: Option[Outlet] = connector.from.flatMap { outRef =>
      resolvePath[Outlet](outRef.pathId, parents)
    }
    val maybeInlet: Option[Inlet] = connector.to.flatMap { inRef =>
      resolution.refMap.definitionOf[Inlet](inRef.pathId, parents.head)
    }

    (maybeOutlet, maybeInlet) match {
      case (Some(outlet: Outlet), Some(inlet: Inlet)) =>
        if (!areSameType(inlet.type_, outlet.type_, parents)) {
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
    if (!t.typ.isInstanceOf[AggregateTypeExpression]) {
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
    if (!isAssignmentCompatible(Some(c.typeEx), maybeTypEx)) {
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
    checkRefAndExamine[Type](s.typ, s, parents) { typ: Type =>
      typ.typ match {
        case agg: Aggregation =>
          if (agg.fields.isEmpty && !s.isEmpty) {
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
    h.clauses.foreach(validateOnClause(_, parents))
    checkDescription(h)
  }

  private def validateOnClause(
    oc: OnClause,
    parents: Seq[Definition]
  ): Unit = {
    oc match {
      case oic: OnInitClause =>
        checkDefinition(parents, oic)
      case omc@OnMessageClause(_, msg, from, _, _, _) =>
        checkDefinition(parents, omc)
        if (msg.nonEmpty) {
            checkMessageRef(msg, oc, parents, msg.messageKind)
          }
        if (from.nonEmpty) {
          checkRef(from.get, oc, parents)
        }
      case oic: OnTermClause =>
        checkDefinition(parents, oic)
      case ooc: OnOtherClause =>
        checkDefinition(parents, ooc)
    }
    checkDescription(oc)
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
    if (e.states.isEmpty && !e.isEmpty) {
      messages.add(Message(
        e.loc,
        s"${e.identify} must define at least one state",
        Messages.MissingWarning
      ))
    }
    if (e.handlers.nonEmpty && e.handlers.forall(_.clauses.isEmpty)) {
      messages.add(
        Message(e.loc, s"${e.identify} has only empty handlers", Messages.MissingWarning)
      )
    }
    if (e.hasOption[EntityIsFiniteStateMachine] && e.states.sizeIs < 2) {
      messages.add(Message(
        e.loc,
        s"${e.identify} is declared as an fsm, but doesn't have at least two states",
        Messages.Error
      ))
    }
    if (e.states.nonEmpty && e.states.forall(_.handlers.isEmpty) && e.handlers.isEmpty) {
      messages.add(
        Message(
          e.loc,
          s"${e.identify} has ${e.states.size} state${
            if (e.states.size != 1) "s"
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
    check(p.types.exists { typ: Type =>
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
        resolvePath(a.context.pathId, parents).map { target: Context =>
          if (target == c) {
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
    addStreamlet(s)
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
      if (d.domains.isEmpty) d.loc else d.domains.head.loc
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
    if (s.userStory.isEmpty) {
      messages.addMissing(s.loc, s"${s.identify} is missing a user story")
    }
    checkDescription(s)
  }

  private def validateApplication(
    app: Application,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, app)
    if (app.groups.isEmpty) {
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
    if (actor.is_a.isEmpty) {
      messages.addMissing(
        actor.loc,
        s"${actor.identify} is missing its role kind ('is a')"
      )
    }
    checkDescription(actor)
  }

  private def validateStoryCase(
    sc: UseCase,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, sc)
      if (sc.interactions.nonEmpty) {
        sc.interactions.foreach { step =>
          step match {
            case seq: SequentialInteractions =>
              if (seq.contents.isEmpty) {
                messages.addMissing(seq.loc,
                  "Sequential interactions should not be empty")
              }
            case par: ParallelInteractions =>
              if (par.contents.isEmpty) {
                messages.addMissing(
                  par.loc,
                  "Parallel interaction should not be empty"
                )
              }
            case opt: OptionalInteractions =>
              if (opt.contents.isEmpty) {
                messages.addMissing(
                  opt.loc,
                  "Optional interaction should not be empty"
                )
              }
            case is: GenericInteraction =>
              checkPathRef[Definition](is.from.pathId, sc, parents)
              checkPathRef[Definition](is.to.pathId, sc, parents)
              if (is.relationship.isEmpty) {
                messages.addMissing(
                  step.loc,
                  s"Interactions must have a non-empty relationship"
                )
              }
          }
        }
      }
      if (sc.nonEmpty) {
        if (sc.interactions.isEmpty)(
          messages.addMissing(
            sc.loc,
            s"${sc.identify} doesn't define any interactions"
          )
        )
      }
      checkDescription(sc)
  }
}
