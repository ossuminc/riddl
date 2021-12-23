package com.yoppworks.ossum.riddl.language

import fastparse._
import ScalaWhitespace._
import com.yoppworks.ossum.riddl.language.AST._
import Terminals.Keywords
import Terminals.Readability

/** Unit Tests For StreamingParser */
trait StreamingParser extends CommonParser with TypeParser {

  def pipeDefinition[_: P]: P[Pipe] = {
    location ~ Keywords.pipe ~/ identifier ~ is ~ open ~ Keywords.transmit ~ typeRef ~ close ~/
      description
  }.map { case (loc, id, typeRef, desc) => Pipe(loc, id, typeRef, desc) }

  def inlet[_: P]: P[Inlet] = {
    P(location ~ Keywords.inlet ~/ identifier ~ is ~ typeRef ~/ description).map {
      case (loc, id, typeRef, desc) => Inlet(loc, id, typeRef, desc)
    }
  }

  def outlet[_: P]: P[Outlet] = {
    P(location ~ Keywords.outlet ~/ identifier ~ is ~ typeRef ~/ description).map {
      case (loc, id, typeRef, desc) => Outlet(loc, id, typeRef, desc)
    }
  }

  def processorBody[_: P]: P[(Seq[Inlet], Seq[Outlet])] = {
    P((inlet | outlet).rep(1).map { seq: Seq[Streamlet] =>
      val groups = seq.groupBy(_.getClass)
      mapTo[Inlet](groups.get(classOf[Inlet])) -> mapTo[Outlet](groups.get(classOf[Outlet]))
    })
  }

  def processorDefinition[_: P]: P[Processor] = P(
    location ~ Keywords.processor ~/ identifier ~ is ~ open ~ processorBody ~ close ~/ description
  ).map { case (loc, id, (inlets, outlets), description) =>
    Processor(loc, id, inlets, outlets, description)
  }

  def outletRef[_: P]: P[DefRef[Outlet]] = { P(location ~ Keywords.outlet ~ pathIdentifier) }.map {
    case (loc, pid) => DefRef[Outlet](loc, pid)
  }

  def inletRef[_: P]: P[DefRef[Inlet]] = { P(location ~ Keywords.outlet ~ pathIdentifier) }.map {
    case (loc, pid) => DefRef[Inlet](loc, pid)
  }

  def pipeRef[_: P]: P[DefRef[Pipe]] = { P(location ~ Keywords.pipe ~ pathIdentifier) }.map {
    case (loc, pid) => DefRef[Pipe](loc, pid)
  }

  def inJoint[_: P]: P[InJoint] = {
    P(
      location ~ Keywords.joint ~/ identifier ~ is ~ inletRef ~ Readability.from ~ pipeRef ~/
        description
    ).map { case (loc, id, inRef, pipeRef, desc) => InJoint(loc, id, inRef, pipeRef, desc) }
  }

  def outJoint[_: P]: P[OutJoint] = {
    P(
      location ~ Keywords.joint ~/ identifier ~ is ~ outletRef ~ Readability.to ~ pipeRef ~/
        description
    ).map { case (loc, id, outRef, pipeRef, desc) => OutJoint(loc, id, outRef, pipeRef, desc) }
  }

  def plantDefinition[_: P]: P[(Seq[Pipe], Seq[Processor], Seq[InJoint], Seq[OutJoint])] = {
    P(pipeDefinition | processorDefinition | inJoint | outJoint).rep(0).map { seq =>
      val groups = seq.groupBy(_.getClass)
      (
        mapTo[Pipe](groups.get(classOf[Pipe])),
        mapTo[Processor](groups.get(classOf[Processor])),
        mapTo[InJoint](groups.get(classOf[InJoint])),
        mapTo[OutJoint](groups.get(classOf[OutJoint]))
      )
    }
  }

  def plant[_: P]: P[Plant] = {
    P(
      location ~ Keywords.plant ~/ identifier ~ is ~ open ~/
        (undefined.map(_ =>
          (Seq.empty[Pipe], Seq.empty[Processor], Seq.empty[InJoint], Seq.empty[OutJoint])
        ) | plantDefinition) ~ close ~/ description
    ).map { case (loc, id, (pipes, processors, inJoints, outJoints), addendum) =>
      Plant(loc, id, pipes, processors, inJoints, outJoints, addendum)
    }
  }


}
