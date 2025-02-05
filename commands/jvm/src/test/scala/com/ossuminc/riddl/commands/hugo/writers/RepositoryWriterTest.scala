/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo.writers

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.{Await, URL, ec, pc}
import org.scalatest.Assertion

import scala.concurrent.duration.DurationInt

class RepositoryWriterTest extends WriterTest {

  "RepositoryWriter" must {
    "handle a repository" in {
      val url = URL.fromCwdPath("commands/input/repository.riddl")
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        validateRoot(rpi) { case passesResult: PassesResult =>
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
          val expected =
            """---
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
              || _View Source Link_ | [commands/input/repository.riddl(46->85)]() |
              || _Used By_ | None |
              || _Uses_ | None |
              |
              |## *Description*
              |""".stripMargin
          result mustBe expected
        }
      }
      Await.result(future, 10.seconds)
    }
  }
}
