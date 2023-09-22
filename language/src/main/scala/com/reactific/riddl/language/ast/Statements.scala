/*
 * Copyright 2023 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.ast

trait Statements {
  this: Definitions with AbstractDefinitions =>

  trait Statement extends RiddlValue {
    def kind: String = "Statement"
  }

  /** A statement whose behavior is specified as a text string allowing an arbitrary action to be specified handled by
    * RIDDL's syntax.
    *
    * @param loc
    *   The location where the action occurs in the source
    * @param what
    *   The action to take (emitted as pseudo-code)
    */
  case class ArbitraryStatement(
    loc: At,
    what: LiteralString
  ) extends Statement {
    override def kind: String = "Arbitrary Statement"
    def format: String = what.format
  }

  /** An action that is intended to generate a runtime error in the generated application or otherwise indicate an error
    * condition
    *
    * @param loc
    *   The location where the action occurs in the source
    * @param message
    *   The error message to report
    */
  case class ErrorStatement(
    loc: At,
    message: LiteralString
  ) extends Statement {
    override def kind: String = "Error Statement"
    def format: String = s"error ${message.format}"
  }

  case class SetStatement(
    loc: At,
    field: FieldRef,
    value: LiteralString
  ) extends Statement {
    override def kind: String = "Set Statement"
    def format: String = s"set ${field.format} to ${value.format}"
  }

  /** An action that returns a value from a function
    *
    * @param loc
    *   The location in the source of the publish action
    * @param value
    *   The value to be returned
    */
  case class ReturnStatement(
    loc: At,
    value: LiteralString
  ) extends Statement {
    override def kind: String = "Return Statement"
    def format: String = s"return ${value.format}"
  }

  /** An action that sends a message to an [[Inlet]] or [[Outlet]].
    *
    * @param loc
    *   The location in the source of the send action
    * @param msg
    *   The constructed message to be sent
    * @param portlet
    *   The inlet or outlet to which the message is sent
    */
  case class SendStatement(
    loc: At,
    msg: MessageRef,
    portlet: PortletRef[Portlet]
  ) extends Statement {
    override def kind: String = "Send Statement"
    def format: String = s"send ${msg.format} to ${portlet.format}"
  }

  /** A statement that replys in a handler to a query
    *
    * @param loc
    *   The location in the source of the publish action
    * @param value
    *   The value to be returned
    */
  case class ReplyStatement(
    loc: At,
    message: MessageRef
  ) extends Statement {
    override def kind: String = "Reply Statement"
    def format: String = s"reply ${message.format}"
  }

  /** An statement that morphs the state of an entity to a new structure
    *
    * @param loc
    *   The location of the morph action in the source
    * @param entity
    *   The entity to be affected
    * @param state
    *   The reference to the new state structure
    */
  case class MorphStatement(
    loc: At,
    entity: EntityRef,
    state: StateRef,
    value: MessageRef
  ) extends Statement {
    override def kind: String = "Morph Statement"
    def format: String = s"morph ${entity.format} to ${state.format} with ${value.format}"
  }

  /** An action that changes the behavior of an entity by making it use a new handler for its messages; named for the
    * "become" operation in Akka that does the same for an user.
    *
    * @param loc
    *   The location in the source of the become action
    * @param entity
    *   The entity whose behavior is to change
    * @param handler
    *   The reference to the new handler for the entity
    */
  case class BecomeStatement(
    loc: At,
    entity: EntityRef,
    handler: HandlerRef
  ) extends Statement {
    override def kind: String = "Become Statement"
    def format: String = s"become ${entity.format} to ${handler.format}"
  }

  /** An action that tells a message to an entity. This is very analogous to the tell operator in Akka. Unlike using an
    * Portlet, this implies a direct relationship between the telling entity and the told entity. This action is
    * considered useful in "high cohesion" scenarios. Use [[SendStatement]] to reduce the coupling between entities
    * because the relationship is managed by a [[Context]] 's [[Connector]] instead.
    *
    * @param loc
    *   The location of the tell action
    * @param msg
    *   A constructed message value to send to the entity, probably a command
    * @param entityRef
    *   The entity to which the message is directed
    */
  case class TellStatement(
    loc: At,
    msg: MessageRef,
    entityRef: ProcessorRef[Processor[?, ?]]
  ) extends Statement {
    override def kind: String = "Tell Statement"
    def format: String = s"tell ${msg.format} to ${entityRef.format}"
  }

  case class CallStatement(
    loc: At,
    func: FunctionRef
  ) extends Statement {
    override def kind: String = "Call Statement"
    def format: String = "scall ${func.format}"
  }

  case class ForEachStatement(
    loc: At,
    ref: PathIdentifier,
    do_ : Seq[Statement]
  ) extends Statement {
    override def kind: String = "Foreach Statement"
    def format: String = s"foreach ${ref.format} do \n" +
      do_.map(_.format).mkString("\n") + "end\n"
  }

  case class IfThenElseStatement(
    loc: At,
    cond: LiteralString,
    thens: Seq[Statement],
    elses: Seq[Statement]
  ) extends Statement {
    override def kind: String = "IfThenElse Statement"
    def format: String = s"if ${cond.format} then\n{\n${thens.map(_.format).mkString("  ", "\n  ", "\n}") +
        (if elses.nonEmpty then " else {\n" + elses.map(_.format).mkString("  ", "\n  ", "\n}\n")
         else "\n")}"
  }

  case class StopStatement(
    loc: At
  ) extends Statement {
    override def kind: String = "Stop Statement"
    def format: String = "scall ${func.format}"

  }
}
