/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext, Timer, URL}
import fastparse.*
import fastparse.MultiLineWhitespace.*

import java.nio.file.{Files, Path}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.{ClassTag, classTag}
import scala.scalajs.js.annotation.*

/** An extensible version of the Top Level Parser. */

trait ExtensibleTopLevelParser(using PlatformContext)
    extends ProcessorParser
    with DomainParser
    with AdaptorParser
    with ApplicationParser
    with ContextParser
    with EntityParser
    with EpicParser
    with FunctionParser
    with ModuleParser
    with NebulaParser
    with ProjectorParser
    with RepositoryParser
    with RootParser
    with SagaParser
    with StreamingParser
    with StatementParser
    with ParsingContext {

  def input: RiddlParserInput
  def withVerboseFailures: Boolean
  
  private def doParse[E <: Parent: ClassTag](rule: P[?] => P[E]): Either[Messages, E] = {
    parseRule[E](input, rule, withVerboseFailures) {
      (result: Either[Messages, E], input: RiddlParserInput, index: Int) =>
        result match {
          case l: Left[Messages, E] => l
          case result @ Right(node: E) =>
            if node.contents.isEmpty then
              error(At(input, index), s"Parser could not translate '${input.origin}' after $index characters")
            end if
            result
          case _ @ Right(wrongNode) =>
            val expected = classTag[E].runtimeClass
            val actual = wrongNode.getClass
            error(At(input, index), s"Parser did not yield a ${expected.getSimpleName} but ${actual.getSimpleName}")
            Left(this.messagesAsList)
        }
    }
  }
  def parseRoot: Either[Messages, Root] = 
    doParse[Root](root(_)) 

  def parseNebula: Either[Messages, Nebula] = doParse[Nebula](nebula(_))

  def parseRootWithURLs: Either[(Messages, Seq[URL]), (Root, Seq[URL])] = {
    doParse[Root](root(_)) match {
      case l @ Left(messages) => Left(messages -> this.getURLs)
      case r @ Right(root)    => Right(root -> this.getURLs)
    }
  }
}
