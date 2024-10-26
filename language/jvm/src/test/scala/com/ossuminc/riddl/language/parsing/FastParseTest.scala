package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.utils.*

import fastparse.Parsed.{Failure, Success}
import fastparse.*
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class FastParseTest(using PlatformContext) extends ParsingContext with AbstractTestingBasisWithTestData {

  given io: PlatformContext = JVMPlatformContext()

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def validateResult[RESULT](result: Either[Messages, RESULT], input: RiddlParserInput, index: Int): RESULT = {
    result match {
      case Left(messages)        => fail(messages.format)
      case Right(result: RESULT) => result
    }
  }

  def testRule[RESULT](
    rpi: RiddlParserInput,
    rule: P[?] => P[RESULT]
  ): RESULT = {
    try {
      fastparse.parse[RESULT](rpi, rule(_), true) match {
        case Success(root, index) =>
          if messagesNonEmpty then validateResult(Left(messagesAsList), rpi, index)
          else validateResult(Right(root), rpi, index)
          end if
        case failure: Failure =>
          makeParseFailureError(failure, rpi)
          validateResult(Left(messagesAsList), rpi, 0)
      }
    } catch {
      case NonFatal(exception) =>
        makeParseFailureError(exception, At((0, 0), rpi))
        validateResult(Left(messagesAsList), rpi, 0)
    }
  }
}
