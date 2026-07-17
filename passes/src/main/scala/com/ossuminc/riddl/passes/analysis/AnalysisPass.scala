/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{Pass, PassCreators, PassInput, PassesResult}
import com.ossuminc.riddl.passes.diagrams.DiagramsPass
import com.ossuminc.riddl.passes.resolve.ResolutionPass
import com.ossuminc.riddl.passes.stats.StatsPass
import com.ossuminc.riddl.passes.symbols.SymbolsPass
import com.ossuminc.riddl.passes.validate.ValidationPass
import com.ossuminc.riddl.utils.PlatformContext

/** Comprehensive model analysis that produces an AnalysisResult.
  *
  * AnalysisPass runs the required passes to collect all analysis data:
  *   - SymbolsPass: Symbol table and hierarchy
  *   - ResolutionPass: Reference resolution and usage tracking
  *   - ValidationPass: Handler completeness categorization
  *   - StatsPass: Metrics and completeness
  *   - DiagramsPass: Context relationships and use case data
  *   - MessageFlowPass: Message producer/consumer graph
  *   - EntityLifecyclePass: Entity state machines
  *   - DependencyAnalysisPass: Cross-context/entity/type dependencies
  */
object AnalysisPass:

  /** The passes required for comprehensive analysis.
    *
    * Order matters - each pass may depend on outputs from previous passes.
    */
  def analysisPasses(using PlatformContext): PassCreators =
    Seq(
      SymbolsPass.creator(),
      ResolutionPass.creator(),
      ValidationPass.creator(),
      StatsPass.creator(),
      DiagramsPass.creator(),
      MessageFlowPass.creator(),
      EntityLifecyclePass.creator(),
      DependencyAnalysisPass.creator()
    )

  /** Run analysis passes on a parsed model.
    *
    * @param root
    *   The parsed RIDDL model
    * @return
    *   AnalysisResult containing consolidated pass outputs
    */
  def analyze(root: Root)(using PlatformContext): AnalysisResult =
    val input = PassInput(root)
    val passesResult = Pass.runThesePasses(input, analysisPasses)
    AnalysisResult.fromPassesResult(passesResult)

  /** Run analysis on a PassesResult that already has required passes run.
    *
    * @param passesResult
    *   Result from running passes (must include required passes)
    * @return
    *   AnalysisResult containing consolidated pass outputs
    */
  def fromPassesResult(passesResult: PassesResult): AnalysisResult =
    AnalysisResult.fromPassesResult(passesResult)

  /** Parse RIDDL source and run analysis.
    *
    * @param input
    *   The RIDDL parser input
    * @return
    *   Either parse/analysis errors or the AnalysisResult
    */
  def analyzeInput(input: RiddlParserInput)(using
    PlatformContext
  ): Either[Messages.Messages, AnalysisResult] =
    TopLevelParser.parseInput(input) match
      case Left(messages) => Left(messages)
      case Right(root) =>
        val result = analyze(root)
        if result.messages.hasErrors then Left(result.messages)
        else Right(result)

end AnalysisPass
