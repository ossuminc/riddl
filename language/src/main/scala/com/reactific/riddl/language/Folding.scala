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

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

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
      searchFor: String,
      pstack: mutable.Stack[Definition],
      nstack: mutable.Stack[String]
    ): Seq[Definition] = {
      if (pstack.isEmpty) {
        // Nothing in the parent stack so we're done searching and
        // we return empty to signal nothing found
        Seq.empty[Definition]
      } else {
        pstack.head match {
          case s: AST.State =>
            // If we're at an entity's state, the state's fields are next
            s.aggregation.fields
          case p: Projection =>
            // Projections have fields like states
            p.aggregation.fields
          case oc: OnClause =>
            // if we're at an onClause that references a message then we
            // need to push that message's path on the name stack
            nstack.push(searchFor) // undo the pop above
            nstack.pushAll(oc.msg.id.value.reverse)
            Seq.empty[Definition]
          case f: Field => f.typeEx match {
              case Aggregation(_, fields) =>
                // if we're at a field composed of more fields, then those fields
                // what we are looking for
                fields
              case MessageType(_, _, fields) =>
                // Message types have fields too, those fields are what we seek
                fields
              case AliasedTypeExpression(_, pid) =>
                // if we're at a field that references another type then we
                // need to push that types path on the name stack
                nstack.push(searchFor)
                nstack.pushAll(pid.value.reverse)
                Seq.empty[Definition]
              case _ =>
                // Anything else doesn't have something to descend into
                Seq.empty[Definition]
            }
          case t: Type => t.typ match {
              case Aggregation(_, fields)        => fields
              case MessageType(_, _, fields)     => fields
              case AliasedTypeExpression(_, pid) =>
                // if we're at a type definition that references another type then
                // we need to push that type's path on the name stack
                nstack.push(searchFor)
                nstack.pushAll(pid.value.reverse)
                Seq.empty[Definition]
              case _ => Seq.empty[Definition]
            }
          case f: Function if f.input.nonEmpty =>
            // If we're at a Function node, the functions input parameters
            // are the candidates to search next
            f.input.get.fields
          case v: VitalDefinition[?] => v.contents.flatMap {
              case Include(_, contents, _) => contents
              case d: Definition           => Seq(d)
            }
          case _ =>
            // anything else isn't searchable so the name can't be found
            Seq.empty[Definition]
        }
      }
    }

    /** Resolve a Relative PathIdentifier. If the path is already resolved or it
      * has no empty components then we can resolve it from the map or the
      * symbol table.
      *
      * @param pid
      *   The path to consider
      * @param parents
      *   The parent stack to provide the context from which the search starts
      * @return
      *   Either an error or a definition
      */
    def resolveRelativePath(
      pid: PathIdentifier,
      parents: Seq[Definition] = parents
    ): Seq[Definition] = {

      // Initialize the visited stack. This is used to detect looping. We
      // should never visit the same definition twice but if we do we will
      // catch it below.
      val vstack = mutable.Stack.empty[Definition]

      // Implicit definitions don't have names so they don't count in the stack
      val namedParents = parents.filterNot(_.isImplicit).reverse

      // Build the parent stack from the named parents
      val pstack = mutable.Stack.empty[Definition]
      pstack.pushAll(namedParents)

      // Build the name stack from the PathIdentifier provided
      val nstack = mutable.Stack.empty[String]
      nstack.pushAll(pid.value.reverse)

      // Loop over the names in the stack. Note that mutable stacks are used
      // here because the algorithm can adjust them as it finds intermediary
      // definitions. If the name stack becomes empty, we're done searching.
      while (nstack.nonEmpty) {
        // Pop the name we're currently looking for and save it
        val soughtName = nstack.pop()

        // if the name indicates we are supposed to pop parent off the stack ...
        if (soughtName.isEmpty) {
          // if there is a parent to pop off the stack
          if (pstack.nonEmpty) {
            // pop it and the result is the new head, if there's no more names
            pstack.pop()
          }
        } else {
          // We have a name to search for if the parent stack is not empty
          if (pstack.nonEmpty) {
            val definition =
              pstack.head // get the next definition of the pstack

            // If we have already visisted this definition, its an error
            if (vstack.contains(definition)) {
              // Generate the error message
              this.addError(
                pid.loc,
                msg = s"""Path resolution encountered a loop at ${definition
                  .identify}
                         |  for name '$soughtName' when resolving ${pid.format}
                         |  in definition context: ${parents.map(_.identify)
                  .mkString("\n    ", "\n    ", "\n")}
                         |""".stripMargin
              )
              // Signal we're done searching with no result
              pstack.clear()
            } else {
              // otherwise we are good to search for soughtName

              // capture the size of the name stack before we search
              val preFindSize = nstack.size

              // Look where we are and find the candidate things that could
              // possibly match soughtName
              val candidates = findCandidates(soughtName, pstack, nstack)

              // If the name stack grew because findCandidates added to it
              if (nstack.size > preFindSize) {
                // then push the definition on the visited stack because we
                // already resolved this one and looked for candidates, no
                // point looping through here again.
                vstack.push(definition)
              }

              // Now find the match, if any, and handle appropriately
              val found = candidates.find(_.id.value == soughtName)
              found match {
                case Some(q: Definition) =>
                  // found the named item, and it is a Container, so put it on
                  // the stack in case there are more things to resolve
                  pstack.push(q)
                case None =>
                // No search result, there may be more things to find in
                // the next iteration
              }
            }
          }
        }
      }

      // if there is a single thing left on the stack and that things is
      // a RootContainer
      if (pstack.size == 1 && pstack.head.isInstanceOf[RootContainer]) {
        // then pop it off because RootContainers don't count and we want to
        // rightfully return an empty sequence for "not found"
        pstack.pop()
      }
      // Convert parent stack to immutable sequence
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

    def resolvePathFromHierarchy(
      pid: PathIdentifier,
      container: Definition,
      parents: Seq[Definition] = parents
    ): Option[Definition] = {
      // First, search from the container downward
      container.find(pid.value.head) match {
        case Some(found) =>
          // found it, now descend through the path
          descendingResolve(found, pid.value.tail)
        case None =>
          // container doesn't have that name, see if it is one of the parents
          // names
          val top = pid.value.head
          val all = container +: parents
          all.find(_.id.value == top) match {
            case Some(found) =>
              // we matched a parent name, now descend from there
              descendingResolve(found, pid.value.tail)
            case None =>
              // No match, can't continue
              Option.empty[Definition]
          }
      }
    }

    @tailrec private def descendingResolve(
      definition: Definition,
      names: Seq[String]
    ): Option[Definition] = {
      if (names.isEmpty) { Some(definition) }
      else {
        definition.find(names.head) match {
          case None        => None
          case Some(found) => descendingResolve(found, names.tail)
        }
      }
    }
  }
}
