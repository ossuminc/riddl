/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.{CommonOptions, StringLogger, pc}

class RunCommandsOnExamplesTest extends RunCommandOnExamplesTest(shouldDelete = false) {

  "RunCommandsOnExamplesTest" should {
    "handle from as in IDEA Plugin" in {
      pc.withOptions(CommonOptions(noANSIMessages = true)) { _ =>
        pc.withLogger(StringLogger()) { _ =>
          runTestWithArgs("ReactiveBBQ", Array("from", "ReactiveBBQ.conf"))
        }
      }
    }
  }
}
