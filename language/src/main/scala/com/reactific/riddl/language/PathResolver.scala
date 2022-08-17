package com.reactific.riddl.language

import com.reactific.riddl.language.AST.{InletRef, *}
import com.reactific.riddl.language.Folding.Folder
import com.reactific.riddl.language.Messages.*
import org.apache.commons.lang3.exception.ExceptionUtils

import scala.annotation.unused
import scala.collection.mutable
import scala.util.control.NonFatal

/** Resolve PathIdentifiers To Definitions
 *
 * This object folds the contents of a container into a mapping from
 * PathIdentifier to Definition. If a
 */
object PathResolver {

  type ResolvedPaths = Map[Seq[String],Definition]

  case class PathResolverState(
    root: ParentDefOf[Definition],
    commonOptions: CommonOptions = CommonOptions()
  ) extends Folding.MessagesState[PathResolverState] {
    private val symTab = SymbolTable(root)
    private val map = mutable.HashMap.empty[Seq[String],Definition]

    override def step(
      f: PathResolverState => PathResolverState
    ): PathResolverState = f(this)

    def get(path: Seq[String]): Option[Definition] = {
      map.get(path)
    }

    def put(path: Seq[String], defn: Definition): PathResolverState = {
      map.put(path, defn)
      this
    }

    def toMap: Map[Seq[String], Definition] = map.toMap

    def lookup(path: Seq[String]): Option[Definition] = {
      symTab.lookup[Definition](path) match {
        case Nil =>
          None
        case defn :: x if x == Nil =>
          // We found it in the symtab so just use it
          map.put(path, defn)
          Some(defn)
        case _ :: _ =>
          // multiple definitions == failure to find!
          None
      }
    }
  }

  /**
   * This class is provided to the Folder.foldAbout method and is invoked
   * by it to traverse the AST. The implementation here looks for PathIdentifiers
   * and builds the State's map element
   */
  class PathResolverFolder extends Folder[PathResolverState] {

    /** Look for obvious resolutions
     * If the path is already resolved or it has no empty components then
     * we can resolve it from the map or the symbol table.
     *
     * @param state The folder state
     * @param path The path to consider
     * @return The definition found or None
     */
    def fastResolve(state: PathResolverState, path: Seq[String]): Option[Definition] = {
      // is it a full path that is resolvable by symbol table?
      if (path.forall(_.nonEmpty)) {
        state.lookup(path)
      } else {
        None
      }
    }

    def slowResolve(
      pid: PathIdentifier,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): Either[Message,Definition] = {
      val stack = mutable.Stack.empty[ParentDefOf[Definition]]
      stack.pushAll(parents.reverse)
      definition match {
        case pdo: ParentDefOf[Definition] =>
          stack.push(pdo)
        case _ =>
      }
      val results: Option[Definition] =
        pid.value.foldLeft(Option.empty[Definition]) { (r,n) =>
        if (n.isEmpty) {
          val valueFound = stack.pop()
          Some(valueFound)
        } else if (r.isEmpty) {
          None
        } else {
          val valueFound = r.get.contents.find(_.id.value == n)
          valueFound
        }
      }
      results match {
        case Some(defn) =>
          Right(defn)
        case None =>
          Left(Message(pid.loc,
            s"Path '${
              pid.value.mkString(".")
            } not resolved in definition:]\n  " +
              definition.id.value + "\n" + parents.mkString("  ", "\n  ", "\n")
            , Error))
      }
    }

    def resolvePath(
      state: PathResolverState,
      pid: PathIdentifier,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): PathResolverState = {
      val names = pid.value
      fastResolve(state, names) match {
        case None =>
          slowResolve(pid, definition, parents) match {
            case Right(defn) =>
              state.put(names, defn)
            case Left(error) =>
              state.add(error)
          }
        case Some(defn) =>
          state.put(names, defn)
      }
    }

    override def openContainer(
      state: PathResolverState,
      container: ParentDefOf[Definition],
      parents: Seq[ParentDefOf[Definition]]
    ): PathResolverState = {
      container match {
        case typ: Type =>
          typ.typ match {
            case TypeRef(_,pid) =>
              resolvePath(state,pid, container, parents)
            case _ => // others don't have a TypeRef so ignore
              state
          }
        case _: Function =>  state
        case _: Entity =>  state
        case _: OnClause => state
        case _: Processor => state
        case _: Story =>  state
        case _: SagaStep => state
        case _: Context => state
        case _: Include => state
        case _: AdaptorDefinition => state
        case _: RootContainer =>
          // we don't inspect RootContainers
          state
        case _: Domain => state
        case _: ParentDefOf[Definition] => state
      }
    }

    def doArgList(
      state: PathResolverState,
      arguments: ArgList,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ) : PathResolverState = {
      arguments.args.values.foldLeft(state) { (st, arg) =>
        doExpression(st, arg, definition, parents)
      }
    }

    def doExpressions(
      state: PathResolverState,
      expressions: Seq[Expression],
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): PathResolverState = {
      expressions.foldLeft(state) { (st, cond) =>
        doExpression(st, cond, definition, parents)
      }
    }

    def doExpression(
      state: PathResolverState,
      expression: Expression,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ) : PathResolverState = {
      expression match {
        case ArithmeticOperator(_, _, operands) =>
          doExpressions(state, operands, definition, parents)
        case ValueExpression(_, path) =>
          resolvePath(state, path, definition, parents)
        case AggregateConstructionExpression(_, msg, args) =>
          resolvePath(state, msg.id, definition, parents)
          doArgList(state, args, definition, parents)
        case EntityIdExpression(_, EntityRef(_, pid)) =>
          resolvePath(state, pid, definition, parents)
        case FunctionCallExpression(_, name, arguments) =>
          resolvePath(state, name, definition, parents)
          doArgList(state, arguments, definition, parents)
        case FunctionCallAction(_, function, arguments, _) =>
          resolvePath(state, function, definition, parents)
          doArgList(state, arguments, definition, parents)
        case GroupExpression(_, expression) =>
          doExpression(state, expression, definition, parents)
        case Ternary(_, condition, expr1, expr2) =>
          doExpression(state, condition, definition, parents)
          doExpression(state, expr1, definition, parents)
          doExpression(state, expr2, definition, parents)
        case Comparison(_, _, expr1, expr2) =>
          doExpression(state, expr1, definition, parents)
          doExpression(state, expr2, definition, parents)
        case NotCondition(_, cond1) =>
          doExpression(state, cond1, definition, parents)
        case condition: MultiCondition =>
          doExpressions(state, condition.conditions, definition, parents)
        case _ =>
          state // not of interest
      }
    }
    def doActions(
      state: PathResolverState,
      actions: Seq[Action],
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): PathResolverState = {
      actions.foldLeft(state) { (st, action) =>
        action match {
          case SetAction(_, pid, value, _) =>
            resolvePath(st, pid, definition, parents)
            doExpression(st, value, definition, parents)
          case YieldAction(_, MessageConstructor(msg,args), _) =>
            resolvePath(st, msg.id, definition, parents)
            doArgList(st, args, definition, parents)
          case MorphAction(_, EntityRef(_,pid), StateRef(_,pid2), _) =>
            resolvePath(st, pid, definition, parents)
            resolvePath(st, pid2, definition, parents)
          case BecomeAction(_, EntityRef(_,pid), HandlerRef(_,pid2), _) =>
            resolvePath(st, pid, definition, parents)
            resolvePath(st, pid2, definition, parents)
          case CompoundAction(_, actions, _) =>
            doActions(st, actions, definition, parents)
          case action: SagaStepAction =>
            action match {
              case PublishAction(_, _, PipeRef(_,pid), _) =>
                resolvePath(st, pid, definition, parents)
              case FunctionCallAction(_, function, arguments, _) =>
                resolvePath(st, function, definition, parents)
                doArgList(st, arguments, definition, parents)
              case TellAction(_, MessageConstructor(msg,args), EntityRef(_, pid), _) =>
                resolvePath(st, msg.id, definition, parents)
                doArgList(st, args, definition, parents)
                resolvePath(st, pid, definition, parents)
              case AskAction(_, EntityRef(_,pid), MessageConstructor(msg,args), _) =>
                resolvePath(st, pid, definition, parents)
                resolvePath(st, msg.id, definition, parents)
                doArgList(st, args, definition, parents)
              case ReplyAction(_, MessageConstructor(msg,args), _) =>
                resolvePath(st, msg.id, definition, parents)
                doArgList(st, args, definition, parents)
              case _ => st // ignore others
            }
          case _ => st // ignore others
        }
      }
    }

    def doExample(
      state: PathResolverState,
      example: Example,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): PathResolverState = {
      val Example(_, _, _, whens, thens, buts, _, _) = example
      doExpressions(state, whens.map(_.condition), definition, parents)
      doActions(state, thens.map(_.action), definition, parents)
      doActions(state, buts.map(_.action), definition, parents)
    }

    def doExamples(
      state: PathResolverState,
      examples: Seq[Example],
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): PathResolverState = {
      examples.foldLeft(state) { (st, example) =>
        doExample(st, example, definition, parents)
      }
    }

    def doType(
      state: PathResolverState,
      tye: TypeExpression,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): PathResolverState = {
      tye match {
        case Alternation(_, of) =>
          doTypes(state, of, definition, parents)
        case Mapping(_, from, to) =>
          doType(state, from, definition, parents)
          doType(state, to, definition, parents)
        case ReferenceType(_, EntityRef(_,pid)) =>
          resolvePath(state, pid, definition, parents)
        case UniqueId(_, entityPath) =>
          resolvePath(state, entityPath, definition, parents)
        case cardinality: Cardinality =>
          cardinality match {
            case Optional(_, typeExp) =>
              doType(state, typeExp, definition, parents)
            case ZeroOrMore(_, typeExp) =>
              doType(state, typeExp, definition, parents)
            case OneOrMore(_, typeExp) =>
              doType(state, typeExp, definition, parents)
          }
        case Aggregation(_, fields) =>
          doTypes(state, fields.map(_.typeEx), definition, parents)
        case MessageType(_, _, fields) =>
          doTypes(state, fields.map(_.typeEx), definition, parents)
        case _ =>
          state // others not interesting
      }
    }

    def doTypes(
      state: PathResolverState,
      types: Seq[TypeExpression],
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): PathResolverState = {
      types.foldLeft(state) { (st, tye) =>
        doType(st, tye, definition, parents)
      }
    }

    override def doDefinition(
      state: PathResolverState,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): PathResolverState = {
      definition match {
        case CommandCommandA8n(_, _, CommandRef(_, pid), CommandRef(_, pid2), _, _, _) =>
          resolvePath(state, pid, definition, parents)
          resolvePath(state, pid2, definition, parents)
        case EventCommandA8n(_, _, EventRef(_, pid), CommandRef(_, pid2), _, _, _) =>
          resolvePath(state, pid, definition, parents)
          resolvePath(state, pid2, definition, parents)
        case EventActionA8n(_, _, EventRef(_, pid), actions, examples, _, _) =>
          resolvePath(state, pid, definition, parents)
          doActions(state, actions, definition, parents)
          doExamples(state, examples, definition, parents)
        case Field(_, _, typeEx, _, _) =>
          doType(state, typeEx, definition, parents)
        case Handler(_, _, applicability, _, _, _) =>
          applicability.map(
            resolvePath(state, _, definition, parents)
          ).getOrElse(state)
        case Inlet(_, _, TypeRef(_,pid), entity,_,_) =>
          val st1 = resolvePath(state, pid, definition, parents)
          entity.fold(st1){ er => resolvePath( st1,er.id,definition, parents) }
        case InletJoint(_, _, InletRef(_, pid),PipeRef(_, pid2),_,_) =>
          val st1 = resolvePath(state, pid, definition, parents)
          resolvePath(st1, pid2, definition, parents)
        case Outlet(_, _, TypeRef(_,pid), entity,_,_) =>
          val st = resolvePath(state, pid, definition, parents)
          entity.fold(st) { er => resolvePath(st, er.id, definition, parents) }
        case OutletJoint(_, _, OutletRef(_, pid), PipeRef(_, pid2), _, _) =>
          val st1 = resolvePath(state, pid, definition, parents)
          resolvePath(st1, pid2, definition, parents)
        case Story(_, _, _, _, _, _, implementedBy, _, _, _) =>
          implementedBy.foldLeft(state) { (st, pid) =>
            resolvePath(st, pid, definition, parents)
          }
        case Invariant(_, _, expression, _, _) =>
          doExpression(state, expression, definition, parents)
        case Pipe(_, _, transmitType, _, _) =>
          transmitType.fold(state) { typeRef =>
            resolvePath(state, typeRef.id, definition, parents)
          }
        case OnClause(_, msg, _, _, _) =>
          resolvePath(state, msg.id, definition, parents)
        case _ =>
          state // others not interesting
      }
    }

    override def closeContainer(
      state: PathResolverState,
      container: ParentDefOf[Definition],
      parents: Seq[ParentDefOf[Definition]]
    ): PathResolverState = {
      state
    }
  }

  def resolvePathIdentifiers(
    @unused container: ParentDefOf[Definition],
    commonOptions: CommonOptions = CommonOptions()
  ): Either[List[Message],Map[Seq[String],Definition]] = {

    val state = PathResolverState(container, commonOptions)
    try {
      val folder = new PathResolverFolder
      Folding.foldAround(state, container, folder)
    } catch {
      case NonFatal(xcptn) =>
        val message =
          ExceptionUtils.getRootCauseStackTrace(xcptn).mkString("\n")
        state.addSevere(Location.empty, message)
    }
    Right(state.toMap)
  }

}
