package com.yoppworks.ossum.riddl.language

import java.io.File

import com.yoppworks.ossum.riddl.language.AST.LiteralString
import com.yoppworks.ossum.riddl.language.AST.Location
import fastparse._

import scala.collection.mutable

case class ParserError(input: RiddlParserInput, loc: Location, msg: String)
    extends Throwable {
  override def toString: String = {
    ""
  }
}

/** Unit Tests For ParsingContext */
trait ParsingContext {

  def throwOnError: Boolean
  def root: File

  protected val stack: InputStack = InputStack()
  protected val errors: mutable.ListBuffer[ParserError] =
    mutable.ListBuffer.empty[ParserError]

  def current: RiddlParserInput = stack.current() match {
    case Some(rpi) => rpi
    case None =>
      throw new RuntimeException("Parse Input Stack Underflow")
  }

  def error(loc: Location, msg: String): Unit = {
    val error = ParserError(current, loc, msg)
    errors.append(error)
    if (throwOnError) {
      throw error
    }
  }

  def location[_: P]: P[Location] = {
    P(Index).map(current.location)
  }

  def doInclude[T](str: LiteralString, empty: T)(rule: P[_] => P[T]): T = {
    val name = str.s + ".riddl"
    val file = new File(root, name)
    if (!file.exists()) {
      error(str.loc, s"File '$name' does not exist, can't be included.")
      empty
    } else {
      stack.push(file)
      val fp = FileParser(file)
      val result = fp.expect[T](rule) match {
        case Left(string) =>
          error(str.loc, string)
          empty
        case Right(result) =>
          result
      }
      stack.pop()
      result
    }
  }
}
