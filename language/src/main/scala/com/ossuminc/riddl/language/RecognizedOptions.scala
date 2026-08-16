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
  maxArgs: Int = 0,
  /** The parent kinds for which this option is DEPRECATED, which is not always all of them.
    *
    * Deprecation is per (option, kind), not per name: `consistent`, `available` and `transient`
    * became entity INTENTIONS in 2.0 and are deprecated on an Entity, while remaining perfectly
    * current on a Repository, which has no intentions. A flat "this name is deprecated" table
    * would wrongly condemn the Repository spelling.
    *
    * Empty means not deprecated anywhere. The name still parses and is still RECOGNIZED either
    * way — this marks the AUTHORING surface only.
    */
  deprecatedFor: Seq[String] = Seq.empty,
  /** What to write instead, phrased for a human. Present whenever `deprecatedFor` is non-empty. */
  replacement: Option[String] = None,
  /** Severity of a `validParents` violation for THIS option.
    *
    * StyleWarning by default, because most misplaced options are a tidiness matter: the option is
    * simply ignored where it sits. It is `Error` when putting the option there asserts something
    * about the model that is not true, which no generator could honour and no reader should
    * believe -- see `persistent` below. Per-option on purpose: promoting every violation to an
    * Error would be a corpus-wide behaviour change nobody asked for.
    */
  severity: Messages.KindOfMessage = Messages.StyleWarning
)

/** The current and deprecated option names for one definition kind, together.
  *
  * Returned as one structure rather than two independent calls because an authoring tool needs
  * BOTH and they must agree: the picker offers `current`, but a model already using a deprecated
  * spelling must still be RECOGNIZED when rendered, or the tool reports "not a recognized RIDDL
  * option" about a name RIDDL accepts. Two calls let a consumer use one and forget the other.
  * (Asked for by synapify, 2026-08-05, whose option picker was offering the deprecated spellings
  * and then flagging the deprecation it had just invited.)
  */
case class RecognizedOptionSet(
  current: Seq[String],
  deprecated: Seq[String],
  /** Deprecated name -> what to write instead. Keys match `deprecated` exactly. */
  replacements: Map[String, String]
):
  /** Every recognized name for the kind, deprecated ones included — what `optionsFor` returns. */
  def all: Seq[String] = (current ++ deprecated).sorted
end RecognizedOptionSet

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
    "aggregate" -> OptionSpec(Seq("Entity"), 0, 0, Seq("Entity"), Some("write `aggregate` before `entity`")),
    "auto-id" -> OptionSpec(Seq("Entity"), 0, 0),
    "finite-state-machine" -> OptionSpec(Seq("Entity"), 0, 0),
    // `persistent` states DOMAIN DURABILITY, so it is only meaningful where there is state to
    // persist -- and a Connector is the only definition that takes it as an OPTION. An Entity says
    // persistence with an intention keyword (`persistent entity X`), and a Repository is persistent
    // by implication, so neither wants it here. Everything else, a Context above all, holds no state
    // of its own: §3 of the computational model has domain state living in the Entities,
    // Repositories and Projectors a Context CONTAINS, never in the Context itself.
    //
    // Hence Error rather than the default StyleWarning (Reid, 2026-08-07). The distinction is not
    // severity for its own sake: a misplaced option is usually ignorable, but `persistent` on a
    // stateless definition ASSERTS durability that nothing can provide, so it is a modelling
    // mistake rather than a weaker-but-legitimate choice. Contrast A35's cross-boundary connector
    // warning, deliberately a warning because the in-memory downgrade is a legitimate deployment
    // decision -- there is no equivalent reading here.
    //
    // Filed by riddl-generator for the `gateway` case; the ruling generalised past it.
    //
    // The connector INTENTIONS shipped in 2.0.0-rc.14, so this entry is now DEPRECATED for
    // Connector rather than removed -- `StreamingParser` consumes `option persistent` into the
    // intention and reports a Deprecation, and all 426 uses across riddl-models must keep parsing.
    // Marking it here is what stops an authoring tool inviting the old spelling: synapify's "add
    // option" picker is driven by `optionSetFor(kind).current`, so while this said "current" the
    // panel offered a spelling whose own Problems pane then flagged it (filed 2026-08-14, the same
    // loop the current/deprecated split was built to close for entity intentions).
    //
    // `severity` is retained for the parents check: where the option IS still written, naming a
    // non-Connector parent asserts durability nothing can provide, which stays an Error.
    "persistent" -> OptionSpec(
      Seq("Connector"),
      0,
      0,
      Seq("Connector"),
      Some("write `persistent` before `connector`"),
      severity = Messages.Error
    ),
    // `clustered` says a SINGLETON processor is deployed as several interchangeable copies behind
    // one address (Reid, 2026-08-16). The Computational Model already treats clusterability as a
    // property of every processor -- what was missing was any way to SAY it in a model.
    //
    // An OPTION rather than a grammar intention, deliberately, and this is the same test the
    // entity and connector intentions were judged by: may a generator decline to honour it? Here
    // it may -- deploying one instance is a legitimate realization of `clustered` -- so it is
    // advisory, which is what §4.2 says an option is. Contrast `event-sourced`, where declining
    // changes what the model MEANS, and which therefore had to become grammar.
    //
    // The one HARD rule nearby needs no keyword at all: a projector with correlations must
    // distribute by key rather than round-robin (§6.1/§6.6), or events bearing one key tuple reach
    // instances that do not hold its partial. That is already implied by declaring correlations.
    //
    // NOT valid on an Entity: an entity is already distributed BY IDENTITY (§4.1), one instance
    // per identity value, so it is sharded by construction and `clustered` would state nothing.
    // Left at the default StyleWarning rather than Error -- unlike `persistent` on a stateless
    // definition, `clustered` on an entity asserts nothing false, it is merely redundant.
    //
    // `self.isClustered` was declined alongside this: writing the option is precisely what makes
    // clustering STATICALLY knowable, so a runtime field would ask for what a generator can see.
    //
    // A Streamlet's parent kind is its SHAPE's simple name and never "Streamlet" -- see the
    // parent-kind note at the top of this file -- so the shapes are spelled out.
    "clustered" -> OptionSpec(
      Seq(
        "Context",
        "Projector",
        "Repository",
        "Adaptor",
        "Source",
        "Sink",
        "Flow",
        "Merge",
        "Split",
        "Router",
        "Void"
      ),
      0,
      0
    ),
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
    "event-sourced" -> OptionSpec(Seq("Entity"), 0, 0, Seq("Entity"), Some("write `event-sourced` before `entity`")),
    "value" -> OptionSpec(Seq("Entity"), 0, 0, Seq("Entity"), Some("write `persistent` before `entity`")),
    // CAP markers. Meaningful on a Repository as well as an Entity: the computational model
    // (§5.6) rules that a Repository is a Processor, so its WRITE side is single-writer by
    // default and `available` hands write arbitration to the storage engine, permitting
    // concurrent writes. Queries are side-effect-free and always concurrent either way. Same
    // "meaningful on both" reasoning as `transient` below.
    "consistent" -> OptionSpec(Seq("Entity", "Repository"), 0, 0, Seq("Entity"), Some("write `consistent` before `entity`")),
    "available" -> OptionSpec(Seq("Entity", "Repository"), 0, 0, Seq("Entity"), Some("write `available` before `entity`")),
    "message-queue" -> OptionSpec(Seq("Entity"), 0, 0),
    // Marks the inlet that receives hard-error notifications, redirecting them from the
    // predefined `Riddl.Operations` sink to a receiver the model owns. On the INLET rather than
    // the processor because an inlet names the receiver, the port and the message type in one
    // place; a processor may have several inlets and a generator would be back to guessing.
    // At most one per DOMAIN -- see `checkErrorSinkUniqueness` in ValidationPass.
    "error-sink" -> OptionSpec(Seq("Inlet"), 0, 0),
    // `transient` marks state that is NOT durably persisted. That is meaningful both
    // for an Entity and for a Repository (a cache-like, non-durable store).
    "transient" -> OptionSpec(Seq("Entity", "Repository"), 0, 0, Seq("Entity"), Some("write `transient` before `entity`")),
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
    // "Saga" added for A10, the same shape `timeout` has: ONE concept at TWO scopes, with the
    // parent kind disambiguating. On a step it bounds that step; on a saga it bounds every step
    // that does not state its own. A second name (`step-retry`) was considered and rejected --
    // it would make one concept look like two and leave the existing SagaStep `retry` needing
    // to be told apart from it.
    //
    // PRECEDENCE, a contract between generators that riddlc does NOT enforce: a step's own
    // `retry` wins for that step; the saga's applies to steps without one; absent both, the A10
    // default of 1. Same rule for `timeout`.
    //
    // The optional SECOND argument is a backoff duration (`retry("3", "2s")`) and is
    // duration-validated like `timeout`/`delay`. A backoff STRATEGY is deliberately not
    // modelled: §9.8 leaves "retry/backoff realization within the option-specified bounds" to
    // the generator. The bound is the model's business; the curve is not.
    "retry" -> OptionSpec(
      Seq("Saga", "SagaStep", "Handler"),
      1,
      2
    ),
    // A10. Max retries of a failed UNDO, default 3. Named for the thing being retried: a step
    // says `reverted by { … }` and every generator and diagram calls that block the undo.
    // `compensation-retry` would read as retrying the entire compensating walk.
    "undo-retry" -> OptionSpec(
      Seq("Saga"),
      1,
      1
    ),
    // A10. The text reported when undo retries are exhausted or the run is otherwise
    // unrecoverable. NOT named `error`: that collides with the `error` STATEMENT keyword, which
    // is a refusal mechanism with its own semantics, so an option of that name would read as
    // declaring one. The value is not an error; it is the text reported about one.
    "failure-message" -> OptionSpec(
      Seq("Saga"),
      1,
      1
    ),
    "delay" -> OptionSpec(
      Seq("SagaStep"),
      1,
      1
    ),
    // DELIBERATELY ABSENT: `compensate`. It was registered here (fbf47a8a1) as a saga option
    // declaring that failure runs the steps' undo blocks in reverse -- but that is not a
    // declaration a model gets to make, it is what a Saga IS. `SagaParser.sagaStep` requires
    // `reverted by` on EVERY step, so a saga without compensation cannot be written, and the
    // option therefore distinguished no state of the world. The computational model agrees:
    // §9.8's "must preserve" list names reverse-order compensation and the terminal dichotomy
    // (succeeded xor compensated) with no option qualifying either.
    //
    // The A10 citation in that commit was also wrong: Tools-To-Do-List Part A item 10 asks for
    // a timeout, step retries, undo retries and an error string -- `compensate` is not among
    // them and appears nowhere in the to-do list or the computational model.
    //
    // It caused a real defect before removal: riddl-generator read the registration as a switch
    // and emitted a coordinator that abandoned completed steps on failure unless the model
    // carried the option. Do not re-register it.
    // Saga-level parallelism marker (A11). A saga is SEQUENTIAL by definition; `parallel` declares
    // the exception. The semantics are a contract for the code generator (riddl-gen), NOT something
    // riddlc acts on: all steps start in parallel; the coordinator gathers results asynchronously;
    // when they all succeed the saga succeeds; any one failure triggers compensating actions in
    // REVERSE order of the original sends. Registration only — no behavioral validation, mirroring
    // how A7 added `async`. (An earlier version of this comment credited A10 with `compensate`;
    // A10 asks for no such option, and `compensate` has since been deregistered.)
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
    // Deprecated 2026-08-14 alongside `persistent`, and for the same reason: each is now a
    // connector INTENTION written before the keyword, and the parser CONSUMES the option spelling
    // into it. Until this they parsed as plain registry options, meant nothing and drew no message
    // at all -- two spellings where one was silently inert (reported by synapify). `exactly-once`
    // became a third delivery intention on the same day, which is what unblocked deprecating all
    // three together instead of two of three.
    "at-least-once" -> OptionSpec(
      Seq("Connector"),
      0,
      0,
      Seq("Connector"),
      Some("write `at-least-once` before `connector`")
    ),
    "at-most-once" -> OptionSpec(
      Seq("Connector"),
      0,
      0,
      Seq("Connector"),
      Some("write `at-most-once` before `connector`")
    ),
    "exactly-once" -> OptionSpec(
      Seq("Connector"),
      0,
      0,
      Seq("Connector"),
      Some("write `exactly-once` before `connector`")
    ),
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
    // Message envelope selection. Names the record carrying each message's metadata --
    // `option message_envelope("Riddl.Envelope")` for the predefined CloudEvents-shaped
    // record, or a model's own. SCOPE-INHERITED: declared on a context it applies to all
    // messaging in that context, including every entity within it, so it is resolved by
    // walking UP the parent chain and is therefore valid anywhere (no validParents).
    // Opt-in by design -- RIDDL specifies meaning, not representation, so an envelope is
    // never imposed on a model that has no bus to carry one.
    "message_envelope" -> OptionSpec(Seq.empty, 1, 1),
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
  def optionsFor(parentKind: String): Seq[String] = optionSetFor(parentKind).all

  /** The recognized options for a kind, SPLIT into those still current and those deprecated there.
    *
    * This is the authoring-surface answer: a tool offers `current` in an "add option" picker and
    * keeps recognizing `deprecated` when rendering a model that already uses one. [[optionsFor]] is
    * `all` of this, so the flat list and the split cannot drift — the whole reason this repo keeps
    * one registry rather than two tables.
    *
    * A name deprecated for one kind may be current for another; see [[OptionSpec.deprecatedFor]].
    */
  def optionSetFor(parentKind: String): RecognizedOptionSet =
    val applicable = registry.collect {
      case (name, spec) if spec.validParents.isEmpty || spec.validParents.contains(parentKind) =>
        name -> spec
    }
    val (dep, cur) = applicable.partition(_._2.deprecatedFor.contains(parentKind))
    RecognizedOptionSet(
      current = cur.keys.toSeq.sorted,
      deprecated = dep.keys.toSeq.sorted,
      replacements = dep.collect { case (n, s) if s.replacement.nonEmpty => n -> s.replacement.get }
    )
  end optionSetFor

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
