/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.PassesOutput
import com.ossuminc.riddl.utils.PlatformContext

/** The message [[Type]] a `send`/`tell` statement delivers.
  *
  * Lives here, as ONE function, rather than in each pass that needs it. Two passes ask the same
  * question — `MessageFlowPass` to build a flow edge, `DependencyAnalysisPass` to record a type
  * dependency — and a dispatch written twice is the shape this repo keeps getting bitten by: the
  * tested copy says nothing about the other. Any further consumer should call this rather than grow
  * a third.
  *
  * Every caller must `requires(ValidationPass)`; both current ones already do.
  */
object DeliverableTypes {

  /** @param outputs
    *   the accumulated pass outputs, for the refMap and the validation output
    * @param stmt
    *   the `send`/`tell` statement itself — the key ValidationPass recorded under
    * @param msg
    *   that statement's message operand
    * @param parent
    *   the on-clause the statement sits in, which is the refMap key's parent
    * @return
    *   the delivered [[Type]], or `None` when it is not statically determinable — never a guess
    */
  def of(
    outputs: PassesOutput,
    stmt: Statement,
    msg: MessageRef | Constructor | ValueRef,
    parent: OnMessageLikeClause
  )(using PlatformContext): Option[Type] =
    // The refMap answers first, so every resolution that worked before keeps working by the same
    // route and this can only ADD answers. It cannot answer for a `ValueRef` naming a `let`-local:
    // `let`-locals are LEXICAL by design — not Definitions, statement-ordered, and deliberately
    // absent from the symbol table (see `ValidationPass.letIndexOf`) — so nothing keyed on a path
    // will ever find one. For those, ValidationPass's threaded-scope resolution supplies it.
    outputs.refMap
      .definitionOf[Type](msg.deliverableOperandPathId, parent)
      .orElse(outputs.validation.deliverableTypes.get(stmt))
}
