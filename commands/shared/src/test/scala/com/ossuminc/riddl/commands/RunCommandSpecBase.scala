/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.{AbstractTestingBasis, PlatformContext}
import org.scalatest.*

/** A base class for specs that just want to run a command */
abstract class RunCommandSpecBase(using io: PlatformContext) extends AbstractTestingBasis {

  def runWith(
    commandArgs: Seq[String]
  ): Assertion = { Commands.runMain(commandArgs.toArray) must be(0) }
}
