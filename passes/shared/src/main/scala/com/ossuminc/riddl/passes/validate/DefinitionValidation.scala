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
trait DefinitionValidation extends BasicValidation {

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
    container: Parent, parents: Parents
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

  def checkDescriptions(definition: Definition, contents: Contents[ContentValues]): Unit =
    definition match
      case wd: WithDescriptives =>
        val descriptions = wd.descriptions
        if descriptions.isEmpty then
          check(
            predicate = false,
            s"${definition.identify} should have a description",
            MissingWarning,
            definition.loc
          )
        else
          descriptions.foreach {
            case bd: BlockDescription =>
              check(
                bd.lines.nonEmpty && !bd.lines.forall(_.s.isEmpty),
                s"For ${definition.identify}, description at ${bd.loc} is declared but empty",
                MissingWarning,
                bd.loc
              )
            case ud: URLDescription =>
              check(
                ud.url.isValid,
                s"For ${definition.identify}, description at ${ud.loc} has an invalid URL: ${ud.url}",
                Error,
                ud.loc
              )
            case _ => ()
          }
        end if
      case _ => ()
  end checkDescriptions

  def checkDescription(
    value: RiddlValue
  ): Unit = {
    val id: String = 
      value match
        case wi: WithIdentifier => wi.identify
        case _ => value.format
      end match
    val descriptions: Contents[Description] = 
      value match
        case wd: WithDescriptives => wd.descriptions
        case _ => Contents.empty 
      end match
    if descriptions.isEmpty then {
      check(
        predicate = false,
        s"$id should have a description",
        MissingWarning,
        value.loc
      )
    } else if descriptions.nonEmpty then
      descriptions.foreach { (desc: Description) =>
        check(
          desc.nonEmpty,
          s"For $id, description at ${desc.loc} is declared but empty",
          MissingWarning,
          desc.loc
        )
      }
  }
}
