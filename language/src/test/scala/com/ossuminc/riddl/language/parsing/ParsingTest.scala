/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.CommonOptions
import fastparse.{P, *}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.file.Path

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.annotation.unused
import scala.reflect.*

/** A helper class for testing the parser */
trait ParsingTest extends AnyWordSpec with Matchers {

  protected val testingOptions: CommonOptions = CommonOptions.empty.copy(maxIncludeWait = 10.seconds)

  case class StringParser(content: String) extends TopLevelParser(RiddlParserInput(content), testingOptions)

  def parse[T <: RiddlValue, U <: RiddlValue](
    input: RiddlParserInput,
    parser: P[?] => P[T],
    extraction: T => U
  ): Either[Messages, (U, RiddlParserInput)] = {
    val tp = TestParser(input)
    tp.parse[T, U](parser, extraction)
  }

  def parseRoot(file: File): Either[Messages, Root] = {
    val rpi = RiddlParserInput(file)
    parseTopLevelDomains(rpi)
  }

  def parseRoot(path: Path): Either[Messages, Root] = {
    val rpi = RiddlParserInput(path)
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
  ): Either[Messages, (FROM, RiddlParserInput)] = {
    val tp = TestParser(input)
    tp.parseDefinition[FROM]
  }

  def parseDefinition[FROM <: Definition: ClassTag](
    input: String
  ): Either[Messages, (FROM, RiddlParserInput)] = {
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
    directory: String = "language/src/test/input/"
  ): Root = {
    val file = new File(directory + fileName)
    val rpi = RiddlParserInput(file)
    TopLevelParser.parseInput(rpi) match {
      case Left(errors) =>
        fail(errors.format)
      case Right(root) => root
    }
  }

}
