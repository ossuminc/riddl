/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Messages, toSeq}
import com.ossuminc.riddl.utils.pc
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.passes.diagrams.{
  DiagramsPass,
  DiagramsPassOutput,
  DataFlowDiagramData,
  DomainDiagramData
}
import com.ossuminc.riddl.passes.resolve.{ReferenceMap, ResolutionPass, Usages}
import com.ossuminc.riddl.passes.stats.{StatsOutput, StatsPass}
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.ossuminc.riddl.passes.validate.{HandlerCompleteness, ValidationOutput, ValidationPass}

import java.time.Instant
import java.util.UUID

/** Unique token identifying a cached AnalysisResult.
  *
  * Tokens are opaque handles used by MCP tools to reference previously
  * analyzed models without re-parsing.
  */
opaque type AnalysisToken = String

object AnalysisToken:
  /** Generate a new unique token */
  def generate(): AnalysisToken = UUID.randomUUID().toString

  /** Create token from string (for deserialization) */
  def fromString(s: String): AnalysisToken = s

  extension (token: AnalysisToken) def value: String = token
end AnalysisToken

/** Metadata about the analysis */
case class AnalysisMetadata(
  /** When the analysis was performed */
  analyzedAt: Instant,

  /** Name of the root domain (primary identifier) */
  rootDomainName: Option[String],

  /** Source file or URL that was analyzed */
  sourceLocation: Option[String],

  /** Hash of the source for cache invalidation */
  sourceHash: Option[String],

  /** RIDDL version used for parsing */
  riddlVersion: Option[String]
)

object AnalysisMetadata:
  def apply(root: Root): AnalysisMetadata =
    val rootDomainName = root.domains.headOption.map(_.id.value)
    AnalysisMetadata(
      analyzedAt = Instant.now(),
      rootDomainName = rootDomainName,
      sourceLocation = None,
      sourceHash = None,
      riddlVersion = None
    )

  def fromBranch(branch: Branch[?]): AnalysisMetadata =
    branch match
      case root: Root => apply(root)
      case other =>
        AnalysisMetadata(
          analyzedAt = Instant.now(),
          rootDomainName = Some(other.id.value),
          sourceLocation = None,
          sourceHash = None,
          riddlVersion = None
        )
end AnalysisMetadata

/** Pre-computed information about a DDD message type
  * (Command, Event, Query, Result).
  */
case class MessageTypeInfo(
  typ: Type,
  kind: String,
  fields: Seq[Field],
  users: Seq[Definition],
  qualifiedPath: String,
  brief: Option[String]
)

/** Pre-computed glossary entry for a named definition. */
case class AnalysisGlossaryEntry(
  definition: Definition,
  name: String,
  kind: String,
  qualifiedPath: String,
  brief: Option[String]
)

/** Pre-computed item for a definition that needs more work. */
case class IncompleteItem(
  definition: Definition,
  name: String,
  kind: String,
  qualifiedPath: String,
  authors: Seq[String]
)

/** Consolidated analysis data from multiple RIDDL passes.
  *
  * AnalysisResult aggregates outputs from:
  *   - SymbolsPass: Symbol table, hierarchy, qualified names
  *   - ResolutionPass: Reference resolution, dependency tracking
  *   - ValidationPass: Handler completeness categorization
  *   - StatsPass: Metrics, completeness, complexity
  *   - DiagramsPass: Context relationships, use cases, data flows, domains
  *   - MessageFlowPass: Message producer/consumer graph
  *   - EntityLifecyclePass: Entity state machines
  *   - DependencyAnalysisPass: Cross-context/entity/type dependencies
  *
  * All data is collected once during analysis and cached. Generators
  * only need ValidationPass + AnalysisPass — no additional tree
  * traversals.
  */
case class AnalysisResult(
  token: AnalysisToken,
  metadata: AnalysisMetadata,
  symbols: SymbolsOutput,
  referenceMap: ReferenceMap,
  usages: Usages,
  stats: StatsOutput,
  diagrams: DiagramsPassOutput,
  handlerCompleteness: Seq[HandlerCompleteness],
  messageFlow: MessageFlowOutput,
  entityLifecycles: Map[Entity, EntityLifecycle],
  dependencies: DependencyOutput,
  messages: Messages.Messages
):
  // ============================================================
  // Convenience accessors for common queries
  // ============================================================

  /** Check if analysis completed without errors */
  def isValid: Boolean = !messages.hasErrors

  /** Get all domains in the model */
  def domains: Seq[Domain] =
    symbols.parentage.keys.collect { case d: Domain => d }.toSeq

  /** Get all contexts in the model */
  def contexts: Seq[Context] =
    symbols.parentage.keys.collect { case c: Context => c }.toSeq

  /** Get all entities in the model */
  def entities: Seq[Entity] =
    symbols.parentage.keys.collect { case e: Entity => e }.toSeq

  /** Get all sagas in the model */
  def sagas: Seq[Saga] =
    symbols.parentage.keys.collect { case s: Saga => s }.toSeq

  /** Get all epics in the model */
  def epics: Seq[Epic] =
    symbols.parentage.keys.collect { case e: Epic => e }.toSeq

  /** Get all repositories in the model */
  def repositories: Seq[Repository] =
    symbols.parentage.keys.collect { case r: Repository => r }.toSeq

  /** Get all streamlets in the model */
  def streamlets: Seq[Streamlet] =
    symbols.parentage.keys.collect { case s: Streamlet => s }.toSeq

  /** Get all projectors in the model */
  def projectors: Seq[Projector] =
    symbols.parentage.keys.collect { case p: Projector => p }.toSeq

  /** Get all adaptors in the model */
  def adaptors: Seq[Adaptor] =
    symbols.parentage.keys.collect { case a: Adaptor => a }.toSeq

  /** Get all functions in the model */
  def functions: Seq[Function] =
    symbols.parentage.keys.collect { case f: Function => f }.toSeq

  /** Get all types in the model */
  def types: Seq[Type] =
    symbols.parentage.keys.collect { case t: Type => t }.toSeq

  /** Get the parent chain for a definition */
  def parentsOf(definition: Definition): Seq[Branch[?]] =
    symbols.parentsOf(definition)

  /** Get the containing context for a definition, if any */
  def contextOf(definition: Definition): Option[Context] =
    symbols.contextOf(definition)

  /** Get what definitions this one uses (forward dependencies) */
  def getUses(definition: Definition): Seq[Definition] =
    usages.getUses(definition)

  /** Get what definitions use this one (reverse dependencies) */
  def getUsers(definition: Definition): Seq[Definition] =
    usages.getUsers(definition)

  /** Compute the dotted qualified name for a definition */
  def qualifiedNameOf(definition: Definition): String =
    val parents = parentsOf(definition).collect { case d: Definition => d }
    (parents.reverse.map(_.id.value) :+ definition.id.value).mkString(".")

  // ============================================================
  // Statistics helpers
  // ============================================================

  /** Overall model completeness percentage */
  def completeness: Double =
    stats.categories.get("All").map(_.completeness).getOrElse(0.0)

  /** Overall model complexity score */
  def complexity: Double =
    stats.categories.get("All").map(_.complexity).getOrElse(0.0)

  /** Count of definitions by kind */
  def countByKind(kind: String): Long =
    stats.categories.get(kind).map(_.count).getOrElse(0L)

  /** Maximum nesting depth in the model */
  def maxDepth: Int = stats.maximum_depth

  // ============================================================
  // Diagram data helpers
  // ============================================================

  /** Get context diagram data for a specific context */
  def contextDiagramFor(context: Context) =
    diagrams.contextDiagrams.get(context)

  /** Get use case diagram data for a specific use case */
  def useCaseDiagramFor(useCase: UseCase) =
    diagrams.useCaseDiagrams.get(useCase)

  /** All contexts that have diagram data */
  def contextsWithDiagrams: Iterable[Context] =
    diagrams.contextDiagrams.keys

  /** Get data flow diagram data for a specific context */
  def dataFlowDiagramFor(context: Context): Option[DataFlowDiagramData] =
    diagrams.dataFlowDiagrams.get(context)

  /** Get domain diagram data for a specific domain */
  def domainDiagramFor(domain: Domain): Option[DomainDiagramData] =
    diagrams.domainDiagrams.get(domain)

  /** All domain diagram data */
  def allDomainDiagrams: Map[Domain, DomainDiagramData] =
    diagrams.domainDiagrams

  // ============================================================
  // Handler completeness helpers
  // ============================================================

  /** Get handler completeness data for a specific handler */
  def handlerCompletenessFor(handler: Handler): Option[HandlerCompleteness] =
    handlerCompleteness.find(_.handler == handler)

  // ============================================================
  // Entity lifecycle helpers
  // ============================================================

  /** Get lifecycle data for a specific entity */
  def lifecycleFor(entity: Entity): Option[EntityLifecycle] =
    entityLifecycles.get(entity)

  // ============================================================
  // Context dependency graph (A6)
  // ============================================================

  /** Pre-computed inter-context dependency edges. */
  lazy val contextDependencies: Map[Context, scala.collection.immutable.Set[Context]] =
    contexts.map { ctx =>
      ctx -> getUses(ctx).collect { case c: Context => c }.toSet
    }.toMap

  /** Reverse dependency map: who depends on this context? */
  lazy val contextDependents: Map[Context, scala.collection.immutable.Set[Context]] =
    contexts.map { ctx =>
      ctx -> getUsers(ctx).collect { case c: Context => c }.toSet
    }.toMap

  /** All inter-context edges as (from, to) pairs */
  lazy val contextEdges: Seq[(Context, Context)] =
    contextDependencies.toSeq.flatMap { case (from, tos) =>
      tos.toSeq.map(to => (from, to))
    }

  // ============================================================
  // Pre-computed collections (A3, A4, A5)
  // ============================================================

  /** Pre-computed DDD message types grouped by kind (A3). */
  lazy val messageTypes: Map[String, Seq[MessageTypeInfo]] =
    types
      .flatMap { t =>
        t.typEx match
          case agg: AggregateUseCaseTypeExpression =>
            Some(
              MessageTypeInfo(
                typ = t,
                kind = agg.usecase.toString,
                fields = agg.fields,
                users = getUsers(t),
                qualifiedPath = qualifiedNameOf(t),
                brief = t.brief.map(_.brief.s)
              )
            )
          case _ => None
      }
      .groupBy(_.kind)

  /** All message types as a flat sorted sequence. */
  def messageTypesFlat: Seq[MessageTypeInfo] =
    messageTypes.values.flatten.toSeq.sortBy(_.typ.id.value)

  /** Pre-computed glossary entries for all named definitions (A4). */
  lazy val glossaryEntries: Seq[AnalysisGlossaryEntry] =
    symbols.parentage.keys
      .collect { case d: Definition if !d.isAnonymous => d }
      .map { defn =>
        AnalysisGlossaryEntry(
          definition = defn,
          name = defn.id.value,
          kind = defn.kind,
          qualifiedPath = qualifiedNameOf(defn),
          brief = defn.brief.map(_.brief.s)
        )
      }
      .toSeq
      .sortBy(_.name.toLowerCase)

  /** Pre-computed incomplete definitions needing more work (A5). */
  lazy val incompleteDefinitions: Seq[IncompleteItem] =
    symbols.parentage.keys
      .collect {
        case d: Definition
            if d.isEmpty
              && !d.isAnonymous
              && !d.isInstanceOf[Root]
              && !d.isInstanceOf[Interaction]
              && !d.isInstanceOf[Include[?]] =>
          d
      }
      .map { defn =>
        IncompleteItem(
          definition = defn,
          name = defn.id.value,
          kind = defn.kind,
          qualifiedPath = qualifiedNameOf(defn),
          authors = resolveAuthors(defn)
        )
      }
      .toSeq
      .sortBy(_.name.toLowerCase)

  private def resolveAuthors(defn: Definition): Seq[String] =
    defn.metadata.toSeq
      .collect { case a: AuthorRef => a }
      .flatMap { ref =>
        parentsOf(defn)
          .collectFirst { case b: Branch[?] => b }
          .flatMap(parent => referenceMap.definitionOf[Author](ref.pathId, parent))
          .map(a => s"${a.name.s} <${a.email.s}>")
      }

end AnalysisResult

object AnalysisResult:
  /** Create an AnalysisResult from a PassesResult.
    *
    * Extracts outputs from the standard passes. Throws if required
    * passes haven't been run.
    */
  def fromPassesResult(passesResult: PassesResult): AnalysisResult =
    val symbols = passesResult
      .outputOf[SymbolsOutput](SymbolsPass.name)
      .getOrElse(throw IllegalStateException("SymbolsPass output required"))

    val resolution = passesResult
      .outputOf[
        com.ossuminc.riddl.passes.resolve.ResolutionOutput
      ](ResolutionPass.name)
      .getOrElse(
        throw IllegalStateException("ResolutionPass output required")
      )

    val stats = passesResult
      .outputOf[StatsOutput](StatsPass.name)
      .getOrElse(throw IllegalStateException("StatsPass output required"))

    val diagrams = passesResult
      .outputOf[DiagramsPassOutput](DiagramsPass.name)
      .getOrElse(
        throw IllegalStateException("DiagramsPass output required")
      )

    val validation = passesResult
      .outputOf[ValidationOutput](ValidationPass.name)
      .getOrElse(ValidationOutput())

    val messageFlowOutput = passesResult
      .outputOf[MessageFlowOutput](MessageFlowPass.name)
      .getOrElse(MessageFlowOutput())

    val lifecycleOutput = passesResult
      .outputOf[EntityLifecycleOutput](EntityLifecyclePass.name)
      .getOrElse(EntityLifecycleOutput())

    val dependencyOutput = passesResult
      .outputOf[DependencyOutput](DependencyAnalysisPass.name)
      .getOrElse(DependencyOutput())

    AnalysisResult(
      token = AnalysisToken.generate(),
      metadata = AnalysisMetadata.fromBranch(passesResult.root),
      symbols = symbols,
      referenceMap = resolution.refMap,
      usages = resolution.usage,
      stats = stats,
      diagrams = diagrams,
      handlerCompleteness = validation.handlerCompleteness,
      messageFlow = messageFlowOutput,
      entityLifecycles = lifecycleOutput.lifecycles,
      dependencies = dependencyOutput,
      messages = passesResult.messages
    )
  end fromPassesResult
end AnalysisResult
