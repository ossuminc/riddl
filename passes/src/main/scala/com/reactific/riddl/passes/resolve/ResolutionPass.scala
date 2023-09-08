/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.resolve

import com.reactific.riddl.language.AST.*
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
  val typeMap: TypeMap = TypeMap()
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
      case f: Field =>
        f.typeEx match {
          case AliasedTypeExpression(_, pathId) =>
            resolveAPathId[Type](pathId, parentsAsSeq)
          case EntityReferenceTypeExpression(_, entity) =>
            resolveAPathId[Entity](entity, parentsAsSeq)
          case UniqueId(_, entity) =>
            resolveAPathId[Entity](entity, parentsAsSeq)
          case _ =>
        }
      case t: Type =>
        resolveType(t, parentsAsSeq)
      case ic: OnInitClause =>
        ic.statements.foreach(resolveStatement(_, parentsAsSeq))
      case tc: OnTerminationClause =>
        tc.statements.foreach(resolveStatement(_, parentsAsSeq))
      case oc: OnOtherClause =>
        oc.statements.foreach(resolveStatement(_, parentsAsSeq))
      case mc: OnMessageClause =>
        resolveOnMessageClause(mc, parentsAsSeq)
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
      case i: Invariant =>
        resolveMaybeValue(i.condition, parentsAsSeq)
      case c: Constant =>
        resolveTypeExpression(c.typeEx, parentsAsSeq)
        resolveValue(c.value, parentsAsSeq)
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
      case ti: TakeOutputInteraction =>
        resolveARef[User](ti.to, parentsAsSeq)
        resolveARef[Output](ti.from, parentsAsSeq)
      case pi: PutInputInteraction =>
        resolveARef[User](pi.from, parentsAsSeq)
        resolveARef[Input](pi.to, parentsAsSeq)
      case si: SelfInteraction =>
        resolveARef[Definition](si.from, parentsAsSeq)
      case _: Author                 => () // no references
      case _: User                   => () // no references
      case _: Enumerator             => () // no references
      case _: Group                  => () // no references
      case _: Include[_]             => () // no references
      case _: OptionalInteractions   => () // no references
      case _: ParallelInteractions   => () // no references
      case _: RootContainer          => () // no references
      case _: SagaStep               => () // no references
      case _: SequentialInteractions => () // no references
      case _: Term                   => () // no references
      case _: Statement              => () // handled in OnClause
      // case _ => () // NOTE: Never have this catchall! Want compile time errors.
    }
  }

  private def resolveFunction(f: Function, parents: Seq[Definition]): Unit = {
    f.authors.foreach(resolveARef[Author](_, parents))
    addFunction(f)
    f.input.foreach(resolveTypeExpression(_, parents))
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
      case AliasedTypeExpression(_, otherType) =>
        resolveAPathId[Type](otherType, parents)
      case _ => ()
    }
  }

  private def resolveOnMessageClause(mc: OnMessageClause, parents: Seq[Definition]): Unit = {
    resolveARef[Type](mc.msg, parents)
    if mc.from.nonEmpty then {
      resolveARef[Definition](mc.from.get, parents)
    }
    mc.statements.foreach(resolveStatement(_, parents))
  }

  private def resolveStatement(statement: Statement, parents: Seq[Definition]): Unit = {
    val pars: Seq[Definition] = statement +: parents
    statement match {
      case SetStatement(_, _, ref: FieldRef, value: Value) =>
        resolveARef[Field](ref, pars)
        resolveValue(value, pars)
      case SendStatement(_, _, msg: MessageValue, ref: PortletRef[Portlet]) =>
        resolveARef[Type](msg.msg, pars)
        resolveARef[Portlet](ref, pars)
        msg.args.args.values.foreach(resolveValue(_, pars))
      case TellStatement(_, _, msg: MessageValue, ref: EntityRef) =>
        resolveARef[Type](msg.msg, pars)
        resolveARef[Entity](ref, pars)
        msg.args.args.values.foreach(resolveValue(_, parents))
      case FunctionCallStatement(_, _, pid: PathIdentifier, args: ArgumentValues) =>
        resolveAPathId[Function](pid, pars)
        args.args.values.foreach(resolveValue(_, pars))
      case MorphStatement(_, _, ref: EntityRef, state: StateRef, newValue: Value) =>
        resolveARef[Entity](ref, pars)
        resolveARef[State](state, pars)
        resolveValue(newValue, pars)
      case BecomeStatement(_, _, entity: EntityRef, handler: HandlerRef) =>
        resolveARef[Entity](entity, pars)
        resolveARef[Handler](handler, pars)
      case _: ErrorStatement     => () // no references
      case _: ArbitraryStatement => () // no references
    }
  }

  private def resolveMaybeValue(maybeExpr: Option[Value], parents: Seq[Definition]): Unit = {
    maybeExpr.foreach(resolveValue(_, parents))
  }

  private def resolveValue(expr: Value, parents: Seq[Definition]): Unit = {
    expr match {
      case FunctionCallCondition(_, func, args) =>
        resolveAPathId[Function](func.pathId, parents)
        args.args.values.foreach(resolveValue(_, parents))
      case ValueCondition(_, path) =>
        resolveAPathId[Field](path, parents)
      case NotCondition(_, condition) =>
        resolveValue(condition, parents)
      case mc: MultiCondition =>
        mc.conditions.foreach(resolveValue(_, parents))
      case Comparison(_, _, expr1, expr2) =>
        resolveValue(expr1, parents); resolveValue(expr2, parents)
      case FieldValue(_, pid) =>
        resolveAPathId[Field](pid, parents)
      case ConstantValue(_, pid) =>
        resolveAPathId[Constant](pid, parents)
      case MessageValue(_, msg, args) =>
        resolveAPathId[Type](msg.pathId, parents)
        args.args.values.foreach(resolveValue(_, parents))
/*
        if path.nonEmpty then
          path match {
            case t: Type =>
              t.typ match {
                case aucte: AggregateUseCaseTypeExpression =>
                // okay
                case _ =>
                  messages.error(
                    s"PathId '${msg.pathId}' refers to a non-message aggregate Type'"
                  )
              }
            case x: Definition =>
              messages.error(s"PathId '${msg.pathId}' refers to a ' ${x.kind} but should be a Message Type")
          }*/

      case FunctionCallValue(_, func, args) =>
        resolveAPathId[Function](func.pathId, parents)
        args.args.values.foreach(resolveValue(_, parents))
      case _ =>
      // ArbitraryValue, true, false, integer, decimal, etc. all have no pid
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

  private def resolveAPathId[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parents: Seq[Definition]
  ): Seq[Definition] = {
    val parent = parents.head
    val maybeFound: Seq[Definition] = if pathId.value.isEmpty then {
      notResolved[Definition](pathId, parents.head)
      Seq.empty[Definition]
    } else {
      // Capture the first name we're looking for
      val topName = pathId.value.head

      // Define a function to identify the starting point in the parents
      def startingPoint(defn: Definition): Boolean = {
        defn.id.value == topName || defn.resolveNameTo(topName).nonEmpty
      }

      // Drop parents until starting point found
      val newParents = parents.dropUntil(startingPoint)

      // If we dropped all the parents then the path isn't valid
      if newParents.isEmpty then {
        // Signal not found
        Seq.empty[Definition]
      } else {
        // we found the starting point, and adjusted the parent stack correspondingly
        // use resolveRelativePath to descend through names
        resolveRelativePath(pathId, newParents)
      }
    }

    val isFound: Boolean = if maybeFound.nonEmpty then {
      val head = maybeFound.head
      val hasRightName = head.id.value == pathId.value.last
      val hasSameType = isSameKind[T](head)
      if hasRightName then {
        if hasSameType then {
          // a candidate was found and it has the same type as expected
          resolved[T](pathId, parent, head)
        } else {
          wrongType[T](pathId, parent, head)
        }
        true
      } else {
        false
      }
    } else { false }

    if !isFound then {
      val symTabCompatibleNameSearch = pathId.value.reverse
      val list = symbols.lookupParentage(symTabCompatibleNameSearch)
      list match {
        case Nil =>
          notResolved[T](pathId, parent)
          maybeFound
        case (d, _) :: Nil if isSameKind[T](d) => // exact match
          // Found
          resolved[T](pathId, parent, d)
          maybeFound
        case (d, _) :: Nil =>
          wrongType[T](pathId, parent, d)
          Seq.empty[Definition]
        case list =>
          ambiguous[T](pathId, list)
      }
    } else {
      maybeFound
    }
  }

  private def resolved[T <: Definition: ClassTag](
    pathId: PathIdentifier,
    parent: Definition,
    definition: Definition
  ): Option[T] = {
    // a candidate was found and it has the same type as expected
    val t = definition.asInstanceOf[T]
    refMap.add[T](pathId, parent, t)
    associateUsage(parent, t)
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
    container: Definition
  ): Unit = {
    val tc = classTag[T].runtimeClass
    val message = s"Path '${pid.format}' was not resolved," +
      s" in ${container.identify}"
    val referTo = tc.getSimpleName
    messages.addError(
      pid.loc,
      message + {
        if referTo.nonEmpty then s", but should refer to ${article(referTo)}"
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
    if allDifferent || definitions.head.isImplicit then {
      // pick the one that is the right type or the first one
      list.find(_._1.getClass == expectedClass) match {
        case Some((defn, parents)) => defn +: parents
        case None                  => list.head._1 +: list.head._2
      }
    } else {
      val ambiguity =
        list
          .map { case (definition, parents) =>
            "  " + parents.reverse.map(_.id.value).mkString(".") + "." +
              definition.id.value + " (" + definition.loc + ")"
          }
          .mkString("\n")

      messages.addError(
        pid.loc,
        s"Path reference '${pid.format}' is ambiguous. Definitions are:\n$ambiguity"
      )
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
      parentStack.head.contents
    } else {
      // Couldn't resolve it, error already issued, signal termination of the search
      Seq.empty[Definition]
    }
  }

  private def candidatesFromTypeEx(typEx: TypeExpression, parentStack: mutable.Stack[Definition]): Seq[Definition] = {
    typEx match {
      case Aggregation(_, fields) =>
        // if we're at a field composed of more fields, then those fields
        // what we are looking for
        fields
      case Enumeration(_, enumerators) =>
        // if we're at an enumeration type then the numerators are candidates
        enumerators
      case AggregateUseCaseTypeExpression(_, _, fields) =>
        // Any kind of Aggregate's fields are candidates for resolution
        fields
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
      parentStack.head match {
        case st: State =>
          // If we're at a state definition then it references a type for
          // its fields so we need to push that typeRef's name on the name stack.
          adjustStacksForPid[Type](st.typ.pathId, parentStack)
        case oc: OnMessageClause =>
          // if we're at an onClause that references a named message then we
          // need to push that message's path on the name stack
          adjustStacksForPid[Type](oc.msg.pathId, parentStack)
        case f: Field =>
          candidatesFromTypeEx(f.typeEx, parentStack)
        case c: Constant =>
          candidatesFromTypeEx(c.typeEx, parentStack)
        case t: Type =>
          candidatesFromTypeEx(t.typ, parentStack)
        case f: Function =>
          // If we're at a Function node, the functions input parameters
          // are the candidates to search next
          f.input.get.fields ++ f.output.get.fields
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
      case omc: OnMessageClause if omc.msg.id.nonEmpty => omc.msg.id.get.value == soughtName
      case other: Definition                           => other.id.value == soughtName
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

      // We have a name to search for if the parent stack is not empty
      if parentStack.nonEmpty then {
        val definition = parentStack.head // get the next definition of the parentStack

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
    }

    // if there is a single thing left on the stack and that things is
    // a RootContainer
    if parentStack.size == 1 && parentStack.head.isInstanceOf[RootContainer] then {
      // then pop it off because RootContainers don't count and we want to
      // rightfully return an empty sequence for "not found"
      parentStack.pop()
    }
    // Convert parent stack to immutable sequence
    parentStack.toSeq
  }
}
