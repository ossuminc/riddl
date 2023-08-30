/*
 * Copyright 2023 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.ast

import scala.collection.Map

trait Statements {
  this: Definitions with Conditions with Values with AbstractDefinitions =>

  trait StatementBaseImpl extends Statement {
    def contents: Seq[Definition] = Seq.empty[Definition]
    def description: Option[Description] = None
    def brief: Option[LiteralString] = None
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
  ) extends StatementBaseImpl {
    override def format: String = what.format
    override def kind: String = "ArbitraryStatement"
  }

  /** An action that is intended to generate a runtime error in the generated application or otherwise indicate an error
    * condition
    *
    * @param loc
    *   The location where the action occurs in the source
    * @param message
    *   The error message to report
    */
  case class ErrorStatement(loc: At, message: LiteralString) extends StatementBaseImpl {
    override def format: String = s"error \"${message.format}\""
    override def kind: String = "ErrorStatement"
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
    value: Value
  ) extends StatementBaseImpl {
    override def format: String = s"return ${value.format}"
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
    msg: MessageValue,
    portlet: PortletRef[Portlet]
  ) extends StatementBaseImpl {
    override def format: String = s"send ${msg.format} to ${portlet.format}"
  }

  /** An action to call a function
    *
    * @param loc
    *   The location in the source at which the subscribe action occurs
    * @param function
    *   The function to call
    * @param arguments
    *   The arguments to provide to the function
    */
  case class FunctionCallStatement(
    loc: At,
    function: PathIdentifier,
    arguments: ArgumentValues
  ) extends StatementBaseImpl {
    override def format: String = s"call ${function.format}${arguments.format}"
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
    newValue: Value
  ) extends StatementBaseImpl {
    override def format: String = s"morph ${entity.format} to ${state.format}"
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
  ) extends StatementBaseImpl {
    override def format: String =
      s"become ${entity.format} to ${handler.format}"
  }

  /** An action that tells a message to an entity. This is very analogous to the tell operator in Akka. Unlike using an
    * Portlet, this implies a direct relationship between the telling entity and the told entity. This action is
    * considered useful in "high cohesion" scenarios. Use [[SendStatement]] to reduce the coupling between entities
    * because the relationship is managed by a [[Context]]'s [[Connector]] instead.
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
    msg: MessageValue,
    entityRef: ProcessorRef[Processor[?, ?]]
  ) extends StatementBaseImpl {
    override def format: String = s"tell ${msg.format} to ${entityRef.format}"
  }

  /** An action whose behavior is to set the value of a state field to some expression
    *
    * @param loc
    *   The location where the action occurs int he source
    * @param target
    *   The path identifier of the entity's state field that is to be set
    * @param value
    *   An expression for the value to set the field to
    */
  case class SetStatement(
    loc: At,
    target: PathIdentifier,
    value: Value
  ) extends StatementBaseImpl {
    override def format: String = {
      s"set ${target.format} to ${value.format}"
    }
  }

  case class IfStatement(
    loc: At,
    conditional: Condition,
    then_ : Seq[Statement],
    else_ : Seq[Statement] = Seq.empty[Statement]
  ) extends StatementBaseImpl {
    override def format: String = s"if ${conditional.format}\n" +
      then_.map(_.format).mkString("\n") + " else \n" +
      else_.map(_.format).mkString("\nend")
  }

  case class ForEachStatement(
    loc: At,
    ref: PathIdentifier,
    do_ : Seq[Statement]
  ) extends StatementBaseImpl {
    def format: String = s"foreach ${ref.format} do \n" +
      do_.map(_.format).mkString("\n") + "end\n"
  }

}
