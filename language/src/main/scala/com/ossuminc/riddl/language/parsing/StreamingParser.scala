/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}
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

  /** A70-style intention prefix for connectors: `persistent at-most-once connector X is …`.
    *
    * Mirrors `EntityParser.entityIntentionPrefix`, including its trap: the alternatives are STRING
    * LITERALS, not `ConnectorIntention.keywords`, because `StringIn` is a macro and takes only
    * constants. `ConnectorIntentionKeywordsTest` pins the two lists together so they cannot drift.
    *
    * Longest-first so `at-least-once` can never be matched as a prefix of a shorter word, and the
    * result is sorted canonically -- write order is a convenience, never a structural difference.
    */
  private def connectorIntentionPrefix[u: P]: P[Seq[ConnectorIntention]] =
    P(
      StringIn(
        "at-least-once",
        "at-most-once",
        "exactly-once",
        "persistent"
      ).!.rep(0)
    ).map(kws => ConnectorIntention.canonical(kws.flatMap(ConnectorIntention.fromKeyword)))

  /** The options these intentions replaced, mapped to their keyword.
    *
    * The three DELIVERY options joined `persistent` here on 2026-08-14. Until then they parsed as
    * plain registry options, meant nothing and drew no message at all -- two spellings where one
    * was silently inert, which synapify reported. They could not be deprecated earlier because
    * `exactly-once` had no intention to be consumed INTO; deprecating two of three and leaving the
    * third current would have been its own inconsistency. Reid made it a third delivery intention,
    * which unblocked all three.
    */
  private val deprecatedConnectorOptions: Map[String, ConnectorIntention] = Map(
    "persistent" -> ConnectorIntention.Persistent,
    "at-least-once" -> ConnectorIntention.AtLeastOnce,
    "at-most-once" -> ConnectorIntention.AtMostOnce,
    "exactly-once" -> ConnectorIntention.ExactlyOnce
  )

  /** Consume a deprecated `option` into the intention it became, dropping it from the metadata.
    *
    * CONSUMING rather than keeping it is what makes a round trip converge and what migrates the
    * corpus for free: the 426 `option persistent()` uses across riddl-models become the intention
    * on the next prettify, with no hand edit. Same bargain as the entity intentions.
    */
  private def connectorIntentionsFromDeprecatedOptions(
    meta: Seq[MetaData]
  ): (Seq[MetaData], Seq[ConnectorIntention]) = {
    val found = meta.collect {
      case ov: OptionValue if deprecatedConnectorOptions.contains(ov.name) => ov
    }
    found.foreach { ov =>
      val intention = deprecatedConnectorOptions(ov.name)
      deprecation(
        ov.loc,
        s"'option ${ov.name}' is deprecated; write '${intention.keyword}' before 'connector' instead",
        code = Option(RuleId.ConnectorOptionToIntention),
        autoFixable = false
      )
    }
    val remaining = meta.filterNot(m => found.exists(_ eq m))
    remaining -> found.map(ov => deprecatedConnectorOptions(ov.name))
  }

  def connector[u: P]: P[Connector] = {
    P(
      Index ~ connectorIntentionPrefix ~ Keywords.connector ~/ identifier ~/ is ~
        connectorDefinitions ~ withMetaData ~/ Index
    ).map { case (start, intentions, id, (out, in), descriptives, end) =>
      val (remainingMeta, fromOptions) = connectorIntentionsFromDeprecatedOptions(descriptives)
      Connector(
        at(start, end),
        id,
        out,
        in,
        ConnectorIntention.canonical(intentions ++ fromOptions),
        remainingMeta.toContents
      )
    }
  }

  private def streamletInclude[u: P](
    minInlets: Int,
    maxInlets: Int,
    minOutlets: Int,
    maxOutlets: Int
  ): P[Include[StreamletContents]] = {
    include[u, StreamletContents]((p: P[?]) =>
      streamletDefinition(minInlets, maxInlets, minOutlets, maxOutlets)(using
        p
          .asInstanceOf[P[u]]
      )
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
      undefined(Seq.empty[StreamletContents]) |
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
      Index ~ keyword ~ identifier ~ is ~ open ~
        streamletBody(minInlets, maxInlets, minOutlets, maxOutlets) ~
        close ~ withMetaData ~ Index
    )./.map { case (start, id, contents, descriptives, end) =>
      val loc = at(start, end)
      // Normalize the ascribed-shape loc to At.empty so it matches the `as <shape>`
      // parser path. `ascribedShape` participates in Definition.equals, so the shape
      // loc must be surface-independent (parser/BAST/JSON all use At.empty).
      val shape = keywordToKind(keyword, At.empty)
      // The dedicated shape keywords (source/sink/flow/merge/split/router) are
      // deprecated in favor of the generic `processor <id> as <shape>` form.
      // `void` has no `processor` equivalent, so it is not deprecated.
      if keyword != Keyword.void then
        val kwLoc = at(start, start + keyword.length)
        deprecation(
          kwLoc,
          s"The `$keyword` keyword is deprecated; use `processor ${id.value} as $keyword` instead",
          code = Option(RuleId.ShapeKeyword),
          autoFixable = true
        )
      end if
      checkForDuplicateIncludes(contents)
      Streamlet(loc, id, Some(shape), contents.toContents, descriptives.toContents)
    }
  }

  private val MaxStreamlets = 100

  def source[u: P]: P[Streamlet] = {
    // A source may publish on SEVERAL outlets (Reid, 2026-08-12); see AST.shapeForArity.
    streamletTemplate(Keyword.source, minOutlets = 1, maxOutlets = MaxStreamlets)
  }

  def sink[u: P]: P[Streamlet] = {
    // A sink may drain SEVERAL inlets (Reid, 2026-08-12); see AST.shapeForArity.
    streamletTemplate(Keyword.sink, minInlets = 1, maxInlets = MaxStreamlets)
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

  /** A generic processor with no fixed arity; the author may ascribe a shape via `as <shape>`. */
  def processor[u: P]: P[Streamlet] = {
    P(
      Index ~ Keywords.processor ~/ identifier ~ asShape ~ is ~ open ~
        streamletBody(0, MaxStreamlets, 0, MaxStreamlets) ~
        close ~ withMetaData ~ Index
    )./.map { case (start, id, ascribed, contents, descriptives, end) =>
      checkForDuplicateIncludes(contents)
      Streamlet(at(start, end), id, ascribed, contents.toContents, descriptives.toContents)
    }
  }

  def streamlet[u: P]: P[Streamlet] =
    P(source | flow | sink | merge | split | router | void | processor)

}
