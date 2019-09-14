package com.yoppworks.ossum.idddl.parse

import com.yoppworks.ossum.idddl.parse.Node.Type
import fastparse.Parsed
import fastparse.Parsed.Success
import fastparse.Parsed.Failure
import org.scalatest.MustMatchers
import org.scalatest.WordSpec

/** Unit Tests For ParserTest */
class ParserTest extends WordSpec with MustMatchers {

  "Parser" should {
    "allow type definitions" in {
      val input = "type fooBoo = String"
      fastparse.parse(input, Parser.parseTypeDef(_), verboseFailures = true) match {
        case Success(content, index) ⇒
          content mustBe ("fooBoo" → Node.String)
          index mustBe input.length
          succeed
        case Failure(label, index, extra) ⇒
          fail(s"Parse failed at position $index, $extra")
      }
    }
  }
}
