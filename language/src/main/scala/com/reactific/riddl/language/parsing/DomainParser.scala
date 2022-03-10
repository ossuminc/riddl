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
import com.reactific.riddl.language.Terminals.{Keywords, Punctuation, Readability}
import com.reactific.riddl.language.{AST, Location}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for domains. */
trait DomainParser
    extends CommonParser
    with ContextParser
    with StreamingParser
    with TypeParser {

  def story[u: P]: P[Story] = P(
    location ~ Keywords.story ~ identifier ~ is ~ open ~ Keywords.role ~ is ~
      literalString ~ Keywords.capability ~ is ~ literalString ~
      Keywords.benefit ~ is ~ literalString ~
      (Keywords.shown ~ Readability.by ~ open ~
        httpUrl.rep(1, Punctuation.comma) ~ close).?.map { x =>
        if (x.isEmpty) Seq.empty[java.net.URL] else x.get
      } ~
      (Keywords.implemented ~ Readability.by ~ open ~
        pathIdentifier.rep(1, Punctuation.comma) ~ close).?
        .map(x => if (x.isEmpty) Seq.empty[PathIdentifier] else x.get) ~
      (Keywords.accepted ~ Readability.by ~ open ~ examples ~ close).? ~ close ~
      briefly ~ description
  ).map {
    case (
          loc,
          id,
          role,
          capa,
          bene,
          shown,
          implemented,
          Some(examples),
          briefly,
          description
        ) => Story(
        loc,
        id,
        role,
        capa,
        bene,
        shown,
        implemented,
        examples,
        briefly,
        description
      )
    case (
          loc,
          id,
          role,
          capa,
          bene,
          shown,
          implemented,
          None,
          briefly,
          description
        ) => Story(
        loc,
        id,
        role,
        capa,
        bene,
        shown,
        implemented,
        Seq.empty[Example],
        briefly,
        description
      )
  }

  def author[u: P]: P[Option[AuthorInfo]] = {
    P(
      location ~ Keywords.author ~/ is ~ open ~
        (undefined((
          LiteralString(Location(), ""),
          LiteralString(Location(), ""),
          Option.empty[LiteralString],
          Option.empty[LiteralString],
          Option.empty[java.net.URL]
        )) |
          (Keywords.name ~ is ~ literalString ~ Keywords.email ~ is ~
            literalString ~ (Keywords.organization ~ is ~ literalString).? ~
            (Keywords.title ~ is ~ literalString).? ~
            (Keywords.url ~ is ~ httpUrl).?)) ~ close ~ description
    ).?.map {
      case Some((loc, (name, email, org, title, url), description)) =>
        if (
          name.isEmpty && email.isEmpty && org.isEmpty && title.isEmpty &&
          url.isEmpty
        ) { Option.empty[AuthorInfo] }
        else {
          Option(AuthorInfo(loc, name, email, org, title, url, description))
        }
      case None => None
    }
  }

  def domainInclude[X: P]: P[Include] = {
    include[DomainDefinition, X](domainContent(_))
  }

  def domainContent[u: P]: P[Seq[DomainDefinition]] = {
    P(
      (typeDef | context | plant | story | domain | term | importDef |
        domainInclude).rep(0)
    )
  }

  def domain[u: P]: P[Domain] = {
    P(
      location ~ Keywords.domain ~/ identifier ~ is ~ open ~/
        (undefined((Option.empty[AuthorInfo], Seq.empty[DomainDefinition])) |
          author ~ domainContent) ~ close ~/ briefly ~ description
    ).map { case (loc, id, (author, defs), briefly, description) =>
      val groups = defs.groupBy(_.getClass)
      val domains = mapTo[AST.Domain](groups.get(classOf[AST.Domain]))
      val types = mapTo[AST.Type](groups.get(classOf[AST.Type]))
      val contexts = mapTo[Context](groups.get(classOf[Context]))
      val plants = mapTo[Plant](groups.get(classOf[Plant]))
      val stories = mapTo[Story](groups.get(classOf[Story]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val includes = mapTo[Include](groups.get(classOf[Include]))
      Domain(
        loc,
        id,
        author,
        types,
        contexts,
        plants,
        stories,
        domains,
        terms,
        includes,
        briefly,
        description
      )
    }
  }
}
