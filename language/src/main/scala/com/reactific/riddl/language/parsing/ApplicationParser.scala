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
    extends CommonParser
    with ReferenceParser
    with HandlerParser
    with TypeParser {

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
        (group | appOutput | appInput).rep(0) ~ close ~ briefly ~ description
    ).map { case (loc, id, types, elements, brief, description) =>
      Group(loc, id, types, elements, brief, description)
    }
  }

  def appOutput[u: P]: P[Output] = {
    P(
      location ~ Keywords.output ~/ identifier ~ is ~ open ~ types ~
        Keywords.presents ~/ resultRef ~ close ~ briefly ~ description
    ).map { case (loc, id, types, result, brief, description) =>
      Output(loc, id, types, result, brief, description)
    }
  }

  def appInput[u: P]: P[Input] = {
    P(
      location ~ Keywords.input ~/ identifier ~ is ~ open ~ types ~
        Keywords.acquires ~ commandRef ~ close ~ briefly ~ description
    ).map { case (loc, id, types, yields, brief, description) =>
      Input(loc, id, types, yields, brief, description)
    }
  }

  def applicationDefinition[u: P]: P[ApplicationDefinition] = {
    P(group | handler | term | typeDef | applicationInclude)
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
      location ~ Keywords.application ~/ identifier ~ authorRefs ~ is ~ open ~
        (emptyApplication | (applicationOptions ~ applicationDefinitions)) ~
        close ~ briefly ~ description
    ).map { case (loc, id, authorRefs, (options, content), brief, desc) =>
      val groups = content.groupBy(_.getClass)
      val types = mapTo[Type](groups.get(classOf[Type]))
      val grps = mapTo[Group](groups.get(classOf[Group]))
      val handlers = mapTo[Handler](groups.get(classOf[Group]))
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
        handlers,
        authorRefs,
        terms,
        includes,
        brief,
        desc
      )

    }
  }
}
