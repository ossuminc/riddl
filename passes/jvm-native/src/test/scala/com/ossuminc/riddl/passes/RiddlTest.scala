/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.utils.{AbstractTestingBasis, PathUtils, PlatformContext}
import com.ossuminc.riddl.utils.{ec, pc, Await}
import com.ossuminc.riddl.utils.Timer

import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class RiddlTest extends AbstractTestingBasis {

  val testPath = "language/input/everything.riddl"
  val url = PathUtils.urlFromCwdPath(Path.of(testPath))

  "Riddl" must {

    "parse a file" in {
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        Riddl.parse(rpi) match
          case Left(messages) => fail(messages.format)
          case Right(root) =>
            val domains = AST.getTopLevelDomains(root)
            domains.size mustBe 1
            val domain = domains.head
            val contexts = AST.getContexts(domain)
            contexts.size mustBe 3
            AST.getDomains(domain) mustBe empty

      }
      Await.result(future, 10.seconds)
    }

    "validate a file" in {
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        Riddl.parse(rpi) match
          case Left(messages) => fail(messages.format)
          case Right(root) =>
            Riddl.validate(root) match
              case Left(messages) => fail(messages.justErrors.format)
              case Right(result)  => succeed
        end match
      }
      Await.result(future, 10.seconds)
    }

    "parse and validate a RiddlParserInput" in {
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        Riddl.parseAndValidate(rpi) match
          case Left(messages) => fail(messages.justErrors.format)
          case Right(result)  => succeed
        end match
      }
      Await.result(future, 10.seconds)
    }

    "parse and validate a Path" in {
      Riddl.parseAndValidatePath(testPath) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(result)  => succeed
      end match
    }

    "convert a Root back to text" in {
      val input =
        """domain foo is {
          |  domain bar is { ??? }
          |}""".stripMargin
      val rpi = RiddlParserInput(input, "")
      Riddl.parse(rpi) match
        case Left(messages) => fail(messages.format)
        case Right(root) =>
          val converted =
            Timer.time("toRiddlText", true) {
              Riddl.toRiddlText(root)
            }
          converted must be(input)
    }

  }
}
