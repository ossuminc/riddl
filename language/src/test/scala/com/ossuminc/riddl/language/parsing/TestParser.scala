package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.{Context, Definition, Domain, RiddlValue, Root}
import com.ossuminc.riddl.language.Messages.Messages
import fastparse.P
import org.scalatest.matchers.must.Matchers

import scala.reflect.{ClassTag, classTag}

case class TestParser(input: RiddlParserInput, throwOnError: Boolean = false)
    extends TopLevelParser(input)
    with Matchers {
  push(input)

  def parse[T <: RiddlValue, U <: RiddlValue](
    parser: P[?] => P[T],
    extract: T => U
  ): Either[Messages, (U, RiddlParserInput)] = {
    expect[T](parser).map(x => extract(x._1) -> x._2)
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
  ): Either[Messages, (TO, RiddlParserInput)] = {
    parseRoot(withVerboseFailures = true).map { (root: Root) => extract(root) -> current }
  }

  def parseDefinition[FROM <: Definition: ClassTag, TO <: RiddlValue](
    extract: FROM => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    val parser = parserFor[FROM]
    val result = expect[FROM](parser)
    result.map(x => extract(x._1) -> x._2)
  }

  def parseDefinition[
    FROM <: Definition: ClassTag
  ]: Either[Messages, (FROM, RiddlParserInput)] = {
    val parser = parserFor[FROM]
    expect[FROM](parser)
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
