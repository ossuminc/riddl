package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import fastparse.*
import fastparse.Parsed.{Failure, Success}
import org.scalatest.matchers.must.Matchers

import scala.concurrent.ExecutionContext
import scala.reflect.{ClassTag, classTag}
import scala.util.control.NonFatal

case class TestParser(override val input: RiddlParserInput, throwOnError: Boolean = false)(implicit
  ec: ExecutionContext
) extends TopLevelParser(input)(ec)
    with Matchers {

  def expect[CT <: RiddlValue](
    parser: P[?] => P[CT],
    withVerboseFailures: Boolean = false
  ): Either[Messages, CT] = {
    try {
      fastparse.parse[CT](input, parser(_), withVerboseFailures) match {
        case Success(content: CT, _) =>
          if messagesNonEmpty then Left(messagesAsList)
          else Right(content)
        case failure: Failure =>
          makeParseFailureError(failure, input)
          Left(messagesAsList)
      }
    } catch {
      case NonFatal(exception) =>
        makeParseFailureError(exception)
        Left(messagesAsList)
    }
  }

  def parse[T <: RiddlValue, U <: RiddlValue](
    parser: P[?] => P[T],
    extract: T => U
  ): Either[Messages, (U, RiddlParserInput)] = {
    expect[T](parser).map(x => extract(x) -> input)
  }

  protected def parserFor[T <: Definition: ClassTag]: P[?] => P[T] = {
    val parser: P[?] => P[?] = classTag[T].runtimeClass match {
      case x if x == classOf[AST.Type]       => typeDef(_)
      case x if x == classOf[AST.Domain]     => domain(_)
      case x if x == classOf[AST.Context]    => context(_)
      case x if x == classOf[AST.Entity]     => entity(_)
      case x if x == classOf[AST.Adaptor]    => adaptor(_)
      case x if x == classOf[AST.Invariant]  => invariant(_)
      case x if x == classOf[AST.Function]   => function(_)
      case x if x == classOf[AST.Streamlet]  => streamlet(_)
      case x if x == classOf[AST.Saga]       => saga(_)
      case x if x == classOf[AST.Repository] => repository(_)
      case x if x == classOf[AST.Projector]  => projector(_)
      case x if x == classOf[AST.Epic]       => epic(_)
      case x if x == classOf[AST.Connector]  => connector(_)
      case _ =>
        throw new RuntimeException(
          s"No parser defined for ${classTag[T].runtimeClass}"
        )
    }
    parser.asInstanceOf[P[?] => P[T]]
  }

  def parseRoot: Either[Messages, Root] = {
    parseRoot(withVerboseFailures = true)
  }

  def parseTopLevelDomains: Either[Messages, Root] = {
    parseRoot(withVerboseFailures = true)
  }

  def parseTopLevelDomain[TO <: RiddlValue](
    extract: Root => TO
  ): Either[Messages, TO] = {
    parseRoot(withVerboseFailures = true).map { (root: Root) => extract(root) }
  }

  def parseDefinition[FROM <: Definition: ClassTag, TO <: RiddlValue](
    extract: FROM => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    val parser = parserFor[FROM]
    val result = expect[FROM](parser)
    result.map(x => extract(x) -> input)
  }

  def parseDefinition[
    FROM <: Definition: ClassTag
  ]: Either[Messages, (FROM, RiddlParserInput)] = {
    val parser = parserFor[FROM]
    expect[FROM](parser).map(ct => ct -> input)
  }

  def parseDomainDefinition[TO <: RiddlValue](
    extract: Domain => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    parse[Domain, TO](domain(_), extract)
  }

  def parseContextDefinition[TO <: RiddlValue](
    extract: Context => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    parse[Context, TO](context(_), extract)
  }
}
