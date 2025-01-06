/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo.writers

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{PassesResult, Riddl}
import com.ossuminc.riddl.utils.{Await, CommonOptions, URL, ec, pc}
import org.scalatest.Assertion

import java.nio.file.Path
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class AdaptorWriterTest extends WriterTest {

  "AdaptorWriter" must {
    "handle a message rename" in {
      val url = URL.fromCwdPath("commands/input/adaptors.riddl")
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        validateRoot(rpi) {
          case passesResult: PassesResult =>
            val mkd = makeMDW(output, PassesResult.empty)
            val root = passesResult.root
            val domain = root.domains.head
            val context = domain.contexts.head
            val adaptor = context.adaptors.head
            val parents = Seq(root, domain, context)
            mkd.emitAdaptor(adaptor, parents)
            val result = mkd.toString
            // info(result)
            result mustNot be(empty)
            val expected =
              """---
                |title: "FromTwo"
                |weight: 10
                |draft: "false"
                |description: "No brief description."
                |geekdocAnchor: true
                |geekdocToC: 4
                |geekdocCollapseSection: true
                |---
                |
                |## *Adaptor 'FromTwo'*
                || Item | Value |
                || :---: | :---  |
                || _Briefly_ | No brief description. |
                || _Authors_ |  |
                || _Definition Path_ | Root.Adaptors.One.FromTwo |
                || _View Source Link_ | [commands/input/adaptors.riddl(77->372)]() |
                || _Used By_ | None |
                || _Uses_ | None |
                |
                |## *Description*
                |
                |## *RiddlOptions*
                |* option css("background: blue")
                |
                |## *Direction: from context Two*
                |
                |### _Handlers_
                |
                |### _Handler 'Adaptation'_
                || Item | Value |
                || :---: | :---  |
                || _Briefly_ | No brief description. |
                || _Definition Path_ | FromTwo.Root.Adaptors.One.Adaptation |
                || _View Source Link_ | [commands/input/adaptors.riddl(158->368)]() |
                || _Used By_ | None |
                || _Uses_ | None |
                |
                |## *Description*
                |
                |####  On event Adaptors.Two.DidIt
                |```
                |"convert Two.DidIt to One.TwoIsDone"
                |tell command Adaptors.One.TwoIsDone to context One
                |```
                |""".stripMargin

            result must be(expected)
        }
      }
      Await.result(future, 10.seconds)
    }
  }
}
