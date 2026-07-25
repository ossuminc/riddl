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

private[parsing] trait HandlerParser
    extends CommonParser
    with ReferenceParser
    with StatementParser {

  private def onOtherClause[u: P](set: StatementsSet): P[OnOtherClause] = {
    P(
      Index ~ Keywords.onOther ~ is ~/ pseudoCodeBlock(set) ~ withMetaData ~/ Index
    )./ map { case (start, statements, descriptives, end) =>
      OnOtherClause(at(start, end), statements.toContents, descriptives.toContents)
    }
  }

  private def onInitClause[u: P](set: StatementsSet): P[OnInitializationClause] = {
    P(
      Index ~ Keywords.onInit ~ is ~/ pseudoCodeBlock(set) ~ withMetaData ~/ Index
    ).map { case (start, statements, descriptives, end) =>
      OnInitializationClause(at(start, end), statements.toContents, descriptives.toContents)
    }
  }

  private def onTermClause[u: P](set: StatementsSet): P[OnTerminationClause] = {
    P(
      Index ~ Keywords.onTerm ~ is ~/ pseudoCodeBlock(set) ~ withMetaData ~/ Index
    ).map { case (start, statements, descriptives, end) =>
      OnTerminationClause(at(start, end), statements.toContents, descriptives.toContents)
    }
  }

  // Entity-only lifecycle clauses: per-rehydration / per-eviction. Their bodies use the
  // ActivationClause restriction so activation/passivation stay side-effect-free.
  private def onActivationClause[u: P](set: StatementsSet): P[OnActivationClause] = {
    P(
      Index ~ Keywords.onActivate ~ is ~/ pseudoCodeBlock(set.forActivation) ~ withMetaData ~/ Index
    ).map { case (start, statements, descriptives, end) =>
      OnActivationClause(at(start, end), statements.toContents, descriptives.toContents)
    }
  }

  private def onPassivationClause[u: P](set: StatementsSet): P[OnPassivationClause] = {
    P(
      Index ~ Keywords.onPassivate ~ is ~/ pseudoCodeBlock(set.forActivation) ~ withMetaData ~/ Index
    ).map { case (start, statements, descriptives, end) =>
      OnPassivationClause(at(start, end), statements.toContents, descriptives.toContents)
    }
  }

  private def maybeName[u: P]: P[Option[Identifier]] = {
    P((identifier ~ Punctuation.colon).?)
  }

  private def messageOrigins[u: P]: P[Reference[Definition]] = {
    P(inletRef | processorRef | userRef | epicRef)
  }

  /** The message reference accepted after `on`, restricted by processor kind. Projectors are
    * event-only: `on command`/`on query`/`on record` are rejected at parse time (with a helpful
    * message) so errant projectors are caught by the parser, not just the validator. `on event`
    * and `on result` remain valid for projectors. */
  private def onMessageLikeRef[u: P](set: StatementsSet): P[MessageRef] = {
    if set.processor == ProcessorKind.Projector then
      P(
        eventRef | resultRef |
          (P(Keywords.command | Keywords.query | Keywords.record) ~/ Fail.opaque(
            "a projector is event-only: use 'on event' (or 'on result' for its outputs); " +
              "'on command', 'on query' and 'on record' clauses are not allowed"
          )).asInstanceOf[P[MessageRef]]
      )
    else messageRef
  }

  /** A single `on <message>` clause that becomes either an [[OnEventClause]] (event refs) or an
    * [[OnMessageClause]] (command/query/result/record). It must be ONE parser, not two `|`
    * alternatives: `Keywords.on` cuts after matching, so two `on …` alternatives could not
    * backtrack between event and non-event refs. The parsed ref kind then selects the node AND the
    * clause restriction — event bodies parse under `forEvent` (no `require`/`error`). */
  private def onMessageOrEventClause[u: P](set: StatementsSet): P[OnClause] = {
    P(
      Index ~ Keywords.on ~ onMessageLikeRef(set) ~
        (from ~ maybeName ~~ messageOrigins).? ~ is ~/ Index
    ).flatMap { case (start, msgRef, msgOrigins, _) =>
      val bodySet = msgRef match
        case _: EventRef => set.forEvent
        case _           => set
      P(pseudoCodeBlock(bodySet) ~ withMetaData ~/ Index).map { case (statements, descriptives, end) =>
        msgRef match
          case _: EventRef =>
            OnEventClause(
              at(start, end),
              msgRef,
              msgOrigins,
              statements.toContents,
              descriptives.toContents
            )
          case _ =>
            OnMessageClause(
              at(start, end),
              msgRef,
              msgOrigins,
              statements.toContents,
              descriptives.toContents
            )
      }
    }
  }

  /** Reject entity-only lifecycle clauses where they are not allowed, with a helpful message. */
  private def rejectActivatePassivate[u: P]: P[OnClause] = {
    (P(Keywords.onActivate | Keywords.onPassivate) ~/ Fail.opaque(
      "'on activate'/'on passivate' clauses are only allowed in entity handlers; they model an " +
        "entity entering/leaving memory (rehydration/eviction)"
    )).asInstanceOf[P[OnClause]]
  }

  /** The on-clauses legal in a handler depend on the processor kind. Only entities get
    * activate/passivate; everywhere else those keywords are a parse error. The projector
    * event-only restriction is applied inside [[onMessageOrEventClause]] via [[onMessageLikeRef]].
    * The two-word `on init`/`on term`/`on other`/`on activate`/`on passivate` keywords fail
    * cleanly (they don't share `Keywords.on`'s cut), so ordering among them is free. */
  def onClause[u: P](set: StatementsSet): P[OnClause] = {
    if set.processor == ProcessorKind.Entity then
      P(
        onInitClause(set) | onTermClause(set) |
          onActivationClause(set) | onPassivationClause(set) |
          onOtherClause(set) | onMessageOrEventClause(set)
      )
    else
      P(
        onInitClause(set) | onTermClause(set) | onOtherClause(set) |
          rejectActivatePassivate | onMessageOrEventClause(set)
      )
  }

  private def handlerContents[u: P](set: StatementsSet): P[Seq[HandlerContents]] = {
    (onClause(set) | comment)./.rep(0).asInstanceOf[P[Seq[HandlerContents]]]
  }

  private def handlerBody[u: P](set: StatementsSet): P[Seq[HandlerContents]] = {
    undefined(Seq.empty[HandlerContents]) | handlerContents(set)
  }

  def handler[u: P](set: StatementsSet): P[Handler] = {
    P(
      Index ~ Keywords.maybeInitial ~ Keywords.handler ~/ identifier ~ is ~ open ~ handlerBody(
        set
      ) ~ close ~ withMetaData ~/ Index
    )./.map { case (start, isInitial, id, clauses, descriptives, end) =>
      Handler(
        at(start, end),
        id,
        clauses.toContents,
        descriptives.toContents,
        isInitial = isInitial
      )
    }
  }

  def handlers[u: P](set: StatementsSet): P[Seq[Handler]] = handler(set).rep(0)

}
