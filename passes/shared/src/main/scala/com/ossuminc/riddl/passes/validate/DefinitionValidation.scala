/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, At, Contents, *}
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.passes.symbols.SymbolsOutput
import com.ossuminc.riddl.utils.PlatformContext

/** Specification of a recognized option: which definition types
  * it applies to and its expected argument count.
  *
  * @param validParents
  *   The definition types this option is valid on. Empty means
  *   valid on any definition.
  * @param minArgs
  *   Minimum number of arguments expected
  * @param maxArgs
  *   Maximum number of arguments expected
  */
case class OptionSpec(
  validParents: Seq[String],
  minArgs: Int = 0,
  maxArgs: Int = 0
)

/** Registry of deprecated option names and their replacements.
  * Used to generate deprecation warnings while maintaining
  * backward compatibility.
  */
object DeprecatedOptions:
  case class Deprecation(
    replacement: String,
    sinceVersion: String = "1.15.0"
  )

  val registry: Map[String, Deprecation] = Map(
    "package" -> Deprecation("namespace", "1.15.0")
  )
end DeprecatedOptions

/** Registry of recognized RIDDL option names with their
  * specifications. Options not in this registry will produce
  * style warnings (not errors) to keep the system extensible.
  */
object RecognizedOptions:
  val registry: Map[String, OptionSpec] = Map(
    // Existing well-known options
    "aggregate" -> OptionSpec(Seq("Entity"), 0, 0),
    "auto-id" -> OptionSpec(Seq("Entity"), 0, 0),
    "finite-state-machine" -> OptionSpec(Seq("Entity"), 0, 0),
    "persistent" -> OptionSpec(Seq("Connector"), 0, 0),
    "technology" -> OptionSpec(Seq.empty, 1, 1),
    "kind" -> OptionSpec(Seq.empty, 1, 1),
    "color" -> OptionSpec(Seq.empty, 1, 1),
    // Temporal options (C1)
    "timeout" -> OptionSpec(
      Seq("SagaStep", "Handler", "On Message"), 1, 1
    ),
    "retry" -> OptionSpec(
      Seq("SagaStep", "Handler"), 1, 2
    ),
    "delay" -> OptionSpec(
      Seq("SagaStep"), 1, 1
    ),
    // Resilience options (C2)
    "circuit-breaker" -> OptionSpec(
      Seq("Adaptor", "Connector"), 0, 2
    ),
    "idempotent" -> OptionSpec(
      Seq("Handler", "On Message"), 0, 0
    ),
    "bulkhead" -> OptionSpec(
      Seq("Entity", "Context"), 0, 1
    ),
    // Delivery semantics options (C3)
    "at-least-once" -> OptionSpec(Seq("Connector"), 0, 0),
    "at-most-once" -> OptionSpec(Seq("Connector"), 0, 0),
    "exactly-once" -> OptionSpec(Seq("Connector"), 0, 0),
    "ordered" -> OptionSpec(Seq("Connector", "Inlet"), 0, 0),
    "partitioned" -> OptionSpec(Seq("Connector"), 1, 1),
    // Caching and performance options (C4)
    "cacheable" -> OptionSpec(
      Seq("Projector", "Handler"), 0, 1
    ),
    "rate-limit" -> OptionSpec(
      Seq("Handler", "Entity"), 2, 2
    ),
    "batch" -> OptionSpec(
      Seq("Projector", "Repository"), 1, 1
    ),
    // Icon and display options
    "faicon" -> OptionSpec(Seq.empty, 1, 1),
    // Domain/Context structural options
    "external" -> OptionSpec(Seq("Domain", "Context"), 0, 0),
    "microservice" -> OptionSpec(Seq("Context","Entity","Projector","Repository", "Saga"), 0,0),
    "namespace" -> OptionSpec(Seq("Domain", "Context"), 1, 1),
    "package" -> OptionSpec(Seq("Domain", "Context"), 1, 1),
    // Transport/protocol option (used by AsyncAPI generation)
    "protocol" -> OptionSpec(Seq("Context", "Streamlet"), 1, 1),
  )
end RecognizedOptions

/** A Trait that defines typical Validation checkers for validating definitions */
trait DefinitionValidation(using pc: PlatformContext) extends BasicValidation:
  def symbols: SymbolsOutput

  private def checkUniqueContent(definition: Branch[?]): Unit = {
    val allNamedValues = definition.contents.definitions
    val allNames = allNamedValues.map(_.identify)
    if allNames.distinct.size < allNames.size then {
      val duplicates: Map[String, Seq[Definition]] =
        allNamedValues.groupBy(_.identify).filterNot(_._2.size < 2)
      if duplicates.nonEmpty then {
        val details = duplicates
          .map { case (_: String, defs: Seq[Definition]) =>
            defs.map(_.identifyWithLoc).mkString(", and ")
          }
          .mkString("", "\n  ", "\n")
        messages.addError(
          definition.errorLoc,
          s"${definition.identify} has duplicate content names:\n  $details",
          suggestion = s"Rename or remove the duplicate definitions so each name is unique within ${definition.identify}."
        )
      }
    }
  }

  def checkDefinition(
    parents: Parents,
    definition: Definition
  ): Unit = {
    checkIdentifierLength(definition)
    definition match
      case vd: VitalDefinition[?] =>
        checkMetadata(vd)
        vd.authorRefs.foreach { (authorRef: AuthorRef) =>
          pathIdToDefinition(authorRef.pathId, definition.asInstanceOf[Branch[?]] +: parents) match
            case None =>
              messages.addError(
                authorRef.loc,
                s"${authorRef.format} is not defined",
                suggestion = "Define the referenced author (e.g. 'author Name is { name is \"...\" email is \"...\" }'), " +
                  "or correct the author reference to name an existing author."
              )
            case _ =>
          end match
        }
      case _ => ()
    end match

    val path = symbols.pathOf(definition)
    if !definition.id.isEmpty then {
      val matches = symbols.lookup[Definition](path)
      if matches.isEmpty then {
        messages.addSevere(
          definition.id.loc,
          s"'${definition.id.value}' evaded inclusion in symbol table!",
          suggestion = "This is an internal RIDDL error; please report it with the model that triggered it."
        )
      }
    }
  }

  def checkContents(
    container: Branch[?],
    parents: Parents
  ): Unit =
    val parent: Branch[?] = parents.headOption.getOrElse(Root.empty)
    check(
      container.contents.definitions.nonEmpty || container.isInstanceOf[Field],
      s"${container.identify} in ${parent.identify} should have content",
      MissingWarning,
      container.errorLoc,
      suggestion = s"Add at least one definition inside ${container.identify} (or '???' as a placeholder), " +
        "or remove it if it is not needed."
    )
  end checkContents

  def checkContainer(
    parents: Parents,
    container: Branch[?]
  ): Unit = {
    checkDefinition(parents, container)
    checkContents(container, parents)
    checkUniqueContent(container)
  }
  def checkMetadata(definition: Definition): Unit =
    checkMetadata(definition.identify, definition, definition.errorLoc)

  def checkMetadata(identity: String, definition: WithMetaData, loc: At): Unit =
    check(
      definition.metadata.nonEmpty,
      s"Metadata in $identity should not be empty",
      MissingWarning,
      loc,
      suggestion = s"Add metadata to $identity, such as 'briefly \"...\"', 'described as { ... }', or 'by author ...'."
    )
    var hasAuthorRef = false
    var hasDescription = false
    for { meta <- definition.metadata.toSeq } do {
      meta match
        case bd: BriefDescription =>
          check(
            bd.brief.s.length < 80,
            s"In $identity, brief description at ${bd.loc.format} is too long. Max is 80 chars",
            Warning,
            bd.loc,
            suggestion = "Shorten the 'briefly' text to 80 characters or fewer; move any detail into a 'described as { ... }' block."
          )
        case bd: BlockDescription =>
          check(
            bd.lines.nonEmpty && !bd.lines.forall(_.s.isEmpty),
            s"For $identity, description at ${bd.loc.format} is declared but empty",
            MissingWarning,
            bd.loc,
            suggestion = s"Add description text to the 'described as' block for $identity, or remove the empty block."
          )
          check(
            bd.lines.nonEmpty,
            s"For $identity, description is declared but empty",
            MissingWarning,
            bd.loc,
            suggestion = s"Add description text to the 'described as' block for $identity, or remove the empty block."
          )

          hasDescription = true
        case ud: URLDescription =>
          check(
            ud.url.isValid,
            s"For $identity, description at ${ud.loc.format} has an invalid URL: ${ud.url}",
            Error,
            ud.loc,
            suggestion = "Use a valid absolute URL for the description link, e.g. 'https://example.com/docs'."
          )
          hasDescription = true
        case t: Term =>
          check(
            t.definition.map(_.s.length).sum >= 10,
            s"${t.identify}'s definition is too short. It must be at least 10 characters'",
            Warning,
            t.loc,
            suggestion = s"Expand the definition of ${t.identify} to at least 10 characters so the glossary term is meaningful."
          )
        case o: OptionValue =>
          check(
            o.name.length >= 3,
            s"Option ${o.name}'s name is too short. It must be at least 3 characters'",
            StyleWarning,
            o.loc,
            suggestion = "Use an option name of at least 3 characters."
          )
          validateRecognizedOption(o, identity, loc)
        case _: AuthorRef        => hasAuthorRef = true
        case _: StringAttachment => () // No validation needed
        case _: FileAttachment   => () // No validation needed
        case _: ULIDAttachment   => () // No validation needed
        case _: Description      => () // No validation needed
        case _: Comment          => () // No validation needed
    }
    check(
      hasDescription,
      s"$identity should have a description",
      MissingWarning,
      loc,
      suggestion = s"Add documentation to $identity, e.g. 'briefly \"A short summary\"' or 'described as { | ... | }'."
    )
  end checkMetadata

  /** Validate an option against the recognized options registry.
    * Checks argument count and parent definition type compatibility.
    * Unrecognized options produce style warnings to keep the system extensible.
    */
  private def validateRecognizedOption(
    option: OptionValue,
    identity: String,
    loc: At
  ): Unit =
    DeprecatedOptions.registry.get(option.name).foreach { dep =>
      messages.addStyle(
        option.loc,
        s"Option '${option.name}' in $identity is deprecated" +
          s" since ${dep.sinceVersion}." +
          s" Use '${dep.replacement}' instead",
        suggestion = s"Replace option '${option.name}' with '${dep.replacement}'."
      )
    }
    RecognizedOptions.registry.get(option.name) match
      case Some(spec) =>
        val argCount = option.args.size
        if argCount < spec.minArgs || argCount > spec.maxArgs then
          val expected =
            if spec.minArgs == spec.maxArgs then s"${spec.minArgs}"
            else s"${spec.minArgs} to ${spec.maxArgs}"
          check(
            predicate = false,
            s"Option '${option.name}' in $identity expects $expected argument(s) but has $argCount",
            Warning,
            option.loc,
            suggestion = s"Provide $expected argument(s) to option '${option.name}'."
          )
        end if
        if spec.validParents.nonEmpty then
          val parentKind = identity.split(" ").head
          val isValid = spec.validParents.exists { vp =>
            vp == parentKind || identity.startsWith(vp)
          }
          check(
            isValid,
            s"Option '${option.name}' is not typically used on ${identity.split(" ").head} definitions" +
              s" (expected: ${spec.validParents.mkString(", ")})",
            StyleWarning,
            option.loc,
            suggestion = s"Move option '${option.name}' to one of: ${spec.validParents.mkString(", ")}, or remove it here."
          )
        end if
      case None =>
        check(
          predicate = false,
          s"Option '${option.name}' in $identity is not a recognized RIDDL option",
          StyleWarning,
          option.loc,
          suggestion = s"Check the spelling of '${option.name}' against the recognized RIDDL options, or remove it if unintended."
        )
    end match
  end validateRecognizedOption
end DefinitionValidation
