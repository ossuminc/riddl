/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext, Timer}
import com.ossuminc.riddl.utils.SeqHelpers.*
import com.ossuminc.riddl.utils.URL
import fastparse.*
import fastparse.Parsed.Failure
import fastparse.Parsed.Success

import scala.annotation.unused
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.collection.mutable

/** Unit Tests For ParsingContext */
trait ParsingContext(using pc: PlatformContext) extends ParsingErrors {

  import fastparse.P

  // Use HashSet for O(1) duplicate detection instead of O(n) ListBuffer search
  private val urlSeen: mutable.HashSet[URL] = mutable.HashSet.empty[URL]
  def getURLs: Seq[URL] = urlSeen.toSeq

  protected def parse[RESULT](
    rpi: RiddlParserInput,
    rule: P[?] => P[RESULT],
    withVerboseFailures: Boolean = false
  ): Either[(Messages, Int), (RESULT, Int)] = {
    try {
      fastparse.parse[RESULT](rpi, rule, withVerboseFailures) match {
        case fastparse.Parsed.Success(list, index) =>
          // A successful parse may still have accumulated non-fatal messages
          // (warnings/deprecations). Only treat error-level messages as a
          // failure; carry the rest through so they can surface on the Root.
          if messagesHaveErrors then Left(messagesAsList -> index)
          else Right(list -> index)
        case failure: fastparse.Parsed.Failure =>
          makeParseFailureError(failure, rpi)
          Left(messagesAsList -> failure.index)
      }
    } catch {
      case scala.util.control.NonFatal(exception) =>
        makeParseFailureError(exception, At.empty)
        Left(messagesAsList -> 0)
    }
  }

  protected def parseRule[RESULT](
    rpi: RiddlParserInput,
    rule: P[?] => P[RESULT],
    withVerboseFailures: Boolean = false
  )(
    validate: (
      result: Either[Messages, RESULT] @unused,
      input: RiddlParserInput @unused,
      index: Int @unused
    ) => Either[Messages, RESULT] = {
      (result: Either[Messages, RESULT], _: RiddlParserInput, _: Int) =>
        result
    }
  ): Either[Messages, RESULT] = {
    parse[RESULT](rpi, rule(_), withVerboseFailures) match
      case Right((root, index)) =>
        validate(Right(root), rpi, index)
      case Left((messages, index)) =>
        validate(Left(messagesAsList), rpi, index)
    end match
  }

  def at(offset1: Int, offset2: Int)(implicit context: P[?]): At = {
    // NOTE: This isn't strictly kosher because of the cast but as long as we
    // NOTE: always use a RiddlParserInput, should be safe enough. This is
    // NOTE: required because of includes and concurrent parsing
    context.input.asInstanceOf[RiddlParserInput].at(offset1, offset2)
  }

  /** Placeholder for the old, never-implemented `import domain <id> from "<file>"` production
    * (issue #72). That syntax is now a selective BAST import handled by [[doBASTImport]]: it plucks
    * exactly the named domain out of the referenced `.bast` file. Nothing calls this any more; it
    * is retained only so the public trait keeps its shape through the 1.x/2.0 boundary.
    */
  @deprecated(
    "Superseded by doBASTImport; 'import domain X from \"f.bast\"' is a BAST import",
    "2.0.0"
  )
  def doImport(
    loc: At,
    domainName: Identifier,
    url: LiteralString
  )(implicit ctx: P[?]): Domain = {
    Domain(At(), Identifier(At(), "NotImplemented"))
  }

  /** Parse a BAST import statement.
    *
    * This creates a BASTImport node with the path, optional selector, and optional alias. The
    * actual BAST file loading happens later during a loading pass, avoiding circular dependencies
    * between language and bast modules.
    *
    * @param loc
    *   The location of the import statement
    * @param path
    *   The path to the .bast file
    * @param kind
    *   Optional: the kind of definition to import ("domain", "context", etc.)
    * @param selector
    *   Optional: the name of the specific definition to import
    * @param alias
    *   Optional: an alternate name for the imported definition
    * @return
    *   A BASTImport node (contents populated later by BASTLoader)
    */
  def doBASTImport(
    loc: At,
    path: LiteralString,
    kind: Option[String] = None,
    selector: Option[Identifier] = None,
    alias: Option[Identifier] = None
  )(implicit ctx: P[?]): BASTImport = {
    // Validate the path ends with .bast
    if !path.s.endsWith(".bast") then
      warning(loc, s"Import path '${path.s}' should end with .bast extension")
    end if
    // Create the BASTImport node - actual loading happens by BASTLoader
    BASTImport(loc, path, kind, selector, alias)
  }

  def doIncludeParsing[CT <: RiddlValue](loc: At, path: String, rule: P[?] => P[Seq[CT]])(implicit
    ctx: P[?]
  ): Include[CT] = {
    import com.ossuminc.riddl.utils.{PlatformContext, URL}
    val newURL = if URL.isValid(path) then {
      URL(path)
    } else {
      // Use StringBuilder for path concatenation to avoid intermediate String allocation
      val name: String = if path.endsWith(".riddl") then {
        path
      } else {
        val sb = new StringBuilder(path.length + 6) // ".riddl" = 6 chars
        sb.append(path).append(".riddl").toString
      }
      ctx.input.asInstanceOf[RiddlParserInput].root.parent.resolve(name)
    }
    urlSeen += newURL // O(1) add to HashSet
    try {
      import com.ossuminc.riddl.utils.Await
      implicit val ec: ExecutionContext = pc.ec
      val future: Future[Include[CT]] = pc.load(newURL).map { (data: String) =>
        val rpi = RiddlParserInput(data, newURL)
        val contents = doParse[CT](loc, rpi, newURL, rule)
        Include(loc, newURL, contents.toContents)
      }
      Await.result(future, 300)
    } catch {
      case NonFatal(exception) =>
        makeParseFailureError(exception, loc, s"while including '$path'")
        Include[CT](loc, newURL, Contents.empty[CT](1))
    }
  }

  private def doParse[CT <: RiddlValue](
    loc: At,
    rpi: RiddlParserInput,
    url: URL,
    rule: P[?] => P[Seq[CT]]
  )(implicit
    ctx: P[?]
  ): Seq[CT] = {
    fastparse.parse[Seq[CT]](rpi, rule(_), verboseFailures = true) match {
      case Success(content, _) =>
        if messagesNonEmpty then Seq.empty[CT]
        else if content.isEmpty then
          error(loc, s"Parser could not translate '${rpi.origin}''", s"while including '$url''")
        end if
        content
      case failure: Failure =>
        makeParseFailureError(failure, rpi)
        Seq.empty[CT]
    }
  }

  /** A [[Function]] or [[Saga]] body may hold no `requires` and no `returns`, or one of each, and
    * never two of either.
    *
    * Since the move of these clauses out of the body PREFIX and into ordinary content, the grammar
    * itself no longer bounds them — a `rep` will happily accept `requires A requires B`. The bound
    * is enforced HERE, at the one place that sees a whole body, so that everything downstream may
    * ASSUME it rather than defend against it: `Function.input` / `Saga.input` take `headOption`,
    * and that is a complete answer only because of this check.
    *
    * It counts through the provenance wrappers, matching the accessors — two `requires` are two
    * `requires` whether or not an `include` separates them.
    */
  def checkRequiresReturnsCardinality[CT <: RiddlValue](contents: Seq[CT], kind: String): Unit = {
    def check[T <: RiddlValue: scala.reflect.ClassTag](keyword: String): Unit =
      val found = contents.toContents.filterThroughWrappers[T]
      if found.sizeIs > 1 then
        error(
          found(1).loc,
          s"A $kind may have at most one '$keyword' clause, but ${found.size} were found",
          s"in $kind body"
        )
      end if
    end check
    check[Requires]("requires")
    check[Returns]("returns")
  }

  def checkForDuplicateIncludes[CT <: RiddlValue](contents: Seq[CT]): Unit = {
    import com.ossuminc.riddl.language.Finder
    val allIncludes = Finder(contents.toContents).findByType[Include[?]]
    // Use groupBy for O(n) instead of O(n²) with filter inside loop
    val grouped = allIncludes.groupBy(_.origin)
    grouped.foreach { case (origin, includes) =>
      if includes.size > 1 then
        val copyList = includes.map(i => i.origin.toExternalForm + i.loc.toShort).mkString(", ")
        val message = s"Duplicate include origin detected in $copyList"
        warning(includes.head.loc, message, "while merging includes")
    }
  }
}
