package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.ossuminc.riddl.passes.symbols.{Symbols, SymbolsPass}
import com.ossuminc.riddl.passes.{CollectingPass, CollectingPassOutput, PassCreator, PassInfo, PassInput, PassOutput, PassesOutput}

import scala.collection.mutable

/** Information about message types collected by the MessagesPass
 *
 * @param message
  *   The type of the message - shows the fields of the message, with their brief descriptions if any
 * @param link
 *    The documentation link to the message type
  * @param users
  *   shows the places from which this message is used with full path identification, and each path component is a link
  *   to the documentation for that definition.
  * @param description
 *    Brief description of the message
  */
case class MessageInfo(
  message: String,
  link: String,
  users: String,
  description: String
)

case class MessageOutput(
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

case class MessagesPass(input: PassInput, outputs: PassesOutput, options: HugoCommand.Options)
    extends CollectingPass[MessageInfo](input, outputs)
    with PassUtilities {

  requires(SymbolsPass)
  requires(ResolutionPass)

  private val usages = outputs.usage

  def name: String = MessagesPass.name

  protected def collect(definition: RiddlValue, parents: mutable.Stack[Definition]): Seq[MessageInfo] = {
    definition match {
      case t: Type =>
        val result = t.typ match {
          case _: AggregateUseCaseTypeExpression =>
            val pars = makeStringParents(parents.toSeq)
            val link = makeDocLink(t, pars)
            val users = usages.getUsers(t)
            val userLinks = users
              .map { definition =>
                s"[${definition.id.value}](${makeDocLink(definition, pars)})"
              }
              .mkString(", ")
            val description: String = t.brief.map(_.s).getOrElse("No description provided.")
            val mi = MessageInfo(t.identify, link, userLinks, description)
            Seq(mi)
          case _ =>
            Seq.empty[MessageInfo]
        }
        result
      case _ =>
        Seq.empty[MessageInfo]
    }
  }

  def postProcess(root: Root): Unit = ()

  override def result: MessageOutput = {
    val sortedList = collectedValues.sortBy(_.message).toSeq 
    MessageOutput(messages.toMessages, sortedList)
  }
}

object MessagesPass extends PassInfo {
  val name: String = "Messages"
  val creator: PassCreator = { (in: PassInput, out: PassesOutput) => MessagesPass(in, out, HugoCommand.Options())}
}
