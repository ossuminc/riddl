/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.PassOutput

/** The output of the Validation Pass */
case class ValidationOutput(
  messages: Messages.Messages = Messages.empty,
  inlets: Seq[Inlet] = Seq.empty[Inlet],
  outlets: Seq[Outlet] = Seq.empty[Outlet],
  connectors: Seq[Connector] = Seq.empty[Connector],
  streamlets: Seq[Streamlet] = Seq.empty[Streamlet],
  processors: Seq[Processor[?]] = Seq.empty[Processor[?]]
) extends PassOutput
