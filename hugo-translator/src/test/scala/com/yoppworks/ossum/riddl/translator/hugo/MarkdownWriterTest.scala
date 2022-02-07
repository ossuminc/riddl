package com.yoppworks.ossum.riddl.translator.hugo

import com.yoppworks.ossum.riddl.language.ParsingTest

import java.nio.file.Path

class MarkdownWriterTest extends ParsingTest {

  "MarkdownWriterTest" must {
    "emit a domain" in {
      val paths = Seq[String]("hugo-translator", "target", "test-output", "container.md")
      val output = Path.of(paths.head, paths.tail: _*)
      val mkd = MarkdownWriter(output)
      val input =
        """domain TestDomain {
          |  author is { name="Reid Spencer" email="reid.spencer@yoppworks.com" }
          |  type MyString is String described as "Just a renamed string"
          |} brief "Just For Testing" described as {
          ||A test domain for ensuring that documentation for domains is
          ||generated sufficiently.
          |}
          |""".stripMargin
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          fail("Parse Failed:\n" + errors.map(_.format).mkString("\n"))
        case Right(root) =>
          root.domains mustNot be(empty)
          val domain = root.domains.head
          mkd.emitDomain(domain, paths.dropRight(1))
          val emitted = mkd.toString
          val expected =
            """---
              |title: "TestDomain"
              |weight: 10
              |description: "Just For Testing"
              |geekdocAnchor: true
              |geekdocCollapseSection: true
              |---
              |# Domain 'TestDomain'
              |
              |## Author
              |* _Name_: Reid Spencer
              |* _Email_: reid.spencer@yoppworks.com
              |
              |## Briefly
              |Just For Testing
              |_Path_: hugo-translator.target.test-output.TestDomain
              |_Defined At_: default(1:1)
              |
              |## Details
              |A test domain for ensuring that documentation for domains is
              |generated sufficiently.
              |
              |## Types
              |* _MyString_: String
              |  Just a renamed string
              |""".stripMargin
          emitted mustBe expected
      }
    }
  }
}
