/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

/** The stable identity of a diagnostic RULE.
  *
  * A consumer that wants to filter, suppress, count or fix diagnostics needs to name them. With
  * prose only that means regex-matching message text, which breaks silently the first time a
  * message is reworded -- and messages get reworded. These codes are API: **once published, a code
  * means the same thing forever.** Rewording the human message is always safe; changing or reusing
  * a code is not.
  *
  * ==Why an enum and not a list of constants==
  *
  * This generalizes [[Messages.DeprecationCode]], which was the same idea for deprecations alone --
  * and whose failure mode is the reason for the enum. Its codes were `val`s, with a separate
  * hand-maintained `all: Seq[String]` for consumers wanting an exhaustive report. Twice a code was
  * DEFINED but never added to that list, so "exhaustive" migration reports silently omitted an
  * entire deprecation family for months. `RuleId.values` is exhaustive by construction; there is no
  * second list to forget.
  *
  * ==The non-reuse guarantee is CODE, not documentation==
  *
  * Three mechanisms, all enforced by `RuleIdTest`:
  *   1. `values` is generated, so every rule is enumerable and codes are checked unique.
  *   1. [[RuleId.retired]] names codes that were once emitted and must never be attached to a
  *      different rule. An active code appearing there is a failure.
  *   1. A committed snapshot (`language/src/test/resources/rule-ids.txt`) lists every code ever
  *      seen. Deleting a case without retiring its code fails the test, so a code cannot leave
  *      silently and be reused later.
  *
  * ==The code's shape==
  *
  * `<subject>-<what-is-wrong>`, kebab-case. The subject is the kind of thing the rule is ABOUT, not
  * the pass that happens to report it, so a reader can guess the prefix without reading this file
  * and a consumer can select a whole family with a prefix match.
  *
  * @param code
  *   The stable, published identifier. Kebab-case, subject-prefixed.
  * @param mechanicalFix
  *   Replacement text when this rule's fix is a pure SPAN REPLACEMENT -- the message's `loc` covers
  *   exactly the offending source and swapping in this text resolves it, touching nothing else.
  *   `None` when the fix needs a judgement call or a rewrite somewhere other than the span.
  * @param deprecates
  *   True when the rule reports a deprecated construct. Kept ON THE RULE so the set of deprecations
  *   is derived rather than listed; see the note on `DeprecationCode.all` above.
  */
enum RuleId(
  val code: String,
  val mechanicalFix: Option[String] = None,
  val deprecates: Boolean = false
):

  // ---- ref: reference resolution and path identifiers -------------------------------------
  // `ref-wrong-kind` is emitted from BOTH resolution paths -- ReferenceMap.definitionOf and
  // ResolutionPass.wrongType -- because it is ONE rule reported in two places. That the two paths
  // disagreed about whether to check at all (see CLAUDE.md on resolvePath's missing ClassTag) is
  // exactly why they should answer to a single id.
  case WrongKind extends RuleId("ref-wrong-kind")
  case Ambiguous extends RuleId("ref-ambiguous")
  case AmbiguousSegment extends RuleId("ref-ambiguous-segment")
  case InvalidSymTabAnchor extends RuleId("ref-invalid-symtab-anchor")
  case InvalidParentAnchor extends RuleId("ref-invalid-parent-anchor")
  case EmptyPathInternal extends RuleId("ref-empty-path-internal")
  case EmptyPath extends RuleId("ref-empty-path")
  case WrongKeyword extends RuleId("ref-wrong-keyword")
  case PathUnresolved extends RuleId("ref-path-unresolved")

  // ---- name: identifiers and uniqueness ---------------------------------------------------
  case NameTooShort extends RuleId("name-too-short")
  case DuplicateContentNames extends RuleId("name-duplicate-content")
  case NotInSymbolTable extends RuleId("name-not-in-symbol-table")
  case OverloadedDefinitions extends RuleId("name-overloaded-definitions")
  case EmptyNonImplicitName extends RuleId("name-empty-non-implicit")

  // ---- field ------------------------------------------------------------------------------
  case FieldOverloadedTypes extends RuleId("field-overloaded-types")
  case FieldDuplicateName extends RuleId("field-duplicate-name")

  // ---- type -------------------------------------------------------------------------------
  case TypeAlternatesNotAggregates extends RuleId("type-alternates-not-aggregates")
  case TypeIncompatibleKeyword extends RuleId("type-incompatible-keyword")
  case TypeNeedsAggregate extends RuleId("type-needs-aggregate")
  case TypeNeedsGraphElements extends RuleId("type-needs-graph-elements")
  case TypeNeedsTable extends RuleId("type-needs-table")
  case TypeOverloaded extends RuleId("type-overloaded")
  case ReplicaHasCardinality extends RuleId("type-replica-has-cardinality")
  case ReplicaNotReplicable extends RuleId("type-replica-not-replicable")

  // ---- msg: the four message kinds and what they declare ----------------------------------
  case MessageAlternatesNotAggregates extends RuleId("msg-alternates-not-aggregates")
  case MessageNeedsAggregate extends RuleId("msg-needs-aggregate")
  case MessageRefEmpty extends RuleId("msg-ref-empty")
  case MessageRefWrongKind extends RuleId("msg-ref-wrong-message-kind")
  case MessageRefNotAMessage extends RuleId("msg-ref-not-a-message")
  case YieldsOnlyCommandQuery extends RuleId("msg-yields-only-command-or-query")
  case FlowUnresolvedTellTarget extends RuleId("msg-flow-unresolved-tell-target")
  case FlowUnresolvedMessageType extends RuleId("msg-flow-unresolved-message-type")
  case FlowUnresolvedSendPortlet extends RuleId("msg-flow-unresolved-send-portlet")
  case FlowUnresolvedAdaptorContext extends RuleId("msg-flow-unresolved-adaptor-context")

  // ---- epic: epics, use cases, users, interactions -----------------------------------------
  case PrivateNestedFunction extends RuleId("func-private-nested")

  // ---- stream: portlets, connectors and the shape of a pipeline -----------------------------
  case ConnectorTouchesExternal extends RuleId("stream-connector-touches-external")
  case ConsiderAdaptor extends RuleId("stream-consider-adaptor")
  case ProcessorUnconnected extends RuleId("stream-processor-unconnected")
  case SourceReachesNoSink extends RuleId("stream-source-reaches-no-sink")
  case SinkReachedByNoSource extends RuleId("stream-sink-reached-by-no-source")
  case BoundaryOutlet extends RuleId("stream-boundary-outlet")
  case BoundaryInlet extends RuleId("stream-boundary-inlet")
  case CrossesDomains extends RuleId("stream-crosses-domains")
  case DomainScopeUnnecessary extends RuleId("stream-domain-scope-unnecessary")
  case BoundaryNotPersistent extends RuleId("stream-boundary-not-persistent")
  case CrossesContexts extends RuleId("stream-crosses-contexts")
  case PersistenceNotNeeded extends RuleId("stream-persistence-not-needed")
  case OutletCardinality extends RuleId("stream-outlet-cardinality")
  case InletCardinality extends RuleId("stream-inlet-cardinality")
  case AllPortletsAsync extends RuleId("stream-all-portlets-async")
  case PortletUnconnected extends RuleId("stream-portlet-unconnected")
  case StepNotHandledInState extends RuleId("epic-step-not-handled-in-state")
  case StepGuardedInState extends RuleId("epic-step-guarded-in-state")
  case StepUnwitnessed extends RuleId("epic-step-unwitnessed")

  // ---- use: usage analysis ------------------------------------------------------------------
  case VagueDuration extends RuleId("value-vague-duration")

  // ---- module: modules, includes, imports --------------------------------------------------
  case IncludeExtension extends RuleId("module-include-extension")
  case IncludeContributesNothing extends RuleId("module-include-empty")

  // ---- doc: metadata -- descriptions, authors, terms, figma --------------------------------
  case AuthorUndefined extends RuleId("doc-author-undefined")
  case MultipleBriefs extends RuleId("doc-multiple-briefs")
  case MultipleUlids extends RuleId("doc-multiple-ulids")
  case FigmaRefNotAllowed extends RuleId("doc-figma-ref-not-allowed")
  case FigmaNodeMissing extends RuleId("doc-figma-node-missing")
  case FigmaFileUnreadable extends RuleId("doc-figma-file-unreadable")
  case FigmaFrameDrift extends RuleId("doc-figma-frame-drift")

  // ---- opt: options -------------------------------------------------------------------------
  case OptionDeprecated extends RuleId("opt-deprecated")
  case UnusedDefinition extends RuleId("use-unused-definition")
  case OnlyUsedInPath extends RuleId("use-only-in-path-identifiers")

  // ---- deprecations ------------------------------------------------------------------------
  // These codes PREDATE this enum and arrive from `Messages.DeprecationCode`. Their spellings are
  // already published, so they are reproduced EXACTLY -- including `prompt-statement`, whose rule
  // was renamed to DoStatement in 2026-08-25 while its code deliberately was not. Renaming a rule
  // is a source change; renaming its code is an API break.
  case StateIsRecord extends RuleId("state-is-record", deprecates = true)
  case DoStatement extends RuleId("prompt-statement", mechanicalFix = Some("do"), deprecates = true)
  case SendToInlet extends RuleId("send-to-inlet", deprecates = true)
  case BareStringCondition extends RuleId("bare-string-condition", deprecates = true)
  case AnonymousNebula extends RuleId("anonymous-nebula", deprecates = true)
  case ShapeKeyword extends RuleId("shape-keyword", deprecates = true)
  case AbstractType extends RuleId("abstract-type", mechanicalFix = Some("Anything"), deprecates = true)
  case SingleAlternation extends RuleId("single-alternation", deprecates = true)
  case EntityOptionToIntention extends RuleId("entity-option-to-intention", deprecates = true)
  case TypeFirstAggregate extends RuleId("type-first-aggregate", deprecates = true)
  case ConnectorOptionToIntention extends RuleId("connector-option-to-intention", deprecates = true)
  case QuotedConstantLiteral extends RuleId("quoted-constant-literal", deprecates = true)
end RuleId

object RuleId:

  /** Codes that were emitted by some released version and have since been withdrawn.
    *
    * A code here must never be attached to a different rule: a consumer suppressing it, or a
    * migration script keying on it, would silently change meaning. Retiring is FREE; reusing is
    * not, so when in doubt retire.
    *
    * Empty today because no rule has yet been withdrawn.
    */
  val retired: Set[String] = Set.empty

  /** The closed set of subject prefixes -- the kind of thing a rule is ABOUT.
    *
    * Closed on purpose. A prefix a reader can guess is worth more than one that is precisely
    * accurate, so a new rule joins an existing bucket unless it genuinely belongs to none; adding a
    * subject is a deliberate act, reviewed like any other API change. `RuleIdTest` fails on a code
    * whose prefix is not here, which is what stops the vocabulary drifting one rule at a time.
    */
  val subjects: Set[String] = Set(
    "adaptor", "app", "context", "doc", "domain", "entity", "epic", "field", "func", "handler",
    "invariant", "module", "msg", "name", "opt", "proj", "ref", "repo", "saga", "state", "stmt",
    "stream", "type", "use", "value"
  )

  /** Codes that PREDATE the subject-prefix scheme and are exempt from it.
    *
    * These twelve shipped as deprecation codes before rule ids were generalized, and a published
    * code means the same thing forever -- so `shape-keyword` and `abstract-type` keep spellings
    * that no longer say what they are ABOUT. Renaming them to fit the scheme would be an API break
    * for exactly zero benefit to the reader of an existing migration script.
    *
    * **Nothing may be added here.** It is a closed record of history, not an escape hatch: a new
    * rule that cannot find a subject needs a subject added, not an exemption.
    */
  val grandfathered: Set[String] = Set(
    "state-is-record", "prompt-statement", "send-to-inlet", "bare-string-condition",
    "anonymous-nebula", "shape-keyword", "abstract-type", "single-alternation",
    "entity-option-to-intention", "type-first-aggregate", "connector-option-to-intention",
    "quoted-constant-literal"
  )

  /** Lookup by published code, for a consumer parsing `--fix-rule <id>` or a suppression list. */
  lazy val byCode: Map[String, RuleId] = values.map(r => r.code -> r).toMap

  def parse(code: String): Option[RuleId] = byCode.get(code)

  /** Every deprecation rule, DERIVED. The predecessor to this enum kept a hand-maintained list
    * beside the definitions, and twice a code was defined but never added to it -- so an
    * "exhaustive" migration report silently omitted a whole family for months. Nothing to forget
    * here.
    */
  lazy val deprecations: Seq[RuleId] = values.filter(_.deprecates).toSeq

  /** Every rule whose fix is a pure span replacement, as code -> replacement text. */
  lazy val mechanicalReplacements: Map[String, String] =
    values.flatMap(r => r.mechanicalFix.map(r.code -> _)).toMap

  /** The subject prefix -- the part before the first `-`. Lets a consumer select a whole family. */
  extension (r: RuleId) def subject: String = r.code.takeWhile(_ != '-')
end RuleId
