/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.resolve

import com.reactific.riddl.language.AST.{Entity, *}
import com.reactific.riddl.language.{CommonOptions, Messages}
import com.reactific.riddl.passes.{Pass, PassInfo, PassInput, PassOutput}
import com.reactific.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.reactific.riddl.utils.SeqHelpers.SeqHelpers

import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

case class ResolutionOutput(
  messages: Messages.Messages = Messages.empty,
  refMap: ReferenceMap = ReferenceMap.empty,
  kindMap: KindMap = KindMap.empty,
  usage: Usages = Usages.empty
) extends PassOutput {}

object ResolutionPass extends PassInfo {
  val name: String = "resolution"
}

/** The Reference Resolution Pass */
case class ResolutionPass(input: PassInput) extends Pass(input) with UsageResolution {

  override def name: String = ResolutionPass.name

  requires(SymbolsPass)

  val commonOptions: CommonOptions = input.commonOptions
  val messages: Messages.Accumulator = Messages.Accumulator(input.commonOptions)
  val refMap: ReferenceMap = ReferenceMap(messages)
  val kindMap: KindMap = KindMap()
  val symbols: SymbolsOutput = input.outputOf[SymbolsOutput](SymbolsPass.name)

  override def result: ResolutionOutput =
    ResolutionOutput(messages.toMessages, refMap, kindMap, Usages(uses, usedBy))

  override def close(): Unit = ()

  def postProcess(root: RootContainer): Unit = {
    checkUnused()
  }

  def process(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    kindMap.add(definition)
    val parentsAsSeq: Seq[Definition] = definition +: parents.toSeq
    definition match {
      case ad: AggregateDefinition =>
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
      case h: Handler =>
        h.authors.foreach(resolveARef[Author](_, parentsAsSeq))
      case e: Entity =>
        e.authors.foreach(resolveARef[Author](_, parentsAsSeq))
        addEntity(e)
      case s: State =>
        resolveARef[Type](s.typ, parentsAsSeq)
      case f: Function =>
        resolveFunction(f, parentsAsSeq)
      case i: Inlet =>
        resolveARef[Type](i.type_, parentsAsSeq)
      case o: Outlet =>
        resolveARef[Type](o.type_, parentsAsSeq)
      case c: Connector =>
        resolveConnector(c, parentsAsSeq)
      case c: Constant =>
        resolveTypeExpression(c.typeEx, parentsAsSeq)
      case a: Adaptor =>
        resolveARef[Context](a.context, parentsAsSeq)
        a.authors.foreach(resolveARef[Author](_, parentsAsSeq))
      case s: Streamlet =>
        s.authors.foreach(resolveARef[Author](_, parentsAsSeq))
      case p: Projector =>
        p.authors.foreach(resolveARef[Author](_, parentsAsSeq))
      case r: Repository =>
        r.authors.foreach(resolveARef[Author](_, parentsAsSeq))
      case s: Saga =>
        s.authors.foreach(resolveARef[Author](_, parentsAsSeq))
      case r: Replica =>
        resolveTypeExpression(r.typeExp, parentsAsSeq)
      case d: Domain =>
        d.authors.foreach(resolveARef[Author](_, parentsAsSeq))
      case a: Application =>
        a.authors.foreach(resolveARef[Author](_, parentsAsSeq))
      case c: Context =>
        c.authors.foreach(resolveARef[Author](_, parentsAsSeq))
      case e: Epic =>
        e.authors.foreach(resolveARef[Author](_, parentsAsSeq))
      case uc: UseCase =>
        uc.userStory.foreach(userStory => resolveARef[User](userStory.user, parentsAsSeq))
      case in: Input =>
        resolveARef[Type](in.putIn, parentsAsSeq)
      case out: Output =>
        resolveARef[Type](out.putOut, parentsAsSeq)
      case gi: GenericInteraction =>
        gi match {
          case ArbitraryInteraction(_, _, from, _, to, _, _) =>
            resolveARef[Definition](from, parentsAsSeq)
            resolveARef[Definition](to, parentsAsSeq)
          case ti: ShowOutputInteraction =>
            resolveARef[User](ti.to, parentsAsSeq)
            resolveARef[Output](ti.from, parentsAsSeq)
          case pi: TakeInputInteraction =>
            resolveARef[User](pi.from, parentsAsSeq)
            resolveARef[Input](pi.to, parentsAsSeq)
          case si: SelfInteraction =>
            resolveARef[Definition](si.from, parentsAsSeq)
        }
      case _: Author                 => () // no references
      case _: User                   => () // no references
      case _: Enumerator             => () // no references
      case _: Group                  => () // no references
      case _: Include[_]             => () // no references
      case _: OptionalInteractions   => () // no references
      case _: ParallelInteractions   => () // no references
      case _: SequentialInteractions => () // no references
      case _: VagueInteraction       => () // no references
      case _: RootContainer          => () // no references
      case _: SagaStep               => () // no references
      case _: Term                   => () // no references
      case i: Invariant              => () // no references
      // case _ => () // NOTE: Never have this catchall! Want compile time errors.
    }
  }

  private def resolveFunction(f: Function, parents: Seq[Definition]): Unit = {
    f.authors.foreach(resolveARef[Author](_, parents))
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
      case AliasedTypeExpression(_, pathId) =>
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
      case Mapping(_, from, to) =>
        resolveTypeExpression(from, parents)
      case Set(_, of) =>
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
      case Some(reference) =>
        resolveARef[Definition](reference, parents)
  }

  private def resolveOnClauses(oc: OnClause, parents: Seq[Definition]): Unit = {
    resolveStatements(oc.statements, oc +: parents)
  }

  private def resolveStatements(statements: Seq[Statement], parents: Seq[Definition]): Unit = {
    statements.foreach(resolveStatement(_, parents))
  }

  private def resolveStatement(statement: Statement, parents: Seq[Definition]): Unit = {
    // TODO: Finish implementation of resolutions for statements
    statement match {
      case ss: SetStatement =>
        resolveARef[Field](ss.field, parents)
      case BecomeStatement(loc, entity, handler) =>
        resolveARef[Entity](entity, parents)
      case ForEachStatement(loc, ref, do_) =>
        resolveAPathId[Type](ref, parents)
      case SendStatement(loc, msg, portlet) =>
        resolveARef[Type](msg, parents)
      case MorphStatement(loc, entity, state, message) =>
        resolveARef[Entity](entity, parents)
        resolveARef[State](state, parents)
        resolveARef[Type](message, parents)
      case TellStatement(loc, msg, processorRef) =>
        resolveARef[Type](msg, parents)
        resolveARef[Processor[?, ?]](processorRef, parents)
      case CallStatement(loc, func) =>
        resolveARef[Function](func, parents)
      case ReplyStatement(loc, message) =>
        resolveARef[Type](message, parents)
      case _: ArbitraryStatement  => ()
      case _: ErrorStatement      => ()
      case _: ReturnStatement     => ()
      case _: IfThenElseStatement => ()
      case _: StopStatement       => ()
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
          case Nil =>
            notResolved[T](pathId, parents)
            Seq.empty
          case (d, pars) :: Nil if isSameKind[T](d) => // exact match
            // Found
            resolved[T](pathId, parent, d)
            d +: pars
          case (d, _) :: Nil =>
            wrongType[T](pathId, parent, d)
            Seq.empty
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

  def canFindTopPathIdNameInParents[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Seq[Definition]
  ): Boolean = {
    pathId.value.headOption match {
      case Some(topName) =>
        // The first path name of a multi-part path must be the name of one
        // of the parents above the location the pathId is used at.  If not
        // there is no point doing an expensive search, just fail fast.
        if parents.exists(_.id.value == topName) then
          // Okay, that worked out. The PID is valie
          true
        else
          notResolved(
            pathId,
            parents,
            s"the PathId is invalid since the first element of the PathId is not the name of a parent of ${parents.head.identify}"
          )
          false
      case None =>
        // No top name, can't find it.
        messages.addSevere(pathId.loc, "Empty path id")
        false
    }
  }

  def checkThatPathIdMatchesFoundParentStack[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Seq[Definition],
    maybeResult: Seq[Definition]
  ): Boolean = {
    pathId.value.headOption match {
      case Some(topName) =>
        val maybeNames = maybeResult.reverse.map(_.id.value) // drop "Root"
        val matchingPathPortion = maybeNames.takeRight(pathId.value.length)
        val zipped = matchingPathPortion.zip[String](pathId.value)
        val allMatch = (for {
          (path: String, pid: String) <- zipped
        } yield {
          path == pid
        }).forall(_ == true)
        if !allMatch then
          notResolved[T](
            pathId,
            parents,
            s"the search through the parents ended at:\n  ${maybeNames.mkString(".")}\n" +
              s"and there was no match to the elements of the PathId:\n  ${pathId.format}"
          )
          false
        else true
      case None =>
        messages.addSevere(pathId.loc, "Empty path id")
        false
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  private def resolveAPathId[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Seq[Definition]
  ): Seq[Definition] = {
    if pathId.value.isEmpty then
      // The pathId is empty, can't resolve that
      notResolved[T](pathId, parents, "the Path Identifier is empty")
      Seq.empty[Definition]
    else if pathId.value.size == 1 then
      // Easy case, just search the symtab and see if we can rule it out or
      // find it ambiguous
      searchSymbolTable[T](pathId, parents)
    else if !canFindTopPathIdNameInParents[T](pathId, parents) then
      // The pathId isn't empty since its top name doesn't match any of the
      // parent's names. Errors emitted by canFindTopPathIdNameInParents
      Seq.empty
    else
      // Capture the first name we're looking for
      val topName = pathId.value.head
      // Define a function to identify the starting point in the parents
      def startingPoint(defn: Definition): Boolean = {
        defn.id.value == topName || defn.resolveNameTo(topName).nonEmpty
      }
      // Drop parents until starting point found
      val newParents = parents.dropUntil(startingPoint)

      // If we dropped all the parents then the path isn't valid
      if newParents.isEmpty then
        // Signal not found
        Seq.empty[Definition]
      else
        // we found the starting point, and adjusted the parent stack correspondingly
        // use resolveRelativePath to descend through names
        val maybeFound = resolveRelativePath(pathId, newParents)

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

  private def resolved[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    pidDirectParent: Definition,
    definition: Definition
  ): Option[T] = {
    // a candidate was found and it has the same type as expected
    val t = definition.asInstanceOf[T]
    refMap.add[T](pathId, pidDirectParent, t)
    associateUsage(pidDirectParent, t)
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
  ): Unit = {
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
  }

  private def ambiguous[T <: Definition: ClassTag](
    pid: PathIdentifier,
    list: List[(Definition, Seq[Definition])]
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
        messages.addError(pid.loc, s"Path reference '${pid.format}' is ambiguous. Definitions are:\n$ambiguity")
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
        case None       => Seq.empty[Definition] // shouldn't happen?
        case Some(head) => head.contents

    } else {
      // Couldn't resolve it, error already issued, signal termination of the search
      Seq.empty[Definition]
    }
  }

  private def candidatesFromTypeEx(typEx: TypeExpression, parentStack: mutable.Stack[Definition]): Seq[Definition] = {
    typEx match {
      case a: Aggregation => a.contents
      // if we're at a field composed of more fields, then those fields
      // what we are looking for
      case Enumeration(_, enumerators) =>
        // if we're at an enumeration type then the numerators are candidates
        enumerators
      case a: AggregateUseCaseTypeExpression =>
        // Any kind of Aggregate's fields are candidates for resolution
        a.contents
      case AliasedTypeExpression(_, pid) =>
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
  ): Seq[Definition] = {
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
              candidatesFromTypeEx(field.typeEx, parentStack)
            case c: Constant =>
              candidatesFromTypeEx(c.typeEx, parentStack)
            case t: Type =>
              candidatesFromTypeEx(t.typ, parentStack)
            case func: Function =>
              val inputs: Aggregation = func.input.getOrElse(Aggregation.empty())
              val outputs: Aggregation = func.output.getOrElse(Aggregation.empty())
              // If we're at a Function node, the functions input parameters
              // are the candidates to search next
              inputs.contents ++ outputs.contents
            case d: Definition =>
              d.contents.flatMap {
                case Include(_, contents, _) =>
                  contents
                case d: Definition =>
                  Seq(d)
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

  /** Resolve a Relative PathIdentifier. If the path is already resolved or it has no empty components then we can
    * resolve it from the map or the symbol table.
    *
    * @param pid
    *   The path to consider
    * @param parents
    *   The parent stack to provide the context from which the search starts
    * @return
    *   Either an error or a definition
    */
  private def resolveRelativePath(
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): Seq[Definition] = {
    // Initialize the visited stack. This is used to detect looping. We
    // should never visit the same definition twice but if we do we will
    // catch it below.
    val visitedStack = mutable.Stack.empty[Definition]

    // Implicit definitions don't have names so they don't count in the stack
    val namedParents = parents.filterNot(_.isImplicit).reverse

    // Build the parent stack from the named parents
    val parentStack = mutable.Stack.empty[Definition]
    parentStack.pushAll(namedParents)

    // Build the name stack from the PathIdentifier provided
    val nameStack = mutable.Stack.empty[String]
    nameStack.pushAll(pid.value.reverse)

    // Loop over the names in the stack. Note that mutable stacks are used
    // here because the algorithm can adjust them as it finds intermediary
    // definitions. If the name stack becomes empty, we're done searching.
    while nameStack.nonEmpty do {
      // Pop the name we're currently looking for and save it
      val soughtName = nameStack.pop()

      parentStack.headOption match
        case None =>
          messages.addError(
            pid.loc,
            s"To many names in path identifier for parents"
          )
        case Some(definition) =>
          // We have a name to search for if the parent stack is not empty

          // If we have already visited this definition, its a looping error
          if visitedStack.contains(definition) then {
            // Generate the error message
            messages.addError(
              pid.loc,
              msg = s"""Path resolution encountered a loop at ${definition.identify}
                   |  for name '$soughtName' when resolving ${pid.format}
                   |  in definition context: ${parents.map(_.identify).mkString("\n    ", "\n    ", "\n")}
                   |""".stripMargin
            )
            // Signal we're done searching with no result
            parentStack.clear()
          } else {
            // Look where we are and find the candidates that could
            // possibly match soughtName
            val candidates = findCandidates(parentStack)

            // Now find the match, if any, and handle appropriately
            val found = candidates.find(candidate => findResolution(soughtName, candidate))
            found match {
              case Some(q: Definition) =>
                // found the named item, and it is a Container, so put it on
                // the stack in case there are more things to resolve
                parentStack.push(q)

                // Push the definition on the visited stack because we
                // already resolved this one and looked for candidates, no
                // point looping through here again.
                visitedStack.push(definition)

              case None =>
              // No search result, there may be more things to find in
              // the next iteration
            }
          }
    }

    // if there is a single thing left on the stack and that things is
    // a RootContainer
    parentStack.headOption match
      case Some(head: RootContainer) if parentStack.size == 1 =>
        // then pop it off because RootContainers don't count and we want to
        // rightfully return an empty sequence for "not found"
        parentStack.pop()
        // Convert parent stack to immutable sequence
        parentStack.toSeq
      case Some(head) =>
        // Not the root, just convert the result to immutable Seq
        parentStack.toSeq
      case None =>
        parentStack.toSeq // empty == fail
  }
}
