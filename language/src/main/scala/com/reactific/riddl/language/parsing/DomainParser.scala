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
import com.reactific.riddl.language.Terminals.Punctuation
import com.reactific.riddl.language.Terminals.Readability
import com.reactific.riddl.language.AST
import com.reactific.riddl.language.ast.Location
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
        domainRef.rep(1, Punctuation.comma) ~ close).?
        .map(x => if (x.isEmpty) Seq.empty[DomainRef] else x.get) ~
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

  def author[u: P]: P[AuthorInfo] = {
    P(
      location ~ Keywords.author ~/ identifier ~ is ~ open ~
        (undefined((
          LiteralString(Location(), ""),
          LiteralString(Location(), ""),
          Option.empty[LiteralString],
          Option.empty[LiteralString],
          Option.empty[java.net.URL]
        )) | (Keywords.name ~ is ~ literalString ~ Keywords.email ~ is ~
          literalString ~ (Keywords.organization ~ is ~ literalString).? ~
          (Keywords.title ~ is ~ literalString).? ~
          (Keywords.url ~ is ~ httpUrl).?)) ~ close ~ briefly ~ description
    ).map {
      case (loc, id, (name, email, org, title, url), brief, desc) =>
        AuthorInfo(loc, id, name, email, org, title, url, brief, desc)
    }
  }

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
      val authors = mapTo[AST.AuthorInfo](groups.get(classOf[AST.AuthorInfo]))
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
