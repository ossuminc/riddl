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
import com.reactific.riddl.language.Terminals.Keywords
import com.reactific.riddl.language.Terminals.Options
import com.reactific.riddl.language.AST
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for domains. */
trait DomainParser
  extends CommonParser
    with ContextParser
    with StoryParser
    with StreamingParser
    with TypeParser {


  def domainOptions[X: P]: P[Seq[DomainOption]] = {
    options[X, DomainOption](StringIn(Options.package_).!) {
      case (loc, Options.package_, args) => DomainPackageOption(loc, args)
      case (_, _, _) => throw new RuntimeException("Impossible case")
    }
  }

  def domainInclude[X: P]: P[Include] = {
    include[DomainDefinition, X](domainContent(_))
  }

  def domainContent[u: P]: P[Seq[DomainDefinition]] = {
    P(
      (author | typeDef | context | plant | story | domain | term | importDef |
        domainInclude).rep(0)
    )
  }

  def domain[u: P]: P[Domain] = {
    P(
      location ~ Keywords.domain ~/ identifier ~ is ~ open ~/
        domainOptions ~ (undefined(Seq.empty[DomainDefinition]) | domainContent)
        ~ close ~/ briefly ~ description
    ).map { case (loc, id, options, defs, briefly, description) =>
      val groups = defs.groupBy(_.getClass)
      val authors = mapTo[AST.Author](groups.get(classOf[AST.Author]))
      val subdomains = mapTo[AST.Domain](groups.get(classOf[AST.Domain]))
      val types = mapTo[AST.Type](groups.get(classOf[AST.Type]))
      val contexts = mapTo[Context](groups.get(classOf[Context]))
      val plants = mapTo[Plant](groups.get(classOf[Plant]))
      val stories = mapTo[Story](groups.get(classOf[Story]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val includes = mapTo[Include](groups.get(classOf[Include]))
      Domain(
        loc,
        id,
        options,
        authors,
        types,
        contexts,
        plants,
        stories,
        subdomains,
        terms,
        includes,
        briefly,
        description
      )
    }
  }
}
