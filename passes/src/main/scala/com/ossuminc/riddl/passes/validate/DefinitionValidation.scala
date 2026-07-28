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

/** A Trait that defines typical Validation checkers for validating definitions */
trait DefinitionValidation(using pc: PlatformContext) extends BasicValidation:
  def symbols: SymbolsOutput

  /** A49: every [[Term]] seen during metadata validation, accumulated here for a cross-scope
    * consistency check reconciled in postProcess (same collect-here / reconcile-in-postProcess
    * pattern as the ValidationPass collectors).
    */
  protected val collectedTerms: scala.collection.mutable.ListBuffer[Term] =
    scala.collection.mutable.ListBuffer.empty

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
          suggestion =
            s"Rename or remove the duplicate definitions so each name is unique within ${definition.identify}."
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
                suggestion =
                  "Define the referenced author (e.g. 'author Name is { name is \"...\" email is \"...\" }'), " +
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
          suggestion =
            "This is an internal RIDDL error; please report it with the model that triggered it."
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
      suggestion =
        s"Add at least one definition inside ${container.identify} (or '???' as a placeholder), " +
          "or remove it if it is not needed."
    )
    checkIncludeHygiene(container)
  end checkContents

  /** A51: include hygiene (validation-only subset). ValidationPass does not process `Include` nodes
    * directly (`withIncludes` is false, so `validateInclude` never runs), so the hygiene checks run
    * here from each container's `checkContents` over its direct `includes`. Circular-include
    * detection is intentionally NOT done — it needs the include loader/parser before flattening and
    * is out of validation scope.
    */
  private def checkIncludeHygiene(container: Branch[?]): Unit =
    container.contents.includes.foreach { incl =>
      // A51(a): included files should carry the .riddl extension (mirrors the .bast suffix check in
      // validateBASTImport). Only checked when an origin was provided.
      if incl.origin.nonEmpty && !incl.origin.path.endsWith(".riddl") then
        messages.addStyle(
          incl.loc,
          s"Included file '${incl.origin.path}' should end with .riddl",
          suggestion = "Give the included file a '.riddl' extension."
        )
      // A51(b): an include that parsed but contributed no definitions (e.g. only comments or
      // whitespace) adds nothing to the model.
      if incl.contents.nonEmpty && incl.contents.definitions.isEmpty then
        messages.addMissing(
          incl.loc,
          "Include contributes no definitions",
          suggestion =
            "Add RIDDL definitions to the included file, or remove the include if it is not needed."
        )
    }

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
      suggestion =
        s"Add metadata to $identity, such as 'briefly \"...\"', 'described as { ... }', or 'by author ...'."
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
            suggestion =
              "Shorten the 'briefly' text to 80 characters or fewer; move any detail into a 'described as { ... }' block."
          )
        case bd: BlockDescription =>
          check(
            bd.lines.nonEmpty && !bd.lines.forall(_.s.isEmpty),
            s"For $identity, description at ${bd.loc.format} is declared but empty",
            MissingWarning,
            bd.loc,
            suggestion =
              s"Add description text to the 'described as' block for $identity, or remove the empty block."
          )
          check(
            bd.lines.nonEmpty,
            s"For $identity, description is declared but empty",
            MissingWarning,
            bd.loc,
            suggestion =
              s"Add description text to the 'described as' block for $identity, or remove the empty block."
          )

          hasDescription = true
        case ud: URLDescription =>
          check(
            ud.url.isValid,
            s"For $identity, description at ${ud.loc.format} has an invalid URL: ${ud.url}",
            Error,
            ud.loc,
            suggestion =
              "Use a valid absolute URL for the description link, e.g. 'https://example.com/docs'."
          )
          hasDescription = true
        case t: Term =>
          check(
            t.definition.map(_.s.length).sum >= 10,
            s"${t.identify}'s definition is too short. It must be at least 10 characters'",
            Warning,
            t.loc,
            suggestion =
              s"Expand the definition of ${t.identify} to at least 10 characters so the glossary term is meaningful."
          )
          // A49: accumulate for the cross-scope consistency check in postProcess.
          collectedTerms.addOne(t)
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
    // A52: a single-valued metadata kind appearing more than once is redundant — only the
    // first is used. The single-valued kinds are those whose accessor is Option/at-most-one
    // (not a Seq): BriefDescription (`brief`) and ULIDAttachment (`ulid`). Multi-valued kinds
    // (author/see/term/option/comment/description) are legitimately repeatable.
    val briefs = definition.metadata.filter[BriefDescription]
    if briefs.size > 1 then
      messages.addStyle(
        briefs(1).loc,
        s"$identity has multiple 'brief description' metadata; only the first is used",
        suggestion =
          "Keep a single 'briefly \"...\"'; merge or remove the extra brief descriptions."
      )
    val ulids = definition.metadata.filter[ULIDAttachment]
    if ulids.size > 1 then
      messages.addStyle(
        ulids(1).loc,
        s"$identity has multiple 'ULID' metadata; only the first is used",
        suggestion = "Keep a single ULID attachment; remove the extras."
      )
    check(
      hasDescription,
      s"$identity should have a description",
      MissingWarning,
      loc,
      suggestion =
        s"Add documentation to $identity, e.g. 'briefly \"A short summary\"' or 'described as { | ... | }'."
    )
  end checkMetadata

  /** Validate an option against the recognized options registry. Checks argument count and parent
    * definition type compatibility. Unrecognized options produce style warnings to keep the system
    * extensible.
    */
  private def validateRecognizedOption(
    option: OptionValue,
    identity: String,
    loc: At
  ): Unit =
    DeprecatedOptions.registry.get(option.name).foreach { dep =>
      messages.addDeprecation(
        option.loc,
        s"Option '${option.name}' in $identity is deprecated" +
          s" since ${dep.sinceVersion}." +
          s" Use ${dep.replacement} instead",
        suggestion = s"Replace option '${option.name}' with ${dep.replacement}."
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
            suggestion =
              s"Move option '${option.name}' to one of: ${spec.validParents.mkString(", ")}, or remove it here."
          )
        end if
      case None =>
        check(
          predicate = false,
          s"Option '${option.name}' in $identity is not a recognized RIDDL option",
          StyleWarning,
          option.loc,
          suggestion =
            s"Check the spelling of '${option.name}' against the recognized RIDDL options, or remove it if unintended."
        )
    end match
  end validateRecognizedOption
end DefinitionValidation
