/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.Messages.{Message, MissingWarning, StyleWarning, error, missing}
import com.reactific.riddl.passes.{Pass, PassInfo, PassInput}
import com.reactific.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.reactific.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.reactific.riddl.utils.SeqHelpers.SeqHelpers
import com.sun.nio.file.SensitivityWatchEventModifier

import scala.collection.mutable

object ValidationPass extends PassInfo {
  val name: String = "validation"
}

/** The ValidationPass
  *
  * @param input
  *   Input from previous passes
  */
case class ValidationPass(input: PassInput) extends Pass(input) with StreamingValidation {

  requires(SymbolsPass)
  requires(ResolutionPass)

  override def name: String = ValidationPass.name

  lazy val resolution: ResolutionOutput = input.outputOf[ResolutionOutput](ResolutionPass.name)
  lazy val symbols: SymbolsOutput = input.outputOf[SymbolsOutput](SymbolsPass.name)
  lazy val messages: Messages.Accumulator = Messages.Accumulator(input.commonOptions)

  /** Generate the output of this Pass. This will only be called after all the calls to process have completed.
    *
    * @return
    *   an instance of the output type
    */
  override def result: ValidationOutput = {
    ValidationOutput(messages.toMessages, inlets, outlets, connectors, streamlets)
  }

  def postProcess(root: RootContainer): Unit = {
    checkOverloads()
    checkStreaming(root)
  }

  def process(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    val parentsAsSeq: Seq[Definition] = parents.toSeq
    definition match {
      // TODO: generate some frequency statistics and use them to reorganize this list of cases, most frequent first
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
      // TODO: Add statement validation to OnClauses
      case omc: OnMessageClause =>
        validateOnMessageClause(omc, parentsAsSeq)
      case oic: OnInitClause =>
        checkDefinition(parentsAsSeq, oic)
      case otc: OnTerminationClause =>
        checkDefinition(parentsAsSeq, otc)
      case ooc: OnOtherClause =>
        checkDefinition(parentsAsSeq, ooc)
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
      case i: Interaction   => validateInteraction(i, parentsAsSeq)
      case _: RootContainer => ()

      // NOTE: Never put a catch-all here, every Definition type must be handled
    }
  }

  @SuppressWarnings(Array("org.wartremover.wart.IsInstanceOf"))
  def validateOnMessageClause(omc: OnMessageClause, parents: Seq[Definition]): Unit = {
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
    }
    omc.from.foreach { (ref: Reference[Definition]) =>
      checkRef[Definition](ref, omc, parents)
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
  private def validateMethod(
    m: Method,
    parents: Seq[Definition]
  ): Unit = {
    // TODO: Write validateMethod
    ()
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
    checkDescription(c)
  }

  private def validateReplica(
    replica: Replica,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, replica)
    checkTypeExpression(replica.typeExp, replica, parents)
    replica.typeExp match {
      case Mapping(loc, from, to)                =>
      case Sequence(loc, of)                     =>
      case Set(loc, of)                          =>
      case Optional(loc, typeExp)                =>
      case ZeroOrMore(loc, typeExp)              =>
      case OneOrMore(loc, typeExp)               =>
      case SpecificRange(loc, typeExp, min, max) =>
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
    // FIXME: validate this at the epic/case level so the restriction occurs only when sent to the backend
    checkTypeRef(in.putIn, in, parentsSeq)
    checkDescription(in)
  }

  private def validateOutput(
    out: Output,
    parents: Seq[Definition]
  ): Unit = {
    checkDefinition(parents, out)
    // FIXME: validate this at the epic/case level so the restriction occurs only when received from the backend
    checkTypeRef(out.putOut, out, parents)
    checkDescription(out)
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
      uc.contents.foreach { step =>
        step match {
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
      if uc.contents.isEmpty then
        (
          messages.addMissing(
            uc.loc,
            s"${uc.identify} doesn't define any interactions"
          )
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
              case output @ Output(loc, alias, id, putOut, _, _, _) =>
                checkTypeRef(putOut, parents.head, parents.tail) match {
                  case Some(Type(_,_,typEx,_,_))  if typEx.isContainer =>
                    typEx match {
                      case ate: AggregateUseCaseTypeExpression if  ate.usecase == EventCase || ate.usecase == ResultCase =>
                          None // events and results are permitted
                      case ty: TypeExpression => // everything else is not
                        Some(
                          error(s"${output.identify} showing ${putOut.format} of type ${ty.format} is invalid "+
                            s" because ${o.identify} is a vital definition which can only send Events and Results", loc
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
              case input @ Input(loc, alias, id, putIn, _, _, _) =>
                checkTypeRef(putIn, parents.head, parents.tail) match {
                  case Some(Type(_, _, typEx, _, _)) if typEx.isContainer =>
                    typEx match {
                      case ate: AggregateUseCaseTypeExpression if ate.usecase == CommandCase || ate.usecase == QueryCase =>
                        None // commands and queries are permitted
                      case ty: TypeExpression => // everything else is not
                        Some(
                          error(s"${input.identify} sending ${putIn.format} of type ${ty.format} is invalid "+
                            s" because ${d.identify} is a vital definition which can only receive Commands and Queries")
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
    interaction match {
      case SelfInteraction(_, _, from, _, _, _) => checkRef[Definition](from, interaction, parents)
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
      case _ => ()
      // These are all just containers of other interactions, not needing further validation
      // OptionalInteractions, ParallelInteractions, SequentialInteractions
    }
    checkDescription(interaction)
  }

}
