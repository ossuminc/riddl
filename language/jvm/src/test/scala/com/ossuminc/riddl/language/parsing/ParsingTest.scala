/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.{URL, TestingBasisWithTestData}
import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.{Message, Messages}
import com.ossuminc.riddl.language.CommonOptions

import fastparse.*

import java.nio.file.{Path, Files}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.annotation.unused
import scala.reflect.*

/** A helper class for testing the parser */
trait ParsingTest extends TestingBasisWithTestData {

  import com.ossuminc.riddl.language.AST.RiddlValue
  import com.ossuminc.riddl.language.parsing.RiddlParserInput._

  protected val testingOptions: CommonOptions = CommonOptions.empty.copy(maxIncludeWait = 10.seconds)

  case class StringParser(content: String, testCase: String = "unknown test case")
      extends TopLevelParser(RiddlParserInput(content, testCase), testingOptions)

  def parsePath(
    path: Path,
    commonOptions: CommonOptions = CommonOptions.empty
  ): Either[Messages, Root] = {
    if Files.exists(path) then
      if Files.isReadable(path) then {
        val input = rpiFromPath(path)
        TopLevelParser.parseInput(input, commonOptions)
      } else {
        val message: Message = Messages.error(s"Input file `${path.toString} is not readable.")
        Left(List(message))
      }
      end if
    else {
      val message: Message = Messages.error(s"Input file `${path.toString} does not exist.")
      Left(List(message))
    }
    end if
  }

  def parseFile(
    file: java.io.File,
    commonOptions: CommonOptions = CommonOptions.empty
  ): Either[Messages, Root] = {
    parsePath(file.toPath, commonOptions)
  }

  def parseString(
    input: String,
    commonOptions: CommonOptions = CommonOptions.empty,
    origin: Option[URL] = None
  ): Either[Messages, Root] = {
    val spi = StringParserInput(input, origin.getOrElse(URL(s"string(${input.length})")))
    TopLevelParser.parseInput(spi, commonOptions)
  }

  def parse[T <: RiddlValue, U <: RiddlValue](
    input: RiddlParserInput,
    parser: P[?] => P[T],
    extraction: T => U
  ): Either[Messages, (U, RiddlParserInput)] = {
    val tp = TestParser(input)
    tp.parse[T, U](parser, extraction)
  }

  def parseRoot(file: java.io.File): Either[Messages, Root] = {
    val rpi = rpiFromFile(file)
    parseTopLevelDomains(rpi)
  }

  def parseRoot(path: java.nio.file.Path): Either[Messages, Root] = {
    val rpi = rpiFromPath(path)
    parseTopLevelDomains(rpi)
  }

  def parseTopLevelDomains(
    input: RiddlParserInput
  ): Either[Messages, Root] = {
    val tp = TestParser(input)
    tp.parseRoot
  }

  def parseTopLevelDomain[TO <: RiddlValue](
    input: RiddlParserInput,
    extract: Root => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    val tp = TestParser(input)
    tp.parseTopLevelDomain[TO](extract).map(x => (x, input))
  }

  def parseDomainDefinition[TO <: RiddlValue](
    input: RiddlParserInput,
    extract: Domain => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    val tp = TestParser(input)
    tp.parseDomainDefinition[TO](extract)
  }

  def checkDomainDefinitions[TO <: RiddlValue](
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

  def parseContextDefinition[TO <: RiddlValue](
    input: RiddlParserInput,
    extract: Context => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    val tp = TestParser(input)
    tp.parseContextDefinition[TO](extract)
  }

  def parseDefinition[FROM <: Definition: ClassTag, TO <: RiddlValue](
    input: RiddlParserInput,
    extract: FROM => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    val tp = TestParser(input)
    tp.parseDefinition[FROM, TO](extract)
  }

  def parseDefinition[FROM <: Definition: ClassTag, TO <: RiddlValue](
    input: String,
    extract: FROM => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    val tp = TestParser(RiddlParserInput(input))
    tp.parseDefinition[FROM, TO](extract)
  }

  def parseDefinition[FROM <: Definition: ClassTag](
    input: RiddlParserInput
  ): Either[Messages.Messages, (FROM, RiddlParserInput)] = {
    val tp = TestParser(input)
    tp.parseDefinition[FROM]
  }

  def parseDefinition[FROM <: Definition: ClassTag](
    input: String
  ): Either[Messages.Messages, (FROM, RiddlParserInput)] = {
    parseDefinition(RiddlParserInput(input))
  }

  def checkDefinition[FROM <: Definition: ClassTag, TO <: RiddlValue](
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

  def checkDefinitions[FROM <: Definition: ClassTag, TO <: RiddlValue](
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

  def parseInContext[TO <: RiddlValue](
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
    directory: String = "language/jvm/src/test/input/"
  ): Root = {
    val path = java.nio.file.Path.of(directory, fileName)
    val rpi = rpiFromPath(path)
    TopLevelParser.parseInput(rpi) match {
      case Left(errors) =>
        fail(errors.format)
      case Right(root) => root
    }
  }

}
