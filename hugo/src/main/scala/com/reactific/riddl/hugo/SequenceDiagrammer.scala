/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.hugo
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.utils.FileBuilder

case class SequenceDiagrammer(
  state: HugoTranslatorState,
  story: Epic,
  parents: Seq[Definition])
    extends FileBuilder {

  val participants: Map[Seq[String], Definition] = {
    (for {
      cs <- story.cases
      interaction <- cs.contents
    } yield {
      interaction match {
        case is: GenericInteraction => Seq[Reference[Definition]](is.from, is.to)
        case _ => Seq.empty[Reference[Definition]]
      }
    }).filterNot(_.isEmpty).flatten.distinctBy(_.pathId.value).map { (ref: Reference[Definition]) =>
      state.pathIdToDefinition(ref.pathId, parents) match {
        case Some(definition) => ref.pathId.value -> definition
        case None => throw new IllegalStateException(
          s"Pre-validated PathId not found: ${ref.identify}"
        )
      }
    }
  }.toMap

  def makeParticipant(definition: Definition): Unit = {
    val name = definition.id.value
    definition match {
      case _: Actor      => sb.append(s"  actor $name"); nl
      case _: Definition => sb.append(s"  participant $name"); nl
    }
  }

  def makeLink(definition: Definition): Unit = {
    val name = definition.identify
    val link = state.makeDocLink(definition)
    definition match {
      case _: Actor      => sb.append(s"  link $name:  @ $link"); nl
      case _: Definition => sb.append(s"  link $name: Definition @ $link"); nl
    }
  }

  sb.append("sequenceDiagram"); nl
  sb.append("  autonumber"); nl
  val parts: Seq[Definition] = participants.values.toSeq.sortBy(_.kind)
  parts.foreach(x => makeParticipant(x))
  parts.foreach(x => makeLink(x))

  for { cse <- story.cases } {
    sb.append(s"  opt ${cse.id.value} - ${cse.briefValue}"); nl
    for { ntrctn <- cse.contents} ntrctn match {
      case is: GenericInteraction =>
        val from = participants(is.from.pathId.value)
        val to = participants(is.to.pathId.value)
        sb.append(s"    ${from.id.value}->>${to.id.value}: ${is.relationship}")
        nl
      case _: SequentialInteractions => // TODO: include sequential groups
      case _: ParallelInteractions => // TODO: include parallel groups
      case _: OptionalInteractions => // TODO: include optional groups
    }
    sb.append("  end opt"); nl
  }
  sb.append("end"); nl
}
