package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.passes.validate

class ValidatingTestTest extends ValidatingTest {
  val delegate = new validate.ValidationTest
  delegate.execute()
}
