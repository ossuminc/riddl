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
import com.ossuminc.riddl.utils.{FigmaAccess, FigmaClient, FigmaLookup, PlatformContext}

/** A Trait that defines typical Validation checkers for validating definitions */
trait DefinitionValidation(using pc: PlatformContext) extends BasicValidation:
  def symbols: SymbolsOutput

  /** A49: every [[Term]] seen during metadata validation, accumulated here for a cross-scope
    * consistency check reconciled in postProcess (same collect-here / reconcile-in-postProcess
    * pattern as the ValidationPass collectors).
    */
  protected val collectedTerms: scala.collection.mutable.ListBuffer[Term] =
    scala.collection.mutable.ListBuffer.empty

  /** A definition's nested definitions must have UNIQUE NAMES — regardless of kind.
    *
    * Grouping by `identify` (which is `Kind 'name'`) only caught same-kind collisions, so `type
    * Thing` beside `entity Thing` in one context passed silently. RIDDL is a precise language and a
    * path identifier names ONE thing: with two same-named siblings, `Ctx.Thing` is ambiguous and
    * whichever resolution happens to win is arbitrary. Grouping by the NAME closes that.
    *
    * Anonymous definitions are excluded — several constructs legitimately carry an empty id, and
    * they are not addressable by name, so they cannot be ambiguous.
    */
  private def checkUniqueContent(definition: Branch[?]): Unit = {
    val allNamedValues = definition.contents.definitions.filter(_.id.nonEmpty)
    val allNames = allNamedValues.map(_.id.value)
    if allNames.distinct.size < allNames.size then {
      val duplicates: Map[String, Seq[Definition]] =
        allNamedValues.groupBy(_.id.value).filterNot(_._2.size < 2)
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
            s"Rename or remove the duplicate definitions so each name is unique within ${definition.identify}.",
          ruleId = Some(RuleId.DuplicateContentNames)
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
                    "or correct the author reference to name an existing author.",
                ruleId = Some(RuleId.AuthorUndefined)
              )
            case _ =>
          end match
        }
      // Portlets are Leafs, not VitalDefinitions, so they used to fall past this match
      // entirely and their options went unvalidated. Contents only -- an inlet or outlet
      // with no metadata is perfectly normal and must not draw a MissingWarning.
      // Portlets ONLY, deliberately narrow. They are Leafs, not VitalDefinitions, and --
      // unlike Constant, Adaptor, Schema and friends -- nothing else calls checkMetadata for
      // them, so their options went unvalidated entirely: `option zzznotanoption("x")` on an
      // outlet was accepted in silence while the same typo on a vital definition drew a
      // StyleWarning. Widening this arm to all WithMetaData double-validates every definition
      // whose validator already calls checkMetadata, which showed up as doubled FigmaRef
      // message counts. Contents only: an inlet or outlet with no metadata is normal and must
      // not draw a MissingWarning.
      case portlet: Portlet =>
        val _ = checkMetadataContents(definition.identify, portlet, definition.errorLoc)
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
            "This is an internal RIDDL error; please report it with the model that triggered it.",
          ruleId = Some(RuleId.NotInSymbolTable)
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
          suggestion = "Give the included file a '.riddl' extension.",
          ruleId = Some(RuleId.IncludeExtension)
        )
      // A51(b): an include that parsed but contributed no definitions (e.g. only comments or
      // whitespace) adds nothing to the model.
      if incl.contents.nonEmpty && incl.contents.definitions.isEmpty then
        messages.addMissing(
          incl.loc,
          "Include contributes no definitions",
          suggestion =
            "Add RIDDL definitions to the included file, or remove the include if it is not needed.",
          ruleId = Some(RuleId.IncludeContributesNothing)
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
    val hasDescription = checkMetadataContents(identity, definition, loc)
    check(
      hasDescription,
      s"$identity should have a description",
      MissingWarning,
      loc,
      suggestion =
        s"Add documentation to $identity, e.g. 'briefly \"A short summary\"' or 'described as { | ... | }'."
    )
  end checkMetadata

  /** Validate the CONTENTS of a definition's metadata without requiring any to be present.
    *
    * Split out of [[checkMetadata]] for definitions that legitimately carry no metadata but must
    * still have what they DO carry checked. [[AST.Portlet]]s are the case in point: they are
    * `Leaf`s, not `VitalDefinition`s, so `checkDefinition` fell straight past the metadata branch
    * and NOTHING validated their options -- `option zzznotanoption("x")` on an outlet was accepted
    * in silence while the same typo on any vital definition drew a StyleWarning. Calling the full
    * `checkMetadata` on them instead would emit a "should not be empty" MissingWarning for every
    * ordinary inlet and outlet in every model, which is why this exists.
    */
  def checkMetadataContents(identity: String, definition: WithMetaData, loc: At): Boolean =
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
            ud.toURL.isValid,
            s"For $identity, description at ${ud.loc.format} has an invalid URL: ${ud.path}",
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
        case fr: FigmaRef        => validateFigmaRef(fr, identity, definition)
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
          "Keep a single 'briefly \"...\"'; merge or remove the extra brief descriptions.",
        ruleId = Some(RuleId.MultipleBriefs)
      )
    val ulids = definition.metadata.filter[ULIDAttachment]
    if ulids.size > 1 then
      messages.addStyle(
        ulids(1).loc,
        s"$identity has multiple 'ULID' metadata; only the first is used",
        suggestion = "Keep a single ULID attachment; remove the extras.",
        ruleId = Some(RuleId.MultipleUlids)
      )
    // DELIBERATELY NOT HERE: "should have a description". Documentation is expected of
    // definitions a reader navigates to, not of every field and portlet -- moving it into
    // this method made 14 suites demand a description on every type and field. It stays in
    // checkMetadata, which only vital definitions reach.
    hasDescription
  end checkMetadataContents

  /** A42: the definitions a [[FigmaRef]] may decorate. A Figma frame depicts a piece of user
    * interface, so the reference belongs only where the model describes user interface: the two
    * ends of a UI conversation ([[Input]] and [[Output]]), the screen or region that groups them
    * ([[Group]]), and the [[Context]] that owns them, which by the rules of A41 is exactly a
    * context whose intention is `application`.
    */
  private def mayCarryFigmaRef(definition: WithMetaData): Boolean =
    definition match
      case _: Input | _: Output | _: Group => true
      case c: Context                      => c.intention.contains(Intention.Application)
      case _                               => false
    end match
  end mayCarryFigmaRef

  /** A42: reduce a name to its bare word-characters so that a Figma frame called "Login Screen", a
    * group called `LoginScreen` and a group called `login_screen` all correspond. The comparison is
    * deliberately forgiving about case, spacing and separators and strict about everything else:
    * the point is to catch a frame that has been renamed or repurposed, not to police house style.
    */
  private def normalizedName(name: String): String =
    name.filter(_.isLetterOrDigit).toLowerCase

  /** A42: memo of Figma lookups for this pass, so a file referenced by twenty definitions costs one
    * request per distinct node rather than twenty.
    */
  private val figmaLookups: scala.collection.mutable.HashMap[(String, String), FigmaLookup] =
    scala.collection.mutable.HashMap.empty

  /** A42: resolved once per pass. When drift checking is off this is never even consulted, so an
    * offline build does no work and reads no environment.
    */
  private lazy val figmaAccess: FigmaAccess = FigmaClient.access

  /** A42: validate one `figma "<fileKey>" node "<nodeId>"` reference.
    *
    * Two independent concerns:
    *
    *   1. PLACEMENT, always checked and offline: the reference is only meaningful on a UI-bearing
    *      definition, and anywhere else it is an Error. The parser accepts it in any `with` block
    *      (as it does every other metadata), so this is where a misplaced reference is reported —
    *      as a clear message rather than a parse failure.
    *   1. DRIFT, checked only when `checkFigmaDrift` is on: the node must still exist in the design
    *      (Error if the API says it does not, or if the file itself is gone) and the frame's name
    *      must still correspond to the annotated definition's name (Warning if it does not). The
    *      point of the feature is that design/model divergence fails the build now instead of being
    *      discovered months later.
    *
    * The drift half can never break an offline, unconfigured or air-gapped build. It is off by
    * default; with no token there is no client; and any failure to reach or understand the API
    * yields [[FigmaLookup.Unavailable]], which produces nothing at all. Only an API answer — a
    * successful one, or a 404 denying the file — can produce a message.
    */
  private def validateFigmaRef(
    figmaRef: FigmaRef,
    identity: String,
    definition: WithMetaData
  ): Unit =
    if !mayCarryFigmaRef(definition) then
      messages.addError(
        figmaRef.loc,
        s"A 'figma' reference is not allowed on $identity; it may only appear on an input, an " +
          "output, a group, or an application-intended context",
        suggestion = "Move the 'figma' reference onto the input, output, group or " +
          "'application context' whose design frame it identifies.",
        ruleId = Some(RuleId.FigmaRefNotAllowed)
      )
    else if summon[PlatformContext].options.checkFigmaDrift then
      checkFigmaDrift(figmaRef, identity, definition)
    end if
  end validateFigmaRef

  private def checkFigmaDrift(
    figmaRef: FigmaRef,
    identity: String,
    definition: WithMetaData
  ): Unit =
    val fileKey = figmaRef.fileKey.s
    val nodeId = figmaRef.nodeId.s
    figmaAccess match
      case FigmaAccess.NotConfigured(reason) =>
        // The user asked for drift checking and cannot have it. Say so once, informationally, and
        // never as a warning or error: an unconfigured environment is not a defect in the model.
        if !figmaLookups.contains(("", "")) then
          figmaLookups.update(("", ""), FigmaLookup.Unavailable(reason))
          messages.info(
            s"Figma drift checking was requested but is unavailable: $reason",
            figmaRef.loc,
            suggestion = s"Set the ${FigmaClient.TokenEnvVar} environment variable to a Figma " +
              "personal access token, or drop the --check-figma-drift option."
          )
        end if
      case FigmaAccess.Available(client) =>
        val lookup =
          figmaLookups.getOrElseUpdate((fileKey, nodeId), client.lookupNode(fileKey, nodeId))
        lookup match
          case FigmaLookup.Unavailable(_) =>
          // Nothing was learned about the design, so nothing may be said about it. A network
          // failure must never be reported as drift, and must never fail a build.
          case FigmaLookup.Missing =>
            messages.addError(
              figmaRef.loc,
              s"Figma node '$nodeId' referenced by $identity does not exist in Figma file " +
                s"'$fileKey'",
              suggestion = "Update the node id to the frame's current id, or remove the 'figma' " +
                "reference if the frame was deleted.",
              ruleId = Some(RuleId.FigmaNodeMissing)
            )
          case FigmaLookup.FileNotFound(_) =>
            // The message admits its own ambiguity, because Figma answers 404 for a file the token
            // cannot see exactly as it does for one that has been deleted, and sending the reader
            // after the wrong one of those wastes their time. Reported per reference, as `Missing`
            // is; the memo in `figmaLookups` spares the network, not the diagnostics.
            messages.addError(
              figmaRef.loc,
              s"Figma file '$fileKey' referenced by $identity could not be read; it has been " +
                "deleted or moved, or the token in use cannot see it",
              suggestion = s"Check that the file key is current and that the " +
                s"${FigmaClient.TokenEnvVar} token has access to it, or remove the 'figma' " +
                "reference if the design has been retired.",
              ruleId = Some(RuleId.FigmaFileUnreadable)
            )
          case FigmaLookup.Found(frameName) =>
            val expected = definition match
              case d: Definition => d.id.value
              case _             => identity
            if normalizedName(frameName) != normalizedName(expected) then
              messages.addWarning(
                figmaRef.loc,
                s"Figma frame '$frameName' does not correspond to $identity; the design and the " +
                  "model have drifted apart",
                suggestion = s"Rename the Figma frame to '$expected', rename the definition to " +
                  s"'$frameName', or point the reference at the frame that does correspond.",
                ruleId = Some(RuleId.FigmaFrameDrift)
              )
            end if
        end match
    end match
  end checkFigmaDrift

  /** Validate an option against the recognized options registry. Checks argument count and parent
    * definition type compatibility. Unrecognized options produce style warnings to keep the system
    * extensible.
    */
  /** Options whose FIRST argument states a duration. Their values are a contract for code
    * generators, but a value nobody can read is a defect wherever it is noticed, and noticing it in
    * riddlc beats noticing it in a generator.
    */
  // `AST.Set` shadows `scala.Set` in this file, hence the qualification.
  /** Which ARGUMENT of a temporal option states a duration, by option name.
    *
    * Not a flat set, because the duration is not always the first argument: `retry("3", "2s")`
    * takes a COUNT then an optional backoff, so only index 1 is a duration. Mapping name to index
    * keeps the check honest as options are added.
    */
  private val temporalArgIndex: Map[String, Int] =
    Map("timeout" -> 0, "delay" -> 0, "retry" -> 1)

  /** ISO-8601 durations (`PT1M30S`, `P1DT2H`). `java.time.Duration.parse` handles these but is
    * JVM-only, and this validation must behave identically under Scala.js and Native, so the shape
    * is matched directly.
    *
    * NO LOOKAHEAD. The first version used `(?!$)` and `(?=\d)` to reject a bare `P` or `PT`, which
    * the JVM and Scala.js both accept but Scala NATIVE does not: the pattern is a `val` compiled at
    * class initialisation, so it threw before any validation ran and surfaced as a Severe message
    * with EMPTY text -- every predefined-module test failed on the Native row alone with
    * `Message(empty(0->0), "", Severe, ...)`. The degenerate cases are rejected by the digit check
    * in [[isIso8601Duration]] instead.
    */
  private val iso8601Duration =
    """^P(\d+D)?(T(\d+H)?(\d+M)?(\d+(\.\d+)?S)?)?$""".r

  /** True for an ISO-8601 duration carrying at least one component. The digit test is what rejects
    * a bare `P` or `PT`, which the shape above would otherwise admit.
    */
  private def isIso8601Duration(text: String): Boolean =
    iso8601Duration.matches(text) && text.exists(_.isDigit)

  /** True when an ISO-8601 duration names at least one NON-ZERO component.
    *
    * The shape carries no sign, so a negative ISO duration cannot be written and zero is the only
    * non-positive case. Checking the digits directly avoids parsing: `PT0S` and `P0D` have digits
    * but no magnitude, which `isIso8601Duration` alone cannot tell apart from `PT1S`.
    */
  private def isPositiveIso8601(text: String): Boolean =
    text.exists(c => c.isDigit && c != '0')

  /** Reject a temporal option argument that is not a PRECISE, POSITIVE duration.
    *
    * Two distinct defects, two distinct messages, because they need different fixes:
    *
    *   - VAGUE: `timeout("30")` is ambiguous between seconds and milliseconds and every generator
    *     has to guess. `scala.concurrent.duration.Duration` rejects a bare number and accepts
    *     `30s`, `1500 ms`, `5 minutes`; ISO-8601 is accepted separately because riddl-generator
    *     documents that form and rejecting it would break working models.
    *   - NON-POSITIVE: `timeout("0s")` is perfectly readable and still unusable -- a saga bounded
    *     by zero has expired before its first step starts, and `delay("0s")` says nothing that
    *     omitting the option does not say better.
    *
    * ERRORS rather than warnings in both cases: unlike an unrecognized option NAME, which a
    * generator can ignore, neither of these has a sensible fallback. Inventing a positive bound
    * where the author wrote zero hands the model a bound its author never wrote -- the same
    * objection that made the vague case an error.
    */
  /** The duration test itself, independent of where the literal came from.
    *
    * Extracted from [[validateTemporalArgument]] for A70: a correlation's `times out after` states
    * a duration in the GRAMMAR rather than in an option, and it needs exactly these two checks.
    * Leaving metadata must not mean leaving the validation behind, or `times out after "banana"`
    * would compile.
    *
    * @param subject
    *   names the thing carrying the duration, e.g. "Option 'timeout' in Saga 'S'".
    * @param zeroHint
    *   what to do instead when the duration is zero; the two callers differ, because an option can
    *   simply be removed and a mandatory clause cannot.
    */
  protected def checkPreciseDuration(arg: LiteralString, subject: String, zeroHint: String): Unit =
    val text = arg.s.trim
    val parsed =
      scala.util.Try(scala.concurrent.duration.Duration(text)).toOption.filter(_.isFinite)
    val isIso = isIso8601Duration(text)
    if parsed.isEmpty && !isIso then
      messages.addError(
        arg.loc,
        s"$subject has a vague duration '$text'; it must state a unit",
        suggestion = "Use a precise duration such as '30s', '1500ms', '5 minutes' or 'PT1M30S';" +
          " a bare number is ambiguous between seconds and milliseconds.",
        ruleId = Some(RuleId.VagueDuration)
      )
    else
      val positive =
        parsed.map(_ > scala.concurrent.duration.Duration.Zero).getOrElse(isPositiveIso8601(text))
      check(
        positive,
        s"$subject has a non-positive duration '$text'; it must be positive",
        Messages.Error,
        arg.loc,
        suggestion = zeroHint
      )
    end if
  end checkPreciseDuration

  private def validateTemporalArgument(option: OptionValue, identity: String): Unit =
    temporalArgIndex.get(option.name).foreach { index =>
      option.args.lift(index).foreach { arg =>
        checkPreciseDuration(
          arg,
          s"Option '${option.name}' in $identity",
          s"Give '${option.name}' a duration greater than zero, or remove the" +
            " option entirely if no bound is intended."
        )
      }
    }
  end validateTemporalArgument

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
        suggestion = s"Replace option '${option.name}' with ${dep.replacement}.",
        ruleId = Some(RuleId.OptionDeprecated)
      )
    }
    validateTemporalArgument(option, identity)
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
          // Severity comes from the SPEC, not from this call site: for most options a misplaced
          // name is merely ignored (StyleWarning), but a few assert something untrue about the
          // model where they sit and must be Errors. See `OptionSpec.severity`.
          val wording =
            if spec.severity == Error then "is not valid on"
            else "is not typically used on"
          check(
            isValid,
            s"Option '${option.name}' $wording ${identity.split(" ").head} definitions" +
              s" (expected: ${spec.validParents.mkString(", ")})",
            spec.severity,
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
