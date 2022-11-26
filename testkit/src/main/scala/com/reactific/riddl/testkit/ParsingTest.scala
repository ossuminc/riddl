/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.testkit

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.AST
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.parsing.TopLevelParser
import fastparse.*
import org.scalatest.matchers.must.Matchers

import java.io.File
import scala.annotation.unused
import scala.reflect.*

object TestParser {}
case class TestParser(input: RiddlParserInput, throwOnError: Boolean = false)
    extends TopLevelParser(input) with Matchers {
  push(input)

  def parse[T <: RiddlNode, U <: RiddlNode](
    parser: P[?] => P[T],
    extract: T => U
  ): Either[Messages, (U, RiddlParserInput)] = {
    expect(parser).map(x => extract(x._1) -> x._2)
  }

  def parserFor[T <: RiddlValue: ClassTag]: P[?] => P[T] = {
    val parser: P[?] => P[?] = classTag[T].runtimeClass match {
      case x if x == classOf[AST.Type]        => typeDef(_)
      case x if x == classOf[AST.Domain]      => domain(_)
      case x if x == classOf[AST.Context]     => context(_)
      case x if x == classOf[AST.Entity]      => entity(_)
      case x if x == classOf[AST.Adaptor]     => adaptor(_)
      case x if x == classOf[AST.Application] => application(_)
      case x if x == classOf[AST.Invariant]   => invariant(_)
      case x if x == classOf[AST.Function]    => function(_)
      case x if x == classOf[AST.Plant]       => plant(_)
      case x if x == classOf[AST.Processor]   => processor(_)
      case x if x == classOf[AST.Projection]  => projection(_)
      case x if x == classOf[AST.Pipe]        => pipe(_)
      case x if x == classOf[AST.InletJoint]  => joint(_)
      case x if x == classOf[AST.OutletJoint] => joint(_)
      case x if x == classOf[AST.Saga]        => saga(_)
      case x if x == classOf[AST.Example]     => example(_)
      case x if x == classOf[AST.Story]       => story(_)
      case _ => throw new RuntimeException(
          s"No parser defined for class ${classTag[T].runtimeClass}"
        )
    }
    parser.asInstanceOf[P[?] => P[T]]
  }

  def parseTopLevelDomains: Either[Messages, RootContainer] = {
    expect(fileRoot(_)).map(_._1)
  }

  def parseTopLevelDomain[TO <: RiddlNode](
    extract: RootContainer => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    expect[RootContainer](fileRoot(_)).map(x => extract(x._1) -> x._2)
  }

  def parseDefinition[
    FROM <: Definition: ClassTag
  ]: Either[Messages, (FROM, RiddlParserInput)] = {
    val parser = parserFor[FROM]
    expect[FROM](parser)
  }

  def parseDefinition[FROM <: Definition: ClassTag, TO <: RiddlNode](
    extract: FROM => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    val result = parseDefinition[FROM]
    result.map(x => extract(x._1) -> x._2)
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
    tp.parseTopLevelDomains
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
    parseDefinition[FROM, TO](RiddlParserInput(input), extract)
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
    directory: String = "testkit/src/test/input/"
  ): RootContainer = {
    val file = new File(directory + fileName)
    val rpi = RiddlParserInput(file)
    TopLevelParser.parse(rpi) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString("\n")
        fail(msg)
      case Right(rc) => rc
    }
  }
}
