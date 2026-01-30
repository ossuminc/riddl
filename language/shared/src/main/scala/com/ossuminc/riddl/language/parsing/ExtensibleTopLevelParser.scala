/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{map => _, *}
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
      ContextParser,
      DomainParser,
      EntityParser,
      EpicParser,
      FunctionParser,
      GroupParser,
      ModuleParser,
      NebulaParser,
      ProjectorParser,
      RepositoryParser,
      RootParser,
      SagaParser,
      StreamingParser,
      StatementParser,
      TokenParser,
      ParsingContext {

  def input: RiddlParserInput
  def withVerboseFailures: Boolean

  private def doContentsParse[E <: RiddlValue: ClassTag](
    rule: P[?] => P[Seq[E]]
  ): Either[Messages, Contents[E]] = {
    val result = parseRule[Seq[E]](input, rule, withVerboseFailures) {
      (result: Either[Messages, Seq[E]], input: RiddlParserInput, index: Int) =>
        result match {
          case l: Left[Messages, Seq[E]] => l
          case result @ Right(node: Seq[E]) =>
            if node.isEmpty then
              error(
                At(input, index),
                s"Parser could not translate '${input.origin}' after $index characters"
              )
            end if
            result
          case _ @Right(wrongNode) =>
            val expected = classTag[E].runtimeClass
            val actual = wrongNode.getClass
            error(
              At(input, index),
              s"Parser did not yield a ${expected.getSimpleName} but ${actual.getSimpleName}"
            )
            Left(this.messagesAsList)
        }
    }
    result match
      case l @ Left(messages) => Left(messages)
      case Right(contents)    => Right(contents.toContents)
    end match
  }

  private def doParse[E <: Branch[?]: ClassTag](rule: P[?] => P[E]): Either[Messages, E] = {
    parseRule[E](input, rule, withVerboseFailures) {
      (result: Either[Messages, E], input: RiddlParserInput, index: Int) =>
        result match {
          case l: Left[Messages, E] => l
          case result @ Right(node: E) =>
            if node.contents.isEmpty then
              error(
                At(input, index),
                s"Parser could not translate '${input.origin}' after $index characters"
              )
            end if
            result
          case _ @Right(wrongNode) =>
            val expected = classTag[E].runtimeClass
            val actual = wrongNode.getClass
            error(
              At(input, index),
              s"Parser did not yield a ${expected.getSimpleName} but ${actual.getSimpleName}"
            )
            Left(this.messagesAsList)
        }
    }
  }

  /** Obtain the parser for any of the main AST definition types */
  protected def parserFor[T <: Definition: ClassTag]: P[?] => P[T] = {
    val parser: P[?] => P[?] = classTag[T].runtimeClass match {
      case x if x == classOf[Adaptor]      => p => adaptor(using p)
      case x if x == classOf[Author]       => p => author(using p)
      case x if x == classOf[Connector]    => p => connector(using p)
      case x if x == classOf[Constant]     => p => constant(using p)
      case x if x == classOf[Context]      => p => context(using p)
      case x if x == classOf[Domain]       => p => domain(using p)
      case x if x == classOf[Entity]       => p => entity(using p)
      case x if x == classOf[Epic]         => p => epic(using p)
      case x if x == classOf[Function]     => p => function(using p)
      case x if x == classOf[Group]        => p => group(using p)
      case x if x == classOf[Invariant]    => p => invariant(using p)
      case x if x == classOf[Module]       => p => module(using p)
      case x if x == classOf[Nebula]       => p => nebula(using p)
      case x if x == classOf[Projector]    => p => projector(using p)
      case x if x == classOf[Relationship] => p => relationship(using p)
      case x if x == classOf[Repository]   => p => repository(using p)
      case x if x == classOf[Root]         => p => root(using p)
      case x if x == classOf[Saga]         => p => saga(using p)
      case x if x == classOf[Streamlet]    => p => streamlet(using p)
      case x if x == classOf[Type]         => p => typeDef(using p)
      case x if x == classOf[User]         => p => user(using p)
      case _ =>
        throw new RuntimeException(
          s"No parser defined for ${classTag[T].runtimeClass}"
        )
    }
    parser.asInstanceOf[P[?] => P[T]]
  }

  /** Parse the input expecting the contents of a Root node
    * @return
    *   Either the failure error messages or the Root parsed
    */
  def parseRoot: Either[Messages, Root] = doParse[Root](p => root(using p))

  /** Parse the input expecting the contents of a Root node but also return the list of files that
    * were read
    * @return
    *   Either the failure messages and a list of files or the Root that was parsed and the list of
    *   files parsed.
    */
  def parseRootWithURLs: Either[(Messages, Seq[URL]), (Root, Seq[URL])] = {
    doParse[Root](p => root(using p)) match {
      case l @ Left(messages) => Left(messages -> this.getURLs)
      case r @ Right(root)    => Right(root -> this.getURLs)
    }
  }

  /** Parse the input expecting main definitions in any order, a nebula. Each definition must be
    * syntactically correct but the top level definitions do not require the hierarchical structure
    * of parsing for Root contents.
    * @return
    *   Either the failure messages or the Nebula of definitions
    */
  def parseNebula: Either[Messages, Nebula] = doParse[Nebula](p => nebula(using p))

  /** Parse the input expecting definitions in any order, a nebula. Each definition must be
    * syntactically correct but the top level definitions do not require the hierarchical structure
    * of parsing for Root contents.
    * @return
    *   Either the failure messages with the list of parsed URL or the Nebula of definitions with
    *   the list of parsed URLs
    */
  def parseNebulaWithURLs: Either[(Messages, Seq[URL]), (Nebula, Seq[URL])] = {
    doParse[Nebula](p => nebula(using p)) match {
      case l @ Left(messages) => Left(messages -> this.getURLs)
      case r @ Right(nebula)  => Right(nebula -> this.getURLs)
    }
  }

  def parseTokens: Either[Messages, List[Token]] = {
    parse[List[Token]](input, p => parseAllTokens(using p)) match
      case Left((messages, _)) => Left(messages)
      case Right((list, _))    => Right(list)
    end match
  }

  def parseTokensAndText: Either[Messages, List[(Token, String)]] = {
    parse[List[Token]](input, p => parseAllTokens(using p)) match
      case Left((messages, _)) => Left(messages)
      case Right((list, _)) =>
        Right(list.map { token => token -> token.loc.toText })
    end match
  }
}
