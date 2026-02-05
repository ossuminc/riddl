/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable

object OutlinePass extends PassInfo[PassOptions] {
  val name: String = "Outline"
  def creator(
    options: PassOptions = PassOptions.empty
  )(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => OutlinePass(in, out)
  }
}

case class OutlineEntry(
  kind: String,
  id: String,
  depth: Int,
  line: Int,
  col: Int,
  offset: Int
)

case class OutlineOutput(
  root: PassRoot,
  messages: Messages.Messages,
  entries: Seq[OutlineEntry]
) extends PassOutput

case class OutlinePass(
  input: PassInput,
  outputs: PassesOutput
)(using PlatformContext)
    extends HierarchyPass(input, outputs) {

  def name: String = OutlinePass.name

  private val buffer: mutable.ListBuffer[OutlineEntry] =
    mutable.ListBuffer.empty

  protected def openContainer(
    definition: Definition,
    parents: Parents
  ): Unit = {
    if definition.id.nonEmpty then
      buffer.append(
        OutlineEntry(
          kind = definition.kind,
          id = definition.id.value,
          depth = parents.size,
          line = definition.loc.line,
          col = definition.loc.col,
          offset = definition.loc.offset
        )
      )
    end if
  }

  protected def processLeaf(
    definition: Leaf,
    parents: Parents
  ): Unit = {
    if definition.id.nonEmpty then
      buffer.append(
        OutlineEntry(
          kind = definition.kind,
          id = definition.id.value,
          depth = parents.size,
          line = definition.loc.line,
          col = definition.loc.col,
          offset = definition.loc.offset
        )
      )
    end if
  }

  protected def processValue(
    value: RiddlValue,
    parents: Parents
  ): Unit = ()

  protected def closeContainer(
    definition: Definition,
    parents: Parents
  ): Unit = ()

  def result(root: PassRoot): OutlineOutput = {
    OutlineOutput(root, Messages.empty, buffer.toSeq)
  }
}
