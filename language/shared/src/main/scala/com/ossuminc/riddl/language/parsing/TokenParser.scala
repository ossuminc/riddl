/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext, Timer}
import com.ossuminc.riddl.utils.SeqHelpers.*
import com.ossuminc.riddl.utils.URL
import fastparse.*
import fastparse.MultiLineWhitespace.*
import fastparse.Parsed.Failure
import fastparse.Parsed.Success
import jdk.jshell.SourceCodeAnalysis.Documentation

import scala.util.control.NonFatal

trait TokenParser(using pc: PlatformContext) extends CommonParser with Readability {

  private def numericToken[u: P]: P[Token.Numeric] = {
    P(Index ~~ integer ~~ Index)./.map { case (start, _, end) => Token.Numeric(at(start, end)) }
  }

  private def punctuationToken[u: P]: P[Token.Punctuation] = {
    P(Index ~~ Punctuation.tokenPunctuation ~~ Index)./.map { case (start, end) =>
      Token.Punctuation(at(start, end))
    }
  }

  private def literalCode[u: P]: P[Token.LiteralCode] = {
    P(
      Index ~~ Punctuation.codeQuote ~~ until3('`', '`', '`') ~~ Index
    ).map { case (start: Int, _: String, end: Int) =>
      Token.LiteralCode(at(start, end))
    }
  }

  private def stringContent[u: P]: P[Unit] =
    P(CharsWhile(stringChars) | escape)

  private def quotedStringToken[u: P]: P[Token.QuotedString] = {
    P(
      Index ~~ Punctuation.quote ~~/ stringContent.rep ~~ Punctuation.quote ~~ Index
    )./.map { case (start: Int, end: Int) => Token.QuotedString(at(start, end)) }
  }

  private def readabilityToken[u: P]: P[Token.Readability] = {
    P(Index ~~ anyReadability ~~ Index)./.map { case (start, end) =>
      Token.Readability(at(start, end))
    }
  }

  private def predefinedToken[u: P]: P[Token.Predefined] = {
    import com.ossuminc.riddl.language.parsing.PredefType.*
    P(Index ~~ Keywords.keywords(PredefTypes.anyPredefType) ~~ Index)./.map { case (start, end) =>
      Token.Predefined(at(start, end))
    }
  }

  private def keywordToken[u: P]: P[Token.Keyword] = {
    P(Index ~~ Keywords.anyKeyword ~~ Index)./.map { case (start, end) =>
      Token.Keyword(at(start, end))
    }
  }

  private def commentToken[u: P]: P[Token.Comment] = {
    P(comment)./.map { case comment: Comment => Token.Comment(comment.loc) }
  }

  private def markdownLinesToken[u: P]: P[Token.MarkdownLine] = {
    P(
      Index ~~ Punctuation.verticalBar ~~ CharsWhile(ch => ch != '\n' && ch != '\r') ~~ Index
    )./.map { case (start, end) =>
      Token.MarkdownLine(at(start, end))
    }
  }

  private def identifierToken[u: P]: P[Token.Identifier] = {
    P(identifier)./.map { case id: Identifier => Token.Identifier(id.loc) }
  }

  private def otherToken[u: P]: P[Token.Other] = {
    P(
      Index ~~ (!(CharIn(" \n\r") | End) ~~ AnyChar).rep(1) ~~ Index
    )./.map { case (start, end) =>
      Token.Other(at(start, end))
    }
  }

  def parseAnyToken[u: P]: P[Token] = {
    P(
      quotedStringToken |
        markdownLinesToken |
        literalCode |
        punctuationToken |
        keywordToken |
        readabilityToken |
        predefinedToken |
        identifierToken |
        numericToken |
        commentToken |
        otherToken
    )./
  }

  def parseAllTokens[u: P]: P[List[Token]] = {
    P(Start ~ parseAnyToken.rep(1) ~ End).map(_.toList)
  }

}
