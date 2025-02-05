/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.PassOutput
import com.ossuminc.riddl.language.Messages

/** The output of the Validation Pass */
case class ValidationOutput(
  root: Root = Root.empty,
  messages: Messages.Messages = Messages.empty,
  inlets: Seq[Inlet] = Seq.empty[Inlet],
  outlets: Seq[Outlet] = Seq.empty[Outlet],
  connectors: Seq[Connector] = Seq.empty[Connector],
  streamlets: Seq[Streamlet] = Seq.empty[Streamlet],
) extends PassOutput
