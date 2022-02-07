package com.yoppworks.ossum.riddl.generator.d3.TableOfContentsTest

import com.yoppworks.ossum.riddl.language.Validation.ValidatingOptions
import com.yoppworks.ossum.riddl.language.{ParsingOptions, ValidatingTest}
import com.yoppworks.ossume.riddl.generator.d3.TableOfContents
// import ujson.{Arr, Obj}

import java.net.URL

class TableOfContentsTest extends ValidatingTest {

  "TableOfContents" should {
    "build correct data hierarchy" in {
      val input =
        """
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
        ValidatingOptions(
          parsingOptions = ParsingOptions(showTimes = true),
          showWarnings = false,
          showMissingWarnings = false,
          showStyleWarnings = false
        )
      ) {
        case (root, messages) =>
          messages.filter(m => m.kind.isError || m.kind.isSevereError) mustBe empty
          val baseURL = new URL("https://example.com/")
          val toc = TableOfContents(baseURL, root)
          val data = toc.makeData
          try {
            ujson.validate(data)
          } catch {
            case x: Exception =>
              fail(x)
          }

          val expected =
            """[{"name":"Domain:a","link":"https://example.com/a","brief":"",
              |"children":[{"name":"Context:b","link":"https://example.com/a/b","brief":"",
              |"children":[{"name":"Entity:c","link":"https://example.com/a/b/c","brief":"",
              |"children":[{"name":"Entity:d","link":"https://example.com/a/b/d","brief":"",
              |"children":[{"name":"Context:e","link":"https://example.com/a/e","brief":"",
              |"children":[{"name":"Entity:f","link":"https://example.com/a/e/f","brief":"",
              |"children":[]}]}]}]}]}]}]""".stripMargin
          data.toString() mustBe expected
      }
    }
  }
}
