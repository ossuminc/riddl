/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.Location
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parser rules for Adaptors */
trait AdaptorParser
    extends ReferenceParser with GherkinParser with ActionParser {

  def adaptorOptions[u: P]: P[Seq[AdaptorOption]] = {
    options[u, AdaptorOption](StringIn(Options.technology).!) {
      case (loc, Options.technology, args) => AdaptorTechnologyOption(loc, args)
      case (_, _, _) => throw new RuntimeException("Impossible case")
    }
  }

  def adaptationPrefix[u: P]: P[(Location, Identifier)] = {
    P(location ~ Keywords.adapt ~ identifier ~ is ~ open ~ Readability.from)
  }

  def adaptationSuffix[
    u: P
  ]: P[(Seq[Example], Option[LiteralString], Option[Description])] = {
    P(
      Readability.as ~ open ~ (undefined(Seq.empty[Example]) | examples) ~
        close ~ close ~ briefly ~ description
    )
  }

  def eventCommand[u: P]: P[EventCommandA8n] = {
    P(
      adaptationPrefix ~ eventRef ~ Readability.to ~ commandRef ~
        adaptationSuffix
    ).map { case (loc, id, er, cr, (examples, briefly, description)) =>
      EventCommandA8n(loc, id, er, cr, examples, briefly, description)
    }
  }

  def commandCommand[u: P]: P[CommandCommandA8n] = {
    P(
      adaptationPrefix ~ commandRef ~ Readability.to ~ commandRef ~
        adaptationSuffix
    ).map { case (loc, id, cr1, cr2, (examples, briefly, description)) =>
      CommandCommandA8n(loc, id, cr1, cr2, examples, briefly, description)
    }
  }

  def eventAction[u: P]: P[EventActionA8n] = {
    P(
      adaptationPrefix ~ eventRef ~ Readability.to ~ open ~ actionList ~ close ~
        adaptationSuffix
    ).map { case (loc, id, er, actions, (examples, briefly, description)) =>
      EventActionA8n(loc, id, er, actions, examples, briefly, description)
    }
  }

  def adaptorInclude[u: P]: P[Include[AdaptorDefinition]] = {
    include[AdaptorDefinition, u](adaptorDefinitions(_))
  }

  def adaptorDefinitions[u: P]: P[Seq[AdaptorDefinition]] = {
    P(
      (eventCommand | commandCommand | eventAction | adaptorInclude | term |
        author).rep(1) | undefined(Seq.empty[AdaptorDefinition])
    )
  }

  def adaptor[u: P]: P[Adaptor] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~ Readability.for_ ~
        contextRef ~ is ~ open ~ adaptorOptions ~ adaptorDefinitions ~ close ~
        briefly ~ description
    ).map { case (loc, id, cref, options, defs, briefly, description) =>
      val groups = defs.groupBy(_.getClass)
      val includes = mapTo[Include[AdaptorDefinition]](groups.get(
        classOf[Include[AdaptorDefinition]]
      ))
      val authors = mapTo[Author](groups.get(classOf[Author]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val adaptations: Seq[Adaptation] = defs.filter(_.isInstanceOf[Adaptation])
        .map(_.asInstanceOf[Adaptation])
      Adaptor(
        loc,
        id,
        cref,
        adaptations,
        includes,
        authors,
        options,
        terms,
        briefly,
        description
      )
    }
  }
}
