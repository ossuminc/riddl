package com.reactific.riddl.generator.d3.TableOfContentsTest

import com.reactific.riddl.generator.d3.TableOfContents
import com.reactific.riddl.language.{CommonOptions, ValidatingTest}

import java.net.URL
import scala.io.{Codec, Source}

class TableOfContentsTest extends ValidatingTest {

  "TableOfContents" should {
    "build correct data hierarchy" in {
      val input = """
                    |domain a {
                    | context b {
                    |  entity c { ??? }
                    |  entity d { ??? }
                    | }
                    | context e {
                    |  entity f { ??? }
                    | }
                    |}""".stripMargin
      parseAndValidate(
        input,
        "TableOfContents.build-correct-data-hierarchy",
        CommonOptions(
          showWarnings = false,
          showMissingWarnings = false,
          showStyleWarnings = false
        )
      ) { case (root, messages) =>
        messages.filter(m => m.kind.isError || m.kind.isSevereError) mustBe empty
        val baseURL = new URL("https://example.com/")
        val toc = TableOfContents(baseURL, root)
        val data = toc.makeData
        try { ujson.validate(data) }
        catch { case x: Exception => fail(x) }

        val source = Source.fromFile("d3-generator/src/test/input/toctest.txt")(Codec.UTF8)
        val expected = source.mkString
        source.close()
        data.toString() + "\n" mustBe expected
      }
    }
  }
}
