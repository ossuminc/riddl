package com.yoppworks.ossum.riddl.generator.d3.TableOfContentsTest

import com.yoppworks.ossum.riddl.language.ValidatingTest
import com.yoppworks.ossum.riddl.language.Validation.ValidationOptions
import com.yoppworks.ossume.riddl.generator.d3.TableOfContents
import ujson.{Arr, Obj, validate}

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
        ValidationOptions(
          showTimes = true,
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
            validate(data)
          } catch {
            case x: Exception =>
              fail(x)
          }
          data mustBe
            Arr(
              Obj("name" -> "Domain:a", "link" -> "https://example.com/a/",
                "children" -> Arr(
                  Obj("name" -> "Context:b", "link" -> "https://example.com/a/b/",
                    "children" -> Arr(
                      Obj("name" -> "Entity:c", "link" -> "https://example.com/a/b/c",
                        "children" -> Arr()),
                      Obj("name" -> "Entity:d", "link" -> "https://example.com/a/b/d",
                        "children" -> Arr())
                    )
                  ),
                  Obj("name" -> "Context:e", "link" -> "https://example.com/a/e/",
                    "children" -> Arr(
                      Obj("name" -> "Entity:f", "link" -> "https://example.com/a/e/f",
                        "children" -> Arr()),
                    )
                  )
                )
              )
            )
      }
    }
  }
}
