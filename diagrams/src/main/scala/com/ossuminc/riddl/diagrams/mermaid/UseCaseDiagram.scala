/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.FileBuilder

import scala.reflect.ClassTag

/** A class to generate the sequence diagrams for an Epic's Use Case
  * @param sds
  *   The UseCaseDiagramSupport implementation that provides information for the UseCaseDiagram
  * @param useCase
  * The UseCase from the AST to which this diagram applies
  */
@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
case class UseCaseDiagram(sds: UseCaseDiagramSupport, useCase: UseCase) extends FileBuilder {

  private final val indent_per_level = 4

  def generate: Seq[String] = {
    sb.append("sequenceDiagram"); nl
    sb.append(s"${ndnt()}autonumber"); nl
    val parts: Seq[Definition[?]] = actors.values.toSeq.sortBy(_.kind)
    makeParticipants(parts)
    generateInteractions(useCase.contents, indent_per_level)
    nl
    sb.toString().split('\n').toSeq
  }

  private def actorsFirst(a: (String, Definition[?]), b: (String, Definition[?])): Boolean = {
    a._2 match
      case _: User if b._2.isInstanceOf[User]       => a._1 < b._1
      case _: User                                  => true
      case _: Definition[?] if b._2.isInstanceOf[User] => false
      case _: Definition[?]                            => a._1 < b._1
  }

  private val actors: Map[String, Definition[?]] = {
    useCase.contents
      .map { (interaction: Interaction) =>
        interaction match
          case gi: TwoReferenceInteraction =>
            val fromDef = sds.getDefinitionFor[Definition[?]](gi.from.pathId, gi)
            val toDef = sds.getDefinitionFor[Definition[?]](gi.to.pathId, gi)
            Seq(
              gi.from.pathId.format -> fromDef,
              gi.to.pathId.format -> toDef
            )
          case _ => Seq.empty
      }
      .filterNot(_.isEmpty) // ignore empty things with no references
      .flatten // get rid of seq of seq
      .filterNot(_._1.isEmpty)
      .map(x => x._1 -> x._2.getOrElse(Root.empty))
      .distinctBy(_._1) // reduce to the distinct ones
      .sortWith(actorsFirst)
      .toMap
  }

  private def ndnt(width: Int = indent_per_level): String = {
    " ".repeat(width)
  }

  private def makeParticipants(parts: Seq[Definition[?]]): Unit = {
    parts.foreach { (part: Definition[?]) =>
      val name = part.id.value
      part match
        case u: User       => sb.append(s"${ndnt()}actor $name as ${u.is_a.s}")
        case i: Input => sb.append(s"${ndnt()}participant $name as ${i.nounAlias} ${i.id.value}")
        case o: Output => sb.append(s"${ndnt()}participant $name as ${o.nounAlias} ${o.id.value}")
        case g: Group      => sb.append(s"${ndnt()}participant $name as ${g.alias} ${g.id.value}")
        case d: Definition[?] => sb.append(s"${ndnt()}participant $name as ${d.identify}")
      nl
    }
    parts.foreach { (part: Definition[?]) =>
      val name = part.id.value
      val link = sds.makeDocLink(part)
      part match
        case _: User       => sb.append(s"${ndnt()}link $name: User @ $link")
        case i: Input => sb.append(s"${ndnt()}link $name: ${i.nounAlias} @ $link")
        case o: Output => sb.append(s"${ndnt()}link $name: ${o.nounAlias} @ $link")
        case g: Group      => sb.append(s"${ndnt()}link $name: ${g.alias} @ $link")
        case d: Definition[?] => sb.append(s"${ndnt()}link $name: ${d.kind} @ $link")
      nl
    }
  }

  private def generateInteractions(interactions: Seq[Interaction], indent: Int): Unit = {
    interactions.foreach { (interaction: Interaction) =>
      interaction match
        case gi: GenericInteraction     => genericInteraction(gi, indent)
        case si: SequentialInteractions => sequentialInteractions(si, indent)
        case pi: ParallelInteractions   => parallelInteractions(pi, indent)
        case oi: OptionalInteractions   => optionalInteractions(oi, indent)
    }
  }

  private def genericInteraction(gi: GenericInteraction, indent: Int): Unit = {
    gi match {
      case tri: TwoReferenceInteraction =>
        val from = actors(tri.from.pathId.format).id.value
        val to = actors(tri.to.pathId.format).id.value
        sb.append(s"${ndnt(indent)}$from->>$to: ${tri.relationship.s}")
        nl
      case vi: VagueInteraction =>
        val from = vi.from.s
        val to = vi.to.s
        sb.append(s"${ndnt(indent)}$from->>$to: ${vi.relationship.s}")
      case smi: SendMessageInteraction =>
        val from = actors(smi.from.pathId.format).id.value
        val to = actors(smi.to.pathId.format).id.value
        sb.append(s"${ndnt(indent)}$from->>$to: send ${smi.message.format} to")
      case di: DirectUserToURLInteraction =>
        val from = actors(di.from.pathId.format).id.value
        val to = "Internet"
        sb.append(s"${ndnt(indent)}$from->>$to: direct to ${di.url.toExternalForm}")
    }
  }

  private def sequentialInteractions(si: SequentialInteractions, indent: Int): Unit = {
    generateInteractions(si.contents, indent + indent_per_level)
  }

  private def parallelInteractions(pi: ParallelInteractions, indent: Int): Unit = {
    sb.append(s"${ndnt(indent)}par ${pi.briefValue}")
    generateInteractions(pi.contents, indent + indent_per_level)
    sb.append(s"${ndnt(indent)}end")
  }

  private def optionalInteractions(oi: OptionalInteractions, indent: Int): Unit = {
    sb.append(s"${ndnt(indent)}opt ${oi.briefValue}")
    generateInteractions(oi.contents, indent + indent_per_level)
    sb.append(s"${ndnt(indent)}end")
  }
}
