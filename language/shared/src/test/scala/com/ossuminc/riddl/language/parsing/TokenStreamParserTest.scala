/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{Await, PlatformContext, Timer, URL}
import org.scalatest.TestData

import scala.concurrent.ExecutionContext
import scala.io.AnsiColor.{GREEN, RED, RESET}

abstract class TokenStreamParserTest(using pc: PlatformContext) extends AbstractParsingTest {
  "TokenStreamParser" must {
    "handle simple document fragment" in { (td: TestData) =>
      val rpi: RiddlParserInput = RiddlParserInput(
        """module foo is {
          |   // this is a comment
          |   domain blah is { ??? }
          |}
          |""".stripMargin,
        td
      )
      val result = Timer.time("Token Collection: simple document") {
        TopLevelParser.parseToTokens(rpi)
      }
      result match
        case Left(messages) =>
          fail(messages.format)
        case Right(tokens) =>
          val expected = Seq(
            AST.KeywordTKN(At(rpi, 0, 6)),
            AST.IdentifierTKN(At(rpi, 7, 10)),
            AST.ReadabilityTKN(At(rpi, 11, 13)),
            AST.PunctuationTKN(At(rpi, 14, 15)),
            AST.CommentTKN(At(rpi, 19, 39)),
            AST.KeywordTKN(At(rpi, 43, 49)),
            AST.IdentifierTKN(At(rpi, 50, 54)),
            AST.ReadabilityTKN(At(rpi, 55, 57)),
            AST.PunctuationTKN(At(rpi, 58, 59)),
            AST.PunctuationTKN(At(rpi, 60, 63)),
            AST.PunctuationTKN(At(rpi, 64, 65)),
            AST.PunctuationTKN(At(rpi, 66, 67))
          )
          tokens must be(expected)
    }
  }
  "handle full document" in { (td: TestData) =>
    implicit val ec: ExecutionContext = pc.ec
    val url = URL.fromCwdPath("language/jvm/src/test/input/everything.riddl")
    val future = RiddlParserInput.fromURL(url, td).map { rpi =>
      val result = Timer.time("Token Collection: full document") {
        TopLevelParser.parseToTokens(rpi)
      }
      result match
        case Left(messages) =>
          fail(messages.format)
        case Right(tokens) =>
          tokens
      end match
    }
    Await.result(future,3)
  }
}
