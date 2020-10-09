package com.yoppworks.ossum.riddl.translator.graph

import java.lang.reflect.InvocationTargetException

import com.yoppworks.ossum.riddl.language.AST._

case class NodeConfig(
  shape: String = "ellipse",
  color: String = "black",
  fillColor: String = "white",
  style: String = "filled",
  fontSize: Double = 12.0,
  url: Option[String] = None,
  layer: Option[String] = None) {

  def format: Seq[(String, String)] = {
    Seq(
      "shape" -> shape.toString,
      "color" -> color,
      "fillcolor" -> fillColor,
      "style" -> style,
      "fontsize" -> fontSize.toString
    ) ++ url.map("url" -> _) ++ url.map("layer" -> _)
  }
}

case class GraphASTConfig(
  root: NodeConfig = NodeConfig(),
  domain: NodeConfig = NodeConfig(fillColor = "aquamarine"),
  context: NodeConfig = NodeConfig(fillColor = "yellow"),
  entity: NodeConfig = NodeConfig(fillColor = "green"))

/** Provides Graphing of AST nodes via GraphVizAPI */
case class GraphAST(
  node: Definition,
  config: GraphASTConfig) {

  private final val bufferCapacity: Int = 1024

  import com.yoppworks.ossum.riddl.translator.graph.GraphVizAPI._

  private final def nameOf(definition: Definition): String = {
    s""""${definition.id.value}(${definition.id.loc})""""
  }

  def drawRoot(root: RootContainer): StringBuilder = {
    val buffer: StringBuilder = new StringBuilder(bufferCapacity)
    buffer.digraph(nameOf(root)) { sb =>
      sb.expand[Container](root.contents) { (sb, container) =>
        container match {
          case domain: Domain => sb
              .append(drawDomain(domain).mkString.indent(2))
          case _ => sb
        }
      }
    }
  }

  def drawDomain(domain: Domain): StringBuilder = {
    val domainSB = new StringBuilder
    domainSB.cluster(domain.id.value) { sb =>
      sb.node(nameOf(domain), config.domain.format)
      sb.expand[Context](domain.contexts) { (sb, context) =>
        sb.append(drawContext(context).mkString.indent(2))
        sb.relation(nameOf(domain), nameOf(context))
      }
    }
  }

  def drawContext(context: Context): StringBuilder = {
    val sb = new StringBuilder
    sb.node(nameOf(context), config.context.format)
    context.entities.foldLeft(sb) { (sb, entity) =>
      sb.append(drawEntity(entity).mkString.indent(2))
      sb.relation(nameOf(context), nameOf(entity))
    }
  }

  def drawEntity(entity: Entity): StringBuilder = {
    val sb = new StringBuilder
    sb.subgraph(nameOf(entity)) { sb =>
      sb.node(nameOf(entity), config.entity.format)
    }
  }
}
