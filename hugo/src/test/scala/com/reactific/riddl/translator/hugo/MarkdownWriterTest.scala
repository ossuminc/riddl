package com.reactific.riddl.translator.hugo

import com.reactific.riddl.hugo.GlossaryEntry
import com.reactific.riddl.hugo.HugoTranslatorState
import com.reactific.riddl.hugo.MarkdownWriter
import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.SymbolTable
import com.reactific.riddl.language.Validation
import com.reactific.riddl.testkit.ParsingTest

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path

class MarkdownWriterTest extends ParsingTest {

  "MarkdownWriterTest" must {
    "emit a domain" in {
      val paths =
        Seq[String]("hugo-translator", "target", "test-output", "container.md")
      val output = Path.of(paths.head, paths.tail*)
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
          val symtab = SymbolTable(root)
          val result = Validation
            .Result(Messages.empty, root, symtab, Map.empty, Map.empty)
          val state = HugoTranslatorState(result, symtab)
          val mkd = MarkdownWriter(output, state)
          mkd.emitDomain(domain, paths.dropRight(1))
          val emitted = mkd.toString
          val expected =
            """---
              |title: "TestDomain: Domain"
              |weight: 10
              |draft: "false"
              |description: "Just For Testing"
              |geekdocAnchor: true
              |geekdocToC: 4
              |geekdocCollapseSection: true
              |geekdocFilePath: no-such-file
              |---
              || Item | Value |
              || :---: | :---  |
              || _Briefly_ | Just For Testing |
              || _Definition Path_ | hugo-translator.target.test-output.TestDomain |
              || _View Source Link_ | [empty(1:1)]() |
              |
              |## *Details*
              |A test domain for ensuring that documentation for domains is
              |generated sufficiently.
              |
              |## *Author*
              |* _Name_: Reid Spencer
              |* _Email_: reid@reactific.com
              |
              |## *Types*
              |
              |### _Others_
              |* [MyString](mystring)
              |
              |## *Textual Domain Index*
              |{{< toc-tree >}}
              |""".stripMargin
          emitted mustBe expected
      }
    }
    "emit a glossary" in {
      val term1 = GlossaryEntry(
        "one",
        "Term",
        "The first term",
        Seq("A", "B"),
        "A/B/one",
        "https://example.com/blob/main/src/main/riddl/one"
      )
      val term2 = {
        GlossaryEntry(
          "two",
          "Term",
          "The second term",
          Seq("A", "B", "C"),
          "A/B/C/two",
          "https://example.com/blob/main/src/main/riddl/two"
        )
      }
      val root = RootContainer.empty
      val symtab = SymbolTable(root)
      val result = Validation
        .Result(Messages.empty, root, symtab, Map.empty, Map.empty)
      val state = HugoTranslatorState(result, symtab)
      val mdw = MarkdownWriter(Path.of("foo.md"), state)
      mdw.emitGlossary(10, Seq(term1, term2))
      val strw = new StringWriter()
      val pw = new PrintWriter(strw)
      mdw.write(pw)
      val output = strw.toString
      val expected =
        """---
          |title: "Glossary Of Terms"
          |weight: 10
          |draft: "false"
          |description: "A generated glossary of terms"
          |geekdocAnchor: true
          |geekdocToC: 4
          |
          |---
          || Term | Type | Brief Description |
          || :---: | :---: | :---              |
          || [`one`](A/B/one)[{{< icon "gdoc_github" >}}](https://example.com/blob/main/src/main/riddl/one "GitHub Link") | [Term](https://riddl.tech/concepts/term/) | The first term |
          || [`two`](A/B/C/two)[{{< icon "gdoc_github" >}}](https://example.com/blob/main/src/main/riddl/two "GitHub Link") | [Term](https://riddl.tech/concepts/term/) | The second term |
          |""".stripMargin
      output mustBe expected
    }
  }
}
