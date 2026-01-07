/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo
import com.ossuminc.riddl.commands.hugo.themes.ThemeGenerator
import com.ossuminc.riddl.language.{AST, Messages}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.{CollectingPass, CollectingPassOutput, PassCreator, PassesOutput, PassInfo, PassInput}
import com.ossuminc.riddl.passes.PassRoot
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable

case class ToDoItem(
  item: String,
  author: String,
  path: String,
  link: String
)

case class ToDoListOutput(
  root: PassRoot,
  messages: Messages.Messages,
  collected: Seq[ToDoItem] = Seq.empty
) extends CollectingPassOutput[ToDoItem]

case class ToDoListPass(
  input: PassInput,
  outputs: PassesOutput,
  options: HugoPass.Options
)(using PlatformContext)
    extends CollectingPass[ToDoItem](input, outputs) {

  private val generator: ThemeGenerator = ThemeGenerator(options, input, outputs, messages)

  protected def collect(definition: RiddlValue, parents: ParentStack): Seq[ToDoItem] = {
    definition match {
      case _: Root | _: Interaction | _: Include[Definition] @unchecked =>
        // None of these kinds of definitions contribute to the TODO List because they have a weird name
        Seq.empty[ToDoItem]
      case ad: Definition if ad.isAnonymous => Seq.empty[ToDoItem]
      // Implicit definitions don't have a name so there's no word to define in the glossary
      case d: Definition if d.isEmpty =>
        val pars = parents.toParents
        val item = d.identify
        val authors =
          d match
            case wd: WithMetaData => AST.findAuthors(wd, pars.toContents)
            case _ => Seq.empty[AuthorRef]
          end match
        val auths = if authors.isEmpty then Seq("Unspecified Author") else mkAuthor(authors, pars)
        val prnts = generator.makeStringParents(pars)
        val path = (prnts :+ d.id.value).mkString(".")
        val link = generator.makeDocLink(d, prnts)
        auths.map(auth => ToDoItem(item, auth, path, link))
      case _ =>
        Seq.empty[ToDoItem]
    }
  }

  private def mkAuthor(authors: Seq[AuthorRef], parents: Parents): Seq[String] = {
    if authors.isEmpty then Seq.empty
    else
      parents.headOption match {
        case None => Seq.empty
        case Some(parent: Branch[?]) =>
          authors
            .map { (ref: AuthorRef) =>
              outputs.refMap.definitionOf[Author](ref.pathId, parent)
            }
            .filterNot(_.isEmpty)
            .map(_.get)
            .map(x => s"${x.name.s} &lt;${x.email.s}&gt;")
      }
  }

  override def result(root: PassRoot): ToDoListOutput = {
    ToDoListOutput(root, messages.toMessages, collectedValues.toSeq)
  }

  def name: String = ToDoListPass.name
}

object ToDoListPass extends PassInfo[HugoPass.Options] {
  val name: String = "ToDoList"
  def creator(options: HugoPass.Options)(using PlatformContext): PassCreator = { (in: PassInput, out: PassesOutput) =>
    ToDoListPass(in, out, options)
  }
}
