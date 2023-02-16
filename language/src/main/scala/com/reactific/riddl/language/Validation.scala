/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.validation.{
  ValidationState, DefinitionValidator
}

import scala.util.control.NonFatal
import org.apache.commons.lang3.exception.ExceptionUtils

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
    val endState: ValidationState = {
      try {
        val s1 = DefinitionValidator.validate(state, root, parents)
        s1.checkUnused()
        s1.checkOverloads()
        s1.checkStreaming()
        s1
      } catch {
        case NonFatal(xcptn) =>
          val message = ExceptionUtils.getRootCauseStackTrace(xcptn)
            .mkString("\n")
          state.addSevere(At.empty, message)
          state
      }
    }
    Result(
      endState.messages.sortBy(_.loc),
      root,
      symTab,
      endState.usesAsMap,
      endState.usedByAsMap
    )
  }
}
