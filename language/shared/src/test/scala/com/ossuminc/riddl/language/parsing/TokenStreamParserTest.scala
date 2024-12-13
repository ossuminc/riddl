/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.CommentTKN
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.{AST,At}
import com.ossuminc.riddl.language.AST.*
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
          |   invariant bar is "condition"
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
            AST.KeywordTKN(At(rpi, 69, 78)),
            AST.IdentifierTKN(At(rpi, 79, 82)),
            AST.ReadabilityTKN(At(rpi, 83, 85)),
            AST.QuotedStringTKN(At(rpi, 86, 97)),
            AST.PunctuationTKN(At(rpi, 98, 99))
          )
          tokens must be(expected)
    }
  }

  "handle everything.riddl in detail" in { (td: TestData) =>
    implicit val ec: ExecutionContext = pc.ec
    val url = URL.fromCwdPath("language/input/everything.riddl")
    val future = RiddlParserInput.fromURL(url, td).map { rpi =>
      val expectedTokens: List[AST.Token] = List(
        CommentTKN(At(rpi,0,19)),
        KeywordTKN(At(rpi,20,26)),
        IdentifierTKN(At(rpi,27,31)),
         ReadabilityTKN(At(rpi,32,34)),
         PunctuationTKN(At(rpi,35,36)),
         KeywordTKN(At(rpi,37,41)),
         PunctuationTKN(At(rpi,41,42)),
         QuotedStringTKN(At(rpi,43,49)),
         KeywordTKN(At(rpi,50,55)),
         PunctuationTKN(At(rpi,55,56)),
         QuotedStringTKN(At(rpi,57,73)),
         PunctuationTKN(At(rpi,74,75)),
         CommentTKN(At(rpi,77,98)),
         KeywordTKN(At(rpi,99,105)),
         IdentifierTKN(At(rpi,106,116)),
         ReadabilityTKN(At(rpi,117,119)),
         PunctuationTKN(At(rpi,120,121)),
         KeywordTKN(At(rpi,124,128)),
         IdentifierTKN(At(rpi,129,137)),
         ReadabilityTKN(At(rpi,138,140)),
         PredefinedTKN(At(rpi,141,147)),
         CommentTKN(At(rpi,148,168)),
         CommentTKN(At(rpi,172,233)),
         KeywordTKN(At(rpi,233,240)),
         IdentifierTKN(At(rpi,241,249)),
         PunctuationTKN(At(rpi,249,250)),
         IdentifierTKN(At(rpi,250,260)),
         PunctuationTKN(At(rpi,260,261)),
         PredefinedTKN(At(rpi,262,269)),
         PunctuationTKN(At(rpi,269,270)),
         KeywordTKN(At(rpi,274,281)),
         QuotedStringTKN(At(rpi,282,301)),
         KeywordTKN(At(rpi,305,309)),
         IdentifierTKN(At(rpi,310,316)),
         ReadabilityTKN(At(rpi,317,319)),
         QuotedStringTKN(At(rpi,320,327)),
         KeywordTKN(At(rpi,329,333)),
         PunctuationTKN(At(rpi,334,335)),
         KeywordTKN(At(rpi,336,343)),
         QuotedStringTKN(At(rpi,344,357)),
         PunctuationTKN(At(rpi,358,359)),
         KeywordTKN(At(rpi,363,367)),
         IdentifierTKN(At(rpi,368,380)),
         ReadabilityTKN(At(rpi,381,383)),
         PunctuationTKN(At(rpi,384,385)),
         KeywordTKN(At(rpi,390,394)),
         IdentifierTKN(At(rpi,395,405)),
         PunctuationTKN(At(rpi,405,406)),
         IdentifierTKN(At(rpi,406,412)),
         ReadabilityTKN(At(rpi,413,418)),
         QuotedStringTKN(At(rpi,419,442)),
         ReadabilityTKN(At(rpi,443,445)),
         ReadabilityTKN(At(rpi,446,450)),
         QuotedStringTKN(At(rpi,451,486)),
         KeywordTKN(At(rpi,491,495)),
         IdentifierTKN(At(rpi,496,503)),
         ReadabilityTKN(At(rpi,504,506)),
         PunctuationTKN(At(rpi,507,508)),
         KeywordTKN(At(rpi,509,513)),
         IdentifierTKN(At(rpi,514,524)),
         PunctuationTKN(At(rpi,524,525)),
         IdentifierTKN(At(rpi,525,531)),
         ReadabilityTKN(At(rpi,532,537)),
         QuotedStringTKN(At(rpi,538,558)),
         ReadabilityTKN(At(rpi,559,561)),
         ReadabilityTKN(At(rpi,562,566)),
         QuotedStringTKN(At(rpi,567,589)),
         PunctuationTKN(At(rpi,590,593)),
         PunctuationTKN(At(rpi,594,595)),
         KeywordTKN(At(rpi,600,604)),
         IdentifierTKN(At(rpi,605,614)),
         ReadabilityTKN(At(rpi,615,617)),
         PunctuationTKN(At(rpi,618,619)),
         KeywordTKN(At(rpi,620,624)),
         IdentifierTKN(At(rpi,625,635)),
         PunctuationTKN(At(rpi,635,636)),
         IdentifierTKN(At(rpi,636,642)),
         ReadabilityTKN(At(rpi,643,648)),
         QuotedStringTKN(At(rpi,649,664)),
         ReadabilityTKN(At(rpi,665,667)),
         ReadabilityTKN(At(rpi,668,672)),
         QuotedStringTKN(At(rpi,673,712)),
         PunctuationTKN(At(rpi,713,716)),
         PunctuationTKN(At(rpi,717,718)),
         PunctuationTKN(At(rpi,721,722)),
         KeywordTKN(At(rpi,723,727)),
         PunctuationTKN(At(rpi,728,729)),
         KeywordTKN(At(rpi,730,737)),
         ReadabilityTKN(At(rpi,738,740)),
         QuotedStringTKN(At(rpi,741,766)),
         PunctuationTKN(At(rpi,767,768)),
         KeywordTKN(At(rpi,772,779)),
         QuotedStringTKN(At(rpi,780,797)),
         KeywordTKN(At(rpi,801,808)),
         QuotedStringTKN(At(rpi,809,825)),
         PunctuationTKN(At(rpi,826,827)),
         KeywordTKN(At(rpi,828,832)),
         PunctuationTKN(At(rpi,833,834)),
         ReadabilityTKN(At(rpi,837,839)),
         KeywordTKN(At(rpi,840,846)),
         IdentifierTKN(At(rpi,847,851)),
         PunctuationTKN(At(rpi,852,853))
      )
      val result = pc.withOptions(pc.options.copy(showTimes=true)) { options => 
        Timer.time("parseToTokens") { 
          TopLevelParser.parseToTokens(rpi) 
        }
      }
      result match
        case Left(messages) =>
          fail(messages.format)
        case Right(tokens) =>
          val sample = tokens.take(expectedTokens.length).toString
          val expected = expectedTokens.toString
          sample must be(expected)
      end match
    }
    Await.result(future, 30)

  }
}
