package com.reactific.riddl.language.passes.validation

import com.reactific.riddl.language.{AST, CommonOptions, Messages}
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.passes.PassOutput
import com.reactific.riddl.language.passes.resolution.ResolutionOutput

/** Unit Tests For ValidationOutput */
case class ValidationOutput (
  root: AST.RootContainer,
  commonOptions: CommonOptions,
  messages: Messages.Messages,
  resolution: ResolutionOutput,
  inlets: Seq[Inlet],
  outlets: Seq[Outlet],
  connectors: Seq[Connector],
  streamlets: Seq[Streamlet],
  sends: Map[SendAction, Seq[Definition]]
) extends PassOutput
