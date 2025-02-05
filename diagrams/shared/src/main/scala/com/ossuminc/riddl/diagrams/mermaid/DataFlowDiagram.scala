/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.passes.symbols.SymbolsPass
import com.ossuminc.riddl.passes.resolve.ResolutionPass
import com.ossuminc.riddl.utils.{FileBuilder, PlatformContext}

import scala.scalajs.js.annotation.*

/** Generate a data flow diagram Like this:
  * {{{
  * flowchart TD
  *   A[Christmas] -->|Get money| B(Go shopping)
  *   B --> C{Let me think}
  *   C -->|One| D[Laptop]
  *   C -->|Two| E[iPhone]
  *   C -->|Three| F[fa:fa-car Car]
  * }}}
  *
  * @param pr
  *   The PassesResult from running the standard passes to obtain all the collected ideas.
  */
@JSExportTopLevel("DataFlowDiagram")
case class DataFlowDiagram(pr: PassesResult)(using pc: PlatformContext) extends FileBuilder {

  require(pr.hasOutputOf(SymbolsPass.name))
  require(pr.hasOutputOf(ResolutionPass.name))

  override val spaces_per_level = 2

  private def makeNodeLabel(definition: Definition): Unit = {
    pr.symbols.parentOf(definition) match {
      case Some(parent) =>
        val name = parent.id.value + "." + definition.id.value
        val id = definition match {
          case _: Outlet     => s"Outlet $name"
          case _: Inlet      => s"Inlet $name"
          case s: Streamlet  => s"${s.kind} $name"
          case _: Connector  => s"Connector $name"
          case d: Definition => s"${d.kind} $name"
        }
        val (left, right) = definition match {
          case _: Outlet       => "[\\" -> "\\]"
          case _: Inlet        => "[/" -> "/]"
          case _: Streamlet    => "[[" -> "]]"
          case _: Processor[?] => "[{" -> "}]"
          case _: Definition   => "[" -> "]"
        }
        addLine(s"${definition.id.value}$left\"$id\"$right")
      case _ =>
        addLine(s"${definition.id.value}")
    }
  }

  private[mermaid] def makeConnection(from: Outlet, to: Inlet, thick: Boolean, how: String): Unit = {
    val fromName = from.id.value
    val toName = to.id.value
    if thick then addLine(s"$fromName == $how ==> $toName")
    else addLine(s"$fromName -- $how --> $toName")
  }

  private[mermaid] def participants(connector: Connector, parent: Branch[?]): Seq[Definition] = {
    for {
      toDef <- pr.refMap.definitionOf[Inlet](connector.to, parent)
      fromDef <- pr.refMap.definitionOf[Outlet](connector.from, parent)
    } yield {
      val toUsers: Seq[Definition] = pr.usage.getUsers(toDef).flatMap {
        case oc: OnClause => pr.symbols.parentOf(oc).flatMap(pr.symbols.parentOf)
        case e: Entity    => Seq.empty
        case _            => Seq.empty // FIXME: unfinished cases here
      }
      val fromUsers = pr.usage.getUsers(fromDef)
      (Seq(fromDef, toDef) ++ toUsers ++ fromUsers).distinct.filterNot(_.isInstanceOf[Connector])
    }
  }.getOrElse(Seq.empty)

  def generate(context: Context): String = {
    sb.append("flowchart LR"); nl
    val parts = for
      connector <- context.connectors
      participants <- this.participants(connector, context)
    yield participants
    for part <- parts.distinct do makeNodeLabel(part)
    for {
      conn <- context.connectors
      toDef <- pr.refMap.definitionOf[Inlet](conn.to, context)
      fromDef <- pr.refMap.definitionOf[Outlet](conn.from, context)
    } do {
      makeConnection(fromDef, toDef, false, fromDef.type_.identify)
    }
    sb.result()
  }

}
