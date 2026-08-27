/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{pc, DOMPlatformContext, PlatformContext, URL}
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.must.Matchers

import scala.concurrent.{ExecutionContext, Future}

/** TopLevelParser on Scala.js.
  *
  * This used to fetch `dokn.riddl` from `raw.githubusercontent.com/ossuminc/riddl-examples` and
  * assert on its contents, which made a parser test depend on the network and on another
  * repository's current state — it broke when riddl-examples was migrated to 2.0 syntax, having
  * caught nothing. The subject here is the parser, so the input is inline and the result is
  * deterministic. Loading over a URL on JS is covered by `utils`' own LoaderTest.
  */
class TopLevelParserTest extends AsyncFunSpec with Matchers:
  implicit override def executionContext: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  private val model =
    """domain dokn is {
      |  context Deliveries is {
      |    type Package is { id: String }
      |    command Deliver is { id: String }
      |    handler H is {
      |      on command Deliver is { ??? }
      |    }
      |  }
      |}
      |""".stripMargin

  describe("TopLevelParser") {
    it("do some parsing") {
      val input = RiddlParserInput(model, "parsing")
      TopLevelParser.parseInput(input) match {
        case Left(errors) => fail(errors.format)
        case Right(root) =>
          root.domains.head.id.value must be("dokn")
          root.domains.head.contexts.head.id.value must be("Deliveries")
      }
    }
  }
end TopLevelParserTest
