/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.PassesResult

import scala.reflect.ClassTag
import scala.scalajs.js.annotation.*

/** A trait to be implemented by the user of UseCaseDiagram that provides information that can only be provided from
  * outside UseCaseDiagram itself. Note that the PassesResult from running the standard passes is required.
  */
trait UseCaseDiagramSupport {
  @JSExport
  def passesResult: PassesResult
  @JSExport
  def makeDocLink(definition: Definition): String

  @JSExport
  def getDefinitionFor[T <: Definition: ClassTag](pathId: PathIdentifier, parent: Parent): Option[T] = {
    passesResult.refMap.definitionOf[T](pathId, parent)
  }

}
