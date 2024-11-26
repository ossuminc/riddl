/*
 * Copyright 2019 Ossum, Inc.
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

trait TokenStreamParser extends CommonParser with Readability {

  def punctuationToken[u: P]: P[PunctuationTKN] = {
    P(Index ~~ Punctuation.anyPunctuation ~~ Index)./.map { case (start, end) => PunctuationTKN(at(start, end)) }
  }

  def quotedStringToken[u: P]: P[QuotedStringTKN] = {
    P(literalString)./.map { case litStr: LiteralString => QuotedStringTKN(litStr.loc) }
  }

  def readabilityToken[u: P]: P[ReadabilityTKN] = {
    P(Index ~~ anyReadability ~~ Index)./.map { case (start, end) => ReadabilityTKN(at(start, end)) }
  }

  def predefinedToken[u: P]: P[PredefinedTKN] = {
    import com.ossuminc.riddl.language.parsing.PredefType.*
    P(Index ~~ PredefTypes.anyPredefType ~~ Index)./.map { case (start, end) => PredefinedTKN(at(start, end)) }
  }
  def keywordToken[u: P]: P[KeywordTKN] = {
    P(Index ~~ Keywords.anyKeyword ~~ Index)./.map { case (start, end) => KeywordTKN(at(start, end)) }
  }
  def commentToken[u: P]: P[CommentTKN] = {
    P(comment)./.map { case comment: Comment => CommentTKN(comment.loc) }
  }
  
  def markdownLinesToken[u: P]: P[MarkdownLinesTKN] = {
    P(markdownLines)./.map { case mdl: Seq[LiteralString] =>
      require(mdl.nonEmpty, "markdownLines return empty list of lines")
      val first = mdl.head.loc
      val last = mdl.last.loc
      MarkdownLinesTKN(At.range(first, last))
    }
  }

  def identifierToken[u: P]: P[IdentifierTKN] = {
    P(identifier)./.map { case id: Identifier => IdentifierTKN(id.loc) }
  }

  def otherToken[u: P]: P[OtherTKN] = {
    P(Index ~~ AnyChar.rep(1) ~~ Index)./.map { case (start, end) => OtherTKN(at(start, end)) }
  }

  def parseAnyToken[u: P]: P[Token] = {
    P(
      keywordToken |
        readabilityToken |
        quotedStringToken |
        predefinedToken |
        identifierToken |
        commentToken |
        markdownLinesToken |
        punctuationToken |
        otherToken
    )./
  }

  def parseAllTokens[u: P]: P[List[Token]] = {
    P(Start ~ parseAnyToken.rep(0) ~ End).map(_.toList)
  }
}
