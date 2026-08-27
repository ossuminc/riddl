/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** Unit Tests For FunctionParser */
private[parsing] trait ProjectorParser {
  this: ProcessorParser & StreamingParser =>

  private def projectorInclude[u: P]: P[Include[ProjectorContents]] = {
    include[u, ProjectorContents]((p: P[?]) => projectorDefinitions(using p.asInstanceOf[P[u]]))
  }

  private def updates[u: P]: P[RepositoryRef] =
    P(Keywords.updates ~ repositoryRef)

  private def correlationContent[u: P]: P[CorrelationContents] =
    P(handler(StatementsSet.ProjectorStatements) | comment).asInstanceOf[P[CorrelationContents]]

  private def correlationBody[u: P]: P[Seq[CorrelationContents]] =
    P(is ~ open ~ (undefined(Seq.empty[CorrelationContents]) | correlationContent.rep(1)) ~ close)

  /** A70: the mandatory timeout clause, `times out after "30 days" { … }`.
    *
    * Mandatory on purpose (author's ruling, 2026-08-11): a correlation with no bound was the one
    * case the earlier optional design could not answer, and requiring it makes the unbounded state
    * unrepresentable rather than diagnosed. The duration is an ordinary [[literalString]] so
    * neither ISO-8601 nor Scala `Duration` syntax enters the grammar; `ValidationPass` checks that
    * it parses as a duration, so `times out after "banana"` is an Error rather than accepted.
    *
    * The body takes UNRESTRICTED projector statements. Unlike a fold it MAY have effects — it
    * exists to have one (Computational Model §6.7) — so banning them here would leave it unable to
    * do anything. [[pseudoCodeBlock]] brackets itself (its brace-wrapped form is one of its own
    * alternatives, as `saga_step` uses it), so it must NOT be wrapped in `open`/`close` here — that
    * would demand doubled braces. It admits `???` and requires at least one statement otherwise, so
    * an empty `{ }` is a parse error; `do "nothing"` is the idiom when discarding really is right.
    */
  private def correlationTimeout[u: P]: P[(LiteralString, Seq[Statements])] =
    P(Keywords.timesOutAfter ~/ literalString ~ pseudoCodeBlock(StatementsSet.ProjectorStatements))

  /** A70: a named, keyed accumulation of several events into one command the Repository handles.
    *
    * {{{
    *   correlation Fulfillment by customerId, orderId yields command Sales.RecordFulfillment is {
    *     handler Collect is { on event Sales.OrderPlaced is { set field orderedAt to occurredAt } }
    *   } times out after "30 days" { tell command Ops.ReportStalled to entity Ops.Monitor }
    * }}}
    *
    * A [[commandRef]], not a [[recordRef]] (author's ruling, 2026-08-12): a projector's output is
    * ALWAYS a change to a repository, and a repository is changed by handling a command. Writing
    * `yields record R` therefore no longer parses at all — the wrong KEYWORD dies here, while a
    * `command` naming something that is not a command is left to `ValidationPass`, which is the
    * only place with the resolved referent to judge it.
    *
    * The keys are kept in WRITTEN order and are not sorted: §6.5 makes identity the full tuple, and
    * component order can matter to a generator's composite index. Since `Definition.equals` is
    * structural, sorting them here would silently make two differently-ordered declarations equal.
    */
  private def correlation[u: P]: P[Correlation] = {
    P(
      Index ~ Keywords.correlation ~/ identifier ~ by ~ identifier.rep(1, Punctuation.comma) ~
        Keywords.yields ~ commandRef ~ correlationBody ~ correlationTimeout ~ withMetaData ~ Index
    )./.map { case (start, id, keys, command, contents, (timeout, onTimeout), descriptives, end) =>
      Correlation(
        at(start, end),
        id,
        keys,
        command,
        timeout,
        contents.toContents,
        onTimeout.toContents,
        descriptives.toContents
      )
    }
  }

  private def projectorDefinitions[u: P]: P[Seq[ProjectorContents]] = {
    P(
      processorDefinitionContents(StatementsSet.ProjectorStatements) | updates | correlation |
        projectorInclude
    ).asInstanceOf[P[ProjectorContents]]./.rep(1)
  }

  private def projectorBody[u: P]: P[Seq[ProjectorContents]] = {
    P(
      undefined(Seq.empty[ProjectorContents]) | projectorDefinitions
    )
  }

  /** Parses projector definitions, e.g.
    *
    * {{{
    *   projector myView is {
    *     foo: Boolean
    *     bar: Integer
    *   }
    * }}}
    */
  def projector[u: P]: P[Projector] = {
    P(
      Index ~ Keywords.projector ~/ identifier ~ asShape ~ is ~ open ~ projectorBody ~ close ~
        withMetaData ~ Index
    )./.map { case (start, id, ascribed, contents, descriptives, end) =>
      checkForDuplicateIncludes(contents)
      Projector(
        at(start, end),
        id,
        contents.toContents,
        ascribedShape = ascribed,
        metadata = descriptives.toContents
      )
    }
  }
}
