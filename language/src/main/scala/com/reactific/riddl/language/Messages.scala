package com.reactific.riddl.language

import com.reactific.riddl.language.ast.Location
import com.reactific.riddl.utils.Logger

object Messages {

  sealed trait KindOfMessage {
    def isSevereError: Boolean = false

    def isError: Boolean = false

    def isWarning: Boolean = false

    def isMissing: Boolean = false

    def isStyle: Boolean = false
  }

  final case object MissingWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isMissing: Boolean = true

    override def toString: String = "Missing"
  }

  final case object StyleWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isStyle: Boolean = true

    override def toString: String = "Style"
  }

  final case object Warning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def toString: String = "Warning"
  }

  final case object Error extends KindOfMessage {
    override def isError: Boolean = true

    override def toString: String = "Error"
  }

  final case object SevereError extends KindOfMessage {
    override def isError: Boolean = true

    override def isSevereError: Boolean = true

    override def toString: String = "Severe"
  }

  case class Message(
    loc: Location,
    message: String,
    kind: KindOfMessage = Error,
    context: String = ""
  ) extends Ordered[Message] {
    override def compare(that: Message): Int = this.loc.compare(that.loc)
    def format: String = {
      val nl = System.lineSeparator()
      val ctxt = if (context.nonEmpty) { s"${nl}Context: $context" } else ""
      val errorLine = loc.source.annotateErrorLine(loc).dropRight(1)
      if (loc.isEmpty || loc.source.isEmpty || errorLine.isEmpty) {
        s"$kind: $loc: $message$ctxt"
      } else {
        s"$kind: $loc: $message:$nl$errorLine$ctxt"
      }
    }
  }

  def error(
    message: String,
    loc: Location = Location.empty,
  ): Message = {
    Message(loc, message)
  }

  def warning(message: String, loc: Location = Location.empty): Message = {
    Message(loc, message, Warning)
  }

  def severe(message: String, loc: Location = Location.empty): Message = {
    Message(loc, message, SevereError)
  }

  def errors(message: String, loc: Location = Location.empty): Messages = {
    List(Message(loc,message))
  }

  def warnings(message: String, loc: Location = Location.empty): Messages = {
    List(Message(loc, message, Warning))
  }

  def severes(message: String, loc: Location = Location.empty): Messages = {
    List(Message(loc, message, Messages.SevereError))
  }

  type Messages = List[Message]

  implicit class MessagesAuxiliary(msgs: Messages) {
    def format: String = {
      msgs.map(_.format).mkString(System.lineSeparator())
    }
  }

  val empty: Messages = List.empty[Message]

  def logMessages(
    messages: Messages,
    commonOptions: CommonOptions,
    log: Logger
  ): Unit = {
    if (messages.nonEmpty) {
      val (warns, errs) = messages.partition(_.kind.isWarning)
      val (severe, errors) = errs.partition(_.kind.isSevereError)
      val missing = warns.filter(_.kind.isMissing)
      val style = warns.filter(_.kind.isStyle)
      val warnings = warns.filterNot(x => x.kind.isMissing | x.kind.isStyle)
      log.info(s"""Validation Warnings: ${warns.length}""")
      if (commonOptions.showWarnings) {
        warnings.map(_.format).foreach(log.warn(_))
      }
      if (commonOptions.showMissingWarnings) {
        missing.map(_.format).foreach(log.warn(_))
      }
      if (commonOptions.showStyleWarnings) {
        style.map(_.format).foreach(log.warn(_))
      }
      log.info(s"""Validation Errors: ${errors.length}""")
      errors.map(_.format).foreach(log.error(_))
      log.info(s"""Severe Errors: ${severe.length}""")
      severe.map(_.format).foreach(log.severe(_))
    }
  }

}
