/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.ai

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Messages, toSeq}
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.ossuminc.riddl.passes.validate.{ValidationOutput, ValidationPass}
import com.ossuminc.riddl.utils.PlatformContext

/** Categories for AI helper tips */
enum TipCategory:
  /** Missing but commonly needed elements */
  case Completeness
  /** Recognized patterns that could be completed */
  case Pattern
  /** Conventional RIDDL idioms */
  case BestPractice
  /** Connections between definitions */
  case Relationship
  /** Description and metadata suggestions */
  case Documentation
end TipCategory

/** Output from the AIHelperPass containing filtered and
  * augmented messages
  */
case class AIHelperOutput(
  root: PassRoot = Root.empty,
  messages: Messages.Messages = Messages.empty
) extends PassOutput

/** A pass that transforms resolution and validation output
  * into AI-friendly guidance for iterative model building.
  * It filters out noise (StyleWarning, Info) and converts
  * MissingWarning and UsageWarning messages into actionable
  * Tip messages. Resolution errors are also converted to Tips
  * when ValidationPass was not run.
  */
case class AIHelperPass(
  input: PassInput,
  outputs: PassesOutput
)(using io: PlatformContext)
    extends Pass(input, outputs) {

  override def name: String = AIHelperPass.name

  requires(SymbolsPass)
  requires(ResolutionPass)

  private lazy val symbols: SymbolsOutput =
    outputs.outputOf[SymbolsOutput](SymbolsPass.name).get
  private lazy val resolution: ResolutionOutput =
    outputs.outputOf[ResolutionOutput](ResolutionPass.name).get

  /** Whether ValidationPass ran (it only runs when
    * ResolutionPass produces no errors)
    */
  private lazy val hasValidation: Boolean =
    outputs.hasPassOutput(ValidationPass.name)

  private lazy val validationOutput: Option[ValidationOutput] =
    outputs.outputOf[ValidationOutput](ValidationPass.name)

  override protected def process(
    definition: RiddlValue,
    parents: ParentStack
  ): Unit = {
    // Tip generation rules run during traversal
    definition match
      case d: Domain =>
        checkEmptyContainer(d, "domain")
        checkDocumentation(d)
      case c: Context =>
        checkEmptyContainer(c, "context")
        checkContext(c)
        checkDocumentation(c)
      case e: Entity =>
        checkEntity(e)
        checkDocumentation(e)
      case h: Handler =>
        checkHandler(h, parents)
      case _: Adaptor | _: Repository | _: Projector |
           _: Saga | _: Streamlet | _: Epic =>
        definition match
          case d: Definition => checkDocumentation(d)
          case _ => ()
      case _ => ()
    end match
  }

  override def postProcess(root: PassRoot): Unit = {
    if hasValidation then
      processValidationMessages()
    else
      processResolutionErrors()
    end if
  }

  override def result(root: PassRoot): AIHelperOutput = {
    AIHelperOutput(root, messages.toMessages)
  }

  // --- Path B: ValidationPass ran ---

  private def processValidationMessages(): Unit = {
    val valOutput = validationOutput.get
    for msg <- valOutput.messages do
      msg.kind match
        case Error | SevereError =>
          messages.add(msg)
        case Warning =>
          messages.add(msg)
        case CompletenessWarning =>
          messages.add(msg)
        case MissingWarning =>
          convertMissingToTip(msg)
        case UsageWarning =>
          convertUsageToTip(msg)
        case StyleWarning =>
          () // filtered out
        case Info =>
          () // filtered out
        case Tip =>
          messages.add(msg) // pass through any existing tips
      end match
    end for
  }

  private def convertMissingToTip(msg: Message): Unit = {
    val tipMsg = s"Tip: ${msg.message}"
    messages.addTip(msg.loc, tipMsg)
  }

  private def convertUsageToTip(msg: Message): Unit = {
    val tipMsg = s"Tip: ${msg.message} — consider adding " +
      "a handler, reference, or message flow that uses this definition"
    messages.addTip(msg.loc, tipMsg)
  }

  // --- Path A: ResolutionPass had errors ---

  private def processResolutionErrors(): Unit = {
    val resMessages = resolution.messages
    for msg <- resMessages do
      msg.kind match
        case Error | SevereError =>
          convertResolutionErrorToTip(msg)
        case _ =>
          messages.add(msg)
      end match
    end for
  }

  private def convertResolutionErrorToTip(msg: Message): Unit = {
    val text = msg.message
    val tipMsg =
      if text.contains("was not resolved") then
        rewriteNotResolved(text)
      else if text.contains("resolved to") &&
              text.contains("was expected") then
        rewriteWrongType(text)
      else if text.contains("is ambiguous") then
        rewriteAmbiguous(text)
      else if text.contains("is not resolvable") then
        s"Tip: $text — ensure the definition exists " +
          "and is reachable from the current scope."
      else if text.contains("needs to be an aggregate") then
        rewriteAggregateNeeded(text)
      else if text.contains("is not compatible with keyword") then
        rewriteIncompatibleKeyword(text)
      else if text.contains("must be") &&
              text.contains("aggregates") then
        rewriteAlternateAggregates(text)
      else
        s"Tip: $text"
    messages.addTip(msg.loc, tipMsg)
  }

  /** Rewrite "Path 'X' was not resolved" errors */
  private def rewriteNotResolved(text: String): String = {
    val pathPattern = """Path '([^']+)' was not resolved""".r
    pathPattern.findFirstMatchIn(text) match
      case Some(m) =>
        val path = m.group(1)
        s"Tip: Define the missing definition '$path' " +
          "referenced in this model. Create the " +
          "definition so this reference can be resolved."
      case None =>
        s"Tip: $text"
  }

  /** Rewrite "resolved to X but Y was expected" errors */
  private def rewriteWrongType(text: String): String = {
    val pattern =
      """Path '([^']+)' resolved to ([^,]+), .* but (an? \w+) was expected""".r
    pattern.findFirstMatchIn(text) match
      case Some(m) =>
        val path = m.group(1)
        val expected = m.group(3)
        s"Tip: The reference '$path' points to the " +
          "wrong kind of definition. It should refer " +
          s"to $expected. Rename the reference or " +
          "create the correct definition."
      case None =>
        s"Tip: $text"
  }

  /** Rewrite "is ambiguous" errors */
  private def rewriteAmbiguous(text: String): String = {
    val pattern =
      """Path reference '([^']+)' is ambiguous""".r
    pattern.findFirstMatchIn(text) match
      case Some(m) =>
        val path = m.group(1)
        s"Tip: The reference '$path' matches multiple " +
          "definitions. Use a more specific path to " +
          "disambiguate (e.g., Context.Entity.TypeName " +
          "instead of just TypeName)."
      case None =>
        s"Tip: $text"
  }

  /** Rewrite "needs to be an aggregate" errors */
  private def rewriteAggregateNeeded(text: String): String = {
    val pattern =
      """Type expression `([^`]+)` needs to be an aggregate for `(\w+)`""".r
    pattern.findFirstMatchIn(text) match
      case Some(m) =>
        val typeExpr = m.group(1)
        val kind = m.group(2)
        s"Tip: The type '$typeExpr' needs to be " +
          s"defined as a $kind aggregate. Change it to: " +
          s"type X = $kind { ??? }"
      case None =>
        s"Tip: $text"
  }

  /** Rewrite "is not compatible with keyword" errors */
  private def rewriteIncompatibleKeyword(
    text: String
  ): String = {
    val pattern =
      """Type expression `([^`]+)` is not compatible with keyword `(\w+)`""".r
    pattern.findFirstMatchIn(text) match
      case Some(m) =>
        val typeExpr = m.group(1)
        val keyword = m.group(2)
        s"Tip: The type '$typeExpr' is not compatible " +
          s"with the '$keyword' keyword. Ensure the " +
          "type definition matches the expected kind."
      case None =>
        s"Tip: $text"
  }

  /** Rewrite "must be X aggregates" errors */
  private def rewriteAlternateAggregates(
    text: String
  ): String = {
    val pattern =
      """All alternates of `([^`]+)` must be (\w+) aggregates""".r
    pattern.findFirstMatchIn(text) match
      case Some(m) =>
        val typeExpr = m.group(1)
        val kind = m.group(2)
        s"Tip: All alternatives in '$typeExpr' must " +
          s"be $kind types. Ensure each alternative " +
          s"is defined as: type X = $kind {{ ??? }}"
      case None =>
        s"Tip: $text"
  }

  // --- Tip Generation Rules: Context ---

  private def checkContext(context: Context): Unit = {
    val contents = context.contents.toSeq
    val hasAdaptor = contents.exists(_.isInstanceOf[Adaptor])
    val hasRepository = contents.exists(
      _.isInstanceOf[Repository]
    )
    val hasEntity = contents.exists(_.isInstanceOf[Entity])
    val name = context.id.value

    if hasEntity && !hasRepository then
      val snippet =
        s"""repository ${name}Repository is {
           |  ???
           |}""".stripMargin
      messages.addTip(
        context.loc,
        s"Tip: Context '$name' has entities but no " +
          "repository. Consider adding a repository to " +
          "persist entity state.\n" +
          s"Suggested RIDDL:\n$snippet"
      )
    end if

    if !hasAdaptor then
      val snippet =
        s"""adaptor ${name}Adaptor from context OtherContext is {
           |  ???
           |}""".stripMargin
      messages.addTip(
        context.loc,
        s"Tip: Context '$name' has no adaptors. " +
          "Adaptors connect contexts by translating " +
          "messages between them.\n" +
          s"Suggested RIDDL:\n$snippet"
      )
    end if
  }

  // --- Tip Generation Rules: Documentation ---

  private def checkDocumentation(defn: Definition): Unit = {
    if defn.brief.isEmpty then
      val kind = defn.kind
      val name = defn.id.value
      val snippet = s"""briefly \"A brief description of $name\""""
      messages.addTip(
        defn.loc,
        s"Tip: $kind '$name' has no brief description. " +
          "Add documentation to make the model " +
          "self-describing.\n" +
          s"Suggested RIDDL:\n$snippet"
      )
    end if
  }

  // --- Tip Generation Rules: Containers ---

  private def checkEmptyContainer(
    branch: Branch[?],
    kind: String
  ): Unit = {
    val meaningful = branch.contents.toSeq.filterNot {
      case _: Comment => true
      case _          => false
    }
    if meaningful.isEmpty then
      val name = branch match
        case d: Definition => d.id.value
      val snippet = kind match
        case "domain" =>
          s"""domain $name is {
             |  context MyContext is {
             |    ???
             |  }
             |}""".stripMargin
        case "context" =>
          s"""context $name is {
             |  entity MyEntity is {
             |    ???
             |  }
             |}""".stripMargin
        case _ => ""
      val tipMsg = s"Tip: $kind '$name' is empty. " +
        s"Consider adding contents."
      val fullMsg = if snippet.nonEmpty then
        s"$tipMsg\nSuggested RIDDL:\n$snippet"
      else tipMsg
      messages.addTip(branch.loc, fullMsg)
    end if
  }

  private def checkEntity(entity: Entity): Unit = {
    val contents = entity.contents.toSeq
    val name = entity.id.value
    val types = contents.collect { case t: Type => t }
    val states = contents.collect { case s: State => s }
    val handlers = contents.collect { case h: Handler => h }

    val commandTypes = types.filter(t =>
      t.typEx.isAggregateOf(AggregateUseCase.CommandCase)
    )
    val eventTypes = types.filter(t =>
      t.typEx.isAggregateOf(AggregateUseCase.EventCase)
    )

    // Completely empty entity
    if types.isEmpty && states.isEmpty && handlers.isEmpty then
      val snippet =
        s"""entity $name is {
           |  type ${name}Command = command { ??? }
           |  type ${name}Event = event { ??? }
           |  state ${name}State of ${name}StateData is {
           |    handler ${name}Handler is {
           |      ???
           |    }
           |  }
           |}""".stripMargin
      messages.addTip(
        entity.loc,
        s"Tip: Entity '$name' has no types, state, or " +
          s"handlers. Consider adding them.\n" +
          s"Suggested RIDDL:\n$snippet"
      )
    else
      // Missing command types
      if commandTypes.isEmpty then
        val snippet =
          s"type ${name}Command = command { ??? }"
        messages.addTip(
          entity.loc,
          s"Tip: Entity '$name' has no command types. " +
            "Commands define the input messages an entity " +
            "can receive.\n" +
            s"Suggested RIDDL:\n$snippet"
        )
      end if

      // Missing event types
      if eventTypes.isEmpty then
        val snippet =
          s"type ${name}Event = event { ??? }"
        messages.addTip(
          entity.loc,
          s"Tip: Entity '$name' has no event types. " +
            "Events record what happened when a command " +
            "is processed.\n" +
            s"Suggested RIDDL:\n$snippet"
        )
      end if

      // Missing state
      if states.isEmpty then
        val stateTypeName = s"${name}StateData"
        val snippet =
          s"""state ${name}State of $stateTypeName is {
             |  handler ${name}Handler is {
             |    ???
             |  }
             |}""".stripMargin
        messages.addTip(
          entity.loc,
          s"Tip: Entity '$name' has no state definition. " +
            "Entities typically need state to track their " +
            "data.\n" +
            s"Suggested RIDDL:\n$snippet"
        )
      end if

      // Missing handlers (only check if no state, since
      // handlers inside state are the normal pattern)
      if handlers.isEmpty && states.isEmpty then
        messages.addTip(
          entity.loc,
          s"Tip: Entity '$name' has no handlers. " +
            "Entities need handlers to process commands " +
            "and events."
        )
      end if
    end if
  }

  private def checkHandler(
    handler: Handler,
    parents: ParentStack
  ): Unit = {
    val clauses = handler.contents.toSeq.collect {
      case oc: OnClause => oc
    }
    if clauses.isEmpty then
      // Find the enclosing entity to suggest specific
      // on-clauses for its command types
      val entityOpt = parents.toParents.collectFirst {
        case e: Entity => e
      }
      val snippet = entityOpt match
        case Some(entity) =>
          val cmdTypes = entity.contents.toSeq.collect {
            case t: Type
              if t.typEx.isAggregateOf(
                AggregateUseCase.CommandCase
              ) => t
          }
          if cmdTypes.nonEmpty then
            cmdTypes.map { cmd =>
              s"""on command ${cmd.id.value} {
                 |  ???
                 |}""".stripMargin
            }.mkString("\n")
          else
            s"""on command MyCommand {
               |  ???
               |}""".stripMargin
        case None =>
          s"""on command MyCommand {
             |  ???
             |}""".stripMargin

      messages.addTip(
        handler.loc,
        s"Tip: Handler '${handler.id.value}' has no " +
          "on-clauses. Add on-clauses to handle commands " +
          "and events.\n" +
          s"Suggested RIDDL:\n$snippet"
      )
    else
      // Check for on-clauses with empty bodies
      for clause <- clauses do
        val stmts = clause.contents.toSeq
        if stmts.isEmpty then
          messages.addTip(
            clause.loc,
            s"Tip: On-clause '${clause.id.value}' in " +
              s"handler '${handler.id.value}' has an " +
              "empty body. Add statements to define its " +
              "behavior."
          )
        end if
      end for

      // Check if entity has command types without
      // corresponding on-clauses
      val entityOpt = parents.toParents.collectFirst {
        case e: Entity => e
      }
      entityOpt.foreach { entity =>
        val cmdTypes = entity.contents.toSeq.collect {
          case t: Type
            if t.typEx.isAggregateOf(
              AggregateUseCase.CommandCase
            ) => t
        }
        val handledMsgNames = clauses.collect {
          case omc: OnMessageClause =>
            omc.msg.pathId.value.lastOption.getOrElse("")
        }.toSet
        for cmd <- cmdTypes do
          if !handledMsgNames.contains(cmd.id.value) then
            val snippet =
              s"""on command ${cmd.id.value} {
                 |  ???
                 |}""".stripMargin
            messages.addTip(
              handler.loc,
              s"Tip: Handler '${handler.id.value}' does " +
                s"not handle command '${cmd.id.value}'. " +
                "Consider adding an on-clause for it.\n" +
                s"Suggested RIDDL:\n$snippet"
            )
          end if
        end for
      }
    end if
  }
}

object AIHelperPass extends PassInfo[PassOptions] {
  val name: String = "AIHelper"

  def creator(
    options: PassOptions = PassOptions.empty
  )(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => AIHelperPass(in, out)
  }

  /** Entry Point 1: Analyze an existing parsed AST.
    * Runs SymbolsPass → ResolutionPass → (ValidationPass)
    * → AIHelperPass. The returned messages contain the
    * filtered and augmented output including Tips.
    *
    * @param root
    *   A previously parsed Root AST
    * @return
    *   A PassesResult containing all pass outputs and
    *   the AIHelperPass messages (Tips, errors, warnings)
    */
  def analyze(
    root: Root
  )(using PlatformContext): PassesResult = {
    val input = PassInput(root)
    val outputs = PassesOutput()

    // Always run Symbols and Resolution
    Pass.runPass[SymbolsOutput](
      input, outputs, SymbolsPass(input, outputs)
    )
    val resOutput = Pass.runPass[ResolutionOutput](
      input, outputs, ResolutionPass(input, outputs)
    )

    // Only run Validation if Resolution had no errors
    if !resOutput.messages.hasErrors then
      Pass.runPass[ValidationOutput](
        input, outputs, ValidationPass(input, outputs)
      )
    end if

    // Run AIHelperPass
    Pass.runPass[AIHelperOutput](
      input, outputs, AIHelperPass(input, outputs)
    )

    PassesResult(input, outputs)
  }

  /** Entry Point 2: Parse source text and then analyze.
    * If parsing fails, returns parse errors immediately.
    *
    * @param input
    *   The RIDDL source to parse and analyze
    * @return
    *   Right(PassesResult) with Tip messages, or
    *   Left(messages) if parsing fails
    */
  def analyzeSource(
    input: RiddlParserInput
  )(using PlatformContext): Either[Messages.Messages, PassesResult] = {
    TopLevelParser.parseInput(input) match
      case Left(errors) => Left(errors)
      case Right(root)  => Right(analyze(root))
  }
}
