/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.URL
import com.ossuminc.riddl.language.AST.*
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
      ShowOutputInteraction(at(start, end), from, LiteralString.empty, to, descriptives.toContents)
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

  private def stepInteractions[u: P]: P[Interaction] = {
    P(
      Keywords.step ~ (focusOnGroupStep | directUserToURL | selectInputStep | takeInputStep |
        showOutputStep | selfProcessingStep | sendMessageStep | arbitraryStep | vagueStep)
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
      Index ~ userRef ~ wants ~ to.? ~ literalString ~ so ~ that.? ~ literalString ~ Index
    ).map { case (start, user, capability, benefit, end) =>
      UserStory(at(start, end), user, capability, benefit)
    }
  }

  private def epicInclude[u: P]: P[Include[EpicContents]] = {
    include[u, EpicContents](epicDefinitions(_))
  }

  private def epicDefinitions[u: P]: P[Seq[EpicContents]] = {
    P(vitalDefinitionContents | useCase | shownBy | epicInclude).asInstanceOf[P[EpicContents]].rep(1)
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
