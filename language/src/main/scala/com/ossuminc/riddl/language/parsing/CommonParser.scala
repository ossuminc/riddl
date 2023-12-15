/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import fastparse.*
import fastparse.MultiLineWhitespace.*

import java.net.URI
import java.nio.file.Files
import scala.reflect.{ClassTag, classTag}

/** Common Parsing Rules */
@SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf", "org.wartremover.warts.Throw"))
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
            Option.empty[java.net.URL]
          )
        ) |
          (Keywords.name ~ Readability.is ~ literalString ~ Keywords.email ~ Readability.is ~
            literalString ~ (Keywords.organization ~ Readability.is ~ literalString).? ~
            (Keywords.title ~ Readability.is ~ literalString).? ~
            (Keywords.url ~ Readability.is ~ httpUrl).?)) ~ close ~ briefly ~ description ~ comments
    ).map { case (loc, id, (name, email, org, title, url), brief, description, comments) =>
      Author(loc, id, name, email, org, title, url, brief, description, comments)
    }
  }

  def include[K <: Definition, u: P](
    parser: P[?] => P[Seq[K]]
  ): P[Include[K]] = {
    P(Keywords.include ~/ literalString)./.map { (str: LiteralString) =>
      doInclude[K](str)(parser)
    }
  }

  def importDef[u: P]: P[DomainDefinition] = {
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

  private def fileDescription[u: P]: P[FileDescription] = {
    P(location ~ Keywords.file ~ literalString).map { tpl =>
      val path = current.root.toPath.resolve(tpl._2.s)
      if Files.isReadable(path) && Files.isRegularFile(path) then {
        FileDescription(tpl._1, path)
      } else {
        error(tpl._1, s"Description file cannot be read: $path ")
        FileDescription(tpl._1, path)
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

  private def inlineComment[u: P]: P[Comment] = {
    P(
      location ~ "/*" ~ until('*', '/')
    ).map { case (loc, comment) =>
      Comment(loc, comment)
    }
  }

  private def endOfLineComment[u: P]: P[Comment] = {
    P(location ~ "//" ~ toEndOfLine).map { case (loc, comment) =>
      Comment(loc, comment)
    }
  }

  def comments[u: P]: P[Seq[Comment]] = {
    P(
      inlineComment | endOfLineComment
    ).rep(0)
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
        (Punctuation.roundOpen ~ literalString.rep(0, P(Punctuation.comma)) ~
          Punctuation.roundClose).?
    ).map {
      case (loc, opt, Some(maybeArgs)) => (loc, opt, maybeArgs)
      case (loc, opt, None)            => (loc, opt, Seq.empty[LiteralString])
    }
  }

  def options[u: P, TY <: RiddlValue](
    validOptions: => P[String]
  )(mapper: => (At, String, Seq[LiteralString]) => TY): P[Seq[TY]] = {
    P(
      (Keywords.options ~ Punctuation.roundOpen ~/
        maybeOptionWithArgs(validOptions).rep(1, P(Punctuation.comma)) ~
        Punctuation.roundClose).map(_.map { case (loc, opt, arg) =>
        mapper(loc, opt, arg)
      }) |
        (Keywords.option ~/ Readability.is.? ~
          maybeOptionWithArgs(validOptions)).map(tpl => Seq(mapper.tupled(tpl)))
    ).?.map {
      case Some(seq) => seq
      case None      => Seq.empty[TY]
    }
  }

  implicit class ClassMapHelper(
    map: Map[Class[Definition], Seq[Definition]]
  ) {
    def extract[T <: Definition: ClassTag]: Seq[T] = {
      val clazzTag = classTag[T].runtimeClass
      map
        .get(clazzTag.asInstanceOf[Class[Definition]])
        .fold(Seq.empty[T])(_.map(_.asInstanceOf[T]))
    }
  }

  def mapTo[T <: Definition](seq: Option[Seq[Definition]]): Seq[T] = {
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

  def httpUrl[u: P]: P[java.net.URL] = {
    P(
      "http" ~ "s".? ~ "://" ~ hostString ~ (":" ~ portNum).? ~ "/" ~ urlPath.?
    ).!.map(java.net.URI(_).toURL)
  }

  def term[u: P]: P[Term] = {
    P(location ~ Keywords.term ~ identifier ~ Readability.is ~ briefly ~ description ~ comments)./.map(tpl =>
      (Term.apply _).tupled(tpl)
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
          "flow"
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
