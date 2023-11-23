/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*
import Readability.*

/** Parsing rules for Context definitions */
private[parsing] trait ContextParser {
  this: HandlerParser
    with AdaptorParser
    with EntityParser
    with FunctionParser
    with ProjectorParser
    with ReferenceParser
    with RepositoryParser
    with SagaParser
    with StreamingParser
    with StatementParser
    with TypeParser =>

  private def contextOptions[X: P]: P[Seq[ContextOption]] = {
    options[X, ContextOption](RiddlOptions.contextOptions) {
      case (loc, RiddlOption.wrapper, _)       => WrapperOption(loc)
      case (loc, RiddlOption.gateway, _)       => GatewayOption(loc)
      case (loc, RiddlOption.service, _)       => ServiceOption(loc)
      case (loc, RiddlOption.package_, args)   => ContextPackageOption(loc, args)
      case (loc, RiddlOption.technology, args) => ContextTechnologyOption(loc, args)
    }
  }

  private def contextInclude[X: P]: P[Include[ContextDefinition]] = {
    include[ContextDefinition, X](contextDefinitions(_))
  }

  private def replica[x: P]: P[Replica] = {
    P(
      location ~ Keywords.replica ~ identifier ~ is ~ replicaTypeExpression ~ briefly ~ description
    ).map { case (loc, id, typeExp, brief, description) =>
      Replica(loc, id, typeExp, brief, description)
    }
  }
  private def contextDefinitions[u: P]: P[Seq[ContextDefinition]] = {
    P(
      typeDef | handler(StatementsSet.ContextStatements) | entity |
        adaptor | function | saga | streamlet | projector | repository |
        inlet | outlet | connector | term | replica | contextInclude
    )./.rep(1)
  }

  private def contextBody[u: P]: P[Seq[ContextDefinition]] = {
    P(
      undefined(Seq.empty[ContextDefinition])./ | contextDefinitions./
    )
  }

  def context[u: P]: P[Context] = {
    P(
      location ~ Keywords.context ~/ identifier ~ authorRefs ~ is ~ open ~
        contextOptions ~ contextBody ~ close ~ briefly ~ description
    ).map { case (loc, id, authors, options, definitions, brief, description) =>
      val groups = definitions.groupBy(_.getClass)
      val types = mapTo[Type](groups.get(classOf[Type]))
      val constants = mapTo[Constant](groups.get(classOf[Constant]))
      val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
      val functions = mapTo[Function](groups.get(classOf[Function]))
      val entities = mapTo[Entity](groups.get(classOf[Entity]))
      val adaptors = mapTo[Adaptor](groups.get(classOf[Adaptor]))
      val streamlets = mapTo[Streamlet](groups.get(classOf[Streamlet]))
      val inlets = mapTo[Inlet](groups.get(classOf[Inlet]))
      val outlets = mapTo[Outlet](groups.get(classOf[Outlet]))
      val connections = mapTo[Connector](groups.get(classOf[Connector]))
      val includes = mapTo[Include[ContextDefinition]](
        groups.get(
          classOf[Include[ContextDefinition]]
        )
      )
      val sagas = mapTo[Saga](groups.get(classOf[Saga]))
      val handlers = mapTo[Handler](groups.get(classOf[Handler]))
      val projectors = mapTo[Projector](groups.get(classOf[Projector]))
      val repositories = mapTo[Repository](groups.get(classOf[Repository]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val replicas = mapTo[Replica](groups.get(classOf[Replica]))
      Context(
        loc,
        id,
        options,
        types,
        constants,
        entities,
        adaptors,
        sagas,
        streamlets,
        functions,
        terms,
        invariants,
        includes,
        handlers,
        projectors,
        repositories,
        inlets,
        outlets,
        connections,
        replicas,
        authors,
        brief,
        description
      )
    }
  }
}
