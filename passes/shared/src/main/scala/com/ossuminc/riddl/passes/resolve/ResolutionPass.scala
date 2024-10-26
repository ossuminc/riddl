/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.{Entity, *}
import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.symbols.Symbols.*
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

case class ResolutionOutput(
  root: Root = Root.empty,
  messages: Messages.Messages = Messages.empty,
  refMap: ReferenceMap = ReferenceMap.empty,
  usage: Usages = Usages.empty
) extends PassOutput {}

object ResolutionPass extends PassInfo[PassOptions] {
  val name: String = "Resolution"
  def creator(options: PassOptions = PassOptions.empty)(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => ResolutionPass(in, out)
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
case class ResolutionPass(input: PassInput, outputs: PassesOutput)(using io: PlatformContext)
    extends Pass(input, outputs)
    with UsageResolution {

  override def name: String = ResolutionPass.name

  requires(SymbolsPass)

  val refMap: ReferenceMap = ReferenceMap(messages)
  val kindMap: KindMap = KindMap()
  val symbols: SymbolsOutput = outputs.outputOf[SymbolsOutput](SymbolsPass.name).get

  override def result(root: Root): ResolutionOutput =
    ResolutionOutput(root, messages.toMessages, refMap, Usages(uses, usedBy))

  override def close(): Unit = ()

  override def postProcess(root: Root): Unit = {
    checkUnused()
  }

  def process(value: RiddlValue, parentsStack: ParentStack): Unit =
    val parents: Parents =
      value match
        case p: Parent =>
          kindMap.add(p)
          p +: parentsStack.toParents
        case _ => parentsStack.toParents
      end match
    value match
      case av: AggregateValue => // Field, Method
        val resolution = resolveTypeExpression(av, av.typeEx, parents)
        associateUsage[Type](av, resolution)
      case t: Type =>
        associateUsage[Type](t, resolveType(t, parents))
      case mc: OnMessageClause =>
        resolveOnMessageClause(mc, parents)
      case statement: Statement =>
        resolveStatement(statement, parents)
      case _: OnTerminationClause => ()
      case _: OnOtherClause       => ()
      case e: Entity =>
        resolveAuthorRefs(e, parents)
        addEntity(e)
      case s: State =>
        associateUsage(s, resolveATypeRef(s.typ, parents))
      case f: Function =>
        resolveFunction(f, parents)
      case i: Inlet =>
        associateUsage(i, resolveATypeRef(i.type_, parents))
      case o: Outlet =>
        val resolution = resolveATypeRef(o.type_, parents)
        associateUsage(o, resolution)
      case c: Connector =>
        associateUsage(c, resolveARef[Outlet](c.from, parents))
        associateUsage(c, resolveARef[Inlet](c.to, parents))
      case c: Constant =>
        associateUsage(c, resolveTypeExpression(c, c.typeEx, parents))
      case a: Adaptor =>
        associateUsage(a, resolveARef[Context](a.context, parents))
        resolveAuthorRefs(a, parents)
      case s: Streamlet =>
        resolveAuthorRefs(s, parents)
      case p: Projector =>
        resolveAuthorRefs(p, parents)
        p.repositories.foreach { ref => associateUsage(p, resolveARef[Repository](ref, parents)) }
      case r: Repository =>
        resolveAuthorRefs(r, parents)
      case s: Saga =>
        resolveAuthorRefs(s, parents)
      case d: Domain =>
        resolveAuthorRefs(d, parents)
      case a: Application =>
        resolveAuthorRefs(a, parents)
      case c: Context =>
        resolveAuthorRefs(c, parents)
      case e: Epic =>
        resolveAuthorRefs(e, parents)
      case uc: UseCase =>
        if uc.userStory.nonEmpty then associateUsage[User](uc, resolveARef(uc.userStory.user, parents))
        val interactions = uc.contents.filter[Interaction]
        if interactions.nonEmpty then resolveInteractions(uc, interactions, parents)
      case in: Input =>
        associateUsage(in, resolveATypeRef(in.takeIn, parents))
      case out: Output =>
        out.putOut match {
          case typ: TypeRef       => associateUsage(out, resolveATypeRef(typ, parents))
          case const: ConstantRef => associateUsage(out, resolveARef[Constant](const, parents))
          case _: LiteralString   => () // not a reference
        }
      case cg: ContainedGroup =>
        associateUsage(cg, resolveARef[Group](cg.group, parents))
      case _: NonReferencableDefinitions => () // These can't be referenced
      case _: NonDefinitionValues        => () // Neither can these values
      // case _ => () // NOTE: Never have this catchall! Want compile time errors!
    end match
  end process

  private def resolveAuthorRefs(definition: Parent & WithMetaData, parents: Parents): Unit =
    definition.authorRefs.foreach { item => associateUsage(definition, resolveARef[Author](item, parents)) }
  end resolveAuthorRefs

  private def resolveFunction(f: Function, parents: Parents): Unit = {
    addFunction(f)
    f.authorRefs.foreach { item => associateUsage[Author](f, resolveARef[Author](item, parents)) }
    f.input.foreach(resolveTypeExpression(f, _, parents))
    f.output.foreach(resolveTypeExpression(f, _, parents))
  }

  private def resolveType(typ: Type, parents: Parents): Resolution[Type] = {
    addType(typ)
    resolveTypeExpression(typ, typ.typEx, parents)
  }

  private def resolveTypeExpression(
    user: Definition,
    typ: TypeExpression,
    parents: Parents
  ): Resolution[Type] = {
    typ match {
      case UniqueId(_, entityPath) =>
        val resolution = resolveAPathId[Entity](entityPath, parents)
        associateUsage[Entity](user, resolution)
        None
      case AliasedTypeExpression(_, _, pathId) =>
        associateUsage[Type](user, resolveAPathId[Type](pathId, parents))
      case agg: AggregateTypeExpression =>
        agg.fields.foreach { (fld: Field) =>
          associateUsage[Type](fld, resolveTypeExpression(fld, fld.typeEx, parents))
        }
        None
      case EntityReferenceTypeExpression(_, entity) =>
        associateUsage[Entity](user, resolveAPathId[Entity](entity, parents))
        None
      case Alternation(_, of) =>
        of.foreach(resolveTypeExpression(user, _, parents))
        None
      case Sequence(_, of) =>
        resolveTypeExpression(user, of, parents)
      case Mapping(_, from, _) =>
        resolveTypeExpression(user, from, parents)
      case Set(_, of) =>
        resolveTypeExpression(user, of, parents)
      case Graph(_, of) =>
        resolveTypeExpression(user, of, parents)
      case Table(_, of, _) =>
        resolveTypeExpression(user, of, parents)
      case Replica(_, of) =>
        resolveTypeExpression(user, of, parents)
      case c: Cardinality =>
        associateUsage[Type](user, resolveTypeExpression(user, c.typeExp, parents))
      case _: Enumeration | _: NumericType | _: PredefinedType => None // no references
    }
  }

  private def resolveOnMessageClause(mc: OnMessageClause, parents: Parents): Unit = {
    val resolution = resolveARef[Type](mc.msg, parents)
    associateUsage[Type](mc, resolution)
    mc.from match
      case None => ()
      case Some(_, reference) =>
        val resolution = resolveARef[Definition](reference, parents)
        associateUsage[Definition](mc, resolution)
  }

  private def resolveStatement(statement: Statement, parents: Parents): Unit = {
    statement match {
      case SetStatement(_, field, _) =>
        associateUsage[Field](parents.head, resolveARef[Field](field, parents))
      case BecomeStatement(_, entity, handler) =>
        associateUsage[Entity](parents.head, resolveARef[Entity](entity, parents))
        associateUsage[Handler](parents.head, resolveARef[Handler](handler, parents))
      case FocusStatement(_, group) =>
        associateUsage[Group](parents.head, resolveARef[Group](group, parents))
      case ForEachStatement(_, ref, _) =>
        ref match {
          case ir: InletRef  => associateUsage[Inlet](parents.head, resolveAPathId[Inlet](ir.pathId, parents))
          case or: OutletRef => associateUsage[Outlet](parents.head, resolveAPathId[Outlet](or.pathId, parents))
          case fr: FieldRef  => associateUsage[Type](parents.head, resolveAPathId[Type](fr.pathId, parents))
        }
      case SendStatement(_, msg, portlet) =>
        associateUsage[Type](parents.head, resolveARef[Type](msg, parents))
        associateUsage[Portlet](parents.head, resolveARef[Portlet](portlet, parents))
      case MorphStatement(_, entity, state, message) =>
        associateUsage[Entity](parents.head, resolveARef[Entity](entity, parents))
        associateUsage[State](parents.head, resolveARef[State](state, parents))
        associateUsage[Type](parents.head, resolveARef[Type](message, parents))
      case TellStatement(_, msg, processorRef) =>
        associateUsage[Type](parents.head, resolveARef[Type](msg, parents))
        associateUsage(parents.head, resolveARef[Processor[?]](processorRef, parents))
      case CallStatement(_, func) =>
        associateUsage[Function](parents.head, resolveARef[Function](func, parents))
      case ReplyStatement(_, message) =>
        associateUsage[Type](parents.head, resolveARef[Type](message, parents))
      case _: CodeStatement       => () // no references
      case _: ReadStatement       => () // no references
      case _: WriteStatement      => () // no references
      case _: ArbitraryStatement  => () // no references
      case _: ErrorStatement      => () // no references
      case _: ReturnStatement     => () // no references
      case _: IfThenElseStatement => () // no references
      case _: StopStatement       => () // no references
    }
  }

  private def resolveInteractions(
    useCase: UseCase,
    interactions: Seq[Interaction],
    parentsAsSeq: Parents
  ): Unit = {
    for interaction <- interactions do {
      interaction match {
        case ArbitraryInteraction(_, from, _, to, _) =>
          associateUsage[Definition](useCase, resolveARef[Definition](from, parentsAsSeq))
          associateUsage[Definition](useCase, resolveARef[Definition](to, parentsAsSeq))
        case fi: FocusOnGroupInteraction =>
          associateUsage[Definition](useCase, resolveARef[User](fi.from, parentsAsSeq))
          associateUsage[Definition](useCase, resolveARef[Group](fi.to, parentsAsSeq))
        case fou: DirectUserToURLInteraction =>
          associateUsage[Definition](useCase, resolveARef[User](fou.from, parentsAsSeq))
        case ti: ShowOutputInteraction =>
          associateUsage[Definition](useCase, resolveARef[User](ti.to, parentsAsSeq))
          associateUsage[Definition](useCase, resolveARef[Output](ti.from, parentsAsSeq))
        case si: SelectInputInteraction =>
          associateUsage[Definition](useCase, resolveARef[User](si.from, parentsAsSeq))
          associateUsage[Definition](useCase, resolveARef[Input](si.to, parentsAsSeq))
        case pi: TakeInputInteraction =>
          associateUsage[Definition](useCase, resolveARef[User](pi.from, parentsAsSeq))
          associateUsage[Definition](useCase, resolveARef[Input](pi.to, parentsAsSeq))
        case si: SelfInteraction =>
          associateUsage[Definition](useCase, resolveARef[Definition](si.from, parentsAsSeq))
        case SendMessageInteraction(_, from, message, to, _) =>
          associateUsage[Definition](useCase, resolveARef[Definition](from, parentsAsSeq))
          associateUsage[Definition](useCase, resolveAMessageRef(message, parentsAsSeq))
          associateUsage[Definition](useCase, resolveARef[Definition](to, parentsAsSeq))
        case _: VagueInteraction       => () // no references
        case _: OptionalInteractions   => () // no references
        case _: ParallelInteractions   => () // no references
        case _: SequentialInteractions => () // no references
      }
    }
  }

  private def resolveARef[T <: Definition: ClassTag](
    ref: Reference[T],
    parents: Parents
  ): Resolution[T] = {
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
        notResolved[T](pathId, parents.headOption, "there are no parents of the found symbol")
        None
      case Some(parent) =>
        list match
          case Nil =>
            // List is empty so this is the NotFound case
            notResolved[T](
              pathId,
              parents.headOption,
              s"the sought name, '${pathId.value.last}', was not found in the symbol table,"
            )
            None
          case (d, pars) :: Nil if isSameKind[T](d) => // exact match
            // List just has one component and the types are the same so this is the Resolved case
            resolved[T](pathId, parent, d)
            Some(d.asInstanceOf[T] -> pars)
          case (d, _) :: Nil =>
            // List has one component but it's the wrong type
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
  private case class AnchorFoundInSymTab(anchor: Definition, anchor_parents: Parents) extends AnchorCase
  private case class AnchorFoundInParents(anchor: Definition, anchor_parents: Parents) extends AnchorCase
  private case class AnchorIsRoot(anchor: Definition, anchor_parents: Parents) extends AnchorCase

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
      case (anchor: Definition, anchor_parents: Parents) :: Nil =>
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
            // It's not an ancestor so let's try the symbol table
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
    anchor: Definition,
    anchor_parents: Parents
  ): Resolution[T] = {
    val stack = DefinitionStack.empty
    val parents_to_add = anchor_parents.reverse
    if anchor_parents.nonEmpty && anchor_parents.last.isRootContainer then stack.pushAll(parents_to_add.drop(1))
    else stack.pushAll(parents_to_add)
    stack.push(anchor)
    val pathIdStart = pathId.value.drop(1) // we already resolved the anchor
    var continue: Boolean = true
    var resolution: Resolution[T] = None
    var elementCounter: Int = pathIdStart.length
    for { soughtName: String <- pathIdStart if continue } do
      // Because names in a PathId are not unique, we can't use comparison against
      // the last name to determine if we're at the end of the names. Instead, we
      // count down the number of elements remaining
      elementCounter -= 1
      val isLastPathElement = elementCounter <= 0
      // Find matching item at head of stack and return the candidates derived
      // from it for the next loop. If nothing is returned, the head of stack
      // didn't match the sought name.
      findMatchingCandidate(soughtName, stack) match
        case None =>
          // None of the candidates match the name we're seeking, so this PathId doesn't match the model
          notResolved[T](
            pathId,
            stack.headOption,
            s"the name '$soughtName' was not found in ${stack.head.identify}"
          )
          continue = false
        case Some(definition) =>
          if isLastPathElement then
            // The soughtName is the last one in the pathId, no point continuing the loop
            continue = false
            // Since we are on the last element, let's try to find the match
            resolution = checkMatch[T](pathId, definition, parents)
          else
            // We have matched the current element and found some candidates for
            // the next round, so we must push and continue
            stack.push(definition)
          end if
      end match
    end for
    if !continue then
      // return the resolution
      resolution
    else
      stack.headOption match
        case Some(_: Root) if stack.size == 1 =>
          // then pop it off because RootContainers don't count, and we want to
          // rightfully return an empty sequence for "not found"
          stack.pop()
          // Convert parent stack to immutable sequence
          Some(stack.head.asInstanceOf[T] -> stack.tail.toSeq.asInstanceOf[Seq[Parent]])
        case Some(definition: T) =>
          // Not the root, just convert the result to immutable Seq
          Some(definition -> stack.tail.toSeq.asInstanceOf[Seq[Parent]])
        case Some(_) =>
          None
        case None =>
          None
      end match
    end if
  }

  private def resolved[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    pidDirectParent: Parent,
    definition: Definition
  ): T =
    // A candidate was found, and it has the same type as expected
    val t = definition.asInstanceOf[T]
    refMap.add[T](pathId, pidDirectParent, t)
    associateUsage(pidDirectParent, t)
    if io.options.debug then
      messages.add(
        Messages.info(
          s"Path Identifier ${pathId.format} in ${pidDirectParent.identify} resolved to ${definition.identify}",
          pathId.loc
        )
      )
    end if
    if io.options.debug then println(s"Resolved: ${pathId.format} ==> ${t.identify}")
    t
  end resolved

  private def wrongType[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    container: Definition,
    foundDef: WithIdentifier
  ): Unit =
    val referTo = classTag[T].runtimeClass.getSimpleName
    val message = s"Path '${pathId.format}' resolved to ${foundDef.identifyWithLoc}," +
      s" in ${container.identify}, but ${article(referTo)} was expected"
    messages.addError(pathId.loc, message)
    if io.options.debug then
      println(s"WrongType: ${pathId.format} ==> ${foundDef.identifyWithLoc} not ${article(referTo)}")
    end if

  end wrongType

  private def notResolved[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    container: Option[Definition],
    why: String = ""
  ): Unit =
    val tc = classTag[T].runtimeClass
    val message = container match
      case None =>
        s"Path '${pathId.format}' is not resolvable, because it has no container"
      case Some(definition) =>
        s"Path '${pathId.format}' was not resolved, in ${definition.identify}${
            if why.isEmpty then "\n"
            else "\nbecause " + why + "\n"
          }"

    val referTo = tc.getSimpleName
    messages.addError(
      pathId.loc,
      message + {
        if referTo.nonEmpty then s"and it should refer to ${article(referTo)}"
        else ""
      }
    )
    if io.options.debug then println(s"Unresolved: ${pathId.format} ==> ???")
  end notResolved

  private def checkMatch[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    definition: Definition,
    parents: Parents
  ): Resolution[T] =
    parents.headOption match
      case Some(parent) =>
        if isSameKind[T](definition) then
          // we found a matching definition in both name and type
          val t: T = resolved[T](pathId, parent, definition)
          // return the resolution
          Some(t -> symbols.parentsOf(t))
        else
          // the name matches, the type does not, emit error
          wrongType[T](pathId, parent, definition)
          None
        end if
      case None =>
        // No parent of the node, shouldn't happen!
        notResolved[T](pathId, None, s"because ${definition.identify} does have parents!")
        None
    end match
  end checkMatch

  private def findMatchingCandidate(
    soughtName: String,
    defStack: DefinitionStack
  ): Option[Definition] =
    require(defStack.nonEmpty, "No stack to consider in findCandidates")
    defStack.headOption match
      case None =>
        Option.empty[Definition] // nothing to search to provide candidates
      case Some(head) =>
        val candidates: Definitions =
          head match
            case st: State =>
              // We found a state so the fields of the state
              // the contained handlers and the fields of the state's data
              candidatesFromPathIdentifier[Type](st.typ.pathId, defStack)
            case omc: OnMessageClause if omc.msg.id.nonEmpty =>
              // we found an onClause that references a named message
              // need to push that message's path on the name stack
              candidatesFromPathIdentifier[Type](omc.msg.pathId, defStack)
            case field: Field =>
              candidatesFromTypeExpression(field.typeEx, defStack)
            case constant: Constant =>
              candidatesFromTypeExpression(constant.typeEx, defStack)
            case typ: Type =>
              candidatesFromTypeExpression(typ.typEx, defStack)
            case inlet: Inlet =>
              candidatesFromPathIdentifier[Type](inlet.type_.pathId, defStack)
            case outlet: Outlet =>
              candidatesFromPathIdentifier[Type](outlet.type_.pathId, defStack)
            case include: Include[?] =>
              candidatesFromContents(include.contents.definitions.toContents).asInstanceOf[Definitions]
            case function: Function =>
              function.input.map(_.contents.filter[Field]).asInstanceOf[Definitions] ++
                function.output.map(_.contents.filter[Field]).asInstanceOf[Definitions] ++
                function.contents.definitions
            case vital: VitalDefinition[?] =>
              vital.contents.toSeq.flatMap {
                case include: Include[ContentValues] @unchecked => include.contents.definitions
                case value: Definition                          => Seq(value)
                case _                                          => Seq.empty[Definition]
              }
            case p: Parent =>
              p.contents.definitions
            case _ =>
              // No match so no candidates
              Seq.empty[Definition]
          end match
        candidates.find(_.id.value == soughtName)
    end match
  end findMatchingCandidate

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
      case Some((typ: Type, _: Parents)) =>
        keyword match
          case Keyword.type_ | "" => resolution // this is generic, any type so just pass the result
          case Keyword.command    => handleTypeResolution(typ, CommandCase, resolution)
          case Keyword.query      => handleTypeResolution(typ, QueryCase, resolution)
          case Keyword.event      => handleTypeResolution(typ, EventCase, resolution)
          case Keyword.result     => handleTypeResolution(typ, ResultCase, resolution)
          case Keyword.record     => handleTypeResolution(typ, RecordCase, resolution)
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
                require(false, s"Type $typ is not a")
                None
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
      notResolved[T](pathId, parents.headOption, "the PathId is empty")
      None
    else
      // If we already resolved this one, return it
      val result =
        refMap.definitionOf[T](pathId, parents.head) match
          case Some(definition) =>
            Some(definition -> symbols.parentsOf(definition))
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
                    parents.headOption,
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
                    parents.headOption,
                    s"the PathId is invalid since it's first element, $topName, does not exist in the model"
                  )
                  None
                case AnchorNotFoundAnywhere(_) =>
                  notResolved(pathId, parents.headOption, "PathID anchor not found")
                  None
                case AnchorIsRoot(anchor, anchor_parents) =>
                  // The first name in the path id was "Root" so start from there
                  resolvePathFromAnchor[T](pathId, parents, anchor, anchor_parents)
                case AnchorIsAmbiguous(_, list) =>
                  // The anchor is ambiguous so generate that message
                  ambiguous[T](pathId, list, Some("The top node in the Path Id is the ambiguous one"))
                  None
              end match
        end match
      result
    end if
  end resolveAPathId

  private def ambiguous[T <: Definition: ClassTag](
    pid: PathIdentifier,
    list: List[SymTabItem],
    context: Option[String] = None
  ): Seq[WithIdentifier] = {
    // Extract all the definitions that were found
    val definitions = list.map(_._1)
    val allDifferent = definitions.map(_.kind).distinct.sizeIs ==
      definitions.size
    val expectedClass = classTag[T].runtimeClass
    definitions.headOption match {
      case Some(head) if head.isAnonymous && allDifferent =>
        // pick the one that is the right type or the first one
        list.find(_._1.getClass == expectedClass) match {
          case Some((definition, parents)) => definition +: parents
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
        Seq.empty[WithIdentifier]
    }
  }

  private val vowels: String = "aAeEiIoOuU"

  private def article(thing: String): String = {
    val article = if vowels.contains(thing.head) then "an" else "a"
    s"$article $thing"
  }

  private def candidatesFromPathIdentifier[T <: Definition: ClassTag](
    pid: PathIdentifier,
    defStack: DefinitionStack
  ): Definitions =
    // Recursively resolve this PathIdentifier
    val resolution: Resolution[T] = resolveAPathId[T](pid, defStack.toParentsSeq)
    resolution match
      case None                                             => Seq.empty[Definition]
      case Some((definition: Definition, parents: Parents)) =>
        // if we found the definition
        // Replace the parent stack with the resolved one
        defStack.clear()
        defStack.pushAll(parents.reverse)

        // Return the name and candidates we should next search for
        definition match
          case foundType: Parent =>
            defStack.push(foundType)
            foundType.contents.definitions
          case definition: T =>
            Seq(definition)
        end match
    end match
  end candidatesFromPathIdentifier

  private def candidatesFromTypeExpression(
    typEx: TypeExpression,
    parentStack: DefinitionStack
  ): Definitions = {
    typEx match {
      case a: Aggregation => a.fields
      // if we're at a field composed of more fields, then those fields
      // are what we are looking for
      case Enumeration(_, enumerators) =>
        // if we're at an enumeration type then the numerators are candidates
        enumerators.toSeq
      case a: AggregateUseCaseTypeExpression =>
        // Any kind of Aggregate's fields are candidates for resolution
        a.fields
      case AliasedTypeExpression(_, _, pid) =>
        // if we're at a field that references another type then the candidates
        // are that type's fields. To solve this we need to push
        // that type's path on the name stack to be resolved
        candidatesFromPathIdentifier[Type](pid, parentStack)
      case EntityReferenceTypeExpression(_, entityRef) =>
        candidatesFromPathIdentifier[Entity](entityRef, parentStack)
      case _ =>
        // We cannot descend into any other type expression
        Seq.empty[Definition]
    }
  }

  private def candidatesFromContents(
    contents: Contents[RiddlValue]
  ): Contents[Definition] =
    contents.flatMap { item =>
      item match
        case Include(_, _, contents) =>
          // NOTE: An included file can include another file at the same definitional level.
          // NOTE: We need to recursively descend that stack.  An include in a nested definitional level
          // NOTE: will not be picked up by contents.includes because it would be inside another definition.
          // NOTE: So we take the WithIdentifiers from the contents as well as from the includes
          val nested = candidatesFromContents(contents.includes.toContents)
          val current = contents.definitions
          current ++ nested
        case definition: Definition =>
          Seq(definition)
        case _ =>
          Seq.empty
      end match
    }
  end candidatesFromContents

  // private def candidatesFromStateTypeRef(typeRef: TypeRef, parents: Parents): Contents[Definition] = {
  //   val resolution: Resolution[Type] = resolveATypeRef(typeRef, parents)
  //   resolution match {
  //     case None => Contents.empty[Definition] // not found
  //     case Some((typ: Type, _: Parents)) =>
  //       typ.typEx match {
  //         case agg: AggregateTypeExpression => agg.fields.toContents
  //         case _                            => Contents.empty[Definition]
  //       }
  //   }
  // }

  // private def findResolution(soughtName: String, candidate: Definition): Boolean = {
  //   candidate match {
  //     case omc: OnMessageClause if omc.msg.id.nonEmpty =>
  //       omc.msg.id.getOrElse(Identifier.empty).value == soughtName
  //     case other: Definition =>
  //       other.id.value == soughtName
  //   }
  // }
}
