/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.ast.At

import java.util.regex.PatternSyntaxException
import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.util.control.NonFatal
import scala.util.matching.Regex
import org.apache.commons.lang3.exception.ExceptionUtils

import scala.annotation.tailrec
import scala.annotation.unused
import scala.collection.mutable

/** Validates an AST */
object Validation {

  /** The result of the validation process, yielding information useful to
    * subsequent passes.
    * @param messages
    *   The messages generated during validation that were not fatal
    * @param root
    *   The RootContainer passed into the validate method.
    * @param symTab
    *   The SymbolTable generated during the validation
    * @param uses
    *   The mapping of definitions to the definitions they use
    * @param usedBy
    *   The mapping of definitions to the definitions they are used by
    */
  case class Result(
    messages: Messages,
    root: RootContainer,
    symTab: SymbolTable,
    uses: Map[Definition, Seq[Definition]],
    usedBy: Map[Definition, Seq[Definition]])

  /** Run a validation algorithm in a single pass through the AST that looks for
    * inconsistencies, missing definitions, style violations, errors, etc.
    * @param root
    *   The result of parsing as a RootContainer. This contains the AST that is
    *   validated.
    * @param commonOptions
    *   THe options to use when validating, indicating the verbosity level, etc.
    * @return
    *   A Validation.Result is returned containing the root passed in, the
    *   validation messages generated and analytical results.
    */
  def validate(
    root: RootContainer,
    commonOptions: CommonOptions = CommonOptions()
  ): Result = {
    val symTab = SymbolTable(root)
    val state = ValidationState(symTab, root, commonOptions)
    val parents = mutable.Stack.empty[Definition]
    val endState = {
      try {
        val s1 = state.validateDefinitions(root, parents)
        val s2 = s1.checkUnused()
        val s3 = s2.checkOverloads(symTab)
        s3
      } catch {
        case NonFatal(xcptn) =>
          val message = ExceptionUtils.getRootCauseStackTrace(xcptn)
            .mkString("\n")
          state.addSevere(At.empty, message)
      }
    }
    Result(
      endState.messages.sortBy(_.loc),
      root,
      symTab,
      endState.uses.toMap,
      endState.usedBy.toMap
    )
  }

  case class ValidationState(
    symbolTable: SymbolTable,
    root: Definition = RootContainer.empty,
    commonOptions: CommonOptions = CommonOptions())
      extends Folding.PathResolutionState[ValidationState] {

    val uses: mutable.HashMap[Definition, Seq[Definition]] = mutable.HashMap
      .empty[Definition, Seq[Definition]]
    val usedBy: mutable.HashMap[Definition, Seq[Definition]] = mutable.HashMap
      .empty[Definition, Seq[Definition]]

    var entities: Seq[Entity] = Seq.empty[Entity]
    var types: Seq[Type] = Seq.empty[Type]
    var functions: Seq[Function] = Seq.empty[Function]

    def associateUsage(user: Definition, use: Definition): Unit = {
      val used = uses.getOrElse(user, Seq.empty[Definition])
      val new_used = used :+ use
      uses.update(user, new_used)

      val usages = usedBy.getOrElse(use, Seq.empty[Definition])
      val new_usages = usages :+ user
      usedBy.update(use, new_usages)
    }

    def validateDefinitions(
      definition: Definition,
      parents: mutable.Stack[Definition]
    ): ValidationState = {
      // Capture current parse state including now the definition as the
      // top element of the parent stack
      definition match {
        case leaf: LeafDefinition => validateADefinition(leaf, parents)
        case i: Include[Definition] @unchecked => i.contents.foldLeft(this) {
            case (n, d: LeafDefinition) => n.validateADefinition(d, parents)
            case (n, cd: Definition)    => n.validateDefinitions(cd, parents)
          }
        case container: Definition =>
          validateADefinition(container, parents)
          parents.push(container)
          val st = container.contents.foldLeft(this) { (st, defn) =>
            st.validateDefinitions(defn, parents)
          }
          parents.pop()
          st
      }
    }

    def validateADefinition(
      definition: Definition,
      definitionParents: mutable.Stack[Definition]
    ): ValidationState = {
      val parents = definitionParents.toSeq
      definition match {
        case leaf: LeafDefinition => leaf match {
            case f: Field        => validateField(f, parents)
            case e: Example      => validateExample(e, parents)
            case e: Enumerator   => validateEnumerator(e, parents)
            case i: Invariant    => validateInvariant(i, parents)
            case t: Term         => validateTerm(t, parents)
            case p: Pipe         => validatePipe(p, parents)
            case i: Inlet        => validateInlet(i, parents)
            case o: Outlet       => validateOutlet(o, parents)
            case ij: InletJoint  => validateInletJoint(ij, parents)
            case oj: OutletJoint => validateOutletJoint(oj, parents)
            case a: Author       => validateAuthorInfo(a, parents)
            case sa: Actor       => validateActor(sa, parents)
            case sc: StoryCase   => validateStoryCase(sc, parents)
          }
        case ad: ApplicationDefinition => ad match {
            case typ: Type   => validateType(typ, parents)
            case grp: Group  => validateGroup(grp, parents)
            case h: Handler  => validateHandler(h, parents)
            case in: Input   => validateInput(in, parents)
            case out: Output => validateOutput(out, parents)
            case t: Term     => validateTerm(t, parents)
            case i: Include[ApplicationDefinition] @unchecked =>
              validateInclude(i)
          }
        case ed: EntityDefinition => ed match {
            case t: Type      => validateType(t, parents)
            case s: State     => validateState(s, parents)
            case h: Handler   => validateHandler(h, parents)
            case f: Function  => validateFunction(f, parents)
            case i: Invariant => validateInvariant(i, parents)
            case t: Term      => validateTerm(t, parents)
            case i: Include[EntityDefinition] @unchecked => validateInclude(i)
          }
        case cd: ContextDefinition => cd match {
            case t: Type       => validateType(t, parents)
            case h: Handler    => validateHandler(h, parents)
            case f: Function   => validateFunction(f, parents)
            case e: Entity     => validateEntity(e, parents)
            case a: Adaptor    => validateAdaptor(a, parents)
            case p: Processor  => validateProcessor(p, parents)
            case p: Projection => validateProjection(p, parents)
            case r: Repository => validateRepository(r, parents)
            case t: Term       => validateTerm(t, parents)
            case p: Pipe       => validatePipe(p, parents)
            case s: Saga       => validateSaga(s, parents)
            case i: Include[ContextDefinition] @unchecked => validateInclude(i)
          }
        case dd: DomainDefinition => dd match {
            case a: Application => validateApplication(a, parents)
            case t: Type        => validateType(t, parents)
            case c: Context     => validateContext(c, parents)
            case d: Domain      => validateDomain(d, parents)
            case s: Story       => validateStory(s, parents)
            case p: Plant       => validatePlant(p, parents)
            case t: Term        => validateTerm(t, parents)
            case a: Author      => validateAuthorInfo(a, parents)
            case a: Actor       => validateActor(a, parents)
            case i: Include[DomainDefinition] @unchecked => validateInclude(i)
          }
        case hd: HandlerDefinition => hd match {
            case oc: OnClause => validateOnClause(oc, parents)
          }
        case ad: AdaptorDefinition => ad match {
            case h: Handler => validateHandler(h, parents)
            case t: Term    => validateTerm(t, parents)
            case i: Include[AdaptorDefinition] @unchecked => validateInclude(i)
          }
        case pd: PlantDefinition => pd match {
            case t: Term         => validateTerm(t, parents)
            case ij: InletJoint  => validateInletJoint(ij, parents)
            case oj: OutletJoint => validateOutletJoint(oj, parents)
            case p: Processor    => validateProcessor(p, parents)
            case p: Pipe         => validatePipe(p, parents)
            case i: Include[PlantDefinition] @unchecked => validateInclude(i)
          }
        case ss: SagaStep     => validateSagaStep(ss, parents)
        case _: RootContainer => this // ignore
      }
    }

    def validateTerm(t: Term, parents: Seq[Definition]): ValidationState = {
      this.checkDefinition(parents, t).checkDescription(t)
    }

    def validateEnumerator(
      e: Enumerator,
      parents: Seq[Definition]
    ): ValidationState = {
      this.checkDefinition(parents, e).checkDescription(e)
    }

    def validateField(f: Field, parents: Seq[Definition]): ValidationState = {
      checkDefinition(parents, f)
        .addIf(f.id.value.matches("^[^a-z].*"))(Message(
          f.id.loc,
          "Field names should begin with a lower case letter",
          StyleWarning
        )).checkTypeExpression(f.typeEx, f, parents).checkDescription(f)
    }

    def validateExample(
      e: Example,
      parents: Seq[Definition]
    ): ValidationState = {
      checkDefinition(parents, e).checkExample(e, parents).checkDescription(e)
    }

    def validateInvariant(
      i: Invariant,
      parents: Seq[Definition]
    ): ValidationState = {
      checkDefinition(parents, i).checkOption(i.expression, "condition", i) {
        (st, expr) => st.checkExpression(expr, i, parents)
      }.checkDescription(i)
    }

    def validatePipe(p: Pipe, parents: Seq[Definition]): ValidationState = {
      checkDefinition(parents, p)
        .checkOption(p.transmitType, "transmit type", p) { (st, typeRef) =>
          st.checkPathRef[Type](typeRef.pathId, p, parents)()()
        }.checkDescription(p)
    }

    def validateInlet(i: Inlet, parents: Seq[Definition]): ValidationState = {
      checkDefinition(parents, i).checkRef[Type](i.type_, i, parents)
        .checkOption(i.entity, "entity reference", i) { (st, er) =>
          st.checkRef[Entity](er, i, parents)
        }.checkDescription(i)
    }

    def validateOutlet(o: Outlet, parents: Seq[Definition]): ValidationState = {
      checkDefinition(parents, o).checkRef[Type](o.type_, o, parents)
        .checkOption(o.entity, "entity reference", o) { (st, er) =>
          st.checkRef[Entity](er, o, parents)
        }.checkDescription(o)
    }

    def validateInletJoint(
      ij: InletJoint,
      parents: Seq[Definition]
    ): ValidationState = {
      checkDefinition(parents, ij)
        .checkPathRef[Pipe](ij.pipe.pathId, ij, parents)()()
        .checkPathRef[Inlet](ij.inletRef.pathId, ij, parents)()()
        .checkDescription(ij)
    }

    def validateOutletJoint(
      oj: OutletJoint,
      parents: Seq[Definition]
    ): ValidationState = {
      checkDefinition(parents, oj)
        .checkPathRef[Pipe](oj.pipe.pathId, oj, parents)()()
        .checkPathRef[Outlet](oj.outletRef.pathId, oj, parents)()()
        .checkDescription(oj)
    }

    def validateAuthorInfo(
      ai: Author,
      parents: Seq[Definition]
    ): ValidationState = {
      checkDefinition(parents, ai)
        .checkNonEmptyValue(ai.name, "name", ai, Error, required = true)
        .checkNonEmptyValue(ai.email, "email", ai, Error, required = true)
        .checkDescription(ai)
    }

    def validateType(t: Type, parents: Seq[Definition]): ValidationState = {
      types = types :+ t
      checkDefinition(parents, t).check(
        t.id.value.head.isUpper,
        s"${t.identify} should start with a capital letter",
        StyleWarning,
        t.loc
      ).checkThat(!t.typ.isInstanceOf[AggregateTypeExpression]) { vs =>
        vs.checkTypeExpression(t.typ, t, parents)
      }.checkDescription(t)
    }

    def validateState(s: State, parents: Seq[Definition]): ValidationState = {
      checkContainer(parents, s)
        .addIf(s.aggregation.fields.isEmpty && !s.isEmpty) {
          Message(
            s.aggregation.loc,
            s"${s.identify} must define at least one field"
          )
        }.addIf(s.handlers.isEmpty && !s.isEmpty) {
          Message(s.loc, s"${s.identify} must define a handler")
        }.checkDescription(s)
    }

    def validateFunction(
      f: Function,
      parents: Seq[Definition]
    ): ValidationState = {
      functions = functions :+ f
      checkContainer(parents, f).checkOptions[FunctionOption](f.options, f.loc)
        .checkDescription(f)
    }

    def validateHandler(
      h: Handler,
      parents: Seq[Definition]
    ): ValidationState = { checkContainer(parents, h).checkDescription(h) }

    def validateOnClause(
      oc: OnClause,
      parents: Seq[Definition]
    ): ValidationState = {
      (oc match {
        case ooc: OnOtherClause => this.checkDefinition(parents, ooc)
        case omc @ OnMessageClause(_, msg, from, _, _, _) => this
            .checkDefinition(parents, omc).checkThat(msg.nonEmpty) { st =>
              st.checkMessageRef(msg, oc, parents, msg.messageKind)
            }.checkThat(from.nonEmpty) { st =>
              st.checkRef(from.get, oc, parents)
            }
      }).checkDescription(oc)
    }

    def validateInclude[T <: Definition](i: Include[T]): ValidationState = {
      check(i.nonEmpty, "Include has no included content", Error, i.loc)
        .check(i.path.nonEmpty, "Include has no path provided", Error, i.loc)
        .step { s =>
          if (i.path.nonEmpty) {
            s.check(
              i.path.get.toString.nonEmpty,
              "Include path provided is empty",
              Error,
              i.loc
            )
          } else { s }
        }
    }

    def validateEntity(e: Entity, parents: Seq[Definition]): ValidationState = {
      this.entities = this.entities :+ e
      checkContainer(parents, e).checkOptions[EntityOption](e.options, e.loc)
        .addIf(e.states.isEmpty && !e.isEmpty) {
          Message(
            e.loc,
            s"${e.identify} must define at least one state",
            MissingWarning
          )
        }.addIf(e.handlers.nonEmpty && e.handlers.forall(_.clauses.isEmpty)) {
          Message(
            e.loc,
            s"${e.identify} has only empty handlers",
            MissingWarning
          )
        }
        .addIf(e.hasOption[EntityIsFiniteStateMachine] && e.states.sizeIs < 2) {
          Message(
            e.loc,
            s"${e.identify} is declared as an fsm, but doesn't have at least two states",
            Error
          )
        }.checkDescription(e)
    }

    def validateProjection(
      p: Projection,
      parents: Seq[Definition]
    ): ValidationState = {
      checkContainer(parents, p).checkAggregation(p.aggregation)
        .checkDescription(p)
    }

    def validateRepository(
      r: Repository,
      parents: Seq[Definition]
    ): ValidationState = { checkContainer(parents, r).checkDescription(r) }
    def validateAdaptor(
      a: Adaptor,
      parents: Seq[Definition]
    ): ValidationState = {
      parents.headOption match {
        case Some(c: Context) =>
          val s1 = checkContainer(parents, a)
          val targetContext = resolvePath(a.context.pathId, parents)()()
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
          add(error("Adaptor not contained within Context", a.loc))
      }
    }

    def validateProcessor(
      p: Processor,
      parents: Seq[Definition]
    ): ValidationState = {
      checkContainer(parents, p).checkProcessorKind(p).checkDescription(p)
    }

    def validateDomain(d: Domain, parents: Seq[Definition]): ValidationState = {
      checkContainer(parents, d).checkDescription(d)
    }

    def validateSaga(s: Saga, parents: Seq[Definition]): ValidationState = {
      checkContainer(parents, s).checkDescription(s)
    }

    def validateSagaStep(
      s: SagaStep,
      parents: Seq[Definition]
    ): ValidationState = {
      checkContainer(parents, s).check(
        s.doAction.getClass == s.undoAction.getClass,
        "The primary action and revert action must be the same shape",
        Error,
        s.loc
      ).checkExamples(s.doAction, s +: parents)
        .checkExamples(s.undoAction, s +: parents).checkDescription(s)
    }

    def validateContext(
      c: Context,
      parents: Seq[Definition]
    ): ValidationState = {
      checkContainer(parents, c).checkOptions[ContextOption](c.options, c.loc)
        .checkDescription(c)
    }

    def validateStory(
      s: Story,
      parents: Seq[Definition]
    ): ValidationState = {
      checkContainer(parents, s).checkThat(s.userStory.isEmpty) {
        vs: ValidationState =>
          vs.addMissing(s.loc, s"${s.identify} is missing a user story")
      }.checkExamples(s.examples, parents).checkDescription(s)
    }

    def validateApplication(
      app: Application,
      parents: Seq[AST.Definition]
    ): ValidationState = {
      checkContainer(parents, app).checkThat(app.groups.isEmpty) {
        vs: ValidationState =>
          vs.addMissing(app.loc, s"${app.identify} should have a group")
      }.checkDescription(app)
    }

    def validateGroup(
      grp: Group,
      parents: Seq[AST.Definition]
    ): ValidationState = { checkDefinition(parents, grp) }

    def validateInput(
      in: Input,
      parents: Seq[AST.Definition]
    ): ValidationState = {
      checkDefinition(parents, in)
        .checkMessageRef(in.putIn, in, parents, CommandKind)
        .checkDescription(in)
    }

    def validateOutput(
      out: Output,
      parents: Seq[AST.Definition]
    ): ValidationState = {
      checkDefinition(parents, out)
        .checkMessageRef(out.putOut, out, parents, ResultKind)
        .checkDescription(out)

    }

    def validateActor(
      @unused actor: Actor,
      @unused parents: Seq[Definition]
    ): ValidationState = {
      checkDefinition(parents, actor).checkThat(actor.is_a.isEmpty) { vs =>
        vs.addMissing(
          actor.loc,
          s"${actor.identify} is missing its role kind ('is a')"
        )
      }.checkDescription(actor)
      this
    }

    def validateStoryCase(
      sc: StoryCase,
      parents: Seq[Definition]
    ): ValidationState = {
      checkDefinition(parents, sc).stepIf(sc.interactions.nonEmpty) { st =>
        sc.interactions.foldLeft(st) { (st, step) =>
          step match {
            case par: ParallelGroup => st
                .stepIf(par.contents.isEmpty) { vs: ValidationState =>
                  vs.addMissing(
                    par.loc,
                    "Parallel interaction should not be empty"
                  )
                }
            case opt: OptionalGroup => st
                .stepIf(opt.contents.isEmpty) { vs: ValidationState =>
                  vs.addMissing(
                    opt.loc,
                    "Optional interaction should not be empty"
                  )
                }
            case is: InteractionStep => st
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
      }.stepIf(sc.nonEmpty) { vs =>
        vs.checkThat(sc.interactions.isEmpty)(
          _.addMissing(
            sc.loc,
            s"${sc.identify} doesn't define any interactions"
          )
        )
      }.checkDescription(sc)
    }

    def validatePlant(
      p: Plant,
      parents: Seq[Definition]
    ): ValidationState = { checkContainer(parents, p).checkDescription(p) }

    def checkUnused(): ValidationState = {
      if (commonOptions.showUnusedWarnings) {
        def hasUsages(definition: Definition): Boolean = {
          val result = usedBy.get(definition) match {
            case None        => false
            case Some(users) => users.nonEmpty
          }
          result
        }
        for { e <- entities } {
          check(hasUsages(e), s"${e.identify} is unused", Warning, e.loc)
        }
        for { t <- types } {
          check(hasUsages(t), s"${t.identify} is unused", Warning, t.loc)
        }
        for { f <- functions } {
          check(hasUsages(f), s"${f.identify} is unused", Warning, f.loc)
        }
      }
      this
    }

    def checkOverloads(
      symbolTable: SymbolTable
    ): ValidationState = {
      symbolTable.foreachOverloadedSymbol { defs: Seq[Seq[Definition]] =>
        this.checkSequence(defs) { (s, defs2) =>
          if (defs2.sizeIs == 2) {
            val first = defs2.head
            val last = defs2.last
            s.addStyle(
              last.loc,
              s"${last.identify} overloads ${first.identifyWithLoc}"
            )
          } else if (defs2.sizeIs > 2) {
            val first = defs2.head
            val tail = defs2.tail.map(d => d.identifyWithLoc).mkString(s",\n  ")
            s.addStyle(first.loc, s"${first.identify} overloads:\n  $tail")
          } else { s }
        }
      }
    }

    def parentOf(
      definition: Definition
    ): Container[Definition] = {
      symbolTable.parentOf(definition).getOrElse(RootContainer.empty)
    }

    def lookup[T <: Definition: ClassTag](
      id: Seq[String]
    ): List[T] = { symbolTable.lookup[T](id) }

    def addIf(predicate: Boolean)(msg: => Message): ValidationState = {
      if (predicate) add(msg) else this
    }

    private val vowels: Regex = "[aAeEiIoOuU]".r

    def article(thing: String): String = {
      val article = if (vowels.matches(thing.substring(0, 1))) "an" else "a"
      s"$article $thing"
    }

    def check(
      predicate: Boolean = true,
      message: => String,
      kind: KindOfMessage,
      loc: At
    ): ValidationState = {
      if (!predicate) { add(Message(loc, message, kind)) }
      else { this }
    }

    def checkThat(
      predicate: Boolean = true
    )(f: ValidationState => ValidationState
    ): ValidationState = {
      if (predicate) { f(this) }
      else { this }
    }

    def checkIdentifierLength[T <: Definition](
      d: T,
      min: Int = 3
    ): ValidationState = {
      if (d.id.value.nonEmpty && d.id.value.length < min) {
        addStyle(
          d.id.loc,
          s"${d.kind} identifier '${d.id.value}' is too short. The minimum length is $min"
        )
      } else { this }
    }

    def checkPattern(p: Pattern): ValidationState = {
      try {
        val compound = p.pattern.map(_.s).reduce(_ + _)
        java.util.regex.Pattern.compile(compound)
        this
      } catch {
        case x: PatternSyntaxException => add(Message(p.loc, x.getMessage))
      }
    }

    def checkEnumeration(
      enumerators: Seq[Enumerator]
    ): ValidationState = {
      checkSequence(enumerators) { case (state, enumerator) =>
        val id = enumerator.id
        state.checkIdentifierLength(enumerator).check(
          id.value.head.isUpper,
          s"Enumerator '${id.format}' must start with upper case",
          StyleWarning,
          id.loc
        ).checkDescription(enumerator)
      }
    }

    def checkAlternation(
      alternation: AST.Alternation,
      typeDef: Definition,
      parents: Seq[Definition]
    ): ValidationState = {
      checkSequence(alternation.of) { case (state, typex) =>
        state.checkTypeExpression(typex, typeDef, parents)
      }
    }

    def checkRangeType(
      rt: RangeType
    ): ValidationState = {
      this.check(
        rt.min >= BigInt.long2bigInt(Long.MinValue),
        "Minimum value might be too small to store in a Long",
        Warning,
        rt.loc
      ).check(
        rt.max <= BigInt.long2bigInt(Long.MaxValue),
        "Maximum value might be too large to store in a Long",
        Warning,
        rt.loc
      )
    }

    def checkAggregation(
      aggregation: Option[Aggregation]
    ): ValidationState = {
      aggregation match {
        case None              => this
        case Some(aggregation) => checkAggregation(aggregation)
      }
    }

    def checkAggregation(
      agg: Aggregation
    ): ValidationState = {
      checkSequence(agg.fields) { case (state, field) =>
        state.checkIdentifierLength(field).check(
          field.id.value.head.isLower,
          "Field names in aggregates should start with a lower case letter",
          StyleWarning,
          field.loc
        ).checkDescription(field)
      }
    }

    def checkMessageType(
      mt: MessageType,
      typeDef: Definition,
      parents: Seq[Definition]
    ): ValidationState = {
      val kind = mt.messageKind.kind
      checkSequence(mt.fields) { case (state, field) =>
        state.checkIdentifierLength(field).check(
          field.id.value.head.isLower,
          s"Field names in $kind messages should start with a lower case letter",
          StyleWarning,
          field.loc
        ).checkTypeExpression(field.typeEx, typeDef, parents)
          .checkDescription(field)
      }
    }

    def checkMapping(
      mapping: AST.Mapping,
      typeDef: Definition,
      parents: Seq[Definition]
    ): ValidationState = {
      this.checkTypeExpression(mapping.from, typeDef, parents)
        .checkTypeExpression(mapping.to, typeDef, parents)
    }

    def checkTypeExpression[TD <: Definition](
      typ: TypeExpression,
      defn: Definition,
      parents: Seq[Definition]
    ): ValidationState = {
      typ match {
        case AliasedTypeExpression(_, id: PathIdentifier) =>
          checkPathRef[Type](id, defn, parents)()()
        case agg: Aggregation            => checkAggregation(agg)
        case mt: MessageType             => checkMessageType(mt, defn, parents)
        case alt: Alternation            => checkAlternation(alt, defn, parents)
        case mapping: Mapping            => checkMapping(mapping, defn, parents)
        case rt: RangeType               => checkRangeType(rt)
        case p: Pattern                  => checkPattern(p)
        case Enumeration(_, enumerators) => checkEnumeration(enumerators)
        case Optional(_, tye)   => checkTypeExpression(tye, defn, parents)
        case OneOrMore(_, tye)  => checkTypeExpression(tye, defn, parents)
        case ZeroOrMore(_, tye) => checkTypeExpression(tye, defn, parents)
        case SpecificRange(_, typex: TypeExpression, min, max) =>
          checkTypeExpression(typex, defn, parents)
          check(
            min >= 0,
            "Minimum cardinality must be non-negative",
            Error,
            typ.loc
          )
          check(
            max >= 0,
            "Maximum cardinality must be non-negative",
            Error,
            typ.loc
          )
          check(
            min < max,
            "Minimum cardinality must be less than maximum cardinality",
            Error,
            typ.loc
          )
        case UniqueId(_, pid) => checkPathRef[Entity](pid, defn, parents)()()
        case EntityReferenceTypeExpression(_, pid) =>
          checkPathRef[Entity](pid, defn, parents)()()
        case _: PredefinedType => this // nothing needed
        case _: TypeRef        => this // handled elsewhere
        case x =>
          require(requirement = false, s"Failed to match definition $x")
          this
      }
    }

    private type SingleMatchValidationFunction = (
      /* state:*/ ValidationState,
      /* expectedClass:*/ Class[?],
      /* pathIdSought:*/ PathIdentifier,
      /* foundClass*/ Class[? <: Definition],
      /* definitionFound*/ Definition
    ) => ValidationState

    private type MultiMatchValidationFunction = (
      /* state:*/ ValidationState,
      /* pid: */ PathIdentifier,
      /* list: */ List[(Definition, Seq[Definition])]
    ) => Seq[Definition]

    private val nullSingleMatchingValidationFunction
      : SingleMatchValidationFunction = (state, _, _, _, _) => { state }

    private def defaultSingleMatchValidationFunction(
      state: ValidationState,
      expectedClass: Class[?],
      pid: PathIdentifier,
      foundClass: Class[? <: Definition],
      @unused definitionFound: Definition
    ): ValidationState = {
      state.check(
        expectedClass.isAssignableFrom(foundClass),
        s"'${pid.format}' was expected to be ${article(expectedClass.getSimpleName)} but is " +
          s"${article(foundClass.getSimpleName)}.",
        Error,
        pid.loc
      )
    }

    private def defaultMultiMatchValidationFunction[T <: Definition: ClassTag](
      state: ValidationState,
      pid: PathIdentifier,
      list: List[(Definition, Seq[Definition])]
    ): Seq[Definition] = {
      // Extract all the definitions that were found
      val definitions = list.map(_._1)
      val allDifferent = definitions.map(_.kind).distinct.sizeIs ==
        definitions.size
      val expectedClass = classTag[T].runtimeClass
      if (allDifferent || definitions.head.isImplicit) {
        // pick the one that is the right type or the first one
        list.find(_._1.getClass == expectedClass) match {
          case Some((defn, parents)) => defn +: parents
          case None                  => list.head._1 +: list.head._2
        }
      } else {
        state.addError(
          pid.loc,
          s"""Path reference '${pid.format}' is ambiguous. Definitions are:
             |${formatDefinitions(list)}""".stripMargin
        )
        Seq.empty[Definition]
      }
    }

    private def formatDefinitions[T <: Definition](
      list: List[(T, SymbolTable#Parents)]
    ): String = {
      list.map { case (definition, parents) =>
        "  " + parents.reverse.map(_.id.value).mkString(".") + "." +
          definition.id.value + " (" + definition.loc + ")"
      }.mkString("\n")
    }

    private def notResolved[T <: Definition: ClassTag](
      pid: PathIdentifier,
      container: Definition,
      kind: Option[String]
    ): Unit = {
      val tc = classTag[T].runtimeClass
      val message = s"Path '${pid.format}' was not resolved," +
        s" in ${container.identify}"
      val referTo = if (kind.nonEmpty) kind.get else tc.getSimpleName
      addError(
        pid.loc,
        message + {
          if (referTo.nonEmpty) s", but should refer to ${article(referTo)}"
          else ""
        }
      )
    }

    def checkPathRef[T <: Definition: ClassTag](
      pid: PathIdentifier,
      container: Definition,
      parents: Seq[Definition],
      kind: Option[String] = None
    )(single: SingleMatchValidationFunction =
        defaultSingleMatchValidationFunction
    )(multi: MultiMatchValidationFunction = defaultMultiMatchValidationFunction
    ): ValidationState = {
      val tc = classTag[T].runtimeClass
      if (pid.value.isEmpty) {
        val message =
          s"An empty path cannot be resolved to ${article(tc.getSimpleName)}"
        addError(pid.loc, message)
      } else {
        val pars =
          if (parents.head != container) container +: parents else parents
        val result = resolvePath(pid, pars) { definitions =>
          if (definitions.nonEmpty) {
            val d = definitions.head
            associateUsage(container, d)
            single(this, tc, pid, d.getClass, d)
            definitions
          } else { definitions }
        } { list => multi(this, pid, list) }
        if (result.isEmpty) { notResolved(pid, container, kind) }
      }
      this
    }

    def checkRef[T <: Definition: ClassTag](
      reference: Reference[T],
      defn: Definition,
      parents: Seq[Definition],
      kind: Option[String] = None
    ): ValidationState = {
      checkPathRef[T](reference.pathId, defn, parents, kind)()()
    }

    def checkMessageRef(
      ref: MessageRef,
      topDef: Definition,
      parents: Seq[Definition],
      kind: MessageKind
    ): ValidationState = {
      if (ref.isEmpty) { addError(ref.pathId.loc, s"${ref.identify} is empty") }
      else {
        checkPathRef[Type](ref.pathId, topDef, parents, Some(kind.kind)) {
          (state, _, _, _, defn) =>
            defn match {
              case Type(_, _, typ, _, _) => typ match {
                  case MessageType(_, mk, _) => state.check(
                      mk == kind,
                      s"'${ref.identify} should be ${article(kind.kind)} type" +
                        s" but is ${article(mk.kind)} type instead",
                      Error,
                      ref.pathId.loc
                    )
                  case te: TypeExpression => state.addError(
                      ref.pathId.loc,
                      s"'${ref.identify} should reference ${article(kind.kind)} but is a ${AST
                          .errorDescription(te)} type instead"
                    )
                }
              case _ => state.addError(
                  ref.pathId.loc,
                  s"${ref.identify} was expected to be ${article(kind.kind)} type but is ${article(defn.kind)} instead"
                )
            }
        }(defaultMultiMatchValidationFunction)
      }
    }

    def checkOption[A <: RiddlValue](
      opt: Option[A],
      name: String,
      thing: Definition
    )(folder: (ValidationState, A) => ValidationState
    ): ValidationState = {
      opt match {
        case None => addMissing(
            thing.loc,
            s"$name in ${thing.identify} should not be empty"
          )
        case Some(x) =>
          val s1 = checkNonEmptyValue(x, "Condition", thing, MissingWarning)
          folder(s1, x)
      }
    }
    def checkSequence[A](
      elements: Seq[A]
    )(fold: (ValidationState, A) => ValidationState
    ): ValidationState = elements.foldLeft(this) { case (next, element) =>
      fold(next, element)
    }

    def checkNonEmptyValue(
      value: RiddlValue,
      name: String,
      thing: Definition,
      kind: KindOfMessage = Error,
      required: Boolean = false
    ): ValidationState = {
      check(
        value.nonEmpty,
        message =
          s"$name in ${thing.identify} ${if (required) "must" else "should"} not be empty",
        kind,
        thing.loc
      )
    }

    def checkNonEmpty(
      list: Seq[?],
      name: String,
      thing: Definition,
      kind: KindOfMessage = Error,
      required: Boolean = false
    ): ValidationState = {
      check(
        list.nonEmpty,
        s"$name in ${thing.identify} ${if (required) "must" else "should"} not be empty",
        kind,
        thing.loc
      )
    }

    def checkOptions[T <: OptionValue](
      options: Seq[T],
      loc: At
    ): ValidationState = {
      check(
        options.sizeIs == options.distinct.size,
        "Options should not be repeated",
        Error,
        loc
      )
    }

    def checkUniqueContent(definition: Definition): ValidationState = {
      val allNames = definition.contents.map(_.id.value)
      val uniqueNames = allNames.toSet
      if (allNames.size != uniqueNames.size) {
        val duplicateNames = allNames.toSet.removedAll(uniqueNames)
        addError(
          definition.loc,
          s"${definition.identify} has duplicate content names:\n${duplicateNames
              .mkString("  ", ",\n  ", "\n")}"
        )
      } else { this }
    }

    def checkDefinition(
      parents: Seq[Definition],
      definition: Definition
    ): ValidationState = {
      var result = this.check(
        definition.id.nonEmpty | definition.isImplicit,
        "Definitions may not have empty names",
        Error,
        definition.loc
      ).checkIdentifierLength(definition).checkUniqueContent(definition).check(
        !definition.isVital || definition.hasAuthors,
        "Vital definitions should have an author reference",
        MissingWarning,
        definition.loc
      ).stepIf(definition.isVital) { vs: ValidationState =>
        definition.asInstanceOf[WithAuthors].authors
          .foldLeft(vs) { case (vs, authorRef) =>
            pathIdToDefinition(authorRef.pathId, parents) match {
              case None => vs.addError(
                  authorRef.loc,
                  s"${authorRef.format} is not defined"
                )
              case _ => vs
            }
          }
      }

      val path = symbolTable.pathOf(definition)
      if (!definition.id.isEmpty) {
        val matches = result.lookup[Definition](path)
        if (matches.isEmpty) {
          result = result.addSevere(
            definition.id.loc,
            s"'${definition.id.value}' evaded inclusion in symbol table!"
          )
        } else if (matches.sizeIs >= 2) {
          val parentGroups = matches.groupBy(result.symbolTable.parentOf(_))
          parentGroups.get(parents.headOption) match {
            case Some(head :: tail) if tail.nonEmpty =>
              result = result.addWarning(
                head.id.loc,
                s"${definition.identify} has same name as other definitions " +
                  s"in ${head.identifyWithLoc}:  " +
                  tail.map(x => x.identifyWithLoc).mkString(",  ")
              )
            case Some(head :: tail) if tail.isEmpty =>
              result = result.addStyle(
                head.id.loc,
                s"${definition.identify} has same name as other definitions: " +
                  matches.filterNot(_ == definition).map(x => x.identifyWithLoc)
                    .mkString(",  ")
              )
            case _ =>
            // ignore
          }
        }
      }
      result
    }

    def checkContainer(
      parents: Seq[Definition],
      container: Definition
    ): ValidationState = {
      val parent: Definition = parents.headOption.getOrElse(RootContainer.empty)
      checkDefinition(parents, container).check(
        container.nonEmpty || container.isInstanceOf[Field],
        s"${container.identify} in ${parent.identify} should have content",
        MissingWarning,
        container.loc
      )
    }

    def checkDescription[TD <: DescribedValue](
      id: String,
      value: TD
    ): ValidationState = {
      val description: Option[Description] = value.description
      val shouldCheck: Boolean = {
        value.isInstanceOf[Type] |
          (value.isInstanceOf[Definition] && value.nonEmpty)
      }
      if (description.isEmpty && shouldCheck) {
        this.check(
          predicate = false,
          s"$id should have a description",
          MissingWarning,
          value.loc
        )
      } else if (description.nonEmpty) {
        val desc = description.get
        this.check(
          desc.nonEmpty,
          s"For $id, description at ${desc.loc} is declared but empty",
          MissingWarning,
          desc.loc
        )
      } else this
    }

    def checkDescription[TD <: Definition](
      definition: TD
    ): ValidationState = { checkDescription(definition.identify, definition) }

    def checkAction(
      action: Action,
      defn: Definition,
      parents: Seq[Definition]
    ): ValidationState = {
      action match {
        case _: ErrorAction => this
        case SetAction(_, path, value, _) => this
            .checkPathRef[Field](path, defn, parents)()()
            .checkExpression(value, defn, parents)
            .checkAssignmentCompatability(path, value, parents)
        case AppendAction(_, value, path, _) => this
            .checkExpression(value, defn, parents)
            .checkPathRef[Field](path, defn, parents)()()
        case ReturnAction(_, expr, _) => this
            .checkExpression(expr, defn, parents)
        case YieldAction(_, msg, _) => this
            .checkMessageConstructor(msg, defn, parents)
        case PublishAction(_, msg, pipeRef, _) => this
            .checkMessageConstructor(msg, defn, parents)
            .checkRef[Pipe](pipeRef, defn, parents)
        case FunctionCallAction(_, funcId, args, _) => this
            .checkPathRef[Function](funcId, defn, parents)()()
            .checkArgList(args, defn, parents)
        case BecomeAction(_, entity, handler, _) => this
            .checkRef[Entity](entity, defn, parents)
            .checkRef[Handler](handler, defn, parents)
        case MorphAction(_, entity, entityState, _) => this
            .checkRef[Entity](entity, defn, parents)
            .checkRef[State](entityState, defn, parents)
        case TellAction(_, msg, entity, _) => this
            .checkRef[Definition](entity, defn, parents)
            .checkMessageConstructor(msg, defn, parents)
        case AskAction(_, entity, msg, _) => this
            .checkRef[Entity](entity, defn, parents)
            .checkMessageConstructor(msg, defn, parents)
        case ReplyAction(_, msg, _) =>
          checkMessageConstructor(msg, defn, parents)
        case CompoundAction(loc, actions, _) => check(
            actions.nonEmpty,
            "Compound action is empty",
            MissingWarning,
            loc
          ).checkSequence(actions) { (s, action) =>
            s.checkAction(action, defn, parents)
          }
        case ArbitraryAction(loc, what, _) => this.check(
            what.nonEmpty,
            "arbitrary action is empty so specifies nothing",
            MissingWarning,
            loc
          )
      }
    }

    def checkActions(
      actions: Seq[Action],
      defn: Definition,
      parents: Seq[Definition]
    ): ValidationState = {
      checkSequence(actions)((s, action) =>
        s.checkAction(action, defn, parents)
      )
    }

    def checkExample(
      example: Example,
      parents: Seq[Definition]
    ): ValidationState = {
      val Example(_, _, givens, whens, thens, buts, _, _) = example
      checkSequence(givens) { (state, givenClause) =>
        state.checkSequence(givenClause.scenario) { (state, ls) =>
          state
            .checkNonEmptyValue(ls, "Given Scenario", example, MissingWarning)
        }.checkNonEmpty(givenClause.scenario, "Givens", example, MissingWarning)
      }.checkSequence(whens) { (st, when) =>
        st.checkExpression(when.condition, example, parents)
      }.checkThat(example.id.nonEmpty) { st =>
        st.checkNonEmpty(thens, "Thens", example, required = true)
      }.checkActions(thens.map(_.action), example, parents)
        .checkActions(buts.map(_.action), example, parents)
        .checkDescription(example)
    }

    def checkExamples(
      examples: Seq[Example],
      parents: Seq[Definition]
    ): ValidationState = {
      examples.foldLeft(this) { (next, example) =>
        next.checkExample(example, parents)
      }
    }

    def checkFunctionCall(
      loc: At,
      pathId: PathIdentifier,
      args: ArgList,
      defn: Definition,
      parents: Seq[Definition]
    ): ValidationState = {
      checkArgList(args, defn, parents).checkPathRef[Function](
        pathId,
        defn,
        parents
      ) { (state, foundClass, id, defClass, defn) =>
        defaultSingleMatchValidationFunction(
          state,
          foundClass,
          id,
          defClass,
          defn
        )
        defn match {
          case f: Function if f.input.nonEmpty =>
            val fid = f.id
            val fields = f.input.get.fields
            val paramNames = fields.map(_.id.value)
            val argNames = args.args.keys.map(_.value).toSeq
            val s1 = state.check(
              argNames.size == paramNames.size,
              s"Wrong number of arguments for ${fid.format}. Expected ${paramNames
                  .size}, but got ${argNames.size}",
              Error,
              loc
            )
            val missing = paramNames.filterNot(argNames.contains(_))
            val unexpected = argNames.filterNot(paramNames.contains(_))
            val s2 = s1.check(
              missing.isEmpty,
              s"Missing arguments: ${missing.mkString(", ")}",
              Error,
              loc
            )
            s2.check(
              unexpected.isEmpty,
              s"Arguments do not correspond to parameters; ${unexpected.mkString(",")}",
              Error,
              loc
            )
          case _ => state
        }
      }()
    }

    def checkExpressions(
      expressions: Seq[Expression],
      defn: Definition,
      parents: Seq[Definition]
    ): ValidationState = {
      expressions.foldLeft(this) { (st, expr) =>
        st.checkExpression(expr, defn, parents)
      }
    }

    def checkExpression(
      expression: Expression,
      defn: Definition,
      parents: Seq[Definition]
    ): ValidationState = expression match {
      case ValueOperator(_, path) => checkPathRef[Field](path, defn, parents)(
          nullSingleMatchingValidationFunction
        )()
      case GroupExpression(_, expressions) => checkSequence(expressions) {
          (st, expr) => st.checkExpression(expr, defn, parents)
        }
      case FunctionCallExpression(loc, pathId, arguments) =>
        checkFunctionCall(loc, pathId, arguments, defn, parents)
      case ArithmeticOperator(loc, op, operands) => check(
          op.nonEmpty,
          "Operator is empty in abstract binary operator",
          Error,
          loc
        ).checkExpressions(operands, defn, parents)
      case Comparison(loc, comp, arg1, arg2) =>
        checkExpression(arg1, defn, parents)
          .checkExpression(arg2, defn, parents).check(
            arg1.expressionType.isAssignmentCompatible(arg2.expressionType),
            s"Incompatible expression types in ${comp.format} expression",
            Messages.Error,
            loc
          )
      case AggregateConstructionExpression(_, pid, args) =>
        checkPathRef[Type](pid, defn, parents)()()
          .checkArgList(args, defn, parents)
      case NewEntityIdOperator(_, entityRef) =>
        checkPathRef[Entity](entityRef, defn, parents)()()
      case Ternary(loc, condition, expr1, expr2) =>
        checkExpression(condition, defn, parents)
          .checkExpression(expr1, defn, parents)
          .checkExpression(expr2, defn, parents).check(
            expr1.expressionType.isAssignmentCompatible(expr2.expressionType),
            "Incompatible expression types in Ternary expression",
            Messages.Error,
            loc
          )

      case NotCondition(_, cond1) => checkExpression(cond1, defn, parents)
      case condition: MultiCondition =>
        checkExpressions(condition.conditions, defn, parents)
      case _ => this // not of interest
    }

    @tailrec private def getPathIdType(
      pid: PathIdentifier,
      parents: Seq[Definition]
    ): Option[TypeExpression] = {
      if (pid.value.isEmpty) { None }
      else {
        val newParents: Seq[Definition] = resolvePath(pid, parents)()()
        val candidate: Option[TypeExpression] = newParents.headOption match {
          case None              => None
          case Some(f: Function) => f.output
          case Some(t: Type)     => Some(t.typ)
          case Some(f: Field)    => Some(f.typeEx)
          case Some(s: State)    => Some(s.aggregation)
          case Some(Pipe(_, _, tt, _, _)) =>
            val te = tt.map(x => AliasedTypeExpression(x.loc, x.pathId))
            Some(te.getOrElse(Abstract(pid.loc)))
          case Some(Inlet(_, _, typ, _, _, _)) =>
            Some(AliasedTypeExpression(typ.loc, typ.pathId))
          case Some(Outlet(_, _, typ, _, _, _)) =>
            Some(AliasedTypeExpression(typ.loc, typ.pathId))
          case Some(_) => Option.empty[TypeExpression]
        }
        candidate match {
          case Some(AliasedTypeExpression(_, pid)) =>
            getPathIdType(pid, newParents)
          case Some(other: TypeExpression) => Some(other)
          case None                        => None
        }
      }
    }

    def isAssignmentCompatible(
      typeEx1: Option[TypeExpression],
      typeEx2: Option[TypeExpression]
    ): Boolean = {
      typeEx1 match {
        case None => false
        case Some(ty1) => typeEx2 match {
            case None      => false
            case Some(ty2) => ty1.isAssignmentCompatible(ty2)
          }
      }
    }

    def getExpressionType(
      expr: Expression,
      parents: Seq[Definition]
    ): Option[TypeExpression] = {
      expr match {
        case NewEntityIdOperator(loc, pid)      => Some(UniqueId(loc, pid))
        case ValueOperator(_, path)             => getPathIdType(path, parents)
        case FunctionCallExpression(_, name, _) => getPathIdType(name, parents)
        case GroupExpression(loc, expressions)  =>
          // the type of a group is the last expression but it could be empty
          expressions.lastOption match {
            case None       => Some(Abstract(loc))
            case Some(expr) => getExpressionType(expr, parents)
          }
        case AggregateConstructionExpression(_, pid, _) =>
          getPathIdType(pid, parents)
        case Ternary(loc, _, expr1, expr2) =>
          val expr1Ty = getExpressionType(expr1, parents)
          val expr2Ty = getExpressionType(expr2, parents)
          if (isAssignmentCompatible(expr1Ty, expr2Ty)) { expr1Ty }
          else {
            addError(
              loc,
              s"""Ternary expressions must be assignment compatible but:
                 |  ${expr1.format} and
                 |  ${expr2.format}
                 |are incompatible
                 |""".stripMargin
            )
            None
          }
        case e: Expression => Some(e.expressionType)
      }
    }

    def checkAssignmentCompatability(
      path: PathIdentifier,
      expr: Expression,
      parents: Seq[Definition]
    ): ValidationState = {
      val pidType = getPathIdType(path, parents)
      val exprType = getExpressionType(expr, parents)
      if (!isAssignmentCompatible(pidType, exprType)) {
        addError(
          path.loc,
          s"""Setting a value requires assignment compatibility, but field:
             |  ${path.format} (${pidType.map(_.format)
              .getOrElse("<not found>")})
             |is not assignment compatible with expression:
             |  ${expr.format} (${exprType.map(_.format)
              .getOrElse("<not found>")})
             |""".stripMargin
        )
      } else { this }
    }

    def checkArgList(
      arguments: ArgList,
      defn: Definition,
      parents: Seq[Definition]
    ): ValidationState = {
      arguments.args.values.foldLeft(this) { (st, arg) =>
        st.checkExpression(arg, defn, parents)
      }
    }

    def checkMessageConstructor(
      messageConstructor: MessageConstructor,
      defn: Definition,
      parents: Seq[Definition]
    ): ValidationState = {
      val id = messageConstructor.msg.pathId
      val kind = messageConstructor.msg.messageKind.kind
      checkPathRef[Type](id, defn, parents, Some(kind)) {
        (state, _, id, _, defn) =>
          defn match {
            case Type(_, _, typ, _, _) => typ match {
                case mt: MessageType =>
                  val names = messageConstructor.args.args.keys.map(_.value)
                    .toSeq
                  val unset = mt.fields.filterNot { fName =>
                    names.contains(fName.id.value)
                  }
                  if (unset.nonEmpty) {
                    unset.filterNot(_.isImplicit).foldLeft(state) {
                      (next, field) =>
                        next.addError(
                          messageConstructor.loc,
                          s"${field.identify} was not set in message constructor"
                        )
                    }
                  } else { state }
                case te: TypeExpression => state.addError(
                    id.loc,
                    s"'${id.format}' should reference a message type but is a ${AST.errorDescription(te)} type instead."
                  )
              }
            case _ => addError(
                id.loc,
                s"'${id.format}' was expected to be a message type but is ${article(defn.kind)} instead"
              )
          }
      }(defaultMultiMatchValidationFunction)
    }

    def checkProcessorKind(proc: Processor): ValidationState = {
      val ins = proc.inlets.size
      val outs = proc.outlets.size
      proc.shape match {
        case AST.Source(loc) =>
          if (ins != 0 || outs != 1) {
            this.addError(
              loc,
              s"${proc.identify} should have 1 Outlet and no Inlets but has $outs and $ins "
            )
          } else { this }
        case AST.Flow(loc) =>
          if (ins != 1 || outs != 1) {
            this.addError(
              loc,
              s"${proc.identify} should have 1 Outlet and 1 Inlet but has $outs and $ins"
            )
          } else { this }
        case AST.Sink(loc) =>
          if (ins != 1 || outs != 0) {
            this.addError(
              loc,
              s"${proc.identify} should have no Outlets and 1 Inlet but has $outs and $ins"
            )
          } else { this }
        case AST.Merge(loc) =>
          if (ins < 2 || outs != 1) {
            this.addError(
              loc,
              s"${proc.identify} should have 1 Outlet and >1 Inlets but has $outs and $ins"
            )
          } else { this }
        case AST.Split(loc) =>
          if (ins != 1 || outs < 2) {
            this.addError(
              loc,
              s"${proc.identify} should have >1 Outlets and 1 Inlet but has $outs and $ins"
            )
          } else { this }
        case AST.Multi(loc) =>
          if (ins < 2 || outs < 2) {
            this.addError(
              loc,
              s"${proc.identify} should have >1 Outlets and >1 Inlets but has $outs and $ins"
            )
          } else { this }
        case AST.Void(loc) =>
          if (ins > 0 || outs > 0) {
            this.addError(
              loc,
              s"${proc.identify} should have no Outlets or Inlets but has $outs and $ins"
            )
          } else { this }
      }
    }
  }
}
