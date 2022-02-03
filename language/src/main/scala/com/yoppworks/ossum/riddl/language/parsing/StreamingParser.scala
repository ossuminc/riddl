package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Unit Tests For StreamingParser */
trait StreamingParser extends ReferenceParser with TypeParser with GherkinParser {

  def pipeDefinition[u: P]: P[Pipe] = {
    location ~ Keywords.pipe ~/ identifier ~ is ~ open ~
      (undefined(None) | Keywords.transmit ~/ typeRef.map(Option(_))) ~ close ~ description
  }.map { case (loc, id, typeRef, desc) => Pipe(loc, id, typeRef, desc) }

  def inlet[u: P]: P[Inlet] = {
    P(location ~ Keywords.inlet ~/ identifier ~ is ~ typeRef ~/ description).map {
      case (loc, id, typeRef, desc) => Inlet(loc, id, typeRef, desc)
    }
  }

  def outlet[u: P]: P[Outlet] = {
    P(location ~ Keywords.outlet ~/ identifier ~ is ~ typeRef ~/ description).map {
      case (loc, id, typeRef, desc) => Outlet(loc, id, typeRef, desc)
    }
  }

  def processorDefinitions[u: P]: P[(Seq[Inlet], Seq[Outlet], Seq[Example])] = {
    P(
      undefined((Seq.empty[Inlet], Seq.empty[Outlet], Seq.empty[Example])) |
        ((inlet | outlet).rep(1) ~ examples).map { case (streams, exmpls) =>
          val groups = streams.groupBy(_.getClass)
          (
            mapTo[Inlet](groups.get(classOf[Inlet])),
            mapTo[Outlet](groups.get(classOf[Outlet])),
            exmpls
          )
        }
    )
  }

  def processor[u: P]: P[Processor] = P(
    location ~ Keywords.processor ~ identifier ~ is ~ open ~
      processorDefinitions ~ close ~ briefly ~ description
  ).map { case (loc, id, (inlets, outlets, examples), briefly, description) =>
    Processor(loc, id, inlets, outlets, examples, briefly, description)
  }

  def joint[u: P]: P[Joint] = {
    P(
      location ~ Keywords.joint ~/ identifier ~ is ~
        ((inletRef ~ Readability.from) | (outletRef ~ Readability.to)) ~/ pipeRef ~ description
    ).map { case (loc, id, streamletRef, pipeRef, desc) =>
      streamletRef match {
        case ir: InletRef => InletJoint(loc, id, ir, pipeRef, desc)
        case or: OutletRef => OutletJoint(loc, id, or, pipeRef, desc)
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
        mapTo[OutletJoint](groups.get(classOf[OutletJoint])),
      )
    }
  }

  def plant[u: P]: P[Plant] = {
    P(
      location ~ Keywords.plant ~/ identifier ~ is ~ open ~/
        (undefined((Seq.empty[Pipe], Seq.empty[Processor],
          Seq.empty[InletJoint], Seq.empty[OutletJoint])) | plantDefinitions) ~
        close ~ briefly ~ description
    ).map { case (loc, id, (pipes, processors, inletJoints, outletJoints), briefly, description) =>
      Plant(loc, id, pipes, processors, inletJoints, outletJoints, briefly, description)
    }
  }
}
