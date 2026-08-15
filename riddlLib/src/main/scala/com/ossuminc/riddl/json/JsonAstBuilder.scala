/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.json

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Contents, toSeq, toContents}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.Messages.{Message, Messages}
import com.ossuminc.riddl.utils.{PlatformContext, URL}

import scala.collection.mutable

/** Pure, Native-safe construction of a RIDDL [[AST.Root]] from the JSON wire model ([[JsonModel]]).
  * No I/O. References are emitted as `PathIdentifier`s and left for
  * `ResolutionPass`/`ValidationPass` to resolve — the builder only guarantees structural
  * correctness and supplies RIDDL's required defaults.
  *
  * Builder-level errors (the few things the AST cannot express or default — a missing `Id.entity`,
  * an empty `Enum`/`Pattern`, an unknown `kind`) are collected and returned as `Left(Messages)`;
  * everything else becomes a structurally-valid `Root`.
  */
object JsonAstBuilder:

  import JsonModel.*

  /** The location every node built for the current entry gets.
    *
    * `curAt` is EMPTY, so every node used to be location-free. This resolves to the enclosing
    * definition's span instead: coarse for a definition's inner values (a field takes its type's
    * span from the definition), but real, and — crucially — DISTINCT between two definitions that
    * are otherwise identical.
    */
  private def curAt(using ctx: Ctx): At = ctx.current

  /** The Root's own span: the whole source when there is one, else empty. */
  private def rootLoc(ctx: Ctx): At =
    if ctx.source.data.isEmpty then At(ctx.source, 0, 0)
    else At(ctx.source, 0, ctx.source.data.length)

  /** The source every `$at` in the document indexes.
    *
    * `document` basis: the offsets index the JSON itself, and the reader HAS the JSON, so this is a
    * real input — line, column and the quoted line in a diagnostic are all exact.
    *
    * `origin` basis: the offsets index a RIDDL file this builder must not read (it is no-I/O, which
    * is what keeps it usable on Native). The input therefore carries the origin NAME with no data:
    * offsets and file name are exact, line/col are not recoverable here, and anyone who wants them
    * has the `.riddl` file to resolve against. This is the same trade BAST makes.
    *
    * No `locations` at all means a location-free document, and every node keeps `At.empty`.
    */
  private def sourceFor(
    locations: Option[LocationsDto],
    jsonText: String
  )(using PlatformContext): RiddlParserInput =
    locations match
      case None => RiddlParserInput.empty
      case Some(loc) =>
        val text = if loc.basis == LocationBasis.Document then jsonText else ""
        // `RiddlParserInput.origin` derives from the URL's PATH, not from `purpose`, so the origin
        // has to be carried there or every message reports "empty".
        RiddlParserInput(text, URL(URL.fileScheme, "", "", loc.origin))

  /** Build a `Root` from the wire model, or the accumulated builder errors.
    *
    * Non-fatal messages are DISCARDED here so the long-standing signature and behaviour are
    * untouched; use [[buildWithMessages]] to see them.
    */
  def build(dto: RootDto)(using PlatformContext): Either[Messages, Root] =
    buildWithMessages(dto)._1

  /** As [[build]], told the JSON text so a `document`-basis document can resolve its own offsets.
    */
  def build(dto: RootDto, jsonText: String)(using PlatformContext): Either[Messages, Root] =
    buildWithMessages(dto, jsonText)._1

  /** As [[build]], plus the non-fatal messages the build produced — currently one `Deprecation` per
    * container kind still using the per-kind buckets rather than the ordered `contents` array.
    *
    * Additive rather than a change to [[build]]: `build` returns a `Left` exactly when there are
    * errors, so a deprecation has nowhere to go in that shape without turning a good document into
    * a failure.
    */
  def buildWithMessages(
    dto: RootDto,
    jsonText: String = ""
  )(using PlatformContext): (Either[Messages, Root], Messages) =
    given ctx: Ctx = new Ctx
    ctx.source = sourceFor(dto.locations, jsonText)
    val domains = dto.domains.map(buildDomain)
    val modules = dto.modules.map(buildModule)
    val version = dto.version.map(buildVersion).toSeq
    val copyright = dto.copyright.map(buildCopyright).toSeq
    val authors = dto.authors.map(buildAuthor)
    val root =
      Root(
        rootLoc(ctx),
        childrenOrBuckets[RootContents](
          dto.contents,
          "Root",
          Legal.rootW,
          contentsOf[RootContents](
            domains,
            modules,
            version,
            copyright,
            authors,
            comments(dto.comments)
          )
        )
      )
    val deprecations: Messages =
      if ctx.deprecations.isEmpty then Nil
      else
        List(
          Message(
            At.empty,
            "This JSON uses the deprecated per-kind content arrays (`domains`, `types`, " +
              "`handlers`, …) on: " + ctx.deprecations.mkString(", ") +
              ". They cannot express the order of definitions within their parent, so a model " +
              "read from them does not reproduce its source exactly. Use the ordered `contents` " +
              "array instead; `root2Json` writes it.",
            Messages.Deprecation
          )
        )
    val result = if ctx.errors.isEmpty then Right(root) else Left(ctx.errors.toList)
    (result, deprecations)
  end buildWithMessages

  private def buildModule(m: ModuleDto)(using Ctx): Module =
    val authors = m.authors.map(buildAuthor)
    val domains = m.domains.map(buildDomain)
    val types = m.types.map(buildType)
    val commands = m.commands.map(buildMessage(_, AggregateUseCase.CommandCase))
    val events = m.events.map(buildMessage(_, AggregateUseCase.EventCase))
    val queries = m.queries.map(buildMessage(_, AggregateUseCase.QueryCase))
    val results = m.results.map(buildMessage(_, AggregateUseCase.ResultCase))
    val constants = m.constants.map(buildConstant)
    val invariants = m.invariants.map(buildInvariant)
    val users = m.users.map(buildUser)
    val contexts = m.contexts.map(buildContext)
    val entities = m.entities.map(buildEntity)
    val adaptors = m.adaptors.map(buildAdaptor)
    val functions = m.functions.map(buildFunction)
    val projectors = m.projectors.map(buildProjector)
    val repositories = m.repositories.map(buildRepository)
    val streamlets = m.streamlets.map(buildStreamlet)
    val sagas = m.sagas.map(buildSaga)
    val epics = m.epics.map(buildEpic)
    val connectors = m.connectors.map(buildConnector)
    val relationships = m.relationships.map(buildRelationship)
    val modules = m.modules.map(buildModule)
    val version = m.version.map(buildVersion).toSeq
    val copyright = m.copyright.map(buildCopyright).toSeq
    Module(
      curAt,
      ident(m.name),
      childrenOrBuckets[ModuleContents](
        m.contents,
        "Module",
        Legal.moduleW,
        contentsOf[ModuleContents](
          authors,
          domains,
          types,
          commands,
          events,
          queries,
          results,
          constants,
          invariants,
          users,
          contexts,
          entities,
          adaptors,
          functions,
          projectors,
          repositories,
          streamlets,
          sagas,
          epics,
          connectors,
          relationships,
          modules,
          version,
          copyright,
          comments(m.comments)
        )
      ),
      meta(m.brief, m.metadata)
    )

  /** Mutable error sink threaded through construction, and the platform capability that one
    * metadata kind needs. A `described at <url>` description holds a loader so that its lines can
    * be fetched on demand; rebuilding the node therefore needs a `PlatformContext`, even though
    * nothing here loads anything — `URLDescription.lines` is lazy, so the builder stays no-I/O.
    * Carrying it on `Ctx`, which is already threaded through every `build*`, keeps that requirement
    * from spreading across two dozen signatures.
    */
  private final class Ctx(using val pc: PlatformContext):
    val errors: mutable.ListBuffer[Message] = mutable.ListBuffer.empty
    def err(message: String): Unit = errors += Messages.error(message)

    /** Non-fatal messages, kept apart from `errors` because `build` returns a `Left` whenever
      * `errors` is non-empty — putting a deprecation there would turn a perfectly good document
      * into a failure. Reported once per container KIND rather than per occurrence, so a large
      * bucketed document says "Domain, Context" instead of five hundred identical lines.
      */
    val deprecations: mutable.LinkedHashSet[String] = mutable.LinkedHashSet.empty
    def deprecated(container: String): Unit = deprecations += container

    /** The source every `$at` in this document indexes, and the location of the entry currently
      * being built.
      *
      * `current` is set by [[childrenOf]] per entry, so a definition's inner values — its fields,
      * statements and type expressions — inherit the enclosing definition's span instead of being
      * empty. That is deliberately coarse: the document carries a location per DEFINITION, which is
      * what a diagnostic points at, and it is what makes two same-named definitions DISTINCT again.
      */
    var source: RiddlParserInput = RiddlParserInput.empty
    var current: At = At.empty

    /** Run `body` with `loc` as the location every `curAt` inside it resolves to. */
    def at[T](loc: At)(body: => T): T =
      val saved = current
      current = loc
      try body
      finally current = saved
    end at

    /** Build an `At` from a `[offset, endOffset]` pair against the document's declared source. */
    def locOf(pair: Option[(Int, Int)]): At = pair match
      case Some((start, end)) if end >= start => At(source, start, end)
      case _                                  => current

  /** Collect heterogeneous child groups (each a subtype of `T`) into a typed `Contents[T]`. `Seq[?
    * <: T]` keeps each call-site group correctly typed.
    *
    * This is the DEPRECATED path: concatenating per-kind buckets in a fixed sequence is exactly
    * what made source order unrecoverable. It survives only to read documents written against the
    * older schema — see [[childrenOf]], which is the canonical one.
    */
  private def contentsOf[T <: RiddlValue](groups: Seq[? <: T]*): Contents[T] =
    val buf = mutable.ArrayBuffer.empty[T]
    groups.foreach(g => buf ++= g)
    Contents[T](buf.toSeq*)

  // ---------------------------------------------------------------------------
  // Ordered contents (the canonical form)
  // ---------------------------------------------------------------------------

  /** Which content kinds each container admits, mirroring the AST's own unions in `AST.scala`.
    *
    * A flat array cannot express this the way per-kind buckets did — an `EntityDto` simply had no
    * `sagas` field — so the constraint moves here rather than being dropped on the floor for
    * `ValidationPass` to trip over later with a worse message.
    *
    * Union members with no [[JsonModel.ContentDto]] of their own (`Statement`, `ShownBy`,
    * `RepositoryRef`, `Interaction`, `Include`, `BASTImport`) are absent by construction: they are
    * carried by a dedicated DTO field, not by `contents`.
    */
  /** `AST.Set` shadows `scala.Set` in this file's wildcard import of the AST, so the set of legal
    * kinds is spelled through an alias rather than the plain name.
    */
  private type Kinds = scala.collection.immutable.Set[String]
  private val Kinds = scala.collection.immutable.Set

  private object Legal:
    import JsonModel.ContentKind as K

    /** `OccursInVitalDefinition` — plus the four message use cases, since a message IS a Type. */
    private val vital: Kinds = Kinds(K.Type, K.Comment) ++ K.messageKinds

    /** `OccursInProcessor`. */
    private val processor: Kinds = vital ++ Kinds(
      K.Constant,
      K.Invariant,
      K.Function,
      K.Handler,
      K.Streamlet,
      K.Connector,
      K.Relationship,
      K.Inlet,
      K.Outlet,
      K.Version,
      K.Copyright
    )

    val nebulaKinds: Kinds = Kinds(
      K.Adaptor,
      K.Author,
      K.Connector,
      K.Constant,
      K.Context,
      K.Domain,
      K.Entity,
      K.Epic,
      K.Function,
      K.Invariant,
      K.Module,
      K.Projector,
      K.Relationship,
      K.Repository,
      K.Saga,
      K.Streamlet,
      K.Type,
      K.User,
      K.Version,
      K.Copyright
    ) ++ K.messageKinds

    val root: Kinds = Kinds(K.Domain, K.Author, K.Comment, K.Version, K.Copyright, K.Module)
    val module: Kinds = nebulaKinds + K.Comment
    val domain: Kinds = vital ++ Kinds(
      K.Author,
      K.Context,
      K.Domain,
      K.User,
      K.Epic,
      K.Saga,
      K.Repository,
      K.Connector,
      K.Version,
      K.Copyright
    )
    val context: Kinds =
      processor ++ Kinds(K.Entity, K.Adaptor, K.Group, K.Saga, K.Projector, K.Repository)
    val entity: Kinds = processor + K.State
    val state: Kinds = Kinds(K.Handler, K.Invariant, K.Comment)
    // A70: a correlation holds its folds in one handler; it has no invariants, because a projector
    // cannot refuse an event and an invariant-as-guard would have nothing to mean.
    val correlation: Kinds = Kinds(K.Handler, K.Comment)
    val handler: Kinds = Kinds(K.OnClause, K.Comment)
    val adaptor: Kinds = processor
    val streamlet: Kinds = processor
    val projector: Kinds = processor + K.Correlation
    val repository: Kinds = processor + K.Schema
    /** A saga's and a function's `requires`/`returns` are ordinary contents, so they are legal
      * children here rather than fields lifted out of the body.
      */
    private val clauses: Kinds = Kinds(K.Requires, K.Returns)
    val saga: Kinds = vital ++ clauses + K.SagaStep
    val epic: Kinds = vital + K.UseCase
    val useCase: Kinds = Kinds(K.Comment, K.Interaction)
    val group: Kinds = Kinds(K.Group, K.ContainedGroup, K.Input, K.Output, K.Comment)
    val function: Kinds = vital ++ clauses + K.Function

    /** `Include` and `BASTImport` are members of most of the unions above (`RootContents`,
      * `DomainContents`, `ContextContents`, …). `FunctionContents` is the exception — a function is
      * self-contained and supports no includes — and a State, Handler, UseCase and Group hold no
      * includes either.
      */
    private val wrappers: Kinds = Kinds(K.Include, K.BASTImport)
    val rootW: Kinds = root ++ wrappers
    val moduleW: Kinds = module ++ wrappers
    val domainW: Kinds = domain ++ wrappers
    val contextW: Kinds = context ++ wrappers
    val entityW: Kinds = entity + K.Include
    val adaptorW: Kinds = adaptor + K.Include
    val streamletW: Kinds = streamlet + K.Include
    val projectorW: Kinds = projector + K.Include
    val repositoryW: Kinds = repository + K.Include
    val sagaW: Kinds = saga + K.Include
    val epicW: Kinds = epic + K.Include
  end Legal

  /** The kind tag a [[JsonModel.ContentDto]] travels under — the read-side mirror of the emitter's
    * tagging, used to check a child against its container's [[Legal]] set.
    */
  private def kindOf(c: ContentDto): String =
    import JsonModel.ContentKind as K
    c match
      case _: DomainDto             => K.Domain
      case _: ModuleDto             => K.Module
      case _: ContextDto            => K.Context
      case _: EntityDto             => K.Entity
      case _: TypeDefDto            => K.Type
      case _: StateDto              => K.State
      case _: HandlerDto            => K.Handler
      case _: CorrelationDto        => K.Correlation
      case _: OnClauseDto           => K.OnClause
      case _: FunctionDto           => K.Function
      case _: AdaptorDto            => K.Adaptor
      case _: StreamletDto          => K.Streamlet
      case _: ProjectorDto          => K.Projector
      case _: RepositoryDto         => K.Repository
      case _: SchemaDto             => K.Schema
      case _: ConnectorDto          => K.Connector
      case _: RelationshipDto       => K.Relationship
      case _: SagaDto               => K.Saga
      case _: SagaStepDto           => K.SagaStep
      case _: EpicDto               => K.Epic
      case _: UseCaseDto            => K.UseCase
      case _: GroupDto              => K.Group
      case _: ContainedGroupDto     => K.ContainedGroup
      case _: InputDto              => K.Input
      case _: OutputDto             => K.Output
      case _: AuthorDto             => K.Author
      case _: UserDto               => K.User
      case _: InvariantDto          => K.Invariant
      case _: ConstantDto           => K.Constant
      case _: CommentDto            => K.Comment
      case _: VersionDto            => K.Version
      case _: CopyrightDto          => K.Copyright
      case _: FieldDto              => K.Field
      case _: MethodDto             => K.Method
      case _: TermDto               => K.Term
      case _: RequiresDto           => K.Requires
      case _: ReturnsDto            => K.Returns
      case _: InteractionContentDto => K.Interaction
      case _: IncludeContentDto     => K.Include
      case _: BASTImportContentDto  => K.BASTImport
      case m: MessageDto            => m.usecase.getOrElse(K.Command)
      case p: PortletDto            => p.direction.getOrElse(K.Inlet)

  /** One ordered child, as its AST node. */
  private def buildContent(c: ContentDto, container: String, legal: Kinds)(using
    ctx: Ctx
  ): RiddlValue =
    import JsonModel.ContentKind as K
    c match
      case d: DomainDto         => buildDomain(d)
      case d: ModuleDto         => buildModule(d)
      case d: ContextDto        => buildContext(d)
      case d: EntityDto         => buildEntity(d)
      case d: TypeDefDto        => buildType(d)
      case d: StateDto          => buildState(d)
      case d: CorrelationDto    => buildCorrelation(d)
      case d: HandlerDto        => buildHandler(d)
      case d: OnClauseDto       => buildOnClause(d)
      case d: FunctionDto       => buildFunction(d)
      case d: AdaptorDto        => buildAdaptor(d)
      case d: StreamletDto      => buildStreamlet(d)
      case d: ProjectorDto      => buildProjector(d)
      case d: RepositoryDto     => buildRepository(d)
      case d: SchemaDto         => buildSchema(d)
      case d: ConnectorDto      => buildConnector(d)
      case d: RelationshipDto   => buildRelationship(d)
      case d: SagaDto           => buildSaga(d)
      case d: SagaStepDto       => buildSagaStep(d)
      case d: EpicDto           => buildEpic(d)
      case d: UseCaseDto        => buildUseCase(d)
      case d: GroupDto          => buildGroup(d)
      case d: ContainedGroupDto => buildContainedGroup(d)
      case d: InputDto          => buildInput(d)
      case d: OutputDto         => buildOutput(d)
      case d: AuthorDto         => buildAuthor(d)
      case d: UserDto           => buildUser(d)
      case d: InvariantDto      => buildInvariant(d)
      case d: ConstantDto       => buildConstant(d)
      case d: CommentDto        => comments(Seq(d)).head
      case d: VersionDto        => buildVersion(d)
      case d: CopyrightDto      => buildCopyright(d)
      case d: FieldDto          => buildField(d)
      case d: MethodDto         => buildMethod(d)
      case d: TermDto =>
        Term(curAt, ident(d.name), d.definition.map(LiteralString(curAt, _)))
      // `argOf` returns None only for an arg that is neither a ref nor a field list; a clause must
      // have a value, so an empty aggregation is the honest fallback rather than dropping it.
      case d: RequiresDto =>
        Requires(curAt, argOf(Some(d.arg)).getOrElse(Aggregation(curAt)))
      case d: ReturnsDto =>
        Returns(curAt, argOf(Some(d.arg)).getOrElse(Aggregation(curAt)))
      case d: InteractionContentDto => buildInteraction(d.interaction)
      // A wrapper holds whatever its PARENT holds, so its nested children are checked against the
      // same legal set. Its contents are already in the document, which is what keeps the builder
      // free of I/O and so usable on Native.
      case d: IncludeContentDto =>
        import ctx.pc
        Include[RiddlValue](curAt, URL(d.origin), childrenOf(d.contents, container, legal))
      case d: BASTImportContentDto =>
        BASTImport(
          curAt,
          LiteralString(curAt, d.path),
          d.importKind,
          d.selector.map(ident),
          d.alias.map(ident),
          childrenOf[NebulaContents](d.contents, "BASTImport", Legal.nebulaKinds)
        )
      case d: MessageDto =>
        buildMessage(d, messageUseCase(d.usecase.getOrElse(K.Command)))
      case d: PortletDto =>
        if d.direction.contains(K.Outlet) then buildOutlet(d) else buildInlet(d)

  private def messageUseCase(kind: String): AggregateUseCase =
    import JsonModel.ContentKind as K
    kind match
      case K.Event  => AggregateUseCase.EventCase
      case K.Query  => AggregateUseCase.QueryCase
      case K.Result => AggregateUseCase.ResultCase
      case _        => AggregateUseCase.CommandCase

  /** Rebuild a container's children from the ordered `contents` array, IN ORDER.
    *
    * The cast is discharged by the [[Legal]] check immediately above it: a kind in the container's
    * legal set is by construction a member of that container's AST union, and the unions are erased
    * at runtime anyway (`Contents[?]` is an `ArrayBuffer`), so no test could be written for `T`
    * directly.
    */
  private def childrenOf[T <: RiddlValue](
    contents: Seq[ContentEntry],
    container: String,
    legal: Kinds
  )(using ctx: Ctx): Contents[T] =
    val built = contents.flatMap { entry =>
      val kind = kindOf(entry.content)
      if legal.contains(kind) then
        // Everything built for this entry — the definition and its inner values — resolves `curAt`
        // to the entry's own location.
        ctx.at(ctx.locOf(entry.at)) {
          Some(buildContent(entry.content, container, legal).asInstanceOf[T])
        }
      else
        ctx.err(s"A $container may not contain a '$kind'")
        None
    }
    Contents[T](built*)
  end childrenOf

  /** The ordered `contents` array when the document has one, else the deprecated per-kind buckets.
    *
    * `legacy` is by-name so the bucket builders are not run when `contents` supplies the children —
    * they are not merely redundant then, they would double every child.
    *
    * `extras` are children of this container that `contents` CANNOT carry because they have no
    * [[JsonModel.ContentDto]] of their own and live in a dedicated field instead: a use case's
    * `interactions`, a function's `statements`, a projector's `repository`, an epic's `shownBy`.
    * Without them the ordered path silently dropped those children the moment anything else — a
    * single comment — put something in `contents`. They are appended, so their position relative to
    * the ordered children is not yet faithful; giving each a content kind of its own is what fixes
    * that, and is the remaining field-level work.
    */
  private def childrenOrBuckets[T <: RiddlValue](
    contents: Seq[ContentEntry],
    container: String,
    legal: Kinds,
    legacy: => Contents[T],
    extras: => Seq[T] = Nil
  )(using ctx: Ctx): Contents[T] =
    if contents.isEmpty then
      val built = legacy
      // Only a container that actually HAS bucketed children is using the old shape; an empty one
      // is simply empty and says nothing about which schema the document was written against.
      if built.toSeq.nonEmpty then ctx.deprecated(container)
      built
    else Contents[T]((childrenOf[T](contents, container, legal).toSeq ++ extras)*)

  /** Build a definition's metadata: a `brief` shorthand plus, optionally, the richer
    * [[JsonModel.MetaDto]] (description, terms, options, author refs, attachments, comments). Pure
    * — references are resolved later by the passes.
    */
  private def meta(brief: Option[String], md: Option[MetaDto] = None)(using
    ctx: Ctx
  ): Contents[MetaData] =
    val items = mutable.ArrayBuffer.empty[MetaData]
    // An ordered `items` array carries the brief IN ITS PLACE, so prepending the `brief` shorthand
    // as well would both duplicate it and put it back at the front.
    if md.forall(_.items.isEmpty) then
      brief.foreach(b => items += BriefDescription(curAt, LiteralString(curAt, b)))
    // The ordered `items` array wins when the document has one; the per-kind buckets below are the
    // deprecated form and cannot express the order entries were written in.
    md.filter(_.items.nonEmpty).foreach { m =>
      import ctx.pc
      m.items.foreach { i =>
        i.kind match
          case MetaKind.Description =>
            items += BlockDescription(curAt, i.lines.map(LiteralString(curAt, _)))
          case MetaKind.UrlDescription =>
            i.value.foreach(u => items += URLDescription(curAt, u))
          case MetaKind.Term =>
            items += Term(
              curAt,
              ident(i.name.getOrElse("")),
              i.definition.map(LiteralString(curAt, _))
            )
          case MetaKind.Option_ =>
            items += OptionValue(curAt, i.name.getOrElse(""), i.args.map(LiteralString(curAt, _)))
          case MetaKind.AuthorRef => items += AuthorRef(curAt, pathId(i.path.getOrElse("")))
          case MetaKind.Attachment =>
            val nm = ident(i.name.getOrElse(""))
            val mt = i.mimeType.getOrElse("text/plain")
            val v = LiteralString(curAt, i.value.getOrElse(""))
            items += (if i.inFile then FileAttachment(curAt, nm, mt, v)
                      else StringAttachment(curAt, nm, mt, v))
          case MetaKind.Comment =>
            val text = i.value.getOrElse("")
            items += (if i.inline then InlineComment(curAt, text.split("\n").toSeq)
                      else LineComment(curAt, text))
          case MetaKind.UlidAttachment =>
            i.value.foreach(u => items += ULIDAttachment(curAt, wvlet.airframe.ulid.ULID(u)))
          case MetaKind.Brief =>
            items += BriefDescription(curAt, LiteralString(curAt, i.value.getOrElse("")))
          case MetaKind.FigmaRef =>
            items += FigmaRef(
              curAt,
              LiteralString(curAt, i.fileKey.getOrElse("")),
              LiteralString(curAt, i.nodeId.getOrElse(""))
            )
          case other => ctx.err(s"unknown metadata kind '$other'")
      }
    }
    md.filter(_.items.isEmpty).foreach { m =>
      if m.description.nonEmpty then
        items += BlockDescription(curAt, m.description.map(LiteralString(curAt, _)))
      m.terms
        .foreach(t =>
          items += Term(curAt, ident(t.name), t.definition.map(LiteralString(curAt, _)))
        )
      m.options
        .foreach(o => items += OptionValue(curAt, o.name, o.args.map(LiteralString(curAt, _))))
      m.byAuthors.foreach(a => items += AuthorRef(curAt, pathId(a)))
      m.attachments.foreach { a =>
        if a.inFile then
          items += FileAttachment(curAt, ident(a.name), a.mimeType, LiteralString(curAt, a.value))
        else
          items += StringAttachment(curAt, ident(a.name), a.mimeType, LiteralString(curAt, a.value))
      }
      m.comments.foreach(c => items += LineComment(curAt, c))
      m.figmaRefs.foreach(fr =>
        items += FigmaRef(curAt, LiteralString(curAt, fr.fileKey), LiteralString(curAt, fr.nodeId))
      )
      import ctx.pc
      m.urlDescription.foreach(u => items += URLDescription(curAt, u))
    }
    Contents[MetaData](items.toSeq*)
  end meta

  /** Rebuild the comments that belong in a container's CONTENTS (as opposed to the ones attached to
    * its metadata, which `meta` handles). They are appended after the definitions, since the schema
    * groups children by kind and their original position is not recoverable.
    */
  private def comments(cs: Seq[CommentDto])(using Ctx): Seq[Comment] =
    cs.map { c =>
      if c.inline then InlineComment(curAt, c.text.split("\n").toSeq)
      else LineComment(curAt, c.text)
    }

  private def ident(name: String)(using Ctx): Identifier = Identifier(curAt, name)

  /** A dotted reference string -> PathIdentifier segments. */
  private def pathId(ref: String)(using Ctx): PathIdentifier =
    PathIdentifier(curAt, ref.split('.').iterator.filter(_.nonEmpty).toSeq)

  // ---------------------------------------------------------------------------
  // Definitions
  // ---------------------------------------------------------------------------

  private def buildDomain(d: DomainDto)(using Ctx): Domain =
    val authors = d.authors.map(buildAuthor)
    val users = d.users.map(buildUser)
    val types = d.types.map(buildType)
    val sagas = d.sagas.map(buildSaga)
    val epics = d.epics.map(buildEpic)
    val subdomains = d.domains.map(buildDomain)
    val contexts = d.contexts.map(buildContext)
    val version = d.version.map(buildVersion).toSeq
    val copyright = d.copyright.map(buildCopyright).toSeq
    val commands = d.commands.map(buildMessage(_, AggregateUseCase.CommandCase))
    val events = d.events.map(buildMessage(_, AggregateUseCase.EventCase))
    val queries = d.queries.map(buildMessage(_, AggregateUseCase.QueryCase))
    val results = d.results.map(buildMessage(_, AggregateUseCase.ResultCase))
    val repositories = d.repositories.map(buildRepository)
    val connectors = d.connectors.map(buildConnector)
    Domain(
      curAt,
      ident(d.name),
      childrenOrBuckets[DomainContents](
        d.contents,
        "Domain",
        Legal.domainW,
        contentsOf[DomainContents](
          authors,
          users,
          types,
          sagas,
          epics,
          subdomains,
          contexts,
          version,
          copyright,
          commands,
          events,
          queries,
          results,
          repositories,
          connectors,
          comments(d.comments)
        )
      ),
      meta(d.brief, d.metadata)
    )

  private def buildUser(u: UserDto)(using Ctx): User =
    User(curAt, ident(u.name), LiteralString(curAt, u.isA), meta(u.brief, u.metadata))

  private def buildConstant(c: ConstantDto)(using ctx: Ctx): Constant =
    // `Constant.value: ConstantValue = LiteralString | NumericLiteral | BooleanLiteral |
    // PromptValue` -- narrow what buildValue's general Value result actually is, rather than
    // assuming LiteralString.
    val value: ConstantValue = buildValue(c.value) match
      case cv: ConstantValue => cv
      case other =>
        ctx.err(
          s"constant value must be a literal string, numeric literal, boolean literal, or " +
            s"prompt, got: ${other.getClass.getSimpleName}"
        )
        LiteralString(curAt, "")
    Constant(
      curAt,
      ident(c.name),
      buildTypeExpr(c.`type`),
      value,
      meta(c.brief, c.metadata)
    )

  /** A53: `name` always carries the rendered component; `numeric` says how it was written, and the
    * `number` field is re-derived from the name so the two stay in step.
    */
  private def buildVersion(v: VersionDto)(using Ctx): Version =
    val number = if v.numeric then v.name.toLongOption else None
    Version(curAt, ident(v.name), number, meta(v.brief, v.metadata))

  /** A47: the notice is carried verbatim in `text`; `name` identifies it. */
  private def buildCopyright(c: CopyrightDto)(using Ctx): Copyright =
    Copyright(curAt, ident(c.name), LiteralString(curAt, c.text), meta(c.brief, c.metadata))

  private def buildAuthor(a: AuthorDto)(using Ctx): Author =
    Author(
      curAt,
      ident(a.name),
      LiteralString(curAt, a.fullName),
      LiteralString(curAt, a.email),
      a.organization.map(LiteralString(curAt, _)),
      a.title.map(LiteralString(curAt, _)),
      None,
      meta(None, a.metadata)
    )

  private def buildType(t: TypeDefDto)(using Ctx): Type =
    Type(curAt, ident(t.name), buildTypeExpr(t.typeExpression), meta(t.brief, t.metadata))

  private def buildContext(c: ContextDto)(using Ctx): Context =
    val types = c.types.map(buildType)
    val constants = c.constants.map(buildConstant)
    val commands = c.commands.map(m => buildMessage(m, AggregateUseCase.CommandCase))
    val events = c.events.map(m => buildMessage(m, AggregateUseCase.EventCase))
    val queries = c.queries.map(m => buildMessage(m, AggregateUseCase.QueryCase))
    val results = c.results.map(m => buildMessage(m, AggregateUseCase.ResultCase))
    val entities = c.entities.map(buildEntity)
    val functions = c.functions.map(buildFunction)
    val adaptors = c.adaptors.map(buildAdaptor)
    val streamlets = c.streamlets.map(buildStreamlet)
    val projectors = c.projectors.map(buildProjector)
    val repositories = c.repositories.map(buildRepository)
    val connectors = c.connectors.map(buildConnector)
    val relationships = c.relationships.map(buildRelationship)
    val sagas = c.sagas.map(buildSaga)
    val groups = c.groups.map(buildGroup)
    val handlers = c.handlers.map(buildHandler)
    val inlets = c.inlets.map(buildInlet)
    val outlets = c.outlets.map(buildOutlet)
    val version = c.version.map(buildVersion).toSeq
    val copyright = c.copyright.map(buildCopyright).toSeq
    val invariants = c.invariants.map(buildInvariant)
    Context(
      curAt,
      ident(c.name),
      childrenOrBuckets[ContextContents](
        c.contents,
        "Context",
        Legal.contextW,
        contentsOf[ContextContents](
          types,
          constants,
          commands,
          events,
          queries,
          results,
          entities,
          functions,
          adaptors,
          streamlets,
          projectors,
          repositories,
          connectors,
          relationships,
          sagas,
          groups,
          handlers,
          inlets,
          outlets,
          version,
          copyright,
          invariants,
          comments(c.comments)
        )
      ),
      ascribedShape = parseShape(c.shape),
      intention = parseIntention(c.intention),
      metadata = meta(c.brief, c.metadata)
    )

  /** A message (command/event/query/result) is a `Type` whose expression is an aggregate tagged
    * with the appropriate use case.
    */
  private def buildMessage(m: MessageDto, useCase: AggregateUseCase)(using Ctx): Type =
    val fields = m.fields.map(buildField)
    val typEx = AggregateUseCaseTypeExpression(
      curAt,
      useCase,
      contentsOf[AggregateContents](fields, comments(m.comments)),
      m.yields.map(messageRef)
    )
    Type(curAt, ident(m.name), typEx, meta(m.brief, m.metadata))

  private def buildField(f: FieldDto)(using Ctx): Field =
    Field(curAt, ident(f.name), buildTypeExpr(f.`type`), meta(f.brief, f.metadata))

  /** A field list as an optional Aggregation (None when empty) — used for function and saga
    * input/output.
    */
  private def aggregationOf(fields: Seq[FieldDto])(using Ctx): Option[Aggregation] =
    if fields.isEmpty then None
    else Some(Aggregation(curAt, Contents[AggregateContents](fields.map(buildField)*)))

  /** A9: rebuild a Function/Saga `requires`/`returns` value from its ArgDto — a `TypeRef` from the
    * "keyword path" string (preferred), or a deprecated inline `Aggregation` from a field list.
    */
  /** A `TypeRef` from its rendered "keyword path" form (`record Args`, `command Go`, or a bare
    * path, which means `type`). Shared by every place a type ref arrives as one string.
    */
  private def typeRefOf(s: String)(using Ctx): TypeRef =
    val trimmed = s.trim
    val spaceIdx = trimmed.indexOf(' ')
    val (kw, p) =
      if spaceIdx > 0 then (trimmed.substring(0, spaceIdx), trimmed.substring(spaceIdx + 1).trim)
      else ("type", trimmed)
    TypeRef(curAt, kw, pathId(p))
  end typeRefOf

  private def argOf(arg: Option[ArgDto])(using Ctx): Option[TypeRef | Aggregation] =
    arg.flatMap { a =>
      a.ref match
        case Some(s) => Some(typeRefOf(s))
        case None    => aggregationOf(a.fields)
    }

  private def buildMethod(m: MethodDto)(using Ctx): Method =
    val args = m.args.map(a => MethodArgument(curAt, a.name, buildTypeExpr(a.`type`)))
    Method(curAt, ident(m.name), buildTypeExpr(m.`type`), args, meta(m.brief, m.metadata))

  private def buildEntity(e: EntityDto)(using Ctx): Entity =
    val types = e.types.map(buildType)
    val constants = e.constants.map(buildConstant)
    val commands = e.commands.map(m => buildMessage(m, AggregateUseCase.CommandCase))
    val events = e.events.map(m => buildMessage(m, AggregateUseCase.EventCase))
    val queries = e.queries.map(m => buildMessage(m, AggregateUseCase.QueryCase))
    val results = e.results.map(m => buildMessage(m, AggregateUseCase.ResultCase))
    // Accept both the singular `state` (back-compat) and plural `states`.
    val states = (e.state.toSeq ++ e.states).map(buildState)
    val functions = e.functions.map(buildFunction)
    val handlers = e.handlers.map(buildHandler)
    val invariants = e.invariants.map(buildInvariant)
    val inlets = e.inlets.map(buildInlet)
    val outlets = e.outlets.map(buildOutlet)
    val version = e.version.map(buildVersion).toSeq
    val copyright = e.copyright.map(buildCopyright).toSeq
    val streamlets = e.streamlets.map(buildStreamlet)
    val connectors = e.connectors.map(buildConnector)
    val relationships = e.relationships.map(buildRelationship)
    Entity(
      curAt,
      ident(e.name),
      childrenOrBuckets[EntityContents](
        e.contents,
        "Entity",
        Legal.entityW,
        contentsOf[EntityContents](
          types,
          constants,
          commands,
          events,
          queries,
          results,
          states,
          functions,
          handlers,
          invariants,
          inlets,
          outlets,
          version,
          copyright,
          streamlets,
          connectors,
          relationships,
          comments(e.comments)
        )
      ),
      ascribedShape = parseShape(e.shape),
      intentions = parseEntityIntentions(e.intentions),
      metadata = meta(e.brief, e.metadata)
    )

  /** Rebuild an Entity's intentions from their keywords, dropping any that are unknown (a document
    * written against a newer schema stays readable) and canonicalising the order.
    */
  private def parseEntityIntentions(keywords: Seq[String]): Seq[EntityIntention] =
    EntityIntention.canonical(keywords.flatMap(EntityIntention.fromKeyword))

  /** A state references a record type and may carry nested handlers (RIDDL entity state machines
    * put per-state handlers inside the state).
    */
  private def buildState(s: StateDto)(using Ctx): State =
    State(
      curAt,
      ident(s.name),
      RecordRef(curAt, pathId(s.recordType)), // A9b: state type is a RecordRef
      childrenOrBuckets[StateContents](
        s.contents,
        "State",
        Legal.state,
        contentsOf[StateContents](
          s.handlers.map(buildHandler),
          s.invariants.map(buildInvariant),
          comments(s.comments)
        )
      ),
      meta(s.brief, s.metadata),
      s.isInitial
    )

  /** A70: a keyed accumulation of events into one record, inside a projector.
    *
    * `keys` is rebuilt in document order and never sorted — §6.5 makes identity the full tuple, so
    * reordering here would silently change what the model declares.
    */
  private def buildCorrelation(c: CorrelationDto)(using Ctx): Correlation =
    Correlation(
      curAt,
      ident(c.name),
      c.keys.map(ident),
      CommandRef(curAt, pathId(c.yieldsCommand)),
      LiteralString(curAt, c.timeout),
      childrenOrBuckets[CorrelationContents](
        c.contents,
        "Correlation",
        Legal.correlation,
        contentsOf[CorrelationContents](
          c.handlers.map(buildHandler),
          comments(c.comments)
        )
      ),
      buildStatements(c.timeoutStatements),
      meta(c.brief, c.metadata)
    )

  private def buildHandler(h: HandlerDto)(using Ctx): Handler =
    val clauses = h.onClauses.map(buildOnClause)
    Handler(
      curAt,
      ident(h.name),
      childrenOrBuckets[HandlerContents](
        h.contents,
        "Handler",
        Legal.handler,
        contentsOf[HandlerContents](clauses, comments(h.comments))
      ),
      meta(h.brief, h.metadata),
      h.isInitial
    )

  private def buildOnClause(oc: OnClauseDto)(using ctx: Ctx): OnClause =
    // `Statements` is `Statement | Comment`, so a comment written between two statements belongs
    // in the on-clause's contents beside them, not in its metadata.
    // The statement list carries its own comments (`Statements` is `Statement | Comment`).
    val statements = buildStatements(oc.statements)
    // A55: the optional local name bound to the handled message
    val binding: Option[Identifier] = oc.binding.map(ident)
    val from: Option[(Option[Identifier], Reference[Definition])] =
      oc.from.map(f => (f.name.map(ident), buildRef(f.ref)))
    val md = meta(oc.brief, oc.metadata)
    oc.kind match
      case "message" =>
        oc.message match
          case Some(mr) =>
            OnMessageClause(
              curAt,
              messageRef(mr),
              from,
              binding,
              statements,
              md
            )
          case None =>
            ctx.err("on-clause of kind 'message' requires a 'message' reference")
            OnMessageClause(
              curAt,
              CommandRef(curAt, PathIdentifier.empty),
              from,
              binding,
              statements,
              md
            )
      case "event" =>
        oc.message match
          case Some(mr) =>
            OnEventClause(
              curAt,
              messageRef(mr),
              from,
              binding,
              statements,
              md
            )
          case None =>
            ctx.err("on-clause of kind 'event' requires a 'message' reference")
            OnEventClause(
              curAt,
              EventRef(curAt, PathIdentifier.empty),
              from,
              binding,
              statements,
              md
            )
      case "init" =>
        val parameters = oc.parameters.map(a => MethodArgument(curAt, a.name, buildTypeExpr(a.`type`)))
        OnInitializationClause(curAt, parameters, statements, md)
      case "other" =>
        // A57: rebuild the optional envelope binding and its optional explicit type.
        OnOtherClause(
          curAt,
          binding,
          oc.envelope.map(e => TypeRef(curAt, "type", pathId(e))),
          statements,
          md
        )
      case "term" =>
        val parameters = oc.parameters.map(a => MethodArgument(curAt, a.name, buildTypeExpr(a.`type`)))
        OnTerminationClause(curAt, parameters, statements, md)
      case "activate"  => OnActivationClause(curAt, statements, md)
      case "passivate" => OnPassivationClause(curAt, statements, md)
      case other =>
        ctx.err(
          s"unknown on-clause kind '$other' (expected message|event|init|other|term|activate|passivate)"
        )
        OnOtherClause(curAt, None, None, statements, md)
    end match
  end buildOnClause

  private def messageRef(mr: MessageRefDto)(using ctx: Ctx): MessageRef =
    mr.kind match
      case "command" => CommandRef(curAt, pathId(mr.ref))
      case "event"   => EventRef(curAt, pathId(mr.ref))
      case "query"   => QueryRef(curAt, pathId(mr.ref))
      case "result"  => ResultRef(curAt, pathId(mr.ref))
      case other     =>
        // A9b: a record is not a message.
        ctx.err(s"unknown message kind '$other' (expected command|event|query|result)")
        CommandRef(curAt, pathId(mr.ref))

  private def buildInvariant(i: InvariantDto)(using ctx: Ctx): Invariant =
    // A28: a structured `expression` rebuilds a BooleanExpression; otherwise the `condition` string
    // rebuilds a LiteralString (preserving the legacy always-Some behavior).
    val cond: Option[LiteralString | BooleanExpression | InvariantBlock] = i.block match
      // The block form wins when present: it carries statements the other two arms cannot express.
      case Some(blk) =>
        buildValue(blk.predicate) match
          case be: BooleanExpression =>
            Some(InvariantBlock(curAt, blk.statements.map(buildStatement).toContents, be))
          case _ =>
            ctx.err("invariant 'block.predicate' must be a boolean expression")
            Some(LiteralString(curAt, ""))
      case None =>
        i.expression match
          case Some(exprDto) =>
            buildValue(exprDto) match
              case be: BooleanExpression => Some(be)
              case _ =>
                ctx.err("invariant 'expression' must be a boolean expression")
                Some(LiteralString(curAt, ""))
          case None => Some(LiteralString(curAt, i.condition))
    // `requiresKind` is the discriminator: a bare path cannot say whether `Open` meant the state
    // or a type of that name, and the two put the invariant in different scopes.
    val requires: Option[StateRef | TypeRef] = i.requires.map { ref =>
      i.requiresKind match
        case Some("state") => StateRef(curAt, pathId(ref))
        case Some("type")  => typeRefOf(ref)
        case Some(other) =>
          ctx.err(s"unknown invariant requiresKind '$other' (expected state|type)")
          typeRefOf(ref)
        case None => typeRefOf(ref)
    }
    Invariant(curAt, ident(i.name), cond, requires, meta(i.brief, i.metadata))

  // ---------------------------------------------------------------------------
  // Functions and statements (Phase 3)
  // ---------------------------------------------------------------------------

  private def buildFunction(f: FunctionDto)(using Ctx): Function =
    val types = f.types.map(buildType)
    val statements = f.statements.map(buildStatement)
    val functions = f.functions.map(buildFunction)
    // A9 / revision 4: `requires`/`returns` are CONTENTS now, so they rebuild as Requires/Returns
    // nodes. An ordered document carries them IN PLACE (see RequiresDto), so the bucketed
    // `input`/`output` fields are read only on the legacy path -- reading both would emit each
    // clause twice, and prepending them would undo the very ordering the contents array preserves.
    val clauses: Seq[FunctionContents] =
      if f.contents.nonEmpty then Nil
      else
        argOf(f.input).map(v => Requires(curAt, v)).toSeq ++
          argOf(f.output).map(v => Returns(curAt, v)).toSeq
    val body = childrenOrBuckets[FunctionContents](
      f.contents,
      "Function",
      Legal.function,
      contentsOf[FunctionContents](types, statements, functions, comments(f.comments)),
      statements
    )
    Function(
      curAt,
      ident(f.name),
      (clauses ++ body.toSeq).toContents,
      meta(f.brief, f.metadata)
    )

  private def buildSagaStep(st: SagaStepDto)(using Ctx): SagaStep =
    SagaStep(
      curAt,
      ident(st.name),
      buildStatements(st.`do`),
      buildStatements(st.undo),
      meta(st.brief, st.metadata)
    )

  private def buildSaga(s: SagaDto)(using Ctx): Saga =
    val types = s.types.map(buildType)
    val steps = s.steps.map(buildSagaStep)
    // A9 / revision 4: see buildFunction — the clauses are contents, not fields, and the bucketed
    // fields are the legacy-only path.
    val clauses: Seq[SagaContents] =
      if s.contents.nonEmpty then Nil
      else
        argOf(s.input).map(v => Requires(curAt, v)).toSeq ++
          argOf(s.output).map(v => Returns(curAt, v)).toSeq
    val body = childrenOrBuckets[SagaContents](
      s.contents,
      "Saga",
      Legal.sagaW,
      contentsOf[SagaContents](types, steps, comments(s.comments))
    )
    Saga(
      curAt,
      ident(s.name),
      (clauses ++ body.toSeq).toContents,
      meta(s.brief, s.metadata)
    )

  // ---------------------------------------------------------------------------
  // Epics, use cases, interactions (Phase 7)
  // ---------------------------------------------------------------------------

  private def buildUserStory(us: UserStoryDto)(using Ctx): UserStory =
    UserStory(
      curAt,
      UserRef(curAt, pathId(us.user)),
      LiteralString(curAt, us.capability),
      LiteralString(curAt, us.benefit)
    )

  /** A generic definition reference for an interaction's from/to. */
  private def buildRef(r: RefDto)(using ctx: Ctx): Reference[Definition] =
    r.kind match
      case "user"       => UserRef(curAt, pathId(r.path))
      case "entity"     => EntityRef(curAt, pathId(r.path))
      case "context"    => ContextRef(curAt, pathId(r.path))
      case "group"      => GroupRef(curAt, r.keyword.getOrElse("group"), pathId(r.path))
      case "output"     => OutputRef(curAt, r.keyword.getOrElse("output"), pathId(r.path))
      case "input"      => InputRef(curAt, r.keyword.getOrElse("input"), pathId(r.path))
      case "adaptor"    => AdaptorRef(curAt, pathId(r.path))
      case "projector"  => ProjectorRef(curAt, pathId(r.path))
      case "repository" => RepositoryRef(curAt, pathId(r.path))
      case "saga"       => SagaRef(curAt, pathId(r.path))
      case "streamlet"  => StreamletRef(curAt, r.keyword.getOrElse("source"), pathId(r.path))
      case other =>
        ctx.err(s"unknown reference kind '$other' for an interaction")
        UserRef(curAt, pathId(r.path))

  private def buildInteraction(i: InteractionDto)(using ctx: Ctx): Interaction =
    val nm = Contents.empty[MetaData]()
    i match
      case VagueIxnDto(from, rel, to) =>
        VagueInteraction(
          curAt,
          LiteralString(curAt, from),
          LiteralString(curAt, rel),
          LiteralString(curAt, to),
          nm
        )
      case SendMessageIxnDto(from, msg, to, proc) =>
        SendMessageInteraction(curAt, buildRef(from), messageRef(msg), processorRef(to, proc), nm)
      case ArbitraryIxnDto(from, rel, to) =>
        ArbitraryInteraction(curAt, buildRef(from), LiteralString(curAt, rel), buildRef(to), nm)
      case SelfIxnDto(from, rel) =>
        SelfInteraction(curAt, buildRef(from), LiteralString(curAt, rel), nm)
      case FocusOnGroupIxnDto(user, group, kw) =>
        FocusOnGroupInteraction(
          curAt,
          UserRef(curAt, pathId(user)),
          GroupRef(curAt, kw.getOrElse("group"), pathId(group)),
          nm
        )
      case DirectToURLIxnDto(user, url) =>
        DirectUserToURLInteraction(curAt, UserRef(curAt, pathId(user)), URL(url), nm)
      case ShowOutputIxnDto(output, rel, user, kw) =>
        ShowOutputInteraction(
          curAt,
          OutputRef(curAt, kw.getOrElse("output"), pathId(output)),
          LiteralString(curAt, rel),
          UserRef(curAt, pathId(user)),
          nm
        )
      case SelectInputIxnDto(user, input, kw) =>
        SelectInputInteraction(
          curAt,
          UserRef(curAt, pathId(user)),
          InputRef(curAt, kw.getOrElse("input"), pathId(input)),
          nm
        )
      case TakeInputIxnDto(user, input, kw) =>
        TakeInputInteraction(
          curAt,
          UserRef(curAt, pathId(user)),
          InputRef(curAt, kw.getOrElse("input"), pathId(input)),
          nm
        )
      case RefusalIxnDto(from, user, reason) =>
        RefusalInteraction(
          curAt,
          buildRef(from),
          UserRef(curAt, pathId(user)),
          LiteralString(curAt, reason),
          nm
        )
      case ParallelIxnDto(ixns) =>
        ParallelInteractions(
          curAt,
          contentsOf[InteractionContainerContents](ixns.map(buildInteraction)),
          nm
        )
      case SequentialIxnDto(ixns) =>
        SequentialInteractions(
          curAt,
          contentsOf[InteractionContainerContents](ixns.map(buildInteraction)),
          nm
        )
      case OptionalIxnDto(ixns) =>
        OptionalInteractions(
          curAt,
          contentsOf[InteractionContainerContents](ixns.map(buildInteraction)),
          nm
        )
    end match
  end buildInteraction

  private def buildUseCase(u: UseCaseDto)(using Ctx): UseCase =
    UseCase(
      curAt,
      ident(u.name),
      buildUserStory(u.userStory),
      childrenOrBuckets[UseCaseContents](
        u.contents,
        "UseCase",
        Legal.useCase,
        contentsOf[UseCaseContents](u.interactions.map(buildInteraction), comments(u.comments))
      ),
      meta(u.brief, u.metadata)
    )

  private def buildEpic(e: EpicDto)(using Ctx): Epic =
    val types = e.types.map(buildType)
    val useCases = e.useCases.map(buildUseCase)
    val shownBy = if e.shownBy.isEmpty then Nil else Seq(ShownBy(curAt, e.shownBy.map(u => URL(u))))
    Epic(
      curAt,
      ident(e.name),
      buildUserStory(e.userStory),
      childrenOrBuckets[EpicContents](
        e.contents,
        "Epic",
        Legal.epicW,
        contentsOf[EpicContents](types, shownBy, useCases, comments(e.comments)),
        shownBy
      ),
      meta(e.brief, e.metadata)
    )

  // ---------------------------------------------------------------------------
  // UI groups (Phase 8)
  // ---------------------------------------------------------------------------

  private def buildPutOut(p: PutOutDto)(using ctx: Ctx): TypeRef | ConstantRef | LiteralString =
    p.kind match
      case "type"     => TypeRef(curAt, p.keyword.getOrElse("type"), pathId(p.value))
      case "constant" => ConstantRef(curAt, pathId(p.value))
      case "literal"  => LiteralString(curAt, p.value)
      case other =>
        ctx.err(s"unknown output putOut kind '$other' (expected type|constant|literal)")
        LiteralString(curAt, p.value)

  private def buildInput(i: InputDto)(using Ctx): Input =
    Input(
      curAt,
      i.nounAlias.getOrElse("input"),
      ident(i.name),
      i.verbAlias.getOrElse("acquires"),
      TypeRef(curAt, i.keyword.getOrElse("type"), pathId(i.takeIn)),
      contentsOf[OccursInInput](i.inputs.map(buildInput)),
      meta(i.brief, i.metadata)
    )

  private def buildOutput(o: OutputDto)(using Ctx): Output =
    Output(
      curAt,
      o.nounAlias.getOrElse("output"),
      ident(o.name),
      o.verbAlias.getOrElse("displays"),
      buildPutOut(o.putOut),
      contentsOf[OccursInOutput](o.outputs.map(buildOutput)),
      meta(o.brief, o.metadata)
    )

  private def buildContainedGroup(cg: ContainedGroupDto)(using Ctx): ContainedGroup =
    ContainedGroup(
      curAt,
      ident(cg.name),
      GroupRef(curAt, "group", pathId(cg.group)),
      meta(cg.brief, cg.metadata)
    )

  private def buildGroup(g: GroupDto)(using Ctx): Group =
    val groups = g.groups.map(buildGroup)
    val contained = g.containedGroups.map(buildContainedGroup)
    val inputs = g.inputs.map(buildInput)
    val outputs = g.outputs.map(buildOutput)
    Group(
      curAt,
      g.alias.getOrElse("group"),
      ident(g.name),
      childrenOrBuckets[OccursInGroup](
        g.contents,
        "Group",
        Legal.group,
        contentsOf[OccursInGroup](groups, contained, inputs, outputs, comments(g.comments))
      ),
      meta(g.brief, g.metadata)
    )

  /** `Statements` is `Statement | Comment`, so the comment arm rebuilds a `Comment` in place rather
    * than a statement. `buildStatement` stays total over the statement kinds.
    */
  private def buildStatements(stmts: Seq[StatementDto])(using Ctx): Contents[Statements] =
    val items: Seq[Statements] = stmts.map {
      case CommentStmtDto(text, true)  => InlineComment(curAt, text.split("\n").toSeq)
      case CommentStmtDto(text, false) => LineComment(curAt, text)
      case other                       => buildStatement(other)
    }
    Contents[Statements](items*)

  private def buildStatement(s: StatementDto)(using ctx: Ctx): Statement =
    s match
      // A comment is a member of `Statements` but not of `Statement`, so `buildStatements`
      // intercepts it and this arm is unreachable. It is spelled out rather than left to a
      // catch-all so that adding a statement kind still fails the exhaustivity check.
      case CommentStmtDto(text, _) =>
        ctx.err(s"a comment is not a statement and should not reach buildStatement: '$text'")
        PromptStatement(curAt, LiteralString(curAt, text))
      case PromptStmtDto(text)   => PromptStatement(curAt, LiteralString(curAt, text))
      case ErrorStmtDto(message) => ErrorStatement(curAt, LiteralString(curAt, message))
      case LetStmtDto(name, t, expression) =>
        LetStatement(
          curAt,
          ident(name),
          t.map(p => TypeRef(curAt, "type", pathId(p))),
          buildValue(expression)
        )
      case CodeStmtDto(language, body) => CodeStatement(curAt, LiteralString(curAt, language), body)
      case RequireStmtDto(condition, invariant, expression, argument) =>
        val cond: LiteralString | InvariantRef | BooleanExpression = expression match
          case Some(exprDto) => // A28: structured boolean-expression condition
            buildValue(exprDto) match
              case be: BooleanExpression => be
              case _ =>
                ctx.err("require 'expression' must be a boolean expression")
                LiteralString(curAt, "")
          case None =>
            invariant match
              case Some(name) => InvariantRef(curAt, pathId(name))
              case None =>
                condition match
                  case Some(c) => LiteralString(curAt, c)
                  case None =>
                    ctx.err("require statement needs a 'condition', 'invariant', or 'expression'")
                    LiteralString(curAt, "")
        RequireStatement(curAt, cond, argument.map(buildValue))
      case SetStmtDto(field, state, value) =>
        val target: FieldRef | StateRef = (field, state) match
          case (Some(f), _)     => FieldRef(curAt, pathId(f))
          case (None, Some(st)) => StateRef(curAt, pathId(st))
          case (None, None) =>
            ctx.err("set statement needs a 'field' or a 'state' target")
            FieldRef(curAt, PathIdentifier.empty)
        SetStatement(curAt, target, buildValue(value))
      case SendStmtDto(message, to, portlet) =>
        SendStatement(curAt, buildDeliverableOperand(message), portletRef(to, portlet))
      case MorphStmtDto(entity, state, value) =>
        MorphStatement(
          curAt,
          EntityRef(curAt, pathId(entity)),
          StateRef(curAt, pathId(state)),
          buildRecordOperand(value) // A9b/A54 + Task 2: RecordRef, Constructor or ValueRef
        )
      case BecomeStmtDto(entity, handler) =>
        BecomeStatement(curAt, EntityRef(curAt, pathId(entity)), HandlerRef(curAt, pathId(handler)))
      case TellStmtDto(message, to, processor, by) =>
        TellStatement(
          curAt,
          buildDeliverableOperand(message),
          processorRef(to, processor),
          by.map(ident)
        )
      case YieldStmtDto(message) => YieldStatement(curAt, buildDeliverableOperand(message))
      case ReplyStmtDto(message) => ReplyStatement(curAt, buildDeliverableOperand(message))
      case WhenStmtDto(condition, conditionId, negated, thenS, elseS, expression) =>
        val cond: LiteralString | Identifier | ValueRef | BooleanExpression | PromptValue =
          expression match
            case Some(exprDto) =>
              buildValue(exprDto) match
                case be: BooleanExpression => be // A28: structured boolean expression
                case vr: ValueRef          => vr // A17: bare boolean value reference
                // `when prompt("…")` — an AI-evaluated condition. The emitter has written this
                // since `when prompt` landed, but the builder rejected it, so any model using one
                // produced JSON that could not be read back.
                case pv: PromptValue => pv
                case _ =>
                  ctx.err(
                    "when 'expression' must be a boolean expression, a value reference or a prompt"
                  )
                  LiteralString(curAt, "")
            case None =>
              conditionId match
                case Some(id) => ident(id)
                case None     => LiteralString(curAt, condition.getOrElse(""))
        WhenStatement(curAt, cond, buildStatements(thenS), buildStatements(elseS), negated)
      case MatchStmtDto(subject, cases, default) =>
        val subj: MatchSubject = buildValue(subject) match // A29: narrow to MatchSubject
          case vr: ValueRef      => vr
          case gv: GetValue      => gv
          case ls: LiteralString => ls
          case other =>
            ctx.err(s"match subject must be a value ref, get, or literal, got: $other")
            LiteralString(curAt, "")
        MatchStatement(
          curAt,
          subj,
          cases.map(buildMatchCase),
          buildStatements(default)
        )
      case ForeachStmtDto(element, valueElement, field, local, doStatements) =>
        val collection: FieldRef | Identifier = (field, local) match
          case (Some(f), _)    => FieldRef(curAt, pathId(f))
          case (None, Some(l)) => ident(l)
          case (None, None) =>
            ctx.err("foreach statement needs a 'field' or a 'local' collection")
            FieldRef(curAt, PathIdentifier.empty)
        ForeachStatement(
          curAt,
          ident(element),
          valueElement.map(ident),
          collection,
          buildStatements(doStatements)
        )
      case PutStmtDto(value, output) =>
        PutStatement(curAt, buildValue(value), OutputRef(curAt, "output", pathId(output)))
      case ReturnStmtDto(value) =>
        ReturnStatement(curAt, buildValue(value))
      case TerminateStmtDto(processor, processorKind, args) => // A70/instance-identity
        TerminateStatement(
          curAt,
          processorRef(processor, processorKind),
          args.map(a => ConstructorArg(curAt, a.name.map(ident), buildValue(a.value)))
        )
  end buildStatement

  // A54: ValueDto -> AST Value.
  private def buildValue(v: ValueDto)(using ctx: Ctx): Value =
    v match
      case LiteralValueDto(text)  => LiteralString(curAt, text)
      case NumericLiteralDto(text) => NumericLiteral(curAt, text)
      case PromptValueDto(prompt) => PromptValue(curAt, LiteralString(curAt, prompt))
      case ValueRefDto(p)         => ValueRef(curAt, pathId(p))
      case GetValueDto(source, keyword, ref) =>
        val src: InputRef | StateRef = source match
          case "input" => InputRef(curAt, keyword.getOrElse("input"), pathId(ref))
          case "state" => StateRef(curAt, pathId(ref))
          case other =>
            ctx.err(s"unknown get-value source '$other' (expected input|state)")
            StateRef(curAt, pathId(ref))
        GetValue(curAt, src)
      case c: ConstructorValueDto => buildConstructor(c)
      case c: CallValueDto        => buildCall(c) // A24
      case a: AskValueDto =>
        Ask(curAt, QueryRef(curAt, pathId(a.query)), processorRef(a.processor, a.processorKind))
      case BooleanLiteralDto(b)   => BooleanLiteral(curAt, b)
      case ComparisonDto(op, left, right) =>
        val cop = ComparisonOperator.values
          .find(_.symbol == op)
          .getOrElse { ctx.err(s"unknown comparison operator '$op'"); ComparisonOperator.EQ }
        ComparisonExpression(curAt, cop, buildComparand(left), buildComparand(right))
      case ConstantRefDto(p) => ValueRef(curAt, pathId(p)) // A28: only valid as a comparand
      case LogicalDto(op, left, right) =>
        val lop = LogicalOperator.values
          .find(_.symbol == op)
          .getOrElse { ctx.err(s"unknown logical operator '$op'"); LogicalOperator.And }
        LogicalExpression(curAt, lop, buildValue(left), buildValue(right))
      case NotDto(expr) => NotExpression(curAt, buildValue(expr))
      case InvariantConditionDto(inv, argument) =>
        InvariantCondition(curAt, InvariantRef(curAt, pathId(inv)), argument.map(buildValue))
      case SelfValueDto(field) => SelfValue(curAt, field.map(f => Identifier(curAt, f)))
      case i: InitiateValueDto => // A70/instance-identity
        Initiate(
          curAt,
          processorRef(i.processor, i.processorKind),
          i.args.map(a => ConstructorArg(curAt, a.name.map(ident), buildValue(a.value)))
        )

  // A28, widened 2026-08-14: ValueDto -> AST Comparand (ValueRef | GetValue | ConstantRef |
  // NumericLiteral). Any other DTO is a malformed comparand (reported), degraded to a bare
  // ValueRef.
  private def buildComparand(v: ValueDto)(using ctx: Ctx): Comparand =
    v match
      case ValueRefDto(p)          => ValueRef(curAt, pathId(p))
      case ConstantRefDto(p)       => ConstantRef(curAt, pathId(p))
      case NumericLiteralDto(text) => NumericLiteral(curAt, text)
      case GetValueDto(source, keyword, ref) =>
        val src: InputRef | StateRef = source match
          case "input" => InputRef(curAt, keyword.getOrElse("input"), pathId(ref))
          case "state" => StateRef(curAt, pathId(ref))
          case other =>
            ctx.err(s"unknown get-value source '$other' (expected input|state)")
            StateRef(curAt, pathId(ref))
        GetValue(curAt, src)
      case other =>
        ctx.err(s"comparison operand must be a value/constant reference, got: $other")
        ValueRef(curAt, PathIdentifier.empty)

  // A54: ConstructorValueDto -> AST Constructor.
  private def buildConstructor(c: ConstructorValueDto)(using ctx: Ctx): Constructor =
    val cref: MessageRef | RecordRef = c.refKind match
      case "command" => CommandRef(curAt, pathId(c.ref))
      case "event"   => EventRef(curAt, pathId(c.ref))
      case "query"   => QueryRef(curAt, pathId(c.ref))
      case "result"  => ResultRef(curAt, pathId(c.ref))
      case "record"  => RecordRef(curAt, pathId(c.ref))
      case other =>
        ctx.err(s"unknown constructor refKind '$other'")
        RecordRef(curAt, pathId(c.ref))
    Constructor(
      curAt,
      cref,
      c.args.map(a => ConstructorArg(curAt, a.name.map(ident), buildValue(a.value)))
    )

  // A24: CallValueDto -> AST Call.
  private def buildCall(c: CallValueDto)(using ctx: Ctx): Call =
    Call(
      curAt,
      FunctionRef(curAt, pathId(c.function)),
      c.args.map(a => ConstructorArg(curAt, a.name.map(ident), buildValue(a.value)))
    )

  // A54: a message operand — a bare ref or an inline constructor.
  private def buildMsgOperand(o: MsgOperandDto)(using ctx: Ctx): MessageRef | Constructor = o match
    case c: ConstructorValueDto => buildConstructor(c)
    case m: MessageRefDto       => messageRef(m)

  /** A56: the widened operand of `tell`/`send` and, as of Task 2, `yield`/`reply`. Mirror of
    * `JsonifierPass.serializeDeliverableOperand` — the reserved kind `"bound"` rebuilds a
    * [[ValueRef]]. It stays separate from [[buildMsgOperand]] because that one is still used where
    * only a keyword-led operand is legal.
    */
  private def buildDeliverableOperand(
    o: MsgOperandDto
  )(using ctx: Ctx): MessageRef | Constructor | ValueRef = o match
    case m: MessageRefDto if m.kind == "bound" => ValueRef(curAt, pathId(m.ref))
    case other                                 => buildMsgOperand(other)

  // A54: a record operand for `morph … with` — a bare record ref or an inline constructor, plus
  // Task 2's bare ValueRef, carried by the same reserved `"bound"` kind used for messages.
  private def buildRecordOperand(
    o: MsgOperandDto
  )(using ctx: Ctx): RecordRef | Constructor | ValueRef =
    o match
      case c: ConstructorValueDto                => buildConstructor(c)
      case m: MessageRefDto if m.kind == "bound" => ValueRef(curAt, pathId(m.ref))
      case m: MessageRefDto                      => RecordRef(curAt, pathId(m.ref))

  private def buildMatchCase(c: MatchCaseDto)(using ctx: Ctx): MatchCase =
    val guard: Option[BooleanExpression | ValueRef] = c.guard.map(g =>
      buildValue(g) match
        case be: BooleanExpression => be
        case vr: ValueRef          => vr // A29: bare boolean value-ref guard
        case _ =>
          ctx.err("match case guard must be a boolean expression or a boolean value reference")
          BooleanLiteral(curAt, true)
    )
    MatchCase(curAt, buildMatchPattern(c.pattern), guard, buildStatements(c.statements))

  // A29: MatchPatternDto -> AST MatchPattern.
  private def buildMatchPattern(p: MatchPatternDto)(using ctx: Ctx): MatchPattern =
    p match
      case TypePatternDto(path, keyword) =>
        TypePattern(curAt, TypeRef(curAt, keyword.getOrElse("type"), pathId(path)))
      case ComparisonPatternDto(op, comparand) =>
        val cop = ComparisonOperator.values
          .find(_.symbol == op)
          .getOrElse { ctx.err(s"unknown comparison operator '$op'"); ComparisonOperator.EQ }
        ComparisonPattern(curAt, cop, buildComparand(comparand))
      case LiteralPatternDto(text) => LiteralPattern(curAt, LiteralString(curAt, text))

  private def portletRef(path: String, kind: String)(using ctx: Ctx): PortletRef[Portlet] =
    kind match
      case "inlet"  => InletRef(curAt, pathId(path))
      case "outlet" => OutletRef(curAt, pathId(path))
      case other =>
        ctx.err(s"unknown portlet kind '$other' (expected inlet|outlet)")
        InletRef(curAt, pathId(path))

  private def processorRef(path: String, kind: String)(using ctx: Ctx): ProcessorRef[Processor[?]] =
    kind match
      case "entity"     => EntityRef(curAt, pathId(path))
      case "context"    => ContextRef(curAt, pathId(path))
      case "projector"  => ProjectorRef(curAt, pathId(path))
      case "repository" => RepositoryRef(curAt, pathId(path))
      case "adaptor"    => AdaptorRef(curAt, pathId(path))
      // A streamlet reference is named by its SHAPE, which doubles as the discriminator — it
      // cannot collide with the kinds above.
      case kw @ ("source" | "sink" | "merge" | "split" | "void") =>
        StreamletRef(curAt, kw, pathId(path))
      case other =>
        ctx.err(
          s"unknown processor kind '$other' " +
            "(expected entity|context|projector|repository|adaptor|source|sink|merge|split|void)"
        )
        EntityRef(curAt, pathId(path))

  // ---------------------------------------------------------------------------
  // Streaming & integration (Phase 4)
  // ---------------------------------------------------------------------------

  private def adaptorDirection(s: String)(using ctx: Ctx): AdaptorDirection =
    s match
      case "inbound"  => InboundAdaptor(curAt)
      case "outbound" => OutboundAdaptor(curAt)
      case other =>
        ctx.err(s"unknown adaptor direction '$other' (expected inbound|outbound)")
        InboundAdaptor(curAt)

  private def buildAdaptor(a: AdaptorDto)(using Ctx): Adaptor =
    val types = a.types.map(buildType)
    val constants = a.constants.map(buildConstant)
    val commands = a.commands.map(m => buildMessage(m, AggregateUseCase.CommandCase))
    val events = a.events.map(m => buildMessage(m, AggregateUseCase.EventCase))
    val queries = a.queries.map(m => buildMessage(m, AggregateUseCase.QueryCase))
    val results = a.results.map(m => buildMessage(m, AggregateUseCase.ResultCase))
    val functions = a.functions.map(buildFunction)
    val handlers = a.handlers.map(buildHandler)
    val inlets = a.inlets.map(buildInlet)
    val outlets = a.outlets.map(buildOutlet)
    val version = a.version.map(buildVersion).toSeq
    val copyright = a.copyright.map(buildCopyright).toSeq
    val invariants = a.invariants.map(buildInvariant)
    val streamlets = a.streamlets.map(buildStreamlet)
    val connectors = a.connectors.map(buildConnector)
    val relationships = a.relationships.map(buildRelationship)
    Adaptor(
      curAt,
      ident(a.name),
      adaptorDirection(a.direction),
      ContextRef(curAt, pathId(a.context)),
      childrenOrBuckets[AdaptorContents](
        a.contents,
        "Adaptor",
        Legal.adaptorW,
        contentsOf[AdaptorContents](
          types,
          constants,
          commands,
          events,
          queries,
          results,
          functions,
          handlers,
          inlets,
          outlets,
          version,
          copyright,
          invariants,
          streamlets,
          connectors,
          relationships,
          comments(a.comments)
        )
      ),
      ascribedShape = parseShape(a.shape),
      metadata = meta(a.brief, a.metadata)
    )

  /** Rebuild a Processor's OPTIONAL ascribed shape from its keyword (absent = None). */
  // ascribedShape participates in Definition.equals, so the shape loc must be
  // surface-independent: normalize to At.empty like the parser/BAST paths.
  private def parseShape(s: Option[String]): Option[StreamletShape] =
    s.flatMap(k => StreamletShape.fromKeyword(k, At.empty))

  /** Rebuild a Context's optional intention from its keyword (absent/unknown = None). */
  private def parseIntention(s: Option[String]): Option[Intention] =
    s.flatMap(Intention.fromKeyword)

  private def buildInlet(p: PortletDto)(using Ctx): Inlet =
    Inlet(
      curAt,
      ident(p.name),
      TypeRef(curAt, p.keyword.getOrElse("type"), pathId(p.`type`)),
      meta(p.brief, p.metadata)
    )

  private def buildOutlet(p: PortletDto)(using Ctx): Outlet =
    Outlet(
      curAt,
      ident(p.name),
      TypeRef(curAt, p.keyword.getOrElse("type"), pathId(p.`type`)),
      meta(p.brief, p.metadata)
    )

  private def buildConnector(c: ConnectorDto)(using Ctx): Connector =
    Connector(
      curAt,
      ident(c.name),
      OutletRef(curAt, pathId(c.from)),
      InletRef(curAt, pathId(c.to)),
      parseConnectorIntentions(c.intentions),
      meta(c.brief, c.metadata)
    )

  /** Rebuild a Connector's intentions from their keywords, dropping any that are unknown (a
    * document written against a newer schema stays readable) and canonicalising the order --
    * same contract as `parseEntityIntentions`.
    */
  private def parseConnectorIntentions(keywords: Seq[String]): Seq[ConnectorIntention] =
    ConnectorIntention.canonical(keywords.flatMap(ConnectorIntention.fromKeyword))

  private def buildStreamlet(s: StreamletDto)(using Ctx): Streamlet =
    val inlets = s.inlets.map(buildInlet)
    val outlets = s.outlets.map(buildOutlet)
    val connectors = s.connectors.map(buildConnector)
    val types = s.types.map(buildType)
    val commands = s.commands.map(m => buildMessage(m, AggregateUseCase.CommandCase))
    val events = s.events.map(m => buildMessage(m, AggregateUseCase.EventCase))
    val queries = s.queries.map(m => buildMessage(m, AggregateUseCase.QueryCase))
    val results = s.results.map(m => buildMessage(m, AggregateUseCase.ResultCase))
    val handlers = s.handlers.map(buildHandler)
    val version = s.version.map(buildVersion).toSeq
    val copyright = s.copyright.map(buildCopyright).toSeq
    val constants = s.constants.map(buildConstant)
    val functions = s.functions.map(buildFunction)
    val invariants = s.invariants.map(buildInvariant)
    val nested = s.streamlets.map(buildStreamlet)
    val relationships = s.relationships.map(buildRelationship)
    Streamlet(
      curAt,
      ident(s.name),
      parseShape(s.shape),
      childrenOrBuckets[StreamletContents](
        s.contents,
        "Streamlet",
        Legal.streamletW,
        contentsOf[StreamletContents](
          types,
          commands,
          events,
          queries,
          results,
          inlets,
          outlets,
          connectors,
          handlers,
          version,
          copyright,
          constants,
          functions,
          invariants,
          nested,
          relationships,
          comments(s.comments)
        )
      ),
      meta(s.brief, s.metadata)
    )

  private def relationshipCardinality(s: String)(using ctx: Ctx): RelationshipCardinality =
    s match
      case "1:1" | "OneToOne"   => RelationshipCardinality.OneToOne
      case "1:N" | "OneToMany"  => RelationshipCardinality.OneToMany
      case "N:1" | "ManyToOne"  => RelationshipCardinality.ManyToOne
      case "N:N" | "ManyToMany" => RelationshipCardinality.ManyToMany
      case other =>
        ctx.err(s"unknown relationship cardinality '$other' (expected 1:1|1:N|N:1|N:N)")
        RelationshipCardinality.OneToOne

  private def buildRelationship(r: RelationshipDto)(using Ctx): Relationship =
    Relationship(
      curAt,
      ident(r.name),
      processorRef(r.withProcessor, r.processor),
      relationshipCardinality(r.cardinality),
      r.label.map(LiteralString(curAt, _)),
      meta(r.brief, r.metadata)
    )

  private def buildProjector(p: ProjectorDto)(using Ctx): Projector =
    val types = p.types.map(buildType)
    val constants = p.constants.map(buildConstant)
    val commands = p.commands.map(m => buildMessage(m, AggregateUseCase.CommandCase))
    val events = p.events.map(m => buildMessage(m, AggregateUseCase.EventCase))
    val queries = p.queries.map(m => buildMessage(m, AggregateUseCase.QueryCase))
    val results = p.results.map(m => buildMessage(m, AggregateUseCase.ResultCase))
    val functions = p.functions.map(buildFunction)
    val handlers = p.handlers.map(buildHandler)
    val repoRefs = p.repository.toSeq.map(r => RepositoryRef(curAt, pathId(r)))
    val inlets = p.inlets.map(buildInlet)
    val outlets = p.outlets.map(buildOutlet)
    val version = p.version.map(buildVersion).toSeq
    val copyright = p.copyright.map(buildCopyright).toSeq
    val invariants = p.invariants.map(buildInvariant)
    val streamlets = p.streamlets.map(buildStreamlet)
    val connectors = p.connectors.map(buildConnector)
    val relationships = p.relationships.map(buildRelationship)
    Projector(
      curAt,
      ident(p.name),
      childrenOrBuckets[ProjectorContents](
        p.contents,
        "Projector",
        Legal.projectorW,
        contentsOf[ProjectorContents](
          types,
          constants,
          commands,
          events,
          queries,
          results,
          functions,
          handlers,
          repoRefs,
          inlets,
          outlets,
          version,
          copyright,
          invariants,
          streamlets,
          connectors,
          relationships,
          comments(p.comments)
        ),
        repoRefs
      ),
      ascribedShape = parseShape(p.shape),
      metadata = meta(p.brief, p.metadata)
    )

  private def schemaKind(s: Option[String])(using ctx: Ctx): RepositorySchemaKind =
    s match
      case None => RepositorySchemaKind.Other
      case Some(k) =>
        scala.util.Try(RepositorySchemaKind.valueOf(k)).toOption.getOrElse {
          ctx.err(s"unknown schema kind '$k'")
          RepositorySchemaKind.Other
        }

  private def buildSchema(s: SchemaDto)(using ctx: Ctx): Schema =
    val data = s.data.map { case (k, v) =>
      Identifier(curAt, k) -> TypeRef(curAt, "type", pathId(v))
    }
    val links = s.links.flatMap { case (k, fields) =>
      if fields.sizeIs >= 2 then
        Some(
          Identifier(curAt, k) -> (
            FieldRef(curAt, pathId(fields(0))),
            FieldRef(curAt, pathId(fields(1)))
          )
        )
      else
        ctx.err(s"schema link '$k' needs two field references")
        None
    }
    val indices = s.indices.map(f => FieldRef(curAt, pathId(f)))
    Schema(
      curAt,
      ident(s.name),
      schemaKind(s.kind),
      data,
      links,
      indices,
      meta(s.brief, s.metadata)
    )

  private def buildRepository(r: RepositoryDto)(using Ctx): Repository =
    val types = r.types.map(buildType)
    val commands = r.commands.map(m => buildMessage(m, AggregateUseCase.CommandCase))
    val events = r.events.map(m => buildMessage(m, AggregateUseCase.EventCase))
    val queries = r.queries.map(m => buildMessage(m, AggregateUseCase.QueryCase))
    val results = r.results.map(m => buildMessage(m, AggregateUseCase.ResultCase))
    val handlers = r.handlers.map(buildHandler)
    // Accept both the singular `schema` (back-compat) and plural `schemas`, as an entity accepts
    // both `state` and `states`. root2Json writes the plural and leaves the singular empty, so a
    // round trip cannot duplicate the first schema.
    val schemas = (r.schema.toSeq ++ r.schemas).map(buildSchema)
    val inlets = r.inlets.map(buildInlet)
    val outlets = r.outlets.map(buildOutlet)
    val version = r.version.map(buildVersion).toSeq
    val copyright = r.copyright.map(buildCopyright).toSeq
    val constants = r.constants.map(buildConstant)
    val functions = r.functions.map(buildFunction)
    val invariants = r.invariants.map(buildInvariant)
    val streamlets = r.streamlets.map(buildStreamlet)
    val connectors = r.connectors.map(buildConnector)
    val relationships = r.relationships.map(buildRelationship)
    Repository(
      curAt,
      ident(r.name),
      childrenOrBuckets[RepositoryContents](
        r.contents,
        "Repository",
        Legal.repositoryW,
        contentsOf[RepositoryContents](
          types,
          schemas,
          commands,
          events,
          queries,
          results,
          handlers,
          inlets,
          outlets,
          version,
          copyright,
          constants,
          functions,
          invariants,
          streamlets,
          connectors,
          relationships,
          comments(r.comments)
        )
      ),
      ascribedShape = parseShape(r.shape),
      metadata = meta(r.brief, r.metadata)
    )

  // ---------------------------------------------------------------------------
  // Type expressions (with the defaults table applied here, in the builder)
  // ---------------------------------------------------------------------------

  /** The use case a `RecordDto.aggregate` keyword names. An unrecognised or absent keyword reads as
    * `record`, matching the tolerance the rest of the builder shows hand-authored JSON.
    */
  private def aggregateUseCase(keyword: Option[String]): AggregateUseCase = keyword match
    case Some("type")    => AggregateUseCase.TypeCase
    case Some("graph")   => AggregateUseCase.GraphCase
    case Some("table")   => AggregateUseCase.TableCase
    case Some("command") => AggregateUseCase.CommandCase
    case Some("event")   => AggregateUseCase.EventCase
    case Some("query")   => AggregateUseCase.QueryCase
    case Some("result")  => AggregateUseCase.ResultCase
    case _               => AggregateUseCase.RecordCase

  private def buildTypeExpr(dto: TypeExprDto)(using ctx: Ctx): TypeExpression =
    dto match
      // AI-authored JSON may omit bounds; fill the canonical String(0,255)
      // defaults. root2Json emits these explicitly too, so json1==json2 holds.
      case StringDto(min, max) => String_(curAt, Some(min.getOrElse(0L)), Some(max.getOrElse(255L)))

      case IdDto(entity, keyword) =>
        entity match
          case Some(e) => UniqueId(curAt, pathId(e), keyword)
          case None =>
            ctx.err("Id type requires an 'entity' path (it cannot be defaulted)")
            UniqueId(curAt, PathIdentifier.empty, keyword)

      case PredefDto(kind) =>
        kind match
          case "UUID"        => UUID(curAt)
          case "Boolean"     => Bool(curAt)
          case "Date"        => Date(curAt)
          case "TimeStamp"   => TimeStamp(curAt)
          case "Integer"     => Integer(curAt)
          case "Whole"       => Whole(curAt)
          case "Natural"     => Natural(curAt)
          case "Number"      => Number(curAt)
          case "Real"        => Real(curAt)
          case "UserId"      => UserId(curAt)
          case "Anything"    => Anything(curAt)
          case "Abstract"    => Anything(curAt) // deprecated spelling of Anything
          case "Location"    => Location(curAt)
          case "Nothing"     => Nothing(curAt)
          case "Time"        => Time(curAt)
          case "DateTime"    => DateTime(curAt)
          case "Duration"    => Duration(curAt)
          case "Current"     => Current(curAt)
          case "Length"      => Length(curAt)
          case "Luminosity"  => Luminosity(curAt)
          case "Mass"        => Mass(curAt)
          case "Mole"        => Mole(curAt)
          case "Temperature" => Temperature(curAt)
          case other =>
            ctx.err(s"unknown predefined type kind '$other'")
            Anything(curAt)

      case DecimalDto(w, f)   => Decimal(curAt, w.getOrElse(12L), f.getOrElse(2L))
      case CurrencyDto(c)     => Currency(curAt, c.getOrElse("USD"))
      case RangeDto(min, max) => RangeType(curAt, min.getOrElse(0L), max.getOrElse(100L))

      case PatternDto(ps) =>
        if ps.isEmpty then
          ctx.err("Pattern type requires at least one regular expression")
          Pattern(curAt, Seq.empty)
        else Pattern(curAt, ps.map(LiteralString(curAt, _)))

      case EnumDto(es) =>
        if es.isEmpty then
          ctx.err("Enum type requires at least one value")
          Enumeration(curAt, Contents.empty[Enumerator]())
        else
          val enumerators =
            es.map(e => Enumerator(curAt, ident(e.name), e.value, Contents.empty[MetaData]()))
          Enumeration(curAt, Contents[Enumerator](enumerators*))

      case AlternationDto(of) =>
        val aliases = of.map(t => AliasedTypeExpression(curAt, "type", pathId(t)))
        Alternation(curAt, Contents[AliasedTypeExpression](aliases*))

      case RecordDto(fields, methods, cs, aggregate) =>
        val contents = contentsOf[AggregateContents](
          fields.map(buildField),
          methods.map(buildMethod),
          comments(cs)
        )
        // "aggregation" is the bare `{ ... }` that carries no keyword; every other flavour is a
        // use-case aggregate named by its RIDDL keyword. When the flavour is absent — which only
        // happens in hand-authored JSON, since JsonifierPass always writes it — a Record becomes a
        // proper RIDDL `record` (an aggregate tagged RecordCase) rather than a bare aggregation, so
        // that a `state ... of record X` reference resolves (ResolutionPass.handleTypeResolution).
        aggregate.map(_.toLowerCase) match
          case Some("aggregation") => Aggregation(curAt, contents)
          case flavour => AggregateUseCaseTypeExpression(curAt, aggregateUseCase(flavour), contents)

      case AliasDto(ref) => AliasedTypeExpression(curAt, "type", pathId(ref))

      case URIDto(scheme) => URI(curAt, scheme.map(LiteralString(curAt, _)))

      case BlobDto(blobKind) =>
        val bk = blobKind match
          case None => BlobKind.Text
          case Some(s) =>
            scala.util.Try(BlobKind.valueOf(s)).toOption.getOrElse {
              ctx.err(s"unknown blob kind '$s'")
              BlobKind.Text
            }
        Blob(curAt, bk)

      case ZonedDto(kind, zone) =>
        kind match
          case "ZonedDate"     => ZonedDate(curAt, zone.map(LiteralString(curAt, _)))
          case "ZonedDateTime" => ZonedDateTime(curAt, zone.map(LiteralString(curAt, _)))
          case other =>
            ctx.err(s"unknown zoned time kind '$other'")
            ZonedDateTime(curAt, zone.map(LiteralString(curAt, _)))

      case CollectionDto(kind, of) =>
        val inner = buildTypeExpr(of)
        kind match
          case "Sequence" => Sequence(curAt, inner)
          case "Set"      => Set(curAt, inner)
          case "Graph"    => Graph(curAt, inner)
          case "Replica"  => Replica(curAt, inner)
          case other =>
            ctx.err(s"unknown collection kind '$other'")
            Sequence(curAt, inner)

      case MappingDto(from, to) => Mapping(curAt, buildTypeExpr(from), buildTypeExpr(to))

      case TableDto(of, dimensions) => Table(curAt, buildTypeExpr(of), dimensions)

      case EntityRefDto(entity) =>
        entity match
          case Some(e) => EntityReferenceTypeExpression(curAt, pathId(e))
          case None =>
            ctx.err("EntityReference type requires an 'entity' path")
            EntityReferenceTypeExpression(curAt, PathIdentifier.empty)

      case CardinalityDto(card, of, min, max) =>
        val inner = buildTypeExpr(of)
        card match
          case "optional"   => Optional(curAt, inner)
          case "zeroOrMore" => ZeroOrMore(curAt, inner)
          case "oneOrMore"  => OneOrMore(curAt, inner)
          case "range"      => SpecificRange(curAt, inner, min.getOrElse(0L), max.getOrElse(1L))
          case other =>
            ctx.err(s"unknown cardinality '$other' (expected optional|zeroOrMore|oneOrMore|range)")
            inner
    end match
  end buildTypeExpr

end JsonAstBuilder
