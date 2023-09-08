/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.passes.PassOutput

/** The output of the Validation Pass */
case class ValidationOutput(
  messages: Messages.Messages = Messages.empty,
  inlets: Seq[Inlet] = Seq.empty[Inlet],
  outlets: Seq[Outlet] = Seq.empty[Outlet],
  connectors: Seq[Connector] = Seq.empty[Connector],
  streamlets: Seq[Streamlet] = Seq.empty[Streamlet],
  sends: Map[SendStatement, Seq[Definition]] = Map.empty
) extends PassOutput
