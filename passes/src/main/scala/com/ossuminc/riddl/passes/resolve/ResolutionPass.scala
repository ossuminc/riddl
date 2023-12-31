/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.{Entity, *}
import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.language.{At, CommonOptions, Messages}
import com.ossuminc.riddl.passes.{Pass, PassInfo, PassInput, PassOutput, PassesOutput}
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}

import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

case class ResolutionOutput(
  messages: Messages.Messages = Messages.empty,
  refMap: ReferenceMap = ReferenceMap.empty,
  kindMap: KindMap = KindMap.empty,
  usage: Usages = Usages.empty
) extends PassOutput {}

object ResolutionPass extends PassInfo {
  val name: String = "Resolution"
}

/** The Reference Resolution Pass */
@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
case class ResolutionPass(input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) with UsageResolution {

  override def name: String = ResolutionPass.name

  requires(SymbolsPass)

  val commonOptions: CommonOptions = input.commonOptions
  val refMap: ReferenceMap = ReferenceMap(messages)
  val kindMap: KindMap = KindMap()
  val symbols: SymbolsOutput = outputs.outputOf[SymbolsOutput](SymbolsPass.name).get

  override def result: ResolutionOutput =
    ResolutionOutput(messages.toMessages, refMap, kindMap, Usages(uses, usedBy))

  override def close(): Unit = ()

  def postProcess(root: Root): Unit = {
    checkUnused()
  }

  def process(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    kindMap.add(definition)
    val parentsAsSeq: Seq[Definition] = definition +: parents.toSeq
    definition match {
      case ad: AggregateValue =>
        resolveTypeExpression(ad.typeEx, parentsAsSeq)
      case t: Type =>
        resolveType(t, parentsAsSeq)
      case mc: OnMessageClause =>
        resolveOnMessageClause(mc, parentsAsSeq)
      case ic: OnInitClause =>
        resolveOnClauses(ic, parentsAsSeq)
      case tc: OnTerminationClause =>
        resolveOnClauses(tc, parentsAsSeq)
      case oc: OnOtherClause =>
        resolveOnClauses(oc, parentsAsSeq)
      case e: Entity =>
        e.authorRefs.foreach(resolveARef[Author](_, parentsAsSeq))
        addEntity(e)
      case s: State =>
        resolveATypeRef(s.typ, parentsAsSeq)
      case f: Function =>
        resolveFunction(f, parentsAsSeq)
      case i: Inlet =>
        resolveATypeRef(i.type_, parentsAsSeq)
      case o: Outlet =>
        resolveATypeRef(o.type_, parentsAsSeq)
      case c: Connector =>
        resolveConnector(c, parentsAsSeq)
      case c: Constant =>
        resolveTypeExpression(c.typeEx, parentsAsSeq)
      case a: Adaptor =>
        resolveARef[Context](a.context, parentsAsSeq)
        a.authorRefs.foreach(resolveARef[Author](_, parentsAsSeq))
      case s: Streamlet =>
        s.authorRefs.foreach(resolveARef[Author](_, parentsAsSeq))
      case p: Projector =>
        p.authorRefs.foreach(resolveARef[Author](_, parentsAsSeq))
      case r: Repository =>
        r.authorRefs.foreach(resolveARef[Author](_, parentsAsSeq))
      case s: Saga =>
        s.authorRefs.foreach(resolveARef[Author](_, parentsAsSeq))
      case d: Domain =>
        d.authorRefs.foreach(resolveARef[Author](_, parentsAsSeq))
      case a: Application =>
        a.authorRefs.foreach(resolveARef[Author](_, parentsAsSeq))
      case c: Context =>
        c.authorRefs.foreach(resolveARef[Author](_, parentsAsSeq))
      case e: Epic =>
        e.authorRefs.foreach(resolveARef[Author](_, parentsAsSeq))
      case uc: UseCase =>
        if uc.userStory.nonEmpty then resolveARef(uc.userStory.user, parentsAsSeq)
        end if
        if uc.contents.nonEmpty then
          resolveInteractions(uc.contents, parentsAsSeq)
      case in: Input =>
        resolveATypeRef(in.putIn, parentsAsSeq)
      case out: Output =>
        out.putOut match {
          case typ: TypeRef       => resolveATypeRef(typ, parentsAsSeq)
          case const: ConstantRef => resolveARef[Constant](const, parentsAsSeq)
          case _: LiteralString   => () // not a reference
        }
      case cg: ContainedGroup =>
        resolveARef[Group](cg.group, parentsAsSeq)

      case _: Author                 => () // no references
      case _: User                   => () // no references
      case _: Enumerator             => () // no references
      case _: Group                  => () // no references
      case _: Root                   => () // no references
      case _: SagaStep               => () // no references
      case _: Term                   => () // no references
      case _: Handler                => () // no references
      case _: Invariant              => () // no references
      // case _ => () // NOTE: Never have this catchall! Want compile time errors.
    }
  }

  private def resolveFunction(f: Function, parents: Seq[Definition]): Unit = {
    f.authorRefs.foreach(resolveARef[Author](_, parents))
    addFunction(f)
    f.input.foreach(resolveTypeExpression(_, parents))
    f.output.foreach(resolveTypeExpression(_, parents))
  }

  private def resolveConnector(connector: Connector, parents: Seq[Definition]): Unit = {
    resolveMaybeRef[Type](connector.flows, parents)
    resolveMaybeRef[Outlet](connector.from, parents)
    resolveMaybeRef[Inlet](connector.to, parents)
  }

  private def resolveType(typ: Type, parents: Seq[Definition]): Unit = {
    addType(typ)
    resolveTypeExpression(typ.typ, parents)
  }

  private def resolveTypeExpression(typ: TypeExpression, parents: Seq[Definition]): Unit = {
    typ match {
      case UniqueId(_, entityPath) =>
        resolveAPathId[Entity](entityPath, parents)
      case AliasedTypeExpression(_, _, pathId) =>
        resolveAPathId[Type](pathId, parents)
      case agg: AggregateTypeExpression =>
        agg.fields.foreach { (fld: Field) =>
          resolveTypeExpression(fld.typeEx, fld +: parents)
        }
      case EntityReferenceTypeExpression(_, entity) =>
        resolveAPathId[Entity](entity, parents)
      case Alternation(_, of) =>
        of.foreach(resolveTypeExpression(_, parents))
      case Sequence(_, of) =>
        resolveTypeExpression(of, parents)
      case Mapping(_, from, _) =>
        resolveTypeExpression(from, parents)
      case Set(_, of) =>
        resolveTypeExpression(of, parents)
      case Graph(_, of) =>
        resolveTypeExpression(of, parents)
      case Table(_, of, _) =>
        resolveTypeExpression(of, parents)
      case Replica(_, of) => 
        resolveTypeExpression(of, parents)
      case c: Cardinality =>
        resolveTypeExpression(c.typeExp, parents)
      case _: Enumeration | _: NumericType | _: PredefinedType => ()
    }
  }

  private def resolveOnMessageClause(mc: OnMessageClause, parents: Seq[Definition]): Unit = {
    resolveARef[Type](mc.msg, parents)
    mc.from match
      case None => ()
      case Some(_, reference) =>
        resolveARef[Definition](reference, parents)
    resolveStatements(mc.statements, parents)
  }

  private def resolveOnClauses(oc: OnClause, parents: Seq[Definition]): Unit = {
    resolveStatements(oc.statements, parents)
  }

  private def resolveStatements(statements: Seq[Statement], parents: Seq[Definition]): Unit = {
    statements.foreach(resolveStatement(_, parents))
  }

  private def resolveStatement(statement: Statement, parents: Seq[Definition]): Unit = {
    statement match {
      case ss: SetStatement =>
        resolveARef[Field](ss.field, parents)
      case BecomeStatement(_, entity, _) =>
        resolveARef[Entity](entity, parents)
      case FocusStatement(_, group) =>
        resolveARef[Group](group, parents)
      case ForEachStatement(_, pid, _) =>
        resolveAPathId[Type](pid, parents)
      case SendStatement(_, msg, _) =>
        resolveARef[Type](msg, parents)
      case MorphStatement(_, entity, state, message) =>
        resolveARef[Entity](entity, parents)
        resolveARef[State](state, parents)
        resolveARef[Type](message, parents)
      case TellStatement(_, msg, processorRef) =>
        resolveARef[Type](msg, parents)
        resolveARef[Processor[?, ?]](processorRef, parents)
      case CallStatement(_, func) =>
        resolveARef[Function](func, parents)
      case ReplyStatement(_, message) =>
        resolveARef[Type](message, parents)
      case _: ArbitraryStatement  => () // no references
      case _: ErrorStatement      => () // no references
      case _: ReturnStatement     => () // no references
      case _: IfThenElseStatement => () // no references
      case _: StopStatement       => () // no references
    }
  }

  private def resolveInteractions(
   interactions: Seq[Interaction | Comment],
   parentsAsSeq: Seq[Definition]
  ): Unit = {
    for interaction <- interactions do {
      interaction match {
        case ArbitraryInteraction(_, from, _, to, _, _) =>
          resolveARef[Definition](from, parentsAsSeq)
          resolveARef[Definition](to, parentsAsSeq)
        case fi: FocusOnGroupInteraction =>
          resolveARef[User](fi.from, parentsAsSeq)
          resolveARef[Group](fi.to, parentsAsSeq)
        case fou: DirectUserToURLInteraction =>
          resolveARef[User](fou.from, parentsAsSeq)
        case ti: ShowOutputInteraction =>
          resolveARef[User](ti.to, parentsAsSeq)
          resolveARef[Output](ti.from, parentsAsSeq)
        case pi: TakeInputInteraction =>
          resolveARef[User](pi.from, parentsAsSeq)
          resolveARef[Input](pi.to, parentsAsSeq)
        case si: SelfInteraction =>
          resolveARef[Definition](si.from, parentsAsSeq)
        case SendMessageInteraction(_, from, message, to, _, _) =>
          resolveARef[Definition](from, parentsAsSeq)
          resolveAMessageRef(message, parentsAsSeq)
          resolveARef[Definition](to, parentsAsSeq)
        case _: VagueInteraction => () // no resolution required
        case _: OptionalInteractions => () // no references
        case _: ParallelInteractions => () // no references
        case _: SequentialInteractions => () // no references
        case _: Comment => () // no references
      }
    }
  }

  private def resolveMaybeRef[T <: Definition: ClassTag](
    maybeRef: Option[Reference[T]],
    parents: Seq[Definition]
  ): Unit = {
    maybeRef match {
      case Some(ref: Reference[T]) =>
        resolveARef[T](ref, parents)
      case None => ()
    }
  }

  private def resolveARef[T <: Definition: ClassTag](
    ref: Reference[T],
    parents: Seq[Definition]
  ): Unit = {
    resolveAPathId[T](ref.pathId, parents)
  }

  private def isSameKind[DEF <: Definition: ClassTag](d: Definition): Boolean = {
    val clazz = classTag[DEF].runtimeClass
    clazz.isAssignableFrom(d.getClass)
  }

  private def isSameKindAndHasDifferentPathsToSameNode[T <: Definition: ClassTag](
    list: List[(Definition, Seq[Definition])]
  ): Boolean = {
    list.forall { item => isSameKind[T](item._1) } &&
    list
      .map { item =>
        item._2.filterNot(_.isImplicit)
      }
      .forall(_ == list.head)
  }

  private def handleSymbolTableResults[T <: Definition: ClassTag](
    list: List[(Definition, Seq[Definition])],
    pathId: PathIdentifier,
    parents: Seq[Definition]
  ): Seq[Definition] = {
    parents.headOption match {
      case None =>
        // shouldn't happen
        notResolved[T](pathId, parents)
        Seq.empty
      case Some(parent) =>
        list match {
          // List is empty so this is the NotFound case
          case Nil =>
            notResolved[T](pathId, parents)
            Seq.empty
          // List just has one component and the types are the same so this is the Resolved case
          case (d, pars) :: Nil if isSameKind[T](d) => // exact match
            // Found
            resolved[T](pathId, parent, d)
            d +: pars
          // List has one component but its the wrong type
          case (d, _) :: Nil =>
            wrongType[T](pathId, parent, d)
            Seq.empty
          // List has multiple elements
          case (d, pars) :: _ if isSameKindAndHasDifferentPathsToSameNode(list) =>
            resolved[T](pathId, parent, d)
            d +: pars
          case list =>
            ambiguous[T](pathId, list)
            Seq.empty
        }
    }
  }

  private def searchSymbolTable[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Seq[Definition]
  ): Seq[Definition] = {
    val symTabCompatibleNameSearch = pathId.value.reverse
    val list = symbols.lookupParentage(symTabCompatibleNameSearch)
    handleSymbolTableResults[T](list, pathId, parents)
  }

  private sealed trait AnchorCase
  private case class AnchorNotFoundInSymTab(topName: String) extends AnchorCase
  private case class AnchorNotFoundInParents(topName: String) extends AnchorCase
  private case class AnchorNotFoundAnywhere(topName: String) extends AnchorCase
  private case class AnchorIsAmbiguous(topName: String, list: List[(Definition, Seq[Definition])])
      extends AnchorCase
  private case class AnchorFoundInSymTab(anchor: Definition, anchor_parents: Seq[Definition]) extends AnchorCase
  private case class AnchorFoundInParents(anchor: Definition, anchor_parents: Seq[Definition]) extends AnchorCase
  private case class AnchorIsRoot(anchor: Definition, anchor_parents: Seq[Definition]) extends AnchorCase

  private def findAnchorInParents(
    topName: String,
    parents: Seq[Definition]
  ): AnchorCase = {
    // The anchor is the matching name closest to the PathId location
    parents.find(_.id.value == topName) match {
      case Some(anchor) =>
        // We want to simulate a symtab find here which returns the node of
        // interest and that node's parents. Since there is a node in common
        // we can get it by dropping nodes until we find it.
        val anchor_parents = parents.dropWhile(_ != anchor).drop(1)
        AnchorFoundInParents(anchor, anchor_parents)
      case None =>
        AnchorNotFoundInParents(topName)
    }
  }

  private def findAnchorInSymTab(
    topName: String
  ): AnchorCase = {
    // Let's see if we can find it uniquely in the symbol table
    symbols.lookupParentage(Seq(topName)) match {
      case Nil =>
        AnchorNotFoundInSymTab(topName)
      case (anchor: Definition, anchor_parents: Seq[Definition]) :: Nil =>
        // it is unique
        // Found the top node uniquely in the symbol table
        // now just run down the children and see if all levels of the
        // pathId can be satisfied
        AnchorFoundInSymTab(anchor, anchor_parents)
      case list =>
        AnchorIsAmbiguous(topName, list)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  private def findAnchor[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Seq[Definition]
  ): AnchorCase = {
    pathId.value.headOption match
      case Some(topName) if topName == "Root" =>
        // We anchor at the root of the model so anything possible
        AnchorIsRoot(parents.last, parents.dropRight(1))
      case Some(topName) =>
        // First, determine whether the anchor node is one of
        // the names in the parents above the location the PathId is used.
        findAnchorInParents(topName, parents) match
          case afip: AnchorFoundInParents => afip
          case _: AnchorNotFoundInParents =>
            // Its not an ancestor so let's try the symbol table
            findAnchorInSymTab(topName) match
              case afis: AnchorFoundInSymTab     => afis
              case anfis: AnchorNotFoundInSymTab => anfis
              case aia: AnchorIsAmbiguous        => aia
              case anfis: AnchorCase =>
                messages.addSevere(pathId.loc, s"Invalid result from findAnchorInSymTab($topName, $parents): $anfis")
                anfis
          case anfis: AnchorCase =>
            messages.addSevere(pathId.loc, s"Invalid result from findAnchorInParents($topName, $parents): $anfis")
            anfis
      case None =>
        messages.addSevere(pathId.loc, "PathId is empty; this should already be checked in resolveAPathId")
        AnchorNotFoundAnywhere("<unknown>")
  }

  @SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.IterableOps"))
  private def resolvePathFromAnchor[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Seq[Definition],
    anchor: Definition,
    anchor_parents: Seq[Definition]
  ): Seq[Definition] = {
    val stack: mutable.Stack[Definition] = mutable.Stack.empty[Definition]
    val parents_to_add = anchor_parents.reverse
    if anchor_parents.nonEmpty && anchor_parents.last.isRootContainer then stack.pushAll(parents_to_add.drop(1))
    else stack.pushAll(parents_to_add)
    stack.push(anchor)
    val pathIdStart = pathId.value.drop(1) // we already resolved the anchor
    var continue: Boolean = true
    for { soughtName: String <- pathIdStart if continue } do {
      // Get the list of candidates for the
      val candidates = findCandidates(stack)

      // Now find the match, if any, and handle appropriately
      val maybeFound = candidates.find(candidate => findResolution(soughtName, candidate))
      maybeFound match
        case Some(q: Definition) =>
          // found the named item, and it is a Container, so put it on
          // the stack in case there are more things to resolve
          stack.push(q)

        case None =>
          // None of the candidates match the name we're seeking
          // So this Path Id isn't valid, say so
          notResolved[T](
            pathId,
            parents,
            s"definition '$soughtName' was not found inside '${stack.head.identify}''"
          )
          continue = false
    }
    if continue then
      val maybeFound = stack.toSeq
      checkResultingPath(pathId, parents, maybeFound)
      stack.headOption match
        case Some(_: Root) if stack.size == 1 =>
          // then pop it off because RootContainers don't count and we want to
          // rightfully return an empty sequence for "not found"
          stack.pop()
          // Convert parent stack to immutable sequence
          stack.toSeq
        case Some(_) =>
          // Not the root, just convert the result to immutable Seq
          stack.toSeq
        case None =>
          stack.toSeq // empty == fail
    else Seq.empty[Definition]
  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  private def checkResultingPath[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Seq[Definition],
    maybeFound: Seq[Definition]
  ): Seq[Definition] = {
    maybeFound.toList match {
      case Nil =>
        notResolved[T](pathId, parents)
        Seq.empty
      case head :: Nil =>
        // shouldn't happen, but ...
        messages.addSevere(pathId.loc, s"Single path entry found, '${head.format}' should not be possible'")
        notResolved[T](pathId, parents)
        Seq.empty
      case head :: tail =>
        // we have at least two names, let's find the first one
        // and see if it is
        if checkThatPathIdMatchesFoundParentStack(pathId, parents, maybeFound) then
          if isSameKind[T](head) then
            // a candidate was found and it has the same type as expected
            resolved[T](pathId, parents.head, head)
            head :: tail
          else
            // Not the same type, report the error
            wrongType[T](pathId, parents.head, head)
            Seq.empty
        else Seq.empty
    }

  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  private def checkThatPathIdMatchesFoundParentStack[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Seq[Definition],
    maybeResult: Seq[Definition]
  ): Boolean = {
    pathId.value.headOption match {
      case Some(_) =>
        val foundDefinition = maybeResult.head
        val foundName = foundDefinition.id.value
        val soughtName = pathId.value.last
        val foundClass = foundDefinition.getClass
        val soughtClass = classTag[T].runtimeClass
        if foundName != soughtName then
          notResolved[T](
            pathId,
            parents,
            s"the found name, '$foundName', is not the same as the sought name, '$soughtName'"
          )
          false
        else if !soughtClass.isAssignableFrom(foundClass) then
          notResolved[T](
            pathId,
            parents,
            s"the found class ${foundClass.getSimpleName} is not compatible with the sough class, " +
              s"'${soughtClass.getSimpleName}"
          )
          false
        else true
      case None =>
        messages.addSevere(pathId.loc, "Empty path id")
        false
    }
  }

  private def resolveAMessageRef(ref: MessageRef, parents: Seq[Definition]): Seq[Definition] = {
    val loc: At = ref.loc
    val pathId: PathIdentifier = ref.pathId
    val kind: AggregateUseCase = ref.messageKind
    val path = resolveAPathId[Type](pathId, parents)
    path.headOption match {
      case None => // empty or not a type, bail
        path
      case Some(typ: Type) =>
        typ.typ match {
          case AggregateUseCaseTypeExpression(_, usecase, _) if usecase == kind => path // success
          case typeEx: Alternation if typeEx.of.forall(_.isAggregateOf(kind))      => path // success
          case typeEx: Alternation =>
            messages.addError(
              loc,
              s"All alternates of `${typeEx.format}` must be ${kind.useCase.dropRight(4)} aggregates"
            )
            Seq.empty
          case typeEx: TypeExpression =>
            messages.addError(
              loc,
              s"Type expression `${typeEx.format}` needs to be an aggregate for `${kind.useCase.dropRight(4)}`"
            )
            Seq.empty
        }
      case Some(_) =>
        path // error message should have already been issued
    }
  }

  private def resolveATypeRef(typeRef: TypeRef, parents: Seq[Definition]): Seq[Definition] = {
    val loc: At = typeRef.loc
    val pathId: PathIdentifier = typeRef.pathId
    val keyword: String = typeRef.keyword
    val path = resolveAPathId[Type](pathId, parents)
    path.headOption match {
      case None => // empty or not a type, bail
        path
      case Some(typ: Type) =>
        keyword match {
          case Keyword.type_ | "" => path // this is generic, any type so just pass the result
          case Keyword.command =>
            typ.typ match {
              case typEx: AggregateUseCaseTypeExpression if typEx.usecase == CommandCase => path // success
              case typeEx: Alternation if typeEx.of.forall(_.isAggregateOf(CommandCase)) => path // success
              case typeEx: Alternation =>
                messages.addError(loc, s"All alternates of `${typeEx.format}` must be command aggregates")
                Seq.empty
              case typEx: AggregateUseCaseTypeExpression =>
                messages.addError(loc, s"Type expression `${typEx.format}` is not compatible with keyword `command`")
                Seq.empty
              case typEx: TypeExpression =>
                messages.addError(loc, s"Type expression `${typEx.format}` needs to be an aggregate for `command`")
                Seq.empty
            }
          case Keyword.query =>
            typ.typ match {
              case typEx: AggregateUseCaseTypeExpression if typEx.usecase == QueryCase => path // success
              case typeEx: Alternation if typeEx.of.forall(_.isAggregateOf(QueryCase)) => path // success
              case typeEx: Alternation =>
                messages.addError(loc, s"All alternates of `${typeEx.format}` must be query aggregates")
                Seq.empty
              case typEx: AggregateUseCaseTypeExpression =>
                messages.addError(loc, s"Type expression `${typEx.format}` is not compatible with keyword `query`")
                Seq.empty
              case typEx: TypeExpression =>
                messages.addError(loc, s"Type expression `${typEx.format}` needs to be an aggregate for `query`")
                Seq.empty
            }
          case Keyword.event =>
            typ.typ match {
              case typEx: AggregateUseCaseTypeExpression if typEx.usecase == EventCase => path // success
              case typeEx: Alternation if typeEx.of.forall(_.isAggregateOf(EventCase)) => path // success
              case typeEx: Alternation =>
                messages.addError(loc, s"All alternates of `${typeEx.format}` must be event aggregates")
                Seq.empty
              case typEx: AggregateUseCaseTypeExpression =>
                messages.addError(loc, s"Type expression `${typEx.format}` is not compatible with keyword `event`")
                Seq.empty
              case typEx: TypeExpression =>
                messages.addError(loc, s"Type expression `${typEx.format}` needs to be an aggregate for `event`")
                Seq.empty
            }
          case Keyword.result =>
            typ.typ match {
              case typEx: AggregateUseCaseTypeExpression if typEx.usecase == ResultCase => path // success
              case typeEx: Alternation if typeEx.of.forall(_.isAggregateOf(ResultCase)) => path // success
              case typeEx: Alternation =>
                messages.addError(loc, s"All alternates of `${typeEx.format}` must be result aggregates")
                Seq.empty
              case typEx: AggregateUseCaseTypeExpression =>
                messages.addError(loc, s"Type expression `${typEx.format}` is not compatible with keyword `result`")
                Seq.empty
              case typEx: TypeExpression =>
                messages.addError(loc, s"Type expression `${typEx.format}` needs to be an aggregate for `result`")
                Seq.empty
            }
          case Keyword.record =>
            typ.typ match {
              case typEx: AggregateUseCaseTypeExpression if typEx.usecase == RecordCase => path // success
              case typeEx: Alternation if typeEx.of.forall(_.isAggregateOf(RecordCase)) => path // success
              case typeEx: Alternation =>
                messages.addError(loc, s"All alternates of `${typeEx.format}` must be record aggregates")
                Seq.empty
              case typEx: AggregateUseCaseTypeExpression =>
                messages.addError(loc, s"Type expression `${typEx.format}` is not compatible with keyword `record`")
                Seq.empty
              case typEx: TypeExpression =>
                messages.addError(loc, s"Type expression ${typEx.format} needs to be an aggregate for keyword `record`")
                Seq.empty
            }
          case Keyword.graph =>
            typ.typ match {
              case _: Graph                                                              => path // success
              case typeEx: Alternation if typeEx.of.forall(_.getClass == Graph.getClass) => path // success
              case typEx: TypeExpression =>
                messages.addError(loc, s"Type expression `${typEx.format}` needs to be a graph for keyword `graph`")
                Seq.empty
            }
          case Keyword.table =>
            typ.typ match {
              case _: Table                                                              => path // success
              case typeEx: Alternation if typeEx.of.forall(_.getClass == Table.getClass) => path // success
              case typEx: TypeExpression =>
                messages.addError(
                  typEx.loc,
                  s"Type expression `${typEx.format}` needs to be a table for keyword `table`"
                )
                Seq.empty
            }
        }
      case Some(_) =>
        path // error message should have already been issued
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  private def resolveAPathId[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Seq[Definition]
  ): Seq[Definition] = {
    if pathId.value.isEmpty then
      // The pathId is empty, can't resolve that
      notResolved[T](pathId, parents, "the PathId is empty")
      Seq.empty[Definition]
    else
      // If we already resolved this one, return it
      refMap.definitionOf[T](pathId, parents.head) match
        case Some(result) =>
          result +: symbols.parentsOf(result)
        case None =>
          if pathId.value.size == 1 then
            // Easy case, just search the symbol table and deal with it there.
            // In other words, there really isn't a path to search here, just the
            // symbol table
            searchSymbolTable[T](pathId, parents)
          else
            // Okay, we have multiple names so we first have to find the anchor
            // node from the first name in the PathId. This can be "Root" for the
            // root of the model, a node name directly above, or a node from the
            // symbol table.
            findAnchor[T](pathId, parents) match
              case AnchorNotFoundInParents(topName) =>
                notResolved(
                  pathId,
                  parents,
                  s"the PathId is invalid since it's first element, $topName, is not found in PathId ancestors"
                )
              case AnchorFoundInSymTab(anchor, anchor_parents) =>
                // We found the anchor in the
                resolvePathFromAnchor(pathId, parents, anchor, anchor_parents)
              case AnchorFoundInParents(anchor, anchor_parents) =>
                // We found the anchor in the parents list
                resolvePathFromAnchor(pathId, parents, anchor, anchor_parents)
              case AnchorNotFoundInSymTab(topName) =>
                notResolved(
                  pathId,
                  parents,
                  s"the PathId is invalid since it's first element, $topName, does not exist in the model"
                )
              case AnchorNotFoundAnywhere(_) =>
                notResolved(pathId, parents)
              case AnchorIsRoot(anchor, anchor_parents) =>
                // The first name in the path id was "Root" so start from there
                resolvePathFromAnchor(pathId, parents, anchor, anchor_parents)
              case AnchorIsAmbiguous(_, list) =>
                // The anchor is ambiguous so generate that message
                ambiguous[T](pathId, list, Some("The top node in the Path Id is the ambiguous one"))
  }

  private def resolved[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    pidDirectParent: Definition,
    definition: Definition
  ): Option[T] = {
    // a candidate was found and it has the same type as expected
    val t = definition.asInstanceOf[T]
    refMap.add[T](pathId, pidDirectParent, t)
    associateUsage(pidDirectParent, t)
    if commonOptions.verbose then
      messages.add(
        Messages.info(
          s"Path Identifier ${pathId.format} in ${pidDirectParent.identify} resolved to ${definition.identify}",
          pathId.loc
        )
      )

    Some(t)
  }

  private def wrongType[T <: Definition: ClassTag](
    pid: PathIdentifier,
    container: Definition,
    foundDef: Definition
  ): Unit = {
    val referTo = classTag[T].runtimeClass.getSimpleName
    val message = s"Path '${pid.format}' resolved to ${foundDef.identifyWithLoc}," +
      s" in ${container.identify}, but ${article(referTo)} was expected"
    messages.addError(pid.loc, message)
  }

  private def notResolved[T <: Definition: ClassTag](
    pid: PathIdentifier,
    parents: Seq[Definition],
    why: String = ""
  ): Seq[Definition] = {
    val tc = classTag[T].runtimeClass
    val container = parents.headOption
    val message = container match
      case None =>
        s"Path '${pid.format}' is not resolvable, because it has no container"
      case Some(dfntn) =>
        s"Path '${pid.format}' was not resolved, in ${dfntn.identify}${
            if why.isEmpty then "\n"
            else " because\n" + why + "\n"
          }"

    val referTo = tc.getSimpleName
    messages.addError(
      pid.loc,
      message + {
        if referTo.nonEmpty then s"and it should refer to ${article(referTo)}"
        else ""
      }
    )
    Seq.empty
  }

  private def ambiguous[T <: Definition: ClassTag](
    pid: PathIdentifier,
    list: List[(Definition, Seq[Definition])],
    context: Option[String] = None
  ): Seq[Definition] = {
    // Extract all the definitions that were found
    val definitions = list.map(_._1)
    val allDifferent = definitions.map(_.kind).distinct.sizeIs ==
      definitions.size
    val expectedClass = classTag[T].runtimeClass
    definitions.headOption match {
      case Some(head) if head.isImplicit && allDifferent =>
        // pick the one that is the right type or the first one
        list.find(_._1.getClass == expectedClass) match {
          case Some((defn, parents)) => defn +: parents
          case None                  => list.take(1).map(_._1)
        }
      case _ =>
        val ambiguity = list
          .map { case (definition, parents) =>
            "  " + parents.reverse.map(_.id.value).mkString(".") + "." +
              definition.id.value + " (" + definition.loc + ")"
          }
          .mkString("\n")
        val message = s"Path reference '${pid.format}' is ambiguous. Definitions are:\n$ambiguity" + {
          context match {
            case Some(context) => context + "\n"
            case None          => ""
          }
        }
        messages.addError(pid.loc, message)
        Seq.empty[Definition]
    }
  }

  private val vowels: String = "aAeEiIoOuU"

  private def article(thing: String): String = {
    val article = if vowels.contains(thing.head) then "an" else "a"
    s"$article $thing"
  }

  private def adjustStacksForPid[T <: Definition: ClassTag](
    pid: PathIdentifier,
    parentStack: mutable.Stack[Definition]
  ): Seq[Definition] = {

    // Recursively resolve this PathIdentifier
    val path: Seq[Definition] = resolveAPathId[T](pid, parentStack.toSeq)

    // if we found the definition
    if path.nonEmpty then {
      // Replace the parent stack with the resolved one
      parentStack.clear()
      parentStack.pushAll(path.reverse)

      // Return the name and candidates we should next search for
      parentStack.headOption match
        case None       => Seq.empty[T] // shouldn't happen?
        case Some(head) => head.definitions

    } else {
      // Couldn't resolve it, error already issued, signal termination of the search
      Seq.empty
    }
  }

  private def candidatesFromTypeEx(
    typEx: TypeExpression,
    parentStack: mutable.Stack[Definition]
  ): Contents[Definition] = {
    typEx match {
      case a: Aggregation => a.fields
      // if we're at a field composed of more fields, then those fields
      // are what we are looking for
      case Enumeration(_, enumerators) =>
        // if we're at an enumeration type then the numerators are candidates
        enumerators
      case a: AggregateUseCaseTypeExpression =>
        // Any kind of Aggregate's fields are candidates for resolution
        a.fields
      case AliasedTypeExpression(_, _, pid) =>
        // if we're at a field that references another type then the candidates
        // are that type's fields. To solve this we need to push
        // that type's path on the name stack to be resolved
        adjustStacksForPid[Type](pid, parentStack)
      case EntityReferenceTypeExpression(_, entityRef) =>
        adjustStacksForPid[Entity](entityRef, parentStack)
      case _ =>
        // We cannot descend into any other type expression
        Seq.empty[Definition]
    }
  }

  private def findCandidates(
    parentStack: mutable.Stack[Definition]
  ): Contents[Definition] = {
    if parentStack.isEmpty then {
      // Nothing in the parent stack so we're done searching and
      // we return empty to signal nothing found
      Seq.empty[Definition]
    } else {
      parentStack.headOption match {
        case None =>
          Seq.empty[Definition] // nothing to search to provide candidates
        case Some(head) =>
          head match
            case st: State =>
              // If we're at a state definition then it references a type for
              // its fields so we need to push that typeRef's name on the name stack.
              adjustStacksForPid[Type](st.typ.pathId, parentStack)
            case oc: OnMessageClause =>
              // if we're at an onClause that references a named message then we
              // need to push that message's path on the name stack
              adjustStacksForPid[Type](oc.msg.pathId, parentStack)
            case field: Field =>
              candidatesFromTypeEx(field.typeEx, parentStack).definitions
            case c: Constant =>
              candidatesFromTypeEx(c.typeEx, parentStack)
            case t: Type =>
              candidatesFromTypeEx(t.typ, parentStack)
            case d: Container[RiddlValue] =>
              d.contents.flatMap {
                case Include(_, contents, _) => contents.definitions
                case d: Definition        => Seq(d)
                case _                       => Seq.empty
              }
      }
    }
  }

  private def findResolution(soughtName: String, candidate: Definition): Boolean = {
    candidate match {
      case omc: OnMessageClause if omc.msg.id.nonEmpty =>
        omc.msg.id.getOrElse(Identifier.empty).value == soughtName
      case other: Definition =>
        other.id.value == soughtName
    }
  }
}
