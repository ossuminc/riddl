package com.reactific.riddl.language.passes.validate

import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.passes.PassOutput

/** Unit Tests For ValidationOutput */
case class ValidationOutput (
  messages: Messages.Messages,
  inlets: Seq[Inlet],
  outlets: Seq[Outlet],
  connectors: Seq[Connector],
  streamlets: Seq[Streamlet],
  sends: Map[SendAction, Seq[Definition]]
) extends PassOutput
