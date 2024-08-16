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
      location ~ is ~ literalString ~ literalString ~ literalString ~/
        briefly ~ description
    )./.map { case (loc, from, relationship, to, brief, description) =>
      VagueInteraction(loc, from, relationship, to, brief, description)
    }
  }

  private def arbitraryStep[u: P]: P[ArbitraryInteraction] = {
    P(
      location ~ Keywords.from ~/ anyInteractionRef ~
        literalString ~ to.? ~ anyInteractionRef ~/ briefly ~ description
    )./.map { case (loc, from, ls, to, brief, desc) =>
      ArbitraryInteraction(loc, from, ls, to, brief, desc)
    }
  }

  private def sendMessageStep[u: P]: P[SendMessageInteraction] = {
    P(
      location ~ Keywords.send ~ messageRef ~ from ~ anyInteractionRef ~
        to ~ processorRef ~/ briefly ~ description
    )./.map { case (loc, message, from, to, brief, description) =>
      SendMessageInteraction(loc, from, message, to, brief, description)
    }
  }

  private def selfProcessingStep[u: P]: P[SelfInteraction] = {
    P(
      location ~ Keywords.for_ ~ anyInteractionRef ~ is ~ literalString ~/ briefly ~ description
    )./.map { case (loc, fromTo, proc, brief, description) =>
      SelfInteraction(loc, fromTo, proc, brief, description)
    }
  }

  private def focusOnGroupStep[u: P]: P[FocusOnGroupInteraction] = {
    P(
      location ~ Keywords.focus ~ userRef ~ on ~
        groupRef ~/ briefly ~ description
    )./.map { case (loc, userRef, groupRef, brief, description) =>
      FocusOnGroupInteraction(loc, userRef, groupRef, brief, description)
    }
  }

  private def directUserToURL[u: P]: P[DirectUserToURLInteraction] = {
    P(
      location ~ Keywords.direct ~ userRef ~/ to ~ httpUrl ~/
        briefly ~ description
    )./.map { case (loc, user, url, brief, description) =>
      DirectUserToURLInteraction(loc, user, url, brief, description)
    }
  }

  private def showOutputStep[u: P]: P[ShowOutputInteraction] = {
    P(
      location ~ Keywords.show ~/
        outputRef ~ to ~ userRef ~/ briefly ~ description
    )./.map { case (loc, from, to, brief, description) =>
      ShowOutputInteraction(loc, from, LiteralString.empty, to, brief, description)
    }
  }

  private def takeInputStep[u: P]: P[TakeInputInteraction] = {
    P(
      location ~ Keywords.take ~/
        inputRef ~ from ~ userRef ~/ briefly ~ description
    )./.map { case (loc, input, user, brief, description) =>
      TakeInputInteraction(loc, from = user, to = input, brief, description)
    }
  }

  private def selectInputStep[u: P]: P[SelectInputInteraction] = {
    P(
      location ~ userRef ~ Keywords.selects ~ inputRef ~/ briefly ~ description
    )./.map { case (loc, user, input, brief, description) =>
      SelectInputInteraction(loc, from = user, to = input, brief, description)
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
      location ~ Keywords.sequence ~ open ~ interactions ~ close ~
        briefly ~ description
    )./.map { case (loc, steps, brief, description) =>
      SequentialInteractions(loc, steps, brief, description)
    }
  }

  private def optionalInteractions[u: P]: P[OptionalInteractions] = {
    P(
      location ~ Keywords.optional ~ open ~ interactions ~ close ~
        briefly ~ description
    )./.map { case (loc, steps, brief, description) =>
      OptionalInteractions(loc, steps, brief, description)
    }
  }

  private def parallelInteractions[u: P]: P[ParallelInteractions] = {
    P(
      location ~ Keywords.parallel ~ open ~
        interactions ~ close ~ briefly ~ description
    )./.map { case (loc, steps, brief, description) =>
      ParallelInteractions(loc, steps, brief, description)
    }
  }

  private def interaction[u: P]: P[Interaction | Comment] = {
    P(
      parallelInteractions | optionalInteractions | sequentialInteractions | stepInteractions | comment
    )
  }

  private def interactions[u: P]: P[Seq[Interaction | Comment]] = {
    interaction.rep(1)
  }

  private def useCase[u: P]: P[UseCase] = {
    P(
      location ~ Keywords.case_ ~/ identifier ~ is ~ open ~
        (undefined(
          (Option.empty[UserStory], Seq.empty[TwoReferenceInteraction])
        )./ | (userStory.? ~ interactions)) ~
        close ~ briefly ~ description
    ).map { case (loc, id, (userStory, contents), brief, description) =>
      UseCase(loc, id, userStory.getOrElse(UserStory()), contents, brief, description)
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

  private def epicBody[u: P]: P[EpicBody] = {
    P(
      undefined(
        (
          UserStory.empty,
          Seq.empty[EpicContents]
        )
      )./ |
        (userStory ~ epicDefinitions)./
    )
  }

  def epic[u: P]: P[Epic] = {
    P(
      location ~ Keywords.epic ~/ identifier ~ is ~ open ~ epicBody ~ close ~ briefly ~ description
    ).map { case (loc, id, (userStory, contents), briefly, description) =>
      checkForDuplicateIncludes(contents)
      Epic(loc, id, userStory, foldDescriptions(contents, briefly, description))
    }
  }
}
