/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.ossuminc.riddl.utils.SeqHelpers.*

import scala.collection.mutable
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters.*

object ValidationPass extends PassInfo[PassOptions] {
  val name: String = "Validation"
  def creator(options: PassOptions = PassOptions.empty): PassCreator = { (in: PassInput, out: PassesOutput) =>
    ValidationPass(in, out)
  }
}

/** A Pass for validating the content of a RIDDL model. This pass can produce many warnings and errors about the model.
  * @param input
  *   Input from previous passes
  * @param outputs
  *   The outputs from prior passes (symbols & resolution)
  */
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
  override def result(root: Root): ValidationOutput = {
    ValidationOutput(
      root,
      messages.toMessages,
      inlets,
      outlets,
      connectors,
      streamlets,
      resolution.kindMap.definitionsOfKind[Processor[?]]
    )
  }

  override def postProcess(root: Root): Unit = {
    checkOverloads()
    checkStreaming(root)
  }

  def process(value: RiddlValue, parents: ParentStack): Unit = {
    val parentsAsSeq: Parents = parents.toParentsSeq
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
        validateInvariant(i, parentsAsSeq)
      case t: Term =>
        validateTerm(t, parentsAsSeq)
      case sa: User =>
        validateUser(sa, parentsAsSeq)
      case omc: OnMessageClause =>
        validateOnMessageClause(omc, parentsAsSeq)
        validateStatements(omc.contents, omc, parentsAsSeq)
      case oic: OnInitializationClause =>
        checkDefinition(parentsAsSeq, oic)
        validateStatements(oic.contents, oic, parentsAsSeq)
      case otc: OnTerminationClause =>
        checkDefinition(parentsAsSeq, otc)
        validateStatements(otc.contents, otc, parentsAsSeq)
      case ooc: OnOtherClause =>
        checkDefinition(parentsAsSeq, ooc)
        validateStatements(ooc.contents, ooc, parentsAsSeq)
      case h: Handler =>
        validateHandler(h, parentsAsSeq)
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
        validateProjector(p, parentsAsSeq)
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
      case uc: UseCase =>
        validateUseCase(uc, parentsAsSeq)
      case a: Application =>
        validateApplication(a, parentsAsSeq)
      case grp: Group =>
        validateGroup(grp, parentsAsSeq)
      case in: Input =>
        validateInput(in, parentsAsSeq)
      case out: Output =>
        validateOutput(out, parentsAsSeq)
      case cg: ContainedGroup =>
        validateContainedGroup(cg, parentsAsSeq)
      case _: BriefDescription    => () // Just text, nothing to validate
      case _: Root                => () // No validation needed
      case _: Definition          => () // No validation on abstract type
      case _: NonDefinitionValues => () // We only validate definitions
      // NOTE: Never put a catch-all here, every Definition type must be handled
    }
  }

  private def validateOnMessageClause(omc: OnMessageClause, parents: Parents): Unit = {
    checkDefinition(parents, omc)
    if omc.msg.nonEmpty then {
      checkMessageRef(omc.msg, parents, Seq(omc.msg.messageKind))
      omc.msg.messageKind match {
        case CommandCase =>
          val sends: Seq[SendStatement] = omc.contents
            .filter(_.isInstanceOf[SendStatement])
            .map(_.asInstanceOf[SendStatement])
          if sends.isEmpty || sends.contains { (x: SendStatement) => x.msg.messageKind == EventCase } then
            messages.add(
              missing("Processing for commands should result in sending an event", omc.loc)
            )
        case QueryCase =>
          val sends: Seq[SendStatement] = omc.contents
            .filter(_.isInstanceOf[SendStatement])
            .map(_.asInstanceOf[SendStatement])
          if sends.isEmpty || sends.contains((x: SendStatement) => x.msg.messageKind == ResultCase) then
            messages.add(
              missing("Processing for queries should result in sending a result", omc.loc)
            )
        case _ =>
      }
    } else {}
    omc.from.foreach { (_: Option[Identifier], ref: Reference[Definition]) =>
      checkRef[Definition](ref, parents)
    }
    checkDescriptions(omc,omc.contents)
  }

  private def validateStatements(
    statements: Seq[Statements],
    onClause: OnClause,
    parents: Parents
  ): Unit = {
    if statements.isEmpty then
      messages.add(
        missing(s"${onClause.identify} should have statements")
      )
    else
      val newParents = onClause +: parents
      for { statement <- statements } do {
        statement match {
          case ArbitraryStatement(loc, what) =>
            checkNonEmptyValue(what, "arbitrary statement", onClause, loc, MissingWarning, required = true)
          case FocusStatement(loc, group) =>
            checkRef[Group](group, newParents)
          case ErrorStatement(loc, message) =>
            checkNonEmptyValue(message, "error description", onClause, loc, MissingWarning, required = true)
          case SetStatement(loc, field, value) =>
            checkRef[Field](field, newParents)
            checkNonEmptyValue(value, "value to set", onClause, loc, MissingWarning, required = true)
          case ReturnStatement(loc, value) =>
            checkNonEmptyValue(value, "value to set", onClause, loc, MissingWarning, required = true)
          case SendStatement(loc, msg, portlet) =>
            checkRef[Type](msg, newParents)
            checkRef[Portlet](portlet, newParents)
          case ReplyStatement(loc, message) =>
            checkRef[Type](message, newParents)
          case MorphStatement(loc, entity, state, value) =>
            checkRef[Entity](entity, newParents)
            checkRef[State](state, newParents)
            checkRef[Type](value, newParents)
          case BecomeStatement(loc, entityRef, handlerRef) =>
            checkRef[Entity](entityRef, newParents).foreach { entity =>
              checkCrossContextReference(entityRef.pathId, entity, onClause)
            }
            checkRef[Handler](handlerRef, newParents).foreach { handler =>
              checkCrossContextReference(handlerRef.pathId, handler, onClause)
            }
          case TellStatement(loc, msg, processorRef) =>
            val maybeProc = checkRef[Processor[?]](processorRef, parents)
            maybeProc.foreach { entity =>
              checkCrossContextReference(processorRef.pathId, entity, onClause)
            }
            val maybeType = checkRef[Type](msg, newParents)
            maybeType.foreach { typ =>
              checkCrossContextReference(msg.pathId, typ, onClause)
            }

          case CallStatement(loc, funcRef) =>
            checkRef[Function](funcRef, newParents).foreach { function =>
              checkCrossContextReference(funcRef.pathId, function, onClause)
            }

          case ForEachStatement(loc, ref, do_) =>
            checkPathRef[Type](ref.pathId, newParents).foreach { typ =>
              checkCrossContextReference(ref.pathId, typ, onClause)
              check(
                typ.typEx.hasCardinality,
                s"The foreach statement requires a type with cardinality but ${ref.pathId.format} does not",
                Messages.Error,
                loc
              )
            }
            checkNonEmpty(do_, "statement list", onClause, MissingWarning)
          case IfThenElseStatement(loc, cond, thens, elses) =>
            checkNonEmptyValue(cond, "condition", onClause, loc, MissingWarning, required = true)
            checkNonEmpty(thens, "statements", onClause, loc, MissingWarning, required = true)
            checkNonEmpty(elses, "statements", onClause, loc, MissingWarning, required = false)
          case ReadStatement(loc, keyword, what, from, where) =>
            checkNonEmpty(keyword, "read keyword", onClause, loc, Messages.Error, required = true)
            checkNonEmptyValue(what, "what", onClause, loc, MissingWarning, required = false)
            checkTypeRef(from, parents)
            checkNonEmptyValue(where, "where", onClause, loc, MissingWarning, required = false)
          case WriteStatement(loc, keyword, what, to) =>
            checkNonEmpty(keyword, "write keyword", onClause, loc, Messages.Error, required = true)
            checkTypeRef(to, parents)
            checkNonEmptyValue(what, "what", onClause, loc, MissingWarning, required = false)
          case _: CodeStatement => ()
          case _: StopStatement => ()
          case _: Comment       => ()
        }
      }
  }

  private def validateTerm(
    t: Term,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, t)
    checkDescription(t)
  }

  private def validateEnumerator(
    e: Enumerator,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, e)
    checkDescription(e)
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
          StyleWarning
        )
      )
    }
    checkTypeExpression(f.typeEx, f, parents)
    checkDescription(f)
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
          StyleWarning
        )
      )
    checkTypeExpression(m.typeEx, m, parents)
    for arg <- m.args do
      checkTypeExpression(arg.typeEx, m, parents)
      if arg.name.matches("^[^a-z].*") then
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
    parents: Parents
  ): Unit = {
    checkDefinition(parents, i)
    checkNonEmpty(i.condition.toList, "condition", i, Messages.MissingWarning)
    checkDescription(i)
  }

  private def validateInlet(
    inlet: Inlet,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, inlet)
    checkRef[Type](inlet.type_, parents)
  }

  private def validateOutlet(
    outlet: Outlet,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, outlet)
    checkRef[Type](outlet.type_, parents)
  }

  private def validateConnector(
    connector: Connector,
    parents: Parents
  ): Unit = {
    if connector.nonEmpty then
      val maybeOutlet = checkRef[Outlet](connector.from, parents)
      val maybeInlet = checkRef[Inlet](connector.to, parents)

      (maybeOutlet, maybeInlet) match {
        case (Some(outlet: Outlet), Some(inlet: Inlet)) =>
          val outletType = resolvePath[Type](outlet.type_.pathId, parents)
          val inletType = resolvePath[Type](inlet.type_.pathId, parents)
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
    parents: Parents
  ): Unit = {
    checkDefinition(parents, ai)
    checkNonEmptyValue(ai.name, "name", ai, required = true)
    checkNonEmptyValue(ai.email, "email", ai, required = true)
    checkDescription(ai)
  }

  private def validateType(
    t: Type,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, t)
    check(
      t.id.value.head.isUpper,
      s"${t.identify} should start with a capital letter",
      StyleWarning,
      t.loc
    )
    if !t.typEx.isInstanceOf[AggregateTypeExpression] then {
      checkTypeExpression(t.typEx, t, parents)
    }
    checkDescription(t)
  }

  private def validateConstant(
    c: Constant,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, c)
    checkDescription(c)
  }

  private def validateState(
    s: State,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, s)
    checkDescription(s)
    checkRefAndExamine[Type](s.typ, parents) { (typ: Type) =>
      typ.typEx match {
        case agg: AggregateTypeExpression =>
          if agg.fields.isEmpty && !s.isEmpty then {
            messages.addError(
              s.typ.loc,
              s"${s.identify} references an empty aggregate but must have " +
                s"at least one field"
            )
          }
        case _ =>
      }
      check(
        typ.id.value != s.id.value,
        s"${s.identify} and ${typ.identify} must not have the same name so path resolution can succeed",
        Messages.Error,
        s.loc
      )
    }
    checkDescription(s)
  }

  private def validateFunction(
    f: Function,
    parents: Parents
  ): Unit = {
    checkContainer(parents, f)
    checkDescriptions(f, f.contents)
  }

  private def validateHandler(
    h: Handler,
    parents: Parents
  ): Unit = {
    checkContainer(parents, h)
    checkDescriptions(h, h.contents)
  }

  private def validateInclude[T <: RiddlValue](i: Include[T]): Unit = {
    check(i.contents.nonEmpty, "Include has no included content", Messages.Error, i.loc)
    check(i.origin.nonEmpty, "Include has no source provided", Messages.Error, i.loc)
  }

  private def validateEntity(
    entity: Entity,
    parents: Parents
  ): Unit = {
    checkContainer(parents, entity)
    if entity.states.isEmpty && !entity.isEmpty then {
      messages.add(
        Message(
          entity.loc,
          s"${entity.identify} must define at least one state",
          Messages.MissingWarning
        )
      )
    }
    if entity.handlers.nonEmpty && entity.handlers.forall(_.clauses.isEmpty) then {
      messages.add(
        Message(entity.loc, s"${entity.identify} has only empty handlers", Messages.MissingWarning)
      )
    }
    if entity.hasOption("finite-state-machine") && entity.states.sizeIs < 2 then {
      messages.add(
        Message(
          entity.loc,
          s"${entity.identify} is declared as an fsm, but doesn't have at least two states",
          Messages.Error
        )
      )
    }
    if entity.states.nonEmpty && entity.handlers.isEmpty then {
      messages.add(
        Message(
          entity.loc,
          s"${entity.identify} has ${entity.states.size} state${
              if entity.states.size != 1 then "s"
              else ""
            } but no handlers.",
          Messages.Error
        )
      )
    }
    checkDescriptions(entity, entity.contents)
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
            auc.usecase == RecordCase
          case _ => false
        }
      },
      s"${projector.identify} lacks a required ${RecordCase.useCase} definition.",
      Messages.Error,
      projector.loc
    )
    check(
      projector.handlers.length == 1,
      s"${projector.identify} must have exactly one Handler but has ${projector.handlers.length}",
      Messages.Error,
      projector.loc
    )
    checkDescriptions(projector, projector.contents)
  }

  private def validateRepository(
    repository: Repository,
    parents: Parents
  ): Unit = {
    checkContainer(parents, repository)
    checkDescriptions(repository, repository.contents)
    checkNonEmpty(
      repository.contents.filter[Schema],
      "schema",
      repository,
      repository.loc,
      MissingWarning,
      required = false
    )
  }

  private def validateAdaptor(
    adaptor: Adaptor,
    parents: Parents
  ): Unit = {
    parents.headOption match {
      case Some(c: Context) =>
        checkContainer(parents, adaptor)
        resolvePath(adaptor.context.pathId, parents).map { (target: Context) =>
          if target == c then {
            val message =
              s"${adaptor.identify} may not specify a target context that is " +
                s"the same as the containing ${c.identify}"
            messages.addError(adaptor.loc, message)
          }
        }
        checkDescriptions(adaptor, adaptor.contents)
      case None | Some(_) =>
        messages.addError(adaptor.loc, "Adaptor not contained within Context")
    }
  }

  private def validateStreamlet(
    streamlet: Streamlet,
    parents: Parents
  ): Unit = {
    checkContainer(parents, streamlet)
    checkDescriptions(streamlet, streamlet.contents)
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
      domain.loc
    )
    checkDescriptions(domain, domain.contents)
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
      saga.loc
    )
    check(
      saga.nonEmpty && saga.sagaSteps.size >= 2 && saga.sagaSteps.map(_.id.value).allUnique,
      "Saga step names must all be distinct",
      Messages.Error,
      saga.loc
    )
    checkDescriptions(saga, saga.contents)
  }

  private def validateSagaStep(
    s: SagaStep,
    parents: Parents
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
    parents: Parents
  ): Unit = {
    checkContainer(parents, c)
    checkDescriptions(c, c.contents)
  }

  private def validateEpic(
    epic: Epic,
    parents: Parents
  ): Unit = {
    checkContainer(parents, epic)
    if epic.userStory.isEmpty then {
      messages.addMissing(epic.loc, s"${epic.identify} is missing a user story")
    }
    checkDescriptions(epic, epic.contents)
  }

  private def validateApplication(
    app: Application,
    parents: Parents
  ): Unit = {
    checkContainer(parents, app)
    if app.groups.isEmpty then {
      messages.addMissing(app.loc, s"${app.identify} should have a group")
    }
    checkDescriptions(app, app.contents)
  }

  private def validateGroup(
    grp: Group,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, grp)
    checkDescriptions(grp, grp.contents)
  }

  private def validateInput(
    input: Input,
    parents: Parents
  ): Unit = {
    val parentsSeq = parents
    checkDefinition(parentsSeq, input)
    checkTypeRef(input.putIn, parentsSeq)
    checkDescriptions(input, input.contents)
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
    checkDescriptions(output, output.contents)
  }

  private def validateContainedGroup(
    containedGroup: ContainedGroup,
    parents: Parents
  ): Unit = {
    checkDefinition(parents, containedGroup)
    checkRef[Group](containedGroup.group, parents)
    checkDescription(containedGroup)
  }

  private def validateUser(
    user: User,
    parents: Parents
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
    parents: Parents
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
        case gi: GenericInteraction =>
          gi match {
            case vi: VagueInteraction =>
              check(
                vi.relationship.nonEmpty,
                "Vague Interactions should have a non-empty description",
                Messages.MissingWarning,
                vi.relationship.loc
              )
            case smi: SendMessageInteraction =>
              checkPathRef[Definition](smi.from.pathId, parents)
              checkMessageRef(smi.message, parents, Seq(smi.message.messageKind))
              checkPathRef[Definition](smi.to.pathId, parents)
            case fou: DirectUserToURLInteraction =>
              checkPathRef[User](fou.from.pathId, parents)
            case is: TwoReferenceInteraction =>
              checkPathRef[Definition](is.from.pathId, parents)
              checkPathRef[Definition](is.to.pathId, parents)
              if is.relationship.isEmpty then {
                messages.addMissing(
                  is.loc,
                  s"Interactions must have a non-empty relationship"
                )
              }
          }
        case _: Comment => ()
      }
    }
    if uc.nonEmpty then {
      if uc.contents.isEmpty then
        messages.addMissing(
          uc.loc,
          s"${uc.identify} doesn't define any interactions"
        )
    }
    checkDescriptions(uc, uc.contents)
  }

  private def validateArbitraryInteraction(
    origin: Option[Definition],
    destination: Option[Definition],
    parents: Parents
  ): Unit = {
    val maybeMessage: Option[Message] = origin match {
      case Some(o) if o.isVital =>
        destination match {
          case Some(d) if d.isInstanceOf[ApplicationRelated] =>
            d match {
              case output @ Output(loc, _, id, _, putOut, _) =>
                putOut match {
                  case typRef: TypeRef =>
                    checkTypeRef(typRef, parents) match {
                      case Some(Type(_, _, typEx, _, _)) if typEx.isContainer =>
                        typEx match {
                          case ate: AggregateUseCaseTypeExpression
                              if ate.usecase == EventCase || ate.usecase == ResultCase =>
                            None // events and results are permitted
                          case ty: TypeExpression => // everything else is not
                            Some(
                              error(
                                s"${output.identify} showing ${typRef.format} of type ${ty.format} is invalid " +
                                  s" because ${o.identify} is a vital definition which can only send Events and Results",
                                loc
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
      case Some(o) if o.isInstanceOf[ApplicationRelated] =>
        destination match {
          case Some(d) if d.isVital =>
            o match {
              case input @ Input(loc, _, id, _, putIn, _) =>
                checkTypeRef(putIn, parents) match {
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

  private def validateInteraction(interaction: Interaction, parents: Parents): Unit = {
    val useCase = parents.head
    checkDescription(interaction)
    interaction match {
      case SelfInteraction(_, from, _, _, _) =>
        checkRef[Definition](from, parents)
      case DirectUserToURLInteraction(_, user: UserRef, _, _, _) =>
        checkRef[User](user, parents)
      case FocusOnGroupInteraction(_, user: UserRef, group: GroupRef, _, _) =>
        checkRef[Group](group, parents)
        checkRef[User](user, parents)
      case SelectInputInteraction(_, user: UserRef, inputRef: InputRef, _, _) =>
        checkRef[User](user, parents)
        checkRef[Input](inputRef, parents)
      case TakeInputInteraction(_, user: UserRef, inputRef: InputRef, _, _) =>
        checkRef[User](user, parents)
        checkRef[Input](inputRef, parents)
      case ArbitraryInteraction(_, from, _, to, _, _) =>
        checkRef[Definition](from, parents)
        checkRef[Definition](to, parents)
        val origin = resolution.refMap.definitionOf[Definition](from.pathId, parents.head)
        val destination = resolution.refMap.definitionOf[Definition](to.pathId, parents.head)
        validateArbitraryInteraction(origin, destination, parents)
      case ShowOutputInteraction(_, from: OutputRef, _, to: UserRef, _, _) =>
        checkRef[Output](from, parents)
        checkRef[User](to, parents)
      case SendMessageInteraction(_, from, msg, to, _, _) =>
        checkMessageRef(msg, parents, Seq(msg.messageKind))
        checkRef[Definition](from, parents)
        checkRef[Processor[?]](to, parents)
      case _: VagueInteraction =>
      // Nothing else to validate
      case _: OptionalInteractions | _: ParallelInteractions | _: SequentialInteractions =>
      // These are all just containers of other interactions, not needing further validation
    }
  }

}
