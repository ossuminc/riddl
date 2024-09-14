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
    )./.map { case (loc, from, relationship, to, descriptives) =>
      VagueInteraction(loc, from, relationship, to, descriptives.toContents)
    }
  }

  private def arbitraryStep[u: P]: P[ArbitraryInteraction] = {
    P(
      location ~ Keywords.from ~/ anyInteractionRef ~
        literalString ~ to.? ~ anyInteractionRef ~/ withDescriptives
    )./.map { case (loc, from, ls, to, descriptives) =>
      ArbitraryInteraction(loc, from, ls, to, descriptives.toContents)
    }
  }

  private def sendMessageStep[u: P]: P[SendMessageInteraction] = {
    P(
      location ~ Keywords.send ~ messageRef ~ from ~ anyInteractionRef ~ to ~ processorRef ~/ withDescriptives
    )./.map { case (loc, message, from, to, descriptives) =>
      SendMessageInteraction(loc, from, message, to, descriptives.toContents)
    }
  }

  private def selfProcessingStep[u: P]: P[SelfInteraction] = {
    P(
      location ~ Keywords.`for` ~ anyInteractionRef ~ is ~ literalString ~/ withDescriptives
    )./.map { case (loc, fromTo, proc, descriptives) =>
      SelfInteraction(loc, fromTo, proc, descriptives.toContents)
    }
  }

  private def focusOnGroupStep[u: P]: P[FocusOnGroupInteraction] = {
    P(
      location ~ Keywords.focus ~ userRef ~ Keywords.on ~ groupRef ~/ withDescriptives
    )./.map { case (loc, userRef, groupRef, descriptives) =>
      FocusOnGroupInteraction(loc, userRef, groupRef, descriptives.toContents)
    }
  }

  private def directUserToURL[u: P]: P[DirectUserToURLInteraction] = {
    P(
      location ~ Keywords.direct ~ userRef ~/ to ~ httpUrl ~/ withDescriptives
    )./.map { case (loc, user, url, descriptives) =>
      DirectUserToURLInteraction(loc, user, url, descriptives.toContents)
    }
  }

  private def showOutputStep[u: P]: P[ShowOutputInteraction] = {
    P(
      location ~ Keywords.show ~/ outputRef ~ to ~ userRef ~/ withDescriptives
    )./.map { case (loc, from, to, descriptives) =>
      ShowOutputInteraction(loc, from, LiteralString.empty, to, descriptives.toContents)
    }
  }

  private def takeInputStep[u: P]: P[TakeInputInteraction] = {
    P(
      location ~ Keywords.take ~/ inputRef ~ from ~ userRef ~/ withDescriptives
    )./.map { case (loc, input, user, descriptives) =>
      TakeInputInteraction(loc, from = user, to = input, descriptives.toContents)
    }
  }

  private def selectInputStep[u: P]: P[SelectInputInteraction] = {
    P(
      location ~ userRef ~ Keywords.selects ~ inputRef ~/ withDescriptives
    )./.map { case (loc, user, input, descriptives) =>
      SelectInputInteraction(loc, from = user, to = input, descriptives.toContents)
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
      SequentialInteractions(loc, interactions.toContents)
    }
  }

  private def optionalInteractions[u: P]: P[OptionalInteractions] = {
    P(
      location ~ Keywords.optional ~ open ~ interactions ~ close
    )./.map { case (loc, interactions) =>
      OptionalInteractions(loc, interactions.toContents)
    }
  }

  private def parallelInteractions[u: P]: P[ParallelInteractions] = {
    P(
      location ~ Keywords.parallel ~ open ~ interactions ~ close
    )./.map { case (loc, interactions) =>
      ParallelInteractions(loc, interactions.toContents)
    }
  }

  private def interaction[u: P]: P[Interaction | Comment] = {
    P(
      parallelInteractions | optionalInteractions | sequentialInteractions | stepInteractions | comment
    )
  }

  private def interactions[u: P]: P[Seq[InteractionContainerContents]] = {
    P( interaction.rep(1) )
  }

  private def useCase[u: P]: P[UseCase] = {
    P(
      location ~ Keywords.case_ ~/ identifier ~ is ~ open ~ userStory ~
        (undefined(Seq.empty[TwoReferenceInteraction]) | interactions) ~
        close ~ withDescriptives
    ).map { case (loc, id, userStory, contents, descriptives) =>
      UseCase(loc, id, userStory, contents.toContents, descriptives.toContents)
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
      location ~ Keywords.epic ~/ identifier ~ is ~ open ~ epicBody ~ close ~ withDescriptives
    )./.map { case (loc, id, (userStory, contents), descriptives) =>
      checkForDuplicateIncludes(contents)
      Epic(loc, id, userStory, contents.toContents, descriptives.toContents)
    }
  }
}
