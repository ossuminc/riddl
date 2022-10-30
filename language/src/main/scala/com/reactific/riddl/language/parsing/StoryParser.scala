/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*

trait StoryParser extends CommonParser with ReferenceParser with GherkinParser {

  def messageTakingRef[u: P]: P[MessageTakingRef[Definition]] = {
    P(adaptorRef | contextRef | entityRef | pipeRef | projectionRef)
  }

  def arbitraryStoryRef[u: P]: P[Reference[Definition]] = {
    messageTakingRef | sagaRef | actorRef | applicationRef
  }

  def arbitraryStep[u: P]: P[ArbitraryStep] = {
    P(
      Keywords.arbitrary ~ Keywords.step ~/ location ~ Readability.from.? ~
        arbitraryStoryRef ~ literalString ~ Readability.to.? ~
        arbitraryStoryRef ~ briefly
    ).map { case (loc, from, ls, to, brief) =>
      ArbitraryStep(loc, from, ls, to, brief)
    }
  }

  def tellMessageStep[u: P]: P[TellMessageStep] = {
    P(
      Keywords.tell ~ Keywords.step ~/ location ~ Readability.from.? ~
        messageTakingRef ~ Keywords.tell ~ messageConstructor ~
        Readability.to.? ~ messageTakingRef ~ briefly
    )./.map { case (loc, from, mc, to, brief) =>
      TellMessageStep(loc, from, mc, to, brief)
    }
  }

  def publishMessageStep[u: P]: P[PublishMessageStep] = {
    P(
      Keywords.publish ~ Keywords.step ~/ location ~ Readability.from.? ~
        messageTakingRef ~ Keywords.publish ~ messageConstructor ~
        Readability.to.? ~ pipeRef ~ briefly
    )./.map { case (loc, msr, mc, pipe, brief) =>
      PublishMessageStep(loc, msr, mc, pipe, brief)
    }
  }

  def subscribeToPipeStep[u: P]: P[SubscribeToPipeStep] = {
    P(
      Keywords.subscribe ~ Keywords.step ~/ location ~ Readability.from.? ~
        messageTakingRef ~ literalString ~ Keywords.subscribe ~
        Readability.to.? ~ pipeRef ~ briefly
    )./.map { case (loc, from, lit, pipeRef, brief) =>
      SubscribeToPipeStep(loc, from, lit, pipeRef, brief)
    }
  }

  def sagaInitiationStep[u: P]: P[SagaInitiationStep] = {
    P(
      Keywords.saga ~ Keywords.step ~/ location ~ Readability.from.? ~
        arbitraryStoryRef ~ literalString ~ Readability.to.? ~ sagaRef ~ briefly
    )./.map { case (loc, from, ls, to, brief) =>
      SagaInitiationStep(loc, from, ls, to, brief)
    }
  }

  def activateViewStep[u: P]: P[ActivateViewStep] = {
    P(
      Keywords.activate ~ Keywords.step ~/ location ~ Readability.from.? ~
        actorRef ~ literalString ~ Readability.to.? ~ viewRef ~ briefly
    )./.map { case (loc, actor, rel, display, brief) =>
      ActivateViewStep(loc, actor, rel, display, brief)
    }
  }

  def applicationFormStep[u: P]: P[ApplicationFormStep] = {
    P(
      location ~ Readability.from.? ~
        actorRef ~ literalString ~ Readability.to.? ~ formRef ~ briefly
    )./.map { case (loc, actor, rel, form, brief) =>
      ApplicationFormStep(loc, actor, rel, form, brief)
    }
  }

  def interactionSteps[u: P]: P[Seq[InteractionStep]] = {
    P((tellMessageStep | publishMessageStep | subscribeToPipeStep |
      sagaInitiationStep | activateViewStep | applicationFormStep |
      arbitraryStep)).rep(0, Punctuation.comma.?)
  }

  def storyCase[u: P]: P[StoryCase] = {
    P(
      location ~ Keywords.case_ ~/ identifier ~ is ~ open ~
        (undefined(Seq.empty[InteractionStep]) | interactionSteps) ~ close ~
        briefly ~ description
    ).map { case (loc, id, steps, brief, description) =>
      StoryCase(loc, id, steps, brief, description)
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

  def storyOptions[u: P]: P[Seq[StoryOption]] = {
    options[u, StoryOption](StringIn(Options.technology, Options.sync).!) {
      case (loc, Options.sync, _)          => StorySynchronousOption(loc)
      case (loc, Options.technology, args) => StoryTechnologyOption(loc, args)
      case (_, _, _) => throw new RuntimeException("Impossible case")
    }
  }

  def storyInclude[u: P]: P[Include[StoryDefinition]] = {
    include[StoryDefinition, u](storyDefinitions(_))
  }

  def storyDefinitions[u: P]: P[Seq[StoryDefinition]] = {
    P(storyCase | example | term | author | storyInclude).rep(0)
  }

  def storyBody[u: P]: P[
    (Seq[StoryOption], UserStory, Seq[java.net.URL], Seq[StoryDefinition])
  ] = { P(storyOptions ~ userStory ~ shownBy ~ storyDefinitions)./ }

  def story[u: P]: P[Story] = {
    P(
      location ~ Keywords.story ~/ identifier ~ is ~ open ~ storyBody ~ close ~
        briefly ~ description
    ).map {
      case (
            loc,
            id,
            (options, userStory, shownBy, definitions),
            briefly,
            description
          ) =>
        val groups = definitions.groupBy(_.getClass)
        val authors = mapTo[Author](groups.get(classOf[Author]))
        val terms = mapTo[Term](groups.get(classOf[Term]))
        val includes = mapTo[Include[StoryDefinition]](groups.get(
          classOf[Include[StoryDefinition]]
        ))
        val examples = mapTo[Example](groups.get(classOf[Example]))
        val cases = mapTo[StoryCase](groups.get(classOf[StoryCase]))
        Story(
          loc,
          id,
          userStory,
          shownBy,
          cases,
          examples,
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
