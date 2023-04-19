/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.hugo

import com.reactific.riddl.language.AST
import com.reactific.riddl.language.AST.*

import scala.collection.mutable

object MermaidDiagramsPlugin {
  val containerStyles: Seq[String] = Seq(
    "font-size:1pc,fill:#000088,stroke:black,stroke-width:6,border:solid,color:white,margin-top:36px",
    "font-size:1pc,fill:#2222AA,stroke:black,stroke-width:5,border:solid,color:white,margin-top:36px",
    "font-size:1pc,fill:#4444CC,stroke:black,stroke-width:4,border:solid,color:white,margin-top:36px",
    "font-size:1pc,fill:#6666EE,stroke:black,stroke-width:3,border:solid,color:black,margin-top:36px",
    "font-size:1pc,fill:#8888FF,stroke:black,stroke-width:2,border:solid,color:black,margin-top:36px",
    "font-size:1pc,fill:#AAAAFF,stroke:black,stroke-width:1,border:solid,color:black,margin-top:36px"
  )
}

class MermaidDiagramsPlugin {
  import MermaidDiagramsPlugin.*

  def getTechnology(definition: Definition): String = {
    val maybeStrings: Option[Seq[String]] = definition match {
      case d: Domain => d.getOptionValue[DomainTechnologyOption]
          .map(list => list.map(_.s))
      case c: Context => c.getOptionValue[ContextTechnologyOption]
          .map(list => list.map(_.s))
      case e: Entity => e.getOptionValue[EntityTechnologyOption]
          .map(list => list.map(_.s))
      case p: Projector => p.getOptionValue[ProjectorTechnologyOption]
          .map(list => list.map(_.s))
      case _ => Option.empty[Seq[String]]
    }
    maybeStrings.map(_.mkString(", ")).getOrElse("Arbitrary Technology")
  }

  def openBox(definition: AST.Definition, level: Int = 0): String = {
    val contents: Seq[Definition] = {
      definition match {
        case r: RootContainer => r.contents
        case d: Domain        => d.domains ++ d.includes
        case i: Include[Definition] @unchecked => i.contents
            .filter(_.isInstanceOf[Domain])
        case _ => Seq.empty[Definition]
      }
    }
    val mid = contents.foldLeft("") { case (s, c) => s + openBox(c, level + 1) }
    if !definition.isImplicit then {
      val technology = getTechnology(definition)
      val name = definition.id.value
      val head = "  ".repeat(level) +
        s"subgraph $name [\"$name<br/><small>${definition.briefValue}<br/>($technology)</small>\"]\n"
      head + mid + "  ".repeat(level) + "end\n" + "  ".repeat(level) +
        s"style $name ${containerStyles(level)}\n"
    } else { mid }
  }

  // def traverseDomainsAndContexts

  def makeRootOverview(
    root: AST.RootContainer
  ): String = {
    val sb = new mutable.StringBuilder()
    sb.append("flowchart TB\n")
    sb.append(openBox(root))
    sb.toString()
  }
  /*
  graph TB
    linkStyle default fill:#ffffff

  1["<div style='font-weight: bold'>User</div><div style='font-size: 70%; margin-top: 0px'>[Person]</div><div " +
    "style='font-size: 80%; margin-top:10px'>A user of my software system.</div>"]
  style 1 fill:#08427b,stroke:#052e56,color:#ffffff
  2["<div style='font-weight: bold'>Software System</div><div style='font-size: 70%; margin-top: 0px'>[Software " +
    "System]</div><div style='font-size: 80%; margin-top:10px'>My software system.</div>"]
  style 2 fill:#1168bd,stroke:#0b4884,color:#ffffff

   */
}
