package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.must.Matchers
import wvlet.airframe.ulid.ULID

class ASTAccessTest extends AsyncFunSpec with Matchers:
  describe("RiddlValue") {
    it("must allow put/get/has of ULID values") {
      val node = SimpleContainer(Contents.empty)
      val ulid: ULID = ULID.newULID
      node.put[ULID]("ulid", ulid)
      node.has("ulid") must be(true)
      node.get[ULID]("ulid") match
        case Some(ulid2) =>
          ulid2 must be(ulid)
        case None => fail("Unable to retrieve ulid")
      end match
    }
    it("must accept storage of arbitrary named values in a map") {
      val container = SimpleContainer(Contents.empty)
      container.put("this", "that")
      val result = container.get("this")
      result must be(Some("that"))
    }
  }
end ASTAccessTest
