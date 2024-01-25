package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.language.AST.{Context, Processor}
import com.ossuminc.riddl.diagrams.ContextDiagramData

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
  makeClassAssignments()

  private def emitClassDefs(): Unit = {
    addLine("classDef default fill:#666,stroke:black,stroke-width:3px,color:white;")
    for {
      ctxt <- context +: relatedContexts
    } do {
      val css: String = getCssFor(ctxt)
      if css.nonEmpty then
        addLine(s"classDef ${ctxt.id.value}_class $css; ")
      else
        addLine(s"classDef ${ctxt.id.value}_class color:white,stroke-width:3px;")
      end if
    }
  }

  private def makeClassAssignments(): Unit = {
    for {
      ctxt <- context +: relatedContexts
    } do {
      addLine(s"class ${ctxt.id.value} ${ctxt.id.value}_class")
    }
  }

  private def emitDomainSubgraph(): Unit = {
    addLine(s"subgraph ${data.domain.identify}")
    incr
    makeNodes()
    makeRelationships()
    decr
    addLine("end")
  }

  private def makeNode(processor: Processor[?,?]): String = {
    val iconName = getIconFor(processor)
    val faicon = if iconName.nonEmpty then "fa:" + iconName + "<br/>" else ""
    val contextName: String = processor.id.value
    val numWords = contextName.count(_.isSpaceChar) + 1
    val spacedName =
      if numWords > 4 then
        var i = 0
        for {
          word <- contextName.split(' ')
        } yield {
          i = i + 1
          if i == numWords then word
          else if i % 3 == 0 then word + "<br/"
          else word + " "
        }.mkString
      else
        if contextName.length > 8 then
          contextName
        else
          val numSpaces = (8 - contextName.length) / 2
          val fix = "&nbsp;".repeat(numSpaces)
          fix + contextName + fix
        end if
      end if
    s"$contextName(($faicon$spacedName))"
  }

  private def makeNodes(): Unit = {
    for {
      aContext <- context +: relatedContexts
    } {
      val name = makeNode(aContext)
      addLine(name)
    }
  }

  private def makeRelationships(): Unit = {
    val mainNodeName = context.id.value
    for {
      (ctxt,relationship) <- data.relationships
    } {
      addIndent().append(mainNodeName).append("-->|").append(relationship).append("|").append(makeNode(ctxt)).nl
    }
  }
}
