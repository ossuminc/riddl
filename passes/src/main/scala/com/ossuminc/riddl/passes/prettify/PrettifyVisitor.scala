/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.passes.PassVisitor
import com.ossuminc.riddl.utils.PlatformContext

import scala.annotation.unused

class PrettifyVisitor(options: PrettifyPass.Options)(using PlatformContext) extends PassVisitor:
  val state: PrettifyState = PrettifyState(
    options.flatten,
    if options.topFile.nonEmpty then options.topFile
    else "prettify-output.riddl",
    if options.outputDir.nonEmpty then options.outputDir
    else ".",
    options.inputDir
  )

  def result: PrettifyState = state

  inline private def open(definition: Definition): Unit = state.withCurrent(_.openDef(definition))

  inline private def close(definition: Definition): Unit = state.withCurrent(_.closeDef(definition))

  def openType(typ: Type, parents: Parents): Unit =
    state.withCurrent { rfe =>
      rfe.openDef(typ, withBrace = false)
      rfe.emitTypeExpression(typ.typEx)
    }
  def closeType(typ: Type, parents: Parents): Unit =
    state.withCurrent { rfe =>
      if typ.metadata.isEmpty then rfe.nl
      else
        rfe.trimTrailingNewline()
        rfe.emitMetaData(typ.metadata)
    }

  // A Module is emitted as `module <id> is { ... }` so that parse -> prettify -> re-parse keeps
  // the wrapper (and everything flat inside it) exactly where it was.
  def openModule(module: Module, parents: Parents): Unit = open(module)
  def closeModule(module: Module, parents: Parents): Unit = close(module)

  def openDomain(domain: Domain, parents: Parents): Unit = open(domain)
  def closeDomain(domain: Domain, parents: Parents): Unit = close(domain)

  def openContext(context: Context, parents: Parents): Unit = open(context)
  def closeContext(context: Context, parents: Parents): Unit = close(context)

  def openEntity(entity: Entity, parents: Parents): Unit = open(entity)
  def closeEntity(entity: Entity, parents: Parents): Unit = close(entity)

  def openAdaptor(adaptor: Adaptor, parents: Parents): Unit =
    state.withCurrent { (rfe: RiddlFileEmitter) =>
      rfe
        .addIndent(keyword(adaptor))
        .add(" ")
        .add(adaptor.id.format)
        .add(" ")
        .add(adaptor.direction.format)
        .add(" ")
        .add(adaptor.referent.format)
        .add(adaptor.ascribedShape.map(s => s" as ${s.keyword}").getOrElse(""))
        .add(" is { ")
      if adaptor.isEmpty then rfe.emitUndefined().add(" }").nl
      else rfe.nl.incr
      end if
    }
  end openAdaptor

  def closeAdaptor(adaptor: Adaptor, parents: Parents): Unit = close(adaptor)

  def openEpic(epic: Epic, parents: Parents): Unit =
    state.withCurrent { rfe =>
      rfe
        .openDef(epic)
        .addLine(epic.userStory.format)
    }
  end openEpic
  def closeEpic(epic: Epic, parents: Parents): Unit = close(epic)

  def openUseCase(useCase: UseCase, parents: Parents): Unit =
    state.withCurrent { (rfe: RiddlFileEmitter) =>
      rfe.addIndent(s"${keyword(useCase)} ${useCase.id.format} is {").nl.incr
      rfe.addLine(useCase.userStory.format)
      if useCase.isEmpty then rfe.addLine("???")
    }
  end openUseCase

  def closeUseCase(useCase: UseCase, parents: Parents): Unit =
    state.withCurrent { (rfe: RiddlFileEmitter) =>
      rfe.decr.addIndent("}")
      rfe.emitMetaData(useCase.metadata)
      if useCase.metadata.isEmpty then rfe.nl
    }
  end closeUseCase

  def openFunction(function: Function, parents: Parents): Unit =
    state.withCurrent { rfe =>
      rfe.addIndent(s"${keyword(function)} ${function.id.format} is { ").nl.incr
    }
  end openFunction

  def closeFunction(function: Function, parents: Parents): Unit =
    state.withCurrent { rfe =>
      rfe.decr.addIndent("}")
      rfe.emitMetaData(function.metadata)
      if function.metadata.isEmpty then rfe.nl
    }
  end closeFunction

  def openSaga(saga: Saga, parents: Parents): Unit = open(saga)
  def closeSaga(saga: Saga, parents: Parents): Unit = close(saga)

  def openStreamlet(streamlet: Streamlet, parents: Parents): Unit = open(streamlet)
  def closeStreamlet(streamlet: Streamlet, parents: Parents): Unit = close(streamlet)

  def openRepository(repository: Repository, parents: Parents): Unit = open(repository)
  def closeRepository(repository: Repository, parents: Parents): Unit = close(repository)

  /** A projector's `updates repository <path>` clause is a [[RepositoryRef]] in its contents, not a
    * Definition, so nothing emits it on our behalf. It names the repository that persists the
    * projection — semantic content, and validation reports the projector as incomplete without it —
    * so it has to be written out here or every round trip loses it.
    */
  def openProjector(projector: Projector, parents: Parents): Unit =
    open(projector)
    state.withCurrent { rfe =>
      projector.contents.filter[RepositoryRef].foreach { ref =>
        rfe.addIndent(s"updates ${ref.format}").nl
      }
    }
  def closeProjector(projector: Projector, parents: Parents): Unit = close(projector)

  def openHandler(handler: Handler, parents: Parents): Unit = open(handler)

  def closeHandler(handler: Handler, parents: Parents): Unit = close(handler)

  def openOnClause(onClause: OnClause, parents: Parents): Unit = open(onClause)

  def closeOnClause(onClause: OnClause, parents: Parents): Unit = close(onClause)

  def openGroup(group: Group, parents: Parents): Unit = open(group)

  def closeGroup(group: Group, parents: Parents): Unit = close(group)

  def openOutput(output: Output, parents: Parents): Unit =
    state.withCurrent { rfe =>
      // Use id.format (not identify, which already prepends verbAlias) so the
      // rendered `<nounAlias> <id> <verbAlias> <putOut>` re-parses. Mirrors
      // openInput.
      rfe
        .addIndent(output.nounAlias)
        .add(" ")
        .add(output.id.format)
        .add(" ")
        .add(output.verbAlias)
        .add(" ")
        .add(output.putOut.format)
      // Leave the line unterminated: closeOutput decides between a contents block, a `with`
      // metadata block, or neither.
      if output.nonEmpty then rfe.add(" {").nl.incr
    }
  end openOutput

  def closeOutput(output: Output, parents: Parents): Unit =
    state.withCurrent { rfe =>
      if output.nonEmpty then rfe.decr.addIndent("}")
      // A42: an Output's metadata (a `figma` reference, a `briefly`, ...) was previously dropped
      // here, so it could not survive a round-trip. Emit it, and note that an Output with no
      // contents still needs it -- hence the metadata emission is independent of `nonEmpty`.
      if output.metadata.nonEmpty then rfe.emitMetaData(output.metadata) else rfe.nl
    }
  end closeOutput

  def openInput(input: Input, parents: Parents): Unit =
    state.withCurrent { rfe =>
      // form Identity takes record Whatever.Identity is { .. }
      rfe
        .addIndent(input.nounAlias)
        .add(" ")
        .add(input.id.format)
        .add(" ")
        .add(input.verbAlias)
        .add(" ")
        .add(input.takeIn.format)
      // Leave the line unterminated: closeInput decides between a contents block, a `with`
      // metadata block, or neither.
      if input.nonEmpty then rfe.add(" {").nl.incr
    }
  end openInput

  def closeInput(input: Input, parents: Parents): Unit =
    state.withCurrent { rfe =>
      if input.nonEmpty then rfe.decr.addIndent("}")
      // A42: an Input's metadata (a `figma` reference, a `briefly`, ...) was previously dropped
      // here, so it could not survive a round-trip. Emit it, and note that an Input with no
      // contents still needs it -- hence the metadata emission is independent of `nonEmpty`.
      if input.metadata.nonEmpty then rfe.emitMetaData(input.metadata) else rfe.nl
    }
  end closeInput

  // Close for each type of container definition

  // LeafDefinitions
  def doField(field: Field): Unit = ()
  // NOTE: Fields are handled by their type
  end doField

  def doMethod(method: Method): Unit = ()
  // NOTE: Methods are handled by their type -- `RiddlFileEmitter.emitAggregateContents` walks the
  // aggregate and calls `emitMethod`, which is what keeps a method in the right position among its
  // siblings.
  //
  // This comment asserted the same thing before 2026-08-14, when it was simply UNTRUE: the emitter
  // walked `.fields` only, `emitMethod` had zero callers anywhere in the repo, and every `method`
  // was dropped from prettified output. A no-op hook justified by a claim about code elsewhere is
  // only as good as that claim, and nothing was checking this one.
  end doMethod

  def doAuthor(author: Author): Unit =
    state.withCurrent { rfe =>
      rfe
        .addLine(s"author ${author.id.format} is {")
        .incr
        .addIndent(s"name = ${author.name.format}\n")
        .addIndent(s"email = ${author.email.format}\n")
      author.organization.map(org => rfe.addIndent(s"organization =${org.format}\n"))
      author.title.map(title => rfe.addIndent(s"title = ${title.format}\n"))
      rfe.decr.addIndent("}")
      rfe.emitMetaData(author.metadata)
      if author.metadata.isEmpty then rfe.nl
    }
  end doAuthor

  def doConstant(constant: Constant): Unit =
    state.withCurrent { rfe => rfe.emitConstant(constant) }
  end doConstant

  def doInvariant(invariant: Invariant): Unit =
    state.withCurrent { rfe =>
      rfe
        .addIndent("invariant ")
        .add(invariant.id.format)
        // `requires` decides WHERE the invariant applies, so dropping it changes the model rather
        // than its formatting: `requires state Open` would silently widen to the whole entity.
        .add(invariant.requires.map(r => " requires " + r.format).getOrElse(""))
        .add(" is ")
      // A28 + block form: `condition` is `LiteralString | BooleanExpression | InvariantBlock`. A
      // `BooleanExpression` routes through `emitValue` (not `.format`), since its `LogicalExpression`/
      // `NotExpression`/`InvariantCondition` shapes can nest a `PromptValue` whose ascription needs
      // `emitValue`'s total dispatch — see `RiddlFileEmitter.emitValue`'s doc. `InvariantBlock` routes
      // through `emitInvariantBlock`, which (2026-08-15, Reid's ruling) now renders its `statements`
      // via `emitStatement` and its `predicate` via `emitValue` — the same multi-line, one-per-line
      // convention every other statement block in this emitter uses, and the same ascription fix as
      // everywhere else. See `emitInvariantBlock`'s doc for why the prior single-line rendering was
      // never a deliberate choice for this construct.
      invariant.condition match
        case None                        => rfe.add("N/A")
        case Some(ls: LiteralString)     => rfe.add(ls.format)
        case Some(be: BooleanExpression) => rfe.emitValue(be)
        case Some(ib: InvariantBlock)    => rfe.emitInvariantBlock(ib)
      // An invariant is a leaf with nothing following it on the line, so it must terminate its own
      // line when there is no `with { ... }` block to do it — the same rule `doVersion` and
      // `doCopyright` already carry. Without this a metadata-less invariant ran into whatever
      // followed it, which is why `invariant X is a >= b      // comment` and `... }      handler H
      // is {` came out on one line. The block form's own closing `}` (from `emitInvariantBlock`)
      // ends without a trailing newline for exactly this reason — this is where it gets added.
      if invariant.metadata.isEmpty then rfe.nl else rfe.emitMetaData(invariant.metadata)
    }
  end doInvariant

  // A53: `version <name>` or `version <number>`.
  def doVersion(version: Version): Unit =
    state.withCurrent { rfe =>
      rfe.addIndent("version ")
      // A NUMERIC component must emit as bare digits: Identifier.format would quote "4" as '4',
      // which re-parses as a NAMED component and silently loses the numeric form. A NAMED
      // component goes through Identifier.format so names needing quotes ('Jammy Jellyfish')
      // survive the round trip.
      rfe.add(version.number.map(_.toString).getOrElse(version.id.format))
      // A version is a one-line leaf with nothing following it on the line, so it must terminate
      // its own line when there is no `with { ... }` block to do it.
      if version.metadata.isEmpty then rfe.nl else rfe.emitMetaData(version.metadata)
    }
  end doVersion

  // A47: `copyright <name> is "<notice>"`.
  def doCopyright(copyright: Copyright): Unit =
    state.withCurrent { rfe =>
      rfe.addIndent("copyright ").add(copyright.id.format).add(" is ").add(copyright.text.format)
      // A copyright is a one-line leaf with nothing following it on the line, so it must terminate
      // its own line when there is no `with { ... }` block to do it.
      if copyright.metadata.isEmpty then rfe.nl else rfe.emitMetaData(copyright.metadata)
    }
  end doCopyright

  def doSagaStep(sagaStep: SagaStep): Unit =
    state.withCurrent { rfe =>
      rfe
        .openDef(sagaStep, withBrace = false)
        .emitCodeBlock(sagaStep.doStatements.toSeq)
        .addIndent("reverted by")
        .emitCodeBlock(sagaStep.undoStatements.toSeq)
      rfe.emitMetaData(sagaStep.metadata)
    }
  end doSagaStep

  def doInlet(inlet: Inlet): Unit =
    state.withCurrent { rfe =>
      rfe.addIndent(inlet.format)
      rfe.emitMetaData(inlet.metadata)
    }
  end doInlet

  def doOutlet(outlet: Outlet): Unit =
    state.withCurrent { rfe =>
      rfe.addLine(outlet.format)
      rfe.emitMetaData(outlet.metadata)
    }
  end doOutlet

  def doConnector(connector: Connector): Unit =
    state.withCurrent { rfe =>
      // Declaration.prefix renders the intentions in CANONICAL order, so a round trip converges
      // regardless of how they were written -- and a deprecated `option persistent`, consumed into
      // an intention by the parser, comes back out here as the keyword.
      rfe.addIndent(
        s"${Declaration.prefix(connector)}${keyword(connector)} ${connector.id.format} is "
      )
      rfe
        .add {
          val from =
            if connector.from.nonEmpty then s"from ${connector.from.format} " else "from empty "
          val to = if connector.to.nonEmpty then s"to ${connector.to.format}" else "to empty"
          from + to
        }
        .emitMetaData(connector.metadata)
    }
  end doConnector

  def doUser(user: User): Unit =
    state.withCurrent { rfe =>
      rfe
        .addIndent(s"user ${user.id.format} is \"${user.is_a.s}\"")
        .emitMetaData(user.metadata)
    }
  end doUser

  def doSchema(schema: Schema): Unit =
    state.withCurrent { rfe =>
      rfe
        .addIndent("schema ")
        .add(schema.id.format)
        .add(" is ")
        .emitSchemaKind(schema.schemaKind)
        .nl
      rfe.incr
      schema.data.toSeq.sortBy(_._1.value).foreach { (id: Identifier, typeRef: TypeRef) =>
        rfe.addIndent("of ").add(id.format).add(" as ").add(typeRef.format).nl
      }
      rfe.incr
      schema.links.toSeq.sortBy(_._1.value).foreach { (id: Identifier, tr: (FieldRef, FieldRef)) =>
        rfe
          .addIndent("link ")
          .add(id.format)
          .add(" as ")
          .add(tr._1.format)
          .add(" to ")
          .add(tr._2.format)
          .nl
      }
      schema.indices.foreach { fieldRef =>
        rfe.addIndent("index on ").add(fieldRef.format).nl
      }
      rfe.decr.decr
      rfe.emitMetaData(schema.metadata)
      if schema.metadata.isEmpty then rfe.nl
    }
  end doSchema

  def openState(riddl_state: State, parents: Parents): Unit =
    state.withCurrent { rfe =>
      // Shared with `State.format` and `openDef` -- one implementation of the declaration.
      val prefix = Declaration.prefix(riddl_state)
      if riddl_state.contents.isEmpty then
        rfe.addLine(
          s"$prefix${keyword(riddl_state)} ${riddl_state.id.format} of ${riddl_state.typ.format}"
        )
      else {
        // `of` introduces the record reference and `is` introduces the BODY — the same division of
        // labour every other definition uses. Emitting `is` for the record reference here made
        // every state WITH A BODY prettify to the deprecated spelling, so a prettified model came
        // back with a deprecation on every one of them. (The body-less case above was always
        // right, which is how this stayed hidden.)
        rfe
          .addLine(
            s"$prefix${keyword(riddl_state)} ${riddl_state.id.format} of " +
              s"${riddl_state.typ.format} is {"
          )
          .incr
      }
      end if
    }
  end openState

  /** A70. `correlation C by k1, k2 yields command X is {` — the whole declaration, since everything
    * before the body must survive a round trip.
    *
    * The `command` keyword is not spelled here: `AggregateRef.format` derives it from the ref's
    * `messageKind`, which is why the 2026-08-12 record→command change needed no edit to this line.
    *
    * Keys are emitted in their STORED order, which the parser kept as written: §6.5 makes identity
    * the full tuple and forbids canonicalizing, so re-ordering them here would silently change what
    * the model declares.
    */
  def openCorrelation(correlation: Correlation, parents: Parents): Unit =
    state.withCurrent { rfe =>
      val keys = correlation.keys.map(_.format).mkString(", ")
      rfe
        .addLine(
          s"${keyword(correlation)} ${correlation.id.format} by $keys " +
            s"yields ${correlation.yields.format} is {"
        )
        .incr
    }
  end openCorrelation

  /** A70. Closes the fold body, then emits the mandatory timeout clause.
    *
    * The timeout lives in FIELDS (`timeout`, `timeoutStatements`) rather than in `contents`, so
    * nothing emits it unless this does — the same reason `doSagaStep` emits `reverted by` itself.
    * That is the A57 trap in a different disguise: a declaration rendered only via `format` and not
    * here is silently DROPPED on every round trip, and the model comes back meaning something else.
    * `CorrelationRoundTripTest` is what holds this honest.
    */
  def closeCorrelation(correlation: Correlation, parents: Parents): Unit =
    state.withCurrent { rfe =>
      rfe.decr
        .addIndent("}")
        .add(s" times out after ${correlation.timeout.format}")
        .emitCodeBlock(correlation.timeoutStatements.toSeq)
      rfe.emitMetaData(correlation.metadata)
      if correlation.metadata.isEmpty then rfe.nl
    }
  end closeCorrelation

  def closeState(riddl_state: State, parents: Parents): Unit =
    state.withCurrent { rfe =>
      if riddl_state.contents.nonEmpty then rfe.closeDef(riddl_state)
      else
        // A body-less state has no brace to close, but it may still carry metadata —
        // `state X of record R with { briefly ... }` is legal and common. `closeDef` emits BOTH
        // the brace and the metadata, so guarding the whole call on `contents.nonEmpty` silently
        // dropped the metadata of every body-less state. Emit it on its own here.
        if riddl_state.metadata.nonEmpty then
          rfe.trimTrailingNewline()
          rfe.emitMetaData(riddl_state.metadata)
        end if
      end if
    }
  end closeState

  def doRelationship(rel: com.ossuminc.riddl.language.AST.Relationship): Unit =
    state.withCurrent { rfe =>
      rfe.addIndent(
        s"${keyword(rel)} ${rel.id.format} to ${rel.withProcessor.format} as ${rel.cardinality.proportion}"
      )
      if rel.label.nonEmpty then rfe.add(s" label as ${rel.label.format}")
      end if
      rfe.nl
    }
  end doRelationship

  def doEnumerator(enumerator: Enumerator): Unit =
    () // Note: Handled by RiddlFileEmitter.emitEnumeration

  def doContainedGroup(containedGroup: ContainedGroup): Unit =
    state.withCurrent { rfe =>
      rfe
        .addIndent(s"${keyword(containedGroup)} ${containedGroup.id.format} as ")
        .add(containedGroup.group.format)
        .emitMetaData(containedGroup.metadata)
    }
  end doContainedGroup

  // Non Definition values
  def doComment(comment: Comment): Unit =
    state.withCurrent(_.emitComment(comment))
  end doComment

  /** `requires`/`returns` are emitted HERE, from the contents, not from `openFunction`/`openSaga`
    * via the `input`/`output` accessors as they were before the clauses became content.
    *
    * Emitting them from the accessors would reimpose the very ordering the move removed: they would
    * always be printed first, so a comment the author wrote above `requires` would come out below
    * it — a round trip that changes the document. Order is now a property of the AST rather than of
    * the printer, which is also why this method got simpler instead of smarter.
    *
    * A9: the value is a [[TypeRef]] (preferred) or a deprecated inline [[Aggregation]].
    */
  private def emitRequiresReturns(kw: String, value: TypeRef | Aggregation): Unit =
    state.withCurrent { rfe =>
      value match
        case tr: TypeRef      => rfe.addIndent(kw).add(tr.format).nl
        case agg: Aggregation => rfe.addIndent(kw).emitAggregation(agg)
      end match
    }
  end emitRequiresReturns

  def doRequires(requires: Requires): Unit = emitRequiresReturns("requires ", requires.what)
  def doReturns(returns: Returns): Unit = emitRequiresReturns("returns  ", returns.what)

  def doShownBy(shownBy: ShownBy): Unit =
    state.withCurrent { rfe =>
      rfe.emitShownBy(shownBy)
    }
  end doShownBy

  def doAuthorRef(authorRef: AuthorRef): Unit =
    state.withCurrent { rfe =>
      rfe.emitAuthorRef(authorRef)
    }
  end doAuthorRef

  def doBriefDescription(brief: BriefDescription): Unit = ()
  // state.withCurrent { rfe =>
  //   rfe.emitBriefDescription(brief)
  // }
  end doBriefDescription

  def doDescription(description: Description): Unit = ()
  // state.withCurrent { rfe =>
  //   rfe.emitDescription(description)
  // }
  end doDescription

  def doStatement(statement: Statements): Unit =
    state.withCurrent { rfe => rfe.emitStatement(statement) }
  end doStatement

  def doInteraction(interaction: Interaction): Unit =
    state.withCurrent { rfe =>
      interaction match
        case si: SequentialInteractions =>
          rfe.addIndent("sequence {").nl.incr
          emitInteractionContents(si.contents)
          rfe.decr.addIndent("}")
        case pi: ParallelInteractions =>
          rfe.addIndent("parallel {").nl.incr
          emitInteractionContents(pi.contents)
          rfe.decr.addIndent("}")
        case oi: OptionalInteractions =>
          rfe.addIndent("optional {").nl.incr
          emitInteractionContents(oi.contents)
          rfe.decr.addIndent("}")
        case vi: VagueInteraction =>
          rfe.addIndent(s"step is ${vi.from.format} ${vi.relationship.format} ${vi.to.format}")
        case smi: SendMessageInteraction =>
          rfe.addIndent(
            s"step send ${smi.message.format} from ${smi.from.format} to ${smi.to.format}"
          )
        case ai: ArbitraryInteraction =>
          rfe.addIndent(s"step from ${ai.from.format} ${ai.relationship.format} to ${ai.to.format}")
        case si: SelfInteraction =>
          rfe.addIndent(s"step for ${si.from.format} is ${si.relationship.format}")
        case fi: FocusOnGroupInteraction =>
          rfe.addIndent(s"step focus ${fi.from.format} on ${fi.to.format}")
        case du: DirectUserToURLInteraction =>
          rfe.addIndent(s"step direct ${du.from.format} to ${du.url.toExternalForm}")
        case so: ShowOutputInteraction =>
          rfe.addIndent(s"step show ${so.from.format} to ${so.to.format}")
        case si: SelectInputInteraction =>
          rfe.addIndent(s"step ${si.from.format} selects ${si.to.format}")
        case ti: TakeInputInteraction =>
          rfe.addIndent(s"step take ${ti.to.format} from ${ti.from.format}")
        case ri: RefusalInteraction =>
          rfe.addIndent(s"step ${ri.from.format} refuses ${ri.to.format} ${ri.reason.format}")
      end match
      rfe.emitMetaData(interaction.metadata)
      if interaction.metadata.isEmpty then rfe.nl
    }
  end doInteraction

  private def emitInteractionContents(contents: Contents[InteractionContainerContents]): Unit =
    contents.toSeq.foreach {
      case i: Interaction => doInteraction(i)
      case c: Comment     => doComment(c)
    }
  end emitInteractionContents

  def doOptionValue(option: OptionValue): Unit =
    state.withCurrent { rfe =>
      rfe.emitOption(option)
    }
  end doOptionValue

  def openInclude(include: Include[?], parents: Parents): Unit =
    state.withCurrent { (rfe: RiddlFileEmitter) =>
      if !state.flatten then
        val url = include.origin
        // Use url.path (the relative portion of the include origin)
        // to compute the include directive text relative to the
        // current output file
        val currentDir = rfe.url.path.lastIndexOf('/') match
          case -1 => ""
          case i  => rfe.url.path.substring(0, i)
        val relativePath =
          if currentDir.isEmpty then url.path
          else if url.path.startsWith(currentDir + "/") then url.path.drop(currentDir.length + 1)
          else url.path
        rfe.addLine(s"""include "$relativePath"""")
        // Resolve the relative path from the current file's output
        // URL to construct the RFE URL with correct output path
        val newRFE = RiddlFileEmitter(rfe.url.resolve(relativePath))
        state.pushFile(newRFE)
      end if
    }
  end openInclude

  def closeInclude(@unused include: Include[?], parents: Parents): Unit =
    if !state.flatten then state.popFile()
    end if
  end closeInclude

  def openBASTImport(bi: BASTImport, parents: Parents): Unit =
    state.withCurrent { (rfe: RiddlFileEmitter) =>
      if !state.flatten then
        // Emit the import directive in the current file
        // NOTE: "im" + "port" split to avoid ESM shim rewriting
        rfe.addLine("im" + "port " + "\"" + bi.path.s + "\"")
        // Track this BASTImport for re-serialization in writeOutput
        state.addBASTImport(bi)
      end if
    }
  end openBASTImport

  def closeBASTImport(@unused bi: BASTImport, parents: Parents): Unit = ()
  end closeBASTImport
end PrettifyVisitor

/** A function to translate between a definition and the keyword that introduces them.
  *
  * @param definition
  *   The definition to look up
  * @return
  *   A string providing the definition keyword, if any. Enumerators and fields don't have their own
  *   keywords
  */
def keyword(definition: Definition): String =
  definition match
    case _: Adaptor     => Keyword.adaptor
    case _: UseCase     => Keyword.case_
    case _: Context     => Keyword.context
    case _: Connector   => Keyword.connector
    case _: Domain      => Keyword.domain
    case _: Entity      => Keyword.entity
    case _: Enumerator  => ""
    case _: Field       => ""
    case _: Function    => Keyword.function
    case group: Group   => group.alias
    case input: Input   => input.nounAlias
    case output: Output => output.nounAlias
    case _: Handler     => Keyword.handler
    case _: Module      => Keyword.module
    case _: Inlet       => Keyword.inlet
    case _: Invariant   => Keyword.invariant
    case _: Version     => Keyword.version
    case _: Copyright   => Keyword.copyright
    case _: Outlet      => Keyword.outlet
    case s: Streamlet   => s.effectiveShape.keyword
    case _: Root        => "root"
    case _: Saga        => Keyword.saga
    case _: SagaStep    => Keyword.step
    case _: State       => Keyword.state
    case _: Correlation => Keyword.correlation
    case _: Epic        => Keyword.epic
    case _: Term        => Keyword.term
    case typ: Type =>
      typ.typEx match
        case AggregateUseCaseTypeExpression(_, useCase, _, _) =>
          useCase match
            case AggregateUseCase.CommandCase => Keyword.command
            case AggregateUseCase.EventCase   => Keyword.event
            case AggregateUseCase.QueryCase   => Keyword.query
            case AggregateUseCase.ResultCase  => Keyword.result
            case AggregateUseCase.RecordCase  => Keyword.record
            case AggregateUseCase.TypeCase    => Keyword.type_
            case AggregateUseCase.GraphCase   => Keyword.graph
            case AggregateUseCase.TableCase   => Keyword.table
          end match
        case _ => Keyword.type_
      end match
    case _: ContainedGroup => Keyword.contains
    case _: OnClause       => Keyword.on
    case _: Projector      => Keyword.projector
    case _: Repository     => Keyword.repository
    case _: Relationship   => Keyword.relationship
    case _: Definition     => "unknown"
  end match
end keyword
