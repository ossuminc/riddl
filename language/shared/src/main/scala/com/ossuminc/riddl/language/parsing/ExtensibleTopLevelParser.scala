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
      TokenStreamParser,
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

  /** Obtain the parser for any of the main AST definition types */
  protected def parserFor[T <: Definition: ClassTag]: P[?] => P[T] = {
    val parser: P[?] => P[?] = classTag[T].runtimeClass match {
      case x if x == classOf[Adaptor]        => adaptor(_)
      case x if x == classOf[Author]         => author(_)
      case x if x == classOf[Connector]      => connector(_)
      case x if x == classOf[Constant]       => constant(_)
      case x if x == classOf[Context]        => context(_)
      case x if x == classOf[Domain]         => domain(_)
      case x if x == classOf[Entity]         => entity(_)
      case x if x == classOf[Epic]           => epic(_)
      case x if x == classOf[Function]       => function(_)
      case x if x == classOf[Group]          => group(_)
      case x if x == classOf[Invariant]      => invariant(_)
      case x if x == classOf[Module]         => module(_)
      case x if x == classOf[Nebula]         => nebula(_)
      case x if x == classOf[Projector]      => projector(_)
      case x if x == classOf[Relationship]   => relationship(_)
      case x if x == classOf[Repository]     => repository(_)
      case x if x == classOf[Root]           => root(_)
      case x if x == classOf[Saga]           => saga(_)
      case x if x == classOf[Streamlet]      => streamlet(_)
      case x if x == classOf[Type]           => typeDef(_)
      case x if x == classOf[User]           => user(_)
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
  def parseRoot: Either[Messages, Root] = doParse[Root](root(_))

  /** Parse the input expecting the contents of a Root node but also return the list of files that were read
    * @return
    *   Either the failure messages and a list of files or the Root that was parsed and the list of files parsed.
    */
  def parseRootWithURLs: Either[(Messages, Seq[URL]), (Root, Seq[URL])] = {
    doParse[Root](root(_)) match {
      case l @ Left(messages) => Left(messages -> this.getURLs)
      case r @ Right(root)    => Right(root -> this.getURLs)
    }
  }

  /** Parse the input expecting main definitions in any order, a nebula. Each definition must be syntactically correct
    * but the top level definitions do not require the hierarchical structure of parsing for Root contents.
    * @return
    *   Either the failure messages or the Nebula of definitions
    */
  def parseNebula: Either[Messages, Nebula] = doParse[Nebula](nebula(_))

  /** Parse the input expecting definitions in any order, a nebula. Each definition must be syntactically correct but
    * the top level definitions do not require the hierarchical structure of parsing for Root contents.
    * @return
    *   Either the failure messages with the list of parsed URL or the Nebula of definitions with the list of parsed
    *   URLs
    */
  def parseNebulaWithURLs: Either[(Messages, Seq[URL]), (Nebula, Seq[URL])] = {
    doParse[Nebula](nebula(_)) match {
      case l @ Left(messages) => Left(messages -> this.getURLs)
      case r @ Right(nebula)  => Right(nebula -> this.getURLs)
    }
  }

  def parseTokens: Either[Messages, List[Token]] = {
    parse[List[Token]](input, parseAllTokens(_)) match
      case Left((messages, _)) => Left(messages)
      case Right((list, _))    => Right(list)
    end match
  }
}
