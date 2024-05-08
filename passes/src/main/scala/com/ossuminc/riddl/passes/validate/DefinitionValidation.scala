/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.passes.symbols.SymbolsOutput

/** A Trait that defines typical Validation checkers for validating definitions */
trait DefinitionValidation extends BasicValidation {

  def symbols: SymbolsOutput

  private def checkUniqueContent(definition: Definition): this.type = {
    val allNamedValues = definition.namedValues
    val allNames = allNamedValues.map(_.identify)
    if allNames.distinct.size < allNames.size then {
      val duplicates: Map[String, Seq[NamedValue]] =
        allNamedValues.groupBy(_.identify).filterNot(_._2.size < 2)
      if duplicates.nonEmpty then {
        val details = duplicates
          .map { case (_: String, defs: Seq[NamedValue]) =>
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
      definition.id.nonEmpty | definition.isAnonymous,
      "Definitions may not have empty names",
      Error,
      definition.loc
    )
      .checkIdentifierLength(definition)
      .checkUniqueContent(definition)
      .check(
        !definition.isVital || definition.hasAuthorRefs,
        "Vital definitions should have an author reference",
        MissingWarning,
        definition.loc
      )
    if definition.isVital then {
      definition.asInstanceOf[WithAuthorRefs].authorRefs.foreach { (authorRef: AuthorRef) =>
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
    val parent: Definition = parents.headOption.getOrElse(Root.empty)
    checkDefinition(parents, container).check(
      container.nonEmpty || container.isInstanceOf[Field],
      s"${container.identify} in ${parent.identify} should have content",
      MissingWarning,
      container.loc
    )
  }

  def checkDescription[TD <: DescribedValue](
    value: TD
  ): this.type = {
    val id = if value.isIdentified then value.asInstanceOf[WithIdentifier].identify else value.format
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

}
