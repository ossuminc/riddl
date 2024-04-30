/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.command.CommandPlugin

/** RIDDL Main Program */
object RIDDLC {

  final def main(args: Array[String]): Unit = {
    val resultCode = CommandPlugin.runMain(args)
    if resultCode != 0 then { System.exit(resultCode) }
  }
}
