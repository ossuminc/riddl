package com.ossuminc.riddl.diagrams.mermaid

trait FlowchartDiagramGenerator(val title: String, direction: String = "LR") extends MermaidDiagramGenerator {

  frontMatter()
  addLine(s"flowchart $direction")

  def kind: String = "flowchartConfig"

  def frontMatterItems: Map[String, String] = Map(
    "defaultRenderer" -> "dagre",
    "width" -> "100%"
  )
}
