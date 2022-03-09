package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*

import scala.collection.mutable

object Folding {

  type SimpleDispatch[S] = (Container[Definition], Definition, S) => S

  def foldEachDefinition[S](
    parent: Container[Definition],
    child: Definition,
    state: S
  )(f: SimpleDispatch[S]
  ): S = {
    child match {
      case subcontainer: ParentDefOf[Definition] =>
        val result = f(parent, child, state)
        subcontainer.contents.foldLeft(result) { case (next, child) =>
          foldEachDefinition[S](subcontainer, child, next)(f)
        }
      case ch: Definition => f(parent, ch, state)
    }
  }

  final def foldLeftWithStack[S](
    value: S,
    parents: mutable.Stack[ParentDefOf[Definition]] = mutable.Stack
      .empty[ParentDefOf[Definition]]
  )(top: ParentDefOf[Definition]
  )(f: (S, Definition, Seq[ParentDefOf[Definition]]) => S
  ): S = {
    val initial = f(value, top, parents.toSeq)
    parents.push(top)
    try {
      top.contents.foldLeft(initial) { (next, definition) =>
        definition match {
          case i: Include => i.contents.foldLeft(next) {
              case (n, cd: ParentDefOf[Definition]) =>
                foldLeftWithStack(n, parents)(cd)(f)
              case (n, d: Definition) => f(n, d, parents.toSeq)
            }
          case c: ParentDefOf[Definition] =>
            foldLeftWithStack(next, parents)(c)(f)
          case d: Definition => f(next, d, parents.toSeq)
        }
      }
    } finally { parents.pop() }
  }

  /*  final def foldLeft[S](
    value: S,
    parents: mutable.Stack[ParentDefOf[Definition]] =
    mutable.Stack.empty[ParentDefOf[Definition]]
  )(top: Seq[ParentDefOf[Definition]])(
    f: (S, Definition, mutable.Stack[ParentDefOf[Definition]]) => S
  ): S = {
    top.foldLeft(value) {
      case (next, definition: ParentDefOf[Definition]) =>
        foldLeftWithStack(next, parents)(definition)(f)
    }
  }*/

  final def foldAround[S](
    value: S,
    top: ParentDefOf[Definition],
    folder: Folder[S],
    parents: mutable.Stack[ParentDefOf[Definition]] = mutable.Stack
      .empty[ParentDefOf[Definition]]
  ): S = {
    // Let them know a container is being opened
    val startState = folder.openContainer(value, top, parents.toSeq)
    parents.push(top)
    val middleState = top.contents.foldLeft(startState) {
      case (next, container: ParentDefOf[Definition]) =>
        // Container node so recurse
        foldAround(next, container, folder, parents)
      case (next, definition: Definition) =>
        // Leaf node so mention it
        folder.doDefinition(next, definition, parents.toSeq)
    }
    // Let them know a container is being closed
    parents.pop()
    val endState = folder.closeContainer(middleState, top, parents.toSeq)
    endState
  }

  trait Folder[STATE] {
    def openContainer(
      state: STATE,
      container: ParentDefOf[Definition],
      parents: Seq[ParentDefOf[Definition]]
    ): STATE

    def doDefinition(
      state: STATE,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): STATE

    def closeContainer(
      state: STATE,
      container: ParentDefOf[Definition],
      parents: Seq[ParentDefOf[Definition]]
    ): STATE
  }

  trait State[S <: State[?]] {
    def step(f: S => S): S
  }

}
