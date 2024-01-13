/*
 * Copyright 2023 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.language.{AST, Messages}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.{CollectingPass, CollectingPassOutput, PassCreator, PassInfo, PassInput, PassesOutput}

import scala.collection.mutable

case class ToDoItem(
  item: String,
  author: String,
  path: String,
  link: String
)

case class ToDoListOutput(
  messages: Messages.Messages,
  collected: Seq[ToDoItem] = Seq.empty
) extends CollectingPassOutput[ToDoItem]

case class ToDoListPass(input: PassInput, outputs: PassesOutput, options: HugoCommand.Options)
    extends CollectingPass[ToDoItem](input, outputs)
    with PassUtilities {

  protected def collect(definition: RiddlValue, parents: mutable.Stack[Definition]): Seq[ToDoItem] = {
    definition match {
      case _: Root | _: Interaction | _: Include[Definition] @unchecked =>
        // None of these kinds of definitions contribute to the TODO List because they have a weird name
        Seq.empty[ToDoItem]
      case ad: Definition if ad.isImplicit => Seq.empty[ToDoItem]
      // Implicit definitions don't have a name so there's no word to define in the glossary
      case d: Definition if d.isEmpty =>
        val pars = parents.toSeq
        val item = d.identify
        val authors = AST.findAuthors(d, pars)
        val auths = if authors.isEmpty then Seq("Unspecified Author") else mkAuthor(authors, pars)
        val prnts = makeStringParents(pars)
        val path = (prnts :+ d.id.value).mkString(".")
        val link = makeDocLink(d, prnts)
        auths.map(auth => ToDoItem(item, auth, path, link))
      case _ =>
        Seq.empty[ToDoItem]
    }
  }

  private def mkAuthor(authors: Seq[AuthorRef], parents: Seq[Definition]): Seq[String] = {
    if authors.isEmpty then Seq.empty
    else
      parents.headOption match {
        case None => Seq.empty
        case Some(parent: Definition) =>
          authors
            .map { (ref: AuthorRef) =>
              outputs.refMap.definitionOf[Author](ref.pathId, parent)
            }
            .filterNot(_.isEmpty)
            .map(_.get)
            .map(x => s"${x.name.s} &lt;${x.email.s}&gt;")
      }
  }

  override def result: ToDoListOutput = {
    ToDoListOutput(messages.toMessages, collectedValues.toSeq)
  }

  def name: String = ToDoListPass.name
  def postProcess(root: Root): Unit = ()
}

object ToDoListPass extends PassInfo {
  val name: String = "ToDoList"
  val creator: PassCreator = { (in: PassInput, out: PassesOutput) => ToDoListPass(in, out, HugoCommand.Options() ) }
}

// val finder: Finder = Finder(root)
//       val items: Seq[(String, String, String, String)] = {
//         for {
//           (defn: Definition, pars: Seq[Definition]) <- finder.findEmpty
//           item = defn.identify
//           authors = AST.findAuthors(defn, pars)
//           author = mkAuthor(authors, pars)
//           parents = makeParents(pars)
//           path = parents.mkString(".")
//           link = makeDocLink(defn, parents)
//         } yield (item, author, path, link)
//       }
//
//       val map = items
//         .groupBy(_._2)
//         .view
//         .mapValues(_.map { case (item, _, path, link) =>
//           s"[$item In $path]($link)"
//         })
//         .toMap
