package com.reactific.riddl.language.ast

/** Unit Tests For Actions */
trait Actions extends Definitions {

  // /////////////////////////////////////////////////////////// Actions

  /** An action whose behavior is specified as a text string allowing extension
   * to arbitrary actions not otherwise handled by RIDDL's syntax.
   *
   * @param loc
   * The location where the action occurs in the source
   * @param what
   * The action to take (emitted as pseudo-code)
   */
  case class ArbitraryAction(
                              loc: At,
                              what: LiteralString
                            ) extends Action {
    override def format: String = what.format
  }

  /** An action that is intended to generate a runtime error in the generated
   * application or otherwise indicate an error condition
   *
   * @param loc
   * The location where the action occurs in the source
   * @param message
   * The error message to report
   */
  case class ErrorAction(
                          loc: At,
                          message: LiteralString)
    extends Action {
    override def format: String = s"severe \"${message.format}\""
  }

  /** An action whose behavior is to set the value of a state field to some
   * expression
   *
   * @param loc
   * The location where the action occurs int he source
   * @param target
   * The path identifier of the entity's state field that is to be set
   * @param value
   * An expression for the value to set the field to
   */
  case class SetAction(
                        loc: At,
                        target: PathIdentifier,
                        value: Expression)
    extends Action {
    override def format: String = {
      s"set ${target.format} to ${value.format}"
    }
  }

  /** An action that appends a value to a list of values
   *
   * @param loc
   * The location where the action occurs int he source
   * @param target
   * The path identifier of the entity's state field that is to be set
   * @param value
   * An expression for the value to set the field to
   */
  case class AppendAction(
                           loc: At,
                           value: Expression,
                           target: PathIdentifier)
    extends Action {
    override def format: String = {
      s"append ${value.format} to ${target.format}"
    }
  }

  /** A helper class for publishing messages that represents the construction of
   * the message to be sent.
   *
   * @param msg
   * A message reference that specifies the specific type of message to
   * construct
   * @param args
   * An argument list that should correspond to teh fields of the message
   */
  case class MessageConstructor(
                                 loc: At,
                                 msg: MessageRef,
                                 args: ArgList = ArgList())
    extends RiddlNode {
    override def format: String = msg.format + {
      if (args.nonEmpty) {
        args.format
      }
      else {
        "()"
      }
    }
  }

  /** An action that returns a value from a function
   *
   * @param loc
   * The location in the source of the publish action
   * @param value
   * The value to be returned
   */
  case class ReturnAction(
                           loc: At,
                           value: Expression)
    extends Action {
    override def format: String = s"return ${value.format}"
  }

  /** An action that places a message on an entity's event channel
   *
   * @param loc
   * The location in the source of the publish action
   * @param msg
   * The constructed message to be yielded
   */
  case class YieldAction(
                          loc: At,
                          msg: MessageConstructor)
    extends Action {
    override def format: String = s"yield ${msg.format}"
  }

  /** An action that publishes a message to a pipe
   *
   * @param loc
   * The location in the source of the publish action
   * @param msg
   * The constructed message to be published
   * @param pipe
   * The pipe onto which the message is published
   */
  case class PublishAction(
                            loc: At,
                            msg: MessageConstructor,
                            pipe: PipeRef)
    extends Action {
    override def format: String = s"publish ${msg.format} to ${pipe.format}"
  }

  /** An action that subscribes to messages from a pipe
   *
   * @param loc
   * The location in the source at which the subscribe action occurs
   * @param type_
   * The type of message to be received from the pipe
   * @param pipe
   * The pipe from which the messages are received
   */
  case class SubscribeAction(
                              loc: At,
                              pipe: PipeRef,
                              type_ : TypeRef)
    extends Action {
    def format: String = s"subscribe to ${pipe.format}"
  }

  /** An action to call a function
   *
   * @param loc
   * The location in the source at which the subscribe action occurs
   * @param function
   * The function to call
   * @param arguments
   * The arguments to provide to the function
   */
  case class FunctionCallAction(
                                 loc: At,
                                 function: PathIdentifier,
                                 arguments: ArgList)
    extends Action {
    override def format: String = s"call ${function.format}${arguments.format}"
  }

  /** An action that morphs the state of an entity to a new structure
   *
   * @param loc
   * The location of the morph action in the source
   * @param entity
   * The entity to be affected
   * @param state
   * The reference to the new state structure
   */
  case class MorphAction(
                          loc: At,
                          entity: EntityRef,
                          state: StateRef)
    extends Action {
    override def format: String = s"morph ${entity.format} to ${state.format}"
  }

  /** An action that changes the behavior of an entity by making it use a new
   * handler for its messages; named for the "become" operation in Akka that
   * does the same for an actor.
   *
   * @param loc
   * The location in the source of the become action
   * @param entity
   * The entity whose behavior is to change
   * @param handler
   * The reference to the new handler for the entity
   */
  case class BecomeAction(
                           loc: At,
                           entity: EntityRef,
                           handler: HandlerRef)
    extends Action {
    override def format: String =
      s"become ${entity.format} to ${handler.format}"
  }

  /** An action that tells a message to an entity. This is very analogous to the
   * tell operator in Akka.
   *
   * @param loc
   * The location of the tell action
   * @param entity
   * The entity to which the message is directed
   * @param msg
   * A constructed message value to send to the entity, probably a command
   */
  case class TellAction(
                         loc: At,
                         msg: MessageConstructor,
                         entity: MessageTakingRef[Definition])
    extends Action {
    override def format: String = s"tell ${msg.format} to ${entity.format}"
  }

  /** An action that asks a query to an entity. This is very analogous to the
   * ask operator in Akka.
   *
   * @param loc
   * The location of the ask action
   * @param entity
   * The entity to which the message is directed
   * @param msg
   * A constructed message value to send to the entity, probably a query
   */
  case class AskAction(
                        loc: At,
                        entity: EntityRef,
                        msg: MessageConstructor)
    extends Action {
    override def format: String = s"ask ${entity.format} to ${msg.format}"
  }

  /** An action that provides a reply in response to an [[AskAction]].
   *
   * @param loc
   * The location of the tell action
   * @param msg
   * A constructed message value to send to the entity, probably a command
   */
  case class ReplyAction(
                          loc: At,
                          msg: MessageConstructor)
    extends Action {
    override def format: String = s"reply with ${msg.format}"
  }

  /** An action that is a set of other actions.
   *
   * @param loc
   * The location of the compound action
   * @param actions
   * The actions in the compound group of actions
   */
  case class CompoundAction(
                             loc: At,
                             actions: Seq[Action])
    extends Action {
    override def format: String = actions.mkString("{", ",", "}")
  }

}
