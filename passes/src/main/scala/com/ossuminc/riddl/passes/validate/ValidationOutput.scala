/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.PassOutput
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.PassRoot

import scala.scalajs.js.annotation.*

/** Classification of a handler's behavioral completeness */
enum BehaviorCategory:
  /** Handler contains executable statements (tell, send, morph, set, become, error, code)
    */
  case Executable

  /** Handler contains only `do` statements (natural language descriptions of intended behavior).
    *
    * The NAME predates the `do`/`prompt` split: `do` is the canonical spelling and `prompt` is a
    * deprecated synonym for the same [[com.ossuminc.riddl.language.AST.DoStatement]]. It is
    * kept as `PromptOnly` because this enum is public API and the compatibility policy is to add
    * rather than change; the diagnostic it drives says `do`.
    */
  case PromptOnly

  /** Handler uses ??? or has no statements at all */
  case Empty
end BehaviorCategory

/** Describes the behavioral completeness of a single handler
  *
  * @param handler
  *   The handler being classified
  * @param parent
  *   The parent definition containing this handler
  * @param category
  *   The behavioral completeness classification
  * @param executableCount
  *   Number of executable statements (tell, send, morph, set, become, error, code)
  * @param promptCount
  *   Number of prompt statements
  * @param totalClauses
  *   Total number of on-clauses in the handler
  */
@JSExportTopLevel("HandlerCompleteness")
case class HandlerCompleteness(
  handler: Handler,
  parent: Definition,
  category: BehaviorCategory,
  executableCount: Int,
  promptCount: Int,
  totalClauses: Int
)

/** The output of the Validation Pass
  *
  * @param deliverableTypes
  *   The message [[com.ossuminc.riddl.language.AST.Type]] each `send`/`tell` statement delivers,
  *   keyed by the statement itself.
  *
  * Published because ValidationPass is the ONLY pass that can answer this for every operand shape.
  * A `MessageRef`/`Constructor` operand names its type outright and any pass can look it up in the
  * refMap; a `ValueRef` operand may name a `let`-local, and `let`-locals are deliberately LEXICAL —
  * not Definitions, statement-ordered, absent from the symbol table and therefore from the refMap
  * (see `ValidationPass.letIndexOf`). Only `checkStatementScopes`, which threads the `let` scope as
  * it walks, holds the information needed to resolve one.
  *
  * Without this, `MessageFlowPass` dropped every flow edge whose operand was a `let`-local and
  * reported the binding NAME as an unresolvable message type — 90 such warnings in riddl-models
  * once it migrated to `let` + typed hole, with the corresponding edges missing from the graph the
  * simulator and generator consume. Absent from the map means "not statically determinable", which
  * a consumer must distinguish from "no such statement".
  */
case class ValidationOutput(
  root: PassRoot = Root.empty,
  messages: Messages.Messages = Messages.empty,
  inlets: Seq[Inlet] = Seq.empty[Inlet],
  outlets: Seq[Outlet] = Seq.empty[Outlet],
  connectors: Seq[Connector] = Seq.empty[Connector],
  // [4.1], RULED 2026-08-17: `streamlets` means all PORT-BEARING processors, which since the
  // unified processor model is every Processor kind -- not the `Streamlet` case class. Element
  // type widened accordingly; a caller wanting only Streamlets writes
  // `.collect { case s: Streamlet => s }`.
  streamlets: Seq[Processor[?]] = Seq.empty[Processor[?]],
  handlerCompleteness: Seq[HandlerCompleteness] = Seq.empty[HandlerCompleteness],
  deliverableTypes: Map[Statement, Type] = Map.empty[Statement, Type]
) extends PassOutput
