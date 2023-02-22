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

/** Parsing rules for Context definitions */
private[parsing] trait ContextParser
    extends HandlerParser
    with AdaptorParser
    with EntityParser
    with ProjectionParser
    with RepositoryParser
    with SagaParser
    with StreamingParser
    with TypeParser {

  private def contextOptions[X: P]: P[Seq[ContextOption]] = {
    options[X, ContextOption](
      StringIn(
        Options.wrapper,
        Options.gateway,
        Options.service,
        Options.package_,
        Options.technology
      ).!
    ) {
      case (loc, Options.wrapper, _)       => WrapperOption(loc)
      case (loc, Options.gateway, _)       => GatewayOption(loc)
      case (loc, Options.service, _)       => ServiceOption(loc)
      case (loc, Options.package_, args)   => ContextPackageOption(loc, args)
      case (loc, Options.technology, args) => ContextTechnologyOption(loc, args)
      case (_, _, _) => throw new RuntimeException("Impossible case")
    }
  }

  private def contextInclude[X: P]: P[Include[ContextDefinition]] = {
    include[ContextDefinition, X](contextDefinitions(_))
  }

  private def contextDefinitions[u: P]: P[Seq[ContextDefinition]] = {
    P(
      undefined(Seq.empty[ContextDefinition]) |
        (typeDef | handler | entity | adaptor | function | saga | streamlet |
          projection | repository | inlet | outlet | connector | term |
          contextInclude).rep(0)
    )
  }

  def context[u: P]: P[Context] = {
    P(
      location ~ Keywords.context ~/ identifier ~ authorRefs ~ is ~ open ~
        (undefined(Seq.empty[ContextOption] -> Seq.empty[ContextDefinition]) |
          (contextOptions ~ contextDefinitions)) ~ close ~ briefly ~ description
    ).map { case (loc, id, authorRefs, (options, definitions), briefly, desc) =>
      val groups = definitions.groupBy(_.getClass)
      val types = mapTo[Type](groups.get(classOf[Type]))
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
      val projections = mapTo[Projection](groups.get(classOf[Projection]))
      val repos = mapTo[Repository](groups.get(classOf[Repository]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      Context(
        loc,
        id,
        options,
        types,
        entities,
        adaptors,
        sagas,
        streamlets,
        functions,
        terms,
        includes,
        handlers,
        projections,
        repos,
        inlets,
        outlets,
        connections,
        authorRefs,
        briefly,
        desc
      )
    }
  }
}
