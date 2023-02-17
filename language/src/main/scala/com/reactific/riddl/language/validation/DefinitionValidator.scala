package com.reactific.riddl.language.validation

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.Error
import com.reactific.riddl.language.Messages.Message
import com.reactific.riddl.language.Messages.MissingWarning
import com.reactific.riddl.language.Messages.StyleWarning
import com.reactific.riddl.language.Messages.error
import com.reactific.riddl.utils.SeqHelpers.SeqHelpers

import scala.annotation.unused
import scala.collection.mutable

object DefinitionValidator {

  def validate(
    state: ValidationState,
    definition: Definition,
    parents: mutable.Stack[Definition]
  ): ValidationState = {
    // Capture current parse state including now the definition as the
    // top element of the parent stack
    definition match {
      case leaf: LeafDefinition => validateADefinition(state, leaf, parents)
      case i: Include[Definition] @unchecked =>
        i.contents.foldLeft(state) {
          case (s, d: LeafDefinition) => validateADefinition(s, d, parents)
          case (s, cd: Definition)    => validate(s, cd, parents)
        }
      case container: Definition =>
        validateADefinition(state, container, parents)
        parents.push(container)
        val st = container.contents.foldLeft(state) { (st1, defn) =>
          this.validate(st1, defn, parents)
        }
        parents.pop()
        st
    }
  }

  private def validateADefinition(
    state: ValidationState,
    definition: Definition,
    definitionParents: mutable.Stack[Definition]
  ): ValidationState = {
    val parents = definitionParents.toSeq
    definition match {
      case leaf: LeafDefinition =>
        leaf match {
          case f: Field      => validateField(state, f, parents)
          case e: Example    => validateExample(state, e, parents)
          case e: Enumerator => validateEnumerator(state, e, parents)
          case i: Invariant  => validateInvariant(state, i, parents)
          case t: Term       => validateTerm(state, t, parents)
          case p: Pipe       => validatePipe(state, p, parents)
          case i: Inlet      => validateInlet(state, i, parents)
          case o: Outlet     => validateOutlet(state, o, parents)
          case a: Author     => validateAuthorInfo(state, a, parents)
          case sa: Actor     => validateActor(state, sa, parents)
          case sc: StoryCase => validateStoryCase(state, sc, parents)
        }
      case ad: ApplicationDefinition =>
        ad match {
          case typ: Type   => validateType(state, typ, parents)
          case grp: Group  => validateGroup(state, grp, parents)
          case h: Handler  => validateHandler(state, h, parents)
          case in: Input   => validateInput(state, in, parents)
          case out: Output => validateOutput(state, out, parents)
          case t: Term     => validateTerm(state, t, parents)
          case i: Include[ApplicationDefinition] @unchecked =>
            validateInclude(state, i)
        }
      case ed: EntityDefinition =>
        ed match {
          case t: Type      => validateType(state, t, parents)
          case s: State     => validateState(state, s, parents)
          case h: Handler   => validateHandler(state, h, parents)
          case f: Function  => validateFunction(state, f, parents)
          case i: Invariant => validateInvariant(state, i, parents)
          case t: Term      => validateTerm(state, t, parents)
          case i: Inlet     => validateInlet(state, i, parents)
          case o: Outlet    => validateOutlet(state, o, parents)
          case i: Include[EntityDefinition] @unchecked =>
            validateInclude(state, i)
        }
      case rd: RepositoryDefinition =>
        rd match {
          case h: Handler => validateHandler(state, h, parents)
          case t: Type    => validateType(state, t, parents)
          case i: Inlet   => validateInlet(state, i, parents)
          case o: Outlet  => validateOutlet(state, o, parents)
          case t: Term    => validateTerm(state, t, parents)
          case i: Include[RepositoryDefinition] @unchecked =>
            validateInclude(state, i)
        }
      case cd: ContextDefinition =>
        cd match {
          case t: Type       => validateType(state, t, parents)
          case h: Handler    => validateHandler(state, h, parents)
          case f: Function   => validateFunction(state, f, parents)
          case e: Entity     => validateEntity(state, e, parents)
          case a: Adaptor    => validateAdaptor(state, a, parents)
          case p: Processor  => validateProcessor(state, p, parents)
          case p: Projection => validateProjection(state, p, parents)
          case r: Repository => validateRepository(state, r, parents)
          case t: Term       => validateTerm(state, t, parents)
          case p: Pipe       => validatePipe(state, p, parents)
          case s: Saga       => validateSaga(state, s, parents)
          case i: Inlet      => validateInlet(state, i, parents)
          case o: Outlet     => validateOutlet(state, o, parents)
          case i: Include[ContextDefinition] @unchecked =>
            validateInclude(state, i)
        }
      case dd: DomainDefinition =>
        dd match {
          case a: Application => validateApplication(state, a, parents)
          case t: Type        => validateType(state, t, parents)
          case c: Context     => validateContext(state, c, parents)
          case d: Domain      => validateDomain(state, d, parents)
          case s: Story       => validateStory(state, s, parents)
          case t: Term        => validateTerm(state, t, parents)
          case a: Author      => validateAuthorInfo(state, a, parents)
          case a: Actor       => validateActor(state, a, parents)
          case i: Include[DomainDefinition] @unchecked =>
            validateInclude(state, i)
        }
      case hd: HandlerDefinition =>
        hd match { case oc: OnClause => validateOnClause(state, oc, parents) }
      case ad: AdaptorDefinition =>
        ad match {
          case h: Handler => validateHandler(state, h, parents)
          case i: Inlet   => validateInlet(state, i, parents)
          case o: Outlet  => validateOutlet(state, o, parents)
          case t: Term    => validateTerm(state, t, parents)
          case i: Include[AdaptorDefinition] @unchecked =>
            validateInclude(state, i)
        }
      case ss: SagaStep     => validateSagaStep(state, ss, parents)
      case _: RootContainer => state // ignore
    }
  }

  private def validateTerm(
    state: ValidationState,
    t: Term,
    parents: Seq[Definition]
  ): ValidationState = { state.checkDefinition(parents, t).checkDescription(t) }

  private def validateEnumerator(
    state: ValidationState,
    e: Enumerator,
    parents: Seq[Definition]
  ): ValidationState = { state.checkDefinition(parents, e).checkDescription(e) }

  private def validateField(
    state: ValidationState,
    f: Field,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkDefinition(parents, f)
      .addIf(f.id.value.matches("^[^a-z].*"))(
        Message(
          f.id.loc,
          "Field names should begin with a lower case letter",
          StyleWarning
        )
      )
      .checkTypeExpression(f.typeEx, f, parents)
      .checkDescription(f)
  }

  private def validateExample(
    state: ValidationState,
    e: Example,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkDefinition(parents, e)
      .checkExample(e, parents)
      .checkDescription(e)
  }

  private def validateInvariant(
    state: ValidationState,
    i: Invariant,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkDefinition(parents, i)
      .checkOption(i.expression, "condition", i) { (st, expr) =>
        st.checkExpression(expr, i, parents)
      }
      .checkDescription(i)
  }

  private def validatePipe(
    state: ValidationState,
    p: Pipe,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .addPipe(p)
      .checkDefinition(parents, p)
      .checkOption(p.transmitType, "transmit type", p) { (st, typeRef) =>
        st.checkPathRef[Type](typeRef.pathId, p, parents)()()
      }
      .checkOption(p.from, "from outlet", p) { (st, outlet) =>
        st.checkPathRef[Outlet](outlet.pathId, p, parents)()()
      }
      .checkOption(p.to, "to inlet", p) { (st, inletRef) =>
        val st2: state.type =
          st.checkPathRef[Inlet](inletRef.pathId, p, parents)()()
        if (p.transmitType.nonEmpty) {
          val maybeResolved: Option[Inlet] = st2
            .resolvePathIdentifier[Inlet](inletRef.pathId, parents)

          val mapped: Option[state.type] = maybeResolved.map { inlet =>
            if (!st2.areSameType(p.transmitType.get, inlet.type_, parents)) {
              st2.addError(
                inletRef.loc,
                s"Type mismatch: expected ${inlet.type_.identify} " +
                  s" but ${p.identify} is defined to transmit ${p.transmitType.get.identify}"
              )
            } else { st2 }
          }
          mapped.getOrElse(st2)
        } else { st2 }
      }
      .checkDescription(p)
  }

  private def validateInlet(
    state: ValidationState,
    inlet: Inlet,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .addInlet(inlet)
      .checkDefinition(parents, inlet)
      .checkRef[Type](inlet.type_, inlet, parents)
      .checkDescription(inlet)
  }

  private def validateOutlet(
    state: ValidationState,
    outlet: Outlet,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .addOutlet(outlet)
      .checkDefinition(parents, outlet)
      .checkRef[Type](outlet.type_, outlet, parents)
      .checkDescription(outlet)
  }

  private def validateAuthorInfo(
    state: ValidationState,
    ai: Author,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkDefinition(parents, ai)
      .checkNonEmptyValue(ai.name, "name", ai, required = true)
      .checkNonEmptyValue(ai.email, "email", ai, required = true)
      .checkDescription(ai)
  }

  private def validateType(
    state: ValidationState,
    t: Type,
    parents: Seq[Definition]
  ): ValidationState = {
    state.addType(t)
    state
      .checkDefinition(parents, t)
      .check(
        t.id.value.head.isUpper,
        s"${t.identify} should start with a capital letter",
        StyleWarning,
        t.loc
      )
      .checkThat(!t.typ.isInstanceOf[AggregateTypeExpression]) { vs =>
        vs.checkTypeExpression(t.typ, t, parents)
      }
      .checkDescription(t)
  }

  private def validateState(
    state: ValidationState,
    s: State,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkContainer(parents, s)
      .addIf(s.aggregation.fields.isEmpty && !s.isEmpty) {
        Message(
          s.aggregation.loc,
          s"${s.identify} must define at least one field"
        )
      }
      .addIf(s.handlers.isEmpty && !s.isEmpty) {
        Message(s.loc, s"${s.identify} must define a handler")
      }
      .checkDescription(s)
  }

  private def validateFunction(
    state: ValidationState,
    f: Function,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .addFunction(f)
      .checkContainer(parents, f)
      .checkOptions[FunctionOption](f.options, f.loc)
      .checkDescription(f)
  }

  private def validateHandler(
    state: ValidationState,
    h: Handler,
    parents: Seq[Definition]
  ): ValidationState = { state.checkContainer(parents, h).checkDescription(h) }

  private def validateOnClause(
    state: ValidationState,
    oc: OnClause,
    parents: Seq[Definition]
  ): ValidationState = {
    (oc match {
      case oic: OnInitClause =>
        state
          .checkDefinition(parents, oic)
      case omc @ OnMessageClause(_, msg, from, _, _, _) =>
        state
          .checkDefinition(parents, omc)
          .checkThat(msg.nonEmpty) { st =>
            st.checkMessageRef(msg, oc, parents, msg.messageKind)
          }
          .checkThat(from.nonEmpty) { st =>
            st.checkRef(from.get, oc, parents)
          }
      case oic: OnTermClause =>
        state
          .checkDefinition(parents, oic)
      case ooc: OnOtherClause => state.checkDefinition(parents, ooc)
    }).checkDescription(oc)
  }

  private def validateInclude[T <: Definition](
    state: ValidationState,
    i: Include[T]
  ): ValidationState = {
    state
      .check(i.nonEmpty, "Include has no included content", Error, i.loc)
      .check(i.source.nonEmpty, "Include has no source provided", Error, i.loc)
  }

  private def validateEntity(
    state: ValidationState,
    e: Entity,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .addEntity(e)
      .checkContainer(parents, e)
      .checkOptions[EntityOption](e.options, e.loc)
      .addIf(e.states.isEmpty && !e.isEmpty) {
        Message(
          e.loc,
          s"${e.identify} must define at least one state",
          MissingWarning
        )
      }
      .addIf(e.handlers.nonEmpty && e.handlers.forall(_.clauses.isEmpty)) {
        Message(e.loc, s"${e.identify} has only empty handlers", MissingWarning)
      }
      .addIf(e.hasOption[EntityIsFiniteStateMachine] && e.states.sizeIs < 2) {
        Message(
          e.loc,
          s"${e.identify} is declared as an fsm, but doesn't have at least two states",
          Error
        )
      }
      .checkDescription(e)
  }

  private def validateProjection(
    state: ValidationState,
    p: Projection,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkContainer(parents, p)
      .check(
        p.types.exists { typ =>
          typ.typ match {
            case auc: AggregateUseCaseTypeExpression =>
              auc.usecase == RecordCase
            case _ => false
          }
        },
        s"${p.identify} lacks a required ${RecordCase.format} definition.",
        Error,
        p.loc
      )
      .check(
        p.handlers.length == 1,
        s"${p.identify} must have exactly one Handler but has ${p.handlers.length}",
        Error,
        p.loc
      )
      .checkDescription(p)
  }

  private def validateRepository(
    state: ValidationState,
    r: Repository,
    parents: Seq[Definition]
  ): ValidationState = { state.checkContainer(parents, r).checkDescription(r) }

  private def validateAdaptor(
    state: ValidationState,
    a: Adaptor,
    parents: Seq[Definition]
  ): ValidationState = {
    parents.headOption match {
      case Some(c: Context) =>
        val s1 = state.checkContainer(parents, a)
        val targetContext = s1.resolvePath(a.context.pathId, parents)()()
        val s2 = targetContext.headOption match {
          case Some(target: Context) =>
            if (target == c) {
              val message =
                s"${a.identify} may not specify a target context that is " +
                  s"the same as the containing ${c.identify}"
              s1.add(error(message, a.loc))
            } else { s1 }
          case None | Some(_) => s1
        }
        s2.checkDescription(a)
      case None | Some(_) =>
        state
          .add(error("Adaptor not contained within Context", a.loc))
    }
  }

  private def validateProcessor(
    state: ValidationState,
    p: Processor,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .addProcessor(p)
      .checkContainer(parents, p)
      .checkProcessorShape(p)
      .checkDescription(p)
  }

  private def validateDomain(
    state: ValidationState,
    d: Domain,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkContainer(parents, d)
      .check(
        d.domains.isEmpty || d.domains.size > 2,
        "Singly nested domains do not add value",
        StyleWarning,
        if (d.domains.isEmpty) d.loc else d.domains.head.loc
      )
      .checkDescription(d)
  }

  private def validateSaga(
    state: ValidationState,
    s: Saga,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkContainer(parents, s)
      .check(
        s.nonEmpty && s.sagaSteps.size >= 2,
        "Sagas must define at least 2 steps",
        Error,
        s.loc
      )
      .check(
        s.nonEmpty && s.sagaSteps.size >= 2 && s.sagaSteps
          .map(_.id.value)
          .allUnique,
        "Saga step names must all be distinct",
        Error,
        s.loc
      )
      .checkDescription(s)
  }

  private def validateSagaStep(
    state: ValidationState,
    s: SagaStep,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkContainer(parents, s)
      .check(
        s.doAction.getClass == s.undoAction.getClass,
        "The primary action and revert action must be the same shape",
        Error,
        s.loc
      )
      .checkExamples(s.doAction, s +: parents)
      .checkExamples(s.undoAction, s +: parents)
      .checkDescription(s)
  }

  private def validateContext(
    state: ValidationState,
    c: Context,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkContainer(parents, c)
      .checkOptions[ContextOption](c.options, c.loc)
      .checkDescription(c)
  }

  private def validateStory(
    state: ValidationState,
    s: Story,
    parents: Seq[Definition]
  ): ValidationState = {
    val s1: ValidationState =
      state.checkContainer(parents, s).checkThat(s.userStory.isEmpty) {
        vs: state.type =>
          vs.addMissing(s.loc, s"${s.identify} is missing a user story")
      }
    s1.checkExamples(s.examples, parents).checkDescription(s)
  }

  private def validateApplication(
    state: ValidationState,
    app: Application,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkContainer(parents, app)
      .checkThat(app.groups.isEmpty) { vs: state.type =>
        vs.addMissing(app.loc, s"${app.identify} should have a group")
      }
      .checkDescription(app)
  }

  private def validateGroup(
    state: ValidationState,
    grp: Group,
    parents: Seq[Definition]
  ): ValidationState = {
    state.checkDefinition(parents, grp).checkDescription(grp)
  }

  private def validateInput(
    state: ValidationState,
    in: Input,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkDefinition(parents, in)
      .checkMessageRef(in.putIn, in, parents, CommandCase)
      .checkDescription(in)
  }

  private def validateOutput(
    state: ValidationState,
    out: Output,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkDefinition(parents, out)
      .checkMessageRef(out.putOut, out, parents, ResultCase)
      .checkDescription(out)
  }

  private def validateActor(
    state: ValidationState,
    @unused actor: Actor,
    @unused parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkDefinition(parents, actor)
      .checkThat(actor.is_a.isEmpty) { vs =>
        vs.addMissing(
          actor.loc,
          s"${actor.identify} is missing its role kind ('is a')"
        )
      }
      .checkDescription(actor)
  }

  private def validateStoryCase(
    state: ValidationState,
    sc: StoryCase,
    parents: Seq[Definition]
  ): ValidationState = {
    state
      .checkDefinition(parents, sc)
      .stepIf(sc.interactions.nonEmpty) { st: state.type =>
        sc.interactions.foldLeft[st.type](st) { (st, step) =>
          step match {
            case par: ParallelGroup =>
              st.stepIf(par.contents.isEmpty) { vs =>
                vs.addMissing(
                  par.loc,
                  "Parallel interaction should not be empty"
                )
              }
            case opt: OptionalGroup =>
              st.stepIf(opt.contents.isEmpty) { vs =>
                vs.addMissing(
                  opt.loc,
                  "Optional interaction should not be empty"
                )
              }
            case is: InteractionStep =>
              st
                .checkPathRef[Definition](is.from.pathId, sc, parents)()()
                .checkPathRef[Definition](is.to.pathId, sc, parents)()()
                .checkThat(is.relationship.isEmpty)(
                  _.addMissing(
                    step.loc,
                    s"Interactions must have a non-empty relationship"
                  )
                )
          }
        }
      }
      .stepIf(sc.nonEmpty) { vs =>
        vs.checkThat(sc.interactions.isEmpty)(
          _.addMissing(
            sc.loc,
            s"${sc.identify} doesn't define any interactions"
          )
        )
      }
      .checkDescription(sc)
  }
}