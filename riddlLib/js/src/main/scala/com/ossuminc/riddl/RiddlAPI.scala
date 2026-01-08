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

/** JavaScript API facade for RIDDL parsing functionality.
  *
  * This object provides a stable, clean API for JavaScript/TypeScript applications
  * to parse RIDDL source code. All method names are preserved (not minified) even
  * in production builds.
  *
  * All methods return a result object with:
  * - `succeeded: boolean` - true if parsing succeeded, false otherwise
  * - `value: any` - the parsed result (Root, Nebula, or Token list) when succeeded is true
  * - `errors: string` - formatted error messages when succeeded is false
  *
  * Example usage from JavaScript:
  * ```javascript
  * import { RiddlAPI } from '@ossuminc/riddl-lib';
  *
  * const result = RiddlAPI.parseString("domain MyDomain is { ??? }");
  * if (result.succeeded) {
  *   console.log("Parse successful:", result.value);
  * } else {
  *   console.error("Parse errors:", result.errors);
  * }
  * ```
  */
@JSExportTopLevel("RiddlAPI")
object RiddlAPI {

  /** Default platform context for browser/Node.js environments */
  given defaultContext: PlatformContext = DOMPlatformContext()

  /** Convert Either to JavaScript-friendly result object */
  private def toJsResult[T](either: Either[Messages, T]): js.Dynamic = {
    either match {
      case Right(value) =>
        js.Dynamic.literal(
          succeeded = true,
          value = value.asInstanceOf[js.Any]
        )
      case Left(messages) =>
        js.Dynamic.literal(
          succeeded = false,
          errors = formatMessages(messages)
        )
    }
  }

  /** Parse a RIDDL source string and return the AST Root.
    *
    * @param source The RIDDL source code to parse
    * @param origin Optional origin identifier (e.g., filename) for error messages
    * @param verbose Enable verbose failure messages (useful for debugging)
    * @return Result object with { succeeded: boolean, value?: Root, errors?: string }
    */
  @JSExport("parseString")
  def parseString(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic = {
    val input = RiddlParserInput(source, origin)
    val result = TopLevelParser.parseInput(input, verbose)(using defaultContext)
    toJsResult(result)
  }

  /** Parse a RIDDL source string with custom platform context.
    *
    * @param source The RIDDL source code to parse
    * @param origin Optional origin identifier (e.g., filename) for error messages
    * @param verbose Enable verbose failure messages (useful for debugging)
    * @param context Custom platform context for I/O operations
    * @return Result object with { succeeded: boolean, value?: Root, errors?: string }
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
    toJsResult(result)
  }

  /** Parse arbitrary RIDDL definitions (nebula).
    *
    * A nebula is a collection of RIDDL definitions that may not form a complete,
    * valid Root. This is useful for parsing fragments or partial files.
    *
    * @param source The RIDDL source code to parse
    * @param origin Optional origin identifier for error messages
    * @param verbose Enable verbose failure messages
    * @return Result object with { succeeded: boolean, value?: Nebula, errors?: string }
    */
  @JSExport("parseNebula")
  def parseNebula(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic = {
    val input = RiddlParserInput(source, origin)
    val result = TopLevelParser.parseNebula(input, verbose)(using defaultContext)
    toJsResult(result)
  }

  /** Parse RIDDL source into a list of tokens for syntax highlighting.
    *
    * This is a fast, lenient parse that produces tokens without full validation.
    * Useful for editor syntax highlighting and quick feedback.
    *
    * @param source The RIDDL source code to tokenize
    * @param origin Optional origin identifier for error messages
    * @param verbose Enable verbose failure messages
    * @return Result object with { succeeded: boolean, value?: Token[], errors?: string }
    */
  @JSExport("parseToTokens")
  def parseToTokens(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic = {
    val input = RiddlParserInput(source, origin)
    val result = TopLevelParser.parseToTokens(input, verbose)(using defaultContext)
    toJsResult(result)
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
    *
    * @param messages The messages to format
    * @return Formatted error string
    */
  @JSExport("formatMessages")
  def formatMessages(messages: Messages): String = {
    messages.map(_.format).mkString("\n")
  }
}
