package com.reactific.riddl.hugo
import com.reactific.riddl.language.{AST, Messages}
import com.reactific.riddl.language.AST.{
  AggregateUseCase,
  AggregateUseCaseTypeExpression,
  Definition,
  Description,
  Type
}
import com.reactific.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.reactific.riddl.passes.symbols.{Symbols, SymbolsPass}
import com.reactific.riddl.passes.{CollectingPass, CollectingPassOutput, PassInfo, PassInput, PassOutput, PassesOutput}

import scala.collection.mutable

/** Information about message types collected by the MessagesPass
  * @param message
  *   The type of the message - shows the fields of the message, with their brief descriptions if any
  * @param definedIn
  *   shows the subdomain, context, entity, or other definition in which the message is defined, along with that
  *   definition's brief description
  * @param users
  *   shows the places from which this message is used with full path identification, and each path component is a link
  *   to the documentation for that definition.
  * @param description
  *   Full description of the message as defined in its "described by" type which could be fairly involved with
  *   diagrams, etc.
  */
case class MessageInfo(
  kind: AggregateUseCase,
  message: Type,
  definedIn: Seq[Definition],
  users: Seq[Definition],
  description: Option[Description]
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

class MessagesPass(input: PassInput, outputs: PassesOutput) extends CollectingPass[MessageInfo](input, outputs) {

  requires(SymbolsPass)
  requires(ResolutionPass)

  private val usages = outputs.usage

  def name: String = MessagesPass.name

  protected def collect(definition: Definition, parents: mutable.Stack[AST.Definition]): Option[MessageInfo] = {
    definition match {
      case t: Type =>
        t.typ match {
          case aucte: AggregateUseCaseTypeExpression =>
            val users = usages.getUsers(t)
            Some(MessageInfo(aucte.usecase, t, parents.toSeq, users, t.description))
          case _ => None
        }
      case _ =>
        None
    }
  }

  def postProcess(root: com.reactific.riddl.language.AST.RootContainer): Unit = ()

  override def result: MessageOutput = MessageOutput(messages.toMessages, collectedValues)

}

object MessagesPass extends PassInfo {
  val name: String = "Messages"
}