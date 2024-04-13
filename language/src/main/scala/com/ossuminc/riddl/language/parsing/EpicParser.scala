/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*
import Readability.*
import java.net.URL

private[parsing] trait EpicParser {
  this: CommonParser & ReferenceParser =>

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
        literalString ~ Readability.to.? ~ anyInteractionRef ~/ briefly ~ description
    )./.map { case (loc, from, ls, to, brief, desc) =>
      ArbitraryInteraction(loc, from, ls, to, brief, desc)
    }
  }

  private def sendMessageStep[u: P]: P[SendMessageInteraction] = {
    P(
      location ~ Keywords.send ~ messageRef ~ Readability.from ~ anyInteractionRef ~
        Readability.to ~ processorRef ~/ briefly ~ description
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
      location ~ Keywords.focus ~ userRef ~ Readability.on ~
        groupRef ~/ briefly ~ description
    )./.map { case (loc, userRef, groupRef, brief, description) =>
      FocusOnGroupInteraction(loc, userRef, groupRef, brief, description)
    }
  }

  private def directUserToURL[u: P]: P[DirectUserToURLInteraction] = {
    P(
      location ~ Keywords.direct ~ userRef ~/ Readability.to ~ httpUrl ~/
        briefly ~ description
    )./.map { case (loc, user, url, brief, description) =>
      DirectUserToURLInteraction(loc, user, url, brief, description)
    }
  }

  private def showOutputStep[u: P]: P[ShowOutputInteraction] = {
    P(
      location ~ Keywords.show ~/
        outputRef ~ Readability.to ~ userRef ~/ briefly ~ description
    )./.map { case (loc, from, to, brief, description) =>
      ShowOutputInteraction(loc, from, LiteralString.empty, to, brief, description)
    }
  }

  private def takeInputStep[u: P]: P[TakeInputInteraction] = {
    P(
      location ~ Keywords.take ~/
        inputRef ~ Readability.from ~ userRef ~/ briefly ~ description
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
      location ~ userRef ~ Readability.wants ~ Readability.to.? ~
        literalString ~ Readability.so ~ Readability.that.? ~ literalString
    ).map { case (loc, user, capability, benefit) =>
      UserStory(loc, user, capability, benefit)
    }
  }

  def shownBy[u: P]: P[Seq[java.net.URL]] = {
    P(
      Keywords.shown ~ Readability.by ~ open ~
        httpUrl.rep(0, Punctuation.comma) ~ close
    ).?.map { (x: Option[Seq[java.net.URL]]) => x.getOrElse(Seq.empty[java.net.URL]) }
  }

  private def epicOption[u: P]: P[EpicOption] = {
    option[u, EpicOption](RiddlOptions.epicOptions) {
      case (loc, RiddlOption.sync, _)          => EpicSynchronousOption(loc)
      case (loc, RiddlOption.technology, args) => EpicTechnologyOption(loc, args)
      case (loc, RiddlOption.css, args)      => EpicCssOption(loc, args)
      case (loc, RiddlOption.faicon, args)     => EpicIconOption(loc, args)
      case (loc, RiddlOption.kind, args)       => EpicKindOption(loc, args)
    }
  }

  private def epicInclude[u: P]: P[IncludeHolder[OccursInEpic]] = {
    include[u, OccursInEpic](epicDefinitions(_))
  }

  private def epicDefinitions[u: P]: P[Seq[OccursInEpic]] = {
    P(useCase | term | epicInclude | comment | authorRef | epicOption).rep(1)
  }

  private type EpicBody = (
    Option[UserStory],
    Seq[java.net.URL],
    Seq[OccursInEpic]
  )

  private def epicBody[u: P]: P[EpicBody] = {
    P(
      undefined(
        (
          Option.empty[UserStory],
          Seq.empty[java.net.URL],
          Seq.empty[OccursInEpic]
        )
      )./ |
        (userStory.? ~ shownBy ~ epicDefinitions)./
    )
  }

  def epic[u: P]: P[Epic] = {
    P(
      location ~ Keywords.epic ~/ identifier ~ is ~ open ~ epicBody ~ close ~ briefly ~ description
    ).map { case (loc, id, (userStory, shownBy, contents), briefly, description) =>
      val mergedContent = mergeAsynchContent[OccursInEpic](contents)
      Epic(loc, id, userStory, shownBy, mergedContent, briefly, description)
    }
  }
}
