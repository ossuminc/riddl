/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*
import Readability.*
import com.ossuminc.riddl.language.At

/** Unit Tests For StreamingParser */
private[parsing] trait StreamingParser {
  this: HandlerParser with ReferenceParser with StatementParser with FunctionParser with TypeParser =>

  def inlet[u: P]: P[Inlet] = {
    P(
      location ~ Keywords.inlet ~ identifier ~ is ~
        typeRef ~/ briefly ~ description
    )./.map { tpl => (Inlet.apply _).tupled(tpl) }
  }

  def outlet[u: P]: P[Outlet] = {
    P(
      location ~ Keywords.outlet ~ identifier ~ is ~
        typeRef ~/ briefly ~ description
    )./.map { tpl => (Outlet.apply _).tupled(tpl) }
  }

  private def connectorOptions[X: P]: P[Seq[ConnectorOption]] = {
    options[X, ConnectorOption](
      StringIn(RiddlOption.persistent, RiddlOption.technology, RiddlOption.kind).!
    ) {
      case (loc, RiddlOption.persistent, _)    => ConnectorPersistentOption(loc)
      case (loc, RiddlOption.technology, args) => ConnectorTechnologyOption(loc, args)
      case (loc, RiddlOption.kind, args)       => ConnectorKindOption(loc, args)
    }
  }

  def connector[u: P]: P[Connector] = {
    P(
      location ~ Keywords.connector ~/ identifier ~ is ~/ open ~
        connectorOptions ~
        (undefined((None, None, None)) |
          (
            (Keywords.flows ~ typeRef).? ~/
              Readability.from ~ outletRef ~/
              Readability.to ~ inletRef
          ).map { case (typ, out, in) =>
            (typ, Some(out), Some(in))
          }) ~ close ~/ briefly ~ description
    )./.map { case (loc, id, opts, (typ, out, in), brief, description) =>
      Connector(loc, id, opts, typ, out, in, brief, description)
    }
  }

  private def streamletInclude[u: P](
    minInlets: Int,
    maxInlets: Int,
    minOutlets: Int,
    maxOutlets: Int
  ): P[IncludeHolder[OccursInStreamlet]] = {
    include[OccursInStreamlet, u](
      streamletDefinition(minInlets, maxInlets, minOutlets, maxOutlets)(_)
    )
  }

  private def streamletDefinition[u: P](
    minInlets: Int,
    maxInlets: Int,
    minOutlets: Int,
    maxOutlets: Int
  ): P[Seq[OccursInStreamlet]] = {
    P(
      (inlet./.rep(minInlets, " ", maxInlets) ~
        outlet./.rep(minOutlets, " ", maxOutlets) ~
        (handler(
          StatementsSet.StreamStatements
        ) | term | authorRef | comment | function | invariant | constant | typeDef |
          streamletInclude(minInlets, maxInlets, minOutlets, maxOutlets))./.rep(0)).map {
        case (inlets, outlets, definitions) =>
          inlets ++ outlets ++ definitions
      }
    )
  }

  private def streamletOptions[u: P]: P[Seq[StreamletOption]] = {
    options[u, StreamletOption](StringIn(RiddlOption.technology, RiddlOption.css, RiddlOption.kind).!) {
      case (loc, RiddlOption.technology, args) => StreamletTechnologyOption(loc, args)
      case (loc, RiddlOption.css, args)        => StreamletCssOption(loc, args)
      case (loc, RiddlOption.faicon, args)     => StreamletIconOption(loc, args)
      case (loc, RiddlOption.kind, args)       => StreamletKindOption(loc, args)
    }
  }

  private def streamletBody[u: P](
    minInlets: Int,
    maxInlets: Int,
    minOutlets: Int,
    maxOutlets: Int
  ): P[Seq[OccursInStreamlet]] = {
    P(
      undefined(Seq.empty[OccursInStreamlet]) |
        streamletDefinition(minInlets, maxInlets, minOutlets, maxOutlets)
    )
  }

  private def keywordToKind(keyword: String, location: At): StreamletShape = {
    keyword match {
      case "source" => Source(location)
      case "sink"   => Sink(location)
      case "flow"   => Flow(location)
      case "merge"  => Merge(location)
      case "split"  => Split(location)
      case "router" => Router(location)
      case "void"   => Void(location)
    }
  }

  private def streamletTemplate[u: P](
    keyword: String,
    minInlets: Int = 0,
    maxInlets: Int = 0,
    minOutlets: Int = 0,
    maxOutlets: Int = 0
  ): P[Streamlet] = {
    P(
      location ~ keyword ~ identifier ~ is ~ open ~
        streamletOptions ~ streamletBody(minInlets, maxInlets, minOutlets, maxOutlets) ~
        close ~ briefly ~ description
    )./.map { case (loc, id, options, contents, brief, description) =>
      val shape = keywordToKind(keyword, loc)
      val mergedContent = mergeAsynchContent[OccursInStreamlet](contents)
      Streamlet(loc, id, shape, options, mergedContent, brief, description)
    }
  }

  private val MaxStreamlets = 100

  def source[u: P]: P[Streamlet] = {
    streamletTemplate(Keyword.source, minOutlets = 1, maxOutlets = 1)
  }

  def sink[u: P]: P[Streamlet] = {
    streamletTemplate(Keyword.sink, minInlets = 1, maxInlets = 1)
  }

  def flow[u: P]: P[Streamlet] = {
    streamletTemplate(
      Keyword.flow,
      minInlets = 1,
      maxInlets = 1,
      minOutlets = 1,
      maxOutlets = 1
    )
  }

  def split[u: P]: P[Streamlet] = {
    streamletTemplate(
      Keyword.split,
      minInlets = 1,
      maxInlets = 1,
      minOutlets = 2,
      maxOutlets = MaxStreamlets
    )
  }

  def merge[u: P]: P[Streamlet] = {
    streamletTemplate(
      Keyword.merge,
      minInlets = 2,
      maxInlets = MaxStreamlets,
      minOutlets = 1,
      maxOutlets = 1
    )
  }

  def router[u: P]: P[Streamlet] = {
    streamletTemplate(
      Keyword.router,
      minInlets = 2,
      maxInlets = MaxStreamlets,
      minOutlets = 2,
      maxOutlets = MaxStreamlets
    )
  }

  def void[u: P]: P[Streamlet] = { streamletTemplate(Keyword.void) }

  def streamlet[u: P]: P[Streamlet] =
    P(source | flow | sink | merge | split | router | void)

}
