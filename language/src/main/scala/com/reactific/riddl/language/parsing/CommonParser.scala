/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.Location
import fastparse.*
import fastparse.ScalaWhitespace.*

import java.net.URL
import java.nio.file.Files

/** Common Parsing Rules */
trait CommonParser extends Terminals with NoWhiteSpaceParsers {

  def include[K <: Definition, u: P](
    parser: P[?] => P[Seq[K]]
  ): P[Include[K]] = {
    P(Keywords.include ~/ literalString).map { str: LiteralString =>
      doInclude[K](str)(parser)
    }
  }

  def importDef[u: P]: P[DomainDefinition] = {
    P(
      location ~ Keywords.import_ ~ Keywords.domain ~ identifier ~
        Readability.from ~ literalString
    ).map { tuple => doImport(tuple._1, tuple._2, tuple._3) }
  }

  def optionalNestedContent[u: P, T](parser: => P[T]): P[Seq[T]] = {
    P(open ~ parser.rep(0) ~ close)
  }

  def undefined[u: P, RT](f: => RT): P[RT] = {
    P(Punctuation.undefinedMark./).map(_ => f)
  }

  def literalStrings[u: P]: P[Seq[LiteralString]] = { P(literalString.rep(1)) }

  def markdownLines[u: P]: P[Seq[LiteralString]] = { P(markdownLine.rep(1)) }

  def as[u: P]: P[Unit] = { P(StringIn(Readability.as, Readability.by).?) }

  def maybe[u: P](keyword: String): P[Unit] = P(keyword).?

  def docBlock[u: P]: P[Seq[LiteralString]] = {
    P(
      (open ~
        (markdownLines | literalStrings | undefined(Seq.empty[LiteralString])) ~
        close) | literalString.map(Seq(_))
    )
  }

  def blockDescription[u: P]: P[BlockDescription] = {
    P(location ~ docBlock).map(tpl => BlockDescription(tpl._1, tpl._2))
  }

  def fileDescription[u: P]: P[FileDescription] = {
    P(location ~ Keywords.file ~ literalString).map { tpl =>
      val path = current.root.toPath.resolve(tpl._2.s)
      if (Files.isReadable(path) && Files.isRegularFile(path)) {
        FileDescription(tpl._1, path)
      } else {
        error(tpl._1, s"Description file cannot be read: $path ", "")
        FileDescription(tpl._1, path)
      }
    }
  }

  def briefly[u: P]: P[Option[LiteralString]] = {
    P(StringIn(Keywords.brief, Keywords.briefly) ~ literalString).?
  }

  def description[u: P]: P[Option[Description]] = P(
    StringIn(Keywords.described, Keywords.explained) ~/
      ((as ~ blockDescription) | (Readability.in ~ fileDescription))
  ).?

  def wholeNumber[u: P]: P[Long] = { CharIn("0-9").rep(1).!.map(_.toLong) }

  def integer[u: P]: P[Long] = {
    StringIn(Operators.plus, Operators.minus).? ~ wholeNumber
  }

  def literalInteger[u: P]: P[LiteralInteger] = {
    P(location ~ integer).map(s => LiteralInteger(s._1, BigInt(s._2)))
  }

  def literalDecimal[u: P]: P[LiteralDecimal] = {
    P(
      location ~ StringIn(Operators.plus, Operators.minus).?.! ~ CharIn("0-9")
        .rep(1).! ~ Punctuation.dot.! ~ CharIn("0-9").rep(0).?.! ~
        ("E" ~ CharIn("+\\-") ~ CharIn("0-9").rep(min = 1, max = 3)).?.!
    ).map { case (loc, a, b, c, d, e) =>
      LiteralDecimal(loc, BigDecimal(a + b + c + d + e))
    }
  }

  def simpleIdentifier[u: P]: P[String] = {
    P((CharIn("a-zA-Z") ~~ CharsWhileIn("a-zA-Z0-9_").?).!)
  }

  def quotedIdentifier[u: P]: P[String] = {
    P("'" ~ CharsWhileIn("a-zA-Z0-9_+\\-|/@$%&, :", 1).! ~ "'")
  }

  def anyIdentifier[u: P]: P[String] = {
    P(simpleIdentifier | quotedIdentifier)
  }

  def identifier[u: P]: P[Identifier] = {
    P(location ~ anyIdentifier).map(tpl => (Identifier.apply _).tupled(tpl))
  }

  def pathIdentifier[u: P]: P[PathIdentifier] = {
    P(location ~ (anyIdentifier | "^".! | Punctuation.dot.!).repX(1)).map {
      case (loc, strings) =>
        // PathIdentifiers have empty strings to mean go up one level,
        // and do not need a dot to indicate descent into a new definition
        // because concatenation is sufficient.
        val mappedStrings = strings.flatMap {
          case s: String if s == "^" => Seq("")
          case s: String if s == "." => Seq.empty[String]
          case s: String             => Seq(s)
        }
        PathIdentifier(loc, mappedStrings)
    }
  }

  def is[u: P]: P[Unit] = {
    P(StringIn(
      Readability.is,
      Readability.are,
      Punctuation.colon,
      Punctuation.equalsSign
    )).?
  }

  def by[u: P]: P[Unit] = { P(StringIn(Readability.by, Readability.from)).? }

  def open[u: P]: P[Unit] = { P(Punctuation.curlyOpen) }

  def close[u: P]: P[Unit] = { P(Punctuation.curlyClose) }

  def maybeOptionWithArgs[u: P](
    validOptions: => P[String]
  ): P[(Location, String, Seq[LiteralString])] = {
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
  )(mapper: => (Location, String, Seq[LiteralString]) => TY
  ): P[Seq[TY]] = {
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

  def mapTo[T <: Definition](seq: Option[Seq[Definition]]): Seq[T] = {
    seq.fold(Seq.empty[T])(_.map(_.asInstanceOf[T]))
  }

  def hostString[u: P]: P[String] = {
    P(CharsWhile { ch => ch.isLetterOrDigit || ch == '-' }.rep(1, ".", 32)).!
  }

  def portNum[u: P]: P[String] = {
    P(CharsWhileIn("0-9").rep(min = 1, max = 5)).!
  }

  def urlPath[u: P]: P[String] = {
    P(
      CharsWhile(ch => ch.isLetterOrDigit || "/-?#/.=".contains(ch))
        .rep(min = 0, max = 240)
    ).!
  }

  def httpUrl[u: P]: P[java.net.URL] = {
    P("http" ~ "s".? ~ "://" ~ hostString ~ (":" ~ portNum).? ~ "/" ~ urlPath).!
      .map(new URL(_))
  }

  def term[u: P]: P[Term] = {
    P(location ~ Keywords.term ~ identifier ~ is ~ briefly ~ description)
      .map(tpl => (Term.apply _).tupled(tpl))
  }

}
