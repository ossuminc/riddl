/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.FileBuilder
import com.ossuminc.riddl.diagrams.UseCaseDiagramData

import scala.reflect.ClassTag

/** A class to generate the sequence diagrams for an Epic's Use Case
  * @param ucdd
  *   The UseCaseDiagramData from the DiagramsPass for this
  */
@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
case class UseCaseDiagram(sds: UseCaseDiagramSupport, ucdd: UseCaseDiagramData) extends FileBuilder {
  
  def generate: Seq[String] = {
    sb.append("sequenceDiagram"); nl
    sb.append(s"${indent()}autonumber"); nl
    val parts: Seq[Definition] = ucdd.actors.values.toSeq.sortBy(_.kind)
    makeParticipants(parts)
    generateInteractions(ucdd.interactions)
    nl
    sb.toString().split('\n').toSeq
  }


  private def makeParticipants(parts: Seq[Definition]): Unit = {
    parts.foreach { (part: Definition) =>
      val name = part.id.value
      part match
        case u: User       => sb.append(s"${indent()}actor $name as ${u.is_a.s}")
        case i: Input      => sb.append(s"${indent()}participant $name as ${i.identify}")
        case o: Output     => sb.append(s"${indent()}participant $name as ${o.identify}")
        case g: Group      => sb.append(s"${indent()}participant $name as ${g.identify}")
        case d: Definition => sb.append(s"${indent()}participant $name as ${d.identify}")
      nl
    }
    parts.foreach { (part: Definition) =>
      val name = part.id.value
      val link = sds.makeDocLink(part)
      part match
        case _: User       => sb.append(s"${indent()}link $name: User @ $link")
        case i: Input      => sb.append(s"${indent()}link $name: ${i.nounAlias} @ $link")
        case o: Output     => sb.append(s"${indent()}link $name: ${o.nounAlias} @ $link")
        case g: Group      => sb.append(s"${indent()}link $name: ${g.alias} @ $link")
        case d: Definition => sb.append(s"${indent()}link $name: ${d.kind} @ $link")
      nl
    }
  }

  private def generateInteractions(interactions: Seq[Interaction | Comment], level: Int = 1): Unit = {
    interactions.foreach {
      case gi: GenericInteraction     => genericInteraction(gi, level)
      case si: SequentialInteractions => sequentialInteractions(si, level)
      case pi: ParallelInteractions   => parallelInteractions(pi, level)
      case oi: OptionalInteractions   => optionalInteractions(oi, level)
      case _: Comment                 => ()
    }
  }

  private def genericInteraction(gi: GenericInteraction, level: Int): Unit = {
    gi match {
      case fogi: FocusOnGroupInteraction =>
        val from = ucdd.actors(fogi.from.pathId.format).id.value
        val to = fogi.to.keyword + " " + ucdd.actors(fogi.to.pathId.format).id.value
        indent(s"$from->>$to: set focus on", level)
      case vi: VagueInteraction =>
        val from = vi.from.s
        val to = vi.to.s
        indent(s"$from->>$to: ${vi.relationship.s}", level)
      case smi: SendMessageInteraction =>
        val from = ucdd.actors(smi.from.pathId.format).id.value
        val to = ucdd.actors(smi.to.pathId.format).id.value
        indent(s"$from->>$to: send ${smi.message.format} to", level)
      case di: DirectUserToURLInteraction =>
        val from = ucdd.actors(di.from.pathId.format).id.value
        val to = "Internet"
        indent(s"$from->>$to: direct to ${di.url.toExternalForm}",level)
      case tri: TwoReferenceInteraction =>
        val from = ucdd.actors(tri.from.pathId.format).id.value
        val to = ucdd.actors(tri.to.pathId.format).id.value
        indent(s"$from->>$to: ${tri.relationship.s}", level)
    }
    nl
  }

  private def sequentialInteractions(si: SequentialInteractions, level: Int): Unit = {
    generateInteractions(si.contents, level + 1)
  }

  private def parallelInteractions(pi: ParallelInteractions, level: Int): Unit = {
    indent(s"par ${pi.briefValue}", level)
    generateInteractions(pi.contents.filter[Interaction], level + 1)
    indent(s"end", level)
  }

  private def optionalInteractions(oi: OptionalInteractions, level: Int): Unit = {
    indent(s"opt ${oi.briefValue}", level)
    generateInteractions(oi.contents.filter[Interaction], level + 1)
    indent("end", level)
  }
}
