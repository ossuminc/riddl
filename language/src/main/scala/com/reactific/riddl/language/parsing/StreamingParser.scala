/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Terminals.{Keywords, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Unit Tests For StreamingParser */
trait StreamingParser extends ReferenceParser with TypeParser with GherkinParser {

  def pipeDefinition[u: P]: P[Pipe] = {
    location ~ Keywords.pipe ~/ identifier ~ is ~ open ~
      (undefined(None) | Keywords.transmit ~/ typeRef.map(Option(_))) ~ close ~ briefly ~
      description
  }.map { tpl => (Pipe.apply _).tupled(tpl) }

  def inlet[u: P]: P[Inlet] = {
    P(
      location ~ Keywords.inlet ~/ identifier ~ is ~ typeRef ~/ (Readability.from ~ entityRef).? ~
        briefly ~ description
    ).map { tpl => (Inlet.apply _).tupled(tpl) }
  }

  def outlet[u: P]: P[Outlet] = {
    P(
      location ~ Keywords.outlet ~/ identifier ~ is ~ typeRef ~/ (Readability.to ~ entityRef).? ~
        briefly ~ description
    ).map { tpl => (Outlet.apply _).tupled(tpl) }
  }

  def toEntity[u: P]: P[EntityRef] = { P(Readability.to ~/ entityRef) }

  def fromEntity[u: P]: P[EntityRef] = { P(Readability.from ~/ entityRef) }

  def source[u: P]: P[Processor] = {
    P(
      location ~ Keywords.source ~/ identifier ~ is ~ open ~
        (outlet.map(Seq(_)) | undefined(Seq.empty[Outlet])) ~ close ~ testedWithExamples ~ briefly ~
        description
    ).map { case (location, id, outlets, examples, brief, description) =>
      Processor(
        location,
        id,
        Source(location),
        Seq.empty[Inlet],
        outlets,
        examples,
        brief,
        description
      )
    }
  }

  def sink[u: P]: P[Processor] = {
    P(
      location ~ Keywords.sink ~/ identifier ~ is ~ open ~
        (inlet.map(Seq(_)) | undefined(Seq.empty[Inlet])) ~ close ~ testedWithExamples ~ briefly ~
        description
    ).map { case (location, id, inlets, examples, brief, description) =>
      Processor(
        location,
        id,
        Sink(location),
        inlets,
        Seq.empty[Outlet],
        examples,
        brief,
        description
      )
    }
  }

  def flow[u: P]: P[Processor] = {
    P(
      location ~ Keywords.flow ~/ identifier ~ is ~ open ~
        ((inlet.map(Seq(_)) ~ outlet.map(Seq(_))) |
          undefined((Seq.empty[Inlet], Seq.empty[Outlet]))) ~ close ~ testedWithExamples ~ briefly ~
        description
    ).map { case (location, id, (inlet, outlet), examples, brief, description) =>
      Processor(location, id, Flow(location), inlet, outlet, examples, brief, description)
    }
  }

  def split[u: P]: P[Processor] = {
    P(
      location ~ Keywords.split ~/ identifier ~ is ~ open ~
        ((inlet.map(Seq(_)) ~ outlet.rep(2)) | undefined((Seq.empty[Inlet], Seq.empty[Outlet]))) ~
        close ~ testedWithExamples ~ briefly ~ description
    ).map { case (location, id, (inlets, outlets), examples, brief, description) =>
      Processor(location, id, Split(location), inlets, outlets, examples, brief, description)
    }
  }

  def merge[u: P]: P[Processor] = {
    P(
      location ~ Keywords.merge ~/ identifier ~ is ~ open ~
        ((inlet.rep(2) ~ outlet.map(Seq(_))) | undefined((Seq.empty[Inlet], Seq.empty[Outlet]))) ~
        close ~ testedWithExamples ~ briefly ~ description
    ).map { case (location, id, (inlets, outlets), examples, brief, description) =>
      Processor(location, id, Merge(location), inlets, outlets, examples, brief, description)
    }
  }

  def multi[u: P]: P[Processor] = {
    P(
      location ~ Keywords.multi ~/ identifier ~ is ~ open ~
        ((inlet.rep(2) ~ outlet.rep(2)) | undefined((Seq.empty[Inlet], Seq.empty[Outlet]))) ~
        close ~ testedWithExamples ~ briefly ~ description
    ).map { case (location, id, (inlets, outlets), examples, brief, description) =>
      Processor(location, id, Multi(location), inlets, outlets, examples, brief, description)
    }
  }

  def processor[u: P]: P[Processor] = P(source | flow | sink | merge | split | multi)

  def joint[u: P]: P[Joint] = {
    P(
      location ~ Keywords.joint ~/ identifier ~ is ~
        ((inletRef ~ Readability.from) | (outletRef ~ Readability.to)) ~/ pipeRef ~ briefly ~
        description
    ).map { case (loc, id, streamletRef, pipeRef, briefly, desc) =>
      streamletRef match {
        case ir: InletRef  => InletJoint(loc, id, ir, pipeRef, briefly, desc)
        case or: OutletRef => OutletJoint(loc, id, or, pipeRef, briefly, desc)
      }
    }
  }

  def plantInclude[X: P]: P[Include] = {
    include[PlantDefinition, X](plantDefinitions(_))
  }

  def plantDefinitions[u: P]: P[Seq[PlantDefinition]] = {
    P(pipeDefinition | processor | joint | term | plantInclude).rep(0)
  }

  def plant[u: P]: P[Plant] = {
    P(
      location ~ Keywords.plant ~/ identifier ~ is ~ open ~/
        (undefined(Seq.empty[PlantDefinition]) | plantDefinitions) ~
        close ~ briefly ~ description
    ).map { case (loc, id, defs, briefly, description) =>
      val groups = defs.groupBy(_.getClass)
      val pipes = mapTo[Pipe](groups.get(classOf[Pipe]))
      val processors = mapTo[Processor](groups.get(classOf[Processor]))
      val inJoints = mapTo[InletJoint](groups.get(classOf[InletJoint]))
      val outJoints = mapTo[OutletJoint](groups.get(classOf[OutletJoint]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val includes = mapTo[Include](groups.get(classOf[Include]))
      Plant(loc, id, pipes, processors, inJoints, outJoints, terms, includes, briefly, description)
    }
  }
}
