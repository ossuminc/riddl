package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.language.AST.Context
import com.ossuminc.riddl.diagrams.ContextDiagramData
import com.ossuminc.riddl.diagrams.mermaid.FlowchartDiagramGenerator

/** Context Diagram generator using a DataFlow Diagram from Mermaid
  *
  * Example output:
  * {{{
  * ---
  * title: "Context Diagram For Domain Foo"
  * init:
  *     theme: dark
  * flowchartConfig:
  *     defaultRenderer: dagre
  *     width: 100%
  * ---
  * flowchart LR
  * classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
  * classDef Aclass font-size:1pc,fill:orange,stroke:black,stroke-width:3;
  * classDef Bclass font-size:1pc,fill:#222,stroke:black,stroke-width:3;
  * classDef Cclass font-size:1pc,fill:blue,stroke:black,stroke-width:3;
  * classDef Dclass font-size:1pc,fill:goldenrod,stroke:black,stroke-width:3;
  * classDef Eclass font-size:1pc,fill:green,stroke:black,stroke-width:3;
  * classDef Fclass font-size:1pc,fill:chocolate,stroke:black,stroke-width:3;
  * classDef Gclass font-size:1pc,fill:purple,stroke:black,stroke-width:3
  * subgraph &nbsp;
  * A((Christmas))-->|Relates To| B((Go shopping))
  * A -->|Relates To| C((OtherThing))
  * A -->|Relates To| G((fa:fa-order Another<br/>Thing))
  * A-->|Has An Extensive</br> Relationship That is</br>Really long|F
  * A -->|Relates To| D((fa:fa-laptop<br/>&nbsp;&nbsp;Laptop&nbsp;&nbsp))
  * A -->|Relates To| E((fa:fa-phone<br/>iPhone))
  * A -->|Relates To| F((fa:fa-car<br/>Automobile))
  * A -->|Relates To| G
  * end
  *
  * class A Aclass
  * class B Bclass
  * class C Cclass
  * class D Dclass
  * class E Eclass
  * class F Fclass
  * class G Gclass
  * }}}
  *
  * @param context
  *   The context relevant to this diagram
  * @param data
  *   The data collected by the ((Diagrams Pass)) for this diagram.
  */

case class ContextDiagram(context: Context, data: ContextDiagramData)
    extends FlowchartDiagramGenerator(s"Context Map For ${context.identify}", "TB") {

  private val relatedContexts = data.relationships.map(_._1).distinct

  emitClassDefs()
  emitDomainSubgraph()

  private def emitClassDefs(): Unit = {
    addLine("classDef default fill:#666,stroke:black,stroke-width:3px,color:white;")
    for {
      context <- relatedContexts
      css = getCssFor(context) if css.nonEmpty
    } do {
      addLine(s"classDef ${context.id.value}_class $css; ")
    }
  }

  private def emitDomainSubgraph(): Unit = {
    addLine(s"subgraph ${data.domain.identify}")
    incr
    makeRelationships()
    decr
    addLine("end")
    makeClassAssignments()
  }

  private def makeRelationships(): Unit = {
    for {
      (context,relationship) <- data.relationships
    } {
      val iconName = getIconFor(context)
      val faicon = if iconName.nonEmpty then "fa:" + iconName + "<br/>" else ""
      val contextName = context.id.value
      val numSpaces = if contextName.length < 8 then (8-contextName.length)/2 else 0
      val fix = "&nbsp;".repeat(numSpaces)
      val name = fix + contextName + fix 
      val fullName = s"(($faicon$name))"
      
    }
  }

  private def makeClassAssignments(): Unit = {
    for {
      context <- relatedContexts
      css = getCssFor(context) if css.nonEmpty
    } do {
      addLine(s"class ${context.id.value} ${context.id.value}_class ")
    }
  }
}
