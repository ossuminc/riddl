/*
 * Copyright 2019 Ossum, Inc.
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
      location ~ is ~ literalString ~ literalString ~ literalString ~/ withDescriptives
    )./.map { case (loc, from, relationship, to, withs) =>
      VagueInteraction(loc, from, relationship, to, withs)
    }
  }

  private def arbitraryStep[u: P]: P[ArbitraryInteraction] = {
    P(
      location ~ Keywords.from ~/ anyInteractionRef ~
        literalString ~ to.? ~ anyInteractionRef ~/ withDescriptives
    )./.map { case (loc, from, ls, to, withs) =>
      ArbitraryInteraction(loc, from, ls, to, withs)
    }
  }

  private def sendMessageStep[u: P]: P[SendMessageInteraction] = {
    P(
      location ~ Keywords.send ~ messageRef ~ from ~ anyInteractionRef ~ to ~ processorRef ~/ withDescriptives
    )./.map { case (loc, message, from, to, withs) =>
      SendMessageInteraction(loc, from, message, to, withs)
    }
  }

  private def selfProcessingStep[u: P]: P[SelfInteraction] = {
    P(
      location ~ Keywords.for_ ~ anyInteractionRef ~ is ~ literalString ~/ withDescriptives
    )./.map { case (loc, fromTo, proc, withs) =>
      SelfInteraction(loc, fromTo, proc, withs)
    }
  }

  private def focusOnGroupStep[u: P]: P[FocusOnGroupInteraction] = {
    P(
      location ~ Keywords.focus ~ userRef ~ on ~ groupRef ~/ withDescriptives
    )./.map { case (loc, userRef, groupRef, withs) =>
      FocusOnGroupInteraction(loc, userRef, groupRef, withs)
    }
  }

  private def directUserToURL[u: P]: P[DirectUserToURLInteraction] = {
    P(
      location ~ Keywords.direct ~ userRef ~/ to ~ httpUrl ~/ withDescriptives
    )./.map { case (loc, user, url, withs) =>
      DirectUserToURLInteraction(loc, user, url, withs)
    }
  }

  private def showOutputStep[u: P]: P[ShowOutputInteraction] = {
    P(
      location ~ Keywords.show ~/ outputRef ~ to ~ userRef ~/ withDescriptives
    )./.map { case (loc, from, to, withs) =>
      ShowOutputInteraction(loc, from, LiteralString.empty, to, withs)
    }
  }

  private def takeInputStep[u: P]: P[TakeInputInteraction] = {
    P(
      location ~ Keywords.take ~/ inputRef ~ from ~ userRef ~/ withDescriptives
    )./.map { case (loc, input, user, withs) =>
      TakeInputInteraction(loc, from = user, to = input, withs)
    }
  }

  private def selectInputStep[u: P]: P[SelectInputInteraction] = {
    P(
      location ~ userRef ~ Keywords.selects ~ inputRef ~/ withDescriptives
    )./.map { case (loc, user, input, withs) =>
      SelectInputInteraction(loc, from = user, to = input, withs)
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
      location ~ Keywords.sequence ~ open ~ interactions ~ close
    )./.map { case (loc, interactions) =>
      SequentialInteractions(loc, interactions)
    }
  }

  private def optionalInteractions[u: P]: P[OptionalInteractions] = {
    P(
      location ~ Keywords.optional ~ open ~ interactions ~ close
    )./.map { case (loc, interactions) =>
      OptionalInteractions(loc, interactions)
    }
  }

  private def parallelInteractions[u: P]: P[ParallelInteractions] = {
    P(
      location ~ Keywords.parallel ~ open ~ interactions ~ close
    )./.map { case (loc, interactions) =>
      ParallelInteractions(loc, interactions)
    }
  }

  private def interaction[u: P]: P[Interaction | Descriptives] = {
    P(
      parallelInteractions | optionalInteractions | sequentialInteractions | stepInteractions | descriptive
    )
  }

  private def interactions[u: P]: P[Seq[InteractionContainerContents]] = {
    interaction.rep(1)
  }

  private def useCase[u: P]: P[UseCase] = {
    P(
      location ~ Keywords.case_ ~/ identifier ~ is ~ open ~ userStory ~
        (undefined(Seq.empty[TwoReferenceInteraction]) | interactions) ~
        close
    ).map { case (loc, id, userStory, contents) =>
      UseCase(loc, id, userStory, contents)
    }
  }

  def userStory[u: P]: P[UserStory] = {
    P(
      location ~ userRef ~ wants ~ to.? ~
        literalString ~ so ~ that.? ~ literalString
    ).map { case (loc, user, capability, benefit) =>
      UserStory(loc, user, capability, benefit)
    }
  }

  private def epicInclude[u: P]: P[Include[EpicContents]] = {
    include[u, EpicContents](epicDefinitions(_))
  }

  private def epicDefinitions[u: P]: P[Seq[EpicContents]] = {
    P( vitalDefinitionContents | useCase  | shownBy | epicInclude  ).asInstanceOf[P[EpicContents]].rep(1)
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
      location ~ Keywords.epic ~/ identifier ~ is ~ open ~ epicBody ~ close
    )./.map { case (loc, id, (userStory, contents)) =>
      checkForDuplicateIncludes(contents)
      Epic(loc, id, userStory, contents)
    }
  }
}
