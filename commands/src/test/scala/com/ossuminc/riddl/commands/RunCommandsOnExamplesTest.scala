package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.StringLogger
import com.ossuminc.riddl.language.CommonOptions

class RunCommandsOnExamplesTest extends RunCommandOnExamplesTest(shouldDelete = false) {

  "RunCommandsOnExamplesTest" should {
    "handle from as in IDEA Plugin" in {
      runTestWithArgs("ReactiveBBQ", Array("from", "ReactiveBBQ.conf"),
        StringLogger(withHighlighting = false),
        CommonOptions(noANSIMessages = true)
      )
    }

  }
}
