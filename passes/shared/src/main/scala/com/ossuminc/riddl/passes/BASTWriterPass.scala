/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.bast.{BASTWriter, ByteBufferWriter, StringTable, HEADER_SIZE}
import com.ossuminc.riddl.utils.PlatformContext

/** Output from BASTWriter pass
  *
  * @param root The root of the AST (unchanged)
  * @param messages Any messages generated during serialization
  * @param bytes The serialized BAST bytes
  * @param nodeCount Total number of nodes serialized
  * @param stringTableSize Number of strings in string table
  */
case class BASTOutput(
  root: PassRoot = Root.empty,
  messages: Messages.Messages = Messages.empty,
  bytes: Array[Byte] = Array.empty,
  nodeCount: Int = 0,
  stringTableSize: Int = 0
) extends PassOutput

object BASTWriterPass extends PassInfo[PassOptions] {
  val name: String = "BASTWriter"

  def creator(options: PassOptions = PassOptions.empty)(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => BASTWriterPass(in, out)
  }
}

/** BAST serialization pass
  *
  * Converts a RIDDL AST to Binary AST (BAST) format for efficient storage
  * and fast loading. Uses string interning and variable-length encoding to
  * minimize file size.
  *
  * This pass uses the Pass framework for correct AST traversal and delegates
  * the actual serialization to BASTWriter (in the language module).
  *
  * @param input The AST to serialize
  * @param outputs Output from previous passes
  */
case class BASTWriterPass(input: PassInput, outputs: PassesOutput)(using pc: PlatformContext)
    extends Pass(input, outputs, withIncludes = true) {

  override def name: String = BASTWriterPass.name

  private val bastWriter = BASTWriter()
  private var finalizedBytes: Array[Byte] = Array.empty

  // Reserve space for header
  bastWriter.reserveHeader()

  override protected def process(definition: RiddlValue, parents: ParentStack): Unit = {
    bastWriter.writeNode(definition)
  }

  // Override traverse to write metadata count AFTER contents items
  // and to handle nodes with multiple Contents fields
  override protected def traverse(definition: RiddlValue, parents: ParentStack): Unit = {
    definition match {
      case root: Root =>
        process(root, parents)
        parents.push(root)
        root.contents.foreach { value => traverse(value, parents) }
        parents.pop()

      // Nodes with multiple Contents fields
      case ss: SagaStep => traverseSagaStep(ss, parents)
      case ws: WhenStatement => traverseWhenStatement(ws, parents)
      case ms: MatchStatement => traverseMatchStatement(ms, parents)

      // OnClauses (grouped for clarity)
      case oc: OnInitializationClause => traverseOnClause(oc, oc.contents, parents)
      case oc: OnTerminationClause => traverseOnClause(oc, oc.contents, parents)
      case oc: OnMessageClause => traverseOnClause(oc, oc.contents, parents)
      case oc: OnOtherClause => traverseOnClause(oc, oc.contents, parents)

      // Other Branch types with metadata
      case h: Handler => traverseOnClause(h, h.contents, parents)
      case uc: UseCase => traverseOnClause(uc, uc.contents, parents)
      case g: Group => traverseOnClause(g, g.contents, parents)
      case o: Output => traverseOnClause(o, o.contents, parents)
      case i: Input => traverseOnClause(i, i.contents, parents)

      // Type: contents computed from typEx, no traversal needed
      case t: Type =>
        process(t, parents)
        if t.metadata.nonEmpty then bastWriter.writeMetadataCount(t.metadata)

      // Standard Branch with WithMetaData
      case branch: (Branch[?] & WithMetaData) =>
        process(branch, parents)
        parents.push(branch)
        branch.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        if branch.metadata.nonEmpty then bastWriter.writeMetadataCount(branch.metadata)

      // Non-Branch leaf definitions with metadata
      case wm: WithMetaData =>
        process(wm, parents)
        if wm.metadata.nonEmpty then bastWriter.writeMetadataCount(wm.metadata)

      case _ =>
        super.traverse(definition, parents)
    }
  }

  private def traverseSagaStep(ss: SagaStep, parents: ParentStack): Unit = {
    process(ss, parents)
    bastWriter.writeContents(ss.doStatements)
    ss.doStatements.toSeq.foreach { value => traverse(value, parents) }
    bastWriter.writeContents(ss.undoStatements)
    ss.undoStatements.toSeq.foreach { value => traverse(value, parents) }
    if ss.metadata.nonEmpty then bastWriter.writeMetadataCount(ss.metadata)
  }

  private def traverseWhenStatement(ws: WhenStatement, parents: ParentStack): Unit = {
    process(ws, parents)
    bastWriter.writeContents(ws.thenStatements)
    ws.thenStatements.toSeq.foreach { value => traverse(value, parents) }
    bastWriter.writeContents(ws.elseStatements)
    ws.elseStatements.toSeq.foreach { value => traverse(value, parents) }
  }

  private def traverseMatchStatement(ms: MatchStatement, parents: ParentStack): Unit = {
    process(ms, parents)
    ms.cases.foreach { mc =>
      mc.statements.toSeq.foreach { value => traverse(value, parents) }
    }
    ms.default.toSeq.foreach { value => traverse(value, parents) }
  }

  private def traverseOnClause[T <: RiddlValue](
    node: Branch[T] & WithMetaData,
    contents: Contents[T],
    parents: ParentStack
  ): Unit = {
    process(node, parents)
    parents.push(node)
    contents.foreach { value => traverse(value, parents) }
    parents.pop()
    if node.metadata.nonEmpty then bastWriter.writeMetadataCount(node.metadata)
  }

  override def postProcess(root: PassRoot): Unit = {
    // Write string table at current position
    val stringTableOffset = bastWriter.writeStringTable()

    // Finalize writes the header and returns the complete bytes
    finalizedBytes = bastWriter.finalize(stringTableOffset)
  }

  override def result(root: PassRoot): BASTOutput = {
    println(s"[info] BAST serialization complete: ${bastWriter.getNodeCount} nodes, ${finalizedBytes.length} bytes")
    BASTOutput(root, Messages.empty, finalizedBytes, bastWriter.getNodeCount, bastWriter.stringTable.size)
  }

  override def close(): Unit = ()
}
