/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.utils.{AbstractTestingBasis, JVMPlatformContext, PathUtils, PlatformContext}
import com.ossuminc.riddl.utils.{pc, ec, Await}

import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class RiddlTest extends AbstractTestingBasis {

  val testPath = "language/jvm/src/test/input/everything.riddl"
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
            contexts.size mustBe 2
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
  }
}
