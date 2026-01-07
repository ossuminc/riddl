/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.commands.Commands
import com.ossuminc.riddl.utils.pc

/** RIDDL Main Program */
object RIDDLC {

  final def main(args: Array[String]): Unit = {
    val resultCode = Commands.runMain(args)
    if resultCode != 0 then { System.exit(resultCode) }
  }
}
