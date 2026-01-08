/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.{Nebula, Root, Token}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{CommonOptions, DOMPlatformContext, PlatformContext}

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.scalajs.js.JSConverters.*

/** JavaScript/TypeScript API facade for RIDDL parsing functionality.
  *
  * This object provides a stable, clean API for JavaScript/TypeScript applications
  * to parse RIDDL source code. All method names are preserved (not minified) even
  * in production builds.
  *
  * All methods return TypeScript-friendly result objects with:
  * - `succeeded: boolean` - true if parsing succeeded, false otherwise
  * - `value?: object` - the parsed result (plain JS object) when succeeded is true
  * - `errors?: Array<object>` - array of error objects when succeeded is false
  *
  * All Scala types are converted to plain JavaScript objects:
  * - Scala List -> JavaScript Array
  * - Scala case classes -> Plain JavaScript objects with properties
  * - All values are JSON-serializable
  *
  * Example usage from TypeScript:
  * ```typescript
  * import { RiddlAPI } from '@ossuminc/riddl-lib';
  *
  * const result = RiddlAPI.parseString("domain MyDomain is { ??? }");
  * if (result.succeeded) {
  *   console.log("Parse successful:", result.value);
  *   // result.value is a plain object with { kind, domains, location, ... }
  * } else {
  *   console.error("Parse errors:", result.errors);
  *   // result.errors is an array of { kind, message, location }
  * }
  * ```
  */
@JSExportTopLevel("RiddlAPI")
object RiddlAPI {

  /** Default platform context for browser/Node.js environments */
  given defaultContext: PlatformContext = DOMPlatformContext()

  /** Convert Either to JavaScript-friendly result object with proper type conversion */
  private def toJsResult[T](either: Either[Messages, T], converter: T => js.Any = (v: T) => v.asInstanceOf[js.Any]): js.Dynamic = {
    either match {
      case Right(value) =>
        js.Dynamic.literal(
          succeeded = true,
          value = converter(value)
        )
      case Left(messages) =>
        js.Dynamic.literal(
          succeeded = false,
          errors = formatMessagesAsArray(messages)
        )
    }
  }

  /** Convert Scala List[Token] to JavaScript array of plain objects */
  private def tokensToJsArray(tokens: List[Token]): js.Array[js.Dynamic] = {
    tokens.map { token =>
      val text = token.loc.source.data.substring(token.loc.offset, token.loc.endOffset)
      js.Dynamic.literal(
        text = text,
        kind = token.getClass.getSimpleName.replace("$", ""),
        location = js.Dynamic.literal(
          line = token.loc.line,
          col = token.loc.col,
          offset = token.loc.offset,
          endOffset = token.loc.endOffset,
          source = token.loc.source.toString
        )
      )
    }.toJSArray
  }

  /** Convert AST Root to a simplified JavaScript object */
  private def rootToJsObject(root: Root): js.Dynamic = {
    js.Dynamic.literal(
      kind = "Root",
      isEmpty = root.isEmpty,
      nonEmpty = root.nonEmpty,
      domains = root.domains.map(d =>
        js.Dynamic.literal(
          id = d.id.value,
          kind = "Domain",
          isEmpty = d.isEmpty
        )
      ).toJSArray,
      location = js.Dynamic.literal(
        line = root.loc.line,
        col = root.loc.col,
        offset = root.loc.offset,
        source = root.loc.source.toString
      )
    )
  }

  /** Convert AST Nebula to a simplified JavaScript object */
  private def nebulaToJsObject(nebula: Nebula): js.Dynamic = {
    // Contents has toSeq extension method
    val defs: js.Array[js.Dynamic] = nebula.contents.toSeq.map { d =>
      val idValue = Option(d.id).map(_.value).getOrElse("")
      js.Dynamic.literal(
        kind = d.getClass.getSimpleName.replace("$", ""),
        id = idValue,
        isEmpty = d.isEmpty
      )
    }.toJSArray

    js.Dynamic.literal(
      kind = "Nebula",
      isEmpty = nebula.isEmpty,
      nonEmpty = nebula.nonEmpty,
      definitions = defs,
      location = js.Dynamic.literal(
        line = nebula.loc.line,
        col = nebula.loc.col,
        offset = nebula.loc.offset,
        source = nebula.loc.source.toString
      )
    )
  }

  /** Format messages as an array of error objects for TypeScript consumption */
  private def formatMessagesAsArray(messages: Messages): js.Array[js.Dynamic] = {
    messages.map { msg =>
      js.Dynamic.literal(
        kind = msg.kind.toString,
        message = msg.format,
        location = js.Dynamic.literal(
          line = msg.loc.line,
          col = msg.loc.col,
          offset = msg.loc.offset,
          source = msg.loc.source.toString
        )
      )
    }.toJSArray
  }

  /** Parse a RIDDL source string and return the AST Root.
    *
    * @param source The RIDDL source code to parse
    * @param origin Optional origin identifier (e.g., filename) for error messages
    * @param verbose Enable verbose failure messages (useful for debugging)
    * @return Result object with { succeeded: boolean, value?: object, errors?: Array<object> }
    */
  @JSExport("parseString")
  def parseString(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic = {
    val input = RiddlParserInput(source, origin)
    val result = TopLevelParser.parseInput(input, verbose)(using defaultContext)
    toJsResult(result, rootToJsObject)
  }

  /** Parse a RIDDL source string with custom platform context.
    *
    * @param source The RIDDL source code to parse
    * @param origin Optional origin identifier (e.g., filename) for error messages
    * @param verbose Enable verbose failure messages (useful for debugging)
    * @param context Custom platform context for I/O operations
    * @return Result object with { succeeded: boolean, value?: object, errors?: Array<object> }
    */
  @JSExport("parseStringWithContext")
  def parseStringWithContext(
    source: String,
    origin: String,
    verbose: Boolean,
    context: PlatformContext
  ): js.Dynamic = {
    val input = RiddlParserInput(source, origin)
    val result = TopLevelParser.parseInput(input, verbose)(using context)
    toJsResult(result, rootToJsObject)
  }

  /** Parse arbitrary RIDDL definitions (nebula).
    *
    * A nebula is a collection of RIDDL definitions that may not form a complete,
    * valid Root. This is useful for parsing fragments or partial files.
    *
    * @param source The RIDDL source code to parse
    * @param origin Optional origin identifier for error messages
    * @param verbose Enable verbose failure messages
    * @return Result object with { succeeded: boolean, value?: object, errors?: Array<object> }
    */
  @JSExport("parseNebula")
  def parseNebula(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic = {
    val input = RiddlParserInput(source, origin)
    val result = TopLevelParser.parseNebula(input, verbose)(using defaultContext)
    toJsResult(result, nebulaToJsObject)
  }

  /** Parse RIDDL source into a list of tokens for syntax highlighting.
    *
    * This is a fast, lenient parse that produces tokens without full validation.
    * Useful for editor syntax highlighting and quick feedback.
    *
    * @param source The RIDDL source code to tokenize
    * @param origin Optional origin identifier for error messages
    * @param verbose Enable verbose failure messages
    * @return Result object with { succeeded: boolean, value?: Array<object>, errors?: Array<object> }
    */
  @JSExport("parseToTokens")
  def parseToTokens(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic = {
    val input = RiddlParserInput(source, origin)
    val result = TopLevelParser.parseToTokens(input, verbose)(using defaultContext)
    toJsResult(result, tokensToJsArray)
  }

  /** Create a custom platform context with specific options.
    *
    * @param showTimes Enable timing information in output
    * @param showWarnings Include warnings in messages
    * @param verbose Enable verbose output
    * @return A new DOMPlatformContext with the specified options
    */
  @JSExport("createContext")
  def createContext(
    showTimes: Boolean = false,
    showWarnings: Boolean = true,
    verbose: Boolean = false
  ): PlatformContext = {
    val options = CommonOptions(
      showTimes = showTimes,
      showWarnings = showWarnings,
      verbose = verbose
    )
    val ctx = DOMPlatformContext()
    ctx.withOptions(options)(_ => ctx)
  }

  /** Get version information about the RIDDL library.
    *
    * @return Version string
    */
  @JSExport("version")
  def version: String = {
    import com.ossuminc.riddl.utils.RiddlBuildInfo
    RiddlBuildInfo.version
  }

  /** Format error messages as a human-readable string.
    * This is a utility method for internal use and for users who have the Scala Messages type.
    *
    * @param messages The messages to format
    * @return Formatted error string
    */
  private def formatMessages(messages: Messages): String = {
    messages.map(_.format).mkString("\n")
  }

  /** Format an array of error objects as a human-readable string.
    * Useful for displaying errors to users.
    *
    * @param errors JavaScript array of error objects from a parse result
    * @return Formatted error string with one error per line
    */
  @JSExport("formatErrorArray")
  def formatErrorArray(errors: js.Array[js.Dynamic]): String = {
    errors.map { err =>
      val kind = err.kind.asInstanceOf[String]
      val message = err.message.asInstanceOf[String]
      val loc = err.location.asInstanceOf[js.Dynamic]
      val line = loc.line.asInstanceOf[Int]
      val col = loc.col.asInstanceOf[Int]
      s"[$kind] at line $line, column $col: $message"
    }.mkString("\n")
  }

  /** Convert errors array to a simple array of strings for easy display.
    *
    * @param errors JavaScript array of error objects from a parse result
    * @return Array of formatted error strings
    */
  @JSExport("errorsToStrings")
  def errorsToStrings(errors: js.Array[js.Dynamic]): js.Array[String] = {
    errors.map { err =>
      err.message.asInstanceOf[String]
    }
  }
}
