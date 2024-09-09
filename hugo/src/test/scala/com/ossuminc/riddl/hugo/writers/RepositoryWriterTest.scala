package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Riddl, PassesResult}
import org.scalatest.Assertion

import java.nio.file.Path

class RepositoryWriterTest extends WriterTest {

  "RepositoryWriter" must {
    "handle a repository" in {
      val path = base.resolve("repository.riddl")
      val input = RiddlParserInput.fromCwdPath(path)
      validateRoot(input, CommonOptions()) { case passesResult: PassesResult =>
        val mkd = makeMDW(output, PassesResult.empty)
        val root = passesResult.root
        val domain = root.domains.head
        val context = domain.contexts.head
        val repository = context.repositories.head
        val parents = Seq(root, domain, context)
        mkd.emitRepository(repository, parents)
        val result = mkd.toString
        // info(result)
        result mustNot be(empty)
        val expected = """---
                         |title: "Repo"
                         |weight: 10
                         |draft: "false"
                         |description: "No brief description."
                         |geekdocAnchor: true
                         |geekdocToC: 4
                         |geekdocCollapseSection: true
                         |---
                         || Item | Value |
                         || :---: | :---  |
                         || _Briefly_ | No brief description. |
                         || _Authors_ |  |
                         || _Definition Path_ | Root.Repository.One.Repo |
                         || _View Source Link_ | [hugo/src/test/input/repository.riddl(3:5)]() |
                         || _Used By_ | None |
                         || _Uses_ | None |
                         |
                         |## *Description*
                         |""".stripMargin
        result mustBe expected
      }
    }
  }
}
