/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

/** Classification of the interaction verbs used by UI [[AST.Input]] and [[AST.Output]] elements.
  *
  * The verb vocabularies themselves are fixed parser whitelists (`acquisitionAliases` /
  * `presentationAliases` in `GroupParser`, mirrored in the EBNF grammar). This object gives
  * validation a single, reusable place to reason about the *meaning* of those verbs so that both
  * input validation (A44) and output validation (A46) can share the same categorization.
  *
  * A **selection verb** (`selects` / `chooses` / `picks`) implies the value being acquired or
  * presented is one of a closed set of choices, i.e. its type is expected to be an enumeration or
  * an alternation. A **presentation verb** (`presents` / `shows` / `displays` / `writes` / `emits`)
  * is used by [[AST.Output]] elements to render or emit a value. All other verbs are entry/workflow
  * (acquisition) verbs with no such expectation.
  *
  * Keep [[selectionVerbs]] and [[presentationVerbs]] in sync with the parser whitelists
  * (`acquisitionAliases` / `presentationAliases` in `GroupParser`) and the EBNF grammar.
  */
object UIVerbs {

  /** Verbs whose semantics imply a choice among a closed set of options. */
  val selectionVerbs: Set[String] = Set("selects", "chooses", "picks")

  /** Presentation verbs used by [[AST.Output]] elements (the `presentationAliases` whitelist). */
  val presentationVerbs: Set[String] = Set("presents", "shows", "displays", "writes", "emits")

  /** Coarse category name for an interaction verb: `"selection"` for a selection verb,
    * `"presentation"` for a presentation verb, otherwise `"acquisition"`.
    */
  def verbCategory(verb: String): String =
    if selectionVerbs.contains(verb) then "selection"
    else if presentationVerbs.contains(verb) then "presentation"
    else "acquisition"

  /** True iff `verb` is a selection verb (`selects` / `chooses` / `picks`). */
  def isSelectionVerb(verb: String): Boolean = selectionVerbs.contains(verb)

  /** True iff `verb` is a presentation verb (`presents` / `shows` / `displays` / `writes` /
    * `emits`).
    */
  def isPresentationVerb(verb: String): Boolean = presentationVerbs.contains(verb)

  /** The MODALITY a verb implies, for the verbs that imply one at all.
    *
    * Ruled by Reid, 2026-08-26: a verb that CONTRADICTS its output's kind draws a StyleWarning --
    * never an Error. `haptic Buzz shows …` reads wrongly, but a model that says it is not
    * self-contradictory, and RIDDL does not invalidate a model over how it reads.
    *
    * **The map is deliberately PARTIAL, and that is the whole design.** The verb vocabulary does
    * not partition by modality:
    *
    *   - `presents` and `emits` are broad by meaning -- a system may present or emit through any
    *     channel -- so mapping them would invent a rule the language never stated.
    *   - `diffuses`, `serve`, `offer` and `taste` imply scent and taste, and there is NO scent or
    *     taste output kind. They have no modality to belong to, so they cannot contradict one.
    *
    * A verb absent from this map is SILENT, always. Adding one is a language decision, not a
    * tidy-up: it declares that RIDDL now has an opinion about a word it previously did not.
    */
  val verbModalities: Map[String, Set[String]] = Map(
    // Visual. A thing that is shown, displayed or written is looked at.
    "shows" -> visual,
    "displays" -> visual,
    "writes" -> visual,
    // `plays` covers BOTH a sound and an animation -- both "play" -- so it is not purely auditory.
    "plays" -> Set("sound", "animation"),
    "speaks" -> Set("speech"),
    "announces" -> Set("speech"),
    "vibrates" -> Set("haptic"),
    "pulses" -> Set("haptic"),
    "nudges" -> Set("haptic")
  )

  /** Output kinds a reader looks at. `output` is the generic spelling and is treated as visual,
    * since that is what an unqualified output means in practice.
    */
  private lazy val visual: Set[String] =
    Set("output", "document", "list", "table", "graph", "animation", "picture")

  /** Does `verb` contradict `nounAlias`? None when the verb implies no modality, or when the noun
    * is one the verb admits -- both of which are silence rather than approval.
    */
  def verbContradicts(verb: String, nounAlias: String): Boolean =
    verbModalities.get(verb).exists(admitted => nounAlias.nonEmpty && !admitted.contains(nounAlias))

  /** How a verb's modality reads in a message, for the one place that reports a contradiction. */
  def modalityOf(verb: String): String = verb match
    case "shows" | "displays" | "writes"      => "visual"
    case "plays"                              => "auditory or animated"
    case "speaks" | "announces"               => "spoken"
    case "vibrates" | "pulses" | "nudges"     => "tactile"
    case _                                    => "unclassified"
}
