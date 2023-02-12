/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Unit Tests For StreamingParser */
trait StreamingParser extends ReferenceParser with HandlerParser {

  def pipe[u: P]: P[Pipe] = {
    location ~ Keywords.pipe ~/ identifier ~ is ~ open ~
      (undefined(None) | Keywords.transmit ~/ typeRef.map(Option(_))) ~
      (Readability.from ~ outletRef).? ~
      (Readability.to ~ inletRef).? ~ close ~ briefly ~ description
  }.map { tpl => (Pipe.apply _).tupled(tpl) }

  def toEntity[u: P]: P[EntityRef] = { P(Readability.to ~/ entityRef) }

  def inlet[u: P]: P[Inlet] = {
    P(
      location ~ Keywords.inlet ~ identifier ~ is ~ typeRef ~/
        briefly ~ description
    )./.map { tpl => (Inlet.apply _).tupled(tpl) }
  }

  def fromEntity[u: P]: P[EntityRef] = { P(Readability.from ~ entityRef) }

  def outlet[u: P]: P[Outlet] = {
    P(
      location ~ Keywords.outlet ~ identifier ~ is ~ typeRef ~/
        briefly ~ description
    )./.map { tpl => (Outlet.apply _).tupled(tpl) }
  }

  def processorInclude[u: P](
    minInlets: Int = 0,
    maxInlets: Int = 0,
    minOutlets: Int = 0,
    maxOutlets: Int = 0
  ): P[Include[ProcessorDefinition]] = {
    include[ProcessorDefinition, u](
      processorDefinitions(minInlets, maxInlets, minOutlets, maxOutlets)(_)
    )
  }

  def processorDefinitions[u: P](
    minInlets: Int = 0,
    maxInlets: Int = 0,
    minOutlets: Int = 0,
    maxOutlets: Int = 0
  ): P[Seq[ProcessorDefinition]] = {
    P(
      (inlet.rep(minInlets, "", maxInlets) ~/
        outlet.rep(minOutlets, "", maxOutlets) ~/
        (handler | term |
          processorInclude(minInlets, maxInlets, minOutlets, maxOutlets))
          .rep(0)).map { case (inlets, outlets, definitions) =>
        inlets ++ outlets ++ definitions
      }
    )
  }

  def processorOptions[u: P]: P[Seq[ProcessorOption]] = {
    options[u, ProcessorOption](StringIn(Options.technology).!) {
      case (loc, Options.technology, args) =>
        ProcessorTechnologyOption(loc, args)
      case (_, _, _) => throw new RuntimeException("Impossible case")
    }
  }

  def processorBody[u: P](
    minInlets: Int = 0,
    maxInlets: Int = 0,
    minOutlets: Int = 0,
    maxOutlets: Int = 0
  ): P[(Seq[ProcessorOption], Seq[ProcessorDefinition])] = {
    P(
      undefined((Seq.empty[ProcessorOption], Seq.empty[ProcessorDefinition])) |
        (processorOptions ~
          processorDefinitions(minInlets, maxInlets, minOutlets, maxOutlets))
    )
  }

  def keywordToKind(keyword: String, location: At): ProcessorShape = {
    keyword match {
      case "source" => Source(location)
      case "sink"   => Sink(location)
      case "flow"   => Flow(location)
      case "merge"  => Merge(location)
      case "split"  => Split(location)
      case "router" => Router(location)
      case "multi"  => Multi(location)
      case "void"   => Void(location)
    }
  }

  def processorTemplate[u: P](
    keyword: String,
    minInlets: Int = 0,
    maxInlets: Int = 0,
    minOutlets: Int = 0,
    maxOutlets: Int = 0
  ): P[Processor] = {
    P(
      location ~ keyword ~/ identifier ~ authorRefs ~ is ~ open ~
        processorBody(minInlets, maxInlets, minOutlets, maxOutlets) ~ close ~
        briefly ~ description
    ).map { case (location, id, auths, (options, definitions), brief, desc) =>
      val groups = definitions.groupBy(_.getClass)
      val inlets = mapTo[Inlet](groups.get(classOf[Inlet]))
      val outlets = mapTo[Outlet](groups.get(classOf[Outlet]))
      val handlers = mapTo[Handler](groups.get(classOf[Handler]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val includes = mapTo[Include[ProcessorDefinition]](groups.get(
        classOf[Include[ProcessorDefinition]]
      ))
      Processor(
        location,
        id,
        keywordToKind(keyword, location),
        inlets,
        outlets,
        handlers,
        includes,
        auths,
        options,
        terms,
        brief,
        desc
      )
    }
  }

  val MaxStreamlets = 1000

  def source[u: P]: P[Processor] = {
    processorTemplate(Keywords.source, minOutlets = 1, maxOutlets = 1)
  }

  def sink[u: P]: P[Processor] = {
    processorTemplate(Keywords.sink, minInlets = 1, maxInlets = 1)
  }

  def flow[u: P]: P[Processor] = {
    processorTemplate(
      Keywords.flow,
      minInlets = 1,
      maxInlets = 1,
      minOutlets = 1,
      maxOutlets = 1
    )
  }

  def split[u: P]: P[Processor] = {
    processorTemplate(
      Keywords.split,
      minInlets = 1,
      maxInlets = 1,
      minOutlets = 2,
      maxOutlets = MaxStreamlets
    )
  }

  def merge[u: P]: P[Processor] = {
    processorTemplate(
      Keywords.merge,
      minInlets = 2,
      maxInlets = MaxStreamlets,
      minOutlets = 1,
      maxOutlets = 1
    )
  }

  def multi[u: P]: P[Processor] = {
    processorTemplate(
      Keywords.multi,
      minInlets = 2,
      maxInlets = MaxStreamlets,
      minOutlets = 2,
      maxOutlets = MaxStreamlets
    )
  }

  def void[u: P]: P[Processor] = { processorTemplate(Keywords.void) }

  def processor[u: P]: P[Processor] =
    P(source | flow | sink | merge | split | multi | void)

}
