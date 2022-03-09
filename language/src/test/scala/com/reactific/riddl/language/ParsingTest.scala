package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.{ParserError, RiddlParserInput, TopLevelParser}
import fastparse.*
import org.scalatest.matchers.*
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import scala.annotation.unused
import scala.reflect.*

trait ParsingTestBase extends AnyWordSpec with must.Matchers

case class TestParser(input: RiddlParserInput, throwOnError: Boolean = false)
    extends TopLevelParser(input) with must.Matchers {
  stack.push(input)

  def parse[T <: RiddlNode, U <: RiddlNode](
    parser: P[?] => P[T],
    extract: T => U
  ): Either[Seq[ParserError], U] = {
    expect(parser) match {
      case Right(content) => Right(extract(content))
      case Left(errors)   => Left(errors)
    }
  }

  protected def parserFor[T <: Definition: ClassTag]: P[?] => P[T] = {
    val parser: P[?] => P[?] = classTag[T].runtimeClass match {
      case x if x == classOf[AST.Type]        => typeDef(_)
      case x if x == classOf[AST.Domain]      => domain(_)
      case x if x == classOf[AST.Context]     => context(_)
      case x if x == classOf[AST.Entity]      => entity(_)
      case x if x == classOf[AST.Adaptor]     => adaptor(_)
      case x if x == classOf[AST.Invariant]   => invariant(_)
      case x if x == classOf[AST.Function]    => function(_)
      case x if x == classOf[AST.Plant]       => plant(_)
      case x if x == classOf[AST.Processor]   => processor(_)
      case x if x == classOf[AST.Pipe]        => pipeDefinition(_)
      case x if x == classOf[AST.InletJoint]  => joint(_)
      case x if x == classOf[AST.OutletJoint] => joint(_)
      case x if x == classOf[AST.Saga]        => saga(_)
      case _ => throw new RuntimeException(
          s"No parser defined for class ${classTag[T].runtimeClass}"
        )
    }
    parser.asInstanceOf[P[?] => P[T]]
  }

  def parseTopLevelDomains: Either[Seq[ParserError], RootContainer] = {
    expect(fileRoot(_))
  }

  def parseTopLevelDomain[TO <: RiddlNode](
    extract: RootContainer => TO
  ): Either[Seq[ParserError], TO] = {
    expect[RootContainer](fileRoot(_)) match {
      case Right(content) => Right(extract(content))
      case Left(msg)      => Left(msg)
    }
  }

  def parseDefinition[FROM <: Definition: ClassTag, TO <: RiddlNode](
    extract: FROM => TO
  ): Either[Seq[ParserError], TO] = {
    val parser = parserFor[FROM]
    val result = expect[FROM](parser)
    result.map(extract)
  }

  def parseDefinition[
    FROM <: Definition: ClassTag
  ]: Either[Seq[ParserError], FROM] = {
    val parser = parserFor[FROM]
    expect[FROM](parser)
  }

  def parseDomainDefinition[TO <: RiddlNode](
    extract: Domain => TO
  ): Either[Seq[ParserError], TO] = { parse[Domain, TO](domain(_), extract) }

  def parseContextDefinition[TO <: RiddlNode](
    extract: Context => TO
  ): Either[Seq[ParserError], TO] = { parse[Context, TO](context(_), extract) }
}

/** Unit Tests For ParsingTest */
class ParsingTest extends ParsingTestBase {

  def parse[T <: RiddlNode, U <: RiddlNode](
    input: RiddlParserInput,
    parser: P[?] => P[T],
    extraction: T => U
  ): Either[Seq[ParserError], U] = {
    val tp = TestParser(input)
    tp.parse[T, U](parser, extraction)
  }

  def parseTopLevelDomains(
    input: RiddlParserInput
  ): Either[Seq[ParserError], RootContainer] = {
    val tp = TestParser(input)
    tp.parseTopLevelDomains
  }

  def parseTopLevelDomain[TO <: RiddlNode](
    input: RiddlParserInput,
    extract: RootContainer => TO
  ): Either[Seq[ParserError], TO] = {
    val tp = TestParser(input)
    tp.parseTopLevelDomain[TO](extract)
  }

  def parseDomainDefinition[TO <: RiddlNode](
    input: RiddlParserInput,
    extract: Domain => TO
  ): Either[Seq[ParserError], TO] = {
    val tp = TestParser(input)
    tp.parseDomainDefinition[TO](extract)
  }

  def checkDomainDefinitions[TO <: RiddlNode](
    cases: Map[String, TO],
    extract: Domain => TO
  ): Unit = {
    cases foreach { case (statement, expected) =>
      val input = s"domain foo {\n$statement\n}"
      val tp = TestParser(RiddlParserInput(input))
      tp.parseDomainDefinition(extract) match {
        case Right(content) => content mustBe expected
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
      }
    }
  }

  def parseContextDefinition[TO <: RiddlNode](
    input: RiddlParserInput,
    extract: Context => TO
  ): Either[Seq[ParserError], TO] = {
    val tp = TestParser(input)
    tp.parseContextDefinition[TO](extract)
  }

  def parseDefinition[FROM <: Definition: ClassTag, TO <: RiddlNode](
    input: RiddlParserInput,
    extract: FROM => TO
  ): Either[Seq[ParserError], TO] = {
    val tp = TestParser(input)
    tp.parseDefinition[FROM, TO](extract)
  }

  def parseDefinition[FROM <: Definition: ClassTag, TO <: RiddlNode](
    input: String,
    extract: FROM => TO
  ): Either[Seq[ParserError], TO] = {
    val tp = TestParser(RiddlParserInput(input))
    tp.parseDefinition[FROM, TO](extract)
  }

  def parseDefinition[FROM <: Definition: ClassTag](
    input: RiddlParserInput
  ): Either[Seq[ParserError], FROM] = {
    val tp = TestParser(input)
    tp.parseDefinition[FROM]
  }

  def parseDefinition[FROM <: Definition: ClassTag](
    input: String
  ): Either[Seq[ParserError], FROM] = {
    parseDefinition(RiddlParserInput(input))
  }

  def checkDefinition[FROM <: Definition: ClassTag, TO <: RiddlNode](
    input: String,
    expected: TO,
    extract: FROM => TO
  ): Unit = {
    val rip = RiddlParserInput(input)
    TestParser(rip).parseDefinition[FROM, TO](extract) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right(content) => content mustBe expected
    }
  }

  def checkDefinitions[FROM <: Definition: ClassTag, TO <: RiddlNode](
    cases: Map[String, TO],
    @unused
    extract: FROM => TO
  ): Unit = {
    cases.foreach { case (statement: String, expected: TO @unchecked) =>
      val rip = RiddlParserInput(statement)
      TestParser(rip).parseDefinition[FROM] match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe expected
      }
    }
  }

  def parseInContext[TO <: RiddlNode](
    input: RiddlParserInput,
    extract: Context => TO
  ): Either[Seq[ParserError], TO] = {
    val tp = TestParser(RiddlParserInput(s"context foo is {\n${input.data}\n}"))
    tp.parseContextDefinition[TO](extract)
  }

  def checkContextDefinitions[TO <: RiddlNode](
    cases: Map[String, TO],
    extract: Context => TO
  ): Unit = {
    cases.foreach { case (statement: String, expected: TO @unchecked) =>
      val input = s"context foo is {\n$statement\n}"
      val tp = TestParser(RiddlParserInput(input))
      tp.parseContextDefinition(extract) match {
        case Right(content) => content mustBe expected
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
      }
    }
  }

  def checkFile(
    @unused
    label: String,
    fileName: String,
    directory: String = "language/src/test/input/"
  ): RootContainer = {
    val file = new File(directory + fileName)
    TopLevelParser.parse(file) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right(model) => model
    }
  }
}
