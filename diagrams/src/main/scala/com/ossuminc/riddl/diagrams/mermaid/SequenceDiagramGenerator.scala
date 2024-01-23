package com.ossuminc.riddl.diagrams.mermaid

/** A mermaid diagram generator for making sequence diagrams
  *
  */
trait SequenceDiagramGenerator extends MermaidDiagramGenerator {
  def kind: String = "sequenceDiagram"
  frontMatter()
  addLine("sequenceDiagram").nl.incr.addIndent("autonumber")

}
