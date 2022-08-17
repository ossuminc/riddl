package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
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

    def put(path: Seq[String], defn: Definition): Unit = {
      map.put(path, defn)
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
      // is it already resolved?
      state.get(path) match {
        case None =>
          if (path.forall(_.nonEmpty)) {
            state.lookup(path)
          } else {
            None
          }
        case od: Option[Definition] => od
      }
    }

    def slowResolve(
      loc: Location,
      path: Seq[String],
      container: ParentDefOf[Definition],
      parents: Seq[ParentDefOf[Definition]]
    ): Either[Message,Definition] = {
      val stack = mutable.Stack.empty[ParentDefOf[Definition]]
      stack.pushAll(parents.reverse).push(container)
      val results: Seq[Option[Definition]] = for {
        item <- path
      } yield {
        if (item.isEmpty) {
          Some(stack.pop())
        } else {
          stack.head.contents.find(_.id.value == item)
        }
      }
      results.last match {
        case Some(defn) =>
          Right(defn)
        case None =>
          Left(Message(loc,
            s"Path '${path.mkString(".")} not resolved in definition:]\n  " +
              container.id.value + "\n" + parents.mkString("  ", "\n  ", "\n")
            , Error))
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
            case TypeRef(_,PathIdentifier(loc,names)) =>
              if (fastResolve(state, names).isEmpty) {
                slowResolve(loc, names, container, parents) match {
                  case Right(defn) =>
                    state.put(names.reverse, defn)
                  case Left(error) =>
                    state.add(error)
                }
              }
              state
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

    override def doDefinition(
      state: PathResolverState,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): PathResolverState = {
      state
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
