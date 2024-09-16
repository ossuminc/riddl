/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*
import com.ossuminc.riddl.language.At

/** Unit Tests For StreamingParser */
private[parsing] trait StreamingParser {
  this: ProcessorParser =>

  def inlet[u: P]: P[Inlet] = {
    P(
      location ~ Keywords.inlet ~ identifier ~ is ~ typeRef ~/ withMetaData
    )./.map { case (loc, id, typeRef, descriptives) =>
      Inlet(loc, id, typeRef, descriptives.toContents)
    }
  }

  def outlet[u: P]: P[Outlet] = {
    P(
      location ~ Keywords.outlet ~ identifier ~ is ~ typeRef ~/ withMetaData
    )./.map { case (loc, id, typeRef, descriptives) =>
      Outlet(loc, id, typeRef, descriptives.toContents)
    }
  }

  private def connectorDefinitions[u: P]: P[(OutletRef, InletRef, Seq[OptionValue])] = {
    P(
      (open ~ from ~ outletRef ~/ to ~ inletRef ~/ option.rep(0) ~ close) |
        (from ~ outletRef ~/ to ~ inletRef ~/ option.rep(0))
    )
  }

  def connector[u: P]: P[Connector] = {
    P(
      location ~ Keywords.connector ~/ identifier ~/ is ~ connectorDefinitions ~ withMetaData
    )./.map { case (loc, id, (out, in, opts), descriptives) =>
      Connector(loc, id, out, in, opts, descriptives.toContents)
    }
  }

  private def streamletInclude[u: P](
    minInlets: Int,
    maxInlets: Int,
    minOutlets: Int,
    maxOutlets: Int
  ): P[Include[StreamletContents]] = {
    include[u, StreamletContents](
      streamletDefinition(minInlets, maxInlets, minOutlets, maxOutlets)(_)
    )
  }

  private def streamletDefinition[u: P](
    minInlets: Int,
    maxInlets: Int,
    minOutlets: Int,
    maxOutlets: Int
  ): P[Seq[StreamletContents]] = {
    P(
      inlet./.rep(min = minInlets, max = maxInlets) ~ outlet./.rep(min = minOutlets, max = maxOutlets) ~
        (processorDefinitionContents(StatementsSet.StreamStatements) |
          streamletInclude(minInlets, maxInlets, minOutlets, maxOutlets))./.asInstanceOf[P[StreamletContents]].rep(0)
    )./.map { case (inlets, outlets, contents) =>
      (inlets ++ outlets ++ contents).asInstanceOf[Seq[StreamletContents]]
    }
  }

  private def streamletBody[u: P](
    minInlets: Int,
    maxInlets: Int,
    minOutlets: Int,
    maxOutlets: Int
  ): P[Seq[StreamletContents]] = {
    P(
      streamletDefinition(minInlets, maxInlets, minOutlets, maxOutlets) |
        undefined(Seq.empty[StreamletContents])
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
        streamletBody(minInlets, maxInlets, minOutlets, maxOutlets) ~
        close ~ withMetaData
    )./.map { case (loc, id, contents, descriptives) =>
      val shape = keywordToKind(keyword, loc)
      checkForDuplicateIncludes(contents)
      Streamlet(loc, id, shape, contents.toContents, descriptives.toContents)
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
