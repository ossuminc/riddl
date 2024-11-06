/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.{PlatformContext, URL}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import fastparse.*
import fastparse.MultiLineWhitespace.*
import wvlet.airframe.ulid.ULID

import java.net.URI
import java.nio.file.Files
import scala.reflect.{ClassTag, classTag}
import scala.concurrent.Future

/** Common Parsing Rules */
private[parsing] trait CommonParser(using io: PlatformContext)
    extends ReferenceParser
    with Readability
    with NoWhiteSpaceParsers
    with ParsingContext {

  def open[u: P]: P[Unit] = {
    P(Punctuation.curlyOpen)
  }

  def close[u: P]: P[Unit] = {
    P(Punctuation.curlyClose)
  }

  def author[u: P]: P[Author] =
    P(
      Index ~ Keywords.author ~/ identifier ~ is ~ open ~
        ((undefined(
          (
            LiteralString(At(), ""),
            LiteralString(At(), ""),
            Option.empty[LiteralString],
            Option.empty[LiteralString],
            Option.empty[URL]
          )
        ) |
          (Keywords.name ~ is ~ literalString ~ Keywords.email ~ is ~
            literalString ~ (Keywords.organization ~ is ~ literalString).? ~
            (Keywords.title ~ is ~ literalString).? ~
            (Keywords.url ~ is ~ httpUrl).?))) ~ close ~ withMetaData ~/ Index
    ).map { case (start, id, (name, email, org, title, url), descriptives, end) =>
      Author(at(start, end), id, name, email, org, title, url, descriptives.toContents)
    }
  end author

  def importDef[u: P]: P[OccursInDomain] = {
    P(
      Index ~ Keywords.import_ ~ Keywords.domain ~ identifier ~ from ~ literalString ~ Index
    ).map { case (off1, id, litStr, off2) =>
      doImport(at(off1, off2), id, litStr)
    }
  }

  def undefined[u: P, RT](f: => RT): P[RT] = {
    P(Punctuation.undefinedMark./).map(_ => f)
  }

  def literalStrings[u: P]: P[Seq[LiteralString]] = { P(literalString.rep(1)) }

  private def markdownLines[u: P]: P[Seq[LiteralString]] = {
    P(markdownLine.rep(1))
  }

  def maybe[u: P](keyword: String): P[Unit] = P(keyword).?

  private def briefDescription[u: P]: P[BriefDescription] = {
    P(Index ~ Keywords.briefly ~ byAs.? ~ literalString ~ Index).map { case (off1, brief: LiteralString, off2) =>
      BriefDescription(at(off1, off2), brief)
    }
  }

  private def docBlock[u: P]: P[Seq[LiteralString]] = {
    P(
      (open ~
        (markdownLines | literalStrings | undefined(Seq.empty[LiteralString])) ~
        close) | literalString.map(Seq(_))
    )
  }

  def description[u: P](implicit ctx: P[?]): P[Description] =
    P(
      Index ~ Keywords.described ~ (
        (byAs ~/ docBlock) |
          (at ~/ httpUrl) |
          (in ~/ Keywords.file ~ literalString)
      ) ~ Index
    ).map {
      case (off1, strings: Seq[LiteralString], off2) => BlockDescription(at(off1, off2), strings)
      case (off1, url: URL, off2)                    => URLDescription(at(off1, off2), url)
      case (off1, file: LiteralString, off2) =>
        val url = ctx.input.asInstanceOf[RiddlParserInput].root.resolve(file.s)
        URLDescription(at(off1, off2), url)
    }

  def maybeDescription[u: P]: P[Option[Description]] =
    P(description).?

  private def inlineComment[u: P]: P[InlineComment] = {
    P(
      Index ~ "/*" ~ until('*', '/') ~ Index
    ).map { case (off1, comment, off2) =>
      val actual = comment.dropRight(2) // we don't want the */ in the comment text
      val lines = actual.split('\n').toList
      InlineComment(at(off1, off2), lines)
    }
  }

  private def endOfLineComment[u: P]: P[LineComment] = {
    P(Index ~ "//" ~ toEndOfLine ~ Index).map { case (off1, comment, off2) =>
      LineComment(at(off1, off2), comment)
    }
  }

  def comment[u: P]: P[Comment] = {
    P(inlineComment | endOfLineComment)
  }

  def comments[u: P]: P[Seq[Comment]] = {
    P(comment).rep(0)
  }

  private def wholeNumber[u: P]: P[Long] = {
    CharIn("0-9").rep(1).!.map(_.toLong)
  }

  def integer[u: P]: P[Long] = {
    CharIn("+\\-").? ~~ wholeNumber
  }

  private def simpleIdentifier[u: P]: P[String] = {
    P(CharIn("a-zA-Z") ~~ CharsWhileIn("a-zA-Z0-9_\\-").?).!
  }

  private def quotedIdentifier[u: P]: P[String] = {
    P("'" ~~ CharsWhileIn("a-zA-Z0-9_+\\-|/@$%&, :", 1).! ~~ "'")
  }

  private def anyIdentifier[u: P]: P[String] = {
    P(simpleIdentifier | quotedIdentifier)
  }

  def identifier[u: P]: P[Identifier] = {
    P(Index ~ anyIdentifier ~ Index).map { case (off1, value, off2) => Identifier(at(off1, off2), value) }
  }

  def pathIdentifier[u: P]: P[PathIdentifier] = {
    P(Index ~ anyIdentifier ~~ (Punctuation.dot ~~ anyIdentifier).repX(0) ~ Index).map {
      case (off1, first, strings, off2) =>
        PathIdentifier(at(off1, off2), first +: strings)
    }
  }

  def term[u: P]: P[Term] = {
    P(
      Index ~ Keywords.term ~ identifier ~ is ~ docBlock ~ withMetaData ~ Index
    )./.map { case (off1, id, definition, descriptives, off2) =>
      Term(at(off1, off2), id, definition, descriptives.toContents)
    }
  }

  def mimeType[u: P]: P[String] = {
    P(
      ("application" | "audio" | "example" | "font" |
        "image" | "model" | "text" | "video") ~~ "/" ~~ CharIn("a-z\\-.+").rep(1)
    ).!
  }

  private def fileAttachment[u: P]: P[FileAttachment] = {
    P(
      Index ~ Keywords.attachment ~ identifier ~ is ~ mimeType ~ in ~ Keywords.file ~ literalString ~ Index
    ).map { case (off1, id, mimeType, fileName, off2) =>
      FileAttachment(at(off1, off2), id, mimeType, fileName)
    }
  }

  private def stringAttachment[u: P]: P[StringAttachment] =
    P(
      Index ~ Keywords.attachment ~ identifier ~ is ~ mimeType ~ as ~ literalString ~ Index
    ).map { case (off1, id, mimeType, value, off2) =>
      StringAttachment(at(off1, off2), id, mimeType, value)
    }

  private def ulidAttachment[u: P]: P[ULIDAttachment] =
    P(
      Index ~ Keywords.attachment ~ "ULID" ~ is ~ literalString ~ Index
    ).map { case (start, ulidString, end) =>
      val ulid = ULID.fromString(ulidString.s)
      ULIDAttachment(at(start, end), ulid)
    }
  end ulidAttachment

  private def metaData[u: P]: P[MetaData] =
    P(briefDescription | description | term | authorRef | fileAttachment | stringAttachment | ulidAttachment)
      .asInstanceOf[P[MetaData]]

  def withMetaData[u: P]: P[Seq[MetaData]] = {
    P(
      Keywords.`with` ~ open ~ (undefined(Seq.empty[MetaData]) | metaData.rep(1)) ~ close
    ).?./.map {
      case Some(list: Seq[MetaData]) =>
        list
      case None =>
        Seq.empty
    }
  }

  def include[u: P, CT <: RiddlValue](parser: P[?] => P[Seq[CT]]): P[Include[CT]] = {
    P(Index ~ Keywords.include ~ literalString ~ Index)./.map { case (off1, str: LiteralString, off2) =>
      doIncludeParsing[CT](at(off1, off2), str.s, parser)
    }
  }

  private def hostString[u: P]: P[String] = {
    P(CharsWhile { ch => ch.isLetterOrDigit || ch == '-' }.rep(1, ".", 32)).!
  }

  private def portNum[u: P]: P[String] = {
    P(Index ~~ CharsWhileIn("0-9").rep(min = 1, max = 5).! ~~ Index).map { (i1, numStr: String, i2) =>
      val num = numStr.toInt
      if num > 0 && num < 65535 then 
        numStr
      else
        error(at(i1,i2), s"Invalid port number: $numStr. Must be in range 0 <= port < 65536")
        "0"
      end if  
    }
  }

  private def urlPath[u: P]: P[String] = {
    P(
      CharsWhile(ch => ch.isLetterOrDigit || "/-?#/.=".contains(ch))
        .rep(min = 0, max = 240)
    ).!
  }

  def httpUrl[u: P]: P[URL] = {
    P(
      "http" ~ "s".? ~ "://" ~ hostString ~ (":" ~ portNum).? ~ "/" ~ urlPath.?
    ).!.map(URL)
  }

  def invariant[u: P]: P[Invariant] = {
    P(
      Index ~ Keywords.invariant ~ identifier ~/ is ~ (
        undefined(Option.empty[LiteralString]) | literalString.map(Some(_))
      ) ~ withMetaData ~/ Index
    ).map { case (off1, id, condition, metas, off2) =>
      Invariant(at(off1, off2), id, condition, metas.toContents)
    }
  }

  def groupAliases[u: P]: P[String] = {
    P(
      Keywords.keywords(
        StringIn(
          Keyword.group,
          "page",
          "pane",
          "dialog",
          "menu",
          "popup",
          "frame",
          "column",
          "window",
          "section",
          "tab",
          "flow",
          "block"
        ).!
      )
    )
  }

  def outputAliases[u: P]: P[String] = {
    P(
      Keywords.keywords(
        StringIn(
          Keyword.output,
          "document",
          "list",
          "table",
          "graph",
          "animation",
          "picture"
        ).!
      )
    )
  }

  def inputAliases[u: P]: P[String] = {
    P(
      Keywords.keywords(
        StringIn(
          Keyword.input,
          "form",
          "text",
          "button",
          "picklist",
          "selector",
          "item"
        ).!
      )
    )
  }

  def shownBy[u: P]: P[ShownBy] = {
    P(
      Index ~ Keywords.shown ~ by ~ open ~ httpUrl.rep(1) ~ close ~ Index
    ).map { case (off1, urls, off2) =>
      ShownBy(at(off1, off2), urls)
    }
  }
}
