/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.{AST, CommonOptions}
import com.ossuminc.riddl.utils.{TestingBasisWithTestData, URL}
import fastparse.*

import java.nio.file.{Files, Path}
import scala.annotation.unused
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.reflect.*

/** A helper class for testing the parser */
trait ParsingTest extends TestingBasisWithTestData {

  import com.ossuminc.riddl.language.AST.RiddlValue
  import com.ossuminc.riddl.language.parsing.RiddlParserInput.*

  protected val testingOptions: CommonOptions = CommonOptions.empty.copy(maxIncludeWait = 10.seconds)

  case class StringParser(content: String, testCase: String = "unknown test case")
      extends TopLevelParser(RiddlParserInput(content, testCase), testingOptions)

  def parsePath(
    path: Path,
    commonOptions: CommonOptions = CommonOptions.empty
  ): Either[Messages, Root] = {
    if Files.exists(path) then
      if Files.isReadable(path) then {
        val input = RiddlParserInput.fromCwdPath(path)
        TopLevelParser.parseInput(input, commonOptions)
      } else {
        val message: Message = error(s"Input file `${path.toString} is not readable.")
        Left(List(message))
      }
      end if
    else {
      val message: Message = error(s"Input file `${path.toString} does not exist.")
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

  def parse[T <: RiddlValue, U <: RiddlValue](
    input: RiddlParserInput,
    parser: P[?] => P[T],
    extraction: T => U
  ): Either[Messages, (U, RiddlParserInput)] = {
    val tp = TestParser(input)
    tp.parse[T, U](parser, extraction)
  }

  def parseRoot(path: java.nio.file.Path): Either[Messages, Root] = {
    val rpi = RiddlParserInput.fromCwdPath(path)
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
      val tp = TestParser(RiddlParserInput(input,"checkDomainDefinitions"))
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
    val tp = TestParser(RiddlParserInput(input, "parseDefinition[FROM,TO]"))
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
    parseDefinition(RiddlParserInput(input, "parseDefinition[FROM]"))
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
      val rip = RiddlParserInput(statement, "checkDefinitions")
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
    val rpi = RiddlParserInput(s"context foo is {\n${input.data}\n}", "parseInContext")
    val tp = TestParser(rpi)
    tp.parseContextDefinition[TO](extract)
  }

  val defaultInputDir = "language/jvm/src/test/input"
  def checkFile(
    @unused label: String,
    fileName: String,
    directory: String = defaultInputDir
  ): (Root, RiddlParserInput) = {
    val path = java.nio.file.Path.of(directory, fileName)
    val rpi = fromCwdPath(path)
    TopLevelParser.parseInput(rpi) match {
      case Left(errors) =>
        fail(errors.format)
      case Right(root) => root -> rpi
    }
  }

}
