/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

/** Specification of a recognized option: which definition types it applies to and its expected
  * argument count.
  *
  * @param validParents
  *   The definition types this option is valid on. Empty means valid on any definition.
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

/** Registry of deprecated option names and their replacements. Used to generate deprecation
  * warnings while maintaining backward compatibility.
  */
object DeprecatedOptions:
  case class Deprecation(
    replacement: String,
    sinceVersion: String = "1.15.0"
  )

  val registry: Map[String, Deprecation] = Map(
    "package" -> Deprecation("namespace", "1.15.0"),
    "gateway" -> Deprecation("the `gateway` context intention prefix", "2.0.0"),
    "service" -> Deprecation("the `service` context intention prefix", "2.0.0"),
    "external" -> Deprecation("the `external` context intention prefix", "2.0.0"),
    "wrapper" -> Deprecation("an adaptor", "2.0.0")
  )
end DeprecatedOptions

/** Registry of recognized RIDDL option names with their specifications. Options not in this
  * registry will produce style warnings (not errors) to keep the system extensible.
  *
  * This registry is the SINGLE SOURCE OF TRUTH for RIDDL options. It lives in the `language` module
  * (rather than in `passes`, where the validation that consumes it lives) precisely so that the
  * per-definition-kind lists published by [[KnownOptions]] can be DERIVED from it instead of
  * hand-maintained alongside it. Two hand-maintained tables drifted apart three times, each drift
  * producing a spurious "not a recognized RIDDL option" style warning on a perfectly valid option.
  *
  * A `validParents` of `Seq.empty` means "valid on any definition"; such options therefore appear
  * in EVERY derived per-kind list.
  *
  * NOTE on parent-kind strings: the string compared against `validParents` is `Definition.kind`,
  * which for most definitions is the class's simple name ("Entity", "Context", "Domain", …). Two
  * families are exceptions and are a recurring source of bugs:
  *   - a [[AST.Streamlet]]'s kind is its SHAPE's simple name — "Source", "Sink", "Flow", "Merge",
  *     "Split", "Router" or "Void" — never "Streamlet".
  *   - a portlet's kind is "Inlet" or "Outlet", never "Portlet".
  */
object RecognizedOptions:
  val registry: Map[String, OptionSpec] = Map(
    // Existing well-known options
    "aggregate" -> OptionSpec(Seq("Entity"), 0, 0),
    "auto-id" -> OptionSpec(Seq("Entity"), 0, 0),
    "finite-state-machine" -> OptionSpec(Seq("Entity"), 0, 0),
    "persistent" -> OptionSpec(Seq("Connector"), 0, 0),
    "technology" -> OptionSpec(Seq.empty, 1, 1),
    // riddl-generator's Quarkus generator lowers an outlet to either an `@Outgoing`
    // method (back-pressured, but a method has ONE return so it fits only a single
    // unconditional `send`) or an injected `Emitter` (any code may call it, including
    // a JPA @Entity that cannot host @Outgoing at all -- but no back-pressure). The
    // generator infers the form; this option lets a modeler override the inference.
    //
    // The parent list is spelled out because a Streamlet's `kind` is its SHAPE's simple
    // name and never "Streamlet", and a portlet's is "Inlet"/"Outlet" and never
    // "Portlet" -- see the parent-kind note at the top of this file.
    "lowering" -> OptionSpec(
      Seq("Outlet", "Source", "Sink", "Flow", "Merge", "Split", "Router", "Void"),
      1,
      1
    ),
    "kind" -> OptionSpec(Seq.empty, 1, 1),
    "color" -> OptionSpec(Seq.empty, 1, 1),
    // Diagram/document styling hint carrying a CSS fragment; consumed by the
    // generators (riddl-gen) when rendering a definition. Applicable to any
    // definition that can be rendered, hence no validParents.
    "css" -> OptionSpec(Seq.empty, 1, 1),
    // Entity persistence / lifecycle markers. These are long-standing RIDDL entity
    // options that were published by KnownOptions.entity but never registered here,
    // so every use of one drew a spurious "not a recognized RIDDL option" warning.
    // All are simple markers with no arguments.
    "event-sourced" -> OptionSpec(Seq("Entity"), 0, 0),
    "value" -> OptionSpec(Seq("Entity"), 0, 0),
    // CAP markers. Meaningful on a Repository as well as an Entity: the computational model
    // (§5.6) rules that a Repository is a Processor, so its WRITE side is single-writer by
    // default and `available` hands write arbitration to the storage engine, permitting
    // concurrent writes. Queries are side-effect-free and always concurrent either way. Same
    // "meaningful on both" reasoning as `transient` below.
    "consistent" -> OptionSpec(Seq("Entity", "Repository"), 0, 0),
    "available" -> OptionSpec(Seq("Entity", "Repository"), 0, 0),
    "message-queue" -> OptionSpec(Seq("Entity"), 0, 0),
    // `transient` marks state that is NOT durably persisted. That is meaningful both
    // for an Entity and for a Repository (a cache-like, non-durable store).
    "transient" -> OptionSpec(Seq("Entity", "Repository"), 0, 0),
    // Epic-level marker: the epic's interactions are synchronous.
    "sync" -> OptionSpec(Seq("Epic"), 0, 0),
    // Temporal options (C1)
    // "Saga" added for A10: a saga-level timeout is the THIRD terminal condition of a
    // `parallel` saga (computational model §9.8) -- (a) all steps succeed, (b) a failure is
    // observed and successful steps are compensated, (c) the timeout expires. Condition (c)
    // had no expression in the language, so a generator had to invent a default and two sagas
    // in one model could not be given different bounds. The step-level timeout bounds a step,
    // not the run.
    "timeout" -> OptionSpec(
      Seq("Saga", "SagaStep", "Handler", "On Message"),
      1,
      1
    ),
    "retry" -> OptionSpec(
      Seq("SagaStep", "Handler"),
      1,
      2
    ),
    "delay" -> OptionSpec(
      Seq("SagaStep"),
      1,
      1
    ),
    // Saga-level failure-control marker (A10). `compensate` is a SAGA option: it declares that,
    // on failure, the saga automatically runs the accumulated steps' compensation (undo) blocks in
    // reverse. A Saga's parent-kind is its class simple name ("Saga"), the string the parent-kind
    // check compares against (cf. "microservice" above). Registration only — no behavioral
    // validation, mirroring how A7 added `async`.
    "compensate" -> OptionSpec(
      Seq("Saga"),
      0,
      0
    ),
    // Saga-level parallelism marker (A11). A saga is SEQUENTIAL by definition; `parallel` declares
    // the exception. The semantics are a contract for the code generator (riddl-gen), NOT something
    // riddlc acts on: all steps start in parallel; the coordinator gathers results asynchronously;
    // when they all succeed the saga succeeds; any one failure triggers compensating actions in
    // REVERSE order of the original sends. Registration only — no behavioral validation, mirroring
    // how A7 added `async` and A10 added `compensate`.
    "parallel" -> OptionSpec(
      Seq("Saga"),
      0,
      0
    ),
    // Resilience options (C2)
    "circuit-breaker" -> OptionSpec(
      Seq("Adaptor", "Connector"),
      0,
      2
    ),
    "idempotent" -> OptionSpec(
      Seq("Handler", "On Message"),
      0,
      0
    ),
    "bulkhead" -> OptionSpec(
      Seq("Entity", "Context"),
      0,
      1
    ),
    // Delivery semantics options (C3)
    "at-least-once" -> OptionSpec(Seq("Connector"), 0, 0),
    "at-most-once" -> OptionSpec(Seq("Connector"), 0, 0),
    "exactly-once" -> OptionSpec(Seq("Connector"), 0, 0),
    "ordered" -> OptionSpec(Seq("Connector", "Inlet"), 0, 0),
    // Complement of `ordered` (A33): messages MAY be delivered out of order,
    // enabling partitioning/parallelism. Same stream-property parents as
    // `ordered`. Registration only — no behavioral / cross-option check.
    "unordered" -> OptionSpec(Seq("Connector", "Inlet"), 0, 0),
    "partitioned" -> OptionSpec(Seq("Connector"), 1, 1),
    // Asynchronous messaging-boundary marker (A7, corrected). `async` is a
    // PORTLET option: it marks an individual Inlet or Outlet as an async
    // boundary so the code generator inserts a real async boundary there
    // instead of fusing the stream (cf. Akka Streams operator fusion). A
    // Portlet's parent-kind is its class name — Inlet.kind / Outlet.kind =
    // getSimpleName = "Inlet" / "Outlet" — so those are the strings the
    // parent-kind check (`identity.split(" ").head`) compares against. NOTE:
    // this is registration only — no behavioral pairing with over-
    // parallelization / context-boundary logic (tracked as A7-ext / #59).
    "async" -> OptionSpec(
      Seq("Inlet", "Outlet"),
      0,
      0
    ),
    // Caching and performance options (C4)
    "cacheable" -> OptionSpec(
      Seq("Projector", "Handler"),
      0,
      1
    ),
    "rate-limit" -> OptionSpec(
      Seq("Handler", "Entity"),
      2,
      2
    ),
    "batch" -> OptionSpec(
      Seq("Projector", "Repository"),
      1,
      1
    ),
    // Icon and display options
    "faicon" -> OptionSpec(Seq.empty, 1, 1),
    // Domain/Context structural options
    "external" -> OptionSpec(Seq("Domain", "Context"), 0, 0),
    // Deprecated context-intention options (Task 12): recognized so they draw only the
    // Deprecation message, not an additional "unrecognized option" StyleWarning.
    "gateway" -> OptionSpec(Seq("Context"), 0, 0),
    "service" -> OptionSpec(Seq("Context"), 0, 0),
    "wrapper" -> OptionSpec(Seq("Context"), 0, 0),
    "microservice" -> OptionSpec(Seq("Context", "Entity", "Projector", "Repository", "Saga"), 0, 0),
    "namespace" -> OptionSpec(Seq("Domain", "Context"), 1, 1),
    "package" -> OptionSpec(Seq("Domain", "Context"), 1, 1),
    // Transport/protocol option (used by AsyncAPI generation). Valid on ANY processor.
    // A processor's parent-kind is its class simple name; a Streamlet's is its SHAPE
    // simple name (Source/Sink/Flow/Merge/Split/Router/Void) — so "Streamlet" never
    // matched a real streamlet. List every processor kind, including the shape names.
    "protocol" -> OptionSpec(
      Seq(
        "Context",
        "Source",
        "Sink",
        "Flow",
        "Merge",
        "Split",
        "Router",
        "Void",
        "Adaptor",
        "Projector",
        "Repository",
        "Entity",
        "Saga"
      ),
      1,
      1
    ),
    // Version pinning for EventCatalog generation; valid on domains,
    // contexts and message types alike, hence no validParents
    "event_catalog_version" -> OptionSpec(Seq.empty, 1, 1),
    // SQL DDL generation options; sql_dialect is resolved by walking up
    // the parent chain, so it is valid on entities, repositories,
    // contexts and domains alike, hence no validParents
    "sql_dialect" -> OptionSpec(Seq.empty, 1, 1),
    "sql_table" -> OptionSpec(Seq.empty, 1, 1),
    // Backstage catalog generation options; each is resolved by walking up
    // the parent chain, so all are valid on any definition
    "backstage_owner" -> OptionSpec(Seq.empty, 1, 1),
    "backstage_lifecycle" -> OptionSpec(Seq.empty, 1, 1),
    "backstage_type" -> OptionSpec(Seq.empty, 1, 1),
    // Confluence publishing options; the generator reads these from the
    // domain only, so a misplaced one is worth flagging
    "confluence_space" -> OptionSpec(Seq("Domain"), 1, 1),
    "confluence_parent" -> OptionSpec(Seq("Domain"), 1, 1)
  )

  /** The names of every option that is valid on ANY definition, i.e. whose [[OptionSpec]] declares
    * no `validParents`. These are included in the result of [[optionsFor]] for every kind.
    */
  lazy val universalOptions: Seq[String] =
    registry.collect { case (name, spec) if spec.validParents.isEmpty => name }.toSeq.sorted

  /** The option names valid on a definition of the given kind.
    *
    * @param parentKind
    *   The `Definition.kind` string of the containing definition. Remember that a Streamlet's kind
    *   is its shape name ("Source", "Flow", …) and a Portlet's is "Inlet"/"Outlet".
    * @return
    *   Every registry key whose `validParents` contains `parentKind`, PLUS every universal option
    *   (`validParents` empty), sorted for stable output.
    */
  def optionsFor(parentKind: String): Seq[String] =
    registry
      .collect {
        case (name, spec) if spec.validParents.isEmpty || spec.validParents.contains(parentKind) =>
          name
      }
      .toSeq
      .sorted

  /** The union of [[optionsFor]] over several kinds. Needed for the definition families whose kind
    * string varies: a Streamlet (seven shape names) and a Portlet (Inlet/Outlet).
    */
  def optionsForAny(parentKinds: Seq[String]): Seq[String] =
    parentKinds.flatMap(optionsFor).distinct.sorted

  /** The seven possible `Definition.kind` values of a [[AST.Streamlet]] — its shape's simple name.
    */
  val streamletKinds: Seq[String] =
    Seq("Source", "Sink", "Flow", "Merge", "Split", "Router", "Void")

  /** The two possible `Definition.kind` values of a portlet. */
  val portletKinds: Seq[String] = Seq("Inlet", "Outlet")
end RecognizedOptions
