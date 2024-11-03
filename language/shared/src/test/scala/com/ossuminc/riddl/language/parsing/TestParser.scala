/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext}
import fastparse.*
import fastparse.Parsed.{Failure, Success}
import org.scalatest.matchers.must.Matchers

import scala.concurrent.ExecutionContext
import scala.reflect.{ClassTag, classTag}
import scala.util.control.NonFatal

case class TestParser(
  input: RiddlParserInput,
  throwOnError: Boolean = false,
  withVerboseFailures: Boolean = true
)(using PlatformContext)
    extends ExtensibleTopLevelParser
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

  def parseTopLevelDomains: Either[Messages, Root] = {
    parseRoot
  }

  def parseTopLevelDomain[TO <: RiddlValue](
    extract: Root => TO
  ): Either[Messages, TO] = {
    parseRoot.map { (root: Root) => extract(root) }
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
    parse[Domain, TO](parserFor[Domain], extract)
  }

  def parseContextDefinition[TO <: RiddlValue](
    extract: Context => TO
  ): Either[Messages, (TO, RiddlParserInput)] = {
    parse[Context, TO](parserFor[Context], extract)
  }
}
