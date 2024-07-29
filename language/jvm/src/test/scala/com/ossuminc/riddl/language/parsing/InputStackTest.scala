package com.ossuminc.riddl.language.parsing

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.net.URL
import scala.collection.mutable

class InputStackTest extends AnyWordSpec with Matchers {

  "InputStack" should {
    "stack the input" in {
      val i_top = RiddlParserInput("top", "top")
      val i_middle = RiddlParserInput("middle", "middle")
      val i_bottom = RiddlParserInput("bottom", "bottom")
      val is = InputStack()
      is.push(i_top)
      is.push(i_middle)
      is.push(i_bottom)
      is.current mustBe(i_bottom)
      is.sourceNames mustBe( Seq("bottom", "middle", "top"))
      is.pop mustBe(i_bottom)
      is.pop mustBe(i_middle)
      is.pop mustBe(i_top)
      is.isEmpty mustBe(true)
    }
  }
}
