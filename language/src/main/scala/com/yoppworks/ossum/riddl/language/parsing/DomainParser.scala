package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Punctuation, Readability}
import com.yoppworks.ossum.riddl.language.{AST, Location}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for domains. */
trait DomainParser
    extends CommonParser
    with ContextParser
    with InteractionParser
    with StreamingParser
    with TypeParser {

  def domainInclude[X: P]: P[Seq[DomainDefinition]] = {
    include[DomainDefinition, X](domainContent(_))
  }

  def story[u: P]: P[Story] =
    P(location ~
      Keywords.story ~ identifier ~ is ~ open ~
      Keywords.role ~ is ~ literalString ~
      Keywords.capability ~ is ~ literalString ~
      Keywords.benefit ~ is ~ literalString ~
      (Keywords.shown ~ Readability.by ~ open ~ httpUrl.rep(1,Punctuation.comma) ~ close).?
        .map { x => if (x.isEmpty) Seq.empty[java.net.URL] else x.get } ~
      (Keywords.implemented ~ Readability.by ~ open ~ pathIdentifier.rep(1,Punctuation.comma) ~
        close).?.map(x => if (x.isEmpty) Seq.empty[PathIdentifier] else x.get) ~
      (Keywords.accepted ~ Readability.by ~ open ~ examples ~ close).? ~
      close ~ description
    ).map {
      case (loc, id, role, capa, bene, shown, implemented, Some(examples), description) =>
        Story(loc, id, role, capa, bene, shown, implemented, examples, description)
      case (loc, id, role, capa, bene, shown, implemented, None, description) =>
        Story(loc, id, role, capa, bene, shown, implemented, Seq.empty[Example], description)
    }

  def author[u: P]: P[Option[AuthorInfo]] = {
    P(
      location ~ Keywords.author ~/ is ~ open ~
        (undefined((LiteralString(Location(), ""), LiteralString(Location(), ""),
          Option.empty[LiteralString], Option.empty[LiteralString])) |
          (
            Keywords.name ~ is ~ literalString ~
              Keywords.email ~ is ~ literalString ~
              (Keywords.organization ~ is ~ literalString).? ~
              (Keywords.title ~ is ~ literalString).?
            )) ~ close ~ description
    ).?.map {
      case Some((loc, (name, email, org, title), description)) =>
        if (name.isEmpty && email.isEmpty && org.isEmpty && title.isEmpty) {
          Option.empty[AuthorInfo]
        } else {
          Option(AuthorInfo(loc, name, email, org, title, description))
        }
      case None => None
    }
  }

  def domainContent[u: P]: P[Seq[DomainDefinition]] = {
    P(
      (typeDef | interaction | context | plant | story | domain | importDef).map(Seq(_)) |
        domainInclude
    ).rep(0).map(_.flatten)
  }

  def domain[u: P]: P[Domain] = {
    P(
      location ~ Keywords.domain ~/ identifier ~ is ~ open ~/
        (undefined((Option.empty[AuthorInfo], Seq.empty[DomainDefinition])) |
          author ~ domainContent) ~
        close ~/ description
    ).map { case (loc, id, (author, defs), description) =>
      val groups = defs.groupBy(_.getClass)
      val domains = mapTo[AST.Domain](groups.get(classOf[AST.Domain]))
      val types = mapTo[AST.Type](groups.get(classOf[AST.Type]))
      val contexts = mapTo[Context](groups.get(classOf[Context]))
      val interactions = mapTo[Interaction](groups.get(classOf[Interaction]))
      val plants = mapTo[Plant](groups.get(classOf[Plant]))
      val stories = mapTo[Story](groups.get(classOf[Story]))
      Domain(loc, id, author, types, contexts, interactions, plants, stories, domains, description)
    }
  }
}
