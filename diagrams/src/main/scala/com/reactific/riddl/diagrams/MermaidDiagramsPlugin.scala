package com.reactific.riddl.diagrams
import com.reactific.riddl.language.AST
import com.reactific.riddl.language.AST.*

import scala.collection.mutable

object MermaidDiagramsPlugin {
  val containerStyles = Array(
    "fill:#000088,stroke:black,stroke-width:6,border:solid,color:white", // Navy Blue Solid
    "fill:#2222AA,stroke:black,stroke-width:5,border:solid,color:white", // Navy Blue Solid
    "fill:#4444CC,stroke:black,stroke-width:4,border:solid,color:white", // Navy Blue Solid
    "fill:#6666EE,stroke:black,stroke-width:3,border:solid,color:black", // Navy Blue Solid
    "fill:#8888FF,stroke:black,stroke-width:2,border:solid,color:black", // Navy Blue Solid
    "fill:#AAAAFF,stroke:black,stroke-width:1,border:solid,color:black" // Navy Blue Solid
  )
  case class Node(name: String, brief: String, kids: Seq[Node])
}

class MermaidDiagramsPlugin extends DiagramMakerPlugin {
  import MermaidDiagramsPlugin.*

  def getTechnology(definition: Definition): String = {
    val maybeStrings: Option[Seq[String]] = definition match {
      case d: Domain => d.getOptionValue[DomainTechnologyOption]
          .map(list => list.map(_.s))
      case c: Context => c.getOptionValue[ContextTechnologyOption]
          .map(list => list.map(_.s))
      case e: Entity => e.getOptionValue[EntityTechnologyOption]
          .map(list => list.map(_.s))
      case p: Projection => p.getOptionValue[ProjectionTechnologyOption]
          .map(list => list.map(_.s))
      case _ => Option.empty[Seq[String]]
    }
    maybeStrings.map(_.mkString(", ")).getOrElse("Arbitrary Technology")
  }

  def openBox(definition: AST.Definition, level: Int = 0): String = {
    val technology = getTechnology(definition)
    val name = definition.id.value
    val head = "  ".repeat(level) +
      s"subgraph $name [\"$name<br/><small>${definition.briefValue}<br/>($technology)</small>\"]\n"
    val containers: Seq[Definition] = {
      definition match {
        case r: RootContainer => r.contents
        case d: Domain => d.domains ++ d.contexts ++
            d.includes.flatMap(_.contents)
              .filter(x => x.isInstanceOf[Context] || x.isInstanceOf[Domain])
        case c: Context => c.entities ++ c.handlers ++ c.projections ++
            c.processors ++ c.sagas
        case _ => Seq.empty[Definition]
      }
    }
    val mid = containers.foldLeft("") { case (s, c) =>
      s + openBox(c, level + 1)
    }
    head + mid + "  ".repeat(level) + "end\n" + "  ".repeat(level) +
      s"style $name ${containerStyles(level)}\n"
  }

  // def traverseDomainsAndContexts

  override def makeRootOverview(
    root: AST.RootContainer,
    rootName: String
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

  override def makeDomainOverview(
    root: AST.RootContainer,
    domain: AST.Domain
  ): String = ???
  override def makeContextOverview(
    root: AST.RootContainer,
    context: AST.Context
  ): String = ???
  override def makeEntityOverview(
    root: AST.RootContainer,
    entity: AST.Entity
  ): String = ???
  override def makeStateDetail(
    root: AST.RootContainer,
    state: AST.State
  ): String = ???
  override def makeStoryDiagram(
    root: AST.RootContainer,
    story: AST.Story
  ): String = ???
}
