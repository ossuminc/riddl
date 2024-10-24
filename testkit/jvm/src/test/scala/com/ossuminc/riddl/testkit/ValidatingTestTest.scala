package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.passes.validate
import com.ossuminc.riddl.language.{pc, ec}


class ValidatingTestTest extends ValidatingTest {
  val delegate = new validate.SharedValidationTest
  delegate.execute()
}
