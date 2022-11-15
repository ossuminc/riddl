/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.prettify
import com.reactific.riddl.testkit.RunCommandSpecBase

class PrettifyCommandTest extends RunCommandSpecBase {

  "PrettifyCommand" must {
    "parse a simple command" in {
      runWith(Seq("prettify", "testkit/src/test/input/everything.riddl"))
    }
  }
}
