/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.{Entity, *}
import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.language.{At, CommonOptions, Messages}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.symbols.Symbols.*
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}

import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

case class ResolutionOutput(
  root: Root = Root.empty,
  messages: Messages.Messages = Messages.empty,
  refMap: ReferenceMap = ReferenceMap.empty,
  kindMap: KindMap = KindMap.empty,
  usage: Usages = Usages.empty
) extends PassOutput {}

object ResolutionPass extends PassInfo[PassOptions] {
  val name: String = "Resolution"
  def creator(options: PassOptions = PassOptions.empty): PassCreator = { (in: PassInput, out: PassesOutput) =>
    ResolutionPass(in, out)
  }
}

/** The Reference Resolution Pass. This pass traverses the entire model and resolves every reference it finds into the
  * `refmap` in its output. See [[ReferenceMap]] for details. This resolution must be done before validation to make
  * sure there are no cycles in the references. While it is at it, it also tracks which definition uses which other
  * definition. See [[Usages]] for details. It also keeps a `kindMap`. See [[KindMap]] for details.
  *
  * Reference Resolution is the process of turning a [[com.ossuminc.riddl.language.AST.PathIdentifier]] into the
  * [[com.ossuminc.riddl.language.AST.Definition]] that is referenced by the
  * [[com.ossuminc.riddl.language.AST.PathIdentifier]]. There are several ways to resolve a reference:
  *
  *   1. If its already in the [[ReferenceMap]] then use that resolution
  *   1. A single identifier in the path is looked up in the symbol table and if it uniquely matches only one definition
  *      then that definition is the resolved definition.
  *   1. If there are multiple identifiers in the [[com.ossuminc.riddl.language.AST.PathIdentifier]] then we attempt to
  *      anchor the search using the first identifier. Anchoring is done by (a) checking to see if it is the "Root" node
  *      in which case that is the anchor, (b) checking to see if the first identifier is the name of one of the parent
  *      nodes from the location of the reference, and finally (c) looking up the first identifier in the symbol table
  *      and if it is unique then using that as the anchor. Once the anchor is determined, it is simply a matter of
  *      walking down tree of nodes from the anchor, one name at a time.
  *
  * @param input
  *   The input to the original pass.
  * @param outputs
  *   THe outputs from preceding passes, which should only be the [[com.ossuminc.riddl.passes.symbols.SymbolsPass]]
  *   output.
  */
case class ResolutionPass(input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) with UsageResolution {

  override def name: String = ResolutionPass.name

  requires(SymbolsPass)

  val commonOptions: CommonOptions = input.commonOptions
  val refMap: ReferenceMap = ReferenceMap(messages)
  val kindMap: KindMap = KindMap()
  val symbols: SymbolsOutput = outputs.outputOf[SymbolsOutput](SymbolsPass.name).get

  override def result(root: Root): ResolutionOutput =
    ResolutionOutput(root, messages.toMessages, refMap, kindMap, Usages(uses, usedBy))

  override def close(): Unit = ()

  override def postProcess(root: Root): Unit = {
    checkUnused()
  }

  def process(value: RiddlValue, parents: ParentStack): Unit =
    val resolution: Resolution[Definition] =
      if value.isDefinition then
        val definition = value.asInstanceOf[Definition]
        kindMap.add(definition)
        Some(definition -> parents.toParentsSeq)
      else Some(parents.head -> parents.tail.toParentsSeq)
      end if
    resolution match
      case None => ()
      case Some((definition: Definition, resolutionParents: Parents)) =>
        val parentsAsSeq =
          if definition.isParent then definition.asInstanceOf[Parent] +: resolutionParents
          else resolutionParents
          end if
        definition match
          case ad: AggregateValue =>
            resolveTypeExpression(ad.typeEx, parentsAsSeq)
          case t: Type =>
            resolveType(t, parentsAsSeq)
          case mc: OnMessageClause =>
            resolveOnMessageClause(mc, parentsAsSeq)
          case ic: OnInitializationClause =>
            resolveOnClauses(ic, parentsAsSeq)
          case tc: OnTerminationClause =>
            resolveOnClauses(tc, parentsAsSeq)
          case oc: OnOtherClause =>
            resolveOnClauses(oc, parentsAsSeq)
          case e: Entity =>
            e.authorRefs.foreach(resolveARef[Author](_, parentsAsSeq))
            resolveStateReferences(e, parentsAsSeq)
            addEntity(e)
          case s: State =>
          // resolveATypeRef(s.typ, parentsAsSeq)
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
            p.repositories.foreach(ref => resolveARef[Repository](ref, parentsAsSeq))
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
            if uc.contents.nonEmpty then resolveInteractions(uc.contents, parentsAsSeq)
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
          case _: BriefDescription           => ()
          case _: NonReferencableDefinitions => () // These can't be referenced
          // case _: NonDefinitionValues        => () // Neither can these values
          // case _ => () // NOTE: Never have this catchall! Want compile time errors!
        end match
    end match
  end process

  private def resolveFunction(f: Function, parents: Parents): Unit = {
    f.authorRefs.foreach(resolveARef[Author](_, parents))
    addFunction(f)
    f.input.foreach(resolveTypeExpression(_, parents))
    f.output.foreach(resolveTypeExpression(_, parents))
  }

  private def resolveConnector(connector: Connector, parents: Parents): Unit = {
    if connector.nonEmpty then
      resolveARef[Outlet](connector.from, parents)
      resolveARef[Inlet](connector.to, parents)
  }

  private def resolveType(typ: Type, parents: Parents): Unit = {
    addType(typ)
    resolveTypeExpression(typ.typEx, parents)
  }

  private def resolveTypeExpression(typ: TypeExpression, parents: Parents): Unit = {
    typ match {
      case UniqueId(_, entityPath) =>
        resolveAPathId[Entity](entityPath, parents)
      case AliasedTypeExpression(_, _, pathId) =>
        resolveAPathId[Type](pathId, parents)
      case agg: AggregateTypeExpression =>
        agg.fields.foreach { (fld: Field) =>
          resolveTypeExpression(fld.typeEx, parents)
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

  private def resolveOnMessageClause(mc: OnMessageClause, parents: Parents): Unit = {
    resolveARef[Type](mc.msg, parents)
    mc.from match
      case None               => ()
      case Some(_, reference) => resolveARef[Definition](reference, parents)
    resolveStatements(mc.contents, parents)
  }

  private def resolveStateReferences(e: Entity, parents: Parents): Unit = {
    for { state: State <- e.states } do {
      resolveATypeRef(state.typ, parents)
    }
  }

  private def resolveOnClauses(oc: OnClause, parents: Parents): Unit = {
    resolveStatements(oc.contents, parents)
  }

  private def resolveStatements(statements: Seq[OnClauseContents], parents: Parents): Unit = {
    statements.foreach(resolveStatement(_, parents))
  }

  private def resolveStatement(statement: OnClauseContents, parents: Parents): Unit = {
    statement match {
      case SetStatement(_, field, _) =>
        resolveARef[Field](field, parents)
      case BecomeStatement(_, entity, handler) =>
        resolveARef[Entity](entity, parents)
        resolveARef[Handler](handler, parents)
      case FocusStatement(_, group) =>
        resolveARef[Group](group, parents)
      case ForEachStatement(_, ref, _) =>
        ref match {
          case ir: InletRef  => resolveAPathId[Inlet](ir.pathId, parents)
          case or: OutletRef => resolveAPathId[Outlet](or.pathId, parents)
          case fr: FieldRef  => resolveAPathId[Type](fr.pathId, parents)
        }
      case SendStatement(_, msg, portlet) =>
        resolveARef[Type](msg, parents)
        resolveARef[Portlet](portlet, parents)
      case MorphStatement(_, entity, state, message) =>
        resolveARef[Entity](entity, parents)
        resolveARef[State](state, parents)
        resolveARef[Type](message, parents)
      case TellStatement(_, msg, processorRef) =>
        resolveARef[Type](msg, parents)
        resolveARef[Processor[?]](processorRef, parents)
      case CallStatement(_, func) =>
        resolveARef[Function](func, parents)
      case ReplyStatement(_, message) =>
        resolveARef[Type](message, parents)
      case _: CodeStatement       => () // no references
      case _: ReadStatement       => () // no references
      case _: WriteStatement      => () // no references
      case _: ArbitraryStatement  => () // no references
      case _: ErrorStatement      => () // no references
      case _: ReturnStatement     => () // no references
      case _: IfThenElseStatement => () // no references
      case _: StopStatement       => () // no references
      case _: Comment             => () // no references
    }
  }

  private def resolveInteractions(
    interactions: Seq[Interaction | Comment],
    parentsAsSeq: Parents
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
        case si: SelectInputInteraction =>
          resolveARef[User](si.from, parentsAsSeq)
          resolveARef[Input](si.to, parentsAsSeq)
        case pi: TakeInputInteraction =>
          resolveARef[User](pi.from, parentsAsSeq)
          resolveARef[Input](pi.to, parentsAsSeq)
        case si: SelfInteraction =>
          resolveARef[Definition](si.from, parentsAsSeq)
        case SendMessageInteraction(_, from, message, to, _, _) =>
          resolveARef[Definition](from, parentsAsSeq)
          resolveAMessageRef(message, parentsAsSeq)
          resolveARef[Definition](to, parentsAsSeq)
        case _: VagueInteraction       => () // no resolution required
        case _: OptionalInteractions   => () // no references
        case _: ParallelInteractions   => () // no references
        case _: SequentialInteractions => () // no references
        case _: Comment                => () // no references
      }
    }
  }

  private def resolveARef[T <: Definition: ClassTag](
    ref: Reference[T],
    parents: Parents
  ): Unit = {
    resolveAPathId[T](ref.pathId, parents)
  }

  private def isSameKind[DEF <: WithIdentifier: ClassTag](d: WithIdentifier): Boolean = {
    val clazz = classTag[DEF].runtimeClass
    clazz.isAssignableFrom(d.getClass)
  }

  private def isSameKindAndHasDifferentPathsToSameNode[T <: WithIdentifier: ClassTag](
    list: List[SymTabItem]
  ): Boolean = {
    list.forall { item => isSameKind[T](item._1) } &&
    list
      .map { item =>
        item._2.filterNot(_.isAnonymous)
      }
      .forall(_ == list.head)
  }

  private def handleSymbolTableResults[T <: Definition: ClassTag](
    list: List[SymTabItem],
    pathId: PathIdentifier,
    parents: Parents
  ): Resolution[T] =
    parents.headOption match
      case None =>
        // shouldn't happen
        notResolved[T](pathId, parents, "there are no parents of the found symbol")
        None
      case Some(parent) =>
        list match
          case Nil =>
            // List is empty so this is the NotFound case
            notResolved[T](
              pathId,
              parents,
              s"the sought name, '${pathId.value.last}', was not found in the symbol table,"
            )
            None
          case (d, pars) :: Nil if isSameKind[T](d) => // exact match
            // List just has one component and the types are the same so this is the Resolved case
            resolved[T](pathId, parent, d)
            Some(d.asInstanceOf[T] -> pars)
          case (d, _) :: Nil =>
            // List has one component but its the wrong type
            wrongType[T](pathId, parent, d)
            None
          case (d, pars) :: _ if isSameKindAndHasDifferentPathsToSameNode(list) =>
            // List has multiple elements
            resolved[T](pathId, parent, d)
            Some(d.asInstanceOf[T] -> pars)
          case list =>
            ambiguous[T](pathId, list)
            None
        end match
    end match
  end handleSymbolTableResults

  private def searchSymbolTable[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Parents
  ): Resolution[T] = {
    val symTabCompatibleNameSearch = pathId.value.reverse
    val list = symbols.lookupParentage(symTabCompatibleNameSearch)
    handleSymbolTableResults[T](list, pathId, parents)
  }

  private sealed trait AnchorCase
  private case class AnchorNotFoundInSymTab(topName: String) extends AnchorCase
  private case class AnchorNotFoundInParents(topName: String) extends AnchorCase
  private case class AnchorNotFoundAnywhere(topName: String) extends AnchorCase
  private case class AnchorIsAmbiguous(topName: String, list: List[SymTabItem]) extends AnchorCase
  private case class AnchorFoundInSymTab(anchor: Parent, anchor_parents: Parents) extends AnchorCase
  private case class AnchorFoundInParents(anchor: Parent, anchor_parents: Parents) extends AnchorCase
  private case class AnchorIsRoot(anchor: Parent, anchor_parents: Parents) extends AnchorCase

  private def findAnchorInParents(
    topName: String,
    parents: Parents
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
      case (anchor: Parent, anchor_parents: Parents) :: Nil =>
        // it is unique
        // Found the top node uniquely in the symbol table
        // now just run down the children and see if all levels of the
        // pathId can be satisfied
        AnchorFoundInSymTab(anchor, anchor_parents)
      case list =>
        AnchorIsAmbiguous(topName, list)
    }
  }

  private def findAnchor[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Parents
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

  private def resolvePathFromAnchor[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Parents,
    anchor: Parent,
    anchor_parents: Parents
  ): Resolution[T] = {
    val stack: ParentStack = ParentStack.empty
    val parents_to_add = anchor_parents.reverse
    if anchor_parents.nonEmpty && anchor_parents.last.isRootContainer then stack.pushAll(parents_to_add.drop(1))
    else stack.pushAll(parents_to_add)
    stack.push(anchor)
    val pathIdStart = pathId.value.drop(1) // we already resolved the anchor
    var continue: Boolean = true
    var result: Option[Definition] = None
    for { soughtName: String <- pathIdStart if continue } do {
      // Get the list of candidates for potential matches to the name.

      val candidates = findCandidates(stack)
      val isLastItem = soughtName == pathId.value.last

      // Now find the match, if any, and handle appropriately
      val maybeFound = candidates.find(candidate => findResolution(soughtName, candidate))
      maybeFound match
        case Some(q: Parent) if isLastItem =>
          // Found the named item, and it is the last item in the Path so we have found the result
          result = Some(q)
          continue = false

        case Some(q: Parent) if !isLastItem =>
          // Found the named item, and it is a Parent, but there are more things to resolve so put it on
          // the stack
          stack.push(q)
        // continue = true

        case Some(_: LeafDefinition) if !isLastItem =>
          // Found the named item, but it is not the last item in the PID
          // We are at a leaf node but in the middle of the PathId so bad PathID because we can't descend any further
          notResolved[T](
            pathId,
            parents,
            s"definition '$soughtName' was found in middle of PathId ${stack.toSeq.reverse.mkString(".")}"
          )
          continue = false

        case Some(definition: LeafDefinition) if isLastItem =>
          result = Some(definition)

        case Some(_) =>
          notResolved[T](pathId, parents, s"definition '$soughtName' was found but in wrong context")
          continue = false

        case None =>
          // None of the candidates match the name we're seeking, so this Path Id isn't valid, say so
          notResolved[T](
            pathId,
            parents,
            s"definition '$soughtName' was not in PathId ${stack.toSeq.reverse.mkString(".")}"
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
          Some(stack.head.asInstanceOf[T] -> stack.tail.toSeq)
        case Some(dfntn: T) =>
          // Not the root, just convert the result to immutable Seq
          Some(dfntn -> stack.tail.toSeq)
        case Some(_) =>
          None
        case None =>
          None
    else
      result match
        case Some(definition) =>
          Some(definition.asInstanceOf[T] -> stack.toSeq)
        case None =>
          None
  }

  private def checkResultingPath[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Parents,
    maybeFound: Seq[Parent]
  ): Unit = {
    maybeFound.toList match {
      case Nil =>
        notResolved[T](pathId, parents)
      case head :: Nil =>
        // shouldn't happen, but ...
        messages.addSevere(pathId.loc, s"Single path entry found, '${head.format}' should not be possible'")
        notResolved[T](pathId, parents, s"'${head.format}' should not be possible")
      case found :: _ =>
        // we have at least two names, let's find the first one
        // and see if it is
        if checkThatPathIdMatchesFoundParentStack(pathId, parents, maybeFound) then
          if isSameKind[T](found) then
            // a candidate was found and it has the same type as expected
            resolved[T](pathId, parents.head, found)
          else
            // Not the same type, report the error
            wrongType[T](pathId, parents.head, found)
        else wrongType[T](pathId, parents.head, found)
    }
  }

  private def checkThatPathIdMatchesFoundParentStack[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Parents,
    maybeResult: Seq[WithIdentifier]
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

  private def resolveAMessageRef(ref: MessageRef, parents: Parents): Resolution[Type] =
    val loc: At = ref.loc
    val pathId: PathIdentifier = ref.pathId
    val kind: AggregateUseCase = ref.messageKind
    val result: Resolution[Type] = resolveAPathId[Type](pathId, parents)
    result match
      case Some((typ: Type, _)) =>
        typ.typEx match
          case AggregateUseCaseTypeExpression(_, usecase, _) if usecase == kind => result // success
          case typeEx: Alternation if typeEx.of.forall(_.isAggregateOf(kind))   => result // success
          case typeEx: Alternation =>
            messages.addError(
              loc,
              s"All alternates of `${typeEx.format}` must be ${kind.useCase.dropRight(4)} aggregates"
            )
            None
          case typeEx: TypeExpression =>
            messages.addError(
              loc,
              s"Type expression `${typeEx.format}` needs to be an aggregate for `${kind.useCase.dropRight(4)}`"
            )
            None
        end match
      case _ =>
        None // error message should have already been issued
    end match
  end resolveAMessageRef

  private def handleTypeResolution(
    loc: At,
    typ: Type,
    useCase: AggregateUseCase,
    resolution: Resolution[Type]
  ): Resolution[Type] =
    typ.typEx match
      case typEx: AggregateUseCaseTypeExpression if typEx.usecase == useCase => resolution // success
      case typeEx: Alternation if typeEx.of.forall(_.isAggregateOf(useCase)) => resolution // success
      case typeEx: Alternation =>
        messages.addError(typ.loc, s"All alternates of `${typeEx.format}` must be $useCase aggregates")
        None
      case typEx: AggregateUseCaseTypeExpression =>
        messages.addError(typ.loc, s"Type expression `${typEx.format}` is not compatible with keyword `$useCase`")
        None
      case typEx: TypeExpression =>
        messages.addError(typ.loc, s"Type expression `${typEx.format}` needs to be an aggregate for `$useCase`")
        None
    end match
  end handleTypeResolution

  private def resolveATypeRef(typeRef: TypeRef, parents: Parents): Resolution[Type] =
    val loc: At = typeRef.loc
    val pathId: PathIdentifier = typeRef.pathId
    val keyword: String = typeRef.keyword
    val resolution: Resolution[Type] = resolveAPathId[Type](pathId, parents)
    resolution match
      case None => None
      case Some((found: Definition, _: Parents)) =>
        found match
          case typ: Type =>
            keyword match
              case Keyword.type_ | "" => resolution // this is generic, any type so just pass the result
              case Keyword.command    => handleTypeResolution(loc, typ, CommandCase, resolution)
              case Keyword.query      => handleTypeResolution(loc, typ, QueryCase, resolution)
              case Keyword.event      => handleTypeResolution(loc, typ, EventCase, resolution)
              case Keyword.result     => handleTypeResolution(loc, typ, ResultCase, resolution)
              case Keyword.record     => handleTypeResolution(loc, typ, RecordCase, resolution)
              case Keyword.graph =>
                typ.typEx match
                  case _: Graph => resolution // success
                  case typeEx: Alternation =>
                    if typeEx.of.forall(_.getClass == Graph.getClass) then resolution // success
                    else
                      messages.addError(
                        typeEx.loc,
                        s"Type expression `${typeEx.format}` needs all elements to be a graph type for keyword `graph` at $loc"
                      )
                      None
                    end if
                  case _ =>
                    require(false, "Shouldn't get here")
                    None // shouldn't happen
                end match
              case Keyword.table =>
                typ.typEx match
                  case _: Table => resolution // success
                  case typeEx: Alternation =>
                    if typeEx.of.forall(_.getClass == Table.getClass) then resolution // success
                    else
                      messages.addError(
                        typ.typEx.loc,
                        s"Type expression `${typ.typEx.format}` needs to be a table for keyword `table` at $loc"
                      )
                      None
                    end if
                  case _: TypeExpression => 
                    require(false, "Should not get here")
                    None
                end match
            end match
        end match
    end match     
  end resolveATypeRef

  private def resolveAPathId[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Parents
  ): Resolution[T] =
    if pathId.value.isEmpty then
      // The pathId is empty, can't resolve that
      notResolved[T](pathId, parents, "the PathId is empty")
      None
    else
      // If we already resolved this one, return it
      refMap.definitionOf[T](pathId, parents.head) match
        case Some(result) =>
          Some(result -> symbols.parentsOf(result))
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
                None
              case AnchorFoundInSymTab(anchor, anchor_parents) =>
                // We found the anchor in the
                resolvePathFromAnchor[T](pathId, parents, anchor, anchor_parents)
              case AnchorFoundInParents(anchor, anchor_parents) =>
                // We found the anchor in the parents list
                resolvePathFromAnchor[T](pathId, parents, anchor, anchor_parents)
              case AnchorNotFoundInSymTab(topName) =>
                notResolved(
                  pathId,
                  parents,
                  s"the PathId is invalid since it's first element, $topName, does not exist in the model"
                )
                None
              case AnchorNotFoundAnywhere(_) =>
                notResolved(pathId, parents)
                None
              case AnchorIsRoot(anchor, anchor_parents) =>
                // The first name in the path id was "Root" so start from there
                resolvePathFromAnchor[T](pathId, parents, anchor, anchor_parents)
              case AnchorIsAmbiguous(_, list) =>
                // The anchor is ambiguous so generate that message
                ambiguous[T](pathId, list, Some("The top node in the Path Id is the ambiguous one"))
                None
  end resolveAPathId

  private def resolved[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    pidDirectParent: Parent,
    definition: Definition
  ): Option[T] =
    // a candidate was found and it has the same type as expected
    val t = definition.asInstanceOf[T]
    refMap.add[T](pathId, pidDirectParent, t)
    associateUsage(pidDirectParent, t)
    if commonOptions.debug then
      messages.add(
        Messages.info(
          s"Path Identifier ${pathId.format} in ${pidDirectParent.identify} resolved to ${definition.identify}",
          pathId.loc
        )
      )
    end if
    Some(t)
  end resolved

  private def wrongType[T <: Definition: ClassTag](
    pid: PathIdentifier,
    container: Definition,
    foundDef: WithIdentifier
  ): Unit =
    val referTo = classTag[T].runtimeClass.getSimpleName
    val message = s"Path '${pid.format}' resolved to ${foundDef.identifyWithLoc}," +
      s" in ${container.identify}, but ${article(referTo)} was expected"
    messages.addError(pid.loc, message)
  end wrongType

  private def notResolved[T <: Definition: ClassTag](
    pid: PathIdentifier,
    parents: Parents,
    why: String = ""
  ): Unit =
    val tc = classTag[T].runtimeClass
    val container = parents.headOption
    val message = container match
      case None =>
        s"Path '${pid.format}' is not resolvable, because it has no container"
      case Some(definition) =>
        s"Path '${pid.format}' was not resolved, in ${definition.identify}${
            if why.isEmpty then "\n"
            else "\nbecause " + why + "\n"
          }"

    val referTo = tc.getSimpleName
    messages.addError(
      pid.loc,
      message + {
        if referTo.nonEmpty then s"and it should refer to ${article(referTo)}"
        else ""
      }
    )
  end notResolved

  private def ambiguous[T <: Definition: ClassTag](
    pid: PathIdentifier,
    list: List[SymTabItem],
    context: Option[String] = None
  ): Contents[WithIdentifier] = {
    // Extract all the definitions that were found
    val definitions = list.map(_._1)
    val allDifferent = definitions.map(_.kind).distinct.sizeIs ==
      definitions.size
    val expectedClass = classTag[T].runtimeClass
    definitions.headOption match {
      case Some(head) if head.isAnonymous && allDifferent =>
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
        val message = s"Path reference '${pid.format}' is ambiguous. Definitions are:\n$ambiguity" +
          context.map(_ + "\n").getOrElse("")
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
    parentStack: ParentStack
  ): Contents[Definition] =

    // Recursively resolve this PathIdentifier
    val resolution: Resolution[T] = resolveAPathId[T](pid, parentStack.toParentsSeq)
    resolution match
      case None => Contents.empty
      case Some((_: Definition, parents: Parents)) => 
        // if we found the definition
        // Replace the parent stack with the resolved one
        parentStack.clear()
        parentStack.pushAll(parents.reverse)

        // Return the name and candidates we should next search for
        parentStack.headOption match
          case None       => Seq.empty[T] // shouldn't happen?
          case Some(head) => head.contents.definitions
        end match
    end match
  end adjustStacksForPid

  private def candidatesFromTypeExpression(
    typEx: TypeExpression,
    parentStack: ParentStack
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

  private def candidatesFromContents(
    contents: Contents[RiddlValue],
    stack: ParentStack
  ): Contents[Definition] =
    contents.flatMap { item =>
      item match
        case Include(_, _, contents) =>
          // NOTE: An included file can include another file at the same definitional level.
          // NOTE: We need to recursively descend that stack.  An include in a nested definitional level
          // NOTE: will not be picked up by contents.includes because it would be inside another definition.
          // NOTE: So we take the WithIdentifiers from the contents as well as from the includes
          val nested = candidatesFromContents(contents.includes, stack)
          val current = contents.definitions
          current ++ nested
        case st: State =>
          // At a state the fields of the state's data can be referenced
          candidatesFromStateTypeRef(st.typ, stack.toParentsSeq)
        case field: Field =>
          candidatesFromTypeExpression(field.typeEx, stack).definitions
        case constant: Constant =>
          candidatesFromTypeExpression(constant.typeEx, stack)
        case typ: Type =>
          candidatesFromTypeExpression(typ.typEx, stack)
        case definition: Definition =>
          Seq(definition)
        case _ =>
          Seq.empty
      end match
    }
  end candidatesFromContents

  private def candidatesFromStateTypeRef(typeRef: TypeRef, parents: Parents): Contents[Definition] = {
    val resolution: Resolution[Type] = resolveATypeRef(typeRef, parents)
    resolution match {
      case None => Seq.empty // not found
      case Some((typ: Type, _: Parents)) =>
        typ.typEx match {
          case agg: AggregateTypeExpression => agg.fields
          case _ => Seq.empty
        }
    }
  }

  private def findCandidates(stack: ParentStack): Contents[Definition] =
    require(stack.nonEmpty, "No stack to consider in findCandidates")
    stack.headOption match
      case None =>
        Seq.empty[Definition] // nothing to search to provide candidates
      case Some(head) =>
        head match
          case oc: OnMessageClause =>
            // FIXME: Dubious utility. OnMessageClause doesn't define any mutable state
            // if we're at an onClause that references a named message then we
            // need to push that message's path on the name stack
            adjustStacksForPid[Type](oc.msg.pathId, stack)
          case d: Parent =>
            candidatesFromContents(d.contents, stack)
    end match
  end findCandidates

  private def findResolution(soughtName: String, candidate: Definition): Boolean = {
    candidate match {
      case omc: OnMessageClause if omc.msg.id.nonEmpty =>
        omc.msg.id.getOrElse(Identifier.empty).value == soughtName
      case other: Definition =>
        other.id.value == soughtName
    }
  }
}
