package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.Keywords
import com.yoppworks.ossum.riddl.language.Terminals.Readability
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Unit Tests For StreamingParser */
trait StreamingParser extends ReferenceParser with TypeParser {

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

  def processorDefinitions[u: P]: P[(Seq[Inlet], Seq[Outlet])] = {
    P(undefined((Seq.empty[Inlet], Seq.empty[Outlet])) | (inlet | outlet).rep(1).map {
      seq: Seq[Streamlet] =>
        val groups = seq.groupBy(_.getClass)
        mapTo[Inlet](groups.get(classOf[Inlet])) -> mapTo[Outlet](groups.get(classOf[Outlet]))
    })
  }

  def processor[u: P]: P[Processor] = P(
    location ~ Keywords.processor ~/ identifier ~ is ~ open ~ processorDefinitions ~ close ~
      description
  ).map { case (loc, id, (inlets, outlets), description) =>
    Processor(loc, id, inlets, outlets, description)
  }

  def joint[u: P]: P[Joint] = {
    P(
      location ~ Keywords.joint ~/ identifier ~ is ~
        ((inletRef ~ Readability.from) | (outletRef ~ Readability.to)) ~/ pipeRef ~ description
    ).map { case (loc, id, streamletRef, pipeRef, desc) =>
      Joint(loc, id, streamletRef, pipeRef, desc)
    }
  }

  def plantDefinitions[u: P]: P[(Seq[Pipe], Seq[Processor], Seq[Joint])] = {
    P(pipeDefinition | processor | joint).rep(0).map { seq =>
      val groups = seq.groupBy(_.getClass)
      (
        mapTo[Pipe](groups.get(classOf[Pipe])),
        mapTo[Processor](groups.get(classOf[Processor])),
        mapTo[Joint](groups.get(classOf[Joint]))
      )
    }
  }

  def plant[u: P]: P[Plant] = {
    P(
      location ~ Keywords.plant ~/ identifier ~ is ~ open ~/
        (undefined(Seq.empty[Pipe], Seq.empty[Processor], Seq.empty[Joint]) | plantDefinitions) ~
        close ~ description
    ).map { case (loc, id, (pipes, processors, joints), addendum) =>
      Plant(loc, id, pipes, processors, joints, addendum)
    }
  }

}
