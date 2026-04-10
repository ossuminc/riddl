/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.{
  Adaptor, AdaptorDirection, Aggregation, Context,
  ContextRef, Domain, Entity, Epic, Inlet, Module,
  Nebula, Outlet, Projector, Repository, Root, Saga,
  Streamlet, StreamletShape, Token, UserStory
}
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.{IncrementalValidator, OutlineEntry, TreeNode}
import com.ossuminc.riddl.passes.validate.{BehaviorCategory, HandlerCompleteness}
import com.ossuminc.riddl.passes.analysis.{MessageFlowOutput, MessageFlowEdge, FlowMechanism, EntityLifecycle, StateTransition}
import com.ossuminc.riddl.utils.{CommonOptions, DOMPlatformContext, PlatformContext, URL}

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.scalajs.js.JSConverters.*

/** JavaScript/TypeScript API facade for RIDDL parsing functionality.
  *
  * This object provides a stable, clean API for JavaScript/TypeScript
  * applications to parse RIDDL source code. All method names are
  * preserved (not minified) even in production builds.
  *
  * Core logic is delegated to [[RiddlLib]] (shared across all
  * platforms). This facade converts Scala types to plain JavaScript
  * objects.
  *
  * All methods return TypeScript-friendly result objects with:
  * - `succeeded: boolean` - true if parsing succeeded
  * - `value?: object` - the parsed result when succeeded is true
  * - `errors?: Array<object>` - error objects when succeeded is false
  *
  * Example usage from TypeScript:
  * ```typescript
  * import { RiddlAPI } from '@ossuminc/riddl-lib';
  *
  * const result = RiddlAPI.parseString("domain MyDomain is { ??? }");
  * if (result.succeeded) {
  *   // result.value is an opaque Root handle
  *   const info = RiddlAPI.inspectRoot(result.value);
  *   console.log("Domains:", info.domains);
  * }
  * ```
  */
@JSExportTopLevel("RiddlAPI")
object RiddlAPI {

  /** Default platform context for browser/Node.js environments */
  given defaultContext: PlatformContext = DOMPlatformContext()

  // ── JS conversion helpers ──────────────────────────────

  /** Convert RiddlResult to JavaScript-friendly result
    * object.
    */
  private def toJsResult[T](
    result: RiddlResult[T],
    converter: T => js.Any = (v: T) => v.asInstanceOf[js.Any]
  ): js.Dynamic =
    result match
      case RiddlResult.Success(value) =>
        js.Dynamic.literal(
          succeeded = true,
          value = converter(value)
        )
      case RiddlResult.Failure(messages) =>
        js.Dynamic.literal(
          succeeded = false,
          errors = formatMessagesAsArray(messages)
        )
    end match
  end toJsResult

  /** Convert Scala List[Token] to JavaScript array */
  private def tokensToJsArray(
    tokens: List[Token]
  ): js.Array[js.Dynamic] =
    tokens.map { token =>
      val text = token.loc.source.data.substring(
        token.loc.offset, token.loc.endOffset
      )
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
  end tokensToJsArray

  /** Convert AST Root to a simplified JavaScript object */
  private def rootToJsObject(root: Root): js.Dynamic =
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
  end rootToJsObject

  /** Convert AST Nebula to a simplified JavaScript object */
  private def nebulaToJsObject(nebula: Nebula): js.Dynamic =
    val defs: js.Array[js.Dynamic] =
      nebula.contents.toSeq.map { d =>
        val idValue =
          Option(d.id).map(_.value).getOrElse("")
        js.Dynamic.literal(
          kind = d.getClass.getSimpleName
            .replace("$", ""),
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
  end nebulaToJsObject

  /** Format messages as an array of error objects */
  private def formatMessagesAsArray(
    messages: Messages
  ): js.Array[js.Dynamic] =
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
  end formatMessagesAsArray

  /** Convert an OutlineEntry to JS */
  private def outlineEntryToJs(
    e: OutlineEntry
  ): js.Dynamic =
    js.Dynamic.literal(
      kind = e.kind,
      id = e.id,
      depth = e.depth,
      line = e.line,
      col = e.col,
      offset = e.offset
    )
  end outlineEntryToJs

  /** Convert a TreeNode to a JavaScript object recursively */
  private def treeNodeToJs(node: TreeNode): js.Dynamic =
    js.Dynamic.literal(
      kind = node.kind,
      id = node.id,
      line = node.line,
      col = node.col,
      offset = node.offset,
      children = node.children.map(treeNodeToJs).toJSArray
    )
  end treeNodeToJs

  // ── Public API methods ─────────────────────────────────

  /** Parse a RIDDL source string and return an opaque Root
    * handle.
    *
    * The returned `value` is an opaque Scala Root object.
    * Use `inspectRoot()` to get a plain JS summary, or
    * `getDomains()` to extract domains.
    *
    * @param source The RIDDL source code to parse
    * @param origin Origin identifier for error messages
    * @param verbose Enable verbose failure messages
    * @return Result with opaque Root handle or errors
    */
  @JSExport("parseString")
  def parseString(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic =
    toJsResult(
      RiddlLib.parseString(source, origin, verbose),
      root => root.asInstanceOf[js.Any]
    )
  end parseString

  /** Parse a RIDDL source string with custom platform
    * context.
    */
  @JSExport("parseStringWithContext")
  def parseStringWithContext(
    source: String,
    origin: String,
    verbose: Boolean,
    context: PlatformContext
  ): js.Dynamic =
    given PlatformContext = context
    toJsResult(
      RiddlLib.parseString(source, origin, verbose),
      root => root.asInstanceOf[js.Any]
    )
  end parseStringWithContext

  /** Flatten Include and BASTImport wrapper nodes from the
    * AST. The Root is modified in-place and returned.
    *
    * @param root The opaque Root handle from parseString
    * @return The same Root, with wrappers removed
    */
  @JSExport("flattenAST")
  def flattenAST(root: Root): Root =
    RiddlLib.flattenAST(root)
  end flattenAST

  /** Get domain definitions from an opaque Root handle.
    *
    * @param root The opaque Root handle from parseString
    * @return Array of domain objects with id, kind, isEmpty
    */
  @JSExport("getDomains")
  def getDomains(root: Root): js.Array[js.Dynamic] =
    root.domains.map(d =>
      js.Dynamic.literal(
        id = d.id.value,
        kind = "Domain",
        isEmpty = d.isEmpty
      )
    ).toJSArray
  end getDomains

  /** Inspect an opaque Root handle, returning a plain JS
    * summary object.
    *
    * @param root The opaque Root handle from parseString
    * @return Plain JS object with kind, isEmpty, domains,
    *         location
    */
  @JSExport("inspectRoot")
  def inspectRoot(root: Root): js.Dynamic =
    rootToJsObject(root)
  end inspectRoot

  /** Parse arbitrary RIDDL definitions (nebula). */
  @JSExport("parseNebula")
  def parseNebula(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic =
    toJsResult(
      RiddlLib.parseNebula(source, origin, verbose),
      nebulaToJsObject
    )
  end parseNebula

  /** Parse RIDDL source into a list of tokens for syntax
    * highlighting.
    */
  @JSExport("parseToTokens")
  def parseToTokens(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic =
    toJsResult(
      RiddlLib.parseToTokens(source, origin, verbose),
      tokensToJsArray
    )
  end parseToTokens

  /** Parse and validate RIDDL source, returning both syntax
    * and semantic errors.
    */
  @JSExport("validateString")
  def validateString(
    source: String,
    origin: String = "string",
    verbose: Boolean = false,
    noANSIMessages: Boolean = true
  ): js.Dynamic =
    val vr = RiddlLib.validateString(
      source, origin, verbose, noANSIMessages
    )
    js.Dynamic.literal(
      succeeded = vr.succeeded,
      parseErrors = formatMessagesAsArray(vr.parseErrors),
      validationMessages = js.Dynamic.literal(
        errors = formatMessagesAsArray(vr.errors),
        warnings = formatMessagesAsArray(vr.warnings),
        info = formatMessagesAsArray(vr.info),
        all = formatMessagesAsArray(vr.all)
      )
    )
  end validateString

  /** Parse and validate RIDDL source using quick mode.
    * Skips expensive streaming and handler classification
    * checks for faster interactive feedback.
    */
  @JSExport("validateStringQuick")
  def validateStringQuick(
    source: String,
    origin: String = "string",
    verbose: Boolean = false,
    noANSIMessages: Boolean = true
  ): js.Dynamic =
    val vr = RiddlLib.validateStringQuick(
      source, origin, verbose, noANSIMessages
    )
    js.Dynamic.literal(
      succeeded = vr.succeeded,
      parseErrors = formatMessagesAsArray(vr.parseErrors),
      validationMessages = js.Dynamic.literal(
        errors = formatMessagesAsArray(vr.errors),
        warnings = formatMessagesAsArray(vr.warnings),
        info = formatMessagesAsArray(vr.info),
        all = formatMessagesAsArray(vr.all)
      )
    )
  end validateStringQuick

  /** Create an IncrementalValidator for efficient repeated
    * validation. The validator caches results at the Context
    * level, re-validating only changed Contexts on subsequent
    * calls.
    */
  @JSExport("createIncrementalValidator")
  def createIncrementalValidator(): IncrementalValidator =
    RiddlLib.createIncrementalValidator()
  end createIncrementalValidator

  /** Validate using an IncrementalValidator. Parses the
    * source, then uses cached results for unchanged Contexts.
    */
  @JSExport("validateIncremental")
  def validateIncremental(
    validator: IncrementalValidator,
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic =
    val vr = RiddlLib.validateIncremental(
      validator, source, origin, verbose
    )
    js.Dynamic.literal(
      succeeded = vr.succeeded,
      parseErrors = formatMessagesAsArray(vr.parseErrors),
      validationMessages = js.Dynamic.literal(
        errors = formatMessagesAsArray(vr.errors),
        warnings = formatMessagesAsArray(vr.warnings),
        info = formatMessagesAsArray(vr.info),
        all = formatMessagesAsArray(vr.all)
      )
    )
  end validateIncremental

  /** Create a custom platform context with specific
    * options.
    */
  @JSExport("createContext")
  def createContext(
    showTimes: Boolean = false,
    showWarnings: Boolean = true,
    verbose: Boolean = false
  ): PlatformContext =
    val options = CommonOptions(
      showTimes = showTimes,
      showWarnings = showWarnings,
      verbose = verbose
    )
    val ctx = DOMPlatformContext()
    ctx.withOptions(options)(_ => ctx)
  end createContext

  /** Get version information about the RIDDL library. */
  @JSExport("version")
  def version: String = RiddlLib.version

  /** Get detailed build information. */
  @JSExport("buildInfo")
  def buildInfo: js.Dynamic =
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
      organizationHomepage =
        RiddlBuildInfo.organizationHomepage,
      builtAtString = RiddlBuildInfo.builtAtString,
      buildInstant = RiddlBuildInfo.buildInstant,
      isSnapshot = RiddlBuildInfo.isSnapshot
    )
  end buildInfo

  /** Format an array of error objects as a human-readable
    * string.
    */
  @JSExport("formatErrorArray")
  def formatErrorArray(
    errors: js.Array[js.Dynamic]
  ): String =
    errors.map { err =>
      val kind = err.kind.asInstanceOf[String]
      val message = err.message.asInstanceOf[String]
      val loc = err.location.asInstanceOf[js.Dynamic]
      val line = loc.line.asInstanceOf[Int]
      val col = loc.col.asInstanceOf[Int]
      s"[$kind] at line $line, column $col: $message"
    }.mkString("\n")
  end formatErrorArray

  /** Convert errors array to a simple array of strings. */
  @JSExport("errorsToStrings")
  def errorsToStrings(
    errors: js.Array[js.Dynamic]
  ): js.Array[String] =
    errors.map { err =>
      err.message.asInstanceOf[String]
    }
  end errorsToStrings

  /** Format build information as a human-readable string. */
  @JSExport("formatInfo")
  def formatInfo: String = RiddlLib.formatInfo

  /** Analyze RIDDL source for AI-friendly tips.
    *
    * Returns tips, filtered warnings, and errors from
    * the AIHelperPass pipeline. Tips suggest what to add
    * or fix to improve the model.
    *
    * @param source RIDDL source code
    * @param origin Origin identifier for error messages
    * @return {succeeded, value: {tips, errors, warnings,
    *         all}, errors?}
    */
  @JSExport("analyzeSourceForTips")
  def analyzeSourceForTips(
    source: String,
    origin: String = "string"
  ): js.Dynamic =
    toJsResult(
      RiddlLib.analyzeSourceForTips(source, origin),
      (msgs: Messages) => tipsToJsObject(msgs)
    )
  end analyzeSourceForTips

  /** Analyze a pre-parsed AST for AI-friendly tips.
    *
    * @param root An opaque Root AST from a prior parse
    * @return {succeeded, value: {tips, errors, warnings,
    *         all}, errors?}
    */
  @JSExport("analyzeForTips")
  def analyzeForTips(root: js.Any): js.Dynamic =
    val rootAST = root.asInstanceOf[Root]
    toJsResult(
      RiddlLib.analyzeForTips(rootAST),
      (msgs: Messages) => tipsToJsObject(msgs)
    )
  end analyzeForTips

  /** Convert categorized messages to a JS object */
  private def tipsToJsObject(
    msgs: Messages
  ): js.Dynamic =
    js.Dynamic.literal(
      tips = formatMessagesAsArray(msgs.justTips),
      errors = formatMessagesAsArray(msgs.justErrors),
      warnings = formatMessagesAsArray(
        msgs.justWarnings.filterNot(m =>
          m.isTip || m.isInfo
        )
      ),
      all = formatMessagesAsArray(msgs)
    )
  end tipsToJsObject

  /** Get a flat outline of all named definitions. */
  @JSExport("getOutline")
  def getOutline(
    source: String,
    origin: String = "string"
  ): js.Dynamic =
    toJsResult(
      RiddlLib.getOutline(source, origin),
      entries =>
        entries.map(outlineEntryToJs).toJSArray
    )
  end getOutline

  /** Get a recursive tree of all named definitions. */
  @JSExport("getTree")
  def getTree(
    source: String,
    origin: String = "string"
  ): js.Dynamic =
    toJsResult(
      RiddlLib.getTree(source, origin),
      nodes => nodes.map(treeNodeToJs).toJSArray
    )
  end getTree

  /** Get handler completeness classifications. */
  @JSExport("getHandlerCompleteness")
  def getHandlerCompleteness(
    source: String,
    origin: String = "string"
  ): js.Dynamic =
    toJsResult(
      RiddlLib.getHandlerCompleteness(source, origin),
      entries => entries.map { hc =>
        js.Dynamic.literal(
          handlerId = hc.handler.id.value,
          parentId = hc.parent.id.value,
          parentKind = hc.parent.kind,
          category = hc.category.toString,
          executableCount = hc.executableCount,
          promptCount = hc.promptCount,
          totalClauses = hc.totalClauses
        )
      }.toJSArray
    )
  end getHandlerCompleteness

  /** Get the message flow graph. */
  @JSExport("getMessageFlow")
  def getMessageFlow(
    source: String,
    origin: String = "string"
  ): js.Dynamic =
    toJsResult(
      RiddlLib.getMessageFlow(source, origin),
      mfo => js.Dynamic.literal(
        edges = mfo.edges.map { edge =>
          js.Dynamic.literal(
            producerId = edge.producer.id.value,
            producerKind = edge.producer.kind,
            consumerId = edge.consumer.id.value,
            consumerKind = edge.consumer.kind,
            messageTypeId = edge.messageType.map(_.id.value).getOrElse(""),
            mechanism = edge.mechanism.toString
          )
        }.toJSArray,
        edgeCount = mfo.edges.size
      )
    )
  end getMessageFlow

  /** Convert a parsed AST Root to BAST binary bytes.
    *
    * Returns a RiddlResult containing an Int8Array suitable
    * for structured-clone transfer (e.g., Electron IPC).
    */
  @JSExport("ast2bast")
  def ast2bast(root: Root): js.Dynamic =
    toJsResult(
      RiddlLib.ast2bast(root),
      bytes =>
        val buffer =
          new js.typedarray.ArrayBuffer(bytes.length)
        val view = new js.typedarray.Int8Array(buffer)
        var i = 0
        while i < bytes.length do
          view(i) = bytes(i)
          i += 1
        end while
        view.asInstanceOf[js.Any]
    )
  end ast2bast

  /** Deserialize BAST binary bytes to a flattened AST Root.
    *
    * Reads BAST binary data, converts to AST, flattens
    * Include/BASTImport wrappers, and returns an opaque Root.
    *
    * @param bytes BAST binary data as Int8Array
    * @return Result with opaque Root handle or errors
    */
  @JSExport("bast2FlatAST")
  def bast2FlatAST(
    bytes: js.typedarray.Int8Array
  ): js.Dynamic =
    val scalaBytes = Array.tabulate(bytes.length)(i =>
      bytes(i)
    )
    toJsResult(
      RiddlLib.bast2FlatAST(scalaBytes),
      root => root.asInstanceOf[js.Any]
    )
  end bast2FlatAST

  /** Convert an AST Root to RIDDL source text.
    *
    * Runs PrettifyPass to regenerate RIDDL source code from
    * the AST. Produces a single flattened string with all
    * definitions inline (no include directives).
    *
    * @param root The opaque Root handle from parseString
    *             or bast2FlatAST
    * @return RIDDL source code as a string
    */
  @JSExport("root2RiddlSource")
  def root2RiddlSource(root: Root): String =
    RiddlLib.root2RiddlSource(root)
  end root2RiddlSource

  // ── Scope-based parsing methods ───────────────────────

  /** Parse content as if inside a Domain body.
    *
    * Returns the Domain as an opaque handle. Use
    * `inspectRoot()` pattern or pass to other APIs.
    */
  @JSExport("parseAsDomain")
  def parseAsDomain(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic =
    toJsResult(
      RiddlLib.parseAsDomain(source, origin, verbose),
      d => d.asInstanceOf[js.Any]
    )
  end parseAsDomain

  /** Parse content as if inside a Context body. */
  @JSExport("parseAsContext")
  def parseAsContext(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic =
    toJsResult(
      RiddlLib.parseAsContext(source, origin, verbose),
      c => c.asInstanceOf[js.Any]
    )
  end parseAsContext

  /** Parse content as if inside an Entity body. */
  @JSExport("parseAsEntity")
  def parseAsEntity(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic =
    toJsResult(
      RiddlLib.parseAsEntity(source, origin, verbose),
      e => e.asInstanceOf[js.Any]
    )
  end parseAsEntity

  /** Parse content as if inside an Epic body.
    *
    * @param source The RIDDL source containing epic body content
    * @param userStory Opaque UserStory handle from a parent parse
    * @param origin Origin identifier for error messages
    * @param verbose Enable verbose failure messages
    */
  @JSExport("parseAsEpic")
  def parseAsEpic(
    source: String,
    userStory: js.Any,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic =
    toJsResult(
      RiddlLib.parseAsEpic(
        source,
        userStory.asInstanceOf[UserStory],
        origin, verbose
      ),
      e => e.asInstanceOf[js.Any]
    )
  end parseAsEpic

  /** Parse content as if inside a Streamlet body.
    *
    * @param source The RIDDL source containing streamlet body
    * @param shape Opaque StreamletShape from a parent parse
    * @param inlets Opaque Inlet array from a parent parse
    * @param outlets Opaque Outlet array from a parent parse
    * @param origin Origin identifier for error messages
    * @param verbose Enable verbose failure messages
    */
  @JSExport("parseAsStreamlet")
  def parseAsStreamlet(
    source: String,
    shape: js.Any,
    inlets: js.Array[js.Any],
    outlets: js.Array[js.Any],
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic =
    toJsResult(
      RiddlLib.parseAsStreamlet(
        source,
        shape.asInstanceOf[StreamletShape],
        inlets.toSeq.map(_.asInstanceOf[Inlet]),
        outlets.toSeq.map(_.asInstanceOf[Outlet]),
        origin, verbose
      ),
      s => s.asInstanceOf[js.Any]
    )
  end parseAsStreamlet

  /** Parse content as if inside a Module body. */
  @JSExport("parseAsModule")
  def parseAsModule(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic =
    toJsResult(
      RiddlLib.parseAsModule(source, origin, verbose),
      m => m.asInstanceOf[js.Any]
    )
  end parseAsModule

  /** Parse content as if inside an Adaptor body. */
  @JSExport("parseAsAdaptor")
  def parseAsAdaptor(
    source: String,
    direction: js.Any,
    contextRef: js.Any,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic =
    toJsResult(
      RiddlLib.parseAsAdaptor(
        source,
        direction.asInstanceOf[AdaptorDirection],
        contextRef.asInstanceOf[ContextRef],
        origin, verbose
      ),
      a => a.asInstanceOf[js.Any]
    )
  end parseAsAdaptor

  /** Parse content as if inside a Projector body. */
  @JSExport("parseAsProjector")
  def parseAsProjector(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic =
    toJsResult(
      RiddlLib.parseAsProjector(source, origin, verbose),
      p => p.asInstanceOf[js.Any]
    )
  end parseAsProjector

  /** Parse content as if inside a Repository body. */
  @JSExport("parseAsRepository")
  def parseAsRepository(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic =
    toJsResult(
      RiddlLib.parseAsRepository(source, origin, verbose),
      r => r.asInstanceOf[js.Any]
    )
  end parseAsRepository

  /** Parse content as if inside a Saga body. */
  @JSExport("parseAsSaga")
  def parseAsSaga(
    source: String,
    sagaInput: js.UndefOr[js.Any] = js.undefined,
    sagaOutput: js.UndefOr[js.Any] = js.undefined,
    origin: String = "string",
    verbose: Boolean = false
  ): js.Dynamic =
    toJsResult(
      RiddlLib.parseAsSaga(
        source,
        sagaInput.toOption.map(
          _.asInstanceOf[Aggregation]
        ),
        sagaOutput.toOption.map(
          _.asInstanceOf[Aggregation]
        ),
        origin, verbose
      ),
      s => s.asInstanceOf[js.Any]
    )
  end parseAsSaga

  /** Get entity lifecycle (state machine) data. */
  @JSExport("getEntityLifecycles")
  def getEntityLifecycles(
    source: String,
    origin: String = "string"
  ): js.Dynamic =
    toJsResult(
      RiddlLib.getEntityLifecycles(source, origin),
      lifecycles => lifecycles.map { case (entity, lc) =>
        js.Dynamic.literal(
          entityId = entity.id.value,
          states = lc.states.map(s =>
            s.id.value
          ).toJSArray,
          transitions = lc.transitions.map { t =>
            js.Dynamic.literal(
              fromState = t.fromState.map(_.id.value)
                .getOrElse("*"),
              toState = t.toState.id.value,
              trigger = t.trigger.id.value
            )
          }.toJSArray,
          initialState = lc.initialState
            .map(_.id.value).getOrElse(""),
          terminalStates = lc.terminalStates
            .map(_.id.value).toJSArray
        )
      }.toJSArray
    )
  end getEntityLifecycles
}
