/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo
import com.ossuminc.riddl.commands.hugo.themes.ThemeGenerator
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.resolve.ResolutionPass
import com.ossuminc.riddl.passes.symbols.SymbolsPass
import com.ossuminc.riddl.passes.{CollectingPass, CollectingPassOutput, PassCreator, PassesOutput, PassInfo, PassInput}
import com.ossuminc.riddl.utils.PlatformContext
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.PassRoot

import scala.collection.mutable

/** Information about message types collected by the MessagesPass
  *
  * @param message
  *   The type of the message - shows the fields of the message, with their brief descriptions if any
  * @param link
  *   The documentation link to the message type
  * @param users
  *   shows the places from which this message is used with full path identification, and each path component is a link
  *   to the documentation for that definition.
  * @param description
  *   Brief description of the message
  */
case class MessageInfo(
  kind: String,
  message: String,
  link: String,
  users: String,
  description: String
)

case class MessageOutput(
  root: PassRoot,
  messages: Messages.Messages,
  collected: Seq[MessageInfo]
) extends CollectingPassOutput[MessageInfo]

// This is a request to create a new section in the hugo translator's output that organizes information focusing on
// message types, how they are used, where they are defined, and where in the hierarchy they exist.
// Message types are the four DDD types: command, event, query, and result. The documentation for every leaf subdomain
// should have four pages added, one for each kind of message type. Those pages should list all the messages of their
// type that are USED by the subdomain (all contexts) but none that are merely accessible by the subdomain. Ideally,
// that wouldn't happen, but in practice, it will.
//
// Each page contains a list of relevant messages. Each row in that list starts with the name of the message and then
// a set of tabs:
//

case class MessagesPass(input: PassInput, outputs: PassesOutput, options: HugoPass.Options)(using PlatformContext)
    extends CollectingPass[MessageInfo](input, outputs) {

  requires(SymbolsPass)
  requires(ResolutionPass)

  private val usages = outputs.usage

  private val generator = ThemeGenerator(options, input, outputs, messages)

  def name: String = MessagesPass.name

  protected def collect(definition: RiddlValue, parents: ParentStack): Seq[MessageInfo] = {
    definition match {
      case t: Type =>
        val result = t.typEx match {
          case _: AggregateUseCaseTypeExpression =>
            val pars = generator.makeStringParents(parents.toParents)
            val link = generator.makeDocLink(t, pars)
            val users = usages.getUsers(t)
            val userLinks = users
              .map { definition =>
                s"[${definition.id.value}](${generator.makeDocLink(definition, pars)})"
              }
              .mkString(" <br> ")
            val description: String = t.metadata.filter[BriefDescription].map(_.brief.s).mkString("\n")
            val mi = MessageInfo(t.kind, t.id.value, link, userLinks, description)
            Seq(mi)
          case _ =>
            Seq.empty[MessageInfo]
        }
        result
      case _ =>
        Seq.empty[MessageInfo]
    }
  }

  override def result(root: PassRoot): MessageOutput = {
    val sortedList = collectedValues.sortBy(_.message).toSeq
    MessageOutput(root, messages.toMessages, sortedList)
  }
}

object MessagesPass extends PassInfo[HugoPass.Options] {
  val name: String = "Messages"
  def creator(
    options: HugoPass.Options
  )(using PlatformContext): (PassInput, PassesOutput) => MessagesPass = {
    (in: PassInput, out: PassesOutput) =>
      MessagesPass(in, out, options)
  }
}
