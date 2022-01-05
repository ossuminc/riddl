package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import fastparse.*
import fastparse.Parsed.Failure
import fastparse.Parsed.Success

import java.io.File
import scala.annotation.unused
import scala.collection.mutable

case class ParserError(input: RiddlParserInput, loc: Location, msg: String) extends Throwable {

  def format: String = {
    val context = input.annotateErrorLine(loc)
    s"${input.origin}$loc: $msg\n$context"
  }
}

/** Unit Tests For ParsingContext */
trait ParsingContext {

  def root: File = new File(System.getProperty("user.dir"))

  protected val stack: InputStack = InputStack()

  protected val errors: mutable.ListBuffer[ParserError] = mutable.ListBuffer.empty[ParserError]

  def current: RiddlParserInput = {
    stack.current match {
      case Some(rpi) => rpi
      case None      => throw new RuntimeException("Parse Input Stack Underflow")
    }
  }

  def location[u: P]: P[Location] = {
    val cur = current
    P(Index).map(idx => cur.location(idx, cur.origin))
  }

  def doImport(loc: Location, domainRef: DomainRef, fileName: LiteralString): Domain = {
    val name = fileName.s + ".bast"
    val file = new File(current.root, name)
    if (!file.exists()) {
      error(fileName.loc, s"File '$name` does not exist, can't be imported.")
      Domain(loc, Identifier(domainRef.loc, domainRef.id.value.last)) // FIXME: id is wrong?
    } else { importDomain(file) }
  }

  def importDomain(
    @unused
    file: File
  ): Domain = {
    // TODO: implement importDomain
    ???
  }

  def doInclude[T](str: LiteralString, empty: T)(rule: P[?] => P[T]): T = {
    val name = str.s + ".riddl"
    val file = new File(current.root, name)
    if (!file.exists()) {
      error(str.loc, s"File '$name' does not exist, can't be included.")
      empty
    } else {
      stack.push(file)
      val result = this.expect[T](rule) match {
        case Left(_)       => empty
        case Right(result) => result
      }
      stack.pop
      result
    }
  }

  def error(loc: Location, msg: String): Unit = {
    val error = ParserError(current, loc, msg)
    errors.append(error)
  }

  def expect[T](parser: P[?] => P[T]): Either[Seq[ParserError], T] = {
    fastparse.parse(current, parser(_)) match {
      case Success(content, _) =>
        if (errors.nonEmpty) { Left(errors.toSeq) }
        else { Right(content) }
      case failure @ Failure(_, index, _) =>
        val trace = failure.trace()
        val msg = s"""Parse failed:
                     |${trace.longAggregateMsg}""".stripMargin
        error(current.location(index), msg)
        Left(errors.toSeq)
      case _ => throw new IllegalStateException("Parsed[T] should have matched Success or Failure")

    }
  }
}
