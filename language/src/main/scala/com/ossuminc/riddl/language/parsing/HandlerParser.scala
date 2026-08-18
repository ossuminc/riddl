/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import fastparse.*
import fastparse.MultiLineWhitespace.*

private[parsing] trait HandlerParser
    extends CommonParser
    with ReferenceParser
    with StatementParser
    with TypeParser {

  /** A57: `as <name> [: <envelope-type>]`, the optional envelope binding on `on other`.
    *
    * The type is named BARE after the colon, with no `type` keyword. The colon already says a type
    * follows, so a keyword would add nothing a reader did not have — and the two candidates were
    * `message` (untrue: an envelope is not a message) and `type` (correct only because it is
    * vacuous). Both spellings are legal elsewhere in RIDDL, so this is a choice about meaning
    * rather than consistency.
    */
  private def onOtherBinding[u: P]: P[(Option[Identifier], Option[TypeRef])] = {
    P((as ~/ identifier ~ (Punctuation.colon ~/ typeRef).?).?).map {
      case Some((id: Identifier, typ: Option[TypeRef])) => Option(id) -> typ
      case None                                         => None -> None
    }
  }

  private def onOtherClause[u: P](set: StatementsSet): P[OnOtherClause] = {
    P(
      Index ~ Keywords.onOther ~ onOtherBinding ~ is ~/ pseudoCodeBlock(set) ~ withMetaData ~/ Index
    )./ map { case (start, (binding, envelope), statements, descriptives, end) =>
      OnOtherClause(
        at(start, end),
        binding,
        envelope,
        statements.toContents,
        descriptives.toContents
      )
    }
  }

  /** The optional parameter list on `on init`/`on term` (Task 3): `(a: T, b: U)`, or nothing at
    * all. Reuses [[TypeParser.arguments]] -- the same comma-separated `name: type` parser `method`
    * uses for its own argument list -- so nothing new is invented. Whether a leading `Id(...)`
    * parameter is required (as it is for `on term`) is a VALIDATION question, not a grammar one:
    * the evidence (whether parameters were written at all, and what their types are) survives in
    * the AST either way, so both clauses share this one parser.
    */
  private def lifecycleParameters[u: P]: P[Seq[MethodArgument]] = {
    P((Punctuation.roundOpen ~/ arguments ~ Punctuation.roundClose).?).map(_.getOrElse(Seq.empty))
  }

  private def onInitClause[u: P](set: StatementsSet): P[OnInitializationClause] = {
    P(
      Index ~ Keywords.onInit ~ lifecycleParameters ~ is ~/ pseudoCodeBlock(set) ~
        withMetaData ~/ Index
    ).map { case (start, params, statements, descriptives, end) =>
      OnInitializationClause(
        at(start, end),
        params,
        statements.toContents,
        descriptives.toContents
      )
    }
  }

  private def onTermClause[u: P](set: StatementsSet): P[OnTerminationClause] = {
    P(
      Index ~ Keywords.onTerm ~ lifecycleParameters ~ is ~/ pseudoCodeBlock(set) ~
        withMetaData ~/ Index
    ).map { case (start, params, statements, descriptives, end) =>
      OnTerminationClause(
        at(start, end),
        params,
        statements.toContents,
        descriptives.toContents
      )
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
      Index ~ Keywords.onPassivate ~ is ~/ pseudoCodeBlock(
        set.forActivation
      ) ~ withMetaData ~/ Index
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
    * message) so errant projectors are caught by the parser, not just the validator. `on event` and
    * `on result` remain valid for projectors.
    */
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
    * clause restriction — event bodies parse under `forEvent` (no `require`/`error`).
    *
    * A55: an optional local name may be bound to the handled message with ordinary type ascription
    * — `on foo: command Foo { … }`. It reuses [[maybeName]], the same combinator the `from <name>:
    * <origin>` clause uses. There is no ambiguity with a bare message reference: every message ref
    * is keyword-led (`command`/`event`/`query`/`result`/`record`), and `maybeName` contains no cut,
    * so `on command Foo` backtracks out of the optional binding cleanly.
    */
  private def onMessageOrEventClause[u: P](set: StatementsSet): P[OnClause] = {
    P(
      Index ~ Keywords.on ~ maybeName ~ onMessageLikeRef(set) ~
        (from ~ maybeName ~~ messageOrigins).? ~ is ~/ Index
    ).flatMap { case (start, binding, msgRef, msgOrigins, _) =>
      val bodySet = msgRef match
        case _: EventRef => set.forEvent
        case _           => set
      P(pseudoCodeBlock(bodySet) ~ withMetaData ~/ Index).map {
        case (statements, descriptives, end) =>
          msgRef match
            case _: EventRef =>
              OnEventClause(
                at(start, end),
                msgRef,
                msgOrigins,
                binding,
                statements.toContents,
                descriptives.toContents
              )
            case _ =>
              OnMessageClause(
                at(start, end),
                msgRef,
                msgOrigins,
                binding,
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
    * activate/passivate; everywhere else those keywords are a parse error. The projector event-only
    * restriction is applied inside [[onMessageOrEventClause]] via [[onMessageLikeRef]]. The
    * two-word `on init`/`on term`/`on other`/`on activate`/`on passivate` keywords fail cleanly
    * (they don't share `Keywords.on`'s cut), so ordering among them is free.
    */
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
