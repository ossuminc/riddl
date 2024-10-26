package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.{StringLogger,CommonOptions}
import com.ossuminc.riddl.utils.{pc, ec}

class RunCommandsOnExamplesTest extends RunCommandOnExamplesTest(shouldDelete = false) {

  "RunCommandsOnExamplesTest" should {
    "handle from as in IDEA Plugin" in {
      pc.setOptions(CommonOptions(noANSIMessages = true))
      pc.setLog(StringLogger(withHighlighting = false))
      runTestWithArgs("ReactiveBBQ", Array("from", "ReactiveBBQ.conf"))
    }
  }
}
