package com.ossuminc.riddl.commands

import com.ossuminc.riddl.commands.pc
import com.ossuminc.riddl.utils.StringLogger
import com.ossuminc.riddl.utils.CommonOptions

class RunCommandsOnExamplesTest extends RunCommandOnExamplesTest(shouldDelete = false) {

  "RunCommandsOnExamplesTest" should {
    "handle from as in IDEA Plugin" in {
      pc.setOptions(CommonOptions(noANSIMessages = true))
      pc.setLog(StringLogger(withHighlighting = false))
      runTestWithArgs("ReactiveBBQ", Array("from", "ReactiveBBQ.conf"))
    }
  }
}
