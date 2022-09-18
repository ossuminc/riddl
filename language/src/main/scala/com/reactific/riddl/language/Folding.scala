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
import com.reactific.riddl.language.ast.Location

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
// import scala.reflect.ClassTag

object Folding {

  type SimpleDispatch[S] = (Container[Definition], Definition, S) => S

  def foldEachDefinition[S](
    parent: Definition,
    child: Definition,
    state: S
  )(f: SimpleDispatch[S]
  ): S = {
    child match {
      case defn: LeafDefinition => f(parent, defn, state)
      case defn: Definition =>
        val result = f(parent, child, state)
        defn.contents.foldLeft(result) { case (next, child) =>
          foldEachDefinition[S](defn, child, next)(f)
        }
    }
  }

  final def foldLeftWithStack[S](
    value: S,
    parents: mutable.Stack[Definition] = mutable.Stack.empty[Definition]
  )(top: Definition
  )(f: (S, Definition, Seq[Definition]) => S
  ): S = {
    val initial = f(value, top, parents.toSeq)
    parents.push(top)
    try {
      top.contents.foldLeft(initial) { (next, definition) =>
        definition match {
          case i: Include => i.contents.foldLeft(next) {
              case (n, d: LeafDefinition) => f(n, d, parents.toSeq)
              case (n, cd: Definition) => foldLeftWithStack(n, parents)(cd)(f)
            }
          case d: LeafDefinition => f(next, d, parents.toSeq)
          case c: Definition     => foldLeftWithStack(next, parents)(c)(f)
        }
      }
    } finally { parents.pop() }
  }

  final def foldAround[S](
    value: S,
    top: Definition,
    folder: Folder[S],
    parents: mutable.Stack[Definition] = mutable.Stack.empty[Definition]
  ): S = {
    // Let them know a container is being opened
    val startState = folder.openContainer(value, top, parents.toSeq)
    parents.push(top)
    val middleState = top.contents.foldLeft(startState) {
      case (next, definition: LeafDefinition) =>
        // Leaf node so mention it
        parents.push(definition)
        val st = folder.doDefinition(next, definition, parents.toSeq)
        parents.pop()
        st
      case (next, container: Definition) =>
        // Container node so recurse
        foldAround(next, container, folder, parents)
    }
    // Let them know a container is being closed
    parents.pop()
    folder.closeContainer(middleState, top, parents.toSeq)
  }

  trait Folder[STATE] {
    def openContainer(
      state: STATE,
      container: Definition,
      parents: Seq[Definition]
    ): STATE

    def doDefinition(
      state: STATE,
      definition: Definition,
      parents: Seq[Definition]
    ): STATE

    def closeContainer(
      state: STATE,
      container: Definition,
      parents: Seq[Definition]
    ): STATE
  }

  trait State[S <: State[?]] {
    def step(f: S => S): S = f(this.asInstanceOf[S])
    def stepIf(predicate: Boolean = true)(f: S => S): S = {
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
          } else { this.asInstanceOf[S] }
        case MissingWarning =>
          if (isReportMissingWarnings) {
            msgs += msg
            this.asInstanceOf[S]
          } else { this.asInstanceOf[S] }
        case _ =>
          msgs += msg
          this.asInstanceOf[S]
      }
    }
  }

  trait PathResolutionState[S <: State[?]] extends MessagesState[S] {
    def root: Definition

    def symbolTable: SymbolTable

    var parents: Seq[Definition] = Seq.empty[Definition]

    def captureHierarchy(pars: Seq[Definition]): S = {
      parents = pars
      this.asInstanceOf[S]
    }

    private def findCandidates(
      n: String,
      pstack: mutable.Stack[Definition],
      nstack: mutable.Stack[String]
    ): Seq[Definition] = {
      // First get the list of candidate matches from the current node
      pstack.headOption match {
        case None =>
          // empty result means nothing to search
          Seq.empty[Definition]
        case Some(s: AST.State) =>
          // If we're at a entity's state, the state's fields are next
          s.aggregation.fields
        case Some(Field(_, _, Aggregation(_, fields), _, _)) =>
          // if we're at a field composed of more fields, those fields are it
          fields
        case Some(Type(_, _, Aggregation(_, fields), _, _))    => fields
        case Some(Type(_, _, MessageType(_, _, fields), _, _)) => fields
        case Some(OnClause(_, msgRef, _, _, _))                =>
          // if we're at an onClause that references a message then we
          // need to push that message's path on the name stack
          nstack.push(n) // undo the pop above
          nstack.pushAll(msgRef.id.value.reverse)
          Seq.empty[Definition]
        case Some(Field(_, _, AliasedTypeExpression(_, pid), _, _)) =>
          // if we're at a field that references another type then we
          // need to push that types path on the name stack
          nstack.push(n)
          nstack.pushAll(pid.value.reverse)
          Seq.empty[Definition]
        case Some(Type(_, _, AliasedTypeExpression(_, pid), _, _)) =>
          // if we're at a type definition that references another type then
          // we need to push that type's path on the name stack
          nstack.push(n)
          nstack.pushAll(pid.value.reverse)
          Seq.empty[Definition]
        case Some(f: Function) if f.input.nonEmpty =>
          // If we're at a Function node, the functions input parameters
          // are the candidates to search next
          f.input.get.fields
        case Some(p) if p.isContainer =>
          val conts = p.asInstanceOf[Container[Definition]].contents
          conts.flatMap {
            case Include(_, contents, _) => contents
            case d: Definition           => Seq(d)
          }
        case _ =>
          // anything else isn't searchable so n can't be found
          Seq.empty[Definition]
      }
    }

    /** Resolve a PathIdentifier If the path is already resolved or it has no
      * empty components then we can resolve it from the map or the symbol
      * table.
      *
      * @param pid
      *   The path to consider
      * @return
      *   Either an error or a definition
      */
    def resolveRelativePath(
      pid: PathIdentifier,
      parents: Seq[Definition] = parents
    ): Seq[Definition] = {
      val vstack = mutable.Stack.empty[Definition]
      val pstack = mutable.Stack.empty[Definition]
      // Implicit definitions don't have names so they don't count in the stack
      val namedParents = parents.filterNot(_.isImplicit).reverse
      pstack.pushAll(namedParents)
      val nstack = mutable.Stack.empty[String]
      nstack.pushAll(pid.value.reverse)
      while (nstack.nonEmpty) {
        val n = nstack.pop()
        // if we're supposed to pop parent off the stack ...
        if (n.isEmpty) {
          // if there is a parent to pop off the stack
          if (pstack.nonEmpty) {
            // pop it and the new result is the new head
            pstack.pop()
          }
        } else {
          pstack.headOption match {
            case None => // do nothing
            case Some(d) =>
              if (vstack.contains(d)) {
                this.addError(
                  pid.loc,
                  msg = s"""Path resolution encountered a loop at ${d.identify}
                           |  for name '$n' when resolving ${pid.format}
                           |  in definition context: ${parents.map(_.identify)
                    .mkString("\n    ", "\n    ", "\n")}
                           |""".stripMargin
                )
                pstack.clear()
              } else {
                val preFindSize = nstack.size
                val candidates = findCandidates(n, pstack, nstack)
                if (nstack.size > preFindSize) { vstack.push(d) }

                // Now find the match, if any, and handle appropriately
                val found = candidates.find(_.id.value == n)
                found match {
                  case Some(q: Definition) =>
                    // found the named item, and it is a Container, so put it on
                    // the stack in case there are more things to resolve
                    pstack.push(q)
                  case None =>
                  // No search result, there may be more things
                }
              }
          }
        }
      }
      if (pstack.size == 1) {
        if (pstack.head.isInstanceOf[RootContainer]) pstack.pop()
      }
      pstack.toSeq
    }

    def resolvePathFromSymbolTable(
      pid: PathIdentifier
    ): Seq[Definition] = {
      val symTabCompatibleNameSearch = pid.value.reverse
      val list = symbolTable.lookupParentage(symTabCompatibleNameSearch)
      list match {
        case Nil => // nothing found
          Seq.empty[Definition]
        case (d, parents) :: Nil => // list.size == 1
          d +: parents
        case _ => // list.size > 1
          Seq.empty[Definition]
      }
    }

    def resolvePath(
      pid: PathIdentifier,
      parents: Seq[Definition] = parents
    ): Seq[Definition] = {
      if (pid.value.isEmpty) { Seq.empty[Definition] }
      else if (pid.value.exists(_.isEmpty)) {
        resolveRelativePath(pid, parents)
      } else { resolvePathFromSymbolTable(pid) }
    }

    /*
    def resolvePathTo[TY <: Definition: ClassTag](
      pid: PathIdentifier,
      parents: Seq[Definition] = parents
    ): Seq[Definition] = {
      if (pid.value.isEmpty) { Seq.empty[Definition] }
      else if (pid.value.exists(_.isEmpty)) {
        resolveRelativePath(pid, parents)
      } else { resolvePathFromHierarchy(pid) }
    }

    private def resolvePathFromHierarchy[TY <: Definition: ClassTag](
      pid: PathIdentifier,
      parents: Seq[Definition] = parents
    ): Seq[Definition] = {}
     */
  }
}
