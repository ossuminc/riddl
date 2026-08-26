/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.{
  Adaptor,
  AdaptorDirection,
  Aggregation,
  Author,
  Context,
  ContextRef,
  Domain,
  Entity,
  Epic,
  Identifier,
  Inlet,
  Module,
  Outlet,
  Projector,
  Repository,
  Root,
  RootContents,
  Saga,
  Streamlet,
  StreamletShape,
  Token,
  UserStory
}
import com.ossuminc.riddl.language.{At, Contents, Messages, toSeq}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{
  BASTOutput,
  BASTWriterPass,
  IncrementalValidator,
  Pass,
  PassCreators,
  PassInput,
  PassOptions,
  PassesOutput,
  PassesResult,
  OutlinePass,
  OutlineOutput,
  OutlineEntry,
  TreePass,
  TreeOutput,
  TreeNode
}
import com.ossuminc.riddl.passes.analysis.{
  EntityLifecycle,
  EntityLifecycleOutput,
  EntityLifecyclePass,
  MessageFlowOutput,
  MessageFlowPass,
  RootComparison,
  RootSimilarity
}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.passes.transforms.FlattenPass
import com.ossuminc.riddl.passes.validate.{HandlerCompleteness, ValidationOutput, ValidationPass}
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext, RiddlBuildInfo, URL}
import com.ossuminc.riddl.json.{JsonAstBuilder, JsonifierOutput, JsonifierPass, JsonModel}

import scala.util.control.NonFatal

/** Cross-platform core API for RIDDL parsing, validation, and AST manipulation. Usable on JVM, JS,
  * and Native.
  *
  * All methods require a `PlatformContext` via Scala 3 `using` clause. Each platform provides a
  * default given instance in `com.ossuminc.riddl.utils.pc`.
  */
/** A single minimal replacement in a source file, as produced by [[RiddlLib.deprecationEdits]].
  *
  * Offsets are CHARACTER offsets into the file named by [[file]], half-open `[start, end)` — the
  * same basis as [[com.ossuminc.riddl.language.AST.RiddlValue.span]], so an editor that already
  * maps definitions to ranges can apply these without a second convention.
  *
  * @param start
  *   Character offset where the replaced text begins
  * @param end
  *   Character offset one past the last replaced character
  * @param replacement
  *   Text to put in that range
  * @param code
  *   The [[com.ossuminc.riddl.language.Messages.DeprecationCode]] this edit resolves, so a UI can
  *   group edits and explain what it is about to change
  * @param file
  *   The file the offsets refer to
  */
case class SourceEdit(
  start: Int,
  end: Int,
  replacement: String,
  code: String,
  file: String
)

trait RiddlLib:

  /** Parse a RIDDL source string and return the AST Root.
    *
    * @param source
    *   The RIDDL source code to parse
    * @param origin
    *   Origin identifier (e.g., filename) for error messages
    * @param verbose
    *   Enable verbose failure messages
    * @return
    *   Success(Root) on success, Failure(Messages) on failure
    */
  def parseString(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[Root]

  /** Build a RIDDL AST Root from a JSON document.
    *
    * The JSON describes a RIDDL model using the schema in [[com.ossuminc.riddl.json.JsonModel]]; it
    * is mapped onto the AST correct-by-construction, applying RIDDL's required type-expression
    * defaults. The returned `Root` is then validated and/or prettified by the existing machinery —
    * there is no JSON-specific validation path. References are emitted as path identifiers and
    * resolved later by the standard passes.
    *
    * @param json
    *   The JSON model document
    * @param origin
    *   Origin identifier (e.g., filename) for error messages
    * @return
    *   Success(Root) on success, Failure(Messages) on malformed JSON or a builder-level error
    *   (e.g., missing `Id.entity`)
    */
  def parseJson(
    json: String,
    origin: String = "string"
  )(using PlatformContext): RiddlResult[Root]

  /** As [[parseJson]], plus the non-fatal messages the build produced.
    *
    * `RiddlResult.Success` carries no messages, so a document that loads correctly but uses a
    * deprecated shape has nowhere to say so through [[parseJson]]. Currently reports one
    * `Deprecation` naming the container kinds still using the per-kind content arrays rather than
    * the ordered `contents` array; those arrays cannot express the order of definitions within
    * their parent, so a model read from them does not reproduce its source exactly.
    */
  def parseJsonWithMessages(
    json: String,
    origin: String = "string"
  )(using PlatformContext): (RiddlResult[Root], Messages)

  /** Parse arbitrary RIDDL definitions (nebula).
    *
    * A nebula is a collection of RIDDL definitions that may not form a complete, valid Root. The
    * anonymous `nebula` surface is DEPRECATED: parsing emits one `[deprecated]` message and the
    * result is a [[AST.Module]] with the synthetic id `AST.Module.syntheticId`.
    */
  def parseNebula(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[Module]

  /** Parse RIDDL source into a list of tokens for syntax highlighting.
    */
  def parseToTokens(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[List[Token]]

  /** Flatten Include and BASTImport wrapper nodes from the AST. Modifies the Root in-place and
    * returns the same object.
    */
  def flattenAST(
    root: Root
  )(using PlatformContext): Root

  /** Parse and validate RIDDL source, returning categorized messages.
    */
  def validateString(
    source: String,
    origin: String = "string",
    verbose: Boolean = false,
    noANSIMessages: Boolean = true
  )(using PlatformContext): RiddlLib.ValidateResult

  /** Parse and validate RIDDL source using quick mode. Skips expensive streaming analysis and
    * handler classification for faster interactive feedback. Messages are a strict subset of full
    * validation.
    */
  def validateStringQuick(
    source: String,
    origin: String = "string",
    verbose: Boolean = false,
    noANSIMessages: Boolean = true
  )(using PlatformContext): RiddlLib.ValidateResult

  /** Validate an already-built AST Root with the standard passes.
    *
    * Convenience for callers (e.g. [[parseJson]]) that hold a `Root` and want categorized
    * validation messages without round-tripping through text.
    */
  def validateRoot(
    root: Root
  )(using PlatformContext): RiddlLib.ValidateResult

  /** Get a flat outline of all named definitions. */
  def getOutline(
    source: String,
    origin: String = "string"
  )(using PlatformContext): RiddlResult[Seq[OutlineEntry]]

  /** Get a recursive tree of all named definitions. */
  def getTree(
    source: String,
    origin: String = "string"
  )(using PlatformContext): RiddlResult[Seq[TreeNode]]

  /** Compute a deterministic, model-free structural similarity between two `Root` ASTs.
    *
    * Matches on definition `kind` + fuzzy (location- and case-independent) name, never on
    * `Definition.equals`/`hashCode`. Returns a
    * [[com.ossuminc.riddl.passes.analysis.RootSimilarity]] carrying per-kind counts,
    * matched/unmatched name lists, structural metrics (depth, breadth), and an overall weighted
    * score in `[0.0, 1.0]` (`1.0` for identical inputs). See `RootComparison` for the weighting.
    */
  def compareRoots(a: Root, b: Root)(using PlatformContext): RootSimilarity

  /** Render [[compareRoots]] between two `Root` ASTs as a Markdown report: a per-kind count table
    * (a vs b), matched/unmatched name lists, structural metrics, and the overall score.
    */
  def similarityMarkdown(a: Root, b: Root)(using PlatformContext): String

  /** Get handler completeness classifications from validation.
    *
    * Runs the standard pass pipeline and returns the handler completeness data from
    * ValidationOutput.
    */
  def getHandlerCompleteness(
    source: String,
    origin: String = "string"
  )(using PlatformContext): RiddlResult[Seq[HandlerCompleteness]]

  /** Get the message flow graph for a RIDDL model.
    *
    * Runs standard passes plus the MessageFlowPass to build a directed graph of message producers
    * and consumers.
    */
  def getMessageFlow(
    source: String,
    origin: String = "string"
  )(using PlatformContext): RiddlResult[MessageFlowOutput]

  /** Get entity lifecycle (state machine) data.
    *
    * Runs standard passes plus the EntityLifecyclePass to extract state machines from entities with
    * multiple states.
    */
  def getEntityLifecycles(
    source: String,
    origin: String = "string"
  )(using PlatformContext): RiddlResult[Map[Entity, EntityLifecycle]]

  /** Convert a parsed AST Root to BAST binary bytes.
    *
    * Runs BASTWriterPass to serialize the AST into the compact binary format for efficient storage
    * or IPC transport.
    *
    * @param root
    *   The parsed AST Root
    * @return
    *   Success(bytes) or Failure with diagnostics
    */
  def ast2bast(
    root: Root
  )(using PlatformContext): RiddlResult[Array[Byte]]

  /** Deserialize BAST binary bytes to a flattened AST Root.
    *
    * Reads BAST binary data, converts the resulting root Module to a Root (filtering to valid
    * RootContents), then flattens Include/BASTImport wrapper nodes.
    *
    * @param bytes
    *   The BAST binary data
    * @return
    *   Success(Root) on success, Failure(Messages) on failure
    */
  def bast2FlatAST(
    bytes: Array[Byte]
  )(using PlatformContext): RiddlResult[Root]

  /** Read BAST bytes back into a Root, WITHOUT flattening.
    *
    * BAST's serialization root is a [[Module]], but what a consumer serialized was a [[Root]], so
    * every consumer otherwise has to know which contents are Root-legal AND that top-level
    * `Include` wrappers must be recursed into — miss that and whole included files vanish with no
    * diagnostic. That knowledge belongs here, not in each tool.
    *
    * Use this rather than [[bast2FlatAST]] when you need the include structure intact — a
    * multi-file editor deciding which file to write a change into needs the unflattened tree.
    * Flatten afterwards if you want the single-file view.
    *
    * @param bytes
    *   The BAST binary data
    * @return
    *   Success(Root) preserving Include structure, Failure(Messages) on failure
    */
  def bast2Root(
    bytes: Array[Byte]
  )(using PlatformContext): RiddlResult[Root]

  /** Convert a parsed AST Root to RIDDL source text.
    *
    * Runs PrettifyPass with flatten=true to regenerate RIDDL source code from the AST as a single
    * self-contained string with all definitions inline (no include directives).
    *
    * @param root
    *   The parsed AST Root
    * @return
    *   RIDDL source code as a string
    */
  def root2RiddlSource(
    root: Root
  )(using PlatformContext): String

  /** The MINIMAL edits that resolve mechanically-fixable deprecations, without reformatting.
    *
    * [[root2RiddlSource]] is the mechanical fixer, but it rewrites the whole file: a user with
    * carefully arranged source gets a wholesale reformat as the price of fixing three `reply`
    * keywords, which makes the offer easy to decline. This returns just the substitutions, so an
    * editor can apply them through its own edit API and keep undo/redo coherent.
    *
    * Edits are returned in DESCENDING start order, so applying them in sequence never invalidates a
    * later offset. Applying them in ascending order without adjusting offsets corrupts the file.
    *
    * Only deprecations whose [[com.ossuminc.riddl.language.RuleId.mechanicalFix]] is set appear
    * here — those
    * whose location covers exactly the offending keyword. Deprecations needing an insertion
    * elsewhere, or a human decision, are deliberately absent rather than guessed at; compare the
    * result against `justDeprecations` to report what remains as hand work.
    *
    * @param source
    *   The RIDDL source text
    * @param origin
    *   A name for the source, used for diagnostics and reported on each edit
    * @return
    *   Success(edits) — possibly empty — or Failure(Messages) if the source does not parse
    */
  def deprecationEdits(
    source: String,
    origin: String
  )(using PlatformContext): RiddlResult[Seq[SourceEdit]]

  /** Serialize an AST Root to the JSON wire schema (the inverse of [[parseJson]]).
    *
    * Produces JSON that [[parseJson]] consumes; for any model in the supported subset,
    * `parseJson(root2Json(root))` re-validates identically. Lossless for the documented subset and
    * best-effort (non-crashing) beyond it.
    *
    * @param root
    *   The AST Root to serialize
    * @param pretty
    *   When true (default), pretty-print with indentation
    * @return
    *   JSON string in the `JsonModel` wire schema
    */
  def root2Json(
    root: Root,
    pretty: Boolean = true
  )(using PlatformContext): String

  /** Create an IncrementalValidator for efficient repeated validation of the same model with small
    * edits. The validator caches results at the Context level.
    */
  def createIncrementalValidator()(using
    PlatformContext
  ): IncrementalValidator

  /** Validate using an IncrementalValidator, parsing the source first. Reuses cached results for
    * unchanged Contexts.
    */
  def validateIncremental(
    validator: IncrementalValidator,
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlLib.ValidateResult

  /** Parse content as if inside a Domain body.
    *
    * @param source
    *   The RIDDL source containing domain-level definitions (contexts, types, epics, etc.)
    * @param origin
    *   Origin identifier for error messages
    * @param verbose
    *   Enable verbose failure messages
    * @return
    *   Success(Domain) or Failure(Messages)
    */
  def parseAsDomain(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[Domain]

  /** Parse content as if inside a Context body.
    *
    * @param source
    *   The RIDDL source containing context-level definitions (entities, handlers, types, etc.)
    * @param origin
    *   Origin identifier for error messages
    * @param verbose
    *   Enable verbose failure messages
    * @return
    *   Success(Context) or Failure(Messages)
    */
  def parseAsContext(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[Context]

  /** Parse content as if inside an Entity body.
    *
    * @param source
    *   The RIDDL source containing entity-level definitions (states, handlers, types, etc.)
    * @param origin
    *   Origin identifier for error messages
    * @param verbose
    *   Enable verbose failure messages
    * @return
    *   Success(Entity) or Failure(Messages)
    */
  def parseAsEntity(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[Entity]

  /** Parse content as if inside an Epic body.
    *
    * The caller provides the UserStory from the parent Epic definition. The source should contain
    * use cases, types, and other epic body content.
    *
    * @param source
    *   The RIDDL source containing epic body content
    * @param userStory
    *   The UserStory from the parent Epic
    * @param origin
    *   Origin identifier for error messages
    * @param verbose
    *   Enable verbose failure messages
    * @return
    *   Success(Epic) or Failure(Messages)
    */
  def parseAsEpic(
    source: String,
    userStory: UserStory,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[Epic]

  /** Parse content as if inside a Streamlet body.
    *
    * The caller provides the shape, inlets, and outlets from the parent Streamlet definition. The
    * source should contain handlers, types, functions, and other processor content.
    *
    * @param source
    *   The RIDDL source containing streamlet body
    * @param shape
    *   The StreamletShape from the parent Streamlet
    * @param inlets
    *   The Inlet definitions from the parent
    * @param outlets
    *   The Outlet definitions from the parent
    * @param origin
    *   Origin identifier for error messages
    * @param verbose
    *   Enable verbose failure messages
    * @return
    *   Success(Streamlet) or Failure(Messages)
    */
  def parseAsStreamlet(
    source: String,
    shape: StreamletShape,
    inlets: Seq[Inlet],
    outlets: Seq[Outlet],
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[Streamlet]

  /** Parse content as if inside a Module body. */
  def parseAsModule(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[Module]

  /** Parse content as if inside an Adaptor body.
    *
    * The caller provides the direction and context reference from the parent Adaptor definition.
    */
  def parseAsAdaptor(
    source: String,
    direction: AdaptorDirection,
    contextRef: ContextRef,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[Adaptor]

  /** Parse content as if inside a Projector body. */
  def parseAsProjector(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[Projector]

  /** Parse content as if inside a Repository body. */
  def parseAsRepository(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[Repository]

  /** Parse content as if inside a Saga body.
    *
    * The caller provides the optional input/output aggregations from the parent Saga definition.
    */
  def parseAsSaga(
    source: String,
    sagaInput: Option[Aggregation] = None,
    sagaOutput: Option[Aggregation] = None,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[Saga]

  /** Analyze RIDDL source for AI-friendly tips.
    *
    * Runs the standard validation passes with
    * [[com.ossuminc.riddl.utils.CommonOptions.provideTips]] enabled and returns all resulting
    * messages. Each message carries a remediation `suggestion` describing how to fix the reported
    * condition.
    *
    * @param source
    *   The RIDDL source code to analyze
    * @param origin
    *   Origin identifier for error messages
    * @return
    *   All validation messages, each with its suggestion populated
    */
  @deprecated(
    "Use validateString with CommonOptions.provideTips = true (or `riddlc advise`); " +
      "tips are now remediation suggestions carried on every message.",
    "1.24.0"
  )
  def analyzeSourceForTips(
    source: String,
    origin: String = "string"
  )(using PlatformContext): RiddlResult[Messages]

  /** Analyze a pre-parsed AST for AI-friendly tips.
    *
    * @param root
    *   A previously parsed Root AST
    * @return
    *   All validation messages, each with its suggestion populated
    */
  @deprecated(
    "Use validateString with CommonOptions.provideTips = true (or `riddlc advise`); " +
      "tips are now remediation suggestions carried on every message.",
    "1.24.0"
  )
  def analyzeForTips(
    root: Root
  )(using PlatformContext): RiddlResult[Messages]

  /** Get the RIDDL library version string. */
  def version: String

  /** Get formatted build information. */
  def formatInfo: String
end RiddlLib

/** Default implementations of all RiddlLib methods. Call via `RiddlLib.parseString(...)` etc. with
  * a platform-specific `given PlatformContext` in scope.
  */
object RiddlLib extends RiddlLib:

  /** Result from full validation pipeline. */
  case class ValidateResult(
    succeeded: Boolean,
    parseErrors: Messages,
    errors: Messages,
    warnings: Messages,
    info: Messages,
    all: Messages
  )

  /** Convert an origin string to a URL for RiddlParserInput.
    */
  def originToURL(origin: String): URL =
    if origin.startsWith("/") then URL.fromFullPath(origin)
    else URL(URL.fileScheme, "", "", origin)
    end if
  end originToURL

  override def parseString(
    source: String,
    origin: String,
    verbose: Boolean
  )(using PlatformContext): RiddlResult[Root] =
    val input = RiddlParserInput(source, originToURL(origin))
    RiddlResult.fromEither(
      TopLevelParser.parseInput(input, verbose)
    )
  end parseString

  override def parseJson(
    json: String,
    origin: String
  )(using PlatformContext): RiddlResult[Root] = parseJsonWithMessages(json, origin)._1

  override def parseJsonWithMessages(
    json: String,
    origin: String
  )(using PlatformContext): (RiddlResult[Root], Messages) =
    val parsed: Either[Messages, JsonModel.RootDto] =
      try Right(JsonModel.readRoot(json))
      catch
        case NonFatal(e) =>
          Left(
            List(
              Messages.error(
                s"Invalid JSON for RIDDL model ($origin): ${e.getMessage}"
              )
            )
          )
    parsed match
      case Left(errors) => (RiddlResult.fromEither(Left(errors)), Nil)
      case Right(dto)   =>
        // The JSON TEXT goes through so a document declaring `basis: "document"` can resolve its
        // own offsets against itself, giving exact line/col in diagnostics.
        val (result, messages) = JsonAstBuilder.buildWithMessages(dto, json)
        (RiddlResult.fromEither(result), unknownKeyWarnings(json, origin) ++ messages)
  end parseJsonWithMessages

  /** A `Warning` for every key in the document that NO reader accepts (Reid's ruling, 2026-08-16).
    *
    * Warning rather than Error, deliberately, and this is the repo's warn-then-flip sequencing: it
    * breaks no existing producer, surfaces a typo immediately, and leaves the decision about
    * rejecting outright to be made with evidence.
    *
    * It runs on the RAW TEXT, before the readers, because that is the only place both reader layers
    * can be covered at once — the derived `macroRW` readers, which are most of them, drop unknown
    * keys inside upickle where nothing of ours can observe it.
    *
    * A key that is MISSING is not reported and must never be: readers use `m.get`, so a document
    * predating a field still reads, which is the schema-evolution direction that has to keep
    * working. See `JsonUnknownKeyTest`'s control case.
    */
  private def unknownKeyWarnings(json: String, origin: String): Messages =
    try
      JsonModel
        .unknownKeys(ujson.read(json))
        .map { case (key, path) =>
          Messages.warning(
            s"JSON key '$key' at '$path' is not recognized by any RIDDL reader ($origin), " +
              "so its value is ignored",
            suggestion =
              "Check the spelling against JSON_INPUT.md; an obsolete key should be removed."
          )
        }
        .toList
    catch case NonFatal(_) => Nil // unreadable JSON is the readers' error to report, not ours
  end unknownKeyWarnings

  override def parseNebula(
    source: String,
    origin: String,
    verbose: Boolean
  )(using PlatformContext): RiddlResult[Module] =
    val input = RiddlParserInput(source, originToURL(origin))
    RiddlResult.fromEither(
      TopLevelParser.parseNebula(input, verbose)
    )
  end parseNebula

  override def parseToTokens(
    source: String,
    origin: String,
    verbose: Boolean
  )(using PlatformContext): RiddlResult[List[Token]] =
    val input = RiddlParserInput(source, originToURL(origin))
    RiddlResult.fromEither(
      TopLevelParser.parseToTokens(input, verbose)
    )
  end parseToTokens

  override def flattenAST(
    root: Root
  )(using pc: PlatformContext): Root =
    val passInput = PassInput(root)
    Pass.runThesePasses(
      passInput,
      Seq(FlattenPass.creator(PassOptions.empty))
    )
    root
  end flattenAST

  private def doValidate(
    source: String,
    origin: String,
    verbose: Boolean,
    noANSIMessages: Boolean,
    passes: PassCreators
  )(using pc: PlatformContext): ValidateResult =
    val options = CommonOptions(
      verbose = verbose,
      noANSIMessages = noANSIMessages
    )
    pc.withOptions(options) { _ =>
      val input = RiddlParserInput(source, originToURL(origin))
      TopLevelParser.parseInput(input, verbose) match
        case Right(root) =>
          resultOf(Pass.runThesePasses(PassInput(root), passes).messages)
        case Left(parseMessages) =>
          parseFailure(parseMessages)
      end match
    }
  end doValidate

  override def validateString(
    source: String,
    origin: String,
    verbose: Boolean,
    noANSIMessages: Boolean
  )(using PlatformContext): ValidateResult =
    doValidate(source, origin, verbose, noANSIMessages, Pass.standardPasses)
  end validateString

  override def validateStringQuick(
    source: String,
    origin: String,
    verbose: Boolean,
    noANSIMessages: Boolean
  )(using PlatformContext): ValidateResult =
    doValidate(source, origin, verbose, noANSIMessages, Pass.quickValidationPasses)
  end validateStringQuick

  /** Categorize a pass run's messages into a ValidateResult. */
  private def summarize(messages: Messages): ValidateResult =
    val errs = messages.filter(_.isError).distinct
    val warns = messages.filter(_.isWarning).distinct
    val infos = messages.filter(_.kind.severity == 0).distinct
    ValidateResult(
      succeeded = !messages.hasErrors,
      parseErrors = List.empty,
      errors = errs,
      warnings = warns,
      info = infos,
      all = messages
    )
  end summarize

  /** An empty failed ValidateResult (used when a pass run throws). */
  private val noValidateResult: ValidateResult =
    ValidateResult(
      succeeded = false,
      parseErrors = List.empty,
      errors = List.empty,
      warnings = List.empty,
      info = List.empty,
      all = List.empty
    )

  /** A failed ValidateResult carrying parse errors only. */
  private def parseFailure(parseMessages: Messages): ValidateResult =
    noValidateResult.copy(parseErrors = parseMessages)

  /** Categorize the messages from a pass run, mapping any thrown exception to an empty failure. The
    * argument is by-name so the whole run is guarded.
    */
  private def resultOf(messages: => Messages): ValidateResult =
    try summarize(messages)
    catch case NonFatal(_) => noValidateResult

  override def validateRoot(
    root: Root
  )(using pc: PlatformContext): ValidateResult =
    pc.withOptions(CommonOptions(noANSIMessages = true)) { _ =>
      resultOf(Pass.runThesePasses(PassInput(root), Pass.standardPasses).messages)
    }
  end validateRoot

  override def getOutline(
    source: String,
    origin: String
  )(using PlatformContext): RiddlResult[Seq[OutlineEntry]] =
    val rpi = RiddlParserInput(source, originToURL(origin))
    val parseResult = TopLevelParser.parseInput(rpi)
    RiddlResult.fromEither(parseResult).flatMap { root =>
      val passInput = PassInput(root)
      val passesResult = Pass.runThesePasses(
        passInput,
        Seq(OutlinePass.creator())
      )
      passesResult.outputs
        .outputOf[OutlineOutput](OutlinePass.name) match
        case Some(outlineOutput) =>
          RiddlResult.Success(outlineOutput.entries)
        case None =>
          RiddlResult.Failure(List.empty)
      end match
    }
  end getOutline

  override def getTree(
    source: String,
    origin: String
  )(using PlatformContext): RiddlResult[Seq[TreeNode]] =
    val rpi = RiddlParserInput(source, originToURL(origin))
    val parseResult = TopLevelParser.parseInput(rpi)
    RiddlResult.fromEither(parseResult).flatMap { root =>
      val passInput = PassInput(root)
      val passesResult = Pass.runThesePasses(
        passInput,
        Seq(TreePass.creator())
      )
      passesResult.outputs
        .outputOf[TreeOutput](TreePass.name) match
        case Some(treeOutput) =>
          RiddlResult.Success(treeOutput.tree)
        case None =>
          RiddlResult.Failure(List.empty)
      end match
    }
  end getTree

  override def compareRoots(
    a: Root,
    b: Root
  )(using PlatformContext): RootSimilarity =
    RootComparison.compareRoots(a, b)
  end compareRoots

  override def similarityMarkdown(
    a: Root,
    b: Root
  )(using PlatformContext): String =
    RootComparison.similarityMarkdown(a, b)
  end similarityMarkdown

  override def getHandlerCompleteness(
    source: String,
    origin: String
  )(using PlatformContext): RiddlResult[Seq[HandlerCompleteness]] =
    val rpi = RiddlParserInput(source, originToURL(origin))
    val parseResult = TopLevelParser.parseInput(rpi)
    RiddlResult.fromEither(parseResult).flatMap { root =>
      val passInput = PassInput(root)
      val passesResult = Pass.runStandardPasses(passInput)
      passesResult.outputs
        .outputOf[ValidationOutput](
          ValidationPass.name
        ) match
        case Some(vo) =>
          RiddlResult.Success(vo.handlerCompleteness)
        case None =>
          RiddlResult.Failure(List.empty)
      end match
    }
  end getHandlerCompleteness

  override def getMessageFlow(
    source: String,
    origin: String
  )(using PlatformContext): RiddlResult[MessageFlowOutput] =
    val rpi = RiddlParserInput(source, originToURL(origin))
    val parseResult = TopLevelParser.parseInput(rpi)
    RiddlResult.fromEither(parseResult).flatMap { root =>
      val passInput = PassInput(root)
      val passes =
        Pass.standardPasses :+ MessageFlowPass.creator()
      val passesResult =
        Pass.runThesePasses(passInput, passes)
      passesResult.outputs
        .outputOf[MessageFlowOutput](
          MessageFlowPass.name
        ) match
        case Some(mfo) =>
          RiddlResult.Success(mfo)
        case None =>
          RiddlResult.Failure(List.empty)
      end match
    }
  end getMessageFlow

  override def getEntityLifecycles(
    source: String,
    origin: String
  )(using PlatformContext): RiddlResult[Map[Entity, EntityLifecycle]] =
    val rpi = RiddlParserInput(source, originToURL(origin))
    val parseResult = TopLevelParser.parseInput(rpi)
    RiddlResult.fromEither(parseResult).flatMap { root =>
      val passInput = PassInput(root)
      val passes =
        Pass.standardPasses :+ EntityLifecyclePass.creator()
      val passesResult =
        Pass.runThesePasses(passInput, passes)
      passesResult.outputs
        .outputOf[EntityLifecycleOutput](
          EntityLifecyclePass.name
        ) match
        case Some(elo) =>
          RiddlResult.Success(elo.lifecycles)
        case None =>
          RiddlResult.Failure(List.empty)
      end match
    }
  end getEntityLifecycles

  override def ast2bast(
    root: Root
  )(using PlatformContext): RiddlResult[Array[Byte]] =
    val passInput = PassInput(root)
    val passesResult = Pass.runThesePasses(
      passInput,
      Seq(BASTWriterPass.creator())
    )
    passesResult.outputs
      .outputOf[BASTOutput](BASTWriterPass.name) match
      case Some(bastOutput) =>
        RiddlResult.Success(bastOutput.bytes)
      case None =>
        RiddlResult.Failure(
          List(
            Messages.error("BASTWriterPass produced no output")
          )
        )
    end match
  end ast2bast

  override def bast2FlatAST(
    bytes: Array[Byte]
  )(using PlatformContext): RiddlResult[Root] =
    RiddlResult.fromEither(BASTReader.read(bytes)).map { module =>
      flattenAST(Module.toRoot(module))
    }
  end bast2FlatAST

  override def bast2Root(
    bytes: Array[Byte]
  )(using PlatformContext): RiddlResult[Root] =
    RiddlResult.fromEither(BASTReader.read(bytes)).map(Module.toRoot)
  end bast2Root

  override def deprecationEdits(
    source: String,
    origin: String
  )(using PlatformContext): RiddlResult[Seq[SourceEdit]] =
    val rpi = RiddlParserInput(source, URL.fromCwdPath(origin), "deprecationEdits")
    TopLevelParser.parseInputWithMessages(rpi) match
      case Left(errors) => RiddlResult.Failure(errors)
      case Right((_, msgs)) =>
        // Reads the fix off the RULE rather than looking its code up in a side table: a rule that
        // carries its own mechanical replacement cannot fall out of step with one.
        val edits = msgs.justDeprecations.toSeq.flatMap { m =>
          m.ruleId.flatMap(rule => rule.mechanicalFix.map(rule.code -> _)).map {
            case (code, fix) =>
              // A COMPUTED fix needs the text it matched, and this function has the source -- so it
              // slices the span rather than dropping the fix. `quoted-constant-literal` (`"5"` ->
              // `5`) is only expressible this way, which is why [1.16] made `Fix` a sum type
              // instead of widening the published `Map[String, String]` that cannot carry it.
              val matched = source.slice(m.loc.offset, m.loc.endOffset)
              SourceEdit(m.loc.offset, m.loc.endOffset, fix(matched), code, origin)
          }
        }
        // Descending, so applying them in order never shifts an offset still to be used.
        RiddlResult.Success(edits.sortBy(-_.start))
    end match
  end deprecationEdits

  override def root2RiddlSource(
    root: Root
  )(using PlatformContext): String =
    val passInput = PassInput(root)
    val passes = Seq(
      PrettifyPass.creator(
        PrettifyPass.Options(flatten = true, inputDir = "")
      )
    )
    val result = Pass.runThesePasses(passInput, passes)
    result.outputs
      .outputOf[PrettifyOutput](PrettifyPass.name) match
      case Some(po) => po.state.filesAsString
      case None     => ""
    end match
  end root2RiddlSource

  override def root2Json(
    root: Root,
    pretty: Boolean
  )(using PlatformContext): String =
    val result = Pass.runThesePasses(PassInput(root), Seq(JsonifierPass.creator))
    val dto = result.outputs.outputOf[JsonifierOutput](JsonifierPass.name) match
      case Some(o) => o.rootDto
      case None    => JsonModel.RootDto()
    JsonModel.writeRoot(dto, indent = if pretty then 2 else -1)
  end root2Json

  override def createIncrementalValidator()(using
    PlatformContext
  ): IncrementalValidator =
    new IncrementalValidator()
  end createIncrementalValidator

  override def validateIncremental(
    validator: IncrementalValidator,
    source: String,
    origin: String,
    verbose: Boolean
  )(using pc: PlatformContext): ValidateResult =
    val options = CommonOptions(verbose = verbose)
    pc.withOptions(options) { _ =>
      val input = RiddlParserInput(source, originToURL(origin))
      TopLevelParser.parseInput(input, verbose) match
        case Right(root)         => resultOf(validator.validate(root).messages)
        case Left(parseMessages) => parseFailure(parseMessages)
      end match
    }
  end validateIncremental

  override def parseAsDomain(
    source: String,
    origin: String,
    verbose: Boolean
  )(using PlatformContext): RiddlResult[Domain] =
    val input = RiddlParserInput(source, originToURL(origin))
    RiddlResult.fromEither(
      TopLevelParser.parseAsDomain(input, verbose)
    )
  end parseAsDomain

  override def parseAsContext(
    source: String,
    origin: String,
    verbose: Boolean
  )(using PlatformContext): RiddlResult[Context] =
    val input = RiddlParserInput(source, originToURL(origin))
    RiddlResult.fromEither(
      TopLevelParser.parseAsContext(input, verbose)
    )
  end parseAsContext

  override def parseAsEntity(
    source: String,
    origin: String,
    verbose: Boolean
  )(using PlatformContext): RiddlResult[Entity] =
    val input = RiddlParserInput(source, originToURL(origin))
    RiddlResult.fromEither(
      TopLevelParser.parseAsEntity(input, verbose)
    )
  end parseAsEntity

  override def parseAsEpic(
    source: String,
    userStory: UserStory,
    origin: String,
    verbose: Boolean
  )(using PlatformContext): RiddlResult[Epic] =
    val input = RiddlParserInput(source, originToURL(origin))
    RiddlResult.fromEither(
      TopLevelParser.parseAsEpic(input, userStory, verbose)
    )
  end parseAsEpic

  override def parseAsStreamlet(
    source: String,
    shape: StreamletShape,
    inlets: Seq[Inlet],
    outlets: Seq[Outlet],
    origin: String,
    verbose: Boolean
  )(using PlatformContext): RiddlResult[Streamlet] =
    val input = RiddlParserInput(source, originToURL(origin))
    RiddlResult.fromEither(
      TopLevelParser.parseAsStreamlet(
        input,
        shape,
        inlets,
        outlets,
        verbose
      )
    )
  end parseAsStreamlet

  override def parseAsModule(
    source: String,
    origin: String,
    verbose: Boolean
  )(using PlatformContext): RiddlResult[Module] =
    val input = RiddlParserInput(source, originToURL(origin))
    RiddlResult.fromEither(
      TopLevelParser.parseAsModule(input, verbose)
    )
  end parseAsModule

  override def parseAsAdaptor(
    source: String,
    direction: AdaptorDirection,
    contextRef: ContextRef,
    origin: String,
    verbose: Boolean
  )(using PlatformContext): RiddlResult[Adaptor] =
    val input = RiddlParserInput(source, originToURL(origin))
    RiddlResult.fromEither(
      TopLevelParser.parseAsAdaptor(
        input,
        direction,
        contextRef,
        verbose
      )
    )
  end parseAsAdaptor

  override def parseAsProjector(
    source: String,
    origin: String,
    verbose: Boolean
  )(using PlatformContext): RiddlResult[Projector] =
    val input = RiddlParserInput(source, originToURL(origin))
    RiddlResult.fromEither(
      TopLevelParser.parseAsProjector(input, verbose)
    )
  end parseAsProjector

  override def parseAsRepository(
    source: String,
    origin: String,
    verbose: Boolean
  )(using PlatformContext): RiddlResult[Repository] =
    val input = RiddlParserInput(source, originToURL(origin))
    RiddlResult.fromEither(
      TopLevelParser.parseAsRepository(input, verbose)
    )
  end parseAsRepository

  override def parseAsSaga(
    source: String,
    sagaInput: Option[Aggregation],
    sagaOutput: Option[Aggregation],
    origin: String,
    verbose: Boolean
  )(using PlatformContext): RiddlResult[Saga] =
    val input = RiddlParserInput(source, originToURL(origin))
    RiddlResult.fromEither(
      TopLevelParser.parseAsSaga(
        input,
        sagaInput,
        sagaOutput,
        verbose
      )
    )
  end parseAsSaga

  @deprecated(
    "Use validateString with CommonOptions.provideTips = true (or `riddlc advise`); " +
      "tips are now remediation suggestions carried on every message.",
    "1.24.0"
  )
  override def analyzeSourceForTips(
    source: String,
    origin: String
  )(using pc: PlatformContext): RiddlResult[Messages] =
    val rpi = RiddlParserInput(source, originToURL(origin))
    TopLevelParser.parseInput(rpi) match
      case Left(parseErrors) =>
        RiddlResult.Failure(parseErrors)
      case Right(root) =>
        tipsFor(root)
  end analyzeSourceForTips

  override def analyzeForTips(
    root: Root
  )(using pc: PlatformContext): RiddlResult[Messages] =
    tipsFor(root)
  end analyzeForTips

  /** Run the standard passes with tip generation enabled so each message retains its remediation
    * suggestion, then return all messages. This replaces the former AIHelperPass, which produced
    * separate Tip messages.
    */
  private def tipsFor(root: Root)(using pc: PlatformContext): RiddlResult[Messages] =
    pc.withOptions(pc.options.copy(provideTips = true)) { _ =>
      val passInput = PassInput(root)
      val passesResult = Pass.runThesePasses(passInput, Pass.standardPasses)
      RiddlResult.Success(passesResult.messages)
    }
  end tipsFor

  override def version: String =
    RiddlBuildInfo.version

  override def formatInfo: String =
    import com.ossuminc.riddl.utils.InfoFormatter
    InfoFormatter.formatInfo
  end formatInfo
end RiddlLib
