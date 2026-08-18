/*
 * Copyright 2019-2026 Ossum Inc.
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
  * @param root
  *   The root of the AST (unchanged)
  * @param messages
  *   Any messages generated during serialization
  * @param bytes
  *   The serialized BAST bytes
  * @param nodeCount
  *   Total number of nodes serialized
  * @param stringTableSize
  *   Number of strings in string table
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
  * Converts a RIDDL AST to Binary AST (BAST) format for efficient storage and fast loading. Uses
  * string interning and variable-length encoding to minimize file size.
  *
  * This pass uses the Pass framework for correct AST traversal and delegates the actual
  * serialization to BASTWriter (in the language module).
  *
  * @param input
  *   The AST to serialize
  * @param outputs
  *   Output from previous passes
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
      case c: Correlation       => traverseCorrelation(c, parents)
      case ss: SagaStep         => traverseSagaStep(ss, parents)
      case ws: WhenStatement    => traverseWhenStatement(ws, parents)
      case ms: MatchStatement   => traverseMatchStatement(ms, parents)
      case fs: ForeachStatement => traverseForeachStatement(fs, parents)
      case inv: Invariant       => traverseInvariant(inv, parents)

      // `sequence`/`parallel`/`optional` interaction blocks (2026-08-14). InteractionContainer
      // extends Container but NOT Branch (it has no `id`, so it cannot be a Definition), so it
      // never matched the generic `branch: (Branch[?] & WithMetaData)` case below and fell all
      // the way to the `wm: WithMetaData` catch-all, which calls process() only -- writing the
      // block's header and its contents COUNT (via writeContents) but never descending into the
      // steps themselves. The reader's readContentsDeferred then consumed N nodes that were never
      // written, desynchronising the stream. Same family as BASTImport's openBASTImport/
      // closeBASTImport hooks, but scoped locally here since no other pass needs to push an
      // InteractionContainer onto ParentStack (it isn't a Branch, so it couldn't be) -- every
      // other consumer (PrettifyVisitor.doInteraction, the VisitingPass processValue arm) already
      // recurses into an Interaction's contents manually rather than via Pass-level traversal.
      case ic: InteractionContainer => traverseInteractionContainer(ic, parents)

      // OnClauses (grouped for clarity)
      case oc: OnInitializationClause => traverseOnClause(oc, oc.contents, parents)
      case oc: OnTerminationClause    => traverseOnClause(oc, oc.contents, parents)
      case oc: OnMessageClause        => traverseOnClause(oc, oc.contents, parents)
      case oc: OnEventClause          => traverseOnClause(oc, oc.contents, parents)
      case oc: OnActivationClause     => traverseOnClause(oc, oc.contents, parents)
      case oc: OnPassivationClause    => traverseOnClause(oc, oc.contents, parents)
      case oc: OnOtherClause          => traverseOnClause(oc, oc.contents, parents)

      // Other Branch types with metadata
      case h: Handler  => traverseOnClause(h, h.contents, parents)
      case uc: UseCase => traverseOnClause(uc, uc.contents, parents)
      case g: Group    => traverseOnClause(g, g.contents, parents)
      case o: Output   => traverseOnClause(o, o.contents, parents)
      case i: Input    => traverseOnClause(i, i.contents, parents)

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

  /** A70. Two Contents fields, so the counts must interleave with their items exactly as
    * [[traverseSagaStep]] does — folds first, then the timeout block. Unlike a SagaStep a
    * Correlation IS a Branch, so its contents are traversed with it pushed as the parent.
    */
  private def traverseCorrelation(c: Correlation, parents: ParentStack): Unit = {
    process(c, parents)
    bastWriter.writeContents(c.contents)
    parents.push(c)
    c.contents.foreach { value => traverse(value, parents) }
    bastWriter.writeContents(c.timeoutStatements)
    c.timeoutStatements.toSeq.foreach { value => traverse(value, parents) }
    parents.pop()
    if c.metadata.nonEmpty then bastWriter.writeMetadataCount(c.metadata)
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

  private def traverseForeachStatement(fs: ForeachStatement, parents: ParentStack): Unit = {
    process(fs, parents)
    bastWriter.writeContents(fs.doStatements)
    fs.doStatements.toSeq.foreach { value => traverse(value, parents) }
  }

  /** `sequence { ... }` / `parallel { ... }` / `optional { ... }`. Mirrors [[traverseOnClause]]'s
    * shape exactly (process, then contents, then metadata) but without the push/pop: an
    * [[InteractionContainer]] is a [[Container]] but not a [[Branch]] -- it has no `id` -- so
    * `ParentStack.push`, which requires `Branch[?]`, cannot take it. That omission is harmless
    * here: BASTWriterPass never resolves anything against `parents`, it only orders bytes.
    */
  private def traverseInteractionContainer(ic: InteractionContainer, parents: ParentStack): Unit = {
    process(ic, parents)
    ic.contents.foreach { value => traverse(value, parents) }
    if ic.metadata.nonEmpty then bastWriter.writeMetadataCount(ic.metadata)
  }

  /** A28 + 2026-08-04: an `invariant ... is { <statements> <predicate> }` block form. `Invariant`
    * is a `Leaf`, and the block's statements live in a FIELD of its `condition` (an
    * [[InvariantBlock]], itself not even a `Container`) -- so like [[Correlation]]'s
    * `timeoutStatements` and [[SagaStep]]'s `doStatements`/`undoStatements`, nothing generic ever
    * walks them. `writeInvariant` already writes the statements COUNT (via `writeContents`) as part
    * of the block's encoding, followed -- within that SAME call -- by the predicate and then
    * `requires`. So the items written here land AFTER `requires` on the wire, not right after the
    * count: `process(inv, ...)` runs to completion (id, condition incl. predicate, requires) before
    * this method gets control back, and whatever it appends next is what comes next on the wire.
    * `BASTReader.readInvariantNode` mirrors this exactly: it reads the count, defers building the
    * `InvariantBlock`, reads `requires`, and only THEN reads that many items.
    */
  private def traverseInvariant(inv: Invariant, parents: ParentStack): Unit = {
    process(inv, parents)
    inv.condition match {
      case Some(blk: InvariantBlock) => blk.statements.foreach { value => traverse(value, parents) }
      case _                         => ()
    }
    if inv.metadata.nonEmpty then bastWriter.writeMetadataCount(inv.metadata)
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
    println(
      s"[info] BAST serialization complete: ${bastWriter.getNodeCount} nodes, ${finalizedBytes.length} bytes"
    )
    BASTOutput(
      root,
      Messages.empty,
      finalizedBytes,
      bastWriter.getNodeCount,
      bastWriter.stringTable.size
    )
  }

  override def close(): Unit = ()
}
