package com.ossuminc.riddl.testkit

class ResolvingTestTest extends ResolvingTest {
  val delegate = new com.ossuminc.riddl.passes.resolve.PathResolutionPassTest
  delegate.execute()
}
