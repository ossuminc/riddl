/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*
import Terminals.*
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
      location ~ Keywords.step ~ optionalIdentifier("") ~ is ~ literalString ~/ briefly ~ description
    )./.map { case (loc, id, ls, brief, desc) =>
      VagueInteraction(loc, id, ls, brief, desc)
    }
  }

  private def arbitraryStep[u: P]: P[ArbitraryInteraction] = {
    P(
      location ~ Keywords.step ~ optionalIdentifier(Readability.from) ~/ anyInteractionRef ~
        literalString ~ Readability.to.? ~ anyInteractionRef ~/ briefly ~ description
    )./.map { case (loc, id, from, ls, to, brief, desc) =>
      ArbitraryInteraction(loc, id, from, ls, to, brief, desc)
    }
  }

  private def selfProcessingStep[u: P]: P[SelfInteraction] = {
    P(
      location ~ Keywords.step ~ optionalIdentifier(Readability.for_) ~/
        anyInteractionRef ~ is ~ literalString ~/ briefly ~ description
    )./.map { case (loc, id, fromTo, proc, brief, desc) =>
      SelfInteraction(loc, id, fromTo, proc, brief, desc)
    }
  }

  private def showOutputStep[u: P]: P[ShowOutputInteraction] = {
    P(
      location ~ Keywords.step ~ optionalIdentifier(Keywords.show) ~/
        outputRef ~ Readability.to ~ userRef ~/ briefly ~ description
    )./.map { case (loc, id, outRef, userRef, brief, desc) =>
      ShowOutputInteraction(loc, id, outRef, LiteralString.empty, userRef, brief, desc)
    }
  }

  private def takeInputStep[u: P]: P[TakeInputInteraction] = {
    P(
      location ~ Keywords.step ~ optionalIdentifier(Keywords.take) ~/
        inputRef ~ Readability.from ~ userRef ~/ briefly ~ description
    )./.map { case (loc, id, input, user, brief, desc) =>
      TakeInputInteraction(loc, id, from = user, relationship = LiteralString.empty, to = input, brief, desc)
    }
  }

  private def sequentialInteractions[u: P]: P[SequentialInteractions] = {
    P(
      location ~ Keywords.sequence./ ~ identifier.? ~ open ~ interactions ~ close ~
        briefly ~ description
    )./.map { case (loc, id, steps, brief, desc) =>
      SequentialInteractions(loc, id.getOrElse(Identifier.empty), steps, brief, desc)
    }
  }

  private def optionalInteractions[u: P]: P[OptionalInteractions] = {
    P(
      location ~ Keywords.optional./ ~ identifier.? ~/ open ~ interactions ~ close ~
        briefly ~ description
    )./.map { case (loc, id, steps, brief, desc) =>
      OptionalInteractions(loc, id.getOrElse(Identifier.empty), steps, brief, desc)
    }
  }

  private def parallelInteractions[u: P]: P[ParallelInteractions] = {
    P(
      location ~ Keywords.parallel./ ~ identifier.? ~/ open ~
        interactions ~ close ~ briefly ~ description
    )./.map { case (loc, id, steps, brief, desc) =>
      ParallelInteractions(loc, id.getOrElse(Identifier.empty), steps, brief, desc)
    }
  }

  private def interactions[u: P]: P[Seq[Interaction]] = {
    P(
      parallelInteractions | optionalInteractions | sequentialInteractions |
        takeInputStep | showOutputStep | selfProcessingStep | arbitraryStep | vagueStep
    ).rep(0, Punctuation.comma./)
  }

  private def useCase[u: P]: P[UseCase] = {
    P(
      location ~ Keywords.case_ ~/ identifier ~ is ~ open ~
        (undefined(
          Option.empty[UserStory],
          Seq.empty[GenericInteraction]
        ) | (userStory.? ~ interactions)) ~
        close ~ briefly ~ description
    ).map { case (loc, id, (userStory, steps), brief, description) =>
      UseCase(loc, id, userStory, steps, brief, description)
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
    options[u, EpicOption](StringIn(Options.technology, Options.sync).!) {
      case (loc, Options.sync, _)          => EpicSynchronousOption(loc)
      case (loc, Options.technology, args) => EpicTechnologyOption(loc, args)
    }
  }

  private def epicInclude[u: P]: P[Include[EpicDefinition]] = {
    include[EpicDefinition, u](epicDefinitions(_))
  }

  private def epicDefinitions[u: P]: P[Seq[EpicDefinition]] = {
    P(useCase | term | epicInclude).rep(0)
  }

  type EpicBody = (
    Option[UserStory],
    Seq[java.net.URL],
    Seq[EpicDefinition]
  )

  private def epicBody[u: P]: P[EpicBody] = {
    P(
      undefined(
        (
          Option.empty[UserStory],
          Seq.empty[java.net.URL],
          Seq.empty[EpicDefinition]
        )
      )./ |
        (userStory.? ~ shownBy ~ epicDefinitions)./
    )
  }

  def epic[u: P]: P[Epic] = {
    P(
      location ~ Keywords.epic ~/ identifier ~ authorRefs ~ is ~ open ~
        epicOptions ~ epicBody ~ close ~
        briefly ~ description
    ).map {
      case (
            loc,
            id,
            authors,
            options,
            (userStory, shownBy, definitions),
            briefly,
            description
          ) =>
        val groups = definitions.groupBy(_.getClass)
        val terms = mapTo[Term](groups.get(classOf[Term]))
        val includes = mapTo[Include[EpicDefinition]](
          groups.get(
            classOf[Include[EpicDefinition]]
          )
        )
        val cases = mapTo[UseCase](groups.get(classOf[UseCase]))
        Epic(
          loc,
          id,
          userStory,
          shownBy,
          cases,
          authors,
          includes,
          options,
          terms,
          briefly,
          description
        )
    }
  }

}
