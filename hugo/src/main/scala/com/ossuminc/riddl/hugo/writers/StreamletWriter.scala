/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*

trait StreamletWriter { this: MarkdownWriter =>

  def emitConnector(conn: Connector, parents: Parents): Unit = {
    leafHead(conn, weight = 20)
    emitDefDoc(conn, parents)
    if conn.from.nonEmpty && conn.to.nonEmpty then {
      p(s"from ${conn.from.format} to ${conn.to.format}")

    }
  }

  def emitStreamlet(streamlet: Streamlet, parents: Parents): Unit = {
    containerHead(streamlet)
    emitProcessorDetails(streamlet, parents)
    emitInlets(streamlet.inlets, streamlet +: parents)
    emitOutlets(streamlet.outlets, streamlet +: parents)
    // TODO: emit a diagram of the streamlet's data flow
  }

}
