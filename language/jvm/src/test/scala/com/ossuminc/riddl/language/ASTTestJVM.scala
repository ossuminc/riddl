package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.utils.TestingBasis
import wvlet.airframe.ulid.ULID

class ASTTestJVM extends TestingBasis {

  "RiddlValue" must {
    "reject incorrectly typed values in attachments" in {
      val container = SimpleContainer(Contents.empty)
      container.put("this", "that")
      intercept[ClassCastException] {
        val result: Option[Double] = container.get[Double]("this")
        val timesTwo = result.get * 2.0
        println(timesTwo)
      }
    }
  }
  "Include" should {
    "identify as root container, etc" in {
      import com.ossuminc.riddl.utils.URL
      val incl = Include(At.empty, URL.empty, Seq.empty)
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
    val lines: Seq[String] = ud.lines.map(_.s)
    val head = lines.head
    head must include("sbt-ossuminc")
  }
}
