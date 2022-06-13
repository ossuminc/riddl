package com.reactific.riddl.translator.hugo

import com.reactific.riddl.language.testkit.ParsingTest

import java.io.{PrintWriter, StringWriter}
import java.nio.file.Path

class MarkdownWriterTest extends ParsingTest {

  "MarkdownWriterTest" must {
    "emit a domain" in {
      val paths =
        Seq[String]("hugo-translator", "target", "test-output", "container.md")
      val output = Path.of(paths.head, paths.tail: _*)
      val mkd = MarkdownWriter(output)
      val input =
        """domain TestDomain {
          |  author is { name="Reid Spencer" email="reid@reactific.com" }
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
          root.contents mustNot be(empty)
          val domain = root.contents.head
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
              |* _Email_: reid@reactific.com
              |
              |## Briefly
              |Just For Testing
              |_Path_: hugo-translator.target.test-output.TestDomain
              |_Defined At_: <string>(1:1)
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
    "emit a glossary" in {
      val mdw = MarkdownWriter(Path.of("foo.md"))
      val term1 = GlossaryEntry("one", "Term", "The first term", Seq("A", "B"))
      val term2 =
        GlossaryEntry("two", "Term", "The second term", Seq("A", "B", "C"))
      mdw.emitGlossary(10, Seq(term1, term2))
      val strw = new StringWriter()
      val pw = new PrintWriter(strw)
      mdw.write(pw)
      val output = strw.toString
      val expected = """---
                       |title: "Glossary Of Terms"
                       |weight: 10
                       |description: "A generated glossary of terms"
                       |geekdocAnchor: true
                       |
                       |---
                       || Term | Type | Brief | Path |
                       || ---- | ---- | ----- | ---- |
                       || [one](A/B/one) | Term | The first term | A.B |
                       || [two](A/B/C/two) | Term | The second term | A.B.C |
                       |""".stripMargin
      output mustBe expected
    }
  }
}
