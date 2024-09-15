/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.passes.symbols.SymbolsOutput

/** A Trait that defines typical Validation checkers for validating definitions */
trait DefinitionValidation extends BasicValidation:

  def symbols: SymbolsOutput

  private def checkUniqueContent(definition: Parent): Unit = {
    val allNamedValues = definition.contents.definitions
    val allNames = allNamedValues.map(_.identify)
    if allNames.distinct.size < allNames.size then {
      val duplicates: Map[String, Seq[Definition]] =
        allNamedValues.groupBy(_.identify).filterNot(_._2.size < 2)
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
  }

  def checkDefinition(
    parents: Parents,
    definition: Definition
  ): Unit = {
    check(
      definition.id.nonEmpty | definition.isAnonymous,
      "Definitions may not have empty names",
      Error,
      definition.loc
    )
      .checkIdentifierLength(definition)
      .check(
        !definition.isVital || definition.hasAuthorRefs,
        "Vital definitions should have an author reference",
        MissingWarning,
        definition.loc
      )
    definition match
      case vd: VitalDefinition[?] =>
        vd.authorRefs.foreach { (authorRef: AuthorRef) =>
          pathIdToDefinition(authorRef.pathId, definition.asInstanceOf[Parent] +: parents) match
            case None =>
              messages.addError(
                authorRef.loc,
                s"${authorRef.format} is not defined"
              )
            case _ =>
          end match
        }
      case _ => ()
    end match

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
  }

  def checkContents(
    container: Parent,
    parents: Parents
  ): Unit =
    val parent: Parent = parents.headOption.getOrElse(Root.empty)
    check(
      container.contents.definitions.nonEmpty || container.isInstanceOf[Field],
      s"${container.identify} in ${parent.identify} should have content",
      MissingWarning,
      container.loc
    )
  end checkContents

  def checkContainer(
    parents: Parents,
    container: Parent
  ): Unit = {
    checkDefinition(parents, container)
    checkContents(container, parents)
    checkUniqueContent(container)
  }
  def checkDescriptives(definition: Definition & WithMetaData): Unit =
    checkDescriptives(definition.identify, definition)

  def checkDescriptives(identity: String, definition: WithMetaData): Unit =
    check(
      definition.metadata.nonEmpty,
      s"Descriptives in $identity should  not be empty",
      MissingWarning,
      definition.loc
    )
    var hasAuthorRef = false
    var hasDescription = false
    for { descriptive <- definition.metadata.toSeq} do {
      descriptive match
        case bd: BriefDescription =>
          check(
            bd.brief.s.length < 80,
            s"In $identity, brief description at ${bd.loc} is too long. Max is 80 chars",
            Warning,
            bd.loc
          )
        case bd: BlockDescription =>
          check(
            bd.lines.nonEmpty && !bd.lines.forall(_.s.isEmpty),
            s"For $identity, description at ${bd.loc} is declared but empty",
            MissingWarning,
            bd.loc
          )
          check(
            bd.lines.nonEmpty,
            s"For $identity, description is declared but empty",
            MissingWarning,
            bd.loc
          )

          hasDescription = true
        case ud: URLDescription =>
          check(
            ud.url.isValid,
            s"For $identity, description at ${ud.loc} has an invalid URL: ${ud.url}",
            Error,
            ud.loc
          )
          hasDescription = true
        case t: Term =>
          check(
            t.definition.length > 10,
            s"${t.identify}'s definition is too short. It must be at least 10 characters'",
            Warning,
            t.loc
          )
        case _: AuthorRef   =>
          hasAuthorRef = true
        case _: StringAttachment => ()
        case _: FileAttachment => ()
        case _: ULIDAttachment => () 
        case _: Description => ()
    }
    check(hasDescription,s"$identity should have a description", MissingWarning, definition.loc)
    check(hasAuthorRef,s"$identity should have an author reference", MissingWarning, definition.loc)
  end checkDescriptives
end DefinitionValidation
