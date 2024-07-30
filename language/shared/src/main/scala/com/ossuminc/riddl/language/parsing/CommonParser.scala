/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.URL
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import fastparse.{P, *}
import fastparse.MultiLineWhitespace.*

import java.net.URI
import java.nio.file.Files
import scala.reflect.{ClassTag, classTag}

/** Common Parsing Rules */
private[parsing] trait CommonParser extends NoWhiteSpaceParsers {

  def author[u: P]: P[Author] = {
    P(
      location ~ Keywords.author ~/ identifier ~ Readability.is ~ open ~
        (undefined(
          (
            LiteralString(At(), ""),
            LiteralString(At(), ""),
            Option.empty[LiteralString],
            Option.empty[LiteralString],
            Option.empty[URL]
          )
        ) |
          (Keywords.name ~ Readability.is ~ literalString ~ Keywords.email ~ Readability.is ~
            literalString ~ (Keywords.organization ~ Readability.is ~ literalString).? ~
            (Keywords.title ~ Readability.is ~ literalString).? ~
            (Keywords.url ~ Readability.is ~ httpUrl).?)) ~ close ~ briefly ~ description
    ).map { case (loc, id, (name, email, org, title, url), brief, description) =>
      Author(loc, id, name, email, org, title, url, brief, description)
    }
  }

  def include[u: P, K <: RiddlValue](
    parser: P[?] => P[Seq[K]]
  ): P[IncludeHolder[K]] = {
    P(Keywords.include ~/ location ~ literalString)./.map { case (loc: At, str: LiteralString) =>
      doInclude[K](loc, str)(parser)
    }
  }

  def importDef[u: P]: P[OccursInDomain] = {
    P(
      location ~ Keywords.import_ ~ Keywords.domain ~ identifier ~ Readability.from ~ literalString
    ).map { tuple => doImport(tuple._1, tuple._2, tuple._3) }
  }

  def undefined[u: P, RT](f: => RT): P[RT] = {
    P(Punctuation.undefinedMark./).map(_ => f)
  }

  def literalStrings[u: P]: P[Seq[LiteralString]] = { P(literalString.rep(1)) }

  private def markdownLines[u: P]: P[Seq[LiteralString]] = {
    P(markdownLine.rep(1))
  }

  def maybe[u: P](keyword: String): P[Unit] = P(keyword).?

  def docBlock[u: P]: P[Seq[LiteralString]] = {
    P(
      (open ~
        (markdownLines | literalStrings | undefined(Seq.empty[LiteralString])) ~
        close) | literalString.map(Seq(_))
    )
  }

  private def blockDescription[u: P]: P[BlockDescription] = {
    P(location ~ docBlock).map(tpl => BlockDescription(tpl._1, tpl._2))
  }

  private def fileDescription[u: P](implicit ctx: P[?]): P[FileDescription] = {
    P(location ~ Keywords.file ~ literalString).map { case (loc, file) =>
      val path = ctx.input.asInstanceOf[RiddlParserInput].root.toPath.resolve(file.s)
      if Files.isReadable(path) && Files.isRegularFile(path) then {
        FileDescription(loc, path)
      } else {
        error(loc, s"Description file cannot be read: $path ")
        FileDescription(loc, path)
      }
    }
  }

  private def urlDescription[u: P]: P[URLDescription] = {
    P(location ~ httpUrl).map { case (loc, url) =>
      URLDescription(loc, url)
    }
  }

  def briefly[u: P]: P[Option[LiteralString]] = {
    P(Keywords.briefly ~/ literalString)
  }.?

  def description[u: P]: P[Option[Description]] = P(
    P(
      Keywords.described ~/
        ((Readability.byAs ~ blockDescription) | (Readability.in ~ fileDescription) |
          (Readability.at ~ urlDescription))
    )
  ).?

  private def inlineComment[u: P]: P[InlineComment] = {
    P(
      location ~ "/*" ~ until('*', '/')
    ).map { case (loc, comment) =>
      val lines = comment.split('\n').toList
      InlineComment(loc, lines)
    }
  }

  private def endOfLineComment[u: P]: P[LineComment] = {
    P(location ~ "//" ~ toEndOfLine).map { case (loc, comment) =>
      LineComment(loc, comment)
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
    P(location ~ anyIdentifier).map { case (loc, value) => Identifier(loc, value) }
  }

  def pathIdentifier[u: P]: P[PathIdentifier] = {
    P(location ~ anyIdentifier ~~ (Punctuation.dot ~~ anyIdentifier).repX(0)).map { case (loc, first, strings) =>
      PathIdentifier(loc, first +: strings)
    }
  }

  def open[u: P]: P[Unit] = { P(Punctuation.curlyOpen) }

  def close[u: P]: P[Unit] = { P(Punctuation.curlyClose) }

  private def maybeOptionWithArgs[u: P](
    validOptions: => P[String]
  ): P[(At, String, Seq[LiteralString])] = {
    P(
      location ~ validOptions ~
        (Punctuation.roundOpen ~ literalString.rep(0, Punctuation.comma) ~
          Punctuation.roundClose).?
    ).map {
      case (loc, opt, Some(maybeArgs)) => (loc, opt, maybeArgs)
      case (loc, opt, None)            => (loc, opt, Seq.empty[LiteralString])
    }
  }

  def option[u: P]: P[OptionValue] = {
    P(
      Keywords.option ~/ Readability.is.? ~
        location ~ CharsWhile(ch => ch.isLower | ch.isDigit | ch == '_' | ch == '-').! ~
        (Punctuation.roundOpen ~ literalString.rep(0, Punctuation.comma) ~
          Punctuation.roundClose).?
    ).map { case (loc, option, params) =>
      OptionValue(loc, option, params.getOrElse(Seq.empty[LiteralString]))
    }
  }

  extension (map: Map[Class[RiddlValue], Seq[RiddlValue]])
    def extract[T <: RiddlValue: ClassTag]: Seq[T] = {
      val clazzTag = classTag[T].runtimeClass
      map
        .get(clazzTag.asInstanceOf[Class[RiddlValue]])
        .fold(Seq.empty[T])(_.map(_.asInstanceOf[T]))
    }

  def mapTo[T <: RiddlValue](seq: Option[Seq[RiddlValue]]): Seq[T] = {
    seq.fold(Seq.empty[T])(_.map(_.asInstanceOf[T]))
  }

  private def hostString[u: P]: P[String] = {
    P(CharsWhile { ch => ch.isLetterOrDigit || ch == '-' }.rep(1, ".", 32)).!
  }

  private def portNum[u: P]: P[String] = {
    P(CharsWhileIn("0-9").rep(min = 1, max = 5)).!
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

  def term[u: P]: P[Term] = {
    P(location ~ Keywords.term ~ identifier ~ Readability.is ~ briefly ~ description)./.map(tpl =>
      Term.apply.tupled(tpl)
    )
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

}
