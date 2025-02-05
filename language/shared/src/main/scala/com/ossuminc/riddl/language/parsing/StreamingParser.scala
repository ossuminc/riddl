/*
 * Copyright 2019-2025 Ossum, Inc.
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
      Index ~ Keywords.inlet ~ identifier ~ is ~ typeRef ~/ withMetaData ~/ Index
    ).map { case (start, id, typeRef, descriptives, end) =>
      Inlet(at(start, end), id, typeRef, descriptives.toContents)
    }
  }

  def outlet[u: P]: P[Outlet] = {
    P(
      Index ~ Keywords.outlet ~ identifier ~ is ~ typeRef ~/ withMetaData ~/ Index
    ).map { case (start, id, typeRef, descriptives, end) =>
      Outlet(at(start, end), id, typeRef, descriptives.toContents)
    }
  }

  private def connectorDefinitions[u: P]: P[(OutletRef, InletRef)] = {
    P(
      (open ~ from ~ outletRef ~/ to ~ inletRef ~/ close) |
        (from ~ outletRef ~/ to ~ inletRef)
    )
  }

  def connector[u: P]: P[Connector] = {
    P(
      Index ~ Keywords.connector ~/ identifier ~/ is ~ connectorDefinitions ~ withMetaData ~/ Index
    ).map { case (start, id, (out, in), descriptives, end) =>
      Connector(at(start, end), id, out, in, descriptives.toContents)
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
      inlet./.rep(min = minInlets, max = maxInlets) ~ outlet./.rep(
        min = minOutlets,
        max = maxOutlets
      ) ~
        (processorDefinitionContents(StatementsSet.StreamStatements) |
          streamletInclude(minInlets, maxInlets, minOutlets, maxOutlets))./.asInstanceOf[P[
          StreamletContents
        ]].rep(0)
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
      Index ~ keyword ~ identifier ~ is ~ open ~
        streamletBody(minInlets, maxInlets, minOutlets, maxOutlets) ~
        close ~ withMetaData ~ Index
    )./.map { case (start, id, contents, descriptives, end) =>
      val loc = at(start, end)
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
