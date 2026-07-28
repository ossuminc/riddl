/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}
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

  /** "The parser consumed the input but produced nothing" — reported identically by both parse
    * entry points below, which differ only in whether they yield a `Contents[E]` or a single `E`.
    */
  private def reportEmptyResult(input: RiddlParserInput, index: Int): Unit =
    error(
      At(input, index),
      s"Parser could not translate '${input.origin}' after $index characters"
    )

  /** "The parser produced the wrong kind of node" — likewise shared. Returns the Left both callers
    * return, so the message and the failure value cannot drift apart.
    */
  private def reportWrongNode[E: ClassTag](
    wrongNode: Any,
    input: RiddlParserInput,
    index: Int
  ): Left[Messages, scala.Nothing] = // scala.Nothing: `AST.Nothing` is a RIDDL type expression
    val expected = classTag[E].runtimeClass
    val actual = wrongNode.getClass
    error(
      At(input, index),
      s"Parser did not yield a ${expected.getSimpleName} but ${actual.getSimpleName}"
    )
    Left(this.messagesAsList)

  private def doContentsParse[E <: RiddlValue: ClassTag](
    rule: P[?] => P[Seq[E]]
  ): Either[Messages, Contents[E]] = {
    val result = parseRule[Seq[E]](input, rule, withVerboseFailures) {
      (result: Either[Messages, Seq[E]], input: RiddlParserInput, index: Int) =>
        result match {
          case l: Left[Messages, Seq[E]] => l
          case result @ Right(node: Seq[E]) =>
            if node.isEmpty then reportEmptyResult(input, index)
            end if
            result
          case _ @Right(wrongNode) => reportWrongNode[E](wrongNode, input, index)
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
            if node.contents.isEmpty then reportEmptyResult(input, index)
            end if
            result
          case _ @Right(wrongNode) => reportWrongNode[E](wrongNode, input, index)
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
      case x if x == classOf[Projector]    => p => projector(using p)
      case x if x == classOf[Relationship] => p => relationship(using p)
      case x if x == classOf[Repository]   => p => repository(using p)
      case x if x == classOf[Root]         => p => root(using p)
      case x if x == classOf[Saga]         => p => saga(using p)
      case x if x == classOf[Streamlet]    => p => streamlet(using p)
      case x if x == classOf[Type]         => p => typeDef(using p)
      case x if x == classOf[User]         => p => user(using p)
      case x if x == classOf[Version]      => p => versionDef(using p)
      case x if x == classOf[Copyright]    => p => copyrightDef(using p)
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
    doParse[Root]((u: P[?]) => root(using u.asInstanceOf[P[Any]])) match {
      case l @ Left(msgs) => Left(msgs -> this.getURLs)
      case r @ Right(rt)  => Right(rt -> this.getURLs)
    }
  }

  /** Parse the input expecting main definitions in any order, a nebula. Each definition must be
    * syntactically correct but the top level definitions do not require the hierarchical structure
    * of parsing for Root contents.
    *
    * The anonymous `nebula` surface is DEPRECATED — parsing it emits one `[deprecated]` message and
    * yields a [[AST.Module]] with the synthetic id [[AST.Module.syntheticId]].
    * @return
    *   Either the failure messages or the Module of definitions
    */
  def parseNebula: Either[Messages, Module] = doParse[Module](p => nebula(using p))

  /** Parse the input expecting definitions in any order, a nebula. Each definition must be
    * syntactically correct but the top level definitions do not require the hierarchical structure
    * of parsing for Root contents.
    * @return
    *   Either the failure messages with the list of parsed URL or the Module of definitions with
    *   the list of parsed URLs
    */
  def parseNebulaWithURLs: Either[(Messages, Seq[URL]), (Module, Seq[URL])] = {
    doParse[Module](p => nebula(using p)) match {
      case l @ Left(messages) => Left(messages -> this.getURLs)
      case r @ Right(module)  => Right(module -> this.getURLs)
    }
  }

  /** Parse the input as the contents of a Domain definition. Wraps the input in a synthetic `domain
    * _scope_ is { ... }` and returns the resulting Domain.
    */
  def parseDomainContents: Either[Messages, Domain] =
    doParse[Domain](p => domain(using p))

  /** Parse the input as the contents of a Context definition. Wraps the input in a synthetic
    * `context _scope_ is { ... }` and returns the resulting Context.
    */
  def parseContextContents: Either[Messages, Context] =
    doParse[Context](p => context(using p))

  /** Parse the input as the contents of an Entity definition. Wraps the input in a synthetic
    * `entity _scope_ is { ... }` and returns the resulting Entity.
    */
  def parseEntityContents: Either[Messages, Entity] =
    doParse[Entity](p => entity(using p))

  /** Parse the input as the contents of a Module definition. */
  def parseModuleContents: Either[Messages, Module] =
    doParse[Module](p => module(using p))

  /** Parse the input as the contents of an Adaptor definition. */
  def parseAdaptorContents: Either[Messages, Adaptor] =
    doParse[Adaptor](p => adaptor(using p))

  /** Parse the input as the contents of a Projector definition. */
  def parseProjectorContents: Either[Messages, Projector] =
    doParse[Projector](p => projector(using p))

  /** Parse the input as the contents of a Repository definition. */
  def parseRepositoryContents: Either[Messages, Repository] =
    doParse[Repository](p => repository(using p))

  /** Parse the input as epic body content (use cases, types, etc.) and return a raw sequence of
    * EpicContents.
    */
  def parseEpicDefinitions: Either[Messages, Seq[EpicContents]] =
    doContentsParse[EpicContents](p => epicDefinitions(using p))
      .map(_.toSeq)

  /** Parse the input as saga body content (saga steps, inlets, outlets, functions) and return a raw
    * sequence.
    */
  def parseSagaDefinitions: Either[Messages, Seq[SagaContents]] =
    doContentsParse[SagaContents](p => sagaDefinitions(using p))
      .map(_.toSeq)

  /** Parse the input as streamlet processor content (handlers, types, functions, etc.) and return a
    * raw sequence.
    */
  def parseStreamletDefinitions: Either[Messages, Seq[StreamletContents]] =
    doContentsParse[StreamletContents] { (p: P[?]) =>
      given P[Any] = p.asInstanceOf[P[Any]]
      processorDefinitionContents(StatementsSet.StreamStatements)
        .asInstanceOf[P[StreamletContents]]
        .rep(1)
    }.map(_.toSeq)

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
