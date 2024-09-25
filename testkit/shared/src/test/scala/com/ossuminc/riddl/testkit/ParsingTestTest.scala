package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.language.parsing

class ParsingTestTest extends ParsingTest {
  val delegate = new parsing.ParsingTestTest
  delegate.execute()
}
