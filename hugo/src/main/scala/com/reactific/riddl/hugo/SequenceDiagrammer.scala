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
  story: Story,
  parents: Seq[Definition])
    extends FileBuilder {

  val participants: Map[Seq[String], Definition] = {
    for {
      cs <- story.cases
      interaction <- cs.interactions
    } yield { Seq(interaction.from, interaction.to) }
  }.flatten.distinctBy(_.id.value).map { ref: Reference[?] =>
    state.pathIdToDefinition(ref.id, parents) match {
      case Some(definition) => ref.id.value -> definition
      case None => throw new IllegalStateException(
          s"Pre-validated PathId not found: ${ref.identify}"
        )
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
    val name = definition.id.value
    val link = state.makeDocLink(definition)
    definition match {
      case _: Actor      => sb.append(s"  link $name: Definition @ $link"); nl
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
    for { ntrctn <- cse.interactions } {
      val from = participants(ntrctn.from.id.value)
      val to = participants(ntrctn.to.id.value)
      sb.append(
        s"    ${from.id.value}->>${to.id.value}: ${ntrctn.relationship}"
      )
      nl
    }
    sb.append("  end opt"); nl
  }
  sb.append("end"); nl
}
