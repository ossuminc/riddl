package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.passes.validate.ValidationTest

class ValidatingTestTest extends ValidatingTest {
  val delegate = new ValidationTest
  delegate.execute()
}
