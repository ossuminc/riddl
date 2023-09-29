/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.At
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.passes.symbols.SymbolsOutput

import scala.math.abs

/** Unit Tests For DefinitionValidationState */
trait DefinitionValidation extends BasicValidation  {

  def symbols: SymbolsOutput

  def checkOptions[T <: OptionValue](options: Seq[T], loc: At): this.type = {
    check(
      options.sizeIs == options.distinct.size,
      "Options should not be repeated",
      Error,
      loc
    )
    this
  }

  def checkOption[A <: RiddlValue](
    opt: Option[A],
    name: String,
    thing: Definition
  )(checker: A => Unit): this.type = {
    opt match {
      case None =>
        messages.addMissing(thing.loc, s"$name in ${thing.identify} should not be empty")
      case Some(x) =>
        checkNonEmptyValue(x, "Condition", thing, MissingWarning)
        checker(x)
    }
    this
  }

  private def checkUniqueContent(definition: Definition): this.type = {
    val allNames = definition.contents.filterNot(_.isImplicit).map(_.id.value)
    if allNames.distinct.size != allNames.size then {
      val duplicates: Map[String, Seq[Definition]] =
        definition.contents.groupBy(_.id.value).filterNot(_._2.size < 2)
      if duplicates.nonEmpty then {
        val details = duplicates
          .map { case (_: String, defs: Seq[Definition]) =>
            defs.map(_.identifyWithLoc).mkString(", and ")
          }
          .mkString("", "\n  ", "\n")
        messages.addError(
          definition.loc,
          s"${definition.identify} has duplicate content names:\n  $details"
        )
      }
    }
    this
  }

  def checkDefinition(
    parents: Seq[Definition],
    definition: Definition
  ): this.type = {
    check(
      definition.id.nonEmpty | definition.isImplicit,
      "Definitions may not have empty names",
      Error,
      definition.loc
    )
    .checkIdentifierLength(definition)
    .checkUniqueContent(definition)
    .check(
      !definition.isVital || definition.hasAuthors,
      "Vital definitions should have an author reference",
      MissingWarning,
      definition.loc
    )
    if definition.isVital then {
      definition.asInstanceOf[WithAuthors].authors.foreach { (authorRef: AuthorRef) =>
        pathIdToDefinition(authorRef.pathId, definition +: parents) match {
          case None =>
            messages.addError(
              authorRef.loc,
              s"${authorRef.format} is not defined"
            )
          case _ =>
        }
      }
    }

    val path = symbols.pathOf(definition)
    if !definition.id.isEmpty then {
      val matches = symbols.lookup[Definition](path)
      if matches.isEmpty then {
        messages.addSevere(
          definition.id.loc,
          s"'${definition.id.value}' evaded inclusion in symbol table!"
        )
      }
    }
    this
  }

  def checkContainer(
    parents: Seq[Definition],
    container: Definition
  ): this.type = {
    val parent: Definition = parents.headOption.getOrElse(RootContainer.empty)
    checkDefinition(parents, container).check(
      container.nonEmpty || container.isInstanceOf[Field],
      s"${container.identify} in ${parent.identify} should have content",
      MissingWarning,
      container.loc
    )
  }

  def checkDescription[TD <: DescribedValue](
    id: String,
    value: TD
  ): this.type = {
    val description: Option[Description] = value.description
    val shouldCheck: Boolean = {
      value.isInstanceOf[Type] |
        (value.isInstanceOf[Definition] && value.nonEmpty)
    }
    if description.isEmpty && shouldCheck then {
      check(
        predicate = false,
        s"$id should have a description",
        MissingWarning,
        value.loc
      )
    } else if description.nonEmpty then
      description.fold(this) { (desc: Description) =>
          check(
            desc.nonEmpty,
            s"For $id, description at ${desc.loc} is declared but empty",
            MissingWarning,
            desc.loc
          )
      }
    this
  }

  def checkDescription[TD <: Definition](
    definition: TD
  ): this.type = {
    checkDescription(definition.identify, definition)
  }

  def checkStreamletShape(proc: Streamlet): this.type = {
    val ins = proc.inlets.size
    val outs = proc.outlets.size

    def generateError(
      proc: Streamlet,
      req_ins: Int,
      req_outs: Int
    ): this.type = {
      def sOutlet(n: Int): String = {
        if n == 1 then s"1 outlet"
        else if n < 0 then {
          s"at least ${abs(n)} outlets"
        } else s"$n outlets"
      }

      def sInlet(n: Int): String = {
        if n == 1 then s"1 inlet"
        else if n < 0 then {
          s"at least ${abs(n)} outlets"
        } else s"$n inlets"
      }

      messages.addError(
        proc.loc,
        s"${proc.identify} should have " + sOutlet(req_outs) + " and " +
          sInlet(req_ins) + s" but it has " + sOutlet(outs) + " and " +
          sInlet(ins)
      )
      this
    }

    if !proc.isEmpty then {
      proc.shape match {
        case _: Source =>
          if ins != 0 || outs != 1 then {
            generateError(proc, 0, 1)
          }
        case _: Flow =>
          if ins != 1 || outs != 1 then {
            generateError(proc, 1, 1)
          }
        case _: Sink =>
          if ins != 1 || outs != 0 then {
            generateError(proc, 1, 0)
          }
        case _: Merge =>
          if ins < 2 || outs != 1 then {
            generateError(proc, -2, 1)
          }
        case _: Split =>
          if ins != 1 || outs < 2 then {
            generateError(proc, 1, -2)
          }
        case _: Router =>
          if ins < 2 || outs < 2 then {
            generateError(proc, -2, -2)
          }
        case _: Void =>
          if ins > 0 || outs > 0 then {
            generateError(proc, 0, 0)
          }
      }
    }
    this
  }

}
