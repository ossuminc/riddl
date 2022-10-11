/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.testkit

import com.reactific.riddl.commands.CommandPlugin
import org.scalatest.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

/** A base class for specs that just want to run a command */
abstract class RunCommandSpecBase extends AnyWordSpec with Matchers {

  def runWith(
    commandArgs: Seq[String]
  ): Assertion = { CommandPlugin.runMain(commandArgs.toArray) must be(0) }
}
