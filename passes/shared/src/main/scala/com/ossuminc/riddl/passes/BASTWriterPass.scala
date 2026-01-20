/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
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
  // and to handle nodes with multiple Contents fields (SagaStep, WhenStatement, MatchStatement)
  override protected def traverse(definition: RiddlValue, parents: ParentStack): Unit = {
    definition match {
      case root: Root =>
        process(root, parents)
        parents.push(root)
        root.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        // Root has no metadata - nothing to write (flag was false)

      // SagaStep has TWO Contents fields: doStatements and undoStatements
      // Must write count-items, count-items to match reader expectations
      // SagaStep is not a Branch, so no push/pop needed
      case ss: SagaStep =>
        process(ss, parents)
        // Write doStatements count then items
        bastWriter.writeContents(ss.doStatements)
        ss.doStatements.toSeq.foreach { value => traverse(value, parents) }
        // Write undoStatements count then items
        bastWriter.writeContents(ss.undoStatements)
        ss.undoStatements.toSeq.foreach { value => traverse(value, parents) }
        // Phase 7: Only write metadata if non-empty (flag was set)
        if ss.metadata.nonEmpty then bastWriter.writeMetadataCount(ss.metadata)

      // WhenStatement has thenStatements and elseStatements Contents fields that must be traversed
      case ws: WhenStatement =>
        process(ws, parents)
        bastWriter.writeContents(ws.thenStatements)
        ws.thenStatements.toSeq.foreach { value => traverse(value, parents) }
        bastWriter.writeContents(ws.elseStatements)
        ws.elseStatements.toSeq.foreach { value => traverse(value, parents) }
        // WhenStatement is a Statement, no metadata

      // MatchStatement has cases and default Contents that must be traversed
      case ms: MatchStatement =>
        process(ms, parents)
        // Write each case's statements
        ms.cases.foreach { mc =>
          mc.statements.toSeq.foreach { value => traverse(value, parents) }
        }
        // Write default statements
        ms.default.toSeq.foreach { value => traverse(value, parents) }
        // MatchStatement is a Statement, no metadata

      // Handler extends Branch[HandlerContents] but NOT WithMetaData, so handle separately
      case h: Handler =>
        process(h, parents)
        parents.push(h)
        h.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        // Phase 7: Only write metadata if non-empty
        if h.metadata.nonEmpty then bastWriter.writeMetadataCount(h.metadata)

      // OnClauses extend Branch[Statements] but NOT WithMetaData, so handle separately
      // They have metadata fields that need to be written
      case oc: OnInitializationClause =>
        process(oc, parents)
        parents.push(oc)
        oc.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        // Phase 7: Only write metadata if non-empty
        if oc.metadata.nonEmpty then bastWriter.writeMetadataCount(oc.metadata)

      case oc: OnTerminationClause =>
        process(oc, parents)
        parents.push(oc)
        oc.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        // Phase 7: Only write metadata if non-empty
        if oc.metadata.nonEmpty then bastWriter.writeMetadataCount(oc.metadata)

      case oc: OnMessageClause =>
        process(oc, parents)
        parents.push(oc)
        oc.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        // Phase 7: Only write metadata if non-empty
        if oc.metadata.nonEmpty then bastWriter.writeMetadataCount(oc.metadata)

      case oc: OnOtherClause =>
        process(oc, parents)
        parents.push(oc)
        oc.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        // Phase 7: Only write metadata if non-empty
        if oc.metadata.nonEmpty then bastWriter.writeMetadataCount(oc.metadata)

      // Type extends Branch[TypeContents] but NOT WithMetaData
      // Its contents are computed from typEx, not stored, so no traversal needed
      case t: Type =>
        process(t, parents)
        // Phase 7: Only write metadata if non-empty
        if t.metadata.nonEmpty then bastWriter.writeMetadataCount(t.metadata)

      // UseCase extends Branch[UseCaseContents] but NOT WithMetaData
      case uc: UseCase =>
        process(uc, parents)
        parents.push(uc)
        uc.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        // Phase 7: Only write metadata if non-empty
        if uc.metadata.nonEmpty then bastWriter.writeMetadataCount(uc.metadata)

      // Group extends Branch[OccursInGroup] but NOT WithMetaData
      case g: Group =>
        process(g, parents)
        parents.push(g)
        g.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        // Phase 7: Only write metadata if non-empty
        if g.metadata.nonEmpty then bastWriter.writeMetadataCount(g.metadata)

      // Output extends Branch[OccursInOutput] but NOT WithMetaData
      case o: Output =>
        process(o, parents)
        parents.push(o)
        o.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        // Phase 7: Only write metadata if non-empty
        if o.metadata.nonEmpty then bastWriter.writeMetadataCount(o.metadata)

      // Input extends Branch[OccursInInput] but NOT WithMetaData
      case i: Input =>
        process(i, parents)
        parents.push(i)
        i.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        // Phase 7: Only write metadata if non-empty
        if i.metadata.nonEmpty then bastWriter.writeMetadataCount(i.metadata)

      case branch: Branch[?] with WithMetaData =>
        process(branch, parents) // Writes node data + contents count
        parents.push(branch)
        branch.contents.foreach { value => traverse(value, parents) } // Write contents items
        parents.pop()
        // Phase 7: Only write metadata if non-empty (flag was set in writer)
        if branch.metadata.nonEmpty then bastWriter.writeMetadataCount(branch.metadata)

      // Non-Branch leaf definitions with metadata (Author, User, Term, State, Invariant, etc.)
      // These need their metadata written but have no contents to traverse
      case wm: WithMetaData =>
        process(wm, parents)
        // Phase 7: Only write metadata if non-empty
        if wm.metadata.nonEmpty then bastWriter.writeMetadataCount(wm.metadata)

      case _ =>
        super.traverse(definition, parents) // Use default traversal for non-branches
    }
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
