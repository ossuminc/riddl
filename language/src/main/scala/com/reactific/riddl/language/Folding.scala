/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.ast.At
import com.reactific.riddl.utils.SeqHelpers.*

import scala.annotation.unused
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag
import scala.reflect.classTag

object Folding {

  type SimpleDispatch[S] = (Container[Definition], Definition, S) => S

  def foldEachDefinition[S](
    parent: Definition,
    child: Definition,
    state: S
  )(f: SimpleDispatch[S]
  ): S = {
    child match {
      case definition: LeafDefinition => f(parent, definition, state)
      case definition: Definition =>
        val result = f(parent, child, state)
        definition.contents.foldLeft(result) { case (next, child) =>
          foldEachDefinition[S](definition, child, next)(f)
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
          case i: Include[Definition] @unchecked => i.contents.foldLeft(next) {
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

    def addStyle(loc: At, msg: String): S = {
      add(Message(loc, msg, StyleWarning))
    }

    def addMissing(loc: At, msg: String): S = {
      add(Message(loc, msg, MissingWarning))
    }

    def addWarning(loc: At, msg: String): S = {
      add(Message(loc, msg, Warning))
    }

    def addError(loc: At, msg: String): S = {
      add(Message(loc, msg, Error))
    }

    def addSevere(loc: At, msg: String): S = {
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

    def symbolTable: SymbolTable

    def pathIdToDefinition(
      pid: PathIdentifier,
      parents: Seq[Definition]
    ): Option[Definition] = {
      val result = resolvePath(pid, parents)()()
      result.headOption
    }

    private def adjustStacksForPid(
      searchFor: String,
      pid: PathIdentifier,
      parentStack: mutable.Stack[Definition],
      nameStack: mutable.Stack[String]
    ): Seq[Definition] = {
      // Since we're at a field that references another type then we
      // need to push that type's path on the name stack which is just itself
      nameStack.push(searchFor)
      // Now push the names we found in the pid, to be resolved yet
      nameStack.pushAll(pid.value.reverse)
      // Get the next name to resolve
      val top = pid.value.head
      // If it is a resolvable name and that name is on the parent stack
      if (top.nonEmpty && parentStack.exists(_.id.value == top)) {
        // Remove the top of stack name we just pushed, because we just found it
        nameStack.pop()
        // Drop up the stack until we find the name we just found
        parentStack.popUntil(_.id.value == top)
      }
      Seq.empty[Definition]
    }

    private def findCandidates(
      searchFor: String,
      parentStack: mutable.Stack[Definition],
      nameStack: mutable.Stack[String]
    ): Seq[Definition] = {
      if (parentStack.isEmpty) {
        // Nothing in the parent stack so we're done searching and
        // we return empty to signal nothing found
        Seq.empty[Definition]
      } else {
        parentStack.head match {
          case oc: OnMessageClause =>
            // if we're at an onClause that references a message then we
            // need to push that message's path on the name stack
            adjustStacksForPid(searchFor, oc.msg.pathId, parentStack, nameStack)
          case f: Field => f.typeEx match {
              case Aggregation(_, fields) =>
                // if we're at a field composed of more fields, then those fields
                // what we are looking for
                fields
              case Enumeration(_, enumerators) => enumerators
              case AggregateUseCaseTypeExpression(_, _, fields)   =>
                // Message types have fields too, those fields are what we seek
                fields
              case AliasedTypeExpression(_, pid) =>
                // if we're at a field that references another type then we
                // need to push that types path on the name stack
                adjustStacksForPid(searchFor, pid, parentStack, nameStack)
              case _ =>
                // Any other type expression can't be descend into
                Seq.empty[Definition]
            }
          case t: Type => t.typ match {
              case Aggregation(_, fields)        => fields
              case Enumeration(_, enumerators)   => enumerators
              case AggregateUseCaseTypeExpression(_, _, fields)     => fields
              case AliasedTypeExpression(_, pid) =>
                // if we're at a type definition that references another type then
                // we need to push that type's path on the name stack
                adjustStacksForPid(searchFor, pid, parentStack, nameStack)
              case _ =>
                // Any other type expression can't be descended into
                Seq.empty[Definition]
            }
          case f: Function if f.input.nonEmpty =>
            // If we're at a Function node, the functions input parameters
            // are the candidates to search next
            f.input.get.fields
          case d: Definition => d.contents.flatMap {
              case Include(_, contents, _) => contents
              case d: Definition           => Seq(d)
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
      while (nameStack.nonEmpty) {
        // Pop the name we're currently looking for and save it
        val soughtName = nameStack.pop()

        // if the name indicates we are supposed to pop parent off the stack ...
        if (soughtName.isEmpty) {
          // if there is a parent to pop off the stack
          if (parentStack.nonEmpty) {
            // pop it and the result is the new head, if there's no more names
            parentStack.pop()
          }
        } else {
          // We have a name to search for if the parent stack is not empty
          if (parentStack.nonEmpty) {
            val definition =
              parentStack.head // get the next definition of the parentStack

            // If we have already visited this definition, its an error
            if (visitedStack.contains(definition)) {
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
              parentStack.clear()
            } else {
              // otherwise we are good to search for soughtName

              // Look where we are and find the candidate things that could
              // possibly match soughtName
              val candidates =
                findCandidates(soughtName, parentStack, nameStack)

              // If the name stack grew because findCandidates added to it
              val newSoughtName =
                if (candidates.isEmpty) {
                  // then push the definition on the visited stack because we
                  // already resolved this one and looked for candidates, no
                  // point looping through here again.
                  visitedStack.push(definition)

                  // The name we are now searching for may have been updated by the
                  // findCandidates function adjusting the stacks.
                  nameStack.headOption match {
                    case None       => soughtName
                    case Some(name) => name
                  }

                } else { soughtName }

              // Now find the match, if any, and handle appropriately
              val found = candidates.find(_.id.value == newSoughtName)
              found match {
                case Some(q: Definition) =>
                  // found the named item, and it is a Container, so put it on
                  // the stack in case there are more things to resolve
                  parentStack.push(q)
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

    private def resolvePathFromHierarchy(
      pid: PathIdentifier,
      parents: Seq[Definition]
    ): Seq[Definition] = {
      // First, scan up through the parent stack to find the starting place
      val top = pid.value.head
      val newParents = parents.dropUntil(_.id.value == top)
      if (newParents.isEmpty) {
        newParents // is empty, signalling "not found"
      } else if (pid.value.length == 1) {
        // we found the only name so let's just return it because the found
        // definition is just the head of the adjusted newParents
        Seq(newParents.head)
      } else {
        // we found the starting point, adjust the PathIdentifier to drop the
        // one we found, and
        val newPid = PathIdentifier(pid.loc, pid.value.drop(1))
        resolveRelativePath(newPid, newParents)
      }
    }

    def doNothingSingle(defStack: Seq[Definition]): Seq[Definition] = {
      defStack
    }

    def doNothingMultiple(
      @unused list: List[(Definition, Seq[Definition])]
    ): Seq[Definition] = { Seq.empty[Definition] }

    def resolvePathIdentifier[DEF <: Definition: ClassTag](
      pid: PathIdentifier,
      parents: Seq[Definition]
    ): Option[DEF] = {
      def isSameKind(d: Definition): Boolean = {
        val clazz = classTag[DEF].runtimeClass
        d.getClass == clazz
      }

      if (pid.value.isEmpty) { None }
      else if (pid.value.exists(_.isEmpty)) {
        resolveRelativePath(pid, parents).headOption match {
          case Some(head) if isSameKind(head) => Some(head.asInstanceOf[DEF])
          case _                             => None
        }
      } else {
        resolvePathFromHierarchy(pid, parents).headOption match {
          case Some(head) if isSameKind(head) => Some(head.asInstanceOf[DEF])
          case _ =>
            val symTabCompatibleNameSearch = pid.value.reverse
            val list = symbolTable.lookupParentage(symTabCompatibleNameSearch)
            list match {
              case Nil => // nothing found
                // We couldn't find the path in the hierarchy or the symbol table
                // so let's signal this by returning an empty sequence
                None
              case (d, _) :: Nil if isSameKind(d) => // exact match
                // Give caller an option to do something or morph the results
                Some(d.asInstanceOf[DEF])
              case _ => None
            }
        }
      }
    }

    def resolvePath(
      pid: PathIdentifier,
      parents: Seq[Definition]
    )(onSingle: Seq[Definition] => Seq[Definition] = doNothingSingle
    )(onMultiple: List[(Definition, Seq[Definition])] => Seq[Definition] =
        doNothingMultiple
    ): Seq[Definition] = {
      if (pid.value.isEmpty) { Seq.empty[Definition] }
      else if (pid.value.exists(_.isEmpty)) {
        val resolution = resolveRelativePath(pid, parents)
        onSingle(resolution)
      } else {
        val result = resolvePathFromHierarchy(pid, parents)
        if (result.nonEmpty) { onSingle(result) }
        else {
          val symTabCompatibleNameSearch = pid.value.reverse
          val list = symbolTable.lookupParentage(symTabCompatibleNameSearch)
          list match {
            case Nil => // nothing found
              // We couldn't find the path in the hierarchy or the symbol table
              // so let's signal this by returning an empty sequence
              Seq.empty[Definition]
            case (d, parents) :: Nil => // exact match
              // Give caller an option to do something or morph the results
              onSingle(d +: parents)
            case list => // ambiguous match
              // Give caller an option to do something or morph the results
              onMultiple(list)
          }
        }
      }
    }
  }
}
