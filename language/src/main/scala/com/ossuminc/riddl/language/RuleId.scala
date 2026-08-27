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
  *   How to rewrite the span when this rule's fix is a pure SPAN REPLACEMENT -- the message's `loc`
  *   covers exactly the offending source and swapping in the result resolves it, touching nothing
  *   else. `None` when the fix needs a judgement call or a rewrite somewhere other than the span.
  * @param deprecates
  *   True when the rule reports a deprecated construct. Kept ON THE RULE so the set of deprecations
  *   is derived rather than listed; see the note on `DeprecationCode.all` above.
  */
/** How a rule's fix produces its replacement text.
  *
  * A sum type rather than a second field beside `Option[String]`: two fields describing one fix can
  * disagree, and this repo keeps recording that shape as a defect.
  *
  * The distinction is real, not bookkeeping. A CONSTANT fix is expressible in the published
  * `Map[String, String]` that `RiddlLib.deprecationEdits` and `DeprecationCode.mechanicalReplacement`
  * hand to consumers; a COMPUTED one is not, because it needs the matched text. Keeping them
  * separate is what lets that map stay honest about what it can carry instead of silently omitting
  * or mis-stating the computed ones.
  */
enum Fix:

  /** The span is replaced by this exact text, whatever it matched. */
  case Constant(text: String)

  /** The span is replaced by `f(matched)` -- the fix depends on what it matched.
    *
    * `quoted-constant-literal` is the reason this exists: `constant N: Integer = "5"` becomes `5`,
    * and the replacement is the matched text minus its quotes. That is a pure span replacement --
    * it just is not a constant one, and it was excluded from `--fix` purely for want of a way to
    * say so.
    */
  case Computed(f: String => String)

  /** The replacement for a given matched span. */
  def apply(matched: String): String = this match
    case Constant(text) => text
    case Computed(f)    => f(matched)

  /** The constant text, when there is one. What the published `Map[String, String]` can carry. */
  def constantText: Option[String] = this match
    case Constant(text) => Some(text)
    case _: Computed    => None
end Fix

enum RuleId(
  val code: String,
  val mechanicalFix: Option[Fix] = None,
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
  case DoStatement extends RuleId("prompt-statement", mechanicalFix = Some(Fix.Constant("do")), deprecates = true)
  case SendToInlet extends RuleId("send-to-inlet", deprecates = true)
  case BareStringCondition extends RuleId("bare-string-condition", deprecates = true)
  case AnonymousNebula extends RuleId("anonymous-nebula", deprecates = true)
  case ShapeKeyword extends RuleId("shape-keyword", deprecates = true)
  case AbstractType extends RuleId("abstract-type", mechanicalFix = Some(Fix.Constant("Anything")), deprecates = true)
  case SingleAlternation extends RuleId("single-alternation", deprecates = true)
  case EntityOptionToIntention extends RuleId("entity-option-to-intention", deprecates = true)
  case TypeFirstAggregate extends RuleId("type-first-aggregate", deprecates = true)
  case ConnectorOptionToIntention extends RuleId("connector-option-to-intention", deprecates = true)
  // The fix is the matched text minus its surrounding quotes -- `"5"` becomes `5`. A pure span
  // replacement that simply is not a CONSTANT one, which is the only reason it was excluded from
  // `--fix` until [1.16].
  case QuotedConstantLiteral
      extends RuleId(
        "quoted-constant-literal",
        mechanicalFix = Some(Fix.Computed(m => m.stripPrefix("\"").stripSuffix("\""))),
        deprecates = true
      )

  // ---- handler: handlers and their on-clauses ----------------------------------------------
  case HandlerNoExecutableStatements extends RuleId("handler-no-executable-statements")
  case HandlerOnlyDoStatements extends RuleId("handler-only-do-statements")
  case EmptyOnOther extends RuleId("handler-empty-on-other")
  case CommandNoResponse extends RuleId("handler-command-no-response")
  case QueryNoReply extends RuleId("handler-query-no-reply")
  case BindingShadowsField extends RuleId("handler-binding-shadows-field")
  case OnOtherNoEnvelope extends RuleId("handler-on-other-no-envelope")
  case OnOtherUnbound extends RuleId("handler-on-other-unbound")
  case OnOtherEnvelopeConflict extends RuleId("handler-on-other-envelope-conflict")

  // ---- stmt: statements and their operands -------------------------------------------------
  case ForwardWrongClause extends RuleId("stmt-forward-wrong-clause")
  case ForwardWrongMessage extends RuleId("stmt-forward-wrong-message")
  case SelfNotAMessage extends RuleId("stmt-self-not-a-message")
  case OperandWrongType extends RuleId("stmt-operand-wrong-type")
  case OperandNotAMessage extends RuleId("stmt-operand-not-a-message")
  case MorphOperandUnresolved extends RuleId("stmt-morph-operand-unresolved")
  case MorphOperandType extends RuleId("stmt-morph-operand-type")
  case OperandIsTypeNotValue extends RuleId("stmt-operand-is-type-not-value")
  case EffectNotAllowed extends RuleId("stmt-effect-not-allowed")
  case InitiateIdUnused extends RuleId("stmt-initiate-id-unused")

  // ---- state: entity state, and who may read or write it -----------------------------------
  case SetNotAllowed extends RuleId("state-set-not-allowed")
  case StateReadNotAllowed extends RuleId("state-read-not-allowed")
  case StateReadForeign extends RuleId("state-read-foreign")
  case MultipleInitialHandlers extends RuleId("state-multiple-initial-handlers")
  case StateEmptyAggregate extends RuleId("state-empty-aggregate")

  // ---- invariant ----------------------------------------------------------------------------
  case InvariantNeverApplied extends RuleId("invariant-requires-never-applied")
  case InvariantRequiresArgument extends RuleId("invariant-requires-argument")
  case InvariantUnexpectedArgument extends RuleId("invariant-unexpected-argument")
  case InvariantShadows extends RuleId("invariant-shadows")
  case InvariantRequiresStateMisplaced extends RuleId("invariant-requires-state-misplaced")
  case InvariantRequiresStateRedundant extends RuleId("invariant-requires-state-redundant")
  case InvariantNoStateToRead extends RuleId("invariant-no-state-to-read")

  // ---- context ------------------------------------------------------------------------------
  case QueriesWithoutResults extends RuleId("context-queries-without-results")
  case ResultsWithoutQueries extends RuleId("context-results-without-queries")

  // ---- more msg -----------------------------------------------------------------------------
  case TellNotDeliverable extends RuleId("msg-tell-not-deliverable")
  case TellTargetUnreachable extends RuleId("msg-tell-target-unreachable")
  case CommandNoFields extends RuleId("msg-command-no-fields")
  case EventNeverEmitted extends RuleId("msg-event-never-emitted")
  case YieldUndeclared extends RuleId("msg-yield-undeclared")
  case YieldMismatch extends RuleId("msg-yield-mismatch")
  case AnswersUndeclared extends RuleId("msg-answers-undeclared")
  case WrongResponseKind extends RuleId("msg-wrong-response-kind")

  // ---- more stream --------------------------------------------------------------------------
  case InletNotReceived extends RuleId("stream-inlet-not-received")
  case PortletTypeMismatch extends RuleId("stream-portlet-type-mismatch")
  case ConflictingIntentions extends RuleId("stream-conflicting-intentions")
  case ConnectorTypeMismatch extends RuleId("stream-connector-type-mismatch")
  case OutletUnresolved extends RuleId("stream-outlet-unresolved")
  case InletUnresolved extends RuleId("stream-inlet-unresolved")

  // ---- more doc -----------------------------------------------------------------------------
  case TermInconsistent extends RuleId("doc-term-inconsistent")
  case MultipleVersions extends RuleId("doc-multiple-versions")
  case MultipleCopyrights extends RuleId("doc-multiple-copyrights")

  // ---- more name ----------------------------------------------------------------------------
  case ShouldBeLowercase extends RuleId("name-should-be-lowercase")
  case ShadowsDefinition extends RuleId("name-shadows-definition")

  // ---- more value ---------------------------------------------------------------------------
  case NotWholeNumber extends RuleId("value-not-whole-number")
  case NotNatural extends RuleId("value-not-natural")
  case NotWhole extends RuleId("value-not-whole")

  // ---- repo: repositories and their schemas -------------------------------------------------
  case SchemaShouldNotHaveLinks extends RuleId("repo-schema-should-not-have-links")
  case FlatSchemaManyNodes extends RuleId("repo-flat-schema-many-nodes")
  case TimeSeriesNoIndices extends RuleId("repo-time-series-no-indices")
  case HierarchicalNoLinks extends RuleId("repo-hierarchical-no-links")
  case StarNoLinks extends RuleId("repo-star-no-links")
  case GraphNoLinks extends RuleId("repo-graph-no-links")
  case RelationalNoLinks extends RuleId("repo-relational-no-links")
  case LinkTypeMismatch extends RuleId("repo-link-type-mismatch")
  case VectorManyNodes extends RuleId("repo-vector-many-nodes")
  case RepositoryNoHandler extends RuleId("repo-no-handler")
  case RepositoryNoCommandsOrQueries extends RuleId("repo-no-commands-or-queries")
  case RepositoryInletCarriesEvent extends RuleId("repo-inlet-carries-event")

  // ---- entity --------------------------------------------------------------------------------
  case MultipleInitialStates extends RuleId("entity-multiple-initial-states")
  case EntityMultipleInitialHandlers extends RuleId("entity-multiple-initial-handlers")
  case FsmSingleState extends RuleId("entity-fsm-single-state")
  case StateNoInit extends RuleId("entity-state-no-init")
  case StateInitSetsNothing extends RuleId("entity-state-init-sets-nothing")
  case EntityNoHandlers extends RuleId("entity-no-handlers")
  case EntityNoQueryClause extends RuleId("entity-no-query-clause")
  case EntityNoInlet extends RuleId("entity-no-inlet")
  case EntityNoOutlet extends RuleId("entity-no-outlet")
  case EntityNoIdType extends RuleId("entity-no-id-type")
  case IdDefinedInside extends RuleId("entity-id-defined-inside")
  case IdDefinedOutside extends RuleId("entity-id-defined-outside")
  case EntityNoCommandTypes extends RuleId("entity-no-command-types")
  case EntityNoEventTypes extends RuleId("entity-no-event-types")
  case CommandNotHandled extends RuleId("entity-command-not-handled")
  case EntityConflictingIntentions extends RuleId("entity-conflicting-intentions")
  case SnapshotsNotEventSourced extends RuleId("entity-snapshots-not-event-sourced")
  case EventSourcedCommandNoYields extends RuleId("entity-event-sourced-command-no-yields")
  case EventSourcedEventNoClause extends RuleId("entity-event-sourced-event-no-clause")
  case EventSourcedMutationScope extends RuleId("entity-event-sourced-mutation-scope")

  // ---- proj: projectors and correlations -----------------------------------------------------
  case CorrelationFieldSetTwice extends RuleId("proj-correlation-field-set-twice")
  case CorrelationNeverCompletes extends RuleId("proj-correlation-never-completes")
  case CorrelationWrongClause extends RuleId("proj-correlation-wrong-clause")
  case CorrelationRepoNoHandler extends RuleId("proj-repository-no-handler")
  case FoldEffect extends RuleId("proj-fold-effect")
  case ProjectionTypeNotInRepository extends RuleId("proj-type-not-in-repository")
  case ProjectorNoRepository extends RuleId("proj-no-repository")
  case ProjectorHandlesNoEvents extends RuleId("proj-handles-no-events")
  case ProjectorNoPersistence extends RuleId("proj-no-persistence")
  case ProjectorRepositoryUnused extends RuleId("proj-repository-unused")

  // ---- more handler / stmt / module / func ---------------------------------------------------
  case ClauseShadowed extends RuleId("handler-clause-shadowed")
  case EntityNoCommandsOrQueries extends RuleId("handler-entity-no-commands-or-queries")
  case RepositoryHandlesEvents extends RuleId("handler-repository-handles-events")
  case ImportNotAllowedHere extends RuleId("module-import-not-allowed-here")
  case NoInstanceAddress extends RuleId("msg-no-instance-address")
  case ReadBeforeCreation extends RuleId("state-read-before-creation")
  case MorphSingleState extends RuleId("stmt-morph-single-state")
  case BecomeSingleHandler extends RuleId("stmt-become-single-handler")
  case OverriddenSet extends RuleId("stmt-overridden-set")
  case InlineAggregation extends RuleId("func-inline-aggregation", deprecates = true)

  // ---- adaptor -------------------------------------------------------------------------------
  case AdaptorTargetsOwnContext extends RuleId("adaptor-targets-own-context")
  case AdaptorNoHandler extends RuleId("adaptor-no-handler")
  case AdaptorEmptyHandlers extends RuleId("adaptor-empty-handlers")
  case AdaptorNoOnOther extends RuleId("adaptor-no-on-other")
  case AdaptorDirectionAdvisory extends RuleId("adaptor-direction-advisory")
  case AdaptorInboundWrongMessage extends RuleId("adaptor-inbound-wrong-message")
  case AdaptorOutboundWrongMessage extends RuleId("adaptor-outbound-wrong-message")
  case AdaptorMessageNotInContext extends RuleId("adaptor-message-not-in-context")
  case AdaptorNotInContext extends RuleId("adaptor-not-in-context")
  case AdaptorDuplicate extends RuleId("adaptor-duplicate")

  // ---- saga ------------------------------------------------------------------------------------
  case SagaStepReferencesForeign extends RuleId("saga-step-references-foreign")
  case SagaNoTimeout extends RuleId("saga-no-timeout")
  case SagaStepNoTell extends RuleId("saga-step-no-tell")
  case SagaStepMayNotAsk extends RuleId("saga-step-may-not-ask")

  // ---- more stream -----------------------------------------------------------------------------
  case AscribedShapeMismatch extends RuleId("stream-ascribed-shape-mismatch")
  case PortsWithoutShape extends RuleId("stream-ports-without-shape")
  case StreamletNoHandler extends RuleId("stream-streamlet-no-handler")
  case StreamletSendsNothing extends RuleId("stream-streamlet-sends-nothing")
  case SourceNoInit extends RuleId("stream-source-no-init")
  case NoErrorSink extends RuleId("stream-no-error-sink")
  case DuplicateErrorSink extends RuleId("stream-duplicate-error-sink")

  // ---- more context / app ----------------------------------------------------------------------
  case ServiceShape extends RuleId("context-service-shape")
  case GatewayShape extends RuleId("context-gateway-shape")
  case EntitiesWithoutRepository extends RuleId("context-entities-without-repository")
  case GroupsNeedApplicationContext extends RuleId("app-groups-need-application-context")
  case SelectionVerbType extends RuleId("app-selection-verb-type")

  // ---- more epic -------------------------------------------------------------------------------
  case EpicMissingUserStory extends RuleId("epic-missing-user-story")
  case UserMissingRole extends RuleId("epic-user-missing-role")
  case SequentialEmpty extends RuleId("epic-sequential-empty")
  case ParallelEmpty extends RuleId("epic-parallel-empty")
  case OptionalEmpty extends RuleId("epic-optional-empty")
  case EmptyRelationship extends RuleId("epic-empty-relationship")
  case NoInteractions extends RuleId("epic-no-interactions")
  case InteractionOutsideBoundary extends RuleId("epic-interaction-outside-boundary")
  case InteractionNoTerms extends RuleId("epic-interaction-no-terms")

  // ---- more repo / handler / state -------------------------------------------------------------
  case QueriedWithoutIndex extends RuleId("repo-queried-without-index")
  case RepositoryDomainScope extends RuleId("repo-domain-scope-misplaced")
  case RepositoryHandlesForeign extends RuleId("repo-handles-foreign-contexts")
  case StreamletForeignMessage extends RuleId("handler-streamlet-foreign-message")
  case SetStateNotCurrent extends RuleId("state-set-not-current")

  // ---- more stmt -------------------------------------------------------------------------------
  case SetAfterMorph extends RuleId("stmt-set-after-morph")
  case MorphAfterMorph extends RuleId("stmt-morph-after-morph")
  case Unreachable extends RuleId("stmt-unreachable")
  case RefusalAfterEffect extends RuleId("stmt-refusal-after-effect")
  case ForeachMappingBindsTwo extends RuleId("stmt-foreach-mapping-binds-two")
  case ForeachSecondName extends RuleId("stmt-foreach-second-name")
  case ForeachLocalNotCollection extends RuleId("stmt-foreach-local-not-collection")
  case ForeachLocalUntyped extends RuleId("stmt-foreach-local-untyped")
  case ForeachNotALocal extends RuleId("stmt-foreach-not-a-local")
  case ForeachFieldNotCollection extends RuleId("stmt-foreach-field-not-collection")

  // ---- more value / msg ------------------------------------------------------------------------
  case AtNeedsCollection extends RuleId("value-at-needs-collection")
  case AtWrongArity extends RuleId("value-at-wrong-arity")
  case IndexNumberMismatch extends RuleId("value-index-number-mismatch")
  case IndexStringMismatch extends RuleId("value-index-string-mismatch")
  case AskNotHandled extends RuleId("msg-ask-not-handled")

  // ---- final ValidationPass batch: statements, values, calls and constructors -----------------
  case AskNoReplies extends RuleId("msg-ask-no-replies")
  case AskNotAQuery extends RuleId("msg-ask-not-a-query")
  case AddressAmbiguous extends RuleId("msg-address-ambiguous")
  case TargetCrossesBoundary extends RuleId("msg-target-crosses-boundary")
  case CodeNotPortable extends RuleId("stmt-code-not-portable")
  case NoAddressField extends RuleId("msg-no-address-field")
  case ClauseNoParameters extends RuleId("handler-clause-no-parameters")
  case ClauseWrongArity extends RuleId("handler-clause-wrong-arity")
  case NotInstantiable extends RuleId("stmt-not-instantiable")
  case TerminateNeedsId extends RuleId("stmt-terminate-needs-id")
  case IdEntityMismatch extends RuleId("stmt-id-entity-mismatch")
  case TellValueNeedsId extends RuleId("stmt-tell-value-needs-id")
  case TellCrossesDomain extends RuleId("stmt-tell-crosses-domain")
  case TellCrossesContext extends RuleId("stmt-tell-crosses-context")
  case MatchNotExhaustive extends RuleId("stmt-match-not-exhaustive")
  case PutTypeMismatch extends RuleId("stmt-put-type-mismatch")
  case ReturnTypeMismatch extends RuleId("stmt-return-type-mismatch")
  case YieldAfterForward extends RuleId("stmt-yield-after-forward")
  case ReplyAfterForward extends RuleId("stmt-reply-after-forward")
  case ForwardNotLast extends RuleId("stmt-forward-not-last")
  case StateRecordOutOfScope extends RuleId("state-record-out-of-scope")
  case UnknownTypeCase extends RuleId("type-unknown-type-case")
  case PatternNotAMember extends RuleId("type-pattern-not-a-member")
  case SystemNotStandalone extends RuleId("value-system-not-standalone")
  case SystemUnknownMember extends RuleId("value-system-unknown-member")
  case EmptyNeedsZeroCardinality extends RuleId("value-empty-needs-zero-cardinality")
  case ValueRefUnresolved extends RuleId("value-ref-unresolved")
  case SelfMisplaced extends RuleId("value-self-misplaced")
  case SelfUnknownField extends RuleId("value-self-unknown-field")
  case OperandNotBoolean extends RuleId("value-operand-not-boolean")
  case WhenNotBoolean extends RuleId("value-when-not-boolean")
  case ComparandUnresolved extends RuleId("value-comparand-unresolved")
  case LiteralComparisonStyle extends RuleId("value-literal-comparison-style")
  case IncomparableKinds extends RuleId("value-incomparable-kinds")
  case OrderingNeedsNumeric extends RuleId("value-ordering-needs-numeric")
  case PatternIncomparable extends RuleId("value-pattern-incomparable")
  case PatternOrderingNumeric extends RuleId("value-pattern-ordering-numeric")
  case EmptyNotAllowed extends RuleId("value-empty-not-allowed")
  case ValueTypeMismatch extends RuleId("value-type-mismatch")
  case PromptAscriptionContradicts extends RuleId("value-prompt-ascription-contradicts")
  case ArgumentTypeMismatch extends RuleId("value-argument-type-mismatch")
  case ArgumentDuplicated extends RuleId("value-argument-duplicated")
  case PositionalAfterNamed extends RuleId("value-positional-after-named")
  case NotAField extends RuleId("value-not-a-field")
  case EmptyNotAllowedForField extends RuleId("value-empty-not-allowed-for-field")
  case ConstructorTooManyArgs extends RuleId("value-constructor-too-many-args")
  case ConstructorMissingFields extends RuleId("value-constructor-missing-fields")
  case UntypedPromptSeam extends RuleId("value-untyped-prompt-seam")
  case CallNoReturns extends RuleId("func-call-no-returns")
  case CallPositionalAfterNamed extends RuleId("func-call-positional-after-named")
  case CallNotAnInput extends RuleId("func-call-not-an-input")
  case CallTooManyArgs extends RuleId("func-call-too-many-args")
  case CallTooManyPositional extends RuleId("func-call-too-many-positional")

  // ---- bast: reading a binary AST file --------------------------------------------------------
  // A BAST error names where the reader DERAILED, never what derailed it -- the same constant
  // surfaced as "Invalid string table index" in one model and "Invalid invariant condition kind"
  // in another. So these ids identify the DECODE SITE, which is the only honest thing they know.
  case BastUnknownNodeTag extends RuleId("bast-unknown-node-tag")
  case BastUnknownShapeTag extends RuleId("bast-unknown-shape-tag")
  case BastInvalidShapePresence extends RuleId("bast-invalid-shape-presence")
  case BastUnknownTypeRefSubtype extends RuleId("bast-unknown-type-ref-subtype")
  case BastUnknownTypeStringSubtype extends RuleId("bast-unknown-type-string-subtype")
  case BastUnknownUniqueIdSubtype extends RuleId("bast-unknown-unique-id-subtype")
  case BastUnknownTypeTag extends RuleId("bast-unknown-type-tag")
  case BastUnknownReferenceTag extends RuleId("bast-unknown-reference-tag")
  case BastUnknownMessageRefTag extends RuleId("bast-unknown-message-ref-tag")
  case BastUnexpectedAlternationMember extends RuleId("bast-unexpected-alternation-member")

  // ---- emitted through `check(...)` ------------------------------------------------------------
  // This family was MISSED when ids were first threaded: `check` builds a Message directly instead
  // of going through `Accumulator.add*`, so 68 diagnostics carried a null rule. The lesson is about
  // the CENSUS, not the code -- enumerating one chokepoint proved nothing about the other, and only
  // running `validate --json` and seeing a null exposed it.
  case StmtIdentifierTooShort extends RuleId("stmt-identifier-too-short")
  case CodeBodyEmpty extends RuleId("stmt-code-body-empty")
  case ForeachElementEmpty extends RuleId("stmt-foreach-element-empty")
  case TypeShouldBeCapitalized extends RuleId("type-should-be-capitalized")
  case TypeRedefinesBuiltin extends RuleId("type-redefines-builtin")
  case TypeRedundantCaseVariant extends RuleId("type-redundant-case-variant")
  case StateNameCollidesWithRecord extends RuleId("state-name-collides-with-record")
  case FunctionNoStatements extends RuleId("func-no-statements")
  case IncludeNoContent extends RuleId("module-include-no-content")
  case IncludeNoSource extends RuleId("module-include-no-source")
  case ImportNoPath extends RuleId("module-import-no-path")
  case ImportExtension extends RuleId("module-import-extension")
  case CorrelationSetsNoFields extends RuleId("proj-correlation-sets-no-fields")
  case CorrelationNameCollision extends RuleId("proj-correlation-name-collision")
  case CorrelationMustYieldCommand extends RuleId("proj-correlation-must-yield-command")
  case ProjectorNoRecordType extends RuleId("proj-no-record-type")
  case ProjectorNotOneHandler extends RuleId("proj-not-exactly-one-handler")
  case SourceHasInlets extends RuleId("stream-source-has-inlets")
  case SourceNoOutlets extends RuleId("stream-source-no-outlets")
  case SinkNoInlets extends RuleId("stream-sink-no-inlets")
  case SinkHasOutlets extends RuleId("stream-sink-has-outlets")
  case FlowNoInlets extends RuleId("stream-flow-no-inlets")
  case FlowNoOutlets extends RuleId("stream-flow-no-outlets")
  case MergeTooFewInlets extends RuleId("stream-merge-too-few-inlets")
  case MergeNoOutlets extends RuleId("stream-merge-no-outlets")
  case SplitNoInlets extends RuleId("stream-split-no-inlets")
  case SplitTooFewOutlets extends RuleId("stream-split-too-few-outlets")
  case RouterTooFewInlets extends RuleId("stream-router-too-few-inlets")
  case RouterTooFewOutlets extends RuleId("stream-router-too-few-outlets")
  case ErrorSinkType extends RuleId("stream-error-sink-type")
  case DomainSinglyNested extends RuleId("domain-singly-nested")
  case DomainNoAuthor extends RuleId("doc-domain-no-author")
  case SagaTooFewSteps extends RuleId("saga-too-few-steps")
  case SagaStepNamesNotDistinct extends RuleId("saga-step-names-not-distinct")
  case SagaStepNeedsRevert extends RuleId("saga-step-needs-revert")
  case ByNamesWrongField extends RuleId("msg-by-names-wrong-field")
  case IdentifierEmpty extends RuleId("name-identifier-empty")
  case ContainerShouldHaveContent extends RuleId("def-container-should-have-content")
  case MetadataEmpty extends RuleId("doc-metadata-empty")
  case NoDescription extends RuleId("doc-no-description")
  case BriefTooShort extends RuleId("doc-brief-too-short")
  case DescriptionDeclaredEmpty extends RuleId("doc-description-declared-but-empty")
  case DescriptionEmpty extends RuleId("doc-description-empty")
  case DescriptionInvalid extends RuleId("doc-description-invalid")
  case TermDefinitionTooShort extends RuleId("doc-term-definition-too-short")
  case OptionNameTooShort extends RuleId("opt-name-too-short")
  case NonPositiveDuration extends RuleId("value-non-positive-duration")
  case OptionWrongArity extends RuleId("opt-wrong-arity")
  case OptionMisplaced extends RuleId("opt-misplaced")
  case OptionUnrecognized extends RuleId("opt-unrecognized")
  case EnumeratorCapitalization extends RuleId("type-enumerator-capitalization")
  case RangeMinTooSmall extends RuleId("type-min-too-small")
  case RangeMaxTooLarge extends RuleId("type-max-too-large")
  case FieldShouldBeLowercase extends RuleId("field-should-be-lowercase")
  case MessageFieldShouldBeLowercase extends RuleId("field-message-should-be-lowercase")
  case NegativeMinCardinality extends RuleId("type-negative-min-cardinality")
  case NegativeMaxCardinality extends RuleId("type-negative-max-cardinality")
  case MinExceedsMax extends RuleId("type-min-exceeds-max")
  case IdNamesNonProcessor extends RuleId("type-id-names-non-processor")
  case WholePartPositive extends RuleId("type-whole-part-positive")
  case FractionPartPositive extends RuleId("type-fraction-part-positive")
  // Supplied BY THE CALLER of `checkNonEmptyValue`/`checkNonEmpty`, whose own rule this is. The
  // fallback for a caller that has no more specific rule to name.
  case EmptyContent extends RuleId("def-empty-content")
  case EntityNoStates extends RuleId("entity-no-states")
  case FigmaUnavailable extends RuleId("doc-figma-unavailable")
  case VerbModalityMismatch extends RuleId("app-verb-modality-mismatch")
  case MenuHasNoChoice extends RuleId("app-menu-has-no-choice")
  case GroupUnreachable extends RuleId("app-group-unreachable")
  case CrossContextReference extends RuleId("ref-crosses-context-boundary")
  case ClauseNoStatements extends RuleId("handler-clause-no-statements")
  case OutputShowsWrongKind extends RuleId("app-output-shows-wrong-kind")
  case InputSendsWrongKind extends RuleId("app-input-sends-wrong-kind")
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
  val retired: Set[String] = Set(
    // Published in 2.0.0-rc.25 on `checkAssignable`'s wrong-entity arm, which now answers to
    // `stmt-id-entity-mismatch` -- the name that says what it means. Retired rather than reused:
    // a consumer suppressing it, or keying a migration on it, must not have it silently come back
    // attached to a different rule.
    "stmt-id-type-mismatch"
  )

  /** The closed set of subject prefixes -- the kind of thing a rule is ABOUT.
    *
    * Closed on purpose. A prefix a reader can guess is worth more than one that is precisely
    * accurate, so a new rule joins an existing bucket unless it genuinely belongs to none; adding a
    * subject is a deliberate act, reviewed like any other API change. `RuleIdTest` fails on a code
    * whose prefix is not here, which is what stops the vocabulary drifting one rule at a time.
    */
  val subjects: Set[String] = Set(
    "adaptor", "app", "bast", "context",
    // `def` is the generic bucket: a rule that applies to ANY definition rather than to one kind
    // of thing -- "this container should have content", "this is empty". Deliberately last resort;
    // a rule that can name its subject should.
    "def", "doc", "domain", "entity", "epic", "field", "func",
    "handler",
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
  /** Rules whose fix is a CONSTANT replacement, as code -> text.
    *
    * A computed fix cannot appear here -- its replacement depends on the matched text, which a
    * `Map[String, String]` has no way to express. Omitting it is the honest answer: a consumer
    * reading this map gets replacements it can apply blindly, and `validate --fix` (which has the
    * matched span) applies the computed ones itself.
    */
  lazy val mechanicalReplacements: Map[String, String] =
    values.flatMap(r => r.mechanicalFix.flatMap(_.constantText).map(r.code -> _)).toMap

  /** Every rule with any mechanical fix, constant or computed. What `validate --fix` acts on. */
  lazy val fixable: Map[String, Fix] =
    values.flatMap(r => r.mechanicalFix.map(r.code -> _)).toMap

  /** The subject prefix -- the part before the first `-`. Lets a consumer select a whole family. */
  extension (r: RuleId) def subject: String = r.code.takeWhile(_ != '-')
end RuleId
