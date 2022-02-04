package com.yoppworks.ossum.riddl.translator.hugo

import com.yoppworks.ossum.riddl.language.ParsingTest

import java.nio.file.Path

class MarkdownWriterTest extends ParsingTest {

  "MarkdownWriterTest" must {
    "emit a container" in {
      val paths = Seq[String]("hugo-translator", "target", "test-output", "container.md")
      val output = Path.of(paths.head, paths.tail: _*)
      val mkd = MarkdownWriter(output)
      val input =
        """domain TestDomain {
          |  author is { name="Reid Spencer" email="reid.spencer@yoppworks.com" }
          |  type MyString is String
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
          mkd.emitContainer(domain, paths.dropRight(1))
          val emitted = mkd.toString
          val expected =
            """---
              |title: "TestDomain"
              |weight: 0
              |description: "Just For Testing"
              |---
              |<p style="text-align: center;">
              |# TestDomain
              |</p>
              |## Briefly
              |Just For Testing
              |## Contents
              |* [Type 'MyString'](hugo-translator/target/test-output/MyString)
              |## Description
              |A test domain for ensuring that documentation for domains is
              |generated sufficiently.
              |""".stripMargin
          emitted mustBe expected
      }
    }
  }
}
