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

object TreePass extends PassInfo[PassOptions] {
  val name: String = "Tree"
  def creator(
    options: PassOptions = PassOptions.empty
  )(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => TreePass(in, out)
  }
}

case class TreeNode(
  kind: String,
  id: String,
  line: Int,
  col: Int,
  offset: Int,
  children: Seq[TreeNode]
)

case class TreeOutput(
  root: PassRoot,
  messages: Messages.Messages,
  tree: Seq[TreeNode]
) extends PassOutput

case class TreePass(
  input: PassInput,
  outputs: PassesOutput
)(using PlatformContext)
    extends HierarchyPass(input, outputs) {

  def name: String = TreePass.name

  private val childrenMap: mutable.Map[Definition, mutable.ListBuffer[TreeNode]] =
    mutable.Map.empty

  private val topLevel: mutable.ListBuffer[TreeNode] =
    mutable.ListBuffer.empty

  protected def openContainer(
    definition: Definition,
    parents: Parents
  ): Unit = {
    childrenMap(definition) = mutable.ListBuffer.empty
  }

  protected def processLeaf(
    definition: Leaf,
    parents: Parents
  ): Unit = {
    if definition.id.nonEmpty then
      val leaf = TreeNode(
        kind = definition.kind,
        id = definition.id.value,
        line = definition.loc.line,
        col = definition.loc.col,
        offset = definition.loc.offset,
        children = Seq.empty
      )
      if parents.nonEmpty then
        childrenMap.get(parents.head.asInstanceOf[Definition]) match {
          case Some(buf) => buf.append(leaf)
          case None      => topLevel.append(leaf)
        }
      else topLevel.append(leaf)
      end if
    end if
  }

  protected def processValue(
    value: RiddlValue,
    parents: Parents
  ): Unit = ()

  protected def closeContainer(
    definition: Definition,
    parents: Parents
  ): Unit = {
    val children = childrenMap.remove(definition)
      .map(_.toSeq).getOrElse(Seq.empty)
    if definition.id.nonEmpty then
      val node = TreeNode(
        kind = definition.kind,
        id = definition.id.value,
        line = definition.loc.line,
        col = definition.loc.col,
        offset = definition.loc.offset,
        children = children
      )
      if parents.nonEmpty then
        childrenMap.get(parents.head.asInstanceOf[Definition]) match {
          case Some(buf) => buf.append(node)
          case None      => topLevel.append(node)
        }
      else topLevel.append(node)
      end if
    end if
  }

  def result(root: PassRoot): TreeOutput = {
    TreeOutput(root, Messages.empty, topLevel.toSeq)
  }
}
