/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import Terminals.*
import fastparse.*
import fastparse.ScalaWhitespace.*

private[parsing] trait ApplicationParser {
  this: StreamingParser with FunctionParser with ReferenceParser with HandlerParser with StatementParser
  with TypeParser =>

  private def applicationOptions[u: P]: P[Seq[ApplicationOption]] = {
    options[u, ApplicationOption](StringIn(Options.technology).!) {
      case (loc, Options.technology, args) =>
        ApplicationTechnologyOption(loc, args)
      case (_, _, _) => throw new RuntimeException("Impossible case")
    }
  }

  private def group[u: P]: P[Group] = {
    P(
      location ~ Keywords.group ~/ identifier ~ is ~ open ~ types ~
        (group | appOutput | appInput).rep(0) ~ close ~ briefly ~ description
    ).map { case (loc, id, types, elements, brief, description) =>
      Group(loc, id, types, elements, brief, description)
    }
  }

  private def appOutput[u: P]: P[Output] = {
    P(
      location ~ Keywords.output ~/ identifier ~ is ~ open ~ types ~
        Keywords.presents ~/ messageRef ~ close ~ briefly ~ description
    ).map { case (loc, id, types, result, brief, description) =>
      Output(loc, id, types, result, brief, description)
    }
  }

  private def appInput[u: P]: P[Input] = {
    P(
      location ~ Keywords.input ~/ identifier ~ is ~ open ~ types ~
        Keywords.acquires ~ messageRef ~ close ~ briefly ~ description
    ).map { case (loc, id, types, yields, brief, description) =>
      Input(loc, id, types, yields, brief, description)
    }
  }

  private def applicationDefinition[u: P]: P[ApplicationDefinition] = {
    P(
      group | handler(StatementsSet.ApplicationStatements) | function |
        inlet | outlet | term | typeDef |
        constant | applicationInclude | errorOnInvalidClose(Keywords.application)
    )
  }

  private def applicationDefinitions[u: P]: P[Seq[ApplicationDefinition]] = {
    P(applicationDefinition.rep(0))
  }

  private def applicationInclude[u: P]: P[Include[ApplicationDefinition]] = {
    include[ApplicationDefinition, u](applicationDefinitions(_))
  }

  private def emptyApplication[
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
      val constants = mapTo[Constant](groups.get(classOf[Constant]))
      val grps = mapTo[Group](groups.get(classOf[Group]))
      val handlers = mapTo[Handler](groups.get(classOf[Group]))
      val functions = mapTo[Function](groups.get(classOf[Function]))
      val inlets = mapTo[Inlet](groups.get(classOf[Inlet]))
      val outlets = mapTo[Outlet](groups.get(classOf[Outlet]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val includes = mapTo[Include[ApplicationDefinition]](
        groups.get(
          classOf[Include[ApplicationDefinition]]
        )
      )

      Application(
        loc,
        id,
        options,
        types,
        constants,
        grps,
        handlers,
        inlets,
        outlets,
        functions,
        authorRefs,
        terms,
        includes,
        brief,
        desc
      )

    }
  }
}
