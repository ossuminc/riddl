/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.{Nebula, Root, Token}
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassOptions, PassesOutput, OutlinePass, OutlineOutput, OutlineEntry, TreePass, TreeOutput, TreeNode}
import com.ossuminc.riddl.passes.transforms.FlattenPass
import com.ossuminc.riddl.utils.{CommonOptions, DOMPlatformContext, PlatformContext, URL}

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

  /** Convert origin string to URL for RiddlParserInput.
    * Handles both full file paths (starting with /) and simple identifiers.
    */
  private def originToURL(origin: String): URL = {
    if origin.startsWith("/") then
      // Full file path - use fromFullPath
      URL.fromFullPath(origin)
    else
      // Simple identifier or relative path - create URL with path component
      URL(URL.fileScheme, "", "", origin)
    end if
  }

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
    val input = RiddlParserInput(source, originToURL(origin))
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
    val input = RiddlParserInput(source, originToURL(origin))
    val result = TopLevelParser.parseInput(input, verbose)(using context)
    toJsResult(result, rootToJsObject)
  }

  /** Flatten Include and BASTImport wrapper nodes from the AST.
    *
    * Recursively removes Include and BASTImport nodes, promoting their
    * children to the parent container. This makes accessor methods like
    * `domain.contexts` and `context.entities` return all definitions,
    * including those originally loaded from included/imported files.
    *
    * This modifies the Root in-place and returns the same object.
    * The transformation is one-way and irreversible. Consumers that
    * need the original Include/BASTImport structure (e.g., for source
    * regeneration) should retain the un-flattened AST or raw BAST bytes.
    *
    * @param root The AST Root to flatten
    * @return The same Root, with Include/BASTImport nodes removed
    */
  @JSExport("flattenAST")
  def flattenAST(root: Root): Root = {
    val passInput = PassInput(root)
    Pass.runThesePasses(
      passInput,
      Seq(FlattenPass.creator(PassOptions.empty)(using defaultContext))
    )(using defaultContext)
    root
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
    val input = RiddlParserInput(source, originToURL(origin))
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
    val input = RiddlParserInput(source, originToURL(origin))
    val result = TopLevelParser.parseToTokens(input, verbose)(using defaultContext)
    toJsResult(result, tokensToJsArray)
  }

  /** Parse and validate RIDDL source, returning both syntax and semantic errors.
    *
    * This method runs the full validation pipeline:
    * 1. Parse the source to AST (syntax checking)
    * 2. Run SymbolsPass (build symbol table)
    * 3. Run ResolutionPass (resolve references)
    * 4. Run ValidationPass (semantic validation)
    *
    * Returns separate arrays for parse errors and validation messages (errors, warnings, etc.)
    *
    * @param source The RIDDL source code to validate
    * @param origin Optional origin identifier for error messages
    * @param verbose Enable verbose failure messages
    * @param noANSIMessages When true, ANSI color codes are not included in error messages
    * @return Result object with:
    *         {
    *           succeeded: boolean,
    *           parseErrors?: Array<object>,    // Syntax errors from parsing
    *           validationMessages?: Array<object>  // Semantic errors/warnings from validation
    *         }
    */
  @JSExport("validateString")
  def validateString(
    source: String,
    origin: String = "string",
    verbose: Boolean = false,
    noANSIMessages: Boolean = true
  ): js.Dynamic = {
    // Create a custom context with noANSIMessages option
    val options = CommonOptions(
      verbose = verbose,
      noANSIMessages = noANSIMessages
    )
    val ctx = DOMPlatformContext()
    given customContext: PlatformContext = ctx.withOptions(options)(_ => ctx)

    val input = RiddlParserInput(source, originToURL(origin))
    val parseResult = TopLevelParser.parseInput(input, verbose)(using customContext)

    parseResult match {
      case Right(root) =>
        // Parse succeeded, now run validation passes
        try {
          val passesResult = Pass.runStandardPasses(root)(using customContext)
          val messages = passesResult.messages

          // Separate messages by severity and remove duplicates
          val errors = messages.filter(_.isError).distinct
          val warnings = messages.filter(_.isWarning).distinct
          val info = messages.filter(_.kind.severity == 0).distinct

          js.Dynamic.literal(
            succeeded = !messages.hasErrors,
            parseErrors = js.Array(),  // No parse errors
            validationMessages = js.Dynamic.literal(
              errors = formatMessagesAsArray(errors),
              warnings = formatMessagesAsArray(warnings),
              info = formatMessagesAsArray(info),
              all = formatMessagesAsArray(messages)
            )
          )
        } catch {
          case e: Exception =>
            // Validation failed with exception
            js.Dynamic.literal(
              succeeded = false,
              parseErrors = js.Array(),
              validationMessages = js.Dynamic.literal(
                errors = js.Array(
                  js.Dynamic.literal(
                    kind = "ValidationException",
                    message = s"Validation failed: ${e.getMessage}",
                    location = js.Dynamic.literal(
                      line = 1,
                      col = 1,
                      offset = 0,
                      source = origin
                    )
                  )
                ),
                warnings = js.Array(),
                info = js.Array(),
                all = js.Array()
              )
            )
        }

      case Left(parseMessages) =>
        // Parse failed, return parse errors only
        js.Dynamic.literal(
          succeeded = false,
          parseErrors = formatMessagesAsArray(parseMessages),
          validationMessages = js.Dynamic.literal(
            errors = js.Array(),
            warnings = js.Array(),
            info = js.Array(),
            all = js.Array()
          )
        )
    }
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

  /** Get detailed build information about the RIDDL library.
    *
    * Returns a JavaScript object with all build metadata including:
    * - version, scalaVersion, sbtVersion
    * - organization, copyright, license
    * - build date/time
    * - project URLs
    *
    * @return JavaScript object with build information
    */
  @JSExport("buildInfo")
  def buildInfo: js.Dynamic = {
    import com.ossuminc.riddl.utils.RiddlBuildInfo
    js.Dynamic.literal(
      name = RiddlBuildInfo.name,
      version = RiddlBuildInfo.version,
      scalaVersion = RiddlBuildInfo.scalaVersion,
      sbtVersion = RiddlBuildInfo.sbtVersion,
      moduleName = RiddlBuildInfo.moduleName,
      description = RiddlBuildInfo.description,
      organization = RiddlBuildInfo.organization,
      organizationName = RiddlBuildInfo.organizationName,
      copyrightHolder = RiddlBuildInfo.copyrightHolder,
      copyright = RiddlBuildInfo.copyright,
      licenses = RiddlBuildInfo.licenses,
      projectHomepage = RiddlBuildInfo.projectHomepage,
      organizationHomepage = RiddlBuildInfo.organizationHomepage,
      builtAtString = RiddlBuildInfo.builtAtString,
      buildInstant = RiddlBuildInfo.buildInstant,
      isSnapshot = RiddlBuildInfo.isSnapshot
    )
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

  /** Format build information as a human-readable string.
    * This method provides the same output as the `riddlc info` command.
    *
    * @return Formatted build information string
    */
  @JSExport("formatInfo")
  def formatInfo: String = {
    import com.ossuminc.riddl.utils.InfoFormatter
    InfoFormatter.formatInfo
  }

  /** Get a flat outline of all named definitions in RIDDL source.
    *
    * Returns a flat array of entries, each with kind, id, depth, and location.
    * Useful for building outline/table-of-contents views.
    *
    * @param source The RIDDL source code to outline
    * @param origin Optional origin identifier for error messages
    * @return Result object with { succeeded: boolean, value?: OutlineEntry[], errors?: Array }
    */
  @JSExport("getOutline")
  def getOutline(
    source: String,
    origin: String = "string"
  ): js.Dynamic = {
    val rpi = RiddlParserInput(source, originToURL(origin))
    val parseResult = TopLevelParser.parseInput(rpi)(using defaultContext)
    parseResult match {
      case Right(root) =>
        val passInput = PassInput(root)
        val passesResult = Pass.runThesePasses(
          passInput,
          Seq(OutlinePass.creator()(using defaultContext))
        )(using defaultContext)
        passesResult.outputs.outputOf[OutlineOutput](OutlinePass.name) match {
          case Some(outlineOutput) =>
            val entries = outlineOutput.entries.map { e =>
              js.Dynamic.literal(
                kind = e.kind,
                id = e.id,
                depth = e.depth,
                line = e.line,
                col = e.col,
                offset = e.offset
              )
            }.toJSArray
            js.Dynamic.literal(succeeded = true, value = entries)
          case None =>
            js.Dynamic.literal(
              succeeded = false,
              errors = js.Array(
                js.Dynamic.literal(
                  kind = "Error",
                  message = "OutlinePass produced no output",
                  location = js.Dynamic.literal(
                    line = 1, col = 1, offset = 0, source = origin
                  )
                )
              )
            )
        }
      case Left(messages) =>
        js.Dynamic.literal(
          succeeded = false,
          errors = formatMessagesAsArray(messages)
        )
    }
  }

  /** Get a recursive tree of all named definitions in RIDDL source.
    *
    * Returns a nested tree structure mirroring the RIDDL definition hierarchy.
    * Useful for building tree views or navigation panels.
    *
    * @param source The RIDDL source code to process
    * @param origin Optional origin identifier for error messages
    * @return Result object with { succeeded: boolean, value?: TreeNode[], errors?: Array }
    */
  @JSExport("getTree")
  def getTree(
    source: String,
    origin: String = "string"
  ): js.Dynamic = {
    val rpi = RiddlParserInput(source, originToURL(origin))
    val parseResult = TopLevelParser.parseInput(rpi)(using defaultContext)
    parseResult match {
      case Right(root) =>
        val passInput = PassInput(root)
        val passesResult = Pass.runThesePasses(
          passInput,
          Seq(TreePass.creator()(using defaultContext))
        )(using defaultContext)
        passesResult.outputs.outputOf[TreeOutput](TreePass.name) match {
          case Some(treeOutput) =>
            val nodes = treeOutput.tree.map(treeNodeToJs).toJSArray
            js.Dynamic.literal(succeeded = true, value = nodes)
          case None =>
            js.Dynamic.literal(
              succeeded = false,
              errors = js.Array(
                js.Dynamic.literal(
                  kind = "Error",
                  message = "TreePass produced no output",
                  location = js.Dynamic.literal(
                    line = 1, col = 1, offset = 0, source = origin
                  )
                )
              )
            )
        }
      case Left(messages) =>
        js.Dynamic.literal(
          succeeded = false,
          errors = formatMessagesAsArray(messages)
        )
    }
  }

  /** Convert a TreeNode to a JavaScript object recursively */
  private def treeNodeToJs(node: TreeNode): js.Dynamic = {
    js.Dynamic.literal(
      kind = node.kind,
      id = node.id,
      line = node.line,
      col = node.col,
      offset = node.offset,
      children = node.children.map(treeNodeToJs).toJSArray
    )
  }
}
