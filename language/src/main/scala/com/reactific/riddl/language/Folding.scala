/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

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
    parents: mutable.Stack[ParentDefOf[Definition]] =
      mutable.Stack.empty[ParentDefOf[Definition]]
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
    folder.closeContainer(middleState, top, parents.toSeq)
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
    def step(f: S => S): S = f(this.asInstanceOf[S])
    def stepIf(predicate:Boolean = true)(f: S => S): S = {
      if (predicate) f(this.asInstanceOf[S]) else this.asInstanceOf[S]
    }
  }

  trait MessagesState[S <: State[?]] extends State[S] {

    def commonOptions: CommonOptions

    private val msgs: ListBuffer[Message] = ListBuffer.empty[Message]

    def messages: Messages.Messages = msgs.toList

    def isReportMissingWarnings: Boolean = commonOptions.showMissingWarnings

    def isReportStyleWarnings: Boolean = commonOptions.showStyleWarnings

    def addStyle(loc: Location, msg: String): S = {
      add(Message(loc, msg, StyleWarning))
    }

    def addMissing(loc: Location, msg: String): S = {
      add(Message(loc, msg, MissingWarning))
    }

    def addWarning(loc: Location, msg: String): S = {
      add(Message(loc, msg, Warning))
    }

    def addError(loc: Location, msg: String): S = {
      add(Message(loc, msg, Error))
    }

    def addSevere(loc: Location, msg: String): S = {
      add(Message(loc, msg, SevereError))
    }

    def add(msg: Message): S = {
      msg.kind match {
        case StyleWarning =>
          if (isReportStyleWarnings) {
            msgs += msg
            this.asInstanceOf[S]
          } else {this.asInstanceOf[S]}
        case MissingWarning =>
          if (isReportMissingWarnings) {
            msgs += msg
            this.asInstanceOf[S]
          } else {this.asInstanceOf[S]}
        case _ =>
          msgs += msg
          this.asInstanceOf[S]
      }
    }
  }

  trait PathResolutionState[S <: State[?]] extends State[S] {
    def root: ParentDefOf[Definition]
    var parents: Seq[ParentDefOf[Definition]] = Seq.empty[ParentDefOf[Definition]]
    var definition: Definition = root

    def captureHierarchy(cont: Definition, pars: Seq[ParentDefOf[Definition]]): Unit = {
      definition = cont
      parents = pars
    }

    def resolvePath(
      pid: PathIdentifier,
      parents: Seq[ParentDefOf[Definition]] = parents
    ): Option[Definition] = {
      resolvePath(pid.value, parents)
    }

      /** Resolve a PathIdentifier If the path is already resolved or it has no
     * empty components then we can resolve it from the map or the symbol
     * table.
     *
     * @param pid
     * The path to consider
     * @return
     * Either an error or a definition
     */
    def resolvePath(
      names: Seq[String],
      parents: Seq[ParentDefOf[Definition]]
    ): Option[Definition] = {
      val pstack = mutable.Stack.empty[ParentDefOf[Definition]]
      pstack.pushAll(parents.reverse)
      val nstack = mutable.Stack.empty[String]
      nstack.pushAll(names.reverse)
      var result = pstack.headOption.asInstanceOf[Option[Definition]]
      while (result.nonEmpty && nstack.nonEmpty) {
        val n = nstack.pop()
        // if we're supposed to pop parent off the stack ...
        if (n.isEmpty) {
          // if there is a parent to pop off the stack
          if (pstack.nonEmpty) {
            // pop it and the new result is the new head
            pstack.pop()
            result = pstack.headOption
          } else {
            result = None // no parent stack to pop, exit loop
          }
        } else {
          // First get the list of candidate matches from the current node
          val candidates: Seq[Definition] = result match {
            case None =>
              // empty result means nothing to search
              Seq.empty[Definition]
            case Some(f: Function) if f.input.nonEmpty =>
              // If we're at a Function node, the functions input parameters
              // are the candidates to search next
              f.input.get.fields
            case Some(s: AST.State) =>
              // If we're at a entity's state, the state's fields are next
              s.typeEx.fields
            case Some(Field(_, _, te: Aggregation, _, _)) =>
              // if we're at a field composed of more fields, those fields are it
              te.fields
            case Some(Field(_, _, TypeRef(_,pid), _, _)) =>
              // if we're at a field that references another type then we
              // need to push that types path on the name stack
              nstack.pushAll(pid.value.reverse)
              Seq.empty[Definition]
            case Some(p) if p.isContainer =>
              p.asInstanceOf[Container[Definition]].contents
            case _ => Seq.empty[Definition] // anything else isn't searchable
          }

          // Now find the match, if any, and handle appropriately
          val found = candidates.find(_.id.value == n)
          found match {
            case Some(q : Definition) if q.isContainer =>
              // found the named item, and it is a Container, so put it on
              // the stack in case there are more things to resolve
              pstack.push(q.asInstanceOf[ParentDefOf[Definition]])
              // make it the next value of "r" in our foldLeft
              Some(q)
            case r @ Some(_) =>
              // Found an item but its not a container. So, presumably this is
              // the result they want. If there are more names, they will
              // fall through above and produce a None result.
              result = r
            case None =>
              // No search result, keep looping
          }
        }
      }
      result
    }
  }
}
