/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.URL
import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}
import fastparse.*
import fastparse.MultiLineWhitespace.*

private[parsing] trait EpicParser {
  this: VitalDefinitionParser =>

  private def vagueStep[u: P]: P[VagueInteraction] = {
    P(
      Index ~ is ~ literalString ~ literalString ~ literalString ~/ withMetaData ~ Index
    )./.map { case (start, from, relationship, to, descriptives, end) =>
      VagueInteraction(at(start, end), from, relationship, to, descriptives.toContents)
    }
  }

  private def arbitraryStep[u: P]: P[ArbitraryInteraction] = {
    P(
      Index ~ Keywords.from ~/ anyInteractionRef ~
        literalString ~ to.? ~ anyInteractionRef ~/ withMetaData ~ Index
    )./.map { case (start, from, ls, to, descriptives, end) =>
      ArbitraryInteraction(at(start, end), from, ls, to, descriptives.toContents)
    }
  }

  private def sendMessageStep[u: P]: P[SendMessageInteraction] = {
    P(
      Index ~ Keywords.send ~ messageRef ~ from ~ anyInteractionRef ~ to ~ processorRef ~/ withMetaData ~ Index
    )./.map { case (start, message, from, to, descriptives, end) =>
      SendMessageInteraction(at(start, end), from, message, to, descriptives.toContents)
    }
  }

  private def selfProcessingStep[u: P]: P[SelfInteraction] = {
    P(
      Index ~ Keywords.`for` ~ anyInteractionRef ~ is ~ literalString ~/ withMetaData ~ Index
    )./.map { case (start, fromTo, proc, descriptives, end) =>
      SelfInteraction(at(start, end), fromTo, proc, descriptives.toContents)
    }
  }

  private def focusOnGroupStep[u: P]: P[FocusOnGroupInteraction] = {
    P(
      Index ~ Keywords.focus ~ userRef ~ Keywords.on ~ groupRef ~/ withMetaData ~ Index
    )./.map { case (start, userRef, groupRef, descriptives, end) =>
      FocusOnGroupInteraction(at(start, end), userRef, groupRef, descriptives.toContents)
    }
  }

  private def directUserToURL[u: P]: P[DirectUserToURLInteraction] = {
    P(
      Index ~ Keywords.direct ~ userRef ~/ to ~ httpUrl ~/ withMetaData ~ Index
    )./.map { case (start, user, url, descriptives, end) =>
      DirectUserToURLInteraction(at(start, end), user, url, descriptives.toContents)
    }
  }

  private def showOutputStep[u: P]: P[ShowOutputInteraction] = {
    P(
      Index ~ Keywords.show ~/ outputRef ~ to ~ userRef ~/ withMetaData ~ Index
    )./.map { case (start, from, to, descriptives, end) =>
      // `show X to U` carries no relationship in the source, but a TwoReferenceInteraction with an
      // empty one is rejected by validation — so every `show` step was unvalidatable and no
      // spelling could fix it. Synthesize the word instead of widening the syntax: the relationship
      // reads as "<from> <relationship> <to>", making this "X shown to U", which is the past-tense
      // form of the step itself and how it should read in a generated diagram.
      val loc = at(start, end)
      ShowOutputInteraction(loc, from, LiteralString(loc, "shown"), to, descriptives.toContents)
    }
  }

  private def takeInputStep[u: P]: P[TakeInputInteraction] = {
    P(
      Index ~ Keywords.take ~/ inputRef ~ from ~ userRef ~/ withMetaData ~ Index
    )./.map { case (start, input, user, descriptives, end) =>
      TakeInputInteraction(at(start, end), from = user, to = input, descriptives.toContents)
    }
  }

  private def selectInputStep[u: P]: P[SelectInputInteraction] = {
    P(
      Index ~ userRef ~ Keywords.selects ~ inputRef ~/ withMetaData ~ Index
    )./.map { case (start, user, input, descriptives, end) =>
      SelectInputInteraction(at(start, end), from = user, to = input, descriptives.toContents)
    }
  }

  /** A38: the reason may be prose OR the invariant the request violates. Additive — prose stays
    * valid and unwarned, and is the honest spelling when the handler refuses with `error "…"`
    * rather than `require invariant X`.
    *
    * Ordered prose-first, and the two cannot be confused: a [[LiteralString]] starts with a quote
    * and the invariant form starts with the keyword. Mirrors `StatementParser.requireStatement`,
    * which offers the same two shapes to `require` in the same order, and like it does NOT put a
    * cut after `invariant` — `Keywords.keyword` already ends in `./`, so the keyword itself
    * commits, and adding another `~/` only removes the parser's ability to report a better error.
    */
  private def refusalReason[u: P]: P[LiteralString | InvariantRef] = {
    P(
      literalString.map(ls => ls: LiteralString | InvariantRef) |
        (Index ~ Keywords.invariant ~ pathIdentifier ~ Index).map { case (start, pid, end) =>
          InvariantRef(at(start, end), pid): LiteralString | InvariantRef
        }
    )
  }

  private def refusalStep[u: P]: P[RefusalInteraction] = {
    P(
      Index ~ anyInteractionRef ~ Keywords.refuses ~/ userRef ~ refusalReason ~/
        withMetaData ~ Index
    )./.map { case (start, from, user, reason, descriptives, end) =>
      RefusalInteraction(at(start, end), from = from, to = user, reason, descriptives.toContents)
    }
  }

  private def stepInteractions[u: P]: P[Interaction] = {
    P(
      Keywords.step ~ (focusOnGroupStep | directUserToURL | selectInputStep | refusalStep |
        takeInputStep | showOutputStep | selfProcessingStep | sendMessageStep | arbitraryStep |
        vagueStep)
    )
  }

  private def sequentialInteractions[u: P]: P[SequentialInteractions] = {
    P(
      Index ~ Keywords.sequence ~ open ~ interactions ~ close ~ Index
    )./.map { case (start, interactions, end) =>
      SequentialInteractions(at(start, end), interactions.toContents)
    }
  }

  private def optionalInteractions[u: P]: P[OptionalInteractions] = {
    P(
      Index ~ Keywords.optional ~ open ~ interactions ~ close ~/ Index
    )./.map { case (start, interactions, end) =>
      OptionalInteractions(at(start, end), interactions.toContents)
    }
  }

  private def parallelInteractions[u: P]: P[ParallelInteractions] = {
    P(
      Index ~ Keywords.parallel ~ open ~ interactions ~ close ~/ Index
    )./.map { case (start, interactions, end) =>
      ParallelInteractions(at(start, end), interactions.toContents)
    }
  }

  private def interaction[u: P]: P[Interaction | Comment] = {
    P(
      parallelInteractions | optionalInteractions | sequentialInteractions | stepInteractions | comment
    )
  }

  private def interactions[u: P]: P[Seq[InteractionContainerContents]] = {
    P(interaction.rep(1))
  }

  def useCase[u: P]: P[UseCase] = {
    P(
      Index ~ Keywords.case_ ~/ identifier ~ is ~ open ~ userStory ~
        (undefined(Seq.empty[TwoReferenceInteraction]) | interactions) ~
        close ~ withMetaData ~/ Index
    ).map { case (start, id, userStory, contents, descriptives, end) =>
      UseCase(at(start, end), id, userStory, contents.toContents, descriptives.toContents)
    }
  }

  def userStory[u: P]: P[UserStory] = {
    P(
      Index ~ userRef ~ userStoryVerb ~ to.? ~ literalString ~ so ~ that.? ~ literalString ~ Index
    ).map { case (start, user, capability, benefit, end) =>
      UserStory(at(start, end), user, capability, benefit)
    }
  }

  private def epicInclude[u: P]: P[Include[EpicContents]] = {
    include[u, EpicContents]((p: P[?]) => epicDefinitions(using p.asInstanceOf[P[u]]))
  }

  private[parsing] def epicDefinitions[u: P]: P[Seq[EpicContents]] = {
    P(vitalDefinitionContents | useCase | shownBy | epicInclude)
      .asInstanceOf[P[EpicContents]]
      .rep(1)
  }

  private type EpicBody = (
    UserStory,
    Seq[EpicContents]
  )

  private def epicBody[u: P]: P[EpicBody] =
    P(
      userStory ~ (
        undefined(Seq.empty[EpicContents]) | epicDefinitions
      )./
    )

  def epic[u: P]: P[Epic] = {
    P(
      Index ~ Keywords.epic ~/ identifier ~ is ~ open ~ epicBody ~ close ~ withMetaData ~/ Index
    )./.map { case (start, id, (userStory, contents), descriptives, end) =>
      checkForDuplicateIncludes(contents)
      Epic(at(start, end), id, userStory, contents.toContents, descriptives.toContents)
    }
  }
}
