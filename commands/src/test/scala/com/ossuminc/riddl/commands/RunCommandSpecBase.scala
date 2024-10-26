/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.{PlatformContext, JVMPlatformContext}
import org.scalatest.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A base class for specs that just want to run a command */
abstract class RunCommandSpecBase extends AnyWordSpec with Matchers {

  given io: PlatformContext = JVMPlatformContext()
  def runWith(
    commandArgs: Seq[String]
  ): Assertion = { Commands.runMain(commandArgs.toArray) must be(0) }
}
