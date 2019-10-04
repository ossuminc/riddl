package com.yoppworks.ossum.riddl.parser

import java.io.File

import fastparse.Parsed.Failure
import fastparse.Parsed.Success

import scala.io.Source
import AST._
import fastparse._
import ScalaWhitespace._
import CommonParser._
import TypesParser._
import InteractionParser._
import ContextParser._

/** Top level parsing rules */
object TopLevelParser {

  def annotated_input(input: String, index: Int): String = {
    input.substring(0, index) + "^" + input.substring(index)
  }

  def parseString[T](
    input: String,
    parser: P[_] ⇒ P[T]
  ): Either[String, T] = {
    fastparse.parse(input, parser(_)) match {
      case Success(content, _) ⇒
        Right(content)
      case failure @ Failure(_, index, _) ⇒
        val marked_up = annotated_input(input, index)
        val trace = failure.trace()
        Left(s"""Parse of '$marked_up' failed at position $index"
                |${trace.longAggregateMsg}
                |""".stripMargin)
    }
  }

  def parseFile[T](file: File, parser: P[_] ⇒ P[T]): Either[String, T] = {
    val source = Source.fromFile(file)
    parseSource(source, file.getPath, parser)
  }

  def parseSource[T](
    source: Source,
    name: String,
    parser: P[_] => P[T]
  ): Either[String, T] = {
    val lines = source.getLines()
    val input = lines.mkString("\n")
    fastparse.parse(input, parser(_)) match {
      case Success(content, _) =>
        Right(content)
      case failure @ Failure(label, index, _) ⇒
        val where = s"at $name:$index"
        val marked_up = annotated_input(input, index)
        val trace = failure.trace()
        Left(s"""Parse of '$marked_up' failed at position $where"
                |${trace.longAggregateMsg}
                |""".stripMargin)
    }
  }
}
