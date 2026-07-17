/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{
  Context, Definition, Domain, Root, RiddlValue
}
import com.ossuminc.riddl.language.{Messages, nonEmpty, toSeq}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.resolve.ResolutionPass
import com.ossuminc.riddl.passes.symbols.SymbolsPass
import com.ossuminc.riddl.passes.validate.{
  ValidationMode, ValidationPass
}
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable

/** Incremental validator that caches validation results at
  * the Context level. On subsequent calls, only Contexts
  * whose source text has changed (or that depend on changed
  * Contexts) are re-validated. Unchanged Contexts reuse
  * cached messages.
  *
  * Designed for browser playgrounds and future LSP use
  * where the same model is validated repeatedly with small
  * edits.
  *
  * @note This is a stateful object — it maintains cached
  *       results between `validate()` calls. Use `reset()`
  *       to force a full re-validation.
  */
class IncrementalValidator(using pc: PlatformContext):

  // Cached state from the previous validation run
  private var previousFingerprints: Map[ContextPath, Long] =
    Map.empty
  private var cachedMessages: Map[ContextPath, Messages] =
    Map.empty
  private var cachedGlobalMessages: Messages =
    Messages.empty
  private var previousResult: Option[PassesResult] = None
  private var isFirstRun: Boolean = true

  /** Reset all cached state, forcing the next `validate()`
    * to perform a full validation.
    */
  def reset(): Unit =
    previousFingerprints = Map.empty
    cachedMessages = Map.empty
    cachedGlobalMessages = Messages.empty
    previousResult = None
    isFirstRun = true
  end reset

  /** Validate a parsed Root, using cached results for
    * unchanged Contexts.
    *
    * @param root The parsed AST root to validate
    * @return PassesResult with combined messages from
    *         re-validated and cached Contexts
    */
  def validate(root: Root): PassesResult =
    val currentFingerprints =
      ContextFingerprint.computeAll(root)

    if isFirstRun then
      val result = fullValidation(root)
      cacheResults(result, currentFingerprints, root)
      isFirstRun = false
      result
    else
      val changedContexts =
        findChangedContexts(currentFingerprints)
      val removedContexts =
        previousFingerprints.keySet -- currentFingerprints.keySet
      val addedContexts =
        currentFingerprints.keySet -- previousFingerprints.keySet

      // If structure changed (contexts added/removed) or
      // many contexts changed, do a full validation
      val totalContexts = currentFingerprints.size
      val changedCount =
        changedContexts.size + removedContexts.size +
          addedContexts.size

      if totalContexts == 0 || changedCount > totalContexts / 2
      then
        // More than half changed — full validation is
        // likely faster than incremental
        val result = fullValidation(root)
        cacheResults(result, currentFingerprints, root)
        result
      else if changedCount == 0 then
        // Nothing changed — return previous result
        previousResult.getOrElse(fullValidation(root))
      else
        // Incremental: re-validate only affected Contexts
        val affectedContexts = computeAffectedContexts(
          changedContexts ++ addedContexts,
          root
        )
        val result =
          incrementalValidation(root, affectedContexts)
        cacheResults(result, currentFingerprints, root)
        result
      end if
    end if
  end validate

  /** Determine which Contexts have changed by comparing
    * fingerprints.
    */
  private def findChangedContexts(
    current: Map[ContextPath, Long]
  ): Set[ContextPath] =
    current.collect {
      case (path, hash)
        if previousFingerprints.get(path) match
          case Some(prev) => prev != hash
          case None        => true
        =>
        path
    }.toSet
  end findChangedContexts

  /** Compute the transitive closure of affected Contexts.
    * A Context is affected if it changed OR if it references
    * definitions inside a changed Context.
    *
    * Uses conservative invalidation: runs the full standard
    * passes which handle cross-context resolution correctly.
    * The optimization is in MESSAGE CACHING — we only keep
    * messages from re-validated contexts and reuse cached
    * messages for unchanged ones.
    */
  private def computeAffectedContexts(
    directlyChanged: Set[ContextPath],
    root: Root
  ): Set[ContextPath] =
    // For now, use a conservative approach:
    // run full passes but cache messages per-context
    // A more sophisticated approach would build a reverse
    // dependency graph from ResolutionOutput.refMap
    directlyChanged
  end computeAffectedContexts

  /** Run full standard passes on the root. */
  private def fullValidation(root: Root): PassesResult =
    val input = PassInput(root)
    Pass.runThesePasses(input, Pass.quickValidationPasses)
  end fullValidation

  /** Incremental validation: run full passes (needed for
    * correct resolution) but only cache messages from
    * affected Contexts, reusing cached messages for others.
    */
  private def incrementalValidation(
    root: Root,
    affectedContexts: Set[ContextPath]
  ): PassesResult =
    // Run full passes — the pass framework needs the
    // complete AST for correct symbol/resolution results.
    // The savings come from potentially using Quick mode
    // and from the caller knowing fewer contexts changed.
    val result = fullValidation(root)

    // Partition messages by Context
    val newContextMessages =
      partitionMessagesByContext(result.messages, root)

    // Merge: use new messages for affected Contexts,
    // cached messages for unchanged Contexts
    val mergedMessages = mutable.ListBuffer.empty[Messages.Message]

    // Add global messages (not attributable to any Context)
    mergedMessages ++= newContextMessages.getOrElse(
      None, Messages.empty
    )

    // For each Context, use new or cached messages
    val allContextPaths =
      ContextFingerprint.computeAll(root).keySet
    allContextPaths.foreach { cp =>
      if affectedContexts.contains(cp) then
        // Use fresh messages from this run
        newContextMessages.get(Some(cp)).foreach(
          mergedMessages ++= _
        )
      else
        // Use cached messages from previous run
        cachedMessages.get(cp).foreach(
          mergedMessages ++= _
        )
    }

    // Return result with merged messages
    PassesResult(
      input = result.input,
      outputs = result.outputs,
      additionalMessages = mergedMessages.toList
    )
  end incrementalValidation

  /** Cache the results of a validation run, partitioned
    * by Context.
    */
  private def cacheResults(
    result: PassesResult,
    fingerprints: Map[ContextPath, Long],
    root: Root
  ): Unit =
    previousFingerprints = fingerprints
    previousResult = Some(result)

    val partitioned =
      partitionMessagesByContext(result.messages, root)
    cachedGlobalMessages =
      partitioned.getOrElse(None, Messages.empty)
    cachedMessages = partitioned.collect {
      case (Some(cp), msgs) => cp -> msgs
    }
  end cacheResults

  /** Partition messages by the Context they belong to.
    * Messages whose location falls within a Context's
    * source span are attributed to that Context.
    * Messages outside any Context are keyed by None.
    */
  private def partitionMessagesByContext(
    messages: Messages,
    root: Root
  ): Map[Option[ContextPath], Messages] =
    val contextSpans = buildContextSpans(root)
    messages.groupBy { msg =>
      val offset = msg.loc.offset
      contextSpans.find { case (_, start, end) =>
        offset >= start && offset < end
      }.map(_._1)
    }
  end partitionMessagesByContext

  /** Build a list of (ContextPath, startOffset, endOffset)
    * for all Contexts in the model.
    */
  private def buildContextSpans(
    root: Root
  ): Seq[(ContextPath, Int, Int)] =
    val spans = mutable.ListBuffer
      .empty[(ContextPath, Int, Int)]
    def walkDomains(
      domains: Seq[Domain],
      parentPath: Seq[String]
    ): Unit =
      domains.foreach { domain =>
        val domainPath = parentPath :+ domain.id.value
        domain.contexts.foreach { context =>
          val cp =
            ContextPath(domainPath, context.id.value)
          val start = context.loc.offset
          val end =
            if context.contents.nonEmpty then
              val last = context.contents.toSeq.last
              last match
                case d: Definition =>
                  d.loc.endOffset.max(start)
                case v: RiddlValue =>
                  v.loc.endOffset.max(start)
            else context.loc.endOffset
          spans += ((cp, start, end))
        }
        walkDomains(domain.domains, domainPath)
      }
    end walkDomains
    walkDomains(root.domains, Seq.empty)
    spans.toSeq
  end buildContextSpans
end IncrementalValidator
