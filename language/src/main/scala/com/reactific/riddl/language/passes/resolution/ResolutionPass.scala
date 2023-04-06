package com.reactific.riddl.language.passes.resolution

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.{CommonOptions, Messages}
import com.reactific.riddl.language.passes.Pass
import com.reactific.riddl.language.passes.symbols.SymbolsOutput
import com.reactific.riddl.utils.SeqHelpers.SeqHelpers

import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

/** The Reference Resolution Pass */
case class ResolutionPass(input: SymbolsOutput) extends Pass[SymbolsOutput, ResolutionOutput](input) with
  UsageResolution {

  override def name: String = "resolution"

  val commonOptions: CommonOptions = input.commonOptions
  val messages: Messages.Accumulator = Messages.Accumulator(input.commonOptions)
  val refMap: ReferenceMap = ReferenceMap(messages)

  override def result: ResolutionOutput =
    ResolutionOutput(input.root, input.commonOptions, messages.toMessages, input, refMap, Usages(uses, usedBy))

  override def close: Unit = ()

  def postProcess(root: RootContainer): Unit = {
    checkUnused()
  }

  def process(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
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
      case e: Example =>
        resolveExample(e, parentsAsSeq)
      case ic: OnInitClause =>
        ic.examples.foreach(resolveExample(_, parentsAsSeq))
      case tc: OnTermClause =>
        tc.examples.foreach(resolveExample(_, parentsAsSeq))
      case oc: OnOtherClause =>
        oc.examples.foreach(resolveExample(_, parentsAsSeq))
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
        resolveMaybeExpr(i.expression, parentsAsSeq)
      case c: Constant =>
        resolveTypeExpression(c.typeEx, parentsAsSeq)
        resolveExpr(c.value, parentsAsSeq)
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
        uc.userStory.map(userStory => resolveARef[Actor](userStory.actor, parentsAsSeq))
      case in: Input =>
        resolveARef[Type](in.putIn, parentsAsSeq)
      case out: Output =>
        resolveARef[Type](out.putOut, parentsAsSeq)
      case ti: TakeOutputInteraction =>
        resolveARef[Actor](ti.to, parentsAsSeq)
        resolveARef[Output](ti.from, parentsAsSeq)
      case pi: PutInputInteraction =>
        resolveARef[Actor](pi.from, parentsAsSeq)
        resolveARef[Input](pi.to, parentsAsSeq)
      case si: SelfInteraction =>
        resolveARef[Definition](si.from, parentsAsSeq)
      case _: Author => () // no references
      case _: Actor => () // no references
      case _: Enumerator => () // no references
      case _: Group => () // no references
      case _: Include[_] => () // no references
      case _: OptionalInteractions => () // no references
      case _: ParallelInteractions => () // no references
      case _: RootContainer => () // no references
      case _: SagaStep => () // no references
      case _: SequentialInteractions => () // no references
      case _: Term => () // no references
      // case _ => () // NOTE: Never have this catchall
    }
  }

  private def resolveFunction(f: Function, parents: Seq[Definition]): Unit = {
    f.authors.foreach(resolveARef[Author](_, parents))
    addFunction(f)
    f.input.map(resolveTypeExpression(_, parents))
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
    if (mc.from.nonEmpty) {
      resolveARef[Definition](mc.from.get, parents)
    }
    mc.examples.foreach(resolveExample(_, parents))
  }

  private def resolveExample(example: Example, parents: Seq[Definition]): Unit = {
    val pars = example +: parents
    example.whens.foreach { when => resolveExpr(when.condition, pars) }
    example.thens.foreach { then_ => resolveAction(then_.action, pars) }
    example.buts.foreach { but => resolveAction(but.action, pars) }
  }

  private def resolveAction(action: Action, parents: Seq[Definition]): Unit = {
    action match {
      case AssignAction(_, pid: PathIdentifier, value: Expression) =>
        resolveExpr(value, parents)
        resolveAPathId[Field](pid, parents)
      case AppendAction(_, value: Expression, pid: PathIdentifier) =>
        resolveExpr(value, parents)
        resolveAPathId[Field](pid, parents)
      case ReturnAction(_, value: Expression) =>
        resolveExpr(value, parents)
      case SendAction(_, msg: MessageConstructor, ref: PortletRef[Portlet]) =>
        resolveARef[Portlet](ref, parents)
        resolveARef[Type](msg.msg, parents)
        msg.args.args.values.foreach(resolveExpr(_, parents))
      case TellAction(_, msg: MessageConstructor, ref: EntityRef) =>
        resolveARef[Entity](ref, parents)
        resolveARef[Type](msg.msg, parents)
        msg.args.args.values.foreach(resolveExpr(_, parents))
      case FunctionCallAction(_, pid: PathIdentifier, args: ArgList) =>
        resolveAPathId[Function](pid, parents)
        args.args.values.foreach(resolveExpr(_, parents))
      case MorphAction(_, ref: EntityRef, state: StateRef, newValue: Expression) =>
        resolveARef[Entity](ref, parents)
        resolveARef[State](state, parents)
        resolveExpr(newValue, parents)
      case BecomeAction(_, entity: EntityRef, handler: HandlerRef) =>
        resolveARef[Entity](entity, parents)
        resolveARef[Handler](handler, parents)
      case CompoundAction(_, actions: Seq[Action]) =>
        actions.foreach(resolveAction(_, parents))
      case _: ErrorAction => () // no references
      case _: ArbitraryAction => () // no references
    }
  }

  private def resolveMaybeExpr(maybeExpr: Option[Expression], parents: Seq[Definition]): Unit = {
    maybeExpr.map(resolveExpr(_, parents))
  }

  private def resolveExpr(expr: Expression, parents: Seq[Definition]): Unit = {
    expr match {
      case ValueOperator(_, path) => resolveAPathId[Field](path, parents)
      case ValueCondition(_, path) => resolveAPathId[Field](path, parents)
      case AggregateConstructionExpression(_, msg, _) => resolveAPathId[Type](msg, parents)
      case NewEntityIdOperator(_, entityId) => resolveAPathId[Entity](entityId, parents)
      case FunctionCallExpression(_, func, args) =>
        resolveAPathId[Function](func, parents)
        args.args.values.foreach(resolveExpr(_, parents))
      case FunctionCallCondition(_, func, args) =>
        resolveAPathId[Function](func, parents)
        args.args.values.foreach(resolveExpr(_, parents))
      case ArbitraryOperator(_, _, args) =>
        args.args.values.foreach(resolveExpr(_, parents))
      case Comparison(_, _, expr1, expr2) =>
        resolveExpr(expr1, parents); resolveExpr(expr2, parents)
      case ArithmeticOperator(_, _, operands) =>
        operands.foreach(resolveExpr(_, parents))
      case Ternary(_, cond, expr1, expr2) =>
        resolveExpr(cond, parents); resolveExpr(expr1, parents); resolveExpr(expr2, parents)
      case GroupExpression(_, expressions) =>
        expressions.foreach(resolveExpr(_, parents))
      case NotCondition(_, condition) =>
        resolveExpr(condition, parents)
      case mc: MultiCondition =>
        mc.conditions.foreach(resolveExpr(_, parents))
      case vfe: ValueFunctionExpression =>
        vfe.args.foreach(resolveExpr(_, parents))
      case _ =>
      // ArbitraryCondition, ArbitraryExpression, constant values, undefined
      // None of these have case _: com.reactific.riddl.language.ast.Expressions.GroupExpression =>
      // caseArbitraryOperator(loc, resolve nor paths to resolve
    }
  }

  private def resolveMaybeRef[T <: Definition : ClassTag](
    maybeRef: Option[Reference[T]],
    parents: Seq[Definition]
  ): Unit = {
    maybeRef match {
      case Some(ref: Reference[T]) =>
        resolveARef[T](ref, parents)
      case None => ()
    }
  }

  private def resolveARef[T <: Definition : ClassTag](
    ref: Reference[T],
    parents: Seq[Definition]
  ): Unit = {
    resolveAPathId[T](ref.pathId, parents)
  }

  private def isSameKind[DEF <: Definition : ClassTag](d: Definition): Boolean = {
    val clazz = classTag[DEF].runtimeClass
    clazz.isAssignableFrom(d.getClass)
  }

  private def resolveAPathId[T <: Definition : ClassTag](
    pathId: PathIdentifier,
    parents: Seq[Definition]
  ): Seq[Definition] = {
    val parent = parents.head
    val maybeFound: Seq[Definition] = if (pathId.value.isEmpty) {
      notResolved[Definition](pathId, parents.head)
      Seq.empty[Definition]
    } else {
      // Capture the first name we're looking for
      val topName = pathId.value.head

      // Define a function to identify the starting point in the parents
      def startingPoint(defn: Definition): Boolean = {
        defn.id.value == topName || defn.contents.exists(_.id.value == topName)
      }

      // Drop parents until starting point found
      val newParents = parents.dropUntil(startingPoint(_))

      // If we dropped all the parents then the path isn't valid
      if (newParents.isEmpty) {
        // Signal not found
        Seq.empty[Definition]
      } else {
        // we found the starting point, and adjusted the parent stack correspondingly
        // use resolveRelativePath to descend through names
        val pid = PathIdentifier(pathId.loc, pathId.value)
        resolveRelativePath(pid, newParents)
      }
    }

    val isFound: Boolean = if (maybeFound.nonEmpty) {
      val head = maybeFound.head
      val hasRightName = head.id.value == pathId.value.last
      val hasSameType = isSameKind[T](head)
      if (hasRightName) {
        if (hasSameType) {
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

    if (!isFound) {
      val symTabCompatibleNameSearch = pathId.value.reverse
      val list = input.lookupParentage(symTabCompatibleNameSearch)
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

  private def resolved[T <: Definition : ClassTag](
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

  private def wrongType[T <: Definition : ClassTag](
    pid: PathIdentifier,
    container: Definition,
    foundDef: Definition
  ): Unit = {
    val referTo = classTag[T].runtimeClass.getSimpleName
    val message = s"Path '${pid.format}' resolved to ${foundDef.identifyWithLoc}," +
      s" in ${container.identify}, but ${article(referTo)} was expected"
    messages.addError(pid.loc, message)
  }

  private def notResolved[T <: Definition : ClassTag](
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
        if (referTo.nonEmpty) s", but should refer to ${article(referTo)}"
        else ""
      }
    )
  }

  private def ambiguous[T <: Definition : ClassTag](
    pid: PathIdentifier,
    list: List[(Definition, Seq[Definition])]
  ): Seq[Definition] = {
    // Extract all the definitions that were found
    val definitions = list.map(_._1)
    val allDifferent = definitions.map(_.kind).distinct.sizeIs ==
      definitions.size
    val expectedClass = classTag[T].runtimeClass
    if (allDifferent || definitions.head.isImplicit) {
      // pick the one that is the right type or the first one
      list.find(_._1.getClass == expectedClass) match {
        case Some((defn, parents)) => defn +: parents
        case None => list.head._1 +: list.head._2
      }
    } else {
      val ambiguity =
        list.map { case (definition, parents) =>
          "  " + parents.reverse.map(_.id.value).mkString(".") + "." +
            definition.id.value + " (" + definition.loc + ")"
        }.mkString("\n")

      messages.addError(
        pid.loc,
        s"Path reference '${pid.format}' is ambiguous. Definitions are:\n$ambiguity"
      )
      Seq.empty[Definition]
    }
  }


  private val vowels: String = "aAeEiIoOuU"

  private def article(thing: String): String = {
    val article = if (vowels.contains(thing.head)) "an" else "a"
    s"$article $thing"
  }


  private def adjustStacksForPid[T <: Definition : ClassTag](
    pid: PathIdentifier,
    parentStack: mutable.Stack[Definition]
  ): Seq[Definition] = {

    // Recursively resolve this PathIdentifier
    val path: Seq[Definition] = resolveAPathId[T](pid, parentStack.toSeq)

    // if we found the definition
    if (path.nonEmpty) {
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
    if (parentStack.isEmpty) {
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
          // if we're at an onClause that references a message then we
          // need to push that message's path on the name stack
          adjustStacksForPid[Type](oc.msg.pathId, parentStack)
        case f: Field =>
          candidatesFromTypeEx(f.typeEx, parentStack)
        case t: Type =>
          candidatesFromTypeEx(t.typ, parentStack)
        case f: Function =>
          // If we're at a Function node, the functions input parameters
          // are the candidates to search next
          f.input.get.fields
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

  // final val maxTraversal = 10

  /** Resolve a Relative PathIdentifier. If the path is already resolved or it
   * has no empty components then we can resolve it from the map or the
   * symbol table.
   *
   * @param pid
   * The path to consider
   * @param parents
   * The parent stack to provide the context from which the search starts
   * @return
   * Either an error or a definition
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
    while (nameStack.nonEmpty) {
      // Pop the name we're currently looking for and save it
      val soughtName = nameStack.pop()

      // We have a name to search for if the parent stack is not empty
      if (parentStack.nonEmpty) {
        val definition = parentStack.head // get the next definition of the parentStack

        // If we have already visited this definition, its a looping error
        if (visitedStack.contains(definition)) {
          // Generate the error message
          messages.addError(
            pid.loc,
            msg =
              s"""Path resolution encountered a loop at ${definition.identify}
                 |  for name '$soughtName' when resolving ${pid.format}
                 |  in definition context: ${
                parents.map(_.identify).mkString("\n    ", "\n    ", "\n")
              }
                 |""".stripMargin
          )
          // Signal we're done searching with no result
          parentStack.clear()
        } else {

          // Look where we are and find the candidates that could
          // possibly match soughtName
          val candidates = findCandidates(parentStack)

          // Now find the match, if any, and handle appropriately
          val found = candidates.find(_.id.value == soughtName)
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
    if (
      parentStack.size == 1 && parentStack.head.isInstanceOf[RootContainer]
    ) {
      // then pop it off because RootContainers don't count and we want to
      // rightfully return an empty sequence for "not found"
      parentStack.pop()
    }
    // Convert parent stack to immutable sequence
    parentStack.toSeq
  }
}
