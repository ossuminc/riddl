/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*

trait ApplicationParser
    extends CommonParser with ReferenceParser with TypeParser {

  def applicationOptions[u: P]: P[Seq[ApplicationOption]] = {
    options[u, ApplicationOption](StringIn(Options.technology).!) {
      case (loc, Options.technology, args) =>
        ApplicationTechnologyOption(loc, args)
      case (_, _, _) => throw new RuntimeException("Impossible case")
    }
  }

  def group[u: P]: P[Group] = {
    P(
      location ~ Keywords.group ~/ identifier ~ is ~ open ~ types ~
        (group | view | give).rep(0) ~ close ~ briefly ~ description
    ).map { case (loc, id, types, elements, brief, description) =>
      Group(loc, id, types, elements, brief, description)
    }
  }

  // def select[u:P]: P[Select]
  def view[u: P]: P[View] = {
    P(
      location ~ Keywords.view ~/ identifier ~ is ~ open ~ types ~
        Keywords.presents ~/ resultRef ~ close ~ briefly ~ description
    ).map { case (loc, id, types, result, brief, description) =>
      View(loc, id, types, result, brief, description)
    }
  }

  def give[u: P]: P[Give] = {
    P(
      location ~ Keywords.give ~/ identifier ~ is ~ open ~ types ~
        Keywords.yields ~ commandRef ~ close ~ briefly ~ description
    ).map { case (loc, id, types, yields, brief, description) =>
      Give(loc, id, types, yields, brief, description)
    }
  }

  def applicationDefinition[u: P]: P[ApplicationDefinition] = {
    P(group | author | term | typeDef | applicationInclude)
  }

  def applicationDefinitions[u: P]: P[Seq[ApplicationDefinition]] = {
    P(applicationDefinition.rep(0))
  }

  def applicationInclude[u: P]: P[Include[ApplicationDefinition]] = {
    include[ApplicationDefinition, u](applicationDefinitions(_))
  }

  def emptyApplication[
    u: P
  ]: P[(Seq[ApplicationOption], Seq[ApplicationDefinition])] = {
    undefined((Seq.empty[ApplicationOption], Seq.empty[ApplicationDefinition]))
  }

  def application[u: P]: P[Application] = {
    P(
      location ~ Keywords.application ~/ identifier ~ is ~ open ~
        (emptyApplication | (applicationOptions ~ applicationDefinitions)) ~
        close ~ briefly ~ description
    ).map { case (loc, id, (options, content), brief, desc) =>
      val groups = content.groupBy(_.getClass)
      val authors = mapTo[Author](groups.get(classOf[Author]))
      val types = mapTo[Type](groups.get(classOf[Type]))
      val grps = mapTo[Group](groups.get(classOf[Group]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val includes = mapTo[Include[ApplicationDefinition]](groups.get(
        classOf[Include[ApplicationDefinition]]
      ))

      Application(
        loc,
        id,
        options,
        types,
        grps,
        authors,
        terms,
        includes,
        brief,
        desc
      )

    }
  }
}
