/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.{
  Author, Domain, Entity, Module, Nebula, Root,
  RootContents, Token
}
import com.ossuminc.riddl.language.{At, Contents, Messages, toSeq}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{
  RiddlParserInput, TopLevelParser
}
import com.ossuminc.riddl.passes.{
  BASTOutput, BASTWriterPass, IncrementalValidator,
  Pass, PassCreators, PassInput, PassOptions, PassesOutput,
  OutlinePass, OutlineOutput, OutlineEntry,
  TreePass, TreeOutput, TreeNode
}
import com.ossuminc.riddl.passes.analysis.{
  EntityLifecycle, EntityLifecycleOutput, EntityLifecyclePass,
  MessageFlowOutput, MessageFlowPass
}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.passes.transforms.FlattenPass
import com.ossuminc.riddl.passes.validate.{
  HandlerCompleteness, ValidationOutput, ValidationPass
}
import com.ossuminc.riddl.utils.{
  CommonOptions, PlatformContext, RiddlBuildInfo, URL
}

/** Cross-platform core API for RIDDL parsing, validation,
  * and AST manipulation. Usable on JVM, JS, and Native.
  *
  * All methods require a `PlatformContext` via Scala 3 `using`
  * clause. Each platform provides a default given instance in
  * `com.ossuminc.riddl.utils.pc`.
  */
trait RiddlLib:

  /** Parse a RIDDL source string and return the AST Root.
    *
    * @param source The RIDDL source code to parse
    * @param origin Origin identifier (e.g., filename) for error
    *               messages
    * @param verbose Enable verbose failure messages
    * @return Success(Root) on success, Failure(Messages) on
    *         failure
    */
  def parseString(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[Root]

  /** Parse arbitrary RIDDL definitions (nebula).
    *
    * A nebula is a collection of RIDDL definitions that may
    * not form a complete, valid Root.
    */
  def parseNebula(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[Nebula]

  /** Parse RIDDL source into a list of tokens for syntax
    * highlighting.
    */
  def parseToTokens(
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlResult[List[Token]]

  /** Flatten Include and BASTImport wrapper nodes from the
    * AST. Modifies the Root in-place and returns the same
    * object.
    */
  def flattenAST(
    root: Root
  )(using PlatformContext): Root

  /** Parse and validate RIDDL source, returning categorized
    * messages.
    */
  def validateString(
    source: String,
    origin: String = "string",
    verbose: Boolean = false,
    noANSIMessages: Boolean = true
  )(using PlatformContext): RiddlLib.ValidateResult

  /** Parse and validate RIDDL source using quick mode.
    * Skips expensive streaming analysis and handler
    * classification for faster interactive feedback.
    * Messages are a strict subset of full validation.
    */
  def validateStringQuick(
    source: String,
    origin: String = "string",
    verbose: Boolean = false,
    noANSIMessages: Boolean = true
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

  /** Get handler completeness classifications from validation.
    *
    * Runs the standard pass pipeline and returns the handler
    * completeness data from ValidationOutput.
    */
  def getHandlerCompleteness(
    source: String,
    origin: String = "string"
  )(using PlatformContext): RiddlResult[Seq[HandlerCompleteness]]

  /** Get the message flow graph for a RIDDL model.
    *
    * Runs standard passes plus the MessageFlowPass to build
    * a directed graph of message producers and consumers.
    */
  def getMessageFlow(
    source: String,
    origin: String = "string"
  )(using PlatformContext): RiddlResult[MessageFlowOutput]

  /** Get entity lifecycle (state machine) data.
    *
    * Runs standard passes plus the EntityLifecyclePass to
    * extract state machines from entities with multiple states.
    */
  def getEntityLifecycles(
    source: String,
    origin: String = "string"
  )(using PlatformContext
  ): RiddlResult[Map[Entity, EntityLifecycle]]

  /** Convert a parsed AST Root to BAST binary bytes.
    *
    * Runs BASTWriterPass to serialize the AST into the compact
    * binary format for efficient storage or IPC transport.
    *
    * @param root The parsed AST Root
    * @return Success(bytes) or Failure with diagnostics
    */
  def ast2bast(
    root: Root
  )(using PlatformContext): RiddlResult[Array[Byte]]

  /** Deserialize BAST binary bytes to a flattened AST Root.
    *
    * Reads BAST binary data, converts the resulting Nebula to
    * a Root (filtering to valid RootContents), then flattens
    * Include/BASTImport wrapper nodes.
    *
    * @param bytes The BAST binary data
    * @return Success(Root) on success, Failure(Messages) on
    *         failure
    */
  def bast2FlatAST(
    bytes: Array[Byte]
  )(using PlatformContext): RiddlResult[Root]

  /** Convert a parsed AST Root to RIDDL source text.
    *
    * Runs PrettifyPass with flatten=true to regenerate RIDDL
    * source code from the AST as a single self-contained string
    * with all definitions inline (no include directives).
    *
    * @param root The parsed AST Root
    * @return RIDDL source code as a string
    */
  def root2RiddlSource(
    root: Root
  )(using PlatformContext): String

  /** Create an IncrementalValidator for efficient repeated
    * validation of the same model with small edits.
    * The validator caches results at the Context level.
    */
  def createIncrementalValidator()(
    using PlatformContext
  ): IncrementalValidator

  /** Validate using an IncrementalValidator, parsing the
    * source first. Reuses cached results for unchanged
    * Contexts.
    */
  def validateIncremental(
    validator: IncrementalValidator,
    source: String,
    origin: String = "string",
    verbose: Boolean = false
  )(using PlatformContext): RiddlLib.ValidateResult

  /** Get the RIDDL library version string. */
  def version: String

  /** Get formatted build information. */
  def formatInfo: String
end RiddlLib

/** Default implementations of all RiddlLib methods.
  * Call via `RiddlLib.parseString(...)` etc. with a
  * platform-specific `given PlatformContext` in scope.
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

  /** Convert an origin string to a URL for
    * RiddlParserInput.
    */
  def originToURL(origin: String): URL =
    if origin.startsWith("/") then
      URL.fromFullPath(origin)
    else
      URL(URL.fileScheme, "", "", origin)
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

  override def parseNebula(
    source: String,
    origin: String,
    verbose: Boolean
  )(using PlatformContext): RiddlResult[Nebula] =
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
      val input = RiddlParserInput(
        source, originToURL(origin)
      )
      val parseResult = TopLevelParser.parseInput(
        input, verbose
      )
      parseResult match
        case Right(root) =>
          try
            val passInput = PassInput(root)
            val passesResult =
              Pass.runThesePasses(passInput, passes)
            val messages = passesResult.messages
            val errs =
              messages.filter(_.isError).distinct
            val warns =
              messages.filter(_.isWarning).distinct
            val infos = messages
              .filter(_.kind.severity == 0).distinct
            ValidateResult(
              succeeded = !messages.hasErrors,
              parseErrors = List.empty,
              errors = errs,
              warnings = warns,
              info = infos,
              all = messages
            )
          catch
            case e: Exception =>
              ValidateResult(
                succeeded = false,
                parseErrors = List.empty,
                errors = List.empty,
                warnings = List.empty,
                info = List.empty,
                all = List.empty
              )
          end try
        case Left(parseMessages) =>
          ValidateResult(
            succeeded = false,
            parseErrors = parseMessages,
            errors = List.empty,
            warnings = List.empty,
            info = List.empty,
            all = List.empty
          )
      end match
    }
  end doValidate

  override def validateString(
    source: String,
    origin: String,
    verbose: Boolean,
    noANSIMessages: Boolean
  )(using PlatformContext): ValidateResult =
    doValidate(source, origin, verbose, noANSIMessages,
      Pass.standardPasses)
  end validateString

  override def validateStringQuick(
    source: String,
    origin: String,
    verbose: Boolean,
    noANSIMessages: Boolean
  )(using PlatformContext): ValidateResult =
    doValidate(source, origin, verbose, noANSIMessages,
      Pass.quickValidationPasses)
  end validateStringQuick

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

  override def getHandlerCompleteness(
    source: String,
    origin: String
  )(using PlatformContext
  ): RiddlResult[Seq[HandlerCompleteness]] =
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
  )(using PlatformContext
  ): RiddlResult[MessageFlowOutput] =
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
  )(using PlatformContext
  ): RiddlResult[Map[Entity, EntityLifecycle]] =
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
        RiddlResult.Failure(List(
          Messages.error("BASTWriterPass produced no output")
        ))
    end match
  end ast2bast

  override def bast2FlatAST(
    bytes: Array[Byte]
  )(using PlatformContext): RiddlResult[Root] =
    RiddlResult.fromEither(BASTReader.read(bytes)).map {
      nebula =>
        val rootItems: Seq[RootContents] =
          nebula.contents.toSeq.collect {
            case d: Domain => d
            case m: Module => m
            case a: Author => a
          }
        val root = Root(
          nebula.loc,
          Contents[RootContents](rootItems*)
        )
        flattenAST(root)
    }
  end bast2FlatAST

  override def root2RiddlSource(
    root: Root
  )(using PlatformContext): String =
    val passInput = PassInput(root)
    val passes = Seq(
      PrettifyPass.creator(
        PrettifyPass.Options(flatten = true)
      )
    )
    val result = Pass.runThesePasses(passInput, passes)
    result.outputs
      .outputOf[PrettifyOutput](PrettifyPass.name) match
      case Some(po) => po.state.filesAsString
      case None     => ""
    end match
  end root2RiddlSource

  override def createIncrementalValidator()(
    using PlatformContext
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
      val input = RiddlParserInput(
        source, originToURL(origin)
      )
      val parseResult = TopLevelParser.parseInput(
        input, verbose
      )
      parseResult match
        case Right(root) =>
          try
            val passesResult = validator.validate(root)
            val messages = passesResult.messages
            val errs =
              messages.filter(_.isError).distinct
            val warns =
              messages.filter(_.isWarning).distinct
            val infos = messages
              .filter(_.kind.severity == 0).distinct
            ValidateResult(
              succeeded = !messages.hasErrors,
              parseErrors = List.empty,
              errors = errs,
              warnings = warns,
              info = infos,
              all = messages
            )
          catch
            case e: Exception =>
              ValidateResult(
                succeeded = false,
                parseErrors = List.empty,
                errors = List.empty,
                warnings = List.empty,
                info = List.empty,
                all = List.empty
              )
          end try
        case Left(parseMessages) =>
          ValidateResult(
            succeeded = false,
            parseErrors = parseMessages,
            errors = List.empty,
            warnings = List.empty,
            info = List.empty,
            all = List.empty
          )
      end match
    }
  end validateIncremental

  override def version: String =
    RiddlBuildInfo.version

  override def formatInfo: String =
    import com.ossuminc.riddl.utils.InfoFormatter
    InfoFormatter.formatInfo
  end formatInfo
end RiddlLib
