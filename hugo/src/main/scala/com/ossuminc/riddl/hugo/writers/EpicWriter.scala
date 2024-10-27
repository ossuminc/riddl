/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.{Definition, Epic, UseCase, User, UserStory, Parents}
import com.ossuminc.riddl.diagrams.mermaid.UseCaseDiagram
import com.ossuminc.riddl.passes.diagrams.{DiagramsPass, DiagramsPassOutput, UseCaseDiagramData}

trait EpicWriter { this: MarkdownWriter =>

  def emitEpic(epic: Epic, parents: Parents): Unit = {
    containerHead(epic)
    h2(epic.identify)
    emitVitalDefTable(epic, parents)
    if epic.userStory.nonEmpty then {
      val userPid = epic.userStory.user.pathId
      val parent = parents.head
      val maybeUser = generator.refMap.definitionOf[User](userPid, parent)
      h2("User Story")
      maybeUser match {
        case None => p(s"Unresolvable User id: ${userPid.format}")
        case Some(user) =>
          val name = user.id.value
          val role = user.is_a.s
          val us = epic.userStory
          val benefit = us.benefit.s
          val capability = us.capability.s
          val storyText =
            s"I, $name, as $role, want $capability, so that $benefit"
          p(italic(storyText))
      }
    }
    emitDescriptions(epic.descriptions) // FIXME: there could be more description
    emitOptions(epic.options)
    list("Visualizations", epic.shownBy.map(u => s"($u)[$u]"))
    emitTerms(epic.terms)
    definitionToc("Use Cases", epic.cases)
  }

  def emitUser(u: User, parents: Parents): this.type = {
    leafHead(u, weight = 20)
    p(s"${u.identify} is a ${u.is_a.s}.")
    emitDefDoc(u, parents)
  }

  def emitUseCase(uc: UseCase, parents: Parents): Unit = {
    containerHead(uc)
    emitDefDoc(uc, parents)
    h2("Sequence Diagram")
    parents.headOption match
      case Some(p1) =>
        val epic = p1.asInstanceOf[Epic]
        generator.outputs.outputOf[DiagramsPassOutput](DiagramsPass.name) match
          case Some(dpo) =>
            dpo.useCaseDiagrams.get(uc) match
              case Some(useCaseDiagramData: UseCaseDiagramData) =>

                val ucd = UseCaseDiagram(generator, useCaseDiagramData)
                val lines = ucd.generate
                emitMermaidDiagram(lines)

              case None =>
                notAvailable("Sequence diagram is not available")
            end match
          case None =>
            notAvailable("Sequence diagram is not available")
        end match
      case None =>
        notAvailable("Sequence diagram is not available")
    end match
  }
}
