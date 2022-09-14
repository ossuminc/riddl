package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*

trait StoryParser extends CommonParser
  with ReferenceParser with GherkinParser {

  type AgileStory = (LiteralString, LiteralString, LiteralString)

  def agileStory[u: P]: P[AgileStory] = {
    P(Keywords.role ~ is ~ literalString ~
      Keywords.capability ~ is ~ literalString ~
      Keywords.benefit ~ is ~ literalString)
  }

  type ShownBy = Seq[java.net.URL]

  def shownBy[u: P]: P[ShownBy] = {
    P(Keywords.shown ~ Readability.by ~ open ~
      httpUrl.rep(1, Punctuation.comma)
      ~ close).?.map { x =>
      if (x.isEmpty) Seq.empty[java.net.URL] else x.get
    }
  }

  def c4DefRef[u: P]: P[Reference[ContextDefinition]] = {
    P(
      adaptorRef | entityRef | functionRef | projectionRef | processorRef |
        sagaRef
    )
  }

  def c4Component[u: P]: P[C4.Component] = {
    P(
      location ~ Keywords.component ~ identifier ~ is ~ open ~
        c4DefRef.?
        ~ close ~ briefly ~ description
    ).map {
      case (loc, id, refs, brief, description) =>
        C4.Component(loc, id, refs, brief, description)
    }
  }

  def c4Container[u: P]: P[C4.Container] = {
    P(
      location ~ Keywords.container ~ identifier ~ is ~ open ~ (
        undefined(()).map { _ => (None,Seq.empty[C4.Component])} |
        (contextRef.? ~ c4Component.rep(0))
      ) ~ close ~ briefly ~ description
    ).map {
      case (loc, id, (contextRef, components), briefly, description) =>
        C4.Container(loc, id, contextRef, components, briefly, description)
    }
  }

  def c4Actor[u: P]: P[C4.Actor] = {
    P(
      location ~ Keywords.actor ~ identifier ~ open ~
        close ~ briefly ~ description
    ).map {
      case (loc, id, brief, description) =>
        C4.Actor(loc, id, brief, description)
    }
  }

  def c4Interaction[u:P]: P[C4.Interaction] = {
    P(
      location ~ Keywords.interaction ~ identifier ~ is ~ open ~ (
        undefined(()).map { _ => (0L, None, None)} |
        ( Keywords.step ~ integer ~
          Readability.from ~ designElementRef.? ~
          Readability.to ~ designElementRef.?
        )
      ) ~ close ~ briefly ~ description
    ).map {
      case (loc, id, (step, from, to), brief, description) =>
        C4.Interaction(loc, id, step, from, to, brief, description )
    }
  }

  def c4Context[u: P]: P[C4.Context] = {
    P(
      location ~ Keywords.context ~ identifier ~ is ~ open ~ (
        undefined(()).map( _ =>
          (None, None, Seq.empty[C4.Container], Seq.empty[C4.Interaction])) |
        ( c4Actor.? ~ domainRef.? ~
          c4Container.rep(0) ~ c4Interaction.rep(0) )
      ) ~ close ~ briefly ~ description
    ).map {
      case (loc, id, (actor, ref, containers, interactions), brief, description) =>
        C4.Context(loc, id, ref, actor, containers, interactions, brief, description)
    }
  }

  def design[u:P]: P[C4.Design] = {
    P(location ~ Keywords.design ~ identifier ~ Readability.is ~ open ~
      (Keywords.title ~ Readability.is ~ literalString).? ~
      c4Context.? ~ close ~ briefly ~ description
    ).map {
      case (loc, id, title, context, brief, description) =>
        C4.Design(loc, id, title, context, brief, description )
    }

  }

  def storyOptions[u: P]: P[Seq[StoryOption]] = {
    P("").map(_ => Seq.empty[StoryOption]) // FIXME: What options are needed?
  }

  type Preface = (LiteralString, LiteralString, LiteralString, ShownBy)

  def storyPreface[u:P] : P[Preface] = {
    P( agileStory ~ shownBy)
  }

  def storyInclude[u:P]: P[Include] = {
    include[StoryDefinition,u](storyDefinitions(_))
  }

  def storyDefinitions[u:P]: P[Seq[StoryDefinition]] = {
    P(design | example | term | author | storyInclude ).rep(0)
  }

  def storyBody[u:P]: P[(Seq[StoryOption], Preface, Seq[StoryDefinition])] = {
    P(storyOptions ~ storyPreface ~ storyDefinitions )
  }

  def story[u: P]: P[Story] = {
    P( location ~ Keywords.story ~ identifier ~ is ~ open ~
       storyBody ~ close ~ briefly ~ description
    ).map {
      case (loc, id, (options, (role,capability,benefit, shownBy),
      definitions), briefly, description) =>
        val groups = definitions.groupBy(_.getClass)
        val authors = mapTo[Author](groups.get(classOf[Author]))
        val terms = mapTo[Term](groups.get(classOf[Term]))
        val includes = mapTo[Include](groups.get(classOf[Include]))
        val examples = mapTo[Example](groups.get(classOf[Example]))
        val designs = mapTo[C4.Design](groups.get(classOf[C4.Design]))
        Story(loc, id,
          role, capability, benefit,
          shownBy, designs,
          examples,
          authors, includes, options, terms,
          briefly, description
        )
    }
  }

}
