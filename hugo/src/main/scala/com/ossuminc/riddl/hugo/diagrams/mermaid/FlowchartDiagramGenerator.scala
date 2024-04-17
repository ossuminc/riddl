package com.ossuminc.riddl.hugo.diagrams.mermaid

import com.ossuminc.riddl.language.AST.{Definition, NamedValue, Processor}

/** Flowchart generator abstraction * Example output: * {{{ * --- * title: "Context Diagram For Domain Foo" * init: *
  * theme: dark * flowchartConfig: * defaultRenderer: dagre * width: 100% * --- * flowchart LR * classDef default
  * fill:#666,stroke:black,stroke-width:3px,color:white; * classDef Aclass
  * font-size:1pc,fill:orange,stroke:black,stroke-width:3; * classDef Bclass
  * font-size:1pc,fill:#222,stroke:black,stroke-width:3; * classDef Cclass
  * font-size:1pc,fill:blue,stroke:black,stroke-width:3; * classDef Dclass
  * font-size:1pc,fill:goldenrod,stroke:black,stroke-width:3; * classDef Eclass
  * font-size:1pc,fill:green,stroke:black,stroke-width:3; * classDef Fclass
  * font-size:1pc,fill:chocolate,stroke:black,stroke-width:3; * classDef Gclass
  * font-size:1pc,fill:purple,stroke:black,stroke-width:3 * subgraph &nbsp; * A((Christmas))-->|Relates To| B((Go
  * shopping)) * A -->|Relates To| C((OtherThing)) * A -->|Relates To| G((fa:fa-order Another<br/>Thing)) * A-->|Has An
  * Extensive</br> Relationship That is</br>Really long|F * A -->|Relates To|
  * D((fa:fa-laptop<br/>&nbsp;&nbsp;Laptop&nbsp;&nbsp)) * A -->|Relates To| E((fa:fa-phone<br/>iPhone)) * A -->|Relates
  * To| F((fa:fa-car<br/>Automobile)) * A -->|Relates To| G * end * * class A Aclass * class B Bclass * class C Cclass *
  * class D Dclass * class E Eclass * class F Fclass * class G Gclass * }}} *
  */
trait FlowchartDiagramGenerator(val title: String, direction: String = "LR") extends MermaidDiagramGenerator {

  frontMatter()
  addLine(s"flowchart $direction")
  incr // indent the content from subclass
  def kind: String = "flowchart"

  def frontMatterItems: Map[String, String] = Map(
    "defaultRenderer" -> "dagre",
    "width" -> "100%",
    "useMaxWidth" -> "true",
    "securityLevel" -> "loose"
  )

  protected def emitDefaultClassDef(): Unit = {
    addLine("classDef default fill:#666,stroke:black,stroke-width:3px,color:white;")
  }

  protected def emitClassDefs(nodes: Seq[Definition]): Unit = {
    for {
      node <- nodes
    } do {
      val css: String = getCssFor(node)
      if css.nonEmpty then addLine(s"classDef ${node.id.value}_class $css; ")
      else addLine(s"classDef ${node.id.value}_class color:white,stroke-width:3px;")
      end if
    }
  }

  protected def emitClassAssignments(nodes: Seq[Definition]): Unit = {
    for {
      node <- nodes
    } do {
      addLine(s"class ${node.id.value} ${node.id.value}_class")
    }
  }

  protected def emitGraph(
    startNodeName: String,
    nodes: Seq[Definition],
    relationships: Seq[(Definition, String)]
  ): Unit = {
    emitNodes(nodes)
    emitRelationships(startNodeName, relationships)
  }

  protected def emitSubgraph(
    containingDefinition: Definition,
    startNodeName: String,
    nodes: Seq[Definition],
    relationships: Seq[(Definition, String)],
    direction: String = "TB"
  ): Unit = {
    val name = containingDefinition.identify
    addLine(s"subgraph '${containingDefinition.identify}'")
    incr
    if direction.nonEmpty then addLine(s"direction $direction")
    emitNodes(nodes)
    emitRelationships(startNodeName, relationships)
    decr
    addLine("end")
  }

  protected def emitNodes(nodes: Seq[Definition]): Unit = {
    for {
      processor <- nodes
    } {
      val name = makeNode(processor)
      addLine(name)
    }
  }

  protected def makeNode(definition: Definition): String = {
    val iconName = getIconFor(definition)
    val faicon = if iconName.nonEmpty then "fa:" + iconName + "<br/>" else ""
    val defName: String = definition.id.value
    val displayName: String = definition.identify
    val numWords = displayName.count(_.isSpaceChar) + 1
    val spacedName =
      if numWords > 4 then
        var i = 0
        for {
          word <- displayName.split(' ')
        } yield {
          i = i + 1
          if i == numWords then word
          else if i % 3 == 0 then word + "<br/"
          else word + " "
        }.mkString
      else if displayName.length > 8 then displayName
      else
        val numSpaces = (8 - displayName.length) / 2
        val fix = "&nbsp;".repeat(numSpaces)
        fix + displayName + fix
      end if
    s"$defName(($faicon$spacedName))"
  }

  protected def emitRelationships(mainNodeName: String, relationships: Seq[(Definition, String)]): Unit = {
    for {
      (definition, relationship) <- relationships
    } {
      addIndent().append(mainNodeName).append("-->|").append(relationship).append("|").append(makeNode(definition)).nl
    }
  }

}
