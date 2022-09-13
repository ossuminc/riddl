package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*

trait StoryParser extends CommonParser
  with ReferenceParser with GherkinParser  {

  type AgileStory = (LiteralString, LiteralString, LiteralString)

  def agileStory[u:P]: P[AgileStory] = {
    P(Keywords.role ~ is ~ literalString ~
      Keywords.capability ~ is ~ literalString ~
      Keywords.benefit ~ is ~ literalString)
  }

  type ShownBy = Seq[java.net.URL]

  def shownBy[u:P]: P[ShownBy] = {
    P(Keywords.shown ~ Readability.by ~ open ~
      httpUrl.rep(1, comma)
      ~ close).?.map { x =>
      if (x.isEmpty) Seq.empty[java.net.URL] else x.get
    }
  }

  type ImplementedBy = Seq[DomainRef]

  def implementedBy[u:P]: P[ImplementedBy] = {
    P(
      (Keywords.implemented ~ Readability.by ~ open ~
        domainRef.rep(1, comma) ~ close).?
        .map(x => if (x.isEmpty) Seq.empty[DomainRef] else x.get)
    )
  }


  def storyOptions[u: P]: P[Seq[StoryOption]] = {
    P("").map(_ => Seq.empty[StoryOption]) // FIXME: What options are needed?
  }

  type Preface = (LiteralString, LiteralString, LiteralString, ShownBy, ImplementedBy)

  def storyPreface[u:P] : P[Preface] = {
    P( agileStory ~ shownBy ~ implementedBy)
  }

  def storyInclude[u:P]: P[Include] = {
    include[StoryDefinition,u](storyDefinitions(_))
  }

  def storyDefinitions[u:P]: P[Seq[StoryDefinition]] = {
    P(example | term | author | storyInclude ).rep(0)
  }

  def storyBody[u:P]: P[(Seq[StoryOption], Preface, Seq[StoryDefinition])] = {
    P(storyOptions ~ storyPreface ~ storyDefinitions )
  }

  def story[u: P]: P[Story] = {
    P( location ~ Keywords.story ~ identifier ~ is ~ open ~
       storyBody ~ close ~ briefly ~ description
    ).map {
      case (loc, id,
      (options, (role,capability,benefit, shownBy, implementedBy),
      definitions), briefly, description) =>
        val groups = definitions.groupBy(_.getClass)
        val authors = mapTo[Author](groups.get(classOf[Author]))
        val terms = mapTo[Term](groups.get(classOf[Term]))
        val includes = mapTo[Include](groups.get(classOf[Include]))
        val examples = mapTo[Example](groups.get(classOf[Example]))
        Story(loc, id,
          role, capability, benefit,
          shownBy, implementedBy,
          examples,
          authors, includes, options, terms,
          briefly, description
        )
    }
  }

}
