package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.passes

class ResolvingTestTest extends ResolvingTest {
  val delegate = passes.resolve.NoJVMPathResolutionPassTest()
  delegate.execute()
}
