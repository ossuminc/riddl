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

  def storyDefRef[u: P]: P[StoryCaseRefs[?]] = {
    P(
      adaptorRef | entityRef | projectionRef | processorRef | sagaRef |
        storyRef | actorRef | contextRef
    )
  }

  def storyCaseScope[u: P]: P[StoryCaseScope] = {
    P(location ~ Keywords.scope ~ domainRef ~ briefly).map {
      case (loc, dr, br) => StoryCaseScope(loc, dr, br)
    }
  }

  def abstractInteraction[u: P]: P[(AbstractInteraction, StoryCaseRefs[?])] = {
    P(location ~ literalString ~ Readability.to.? ~ storyDefRef).map {
      case (loc, ls, sdr) => AbstractInteraction(loc, ls) -> sdr
    }
  }

  def tellInteraction[u: P]: P[(TellMessageInteraction, StoryCaseRefs[?])] = {
    P(
      location ~ Keywords.tell ~ messageConstructor ~ Readability.to.? ~
        entityRef
    ).map { case (loc, mc, sdr) => TellMessageInteraction(loc, mc) -> sdr }
  }

  def publishInteraction[
    u: P
  ]: P[(PublishMessageInteraction, StoryCaseRefs[?])] = {
    P(
      location ~ Keywords.publish ~ messageConstructor ~ Readability.to.? ~
        processorRef
    ).map { case (loc, mc, pr) => PublishMessageInteraction(loc, mc) -> pr }
  }

  def interactionRelationship[
    u: P
  ]: P[(InteractionRelationship, StoryCaseRefs[?])] = {
    P(abstractInteraction./ | tellInteraction./ | publishInteraction./)
  }

  def interactionStep[u: P]: P[InteractionStep] = {
    P(
      location ~ Keywords.step ~ Readability.from.? ~ storyDefRef ~
        interactionRelationship ~ briefly
    )./.map { case (loc, from, ir, brief) =>
      InteractionStep(loc, from, ir._1, ir._2, brief)
    }
  }

  def interactionSteps[u: P]: P[Seq[InteractionStep]] = {
    P(
      Keywords.interaction ~ is ~ open ~
        interactionStep.rep(0, Punctuation.comma) ~ close
    )./
  }

  def storyCase[u: P]: P[StoryCase] = {
    P(
      location ~ Keywords.case_ ~/ identifier ~ Readability.is ~ open ~
        (undefined((LiteralString.empty, None, Seq.empty[InteractionStep])) |
          (Keywords.title ~ is ~ literalString ~ storyCaseScope.? ~
            interactionSteps)) ~ close ~ briefly ~ description
    ).map { case (loc, id, (title, scope, steps), brief, description) =>
      StoryCase(loc, id, title, scope, steps, brief, description)
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
      location ~ Keywords.story ~ identifier ~ is ~ open ~ storyBody ~ close ~
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
