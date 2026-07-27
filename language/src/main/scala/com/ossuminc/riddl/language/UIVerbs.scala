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
  * Keep [[selectionVerbs]], [[presentationVerbs]] and [[selectionNouns]] in sync with the parser
  * whitelists (`acquisitionAliases` / `presentationAliases` / `inputAliases` in `GroupParser` and
  * `CommonParser`) and the EBNF grammar.
  */
object UIVerbs {

  /** Verbs whose semantics imply a choice among a closed set of options. */
  val selectionVerbs: Set[String] = Set("selects", "chooses", "picks")

  /** Presentation verbs used by [[AST.Output]] elements (the `presentationAliases` whitelist). */
  val presentationVerbs: Set[String] = Set("presents", "shows", "displays", "writes", "emits")

  /** Input nouns that unambiguously denote a selection widget — one that lets the user pick from a
    * closed set of choices. Kept narrow on purpose: other input nouns (`form` / `text` / `button` /
    * `item` / `input`) pair legitimately with a range of verbs, so only these are policed.
    */
  val selectionNouns: Set[String] = Set("picklist", "selector")

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

  /** True iff `noun` is an unambiguous selection widget (`picklist` / `selector`). */
  def isSelectionNoun(noun: String): Boolean = selectionNouns.contains(noun)
}
