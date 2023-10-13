package com.reactific.riddl.diagrams.mermaid
import com.reactific.riddl.language.AST.{Connector, Context, Definition, Outlet, Inlet, Type}
import com.reactific.riddl.passes.PassesResult

/** Generate a data flow diagram LIke this: flowchart TD A[Christmas] -->|Get money| B(Go shopping) B --> C{Let me
  * think} C -->|One| D[Laptop] C -->|Two| E[iPhone] C -->|Three| F[fa:fa-car Car]
  */
case class DataFlowDiagram(pr: PassesResult) {

  final private val newline: String = System.getProperty("line.separator")
  private val sb: StringBuilder = StringBuilder(1000)
  private val spacesPerIndent = 2

  def indent(str: String, level: Int = 1): Unit = {
    sb.append(" ".indent(level * spacesPerIndent))
    sb.append(str)
    sb.append(newline)
  }

  def participants(connector: Connector): Seq[Definition] =
    if connector.flows.nonEmpty && connector.from.nonEmpty && connector.to.nonEmpty then
      pr.refMap.definitionOf[Type](connector.flows.get, connector) match {
        case Some(typeDef) =>
          pr.refMap.definitionOf[Inlet](connector.to.get, connector) match {
            case Some(toDef) =>
              pr.refMap.definitionOf[Outlet](connector.from.get, connector) match {
                case Some(fromDef) =>
                  val to_users = pr.usage.getUsers(toDef)
                  val from_users = pr.usage.getUsers(fromDef)
                case None => Seq.empty
              }
            case None => Seq.empty
          }
        case None => Seq.empty
      }
    else Seq.empty

  def generate(context: Context): String = {
    sb.append("flowchart LR")
    indent("")
    sb.toString()
  }

}
