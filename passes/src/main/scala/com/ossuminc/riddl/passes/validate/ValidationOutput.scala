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
  /** Handler contains executable statements
    * (tell, send, morph, set, become, error, code)
    */
  case Executable

  /** Handler contains only prompt statements
    * (natural language descriptions of intended behavior)
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
  *   Number of executable statements (tell, send, morph,
  *   set, become, error, code)
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

/** The output of the Validation Pass */
case class ValidationOutput(
  root: PassRoot = Root.empty,
  messages: Messages.Messages = Messages.empty,
  inlets: Seq[Inlet] = Seq.empty[Inlet],
  outlets: Seq[Outlet] = Seq.empty[Outlet],
  connectors: Seq[Connector] = Seq.empty[Connector],
  streamlets: Seq[Streamlet] = Seq.empty[Streamlet],
  handlerCompleteness: Seq[HandlerCompleteness] =
    Seq.empty[HandlerCompleteness],
) extends PassOutput
