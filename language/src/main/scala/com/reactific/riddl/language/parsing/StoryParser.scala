package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*

trait StoryParser extends CommonParser with ReferenceParser with GherkinParser {

  def storyDefRef[u: P]: P[StoryCaseUsesRefs] = {
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

  def storyCaseUses[u: P]: P[Seq[StoryCaseUse]] = {
    P(Keywords.uses ~/ open ~ (location ~ storyDefRef ~ briefly).map {
      case (loc, ref, brief) => StoryCaseUse(loc, ref, brief)
    }.rep(0, Punctuation.comma) ~ close)
  }

  def interactionStep[u: P]: P[InteractionStep] = {
    P(
      location ~ Keywords.step ~ wholeNumber ~ is ~ Readability.from ~
        storyDefRef ~ Readability.to ~ storyDefRef ~ briefly
    )./.map { case (loc, stepNo, from, to, brief) =>
      InteractionStep(loc, stepNo, from, to, brief)
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
        (undefined(()).map(_ =>
          (None, None, Seq.empty[StoryCaseUse], Seq.empty[InteractionStep])
        ) |
          ((Keywords.title ~ is ~ literalString).? ~ storyCaseScope.? ~
            storyCaseUses ~ interactionSteps)) ~ close ~ briefly ~ description
    ).map { case (loc, id, (title, scope, uses, steps), brief, description) =>
      StoryCase(loc, id, title, scope, uses, steps, brief, description)
    }
  }

  def actor[u: P]: P[StoryActor] = {
    P(
      location ~ Keywords.actor ~ identifier ~ is ~ literalString.? ~ briefly ~
        description
    ).map { case (loc, id, is_a, brief, description) =>
      StoryActor(loc, id, is_a, brief, description)
    }
  }
  def userStory[u: P]: P[UserStory] = {
    P(
      location ~ actor ~ Keywords.capability ~ is ~ literalString ~
        Keywords.benefit ~ is ~ literalString
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
    P("").map(_ => Seq.empty[StoryOption]) // FIXME: What options are needed?
  }

  def storyInclude[u: P]: P[Include] = {
    include[StoryDefinition, u](storyDefinitions(_))
  }

  def storyDefinitions[u: P]: P[Seq[StoryDefinition]] = {
    P(storyCase | example | term | author | storyInclude).rep(0)
  }

  def storyBody[u: P]: P[
    (
      Seq[StoryOption],
      Option[UserStory],
      Seq[java.net.URL],
      Seq[StoryDefinition]
    )
  ] = { P(storyOptions ~ userStory.? ~ shownBy ~ storyDefinitions)./ }

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
        val includes = mapTo[Include](groups.get(classOf[Include]))
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
