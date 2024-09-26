package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.language.parsing
import org.scalatest.Suite

class ParsingTestTest extends ParsingTest {
  val delegate: Suite = new parsing.ParsingTestTest
  delegate.execute()
}
