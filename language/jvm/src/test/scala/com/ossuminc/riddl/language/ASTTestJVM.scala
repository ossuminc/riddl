package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.utils.TestingBasis
import wvlet.airframe.ulid.ULID

class ASTTestJVM extends TestingBasis {

  "RiddlValue" must {
    "allow attachments to be added programatically" in {
      val container = Entity(At.empty, Identifier.empty)
      val a = StringAttachment(At.empty, Identifier(At.empty,"foo"), "application/json", LiteralString(At.empty,"{}"))
      container.descriptives += a
      container.descriptives.filter[StringAttachment] match
        case Seq(value) if value == a => succeed
        case _  => fail("No go")
    }
  }
  "Include" should {
    "identify as root container, etc" in {
      import com.ossuminc.riddl.utils.URL
      val incl = Include(At.empty, URL.empty, Contents.empty)
      incl.isRootContainer mustBe true
      incl.loc mustBe At.empty
      incl.format mustBe "include \"\""
    }
  }
  "have useful URLDescription" in {
    import com.ossuminc.riddl.utils.URL
    val url_text = "https://raw.githubusercontent.com/ossuminc/riddl/main/project/plugins.sbt"
    val url: URL = URL(url_text)
    val ud = URLDescription(At(), url)
    ud.loc.isEmpty mustBe true
    ud.url.toExternalForm must be(url_text)
    ud.format must be(url.toExternalForm)
    val lines: scala.collection.Seq[String] = ud.lines.map(_.s)
    val head = lines.head
    head must include("sbt-ossuminc")
  }
}
