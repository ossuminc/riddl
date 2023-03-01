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

private[parsing] trait EpicParser
    extends CommonParser
    with ReferenceParser
    with GherkinParser {

  private def arbitraryStoryRef[u: P]: P[Reference[Definition]] = {
    processorRef | sagaRef | functionRef
  }

  private def arbitraryStep[u: P]: P[ArbitraryStep] = {
    P(
      location ~ Keywords.step ~ Readability.from.? ~ arbitraryStoryRef ~
        literalString ~ Readability.to.? ~ arbitraryStoryRef ~ briefly
    )./.map { case (loc, from, ls, to, brief) =>
      ArbitraryStep(loc, from, ls, to, brief)
    }
  }

  private def selfProcessingStep[u: P]: P[SelfProcessingStep] = {
    P(
      location ~ Keywords.step ~ Readability.for_.? ~
        (arbitraryStoryRef | actorRef) ~ literalString ~ briefly
    )./.map { case (loc, ref, proc, brief) =>
      SelfProcessingStep(loc, ref, proc, brief)
    }
  }

  private def takeOutputStep[u: P]: P[TakeOutputStep] = {
    P(
      location ~ Keywords.step ~ Readability.from.? ~ outputRef ~
        literalString ~ Readability.to.? ~ actorRef ~ briefly
    )./.map { case (loc, output, rel, actor, brief) =>
      TakeOutputStep(loc, output, rel, actor, brief)
    }
  }

  private def giveInputStep[u: P]: P[PutInputStep] = {
    P(
      location ~ Keywords.step ~ Readability.from.? ~ actorRef ~ literalString ~
        Readability.to.? ~ inputRef ~ briefly
    )./.map { case (loc, actor, rel, form, brief) =>
      PutInputStep(loc, actor, rel, form, brief)
    }
  }

  private def optionalInteractions[u: P]: P[OptionalInteractions] = {
    P(
      location ~ Keywords.optional./ ~ open ~ interaction ~ close ~
        briefly
    )./.map { case (loc, steps, brief) =>
      OptionalInteractions(loc, steps, brief)
    }
  }

  private def parallelInteractions[u: P]: P[ParallelInteractions] = {
    P(
      location ~ Keywords.parallel./ ~ open ~ interaction ~ close ~
        briefly
    )./.map { case (loc, steps, brief) =>
      ParallelInteractions(loc, steps, brief)
    }
  }

  private def interaction[u: P]: P[Seq[Interaction]] = {
    P(
      parallelInteractions | optionalInteractions | takeOutputStep | giveInputStep |
        arbitraryStep | selfProcessingStep
    ).rep(0, Punctuation.comma./)
  }

  private def useCase[u: P]: P[UseCase] = {
    P(
      location ~ Keywords.case_ ~/ identifier ~ is ~ open ~
        (undefined(
          Option.empty[UserStory],
          Seq.empty[GenericInteraction]
        ) | (userStory.? ~ interaction)) ~
        close ~ briefly ~ description
    ).map { case (loc, id, (userStory, steps), brief, description) =>
      UseCase(loc, id, userStory, steps, brief, description)
    }
  }

  def userStory[u: P]: P[UserStory] = {
    P(
      location ~ actorRef ~ Readability.wants ~ Readability.to.? ~
        literalString ~ Readability.so ~ Readability.that.? ~ is ~ literalString
    ).map { case (loc, actor, capability, benefit) =>
      UserStory(loc, actor, capability, benefit)
    }
  }

  def shownBy[u: P]: P[Seq[java.net.URL]] = {
    P(
      Keywords.shown ~ Readability.by ~ open ~
        httpUrl.rep(0, Punctuation.comma) ~ close
    ).?.map { x => if (x.isEmpty) Seq.empty[java.net.URL] else x.get }
  }

  private def epicOptions[u: P]: P[Seq[EpicOption]] = {
    options[u, EpicOption](StringIn(Options.technology, Options.sync).!) {
      case (loc, Options.sync, _)          => EpicSynchronousOption(loc)
      case (loc, Options.technology, args) => EpicTechnologyOption(loc, args)
      case (_, _, _) => throw new RuntimeException("Impossible case")
    }
  }

  private def epicInclude[u: P]: P[Include[EpicDefinition]] = {
    include[EpicDefinition, u](epicDefinitions(_))
  }

  private def epicDefinitions[u: P]: P[Seq[EpicDefinition]] = {
    P(useCase | term | epicInclude).rep(0)
  }

  type EpicBody = (
    Seq[EpicOption],
    Option[UserStory],
    Seq[java.net.URL],
    Seq[EpicDefinition]
  )

  private def epicBody[u: P]: P[EpicBody] = {
    P(
      undefined(
        (
          Seq.empty[EpicOption],
          Option.empty[UserStory],
          Seq.empty[java.net.URL],
          Seq.empty[EpicDefinition]
        )
      ) |
        (epicOptions ~ userStory.? ~ shownBy ~ epicDefinitions)
    )./
  }

  def epic[u: P]: P[Epic] = {
    P(
      location ~ Keywords.epic ~/ identifier ~ authorRefs ~ is ~ open ~
        epicBody ~ close ~
        briefly ~ description
    ).map {
      case (
            loc,
            id,
            authors,
            (options, userStory, shownBy, definitions),
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
