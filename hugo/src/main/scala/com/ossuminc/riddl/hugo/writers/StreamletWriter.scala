package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.symbols.Symbols.Parents

trait StreamletWriter { this: MarkdownWriter =>

  def emitConnector(conn: Connector, parents: Parents): Unit = {
    leafHead(conn, weight = 20)
    emitDefDoc(conn, parents)
    if conn.from.nonEmpty && conn.to.nonEmpty then {
      p(s"from ${conn.from.format} to ${conn.to.format}")

    }
    emitUsage(conn)
  }

  def emitStreamlet(streamlet: Streamlet, parents: Parents): Unit = {
    leafHead(streamlet, weight = 30)
    emitProcessorDetails(streamlet, parents)
    emitInlets(streamlet, streamlet +: parents)
    emitOutlets(streamlet, streamlet +: parents)
    // TODO: emit a diagram of the streamlet's data flow
  }
}
