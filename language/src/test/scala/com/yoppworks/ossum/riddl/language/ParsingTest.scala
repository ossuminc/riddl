package com.yoppworks.ossum.riddl.language

import java.io.File

import AST.Definition
import AST.RiddlNode
import AST._
import fastparse._
import org.scalatest.MustMatchers
import org.scalatest.WordSpec

import scala.reflect._

case class TestParser(input: RiddlParserInput, throwOnError: Boolean = false)
    extends TopLevelParser(input)
    with MustMatchers {
  stack.push(input)

  def parse[T <: RiddlNode, U <: RiddlNode](
    parser: P[_] => P[T],
    extract: T => U
  ): Either[Seq[ParserError], U] = {
    expect(parser) match {
      case Right(content) =>
        Right(extract(content))
      case Left(errors) =>
        Left(errors)
    }
  }

  protected def parserFor[T <: Definition: ClassTag]: P[_] => P[T] = {
    val parser: P[_] => P[_] = classTag[T].runtimeClass match {
      case x if x == classOf[AST.Type]        => typeDef(_)
      case x if x == classOf[AST.Domain]      => domain(_)
      case x if x == classOf[AST.Context]     => context(_)
      case x if x == classOf[AST.Interaction] => interaction(_)
      case x if x == classOf[AST.Feature]     => feature(_)
      case x if x == classOf[AST.Entity]      => entity(_)
      case x if x == classOf[AST.Adaptor]     => adaptor(_)
      case x if x == classOf[AST.Topic]       => topic(_)
      case _                                  => domain(_)
    }
    parser.asInstanceOf[P[_] => P[T]]
  }

  def parseTopLevelDomains: Either[Seq[ParserError], RootContainer] = {
    expect(fileRoot(_))
  }

  def parseTopLevelDomain[TO <: RiddlNode](
    extract: RootContainer => TO
  ): Either[Seq[ParserError], TO] = {
    expect[RootContainer](fileRoot(_)) match {
      case Right(content) =>
        Right(extract(content))
      case Left(msg) =>
        Left(msg)
    }
  }

  def parseDefinition[FROM <: Definition: ClassTag, TO <: RiddlNode](
    extract: FROM => TO
  ): Either[Seq[ParserError], TO] = {
    val parser = parserFor[FROM]
    val result = expect[FROM](parser)
    result.map(extract)
  }

  def parseDefinition[FROM <: Definition: ClassTag]
    : Either[Seq[ParserError], FROM] = {
    val parser = parserFor[FROM]
    expect[FROM](parser)
  }

  def parseDomainDefinition[TO <: RiddlNode](
    extract: Domain => TO
  ): Either[Seq[ParserError], TO] = {
    parse[Domain, TO](domain(_), extract)
  }

  def parseContextDefinition[TO <: RiddlNode](
    extract: Context => TO
  ): Either[Seq[ParserError], TO] = {
    parse[Context, TO](context(_), extract)
  }
}

/** Unit Tests For ParsingTest */
class ParsingTest extends WordSpec with MustMatchers {

  def parse[T <: RiddlNode, U <: RiddlNode](
    input: RiddlParserInput,
    parser: P[_] => P[T],
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
    cases foreach {
      case (statement, expected) =>
        val input = s"domain foo {\n$statement\n}"
        val tp = TestParser(RiddlParserInput(input))
        tp.parseDomainDefinition(extract) match {
          case Right(content) =>
            content mustBe expected
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
    val tp = TestParser(RiddlParserInput(input))
    tp.parseDefinition[FROM]
  }

  def checkDefinition[FROM <: Definition: ClassTag, TO <: RiddlNode](
    input: String,
    expected: TO,
    extract: FROM => TO
  ): Unit = {
    val rip = RiddlParserInput(input)
    TestParser(rip).parseDefinition[FROM] match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right(content) =>
        content mustBe expected
    }
  }

  def checkDefinitions[FROM <: Definition: ClassTag, TO <: RiddlNode](
    cases: Map[String, TO],
    extract: FROM => TO
  ): Unit = {
    cases.foreach {
      case (statement: String, expected: TO @unchecked) =>
        val rip = RiddlParserInput(statement)
        TestParser(rip).parseDefinition[FROM] match {
          case Left(errors) =>
            val msg = errors.map(_.format).mkString
            fail(msg)
          case Right(content) =>
            content mustBe expected
        }
    }
  }

  def parseInContext[TO <: RiddlNode](
    input: RiddlParserInput,
    extract: Context => TO
  ): Either[Seq[ParserError], TO] = {
    val tp = TestParser(
      RiddlParserInput(s"context foo {\n${input.data}\n}")
    )
    tp.parseContextDefinition[TO](extract)
  }

  def checkContextDefinitions[TO <: RiddlNode](
    cases: Map[String, TO],
    extract: Context => TO
  ): Unit = {
    cases.foreach {
      case (statement: String, expected: TO @unchecked) =>
        val input = s"context foo {\n$statement\n}"
        val tp = TestParser(RiddlParserInput(input))
        tp.parseContextDefinition(extract) match {
          case Right(content) =>
            content mustBe expected
          case Left(errors) =>
            val msg = errors.map(_.format).mkString
            fail(msg)
        }
    }
  }

  def checkFile(label: String, fileName: String): RootContainer = {
    val directory = "language/src/test/input/"
    val file = new File(directory + fileName)
    TopLevelParser.parse(file) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right(model) =>
        model
    }
  }
}
