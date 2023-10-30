/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.Messages.{Message, MissingWarning, StyleWarning, error, missing}
import com.reactific.riddl.language.parsing.Terminals.Options
import com.reactific.riddl.passes.{Pass, PassInfo, PassInput, PassOutput, PassesOutput}
import com.reactific.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.reactific.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.reactific.riddl.utils.SeqHelpers.SeqHelpers

import scala.collection.mutable


/** The output of the Validation Pass */
case class ValidationOutput(
  messages: Messages.Messages = Messages.empty,
  inlets: Seq[Inlet] = Seq.empty[Inlet],
  outlets: Seq[Outlet] = Seq.empty[Outlet],
  connectors: Seq[Connector] = Seq.empty[Connector],
  streamlets: Seq[Streamlet] = Seq.empty[Streamlet],
  processors: Seq[Processor[?,?]] = Seq.empty[Processor[?,?]]
) extends PassOutput

object ValidationPass extends PassInfo {
  val name: String = "Validation"
}

/** The ValidationPass
  *
  * @param input
  *   Input from previous passes
  */
@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
case class ValidationPass(
  input: PassInput,
  outputs: PassesOutput
) extends Pass(input, outputs)
    with StreamingValidation {

  requires(SymbolsPass)
  requires(ResolutionPass)

  override def name: String = ValidationPass.name

  lazy val resolution: ResolutionOutput = outputs.outputOf[ResolutionOutput](ResolutionPass.name).get
  lazy val symbols: SymbolsOutput = outputs.outputOf[SymbolsOutput](SymbolsPass.name).get

  /** Generate the output of this Pass. This will only be called after all the calls to process have completed.
    *
    * @return
    *   an instance of the output type
    */
  override def result: ValidationOutput = {
    ValidationOutput(messages.toMessages, inlets, outlets, connectors, streamlets, resolution.kindMap.definitionsOfKind[Processor[?,?]])
  }

  def postProcess(root: RootContainer): Unit = {
    checkOverloads()
    checkStreaming(root)
  }

  def process(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    val parentsAsSeq: Seq[Definition] = parents.toSeq
    definition match {
      case f: AggregateDefinition =>
        f match {
          case f: Field  => validateField(f, parentsAsSeq)
          case m: Method => validateMethod(m, parentsAsSeq)
        }
      case t: Type =>
        validateType(t, parentsAsSeq)
      case e: Enumerator =>
        validateEnumerator(e, parentsAsSeq)
      case i: Invariant =>
        validateInvariant(i, parentsAsSeq)
      case t: Term =>
        validateTerm(t, parentsAsSeq)
      case sa: User =>
        validateUser(sa, parentsAsSeq)
      case omc: OnMessageClause =>
        validateOnMessageClause(omc, parentsAsSeq)
        validateStatements(omc.statements, omc, parentsAsSeq)
      case oic: OnInitClause =>
        checkDefinition(parentsAsSeq, oic)
        validateStatements(oic.statements, oic, parentsAsSeq)
      case otc: OnTerminationClause =>
        checkDefinition(parentsAsSeq, otc)
        validateStatements(otc.statements, otc, parentsAsSeq)
      case ooc: OnOtherClause =>
        checkDefinition(parentsAsSeq, ooc)
        validateStatements(ooc.statements, ooc, parentsAsSeq)
      case h: Handler =>
        validateHandler(h, parentsAsSeq)
      case c: Constant =>
        validateConstant(c, parentsAsSeq)
      case i: Include[ApplicationDefinition] @unchecked =>
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
        validateEpic(s, parentsAsSeq)
      case a: Application =>
        validateApplication(a, parentsAsSeq)
      case r: Replica =>
        validateReplica(r, parentsAsSeq)
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
      case i: Interaction   => validateInteraction(i, parentsAsSeq)
      case _: RootContainer => ()

      // NOTE: Never put a catch-all here, every Definition type must be handled
    }
  }

  @SuppressWarnings(Array("org.wartremover.wart.IsInstanceOf"))
  private def validateOnMessageClause(omc: OnMessageClause, parents: Seq[Definition]): Unit = {
    checkDefinition(parents, omc)
    if omc.msg.nonEmpty then {
      checkMessageRef(omc.msg, omc, parents, Seq(omc.msg.messageKind))
      omc.msg.messageKind match {
        case CommandCase =>
          val sends: Seq[SendStatement] = omc.statements
            .filter(_.isInstanceOf[SendStatement])
            .map(_.asInstanceOf[SendStatement])
          if sends.isEmpty || sends.contains { (x: SendStatement) => x.msg.messageKind == EventCase } then
            messages.add(
              missing("Processing for commands should result in sending an event", omc.loc)
            )
        case QueryCase =>
          val sends: Seq[SendStatement] = omc.statements
            .filter(_.isInstanceOf[SendStatement])
            .map(_.asInstanceOf[SendStatement])
          if sends.isEmpty || sends.contains((x: SendStatement) => x.msg.messageKind == ResultCase) then
            messages.add(
              missing("Processing for queries should result in sending a result", omc.loc)
            )
        case _ =>
      }
    } else {}
    omc.from.foreach { (ref: Reference[Definition]) =>
      checkRef[Definition](ref, omc, parents)
    }
    checkDescription(omc)
  }

  private def validateStatements(statements: Seq[Statement], onClause: OnClause, parents: Seq[Definition]): Unit = {
    if statements.isEmpty then
      messages.add(
        missing(s"${onClause.identify} should have statements")
      )
    else
      for { statement <- statements } do {
        statement match {
          case ArbitraryStatement(loc, what) =>
            checkNonEmptyValue(what, "arbitrary statement", onClause, loc, MissingWarning, required = true)
          case ErrorStatement(loc, message) =>
            checkNonEmptyValue(message, "error description", onClause, loc, MissingWarning, required = true)
          case SetStatement(loc, field, value) =>
            checkRef[Field](field, onClause, parents)
            checkNonEmptyValue(value, "value to set", onClause, loc, MissingWarning, required = true)
          case ReturnStatement(loc, value) =>
            checkNonEmptyValue(value, "value to set", onClause, loc, MissingWarning, required = true)
          case SendStatement(loc, msg, portlet) =>
            checkRef[Type](msg, onClause, parents)
            checkRef[Portlet](portlet, onClause, parents)
          case ReplyStatement(loc, message) =>
            checkRef[Type](message, onClause, parents)
          case MorphStatement(loc, entity, state, value) =>
            checkRef[Entity](entity, onClause, parents)
            checkRef[State](state, onClause, parents)
            checkRef[Type](value, onClause, parents)
          case BecomeStatement(loc, entityRef, handlerRef) =>
            checkRef[Entity](entityRef, onClause, parents).foreach { entity =>
              checkCrossContextReference(entityRef.pathId, entity, onClause)
            }
            checkRef[Handler](handlerRef, onClause, parents).foreach { handler =>
              checkCrossContextReference(handlerRef.pathId, handler, onClause)
            }
          case TellStatement(loc, msg, processorRef) =>
            val maybeProc = checkRef[Processor[?, ?]](processorRef, onClause, parents)
            maybeProc.foreach { entity =>
              checkCrossContextReference(processorRef.pathId, entity, onClause)
            }
            val maybeType = checkRef[Type](msg, onClause, parents)
            maybeType.foreach { typ =>
              checkCrossContextReference(msg.pathId, typ, onClause)
            }

          case CallStatement(loc, funcRef) =>
            checkRef[Function](funcRef, onClause, parents).foreach { function =>
              checkCrossContextReference(funcRef.pathId, function, onClause)
            }

          case ForEachStatement(loc, pid, do_) =>
            checkPathRef[Type](pid, onClause, parents).foreach { typ =>
              checkCrossContextReference(pid, typ, onClause)
              check(
                typ.typ.hasCardinality,
                s"The foreach statement requires a type with cardinality but ${pid.format} does not",
                Messages.Error,
                loc
              )
            }
            checkNonEmpty(do_, "statement list", onClause, MissingWarning, required = false)
          case IfThenElseStatement(loc, cond, thens, elses) =>
            checkNonEmptyValue(cond, "condition", onClause, loc, MissingWarning, required = true)
            checkNonEmpty(thens, "statements", onClause, loc, MissingWarning, required = true)
            checkNonEmpty(elses, "statements", onClause, loc, MissingWarning, required = false)
          case StopStatement(loc) => ()
        }
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
  private def validateMethod(
    m: Method,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, m)
    if m.id.value.matches("^[^a-z].*") then
      messages.add(
        Message(
          m.id.loc,
          "Method names should begin with a lower case letter",
          StyleWarning
        )
      )
    checkTypeExpression(m.typeEx, m, parents)
    for arg <- m.args do
      checkTypeExpression(arg.value, m, parents)
      if arg.key.matches("^[^a-z].*") then
        messages.add(
          Messages.style(
            "Method argument names should begin with a lower case letter",
            m.id.loc
          )
        )
    checkDescription(m)
  }

  private def validateInvariant(
    i: Invariant,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, i)
    checkNonEmpty(i.condition.toList, "condition", i, Messages.MissingWarning, false)
    checkDescription(i)
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
        if !areSameType(inletType, outletType) then {
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

  @SuppressWarnings(Array("org.wartremover.wart.IsInstanceOf"))
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
      messages.add(
        Message(
          e.loc,
          s"${e.identify} must define at least one state",
          Messages.MissingWarning
        )
      )
    }
    if e.handlers.nonEmpty && e.handlers.forall(_.clauses.isEmpty) then {
      messages.add(
        Message(e.loc, s"${e.identify} has only empty handlers", Messages.MissingWarning)
      )
    }
    if e.hasOption[EntityIsFiniteStateMachine] && e.states.sizeIs < 2 then {
      messages.add(
        Message(
          e.loc,
          s"${e.identify} is declared as an fsm, but doesn't have at least two states",
          Messages.Error
        )
      )
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
        )
      )
    }
    checkDescription(e)
  }

  private def validateProjection(
    p: Projector,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, p)
    check(
      p.types.exists { (typ: Type) =>
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
      d.loc
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
    checkDefinition(parents, s)
    checkNonEmpty(s.doStatements, "Do Statements", s, MissingWarning)
    checkNonEmpty(s.doStatements, "Revert Statements", s, MissingWarning)
    check(
      s.doStatements.getClass == s.undoStatements.getClass,
      "The primary action and revert action must be the same shape",
      Messages.Error,
      s.loc
    )
    checkDescription(s)
  }

  private def validateContext(
    c: Context,
    parents: Seq[Definition]
  ): Unit = {
    checkContainer(parents, c)
    checkOptions[ContextOption](c.options, c.loc)
    c.options.find(_.name == Options.color) match
      case Some(option) =>
        check(option.args.size == 1, "The 'color' option must have a single value", Messages.Error, option.loc)
        option.args.headOption match
          case Some(value) =>
            val regex = "^([a-z]+)|(#\\p{XDigit}{6})$".r
            if !regex.matches(value.s) then
              messages.addError(value.loc, "The value of the color option must be a valid HTML color")
          case None =>
            messages.addError(option.loc, "The color option requires a single value but none provided")
      case None =>
    checkDescription(c)
  }

  private def validateReplica(
    replica: Replica,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, replica)
    checkTypeExpression(replica.typeExp, replica, parents)
    replica.typeExp match {
      case Mapping(loc, from, to) =>
      case Sequence(loc, of)      =>
      case Set(loc, of)           =>
      case typeEx: Cardinality    =>
      case t: TypeExpression =>
        messages.addError(t.loc, s"Type expression in Replica ${replica.identify} is not a replicable type")
    }
    checkDescription(replica)
  }

  private def validateEpic(
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
    checkTypeRef(in.putIn, in, parentsSeq)
    checkDescription(in)
  }

  private def validateOutput(
    out: Output,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, out)
    checkTypeRef(out.putOut, out, parents)
    checkDescription(out)
  }

  private def validateContainedGroup(
    containedGroup: ContainedGroup,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, containedGroup)
    checkRef[Group](containedGroup.group, containedGroup, parents)
    checkDescription(containedGroup)
  }

  private def validateUser(
    user: User,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, user)
    if user.is_a.isEmpty then {
      messages.addMissing(
        user.loc,
        s"${user.identify} is missing its role kind ('is a')"
      )
    }
    checkDescription(user)
  }

  private def validateUseCase(
    uc: UseCase,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, uc)
    if uc.contents.nonEmpty then {
      uc.contents.foreach {
        case seq: SequentialInteractions =>
          if seq.contents.isEmpty then {
            messages.addMissing(seq.loc, "Sequential interactions should not be empty")
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
        case opt: VagueInteraction =>
          check(
            opt.relationship.nonEmpty,
            "Vague Interactions should have a non-empty description",
            Messages.MissingWarning,
            opt.relationship.loc
          )
        case step @ (is: GenericInteraction) =>
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
    if uc.nonEmpty then {
      if uc.contents.isEmpty then
        messages.addMissing(
          uc.loc,
          s"${uc.identify} doesn't define any interactions"
        )
    }
    checkDescription(uc)
  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  private def validateArbitraryInteraction(
    origin: Option[Definition],
    destination: Option[Definition],
    parents: Seq[Definition]
  ): Unit = {
    val maybeMessage: Option[Message] = origin match {
      case Some(o) if o.isVital =>
        destination match {
          case Some(d) if d.isAppRelated =>
            d match {
              case output@Output(loc, _, id, _, putOut, _, _, _) =>
                checkTypeRef(putOut, parents.head, parents.tail) match {
                  case Some(Type(_, _, typEx, _, _)) if typEx.isContainer =>
                    typEx match {
                      case ate: AggregateUseCaseTypeExpression
                          if ate.usecase == EventCase || ate.usecase == ResultCase =>
                        None // events and results are permitted
                      case ty: TypeExpression => // everything else is not
                        Some(
                          error(
                            s"${output.identify} showing ${putOut.format} of type ${ty.format} is invalid " +
                              s" because ${o.identify} is a vital definition which can only send Events and Results",
                            loc
                          )
                        )
                    }
                  case _ => None //
                }
              case _ => None
            }
          case _ => None
        }
      case Some(o) if o.isAppRelated =>
        destination match {
          case Some(d) if d.isVital =>
            o match {
              case input@Input(loc, _, id, _, putIn, _, _, _) =>
                checkTypeRef(putIn, parents.head, parents.tail) match {
                  case Some(Type(_, _, typEx, _, _)) if typEx.isContainer =>
                    typEx match {
                      case ate: AggregateUseCaseTypeExpression
                          if ate.usecase == CommandCase || ate.usecase == QueryCase =>
                        None // commands and queries are permitted
                      case ty: TypeExpression => // everything else is not
                        Some(
                          error(
                            s"${input.identify} sending ${putIn.format} of type ${ty.format} is invalid " +
                              s" because ${d.identify} is a vital definition which can only receive Commands and Queries"
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

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  private def validateInteraction(interaction: Interaction, parents: Seq[Definition]): Unit = {
    checkDefinition(parents, interaction)
    checkDescription(interaction)
    interaction match {
      case SelfInteraction(_, _, from, _, _, _) =>
        checkRef[Definition](from, interaction, parents)
      case TakeInputInteraction(_, _, user: UserRef, _, inputRef: InputRef, _, _) =>
        checkRef[User](user, interaction, parents)
        checkRef[Input](inputRef, interaction, parents)
      case ArbitraryInteraction(_, _, from, _, to, _, _) =>
        checkRef[Definition](from, interaction, parents)
        checkRef[Definition](to, interaction, parents)
        val origin = resolution.refMap.definitionOf[Definition](from.pathId, parents.head)
        val destination = resolution.refMap.definitionOf[Definition](to.pathId, parents.head)
        validateArbitraryInteraction(origin, destination, parents)
      case ShowOutputInteraction(_, _, from: OutputRef, _, to: UserRef, _, _) =>
        checkRef[Output](from, interaction, parents)
        checkRef[User](to, interaction, parents)
      case _: VagueInteraction =>
      // Nothing else to validate
      case _: OptionalInteractions | _: ParallelInteractions | _: SequentialInteractions =>
      // These are all just containers of other interactions, not needing further validation
    }
  }

}
