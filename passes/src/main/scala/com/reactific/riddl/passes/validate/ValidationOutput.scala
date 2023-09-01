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
  messages: Messages.Messages,
  inlets: Seq[Inlet],
  outlets: Seq[Outlet],
  connectors: Seq[Connector],
  streamlets: Seq[Streamlet],
  sends: Map[SendStatement, Seq[Definition]]
) extends PassOutput
