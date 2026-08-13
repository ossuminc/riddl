/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Finder, Messages, toSeq}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.ResolutionPass
import com.ossuminc.riddl.passes.symbols.SymbolsPass
import com.ossuminc.riddl.passes.validate.ValidationPass
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.immutable.Set as ISet
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.scalajs.js.annotation.*

/** Output of the [[UseCaseWitnessPass]] (A36 Level 1). Carries the completeness warnings emitted
  * for use-case interaction steps that are not witnessed by the model's handler structure and
  * wiring.
  *
  * @param root
  *   The root of the model
  * @param messages
  *   The completeness warnings produced by the pass
  */
@JSExportTopLevel("UseCaseWitnessOutput")
case class UseCaseWitnessOutput(
  root: PassRoot = Root.empty,
  messages: Messages.Messages = Messages.empty
) extends PassOutput

@JSExportTopLevel("UseCaseWitnessPass$")
object UseCaseWitnessPass extends PassInfo[PassOptions] {
  val name: String = "UseCaseWitness"
  def creator(
    options: PassOptions = PassOptions.empty
  )(using PlatformContext): PassCreator = { (in: PassInput, out: PassesOutput) =>
    UseCaseWitnessPass(in, out)
  }
}

/** A36 Level 1 — use-case witnessing by parsed structure.
  *
  * For each [[UseCase]]'s interaction steps, this pass verifies that the step is *witnessed* by the
  * model's handler structure and wiring. An unwitnessed step yields an advisory
  * [[com.ossuminc.riddl.language.Messages.CompletenessWarning]] (gated, like its siblings, by
  * `showCompletenessWarnings` at output time). Witness rules (§3.1 of the A36 design):
  *
  *   - [[SendMessageInteraction]] — witnessed iff (a) the receiver has an `on <message>` clause for
  *     the sent message's Type AND (b) there is reachable wiring from sender to receiver (reusing
  *     [[MessageFlowPass]] edges). A [[User]] sender is a boundary stimulus and is trivially
  *     reachable.
  *   - [[ShowOutputInteraction]] — witnessed iff some handler contains a `put … to <output>`
  *     ([[PutStatement]]) targeting the shown Output.
  *   - [[TakeInputInteraction]] / [[SelectInputInteraction]] — witnessed iff some handler has an
  *     `on`-clause consuming the input's `takeIn` message Type, OR a `get from input <input>`
  *     ([[GetValue]]) reads it.
  *   - FocusOnGroup, DirectUserToURL, Self, Arbitrary, Vague, Refusal — inherently structural /
  *     prose; never witnessed, never warned.
  *
  * The three lookup indices (message-Type → handling processors, put-targeted outputs, gotten
  * inputs) are built ONCE up front and consulted per step.
  */
@JSExportTopLevel("UseCaseWitnessPass")
case class UseCaseWitnessPass(
  input: PassInput,
  outputs: PassesOutput
)(using PlatformContext)
    extends Pass(input, outputs) {

  requires(SymbolsPass)
  requires(ResolutionPass)
  requires(ValidationPass)
  requires(MessageFlowPass)

  override def name: String = UseCaseWitnessPass.name

  private lazy val symTab = outputs.symbols
  private lazy val messageFlow: MessageFlowOutput =
    outputs.outputOf[MessageFlowOutput](MessageFlowPass.name).getOrElse(MessageFlowOutput())

  // All definitions in the model, from the symbol table.
  private lazy val allDefinitions: Iterable[Definition] = symTab.parentage.keys

  private lazy val allProcessors: Iterable[Processor[?]] =
    allDefinitions.collect { case p: Processor[?] => p }

  /** Resolve a path identifier to a single definition of the requested kind via the (parent
    * independent) symbol table. Interaction and on-clause references are keyed in the resolution
    * refMap under differing scopes, so resolving through the symbol table by name keeps this robust
    * regardless of where a reference textually appears. Returns the first match (fixtures name
    * these uniquely); an empty path yields None.
    */
  private def lookupOne[T <: Definition: ClassTag](pid: PathIdentifier): Option[T] =
    if pid.value.isEmpty then None
    else symTab.lookup[T](pid.value.reverse).headOption

  // ============================================================
  // Indices, built once
  // ============================================================

  /** message Type -> the processors that have an `on <that message>` clause for it. Entity state
    * handlers are attributed to their owning entity (state-level handlers apply to the entity).
    */
  private lazy val handledBy: Map[Type, ISet[Processor[?]]] = {
    val acc = mutable.HashMap.empty[Type, mutable.HashSet[Processor[?]]]
    allProcessors.foreach { p =>
      val stateHandlers = p match
        case e: Entity => e.states.flatMap(_.handlers)
        case _         => Seq.empty[Handler]
      val allHandlers = p.handlers ++ stateHandlers
      allHandlers.flatMap(_.clauses).foreach {
        case omc: OnMessageLikeClause =>
          lookupOne[Type](omc.msg.pathId).foreach { t =>
            acc.getOrElseUpdate(t, mutable.HashSet.empty[Processor[?]]).add(p)
          }
        // `on other` / `on init` / `on term` deliberately do NOT witness a step, and this
        // wildcard is where that is decided. `OnOtherClause` names no message -- it has no
        // `msg` to resolve -- so it can only say "something arrived", never "THIS arrived",
        // which is not evidence that a step's specific message is realized.
        //
        // The practical distinction, learned from riddl-examples 2026-08-05: a trailing
        // `on other` catch-all sitting BESIDE a named clause is fine and witnessing still
        // passes; SUBSTITUTING `on other` for the named clause is what leaves steps
        // unwitnessed. The clause is never the problem, the substitution is.
        //
        // So if `on other` ever gains a bound value (e.g. a `Anything`-typed binding), it
        // must still not land in this index. Anything that matches every type would witness
        // every step, silently turning a catch-all into a universal completeness silencer.
        case _ => ()
      }
    }
    acc.map { case (t, set) => t -> set.toSet }.toMap
  }

  /** The set of Outputs targeted by some `put … to <output>` statement anywhere in a handler. */
  private lazy val putOutputs: ISet[Output] = {
    val acc = mutable.HashSet.empty[Output]
    allProcessors.foreach { p =>
      val stateHandlers = p match
        case e: Entity => e.states.flatMap(_.handlers)
        case _         => Seq.empty[Handler]
      (p.handlers ++ stateHandlers).foreach { h =>
        Finder(h).recursiveFindByType[PutStatement].foreach { put =>
          lookupOne[Output](put.output.pathId).foreach(acc.add)
        }
      }
    }
    acc.toSet
  }

  /** The set of Inputs read by some `get from input <input>` value expression anywhere. */
  private lazy val gottenInputs: ISet[Input] = {
    val acc = mutable.HashSet.empty[Input]
    allProcessors.foreach { p =>
      val stateHandlers = p match
        case e: Entity => e.states.flatMap(_.handlers)
        case _         => Seq.empty[Handler]
      (p.handlers ++ stateHandlers).foreach { h =>
        h.clauses.foreach { clause =>
          collectGetInputRefs(clause.contents.toSeq).foreach { ir =>
            lookupOne[Input](ir.pathId).foreach(acc.add)
          }
        }
      }
    }
    acc.toSet
  }

  /** Producer -> consumers adjacency for wiring reachability, derived from MessageFlow edges. */
  private lazy val adjacency: Map[Definition, ISet[Definition]] =
    messageFlow.edges.groupBy(_.producer).map { case (p, es) => p -> es.map(_.consumer).toSet }

  // ============================================================
  // GetValue collection (values are not Container contents, so Finder can't reach them)
  // ============================================================

  private def collectGetInputRefs(stmts: Seq[RiddlValue]): Seq[InputRef] =
    stmts.flatMap {
      case SetStatement(_, _, value)  => getInputRefsIn(value)
      case PutStatement(_, value, _)  => getInputRefsIn(value)
      case LetStatement(_, _, _, exp) => getInputRefsIn(exp)
      case ReturnStatement(_, v)      => getInputRefsIn(v)
      case s: SendStatement =>
        s.msg match { case c: Constructor => getInputRefsIn(c); case _ => Seq.empty }
      case s: TellStatement =>
        s.msg match { case c: Constructor => getInputRefsIn(c); case _ => Seq.empty }
      case s: YieldStatement =>
        s.msg match { case c: Constructor => getInputRefsIn(c); case _ => Seq.empty }
      case s: MorphStatement =>
        s.value match { case c: Constructor => getInputRefsIn(c); case _ => Seq.empty }
      // A70/instance-identity: `terminate <processor>(args)` carries `ConstructorArg`s, the same
      // shape `Constructor.args` does (already walked by `getInputRefsIn`'s own `Constructor`
      // arm) -- a `get from input` can hide inside an argument exactly as it can inside a
      // constructor's, and must be counted or a use case reading an input only through a
      // `terminate` argument is reported as an unwitnessed input.
      case s: TerminateStatement =>
        s.args.flatMap(a => getInputRefsIn(a.value))
      case ws: WhenStatement =>
        getInputRefsIn(ws.condition) ++
          collectGetInputRefs(ws.thenStatements.toSeq) ++
          collectGetInputRefs(ws.elseStatements.toSeq)
      case ms: MatchStatement =>
        getInputRefsIn(ms.expression) ++
          ms.cases.flatMap { mc =>
            mc.guard.toSeq.flatMap(getInputRefsIn) ++ collectGetInputRefs(mc.statements.toSeq)
          } ++
          collectGetInputRefs(ms.default.toSeq)
      case fs: ForeachStatement => collectGetInputRefs(fs.doStatements.toSeq)
      case _                    => Seq.empty
    }

  private def getInputRefsIn(v: RiddlValue): Seq[InputRef] =
    v match
      case GetValue(_, ir: InputRef) => Seq(ir)
      case _: GetValue               => Seq.empty // state read, not an input
      case c: Constructor            => c.args.flatMap(a => getInputRefsIn(a.value))
      case call: Call                => call.args.flatMap(a => getInputRefsIn(a.value))
      case ce: ComparisonExpression  => getInputRefsIn(ce.left) ++ getInputRefsIn(ce.right)
      case le: LogicalExpression     => getInputRefsIn(le.left) ++ getInputRefsIn(le.right)
      case ne: NotExpression         => getInputRefsIn(ne.expr)
      case _                         => Seq.empty

  // ============================================================
  // Reachability
  // ============================================================

  private def isReachable(from: Definition, to: Definition): Boolean =
    if from == to then true
    else
      val visited = mutable.HashSet.empty[Definition]
      val queue = mutable.Queue.empty[Definition]
      queue.enqueue(from)
      visited.add(from)
      var found = false
      while queue.nonEmpty && !found do
        val cur = queue.dequeue()
        val nexts = adjacency.getOrElse(cur, ISet.empty)
        if nexts.contains(to) then found = true
        else
          nexts.foreach { n =>
            if !visited.contains(n) then
              visited.add(n)
              queue.enqueue(n)
          }
        end if
      end while
      found
    end if

  // ============================================================
  // Traversal
  // ============================================================

  protected def process(
    definition: RiddlValue,
    parents: ParentStack
  ): Unit =
    definition match
      case uc: UseCase => witnessSteps(uc, uc.contents.toSeq)
      case _           => () // interaction steps are reached from their enclosing UseCase
    end match
  /** Recurse the interaction structure (into containers) and apply the per-step witness rules. */
  private def witnessSteps(uc: UseCase, contents: Seq[RiddlValue]): Unit =
    contents.foreach {
      case ic: InteractionContainer  => witnessSteps(uc, ic.contents.toSeq)
      case s: SendMessageInteraction => witnessSend(uc, s)
      case s: ShowOutputInteraction  => witnessShow(uc, s)
      case s: TakeInputInteraction   => witnessInput(uc, s.to, s.loc, s.format)
      case s: SelectInputInteraction => witnessInput(uc, s.to, s.loc, s.format)
      case _                         => () // Focus/DirectURL/Self/Arbitrary/Vague/Refusal: skip
    }

  private def warnUnwitnessed(uc: UseCase, loc: At, stepDesc: String, why: String): Unit =
    messages.addCompleteness(
      loc,
      s"use-case '${uc.id.value}' step '$stepDesc' is not witnessed: $why " +
        "— no handler/put/get realizes it",
      suggestion =
        "Add the handler on-clause, wiring (connector/adaptor/tell), put-to-output, or " +
          "get-from-input that realizes this step, or remove/soften the step."
    )

  private def witnessSend(uc: UseCase, s: SendMessageInteraction): Unit =
    val maybeType = lookupOne[Type](s.message.pathId)
    val maybeReceiver = lookupOne[Processor[?]](s.to.pathId)
    (maybeType, maybeReceiver) match
      case (Some(msgType), Some(receiver)) =>
        val handled = handledBy.get(msgType).exists(_.contains(receiver))
        // Reachability: a User sender is a boundary stimulus (trivially reachable); an unresolved
        // sender is skipped (ref-integrity reports it). Only meaningful when the receiver handles
        // the message, so we don't pile a wiring warning on top of a missing-handler one.
        val reachable = lookupOne[Definition](s.from.pathId) match
          case Some(_: User) => true
          case Some(sender)  => isReachable(sender, receiver)
          case None          => true
        val reasons = Seq(
          Option.when(!handled)(
            s"the receiver '${s.to.pathId.format}' has no 'on ${s.message.pathId.format}' clause"
          ),
          Option.when(handled && !reachable)(
            s"no wiring (connector/adaptor/tell) path from '${s.from.pathId.format}' reaches " +
              s"'${s.to.pathId.format}'"
          )
        ).flatten
        if reasons.nonEmpty then warnUnwitnessed(uc, s.loc, s.format, reasons.mkString("; "))
      case _ => () // unresolved message or receiver; ref-integrity already reports it
    end match

  private def witnessShow(uc: UseCase, s: ShowOutputInteraction): Unit =
    lookupOne[Output](s.from.pathId).foreach { output =>
      if !putOutputs.contains(output) then
        warnUnwitnessed(
          uc,
          s.loc,
          s.format,
          s"no 'put … to ${s.from.pathId.format}' statement produces this output"
        )
    }

  private def witnessInput(uc: UseCase, inputRef: InputRef, loc: At, stepDesc: String): Unit =
    lookupOne[Input](inputRef.pathId).foreach { input =>
      val consumed = lookupOne[Type](input.takeIn.pathId).exists(handledBy.contains)
      val gotten = gottenInputs.contains(input)
      if !(consumed || gotten) then
        warnUnwitnessed(
          uc,
          loc,
          stepDesc,
          s"no handler consumes its message type '${input.takeIn.pathId.format}' and no " +
            s"'get from input ${inputRef.pathId.format}' reads it"
        )
    }

  override def result(root: PassRoot): UseCaseWitnessOutput =
    UseCaseWitnessOutput(root = root, messages = messages.toMessages)
}
