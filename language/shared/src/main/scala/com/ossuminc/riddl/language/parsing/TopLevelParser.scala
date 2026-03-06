/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.bast.{BASTUtils, BASTLoader}
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext, Timer, URL}
import fastparse.*
import fastparse.MultiLineWhitespace.*

import java.nio.file.{Files, Path}
import scala.collection.IndexedSeqView
import scala.collection.StringView
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.{classTag, ClassTag}
import scala.scalajs.js.annotation.*

/** The TopLevel (Root) parser. This class munges all the individual parsers together and provides
  * top level parsing functionality.
  * @param input
  *   The RiddlParserInput that contains the data to parse
  * @param withVerboseFailures
  *   For the utility of RIDDL implementers.
  */
@JSExportTopLevel("TopLevelParser")
case class TopLevelParser(
  input: RiddlParserInput,
  withVerboseFailures: Boolean
)(using PlatformContext)
    extends ExtensibleTopLevelParser

@JSExportTopLevel("TopLevelParser$")
object TopLevelParser:

  import com.ossuminc.riddl.utils.URL

  import scala.concurrent.ExecutionContext

  /** Convert a Nebula to a Root.
    *
    * BAST files contain Nebula, but top-level parsing expects Root.
    * This converts the Nebula contents to Root contents, filtering
    * to only include valid RootContents types.
    *
    * Valid at both Nebula and Root level: Domain, Module, Author
    *
    * @param nebula The Nebula to convert
    * @return A Root containing the Nebula's contents
    */
  private def nebulaToRoot(nebula: Nebula): Root = {
    // Filter to only items that are valid in RootContents
    // NebulaContents ∩ RootContents = Domain | Module | Author
    val rootItems: Seq[RootContents] = nebula.contents.toSeq.flatMap {
      case d: Domain => Some(d: RootContents)
      case m: Module => Some(m: RootContents)
      case a: Author => Some(a: RootContents)
      case _ => None // Skip other NebulaContents not valid at Root level
    }
    Root(nebula.loc, rootItems.toContents)
  }

  /** Load BAST imports for a parsed Root.
    *
    * After parsing, any BASTImport nodes will have empty contents.
    * This method calls BASTLoader to populate them with the imported
    * Nebula contents from the referenced .bast files.
    *
    * @param root The parsed Root containing potential BASTImport nodes
    * @param baseURL The base URL for resolving relative import paths
    * @param pc The platform context
    * @return The root (with populated imports) and any error messages
    */
  private def loadBASTImports(root: Root, baseURL: URL)(using pc: PlatformContext): (Root, Messages) = {
    if BASTLoader.hasUnloadedImports(root) then
      val result = BASTLoader.loadImports(root, baseURL)
      // NOTE: avoid "import(" in string literals — ESM shim plugins
      // misinterpret it as a dynamic ES module import() call.
      if result.failedCount > 0 then
        pc.log.warn(s"Failed to load ${result.failedCount} BAST file(s)")
      end if
      if result.loadedCount > 0 then
        pc.log.info(s"Loaded ${result.loadedCount} BAST file(s)")
      end if
      (root, result.messages)
    else
      (root, Messages.empty)
    end if
  }

  /** Main entry point into parsing. This sets up the asynchronous (but maybe not parallel) parsing
    * of the input to the parser.
    *
    * This method first checks if a .bast file exists for the given URL and is newer.
    * If so, it loads from the BAST file for faster startup. Otherwise, it parses
    * the RIDDL file normally.
    *
    * @param url
    *   A `file://` or `https://` based url to specify the source of the parser input
    * @param withVerboseFailures
    *   Control whether parse failures are diagnosed verbosely or not. Typically only useful to
    *   maintainers of RIDDL, or test cases
    */
  def parseURL(
    url: URL,
    withVerboseFailures: Boolean = false
  )(using pc: PlatformContext): Future[Either[Messages, Root]] = {
    // Check for BAST file first
    BASTUtils.tryLoadBastOrParseRiddl(url) match {
      case Some((nebula, _)) =>
        // BAST loaded successfully, convert to Root
        pc.log.info(s"Loaded from BAST: ${BASTUtils.getBastUrlFor(url).toExternalForm}")
        val root = nebulaToRoot(nebula)
        // Load any nested BAST imports
        val (loadedRoot, importMsgs) = loadBASTImports(root, url)
        if importMsgs.hasErrors then
          Future.successful(Left(importMsgs))
        else
          Future.successful(Right(loadedRoot))
        end if
      case None =>
        // No BAST or load failed, parse RIDDL
        pc.load(url).map { (data: String) =>
          val rpi = RiddlParserInput(data.mkString, url)
          val tlp = new TopLevelParser(rpi, withVerboseFailures)
          tlp.parseRoot match {
            case Left(parseErrors) => Left(parseErrors)
            case Right(root) =>
              // Load any BAST imports referenced in the parsed file
              val (loadedRoot, importMsgs) = loadBASTImports(root, url)
              if importMsgs.hasErrors then
                Left(importMsgs)
              else
                Right(loadedRoot)
              end if
          }
        }
    }
  }

  /** Alternate, non-asynchronous interface to parsing. If you have your data already, you can just
    * make your own RiddlParserInput from a string and call this to start parsing.
    * @param input
    *   The RiddlParserInput that contains the data to parse
    * @param withVerboseFailures
    *   For the utility of RIDDL implementers.
    * @return
    */
  def parseInput(
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(using pc: PlatformContext): Either[Messages, Root] = {
    Timer.time(s"parse ${input.origin}", pc.options.showTimes) {
      implicit val _: ExecutionContext = pc.ec
      val tlp = new TopLevelParser(input, withVerboseFailures)
      tlp.parseRoot match {
        case Left(parseErrors) => Left(parseErrors)
        case Right(root) =>
          // Load any BAST imports referenced in the parsed file
          val (loadedRoot, importMsgs) = loadBASTImports(root, input.root)
          if importMsgs.hasErrors then
            Left(importMsgs)
          else
            Right(loadedRoot)
          end if
      }
    }
  }

  /** Parse a string directly
    *
    * @param input
    *   The input string to parse
    * @param withVerboseFailures
    *   For the utility of RIDDL implementers.
    * @return
    *   Left(messages) -> messages indicaitng the error Right(root) -> the resulting AST.Root from
    *   the parse
    */
  def parseString(
    input: String,
    withVerboseFailures: Boolean = false
  )(using PlatformContext): Either[Messages, Root] = {
    val rpi = RiddlParserInput(input, "")
    val tlp = new TopLevelParser(rpi, withVerboseFailures)
    tlp.parseRoot
  }

  /** Parse an arbitrary (nebulous) set of definitions in any order
    *
    * @param input
    *   The input to parse
    * @param withVerboseFailures
    *   For the utility of RIDDL implementers.
    * @return
    *   - Left(messages) -> messages indicaitng the error
    *   - Right(nebula) -> the nebula containing the list of things that were parsed
    */
  def parseNebula(
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(using pc: PlatformContext): Either[Messages, Nebula] = {
    Timer.time(s"parse nebula from ${input.origin}", pc.options.showTimes) {
      val tlp = new TopLevelParser(input, withVerboseFailures)
      tlp.parseNebula
    }
  }

  /** Parse the input to a list of tokens. This is aimed to making highlighting in editors quick and
    * simple. The input is not validate for syntactic correctness and likely succeeds on most input.
    * @param input
    *   The input to be parsed
    * @param withVerboseFailures
    *   Set to true to debug parsing failures. Probably of interest only to the implementors. The
    *   default, false, causes no functional difference.
    */
  def parseToTokens(
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(using PlatformContext): Either[Messages, List[Token]] = {
    val tlp = new TopLevelParser(input, withVerboseFailures)
    tlp.parseTokens
  }

  def mapTextAndToken[T](
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(f: (IndexedSeqView[Char],Token) => T)(using PlatformContext): Either[Messages, List[T]] =
    val tlp = new TopLevelParser(input, withVerboseFailures)
    tlp.parseTokens match
      case Left(messages) => Left(messages)
      case Right(tokens: List[Token]) =>
        val view = StringView(input.data)
        val mapped = tokens.map { token =>
          val slice = view.slice(token.loc.offset, token.loc.endOffset)
          f(slice,token)
        }
        Right(mapped)
    end match
  end mapTextAndToken

  /** Parse content as if it were inside a Domain body.
    *
    * Wraps the input in a synthetic `domain SyntheticScope is { ... }`
    * declaration, parses it, and returns the resulting Domain.
    *
    * @param input The RiddlParserInput containing domain-level content
    * @param withVerboseFailures Enable verbose parse failure messages
    * @return Either error messages or the parsed Domain
    */
  def parseAsDomain(
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(using PlatformContext): Either[Messages, Domain] =
    val wrapped = RiddlParserInput(
      s"domain SyntheticScope is {\n${input.data}\n}",
      input.root
    )
    val tlp = new TopLevelParser(wrapped, withVerboseFailures)
    tlp.parseDomainContents
  end parseAsDomain

  /** Parse content as if it were inside a Context body.
    *
    * Wraps the input in a synthetic `context SyntheticScope is { ... }`
    * declaration, parses it, and returns the resulting Context.
    *
    * @param input The RiddlParserInput containing context-level content
    * @param withVerboseFailures Enable verbose parse failure messages
    * @return Either error messages or the parsed Context
    */
  def parseAsContext(
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(using PlatformContext): Either[Messages, Context] =
    val wrapped = RiddlParserInput(
      s"context SyntheticScope is {\n${input.data}\n}",
      input.root
    )
    val tlp = new TopLevelParser(wrapped, withVerboseFailures)
    tlp.parseContextContents
  end parseAsContext

  /** Parse content as if it were inside an Entity body.
    *
    * Wraps the input in a synthetic `entity SyntheticScope is { ... }`
    * declaration, parses it, and returns the resulting Entity.
    *
    * @param input The RiddlParserInput containing entity-level content
    * @param withVerboseFailures Enable verbose parse failure messages
    * @return Either error messages or the parsed Entity
    */
  def parseAsEntity(
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(using PlatformContext): Either[Messages, Entity] =
    val wrapped = RiddlParserInput(
      s"entity SyntheticScope is {\n${input.data}\n}",
      input.root
    )
    val tlp = new TopLevelParser(wrapped, withVerboseFailures)
    tlp.parseEntityContents
  end parseAsEntity

  /** Parse content as if it were inside a Module body.
    *
    * Wraps the input in a synthetic `module SyntheticScope is { ... }`
    * declaration, parses it, and returns the resulting Module.
    */
  def parseAsModule(
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(using PlatformContext): Either[Messages, Module] =
    val wrapped = RiddlParserInput(
      s"module SyntheticScope is {\n${input.data}\n}",
      input.root
    )
    val tlp = new TopLevelParser(wrapped, withVerboseFailures)
    tlp.parseModuleContents
  end parseAsModule

  /** Parse content as if it were inside an Adaptor body.
    *
    * Wraps the input in a synthetic adaptor declaration using
    * the caller-provided direction and context reference, then
    * parses it and returns the resulting Adaptor.
    *
    * @param input The RiddlParserInput containing adaptor body content
    * @param direction The AdaptorDirection (InboundAdaptor or OutboundAdaptor)
    * @param contextRef The ContextRef the adaptor adapts from/to
    * @param withVerboseFailures Enable verbose parse failure messages
    * @return Either error messages or the parsed Adaptor
    */
  def parseAsAdaptor(
    input: RiddlParserInput,
    direction: AdaptorDirection,
    contextRef: ContextRef,
    withVerboseFailures: Boolean = false
  )(using PlatformContext): Either[Messages, Adaptor] =
    val dirStr = direction match
      case _: InboundAdaptor  => "from"
      case _: OutboundAdaptor => "to"
    val ctxPath = contextRef.pathId.value.mkString(".")
    val wrapped = RiddlParserInput(
      s"adaptor SyntheticScope $dirStr context $ctxPath is {\n${input.data}\n}",
      input.root
    )
    val tlp = new TopLevelParser(wrapped, withVerboseFailures)
    tlp.parseAdaptorContents
  end parseAsAdaptor

  /** Parse content as if it were inside a Projector body.
    *
    * Wraps the input in a synthetic `projector SyntheticScope is { ... }`
    * declaration, parses it, and returns the resulting Projector.
    */
  def parseAsProjector(
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(using PlatformContext): Either[Messages, Projector] =
    val wrapped = RiddlParserInput(
      s"projector SyntheticScope is {\n${input.data}\n}",
      input.root
    )
    val tlp = new TopLevelParser(wrapped, withVerboseFailures)
    tlp.parseProjectorContents
  end parseAsProjector

  /** Parse content as if it were inside a Repository body.
    *
    * Wraps the input in a synthetic `repository SyntheticScope is { ... }`
    * declaration, parses it, and returns the resulting Repository.
    */
  def parseAsRepository(
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(using PlatformContext): Either[Messages, Repository] =
    val wrapped = RiddlParserInput(
      s"repository SyntheticScope is {\n${input.data}\n}",
      input.root
    )
    val tlp = new TopLevelParser(wrapped, withVerboseFailures)
    tlp.parseRepositoryContents
  end parseAsRepository

  /** Parse content as if it were inside a Saga body.
    *
    * Parses the input for saga body content (saga steps,
    * functions, inlets, outlets) and assembles a Saga using
    * the caller-provided input/output aggregations.
    *
    * @param input The RiddlParserInput containing saga body content
    * @param sagaInput Optional input aggregation from the parent Saga
    * @param sagaOutput Optional output aggregation from the parent Saga
    * @param withVerboseFailures Enable verbose parse failure messages
    * @return Either error messages or the parsed Saga
    */
  def parseAsSaga(
    input: RiddlParserInput,
    sagaInput: Option[Aggregation] = None,
    sagaOutput: Option[Aggregation] = None,
    withVerboseFailures: Boolean = false
  )(using PlatformContext): Either[Messages, Saga] =
    val tlp = new TopLevelParser(input, withVerboseFailures)
    tlp.parseSagaDefinitions.map { contents =>
      val loc =
        if contents.nonEmpty then contents.head.loc
        else At.empty
      Saga(loc, Identifier.empty, sagaInput, sagaOutput,
        contents.toContents)
    }
  end parseAsSaga

  /** Parse content as if it were inside an Epic body.
    *
    * Parses the input for epic body content (use cases, types,
    * etc.) and assembles an Epic using the caller-provided
    * UserStory.
    *
    * @param input The RiddlParserInput containing epic-level content
    * @param userStory The UserStory from the parent Epic definition
    * @param withVerboseFailures Enable verbose parse failure messages
    * @return Either error messages or the parsed Epic
    */
  def parseAsEpic(
    input: RiddlParserInput,
    userStory: UserStory,
    withVerboseFailures: Boolean = false
  )(using PlatformContext): Either[Messages, Epic] =
    val tlp = new TopLevelParser(input, withVerboseFailures)
    tlp.parseEpicDefinitions.map { contents =>
      val loc =
        if contents.nonEmpty then contents.head.loc
        else At.empty
      Epic(loc, Identifier.empty, userStory, contents.toContents)
    }
  end parseAsEpic

  /** Parse content as if it were inside a Streamlet body.
    *
    * Parses the input for processor content (handlers, types,
    * functions, etc.) and assembles a Streamlet using the
    * caller-provided shape, inlets, and outlets.
    *
    * @param input The RiddlParserInput containing streamlet body content
    * @param shape The StreamletShape from the parent Streamlet definition
    * @param inlets The Inlet definitions from the parent Streamlet
    * @param outlets The Outlet definitions from the parent Streamlet
    * @param withVerboseFailures Enable verbose parse failure messages
    * @return Either error messages or the parsed Streamlet
    */
  def parseAsStreamlet(
    input: RiddlParserInput,
    shape: StreamletShape,
    inlets: Seq[Inlet],
    outlets: Seq[Outlet],
    withVerboseFailures: Boolean = false
  )(using PlatformContext): Either[Messages, Streamlet] =
    val tlp = new TopLevelParser(input, withVerboseFailures)
    tlp.parseStreamletDefinitions.map { contents =>
      val allContents =
        (inlets.asInstanceOf[Seq[StreamletContents]] ++
          outlets.asInstanceOf[Seq[StreamletContents]] ++
          contents)
      val loc =
        if allContents.nonEmpty then allContents.head.loc
        else At.empty
      Streamlet(loc, Identifier.empty, shape, allContents.toContents)
    }
  end parseAsStreamlet

end TopLevelParser
