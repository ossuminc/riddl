/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.parsing.TopLevelParser
import fastparse.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import scala.annotation.unused
import scala.reflect.*

trait ParsingTestBase extends AnyWordSpec with Matchers {
  case class StringParser(content: String) extends TopLevelParser(RiddlParserInput(content))
}

case class TestParser(input: RiddlParserInput, throwOnError: Boolean = false)
    extends TopLevelParser(input)
    with Matchers {
  push(input)

  def parse[T <: RiddlNode, U <: RiddlNode](
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
      case _ =>
        throw new RuntimeException(
          s"No parser defined for class ${classTag[T].runtimeClass}"
        )
    }
    parser.asInstanceOf[P[?] => P[T]]
  }

  def parseRootContainer: Either[Messages, RootContainer] = {
    parseRootContainer(withVerboseFailures = true)
  }

  def parseTopLevelDomain[TO <: RiddlNode](
    extract: RootContainer => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    parseRootContainer(withVerboseFailures = true).map { case root: RootContainer =>
      extract(root) -> current
    }
  }

  def parseDefinition[FROM <: Definition: ClassTag, TO <: RiddlNode](
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

  def parseDomainDefinition[TO <: RiddlNode](
    extract: Domain => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    parse[Domain, TO](domain(_), extract)
  }

  def parseContextDefinition[TO <: RiddlNode](
    extract: Context => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    parse[Context, TO](context(_), extract)
  }
}

/** Unit Tests For ParsingTest */
class ParsingTest extends ParsingTestBase {

  def parse[T <: RiddlNode, U <: RiddlNode](
    input: RiddlParserInput,
    parser: P[?] => P[T],
    extraction: T => U
  ): Either[Messages, (U, RiddlParserInput)] = {
    val tp = TestParser(input)
    tp.parse[T, U](parser, extraction)
  }

  def parseTopLevelDomains(
    input: RiddlParserInput
  ): Either[Messages, RootContainer] = {
    val tp = TestParser(input)
    tp.parseRootContainer
  }

  def parseTopLevelDomain[TO <: RiddlNode](
    input: RiddlParserInput,
    extract: RootContainer => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    val tp = TestParser(input)
    tp.parseTopLevelDomain[TO](extract)
  }

  def parseDomainDefinition[TO <: RiddlNode](
    input: RiddlParserInput,
    extract: Domain => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
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
  ): Either[Messages, (TO, RiddlParserInput)] = {
    val tp = TestParser(input)
    tp.parseContextDefinition[TO](extract)
  }

  def parseDefinition[FROM <: Definition: ClassTag, TO <: RiddlNode](
    input: RiddlParserInput,
    extract: FROM => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    val tp = TestParser(input)
    tp.parseDefinition[FROM, TO](extract)
  }

  def parseDefinition[FROM <: Definition: ClassTag, TO <: RiddlNode](
    input: String,
    extract: FROM => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    val tp = TestParser(RiddlParserInput(input))
    tp.parseDefinition[FROM, TO](extract)
  }

  def parseDefinition[FROM <: Definition: ClassTag](
    input: RiddlParserInput
  ): Either[Messages, (FROM, RiddlParserInput)] = {
    val tp = TestParser(input)
    tp.parseDefinition[FROM]
  }

  def parseDefinition[FROM <: Definition: ClassTag](
    input: String
  ): Either[Messages, (FROM, RiddlParserInput)] = {
    parseDefinition(RiddlParserInput(input))
  }

  def checkDefinition[FROM <: Definition: ClassTag, TO <: RiddlNode](
    rip: RiddlParserInput,
    expected: TO,
    extract: FROM => TO
  ): Unit = {
    TestParser(rip).parseDefinition[FROM, TO](extract) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right((content, _)) => content mustBe expected
    }
  }

  def checkDefinitions[FROM <: Definition: ClassTag, TO <: RiddlNode](
    cases: Map[String, TO],
    @unused extract: FROM => TO
  ): Unit = {
    cases.foreach { case (statement: String, expected: TO @unchecked) =>
      val rip = RiddlParserInput(statement)
      TestParser(rip).parseDefinition[FROM] match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, _)) => content mustBe expected
      }
    }
  }

  def parseInContext[TO <: RiddlNode](
    input: RiddlParserInput,
    extract: Context => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    val rpi = RiddlParserInput(s"context foo is {\n${input.data}\n}")
    val tp = TestParser(rpi)
    tp.parseContextDefinition[TO](extract)
  }

  def checkFile(
    @unused label: String,
    fileName: String,
    directory: String = "language/src/test/input/"
  ): RootContainer = {
    val file = new File(directory + fileName)
    val rpi = RiddlParserInput(file)
    TopLevelParser.parse(rpi) match {
      case Left(errors) =>
        fail(errors.format)
      case Right(root) => root
    }
  }

}
