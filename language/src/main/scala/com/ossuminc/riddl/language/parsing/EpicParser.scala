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
  this: CommonParser with ReferenceParser =>

  private def optionalIdentifier[u: P](keyword: String): P[Identifier] = {
    P(
      keyword.map(_ => Identifier.empty) | (identifier ~ keyword)
    )
  }

  private def vagueStep[u: P]: P[VagueInteraction] = {
    P(
      location ~ optionalIdentifier("") ~ is ~ literalString ~ literalString ~ literalString ~/
        briefly ~ description
    )./.map { case (loc, id, from, relationship, to, brief, description) =>
      VagueInteraction(loc, id, from, relationship, to, brief, description)
    }
  }

  private def arbitraryStep[u: P]: P[ArbitraryInteraction] = {
    P(
      location ~ optionalIdentifier(Keyword.from) ~/ anyInteractionRef ~
        literalString ~ Readability.to.? ~ anyInteractionRef ~/ briefly ~ description
    )./.map { case (loc, id, from, ls, to, brief, desc) =>
      ArbitraryInteraction(loc, id, from, ls, to, brief, desc)
    }
  }

  private def sendMessageStep[u: P]: P[SendMessageInteraction] = {
    P(
      location ~ optionalIdentifier(Keyword.send) ~/ messageRef ~
        Readability.from ~ anyInteractionRef ~ Readability.to ~ processorRef ~/
        briefly ~ description
    )./.map { case (loc, id, message, from, to, brief, description) =>
      SendMessageInteraction(loc, id, from, message, to, brief, description)
    }
  }

  private def selfProcessingStep[u: P]: P[SelfInteraction] = {
    P(
      location ~ optionalIdentifier(Keyword.for_) ~/
        anyInteractionRef ~ is ~ literalString ~/ briefly ~ description
    )./.map { case (loc, id, fromTo, proc, brief, description) =>
      SelfInteraction(loc, id, fromTo, proc, brief, description)
    }
  }

  private def focusOnGroupStep[u: P]: P[FocusOnGroupInteraction] = {
    P(
      location ~ optionalIdentifier(Keyword.focus) ~/ userRef ~ Readability.on ~
        groupRef ~/ briefly ~ description
    )./.map { case (loc, id, userRef, groupRef, brief, description) =>
      FocusOnGroupInteraction(loc, id, userRef, groupRef, brief, description)
    }
  }

  private def directUserToURL[u: P]: P[DirectUserToURLInteraction] = {
    P(
      location ~ optionalIdentifier(Keyword.direct) ~/ userRef ~/ Readability.to ~ httpUrl ~/
        briefly ~ description
    )./.map { case (loc, id, user, url, brief, description) =>
      DirectUserToURLInteraction(loc, id, user, url, brief, description)
    }
  }

  private def showOutputStep[u: P]: P[ShowOutputInteraction] = {
    P(
      location ~ optionalIdentifier(Keyword.show) ~/
        outputRef ~ Readability.to ~ userRef ~/ briefly ~ description
    )./.map { case (loc, id, from, to, brief, description) =>
      ShowOutputInteraction(loc, id, from, LiteralString.empty, to, brief, description)
    }
  }

  private def takeInputStep[u: P]: P[TakeInputInteraction] = {
    P(
      location ~ optionalIdentifier(Keyword.take) ~/
        inputRef ~ Readability.from ~ userRef ~/ briefly ~ description
    )./.map { case (loc, id, input, user, brief, description) =>
      TakeInputInteraction(loc, id, from = user, relationship = LiteralString.empty, to = input, brief, description)
    }
  }

  private def stepInteractions[u: P]: P[Interaction] = {
    P(
      Keywords.step ~ (focusOnGroupStep | directUserToURL | takeInputStep | showOutputStep | selfProcessingStep |
        sendMessageStep | arbitraryStep | vagueStep)
    )
  }

  private def sequentialInteractions[u: P]: P[SequentialInteractions] = {
    P(
      location ~ Keywords.sequence ~ identifier.? ~ open ~ interactions ~ close ~
        briefly ~ description
    )./.map { case (loc, id, steps, brief, description) =>
      SequentialInteractions(loc, id.getOrElse(Identifier.empty), steps, brief, description)
    }
  }

  private def optionalInteractions[u: P]: P[OptionalInteractions] = {
    P(
      location ~ Keywords.optional ~ identifier.? ~/ open ~ interactions ~ close ~
        briefly ~ description
    )./.map { case (loc, id, steps, brief, description) =>
      OptionalInteractions(loc, id.getOrElse(Identifier.empty), steps, brief, description)
    }
  }

  private def parallelInteractions[u: P]: P[ParallelInteractions] = {
    P(
      location ~ Keywords.parallel ~ identifier.? ~/ open ~
        interactions ~ close ~ briefly ~ description
    )./.map { case (loc, id, steps, brief, description) =>
      ParallelInteractions(loc, id.getOrElse(Identifier.empty), steps, brief, description)
    }
  }

  private def interactions[u: P]: P[Seq[Interaction]] = {
    P(
      parallelInteractions | optionalInteractions | sequentialInteractions | stepInteractions
    ).rep(1, Punctuation.comma./)
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

  private def epicOptions[u: P]: P[Seq[EpicOption]] = {
    options[u, EpicOption](RiddlOptions.epicOptions) {
      case (loc, RiddlOption.sync, _)          => EpicSynchronousOption(loc)
      case (loc, RiddlOption.technology, args) => EpicTechnologyOption(loc, args)
    }
  }

  private def epicInclude[u: P]: P[Include[OccursInEpic]] = {
    include[OccursInEpic, u](epicDefinitions(_))
  }

  private def epicDefinitions[u: P]: P[Seq[OccursInEpic]] = {
    P(useCase | term | epicInclude | comment | authorRef).rep(1)
  }

  type EpicBody = (
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
      location ~ Keywords.epic ~/ identifier ~ is ~ open ~
        epicOptions ~ epicBody ~ close ~ briefly ~ description
    ).map { case (loc, id, options, (userStory, shownBy, contents), briefly, description) =>
      Epic(loc, id, userStory, shownBy, options, contents, briefly, description)
    }
  }
}
