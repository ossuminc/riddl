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
    extends ProcessorParser,
      AdaptorParser,
      ApplicationParser,
      ContextParser,
      DomainParser,
      EntityParser,
      EpicParser,
      FunctionParser,
      ModuleParser,
      NebulaParser,
      ProjectorParser,
      RepositoryParser,
      RootParser,
      SagaParser,
      StreamingParser,
      StatementParser,
      ParsingContext {

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
          case _ @Right(wrongNode) =>
            val expected = classTag[E].runtimeClass
            val actual = wrongNode.getClass
            error(At(input, index), s"Parser did not yield a ${expected.getSimpleName} but ${actual.getSimpleName}")
            Left(this.messagesAsList)
        }
    }
  }
  def parseRoot: Either[Messages, Root] = doParse[Root](root(_))

  def parseNebula: Either[Messages, Nebula] = doParse[Nebula](nebula(_))

  def parseRootWithURLs: Either[(Messages, Seq[URL]), (Root, Seq[URL])] = {
    doParse[Root](root(_)) match {
      case l @ Left(messages) => Left(messages -> this.getURLs)
      case r @ Right(root)    => Right(root -> this.getURLs)
    }
  }

  protected def parserFor[T <: Definition: ClassTag]: P[?] => P[T] = {
    val parser: P[?] => P[?] = classTag[T].runtimeClass match {
      case x if x == classOf[Type]       => typeDef(_)
      case x if x == classOf[Domain]     => domain(_)
      case x if x == classOf[Context]    => context(_)
      case x if x == classOf[Entity]     => entity(_)
      case x if x == classOf[Adaptor]    => adaptor(_)
      case x if x == classOf[Invariant]  => invariant(_)
      case x if x == classOf[Function]   => function(_)
      case x if x == classOf[Streamlet]  => streamlet(_)
      case x if x == classOf[Saga]       => saga(_)
      case x if x == classOf[Repository] => repository(_)
      case x if x == classOf[Projector]  => projector(_)
      case x if x == classOf[Epic]       => epic(_)
      case x if x == classOf[Connector]  => connector(_)
      case x if x == classOf[Module]     => module(_)
      case x if x == classOf[Nebula]     => nebula(_)
      case x if x == classOf[Root]       => root(_)
      case _ =>
        throw new RuntimeException(
          s"No parser defined for ${classTag[T].runtimeClass}"
        )
    }
    parser.asInstanceOf[P[?] => P[T]]
  }

}
