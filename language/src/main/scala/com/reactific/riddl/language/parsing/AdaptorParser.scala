/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Location
import com.reactific.riddl.language.Terminals.{Keywords, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parser rules for Adaptors */
trait AdaptorParser
    extends ReferenceParser with GherkinParser with ActionParser {

  def adaptationPrefix[u: P]: P[(Location, Identifier)] = {
    P(location ~ Keywords.adapt ~ identifier ~ is ~ open ~ Readability.from)
  }

  def adaptationSuffix[u:P]:
  P[(Seq[Example],Option[LiteralString],Option[Description])]  = {
    P( Readability.as ~ open ~ (undefined(Seq.empty[Example]) | examples) ~
      close ~ close ~ briefly ~ description
    )
  }

  def eventCommand[u: P]: P[EventCommandA8n] = {
    P(adaptationPrefix ~ eventRef ~ Readability.to ~ commandRef ~
      adaptationSuffix
    ).map {
      case (loc, id, er, cr, (examples, briefly, description)) =>
        EventCommandA8n(loc, id, er, cr, examples, briefly, description)
    }
  }

  def commandCommand[u:P]: P[CommandCommandA8n] = {
    P(adaptationPrefix ~ commandRef ~ Readability.to ~ commandRef ~
      adaptationSuffix).map {
        case (loc, id, cr1, cr2, (examples, briefly, description)) =>
          CommandCommandA8n(loc, id, cr1, cr2, examples, briefly, description)
    }
  }

  def eventAction[u: P]: P[EventActionA8n] = {
    P(adaptationPrefix ~ eventRef ~ Readability.to ~ open ~ actionList ~ close ~
    adaptationSuffix).map {
      case (loc, id, er, actions, (examples, briefly, description)) =>
        EventActionA8n(loc, id, er, actions, examples, briefly, description)
    }
  }

  def adaptorInclude[u: P]: P[Include] = {
    include[AdaptorDefinition, u](adaptorDefinitions(_))
  }

  def adaptorDefinitions[u: P]: P[Seq[AdaptorDefinition]] = {
    P(
      (eventCommand | commandCommand | eventAction | adaptorInclude)
        .rep(1) |
        undefined(Seq.empty[AdaptorDefinition])
    )
  }


  def adaptor[u: P]: P[Adaptor] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~ Readability.for_ ~
        contextRef ~ is ~ open ~ adaptorDefinitions ~ close ~ briefly ~
        description
    ).map { case (loc, id, cref, defs, briefly, description) =>
      val groups = defs.groupBy(_.getClass)
      val includes = mapTo[Include](groups.get(classOf[Include]))
      val adaptations: Seq[Adaptation] =
        defs.filter(_.isInstanceOf[Adaptation]).map(_.asInstanceOf[Adaptation])
      Adaptor(loc, id, cref, adaptations, includes, briefly, description)
    }
  }
}
