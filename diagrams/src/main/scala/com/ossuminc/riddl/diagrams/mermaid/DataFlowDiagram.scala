/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.diagrams.mermaid
import com.ossuminc.riddl.language.AST._
import com.ossuminc.riddl.passes.PassesResult

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
case class DataFlowDiagram(pr: PassesResult) {

  final private val newline: String = System.getProperty("line.separator")
  private val sb: StringBuilder = StringBuilder(1000)
  private val spacesPerIndent = 2

  def indent(str: String, level: Int = 1): Unit = {
    sb.append(" ".repeat(level * spacesPerIndent))
    sb.append(str)
    sb.append(newline)
  }

  private def makeNodeLabel(definition: Definition): Unit = {
    pr.symbols.parentOf(definition) match {
      case Some(parent) =>
        val name = parent.id.value + "." + definition.id.value
        val id = definition match {
          case _: Outlet     => s"Outlet $name"
          case _: Inlet      => s"Inlet $name"
          case s: Streamlet  => s"${s.kind} $name"
          case s: Connector  => s"Connector $name"
          case d: Definition => s"${d.kind} $name"
        }
        val (left, right) = definition match {
          case _: Outlet          => "[\\" -> "\\]"
          case _: Inlet           => "[/" -> "/]"
          case _: Streamlet       => "[[" -> "]]"
          case _: Processor[?, ?] => "[{" -> "}]"
          case _: Definition      => "[" -> "]"
        }
        indent(s"${definition.id.value}$left\"$id\"$right")
      case _ =>
        indent(s"${definition.id.value}")
    }
  }

  private[mermaid] def makeConnection(from: Outlet, to: Inlet, thick: Boolean, how: String): Unit = {
    val fromName = from.id.value
    val toName = to.id.value
    if thick then indent(s"$fromName == $how ==> $toName")
    else indent(s"$fromName -- $how --> $toName")
  }

  private[mermaid] def participants(connector: Connector): Seq[Definition] = {
    for {
      flows <- connector.flows
      to <- connector.to
      from <- connector.from
      typeDef <- pr.refMap.definitionOf[Type](flows, connector)
      toDef <- pr.refMap.definitionOf[Inlet](to, connector)
      fromDef <- pr.refMap.definitionOf[Outlet](from, connector)
    } yield {
      val to_users: Seq[Definition] = pr.usage.getUsers(toDef).flatMap {
        case oc: OnClause => pr.symbols.parentOf(oc).flatMap(pr.symbols.parentOf)
        case e: Entity => Seq.empty
        case _ => Seq.empty // FIXME: unfinished cases here
      }
      val from_users = pr.usage.getUsers(fromDef)
      (Seq(fromDef, toDef) ++ to_users ++ from_users).distinct.filterNot(_.isInstanceOf[Connector])
    }
  }.getOrElse(Seq.empty)

  def generate(context: Context): String = {
    sb.append("flowchart LR").append(newline)
    val parts = for
      connector <- context.connections
      participants <- this.participants(connector)
    yield participants
    for part <- parts.distinct do makeNodeLabel(part)
    for {
      conn <- context.connections
      from <- conn.from
      to <- conn.to
      flows <- conn.flows
      typeDef <- pr.refMap.definitionOf[Type](flows, conn)
      toDef <- pr.refMap.definitionOf[Inlet](to, conn)
      fromDef <- pr.refMap.definitionOf[Outlet](from, conn)
    } do {
      makeConnection(fromDef, toDef, false, typeDef.identify)
    }
    sb.result()
  }

}
