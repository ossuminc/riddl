package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.utils.{pc, ec}
import com.ossuminc.riddl.passes.resolve.ResolvingTest

class ResolvingTestTest extends ResolvingTest {
  val delegate = com.ossuminc.riddl.passes.resolve.SharedPathResolutionPassTest()
  delegate.execute()
}
