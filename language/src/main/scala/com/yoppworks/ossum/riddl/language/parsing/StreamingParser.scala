package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Readability}
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

  def plantDefinitions[u: P]: P[(Seq[Pipe], Seq[Processor], Seq[InletJoint], Seq[OutletJoint])] = {
    P(pipeDefinition | processor | joint).rep(0).map { seq =>
      val groups = seq.groupBy(_.getClass)
      (
        mapTo[Pipe](groups.get(classOf[Pipe])),
        mapTo[Processor](groups.get(classOf[Processor])),
        mapTo[InletJoint](groups.get(classOf[InletJoint])),
        mapTo[OutletJoint](groups.get(classOf[OutletJoint]))
      )
    }
  }

  def plant[u: P]: P[Plant] = {
    P(
      location ~ Keywords.plant ~/ identifier ~ is ~ open ~/
        (undefined(
          (Seq.empty[Pipe], Seq.empty[Processor], Seq.empty[InletJoint], Seq.empty[OutletJoint])
        ) | plantDefinitions) ~ close ~ briefly ~ description
    ).map { case (loc, id, (pipes, processors, inletJoints, outletJoints), briefly, description) =>
      Plant(loc, id, pipes, processors, inletJoints, outletJoints, briefly, description)
    }
  }
}
