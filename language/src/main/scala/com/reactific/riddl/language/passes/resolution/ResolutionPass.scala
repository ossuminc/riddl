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

  val commonOptions: CommonOptions = input.commonOptions
  val messages: Messages.Accumulator = Messages.Accumulator(input.commonOptions)
  val refMap: ReferenceMap = ReferenceMap(messages)

  override def result: ResolutionOutput =
    ResolutionOutput(input.root, input.commonOptions, messages, input, refMap.copy(), usesAsMap, usedByAsMap)

  override def close: Unit = ()

  def processLeafDefinition(leafDef: LeafDefinition, parents: Seq[Definition]): Unit = {
    leafDef match {
      case f: Field =>
        f.typeEx match {
          case AliasedTypeExpression(_, pathId) =>
            resolveAPathId[Type](pathId, parents)
          case _ =>
        }
      case e: Example => resolveExample(e, parents)
      case i: Invariant => resolveMaybeExpr(i.expression, parents)
      case i: Inlet => resolveARef[Type](i.type_, parents)
      case o: Outlet => resolveARef[Type](o.type_, parents)
      case c: Connector =>
        resolveMaybeRef[Type](c.flows, parents)
        resolveMaybeRef[Outlet](c.from, parents)
        resolveMaybeRef[Inlet](c.to, parents)
      case _ => ()
    }
  }

  def processHandlerDefinition(hd: HandlerDefinition, parents: Seq[Definition]): Unit = {
    hd match {
      case ic: OnInitClause =>
        ic.examples.foreach(resolveExample(_,ic+:parents))
      case tc: OnTermClause =>
        tc.examples.foreach(resolveExample(_,tc+:parents))
      case oc: OnOtherClause =>
        oc.examples.foreach(resolveExample(_,oc+:parents))
      case mc: OnMessageClause =>
        resolveARef[Type](mc.msg, mc+:parents)
        if (mc.from.nonEmpty) {
          resolveARef[Definition](mc.from.get, parents)
        }
        mc.examples.foreach(resolveExample(_,mc+:parents))
    }
  }

  def processApplicationDefinition(appDef: ApplicationDefinition, parents: Seq[Definition]): Unit = {
    appDef match {
      case in: Input => resolveARef[Type](in.putIn, parents)
      case out: Output => resolveARef[Type](out.putOut, parents)
      case h: Handler => h.authors.foreach(resolveARef[Author](_, h+:parents))
      case in: Inlet => resolveARef[Type](in.type_, parents)
      case out: Outlet => resolveARef[Type](out.type_, parents)
      case _ => ()
    }
  }

  def processEntityDefinition(entDef: EntityDefinition, parents: Seq[Definition]): Unit = {
    entDef match {
      case t: Type => addType( t )
      case s: State => resolveARef[Type](s.typ, s +: parents)
      case h: Handler => h.authors.foreach(resolveARef[Author](_, h+:parents))
      case f: Function => f.authors.foreach(resolveARef[Author](_,f+:parents))
      case _ => ()
    }
  }

  def processRepositoryDefinition(repoDef: RepositoryDefinition, parents: Seq[Definition]): Unit = {
    repoDef match {
      case t: Type => addType( t )
      case h: Handler => h.authors.foreach(resolveARef[Author](_, h+:parents))
      case _ => ()
    }
  }

  def processProjectorDefinition(pd: ProjectorDefinition, parents: Seq[Definition]): Unit = {
    pd match {
      case t: Type => addType(t)
      case h: Handler => h.authors.foreach(resolveARef[Author](_, h+:parents))
      case _ => ()
    }
  }

  def processSagaDefinition(sagaDef: SagaDefinition, parents: Seq[Definition]): Unit = {
    sagaDef match {
      case f: Function => f.authors.foreach(resolveARef[Author](_,f+:parents))
      case _ => ()
    }
  }

 def processContextDefinition(contextDef: ContextDefinition, parents: Seq[Definition]): Unit = {
    contextDef match {
      case t: Type => addType( t )
      case h: Handler => h.authors.foreach(resolveARef[Author](_,h+:parents))
      case f: Function =>
        f.authors.foreach(resolveARef[Author](_,f+:parents))
        addFunction(f)
      case e: Entity =>
        e.authors.foreach(resolveARef[Author](_,e+:parents))
        addEntity(e)
      case a: Adaptor =>
        resolveARef[Context](a.context, parents)
        a.authors.foreach(resolveARef[Author](_,a+:parents))
      case s: Streamlet => s.authors.foreach(resolveARef[Author](_,s+:parents))
      case p: Projector => p.authors.foreach(resolveARef[Author](_,p+:parents))
      case r: Repository => r.authors.foreach(resolveARef[Author](_,r+:parents))
      case s: Saga => s.authors.foreach(resolveARef[Author](_,s+:parents))
      case _ => ()
    }
  }

  def processDomainDefinition(domDef: DomainDefinition, parents: Seq[Definition]): Unit = {
    domDef match {
      case a: Application => a.authors.foreach(resolveARef[Author](_,a+:parents))
      case c: Context => c.authors.foreach(resolveARef[Author](_,c+:parents))
      case d: Domain => d.authors.foreach(resolveARef[Author](_,d+:parents))
      case e: Epic => e.authors.foreach(resolveARef[Author](_,e+:parents))
      case _ => ()
    }
  }

  def processAdaptorDefinition(adaptDef: AdaptorDefinition, parents: Seq[Definition]): Unit = {
    adaptDef match {
      case h: Handler => h.authors.foreach(resolveARef[Author](_,h+:parents))
      case _ => ()
    }
  }

  private def resolveExample(example: Example, parents: Seq[Definition]): Unit = {
    val pars = if (example.isImplicit) parents else example +: parents
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
    d.getClass == clazz
  }

  private def resolveAPathId[T <: Definition : ClassTag](
    pathId: PathIdentifier,
    parents: Seq[Definition]
  ): Seq[Definition] = {
    val parent = parents.head
    val maybeFound = pathId.value.size match {
      case 0 =>
        notResolved[Definition](pathId, parents.head)
        Seq.empty[Definition]
      case 1 =>
        val sought = pathId.value.head
        parent.contents.find(_.id.value == sought).toSeq
      case _ =>
        // First, scan up through the parent stack to find the starting place
        val topName = pathId.value.head
        val newParents = parents.dropUntil(_.id.value == topName)

        // we found the starting point, adjust the PathIdentifier to drop the
        // one we found, and use resolveRelativePath to descend through names
        val newPid = PathIdentifier(pathId.loc, pathId.value.drop(1))
        resolveRelativePath(newPid, newParents)
    }
    if (maybeFound.nonEmpty) {
      val head = maybeFound.head
      if (isSameKind[T](head)) {
        // a candidate was found and it has the same type as expected
        resolved[T](pathId, parent, head)
        maybeFound
      } else {
        wrongType[T](pathId, parent, head)
        Seq.empty[Definition]
      }
    } else {
      val symTabCompatibleNameSearch = pathId.value.filterNot(_.isEmpty)
      val list = input.lookupParentage(symTabCompatibleNameSearch)
      list match {
        case (d, _) :: Nil if isSameKind[T](d) => // exact match
          // Found
          resolved[T](pathId, parent, d)
          maybeFound
        case (d, _) :: Nil =>
          wrongType[T](pathId, parent, d)
          Seq.empty[Definition]
        case _ =>
          notResolved[T](pathId, parent)
          Seq.empty[Definition]
      }
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
            case Include(_, contents, _) => contents
            case d: Definition => Seq(d)
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
