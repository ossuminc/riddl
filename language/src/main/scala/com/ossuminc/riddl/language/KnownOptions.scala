/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import scala.scalajs.js.annotation.JSExportTopLevel

/** The option names advertised for each kind of definition.
  *
  * Every list here is now DERIVED from [[RecognizedOptions.registry]], which is the single source
  * of truth for RIDDL options. Previously these lists were maintained by hand alongside the
  * registry; the two drifted apart three times, and each drift produced a spurious "not a
  * recognized RIDDL option" style warning on a perfectly valid option.
  *
  * All the lists are therefore `@deprecated`: call [[RecognizedOptions.optionsFor]]`(<definition
  * kind>)` instead, which also gives you the argument counts by way of
  * [[RecognizedOptions.registry]].
  *
  * The argument to `optionsFor` is a `Definition.kind` string. Note that a Streamlet's kind is its
  * SHAPE name (Source/Sink/Flow/Merge/Split/Router/Void) and a portlet's is Inlet/Outlet — see
  * [[RecognizedOptions.streamletKinds]] and [[RecognizedOptions.portletKinds]].
  */
@JSExportTopLevel("KnownOptions")
object KnownOptions {

  @deprecated("Use RecognizedOptions.optionsFor(kind)", "2.0.0")
  final val adaptor: Seq[String] = RecognizedOptions.optionsFor("Adaptor")

  /** Options advertised for an "application". An application is no longer a definition kind of its
    * own: it is a [[AST.Context]] carrying the `application` intention, so its options are a
    * context's options.
    */
  @deprecated("Use RecognizedOptions.optionsFor(kind)", "2.0.0")
  final val application: Seq[String] = RecognizedOptions.optionsFor("Context")

  @deprecated("Use RecognizedOptions.optionsFor(kind)", "2.0.0")
  final val connector: Seq[String] = RecognizedOptions.optionsFor("Connector")

  @deprecated("Use RecognizedOptions.optionsFor(kind)", "2.0.0")
  final val context: Seq[String] = RecognizedOptions.optionsFor("Context")

  @deprecated("Use RecognizedOptions.optionsFor(kind)", "2.0.0")
  final val domain: Seq[String] = RecognizedOptions.optionsFor("Domain")

  @deprecated("Use RecognizedOptions.optionsFor(kind)", "2.0.0")
  final val entity: Seq[String] = RecognizedOptions.optionsFor("Entity")

  @deprecated("Use RecognizedOptions.optionsFor(kind)", "2.0.0")
  final val epic: Seq[String] = RecognizedOptions.optionsFor("Epic")

  @deprecated("Use RecognizedOptions.optionsFor(kind)", "2.0.0")
  final val projector: Seq[String] = RecognizedOptions.optionsFor("Projector")

  @deprecated("Use RecognizedOptions.optionsFor(kind)", "2.0.0")
  final val repository: Seq[String] = RecognizedOptions.optionsFor("Repository")

  /** Options advertised for a Saga. A saga is SEQUENTIAL by definition, so
    * [[KnownOption.sequential]] is deliberately NOT registered — an option to request the default
    * behaviour is redundant (A11). The constant itself is retained as public API.
    */
  @deprecated("Use RecognizedOptions.optionsFor(kind)", "2.0.0")
  final val saga: Seq[String] = RecognizedOptions.optionsFor("Saga")

  /** Options advertised for a streamlet. A Streamlet's `kind` is its SHAPE's simple name, never
    * "Streamlet", so this is the union over all seven shapes.
    */
  @deprecated("Use RecognizedOptions.optionsFor(kind)", "2.0.0")
  final val streamlet: Seq[String] =
    RecognizedOptions.optionsForAny(RecognizedOptions.streamletKinds)

  /** Options recognized on a portlet (Inlet or Outlet). `async` marks a portlet as a codegen async
    * boundary (anti-fusion); see RecognizedOptions.registry.
    */
  @deprecated("Use RecognizedOptions.optionsFor(kind)", "2.0.0")
  final val portlet: Seq[String] = RecognizedOptions.optionsForAny(RecognizedOptions.portletKinds)
}

object KnownOption {
  final val aggregate = "aggregate"
  final val async = "async"
  final val available = "available"
  final val backstage_lifecycle = "backstage_lifecycle"
  final val backstage_owner = "backstage_owner"
  final val backstage_type = "backstage_type"
  final val concept = "concept"
  final val confluence_parent = "confluence_parent"
  final val confluence_space = "confluence_space"
  final val consistent = "consistent"
  final val css = "css"
  final val device = "device"
  final val external = "external"
  final val event_catalog_version = "event_catalog_version"
  final val event_sourced = "event-sourced"
  final val faicon = "faicon"
  final val finite_state_machine = "finite-state-machine"
  final val gateway = "gateway"
  final val kind = "kind"
  final val message_envelope = "message_envelope"
  final val message_queue = "message-queue"
  final val namespace = "namespace"
  final val package_ = "package"
  final val parallel = "parallel"
  final val persistent = "persistent"
  final val protocol = "protocol"
  final val reply = "reply"
  final val sequential = "sequential"
  final val service = "service"
  final val sql_dialect = "sql_dialect"
  final val sql_table = "sql_table"
  final val sync = "sync"
  final val tail_recursive = "tail-recursive"

  /** The `value` entity option (an entity that is a DDD value object).
    *
    * NOTE: from its introduction until the option registry was consolidated this constant held the
    * string `"final value"` — the artifact of a bad edit of `final val value = "value"`. That
    * string contains a space and so could never name a parseable RIDDL option, and the constant had
    * no consumers anywhere, so the typo was never observable. It is corrected here.
    */
  final val value = "value"
  final val wrapper = "wrapper"
  final val technology = "technology"

  /** Selects how a generator lowers an outlet to its target technology. Consumed by
    * riddl-generator; riddl only checks the name and arity, the VALUES are the generator's concern.
    */
  final val lowering = "lowering"

  /** A10 saga failure-control options. Registration only -- the semantics are a contract for code
    * generators, not something riddlc acts on.
    */
  final val undo_retry = "undo-retry"
  final val failure_message = "failure-message"

  /** Marks the inlet that receives hard-error notifications, in place of `Riddl.Operations`. */
  final val error_sink = "error-sink"
  final val transient = "transient"
  final val user = "user"

}
