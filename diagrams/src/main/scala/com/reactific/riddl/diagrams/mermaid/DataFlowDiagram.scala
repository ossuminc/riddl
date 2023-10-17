package com.reactific.riddl.diagrams.mermaid
import com.reactific.riddl.language.AST.{Connector, Context, Definition, Inlet, Outlet, Streamlet, Type}
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
  }

  private def makeNodeLabel(definition: Definition): Unit = {
    pr.symbols.parentOf(definition) match {
      case Some(parent) =>
        val name = parent.id.value + "." + definition.id.value
        val id = definition match {
          case _: Outlet    => s"Outlet $name"
          case _: Inlet     => s"Inlet $name"
          case s: Streamlet => s"${s.kind} $name"
          case s: Connector => s"Connector $name"
          case d: Definition => s"${d.kind} $name"
        }
        indent(s"${definition.id.value}[\"$id\"]")
      case _ =>
        indent(s"${definition.id.value}")
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  private[mermaid] def participants(connector: Connector): Seq[Definition] =
    if connector.flows.nonEmpty && connector.from.nonEmpty && connector.to.nonEmpty then
      pr.refMap.definitionOf[Type](connector.flows.get, connector) match {
        case Some(typeDef) =>
          pr.refMap.definitionOf[Inlet](connector.to.get, connector) match {
            case Some(toDef) =>
              pr.refMap.definitionOf[Outlet](connector.from.get, connector) match {
                case Some(fromDef) =>
                  val to_users = pr.usage.getUsers(toDef)
                  val from_users = pr.usage.getUsers(fromDef)
                  (Seq(fromDef, toDef) ++ to_users ++ from_users).distinct
                case None => Seq.empty
              }
            case None => Seq.empty
          }
        case None => Seq.empty
      }
    else Seq.empty

  def generate(context: Context): String = {
    sb.append("flowchart LR")
    val parts = for
      connector <- context.connections
      participants <- this.participants(connector)
    yield
      participants
    for part <- parts.distinct do makeNodeLabel(part)
    sb.result()
  }

}
