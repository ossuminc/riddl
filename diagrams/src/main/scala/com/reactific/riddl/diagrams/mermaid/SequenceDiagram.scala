/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.diagrams.mermaid

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.passes.PassesResult
import com.reactific.riddl.utils.FileBuilder

import scala.reflect.ClassTag

/** A trait to be implemented by the user of SequenceDiagram that provides information that can only be provided from
  * outside SequenceDiagram itself. Note that the PassesResult from running the standard passes is required.
  */
trait SequenceDiagramSupport {
  def passesResult: PassesResult
  def getDefinitionFor[T <: Definition: ClassTag](pathId: PathIdentifier, parent: Definition): Option[T] = {
    passesResult.refMap.definitionOf[T](pathId, parent)
  }
  def makeLinkFor(definition: Definition): String
}

/** A class to generate the sequence diagrams for an Epic
  * @param sds
  *   The SequenceDiagramSupport implementation that provides information for the SequenceDiagram
  * @param epic
  *   The epic from which to draw use cases instances and convert into sequence diagrams
  * @param parents
  *   The parents of the epic within the model
  */
@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
case class SequenceDiagram(sds: SequenceDiagramSupport, useCase: UseCase) extends FileBuilder {

  final val indent_per_level = 4

  def generate: Seq[String] = {
    sb.append("sequenceDiagram"); nl
    sb.append(s"${ndnt()}autonumber"); nl
    val parts: Seq[Definition] = actors.values.toSeq.sortBy(_.kind)
    makeParticipants(parts)
    generateInteractions(useCase.contents, indent_per_level)
    nl
    sb.toString().split('\n').toSeq
  }

  def actorsFirst(a: (String, Definition), b: (String, Definition)): Boolean = {
    a._2 match
      case _: User if b._2.isInstanceOf[User]       => a._1 < b._1
      case _: User                                  => true
      case _: Definition if b._2.isInstanceOf[User] => false
      case _: Definition                            => a._1 < b._1
  }

  private val actors: Map[String, Definition] = {
    useCase.contents
      .map { (interaction: Interaction) =>
        interaction match
          case gi: GenericInteraction =>
            val fromDef = sds.getDefinitionFor[Definition](gi.from.pathId, gi)
            val toDef = sds.getDefinitionFor[Definition](gi.to.pathId, gi)
            Seq(
              gi.from.pathId.format -> fromDef,
              gi.to.pathId.format -> toDef
            )
          case _ => Seq.empty
      }
      .filterNot(_.isEmpty) // ignore empty things with no references
      .flatten // get rid of seq of seq
      .filterNot(_._1.isEmpty)
      .map(x => x._1 -> x._2.getOrElse(RootContainer.empty))
      .distinctBy(_._1) // reduce to the distinct ones
      .sortWith(actorsFirst)
      .toMap
  }

  def ndnt(width: Int = indent_per_level): String = {
    " ".repeat(width)
  }

  private def makeParticipants(parts: Seq[Definition]): Unit = {
    parts.foreach { (part: Definition) =>
      val name = part.id.value
      part match
        case u: User       => sb.append(s"${ndnt()}actor $name as ${u.is_a.s}")
        case i: Input      => sb.append(s"${ndnt()}participant $name as ${i.alias} ${i.id.value}")
        case o: Output     => sb.append(s"${ndnt()}participant $name as ${o.alias} ${o.id.value}")
        case g: Group      => sb.append(s"${ndnt()}participant $name as ${g.alias} ${g.id.value}")
        case d: Definition => sb.append(s"${ndnt()}participant $name as ${d.identify}")
      nl
    }
    parts.foreach { (part: Definition) =>
      val name = part.id.value
      val link = sds.makeLinkFor(part)
      part match
        case _: User       => sb.append(s"${ndnt()}link $name: User @ $link")
        case i: Input      => sb.append(s"${ndnt()}link $name: ${i.alias} @ $link")
        case o: Output     => sb.append(s"${ndnt()}link $name: ${o.alias} @ $link")
        case g: Group      => sb.append(s"${ndnt()}link $name: ${g.alias} @ $link")
        case d: Definition => sb.append(s"${ndnt()}link $name: ${d.kind} @ $link")
      nl
    }
  }

  private def generateInteractions(interactions: Seq[Interaction], indent: Int): Unit = {
    interactions.foreach { (interaction: Interaction) =>
      interaction match
        case gi: GenericInteraction     => genericInteraction(gi, indent)
        case si: SequentialInteractions => sequentialInteractions(si, indent)
        case pi: ParallelInteractions   => parallelInteractions(pi, indent)
        case vi: VagueInteraction       => vagueInteraction(vi, indent)
        case oi: OptionalInteractions   => optionalInteractions(oi, indent)
    }
  }

  private def genericInteraction(gi: GenericInteraction, indent: Int): Unit = {
    val from = actors(gi.from.pathId.format).id.value
    val to = actors(gi.to.pathId.format).id.value
    sb.append(s"${ndnt(indent)}$from->>$to: ${gi.relationship.s}")
    nl
  }

  private def sequentialInteractions(si: SequentialInteractions, indent: Int): Unit = {
    generateInteractions(si.contents, indent + indent_per_level)
  }

  private def parallelInteractions(pi: ParallelInteractions, indent: Int): Unit = {
    sb.append(s"${ndnt(indent)}par ${pi.briefValue}")
    generateInteractions(pi.contents, indent + indent_per_level)
    sb.append(s"${ndnt(indent)}end")
  }

  private def vagueInteraction(vi: VagueInteraction, indent: Int): Unit = {
    actors.headOption match {
      case Some((name: String, _: Definition)) =>
        sb.append(s"${ndnt(indent)}Note right of $name: ${vi.relationship}")
      case None =>
        sb.append(s"${ndnt(indent)}Note: Error:No first actor to base vagueInteraction upon")
    }
  }

  private def optionalInteractions(oi: OptionalInteractions, indent: Int): Unit = {
    sb.append(s"${ndnt(indent)}opt ${oi.briefValue}")
    generateInteractions(oi.contents, indent + indent_per_level)
    sb.append(s"${ndnt(indent)}end")
  }
}