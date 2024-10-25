package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.passes.validate.SharedValidationTest
import com.ossuminc.riddl.language.{pc, ec}


class ValidatingTestTest extends ValidatingTest {
  val delegate = new SharedValidationTest
  delegate.execute()
}
