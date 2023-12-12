package com.ossuminc.riddl.language.parsing

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

abstract class ParsingTestBase extends AnyWordSpec with Matchers {
  case class StringParser(content: String) extends TopLevelParser(RiddlParserInput(content))
}

