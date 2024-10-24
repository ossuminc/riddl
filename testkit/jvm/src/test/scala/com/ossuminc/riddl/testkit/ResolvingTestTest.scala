package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.passes
import com.ossuminc.riddl.language.{pc, ec}

class ResolvingTestTest extends ResolvingTest {
  val delegate = passes.resolve.SharedPathResolutionPassTest()
  delegate.execute()
}
