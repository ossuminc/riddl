/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.resolve.ReferenceMap
import com.ossuminc.riddl.passes.symbols.SymbolsOutput
import com.ossuminc.riddl.utils.PlatformContext

/** Which PROCESSOR a `tell` addresses, for either shape of target.
  *
  * A `tell` names its addressee two ways: a keyword-led [[ProcessorRef]] naming a processor
  * statically, or a [[Value]] typed `Id(entity E)` naming WHICH INSTANCE to tell. Every pass that
  * asks "what does this tell reach" needs the same answer for both, so the answer is computed here
  * once instead of in each of them.
  *
  * **The instance is deliberately NOT resolved, because nothing needs it.** A model cannot know at
  * validation time which `Order` an `Id(entity Order)` holds — that is a runtime value — and no
  * check asks. Every question posed of a tell target (does it handle this message, is it reachable
  * by a connector, which edge does the diagram draw) is answered by the processor KIND, which
  * `Id(entity E)` names structurally. Reid, 2026-08-22: *"You CANNOT know the specific instance at
  * validation time, but fortunately you don't need to."*
  *
  * That is what keeps this cheap. An earlier reading of the problem assumed resolving a value
  * target required `ValidationPass`'s general value-typing machinery and therefore a new
  * resolution-output map for the other passes to read. It does not: `self` is answered by a
  * LEXICAL walk with no lookup at all, and a reference is answered by the one refMap lookup those
  * passes already make for the static case.
  */
object TellTarget {

  /** The processor a target addresses, or `None` when that cannot be determined.
    *
    * `None` is deliberately silent — a target that does not resolve, or whose type is not an
    * `Id(...)`, is reported by the checks that own those diagnostics. Reasoning from absence here
    * would double-report them.
    */
  def processorOf(
    target: ProcessorRef[Processor[?]] | Value,
    parents: Parents,
    refMap: ReferenceMap,
    symbols: SymbolsOutput
  )(using PlatformContext): Option[Processor[?]] = target match {
    case pr: ProcessorRef[?] =>
      parents.headOption.flatMap(h => refMap.definitionOf[Processor[?]](pr.pathId, h))

    // `self` needs NO lookup: the enclosing processor is on the parent stack. Terminates at
    // `Function` and `Saga` for the same reason `enclosingProcessorOf` does — a Saga sits inside a
    // Context routinely, so without that terminator `self` in a saga step silently types as the
    // enclosing Context.
    case sv: SelfValue =>
      parents.collectFirst {
        case p: Processor[?] => Some(p)
        case _: Function     => None
        case _: Saga         => None
      }.flatten

    case vr: ValueRef =>
      parents.headOption
        .flatMap(h => refMap.definitionOf[Field](vr.path, h))
        .flatMap(f => entityOfIdType(f.typeEx, parents, refMap, symbols))

    case _ => None
  }

  /** The processor named by an `Id(entity E)` type expression, following a declared alias once.
    *
    * The alias step matters: `type OrderId is Id(entity Order)` is riddl-models' documented house
    * style, so matching a bare `UniqueId` alone would recognise only the rare inline spelling —
    * the same trap `isAddressFieldFor` fell into.
    */
  private def entityOfIdType(
    typeEx: TypeExpression,
    parents: Parents,
    refMap: ReferenceMap,
    symbols: SymbolsOutput
  )(using PlatformContext): Option[Processor[?]] = typeEx match {
    case uid: UniqueId =>
      parents.headOption
        .flatMap(h => refMap.definitionOf[Processor[?]](uid.entityPath, h))
        // The refMap holds only paths that were WRITTEN, and a synthesized `Id(...)` carries a
        // fully-qualified path with no entry — the same two-lookup requirement `resolveIdTarget`
        // documents, found there by instrumenting rather than by reading.
        .orElse(symbols.lookup[Processor[?]](uid.entityPath.value.reverse).headOption)
    case ate: AliasedTypeExpression =>
      refMap
        .definitionOf[Type](ate.pathId)
        .flatMap(t => entityOfIdType(t.typEx, parents, refMap, symbols))
    case _ => None
  }
}
