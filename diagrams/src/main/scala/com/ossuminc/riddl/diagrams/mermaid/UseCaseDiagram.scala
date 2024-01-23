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
case class UseCaseDiagram(sds: UseCaseDiagramSupport, ucdd: UseCaseDiagramData) extends SequenceDiagramGenerator {
  
  private val participants: Seq[Definition] = ucdd.actors.values.toSeq.sortBy(_.kind)
  makeParticipants(participants)
  generateInteractions(ucdd.interactions)
  decr
  nl
  
  def title: String = ucdd.name
  
  def frontMatterItems: Map[String, String] = Map(
      "theme" -> "dark",
      "forceMenus" -> "true",
      "wrap" -> "true",
      "mirrorActors" -> "false",
      "messageFontFamily" -> "monospace"
    )

  private def makeParticipants(parts: Seq[Definition]): Unit = {
    parts.foreach { (part: Definition) =>
      val name = part.id.value
      part match
        case u: User       => addIndent(s"actor $name as ${u.is_a.s}")
        case i: Input      => addIndent(s"participant $name as ${i.nounAlias} ${i.id.value}")
        case o: Output     => addIndent(s"participant $name as ${o.nounAlias} ${o.id.value}")
        case g: Group      => addIndent(s"participant $name as ${g.alias} ${g.id.value}")
        case d: Definition => addIndent(s"participant $name as ${d.identify}")
    }
    parts.foreach { (part: Definition) =>
      val name = part.id.value
      val link = sds.makeDocLink(part)
      part match
        case _: User       => ()
        case i: Input      => addIndent(s"link $name: ${i.nounAlias} @ $link")
        case o: Output     => addIndent(s"link $name: ${o.nounAlias} @ $link")
        case g: Group      => addIndent(s"link $name: ${g.alias} @ $link")
        case d: Definition => addIndent(s"link $name: ${d.kind} @ $link")
    }
  }

  private def generateInteractions(interactions: Seq[Interaction | Comment]): Unit = {
    interactions.foreach {
      case gi: GenericInteraction     => genericInteraction(gi)
      case si: SequentialInteractions => sequentialInteractions(si)
      case pi: ParallelInteractions   => parallelInteractions(pi)
      case oi: OptionalInteractions   => optionalInteractions(oi)
      case _: Comment                 => ()
    }
  }

  private def actorName(key: String): String = {
    ucdd.actors.get(key) match {
      case Some(definition) => definition.id.value
      case None             => key
    }
  }

  private def genericInteraction(gi: GenericInteraction): Unit = {
    gi match {
      case fogi: FocusOnGroupInteraction =>
        val from = actorName(fogi.from.pathId.format)
        val to = fogi.to.keyword + " " + ucdd.actors(fogi.to.pathId.format).id.value
        addIndent(s"$from->>$to: set focus on")
      case vi: VagueInteraction =>
        val from = vi.from.s
        val to = vi.to.s
        addIndent(s"$from->>$to: ${vi.relationship.s}")
      case smi: SendMessageInteraction =>
        val from = actorName(smi.from.pathId.format)
        val to = actorName(smi.to.pathId.format)
        addIndent(s"$from->>$to: send ${smi.message.format} to")
      case di: DirectUserToURLInteraction =>
        val from = actorName(di.from.pathId.format)
        val to = "Internet"
        addIndent(s"$from->>$to: direct to ${di.url.toExternalForm}")
      case tri: TwoReferenceInteraction =>
        val from = actorName(tri.from.pathId.format)
        val to = actorName(tri.to.pathId.format)
        addIndent(s"$from->>$to: ${tri.relationship.s}")
    }
  }

  private def sequentialInteractions(si: SequentialInteractions): Unit = {
    generateInteractions(si.contents)
  }

  private def parallelInteractions(pi: ParallelInteractions): Unit = {
    addIndent(s"par ${pi.briefValue}")
    incr
    generateInteractions(pi.contents.filter[Interaction])
    decr
    addIndent(s"end")
  }

  private def optionalInteractions(oi: OptionalInteractions): Unit = {
    addIndent(s"opt ${oi.briefValue}")
    incr
    generateInteractions(oi.contents.filter[Interaction])
    decr
    addIndent("end")
  }
}
