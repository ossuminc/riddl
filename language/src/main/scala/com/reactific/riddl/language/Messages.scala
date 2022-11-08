/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.ast.Location
import com.reactific.riddl.utils.Logger

object Messages {

  sealed trait KindOfMessage extends Ordered[KindOfMessage] {
    def isSevereError: Boolean = false

    def isError: Boolean = false

    def isWarning: Boolean = false

    def isMissing: Boolean = false

    def isStyle: Boolean = false

    def isInfo: Boolean = false

    def severity: Int

    def isIgnorable: Boolean = severity <= Warning.severity
    def isActionable: Boolean = severity >= Error.severity

    def compare(that: KindOfMessage): Int = { this.severity - that.severity }
  }

  final case object Info extends KindOfMessage {
    override def isInfo: Boolean = true
    override def toString: String = "Info"
    def severity = 0
  }

  final case object StyleWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isStyle: Boolean = true

    override def toString: String = "Style"
    def severity = 1
  }

  final case object MissingWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isMissing: Boolean = true

    override def toString: String = "Missing"

    def severity = 2
  }

  final case object Warning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def toString: String = "Warning"
    def severity = 3
  }

  final case object Error extends KindOfMessage {
    override def isError: Boolean = true

    override def toString: String = "Error"
    def severity = 4
  }

  final case object SevereError extends KindOfMessage {
    override def isError: Boolean = true

    override def isSevereError: Boolean = true

    override def toString: String = "Severe"
    def severity = 5
  }

  case class Message(
    loc: Location,
    message: String,
    kind: KindOfMessage = Error,
    context: String = "")
      extends Ordered[Message] {

    def isInfo: Boolean = kind.isInfo
    def isMissing: Boolean = kind.isMissing
    def isWarning: Boolean = kind.isWarning
    def isStyle: Boolean = kind.isStyle
    def isError: Boolean = kind.isError
    def isSevere: Boolean = kind.isSevereError

    override def compare(that: Message): Int = {
      val comparison = this.loc.compare(that.loc)
      if (comparison == 0) { this.kind.compare(that.kind) }
      else comparison
    }

    def format: String = {
      val nl = System.lineSeparator()
      val ctxt =
        if (context.nonEmpty) { s"${nl}Context: $context" }
        else ""
      val errorLine = loc.source.annotateErrorLine(loc).dropRight(1)
      if (loc.isEmpty || loc.source.isEmpty || errorLine.isEmpty) {
        s"$kind: $loc: $message$ctxt"
      } else { s"$kind: $loc: $message:$nl$errorLine$ctxt" }
    }
  }

  def error(
    message: String,
    loc: Location = Location.empty
  ): Message = { Message(loc, message) }

  def warning(message: String, loc: Location = Location.empty): Message = {
    Message(loc, message, Warning)
  }

  def severe(message: String, loc: Location = Location.empty): Message = {
    Message(loc, message, SevereError)
  }

  def errors(message: String, loc: Location = Location.empty): Messages = {
    List(Message(loc, message))
  }

  def warnings(message: String, loc: Location = Location.empty): Messages = {
    List(Message(loc, message, Warning))
  }

  def severes(message: String, loc: Location = Location.empty): Messages = {
    List(Message(loc, message, Messages.SevereError))
  }

  type Messages = List[Message]

  implicit class MessagesAuxiliary(msgs: Messages) {
    def format: String = { msgs.map(_.format).mkString(System.lineSeparator()) }
    def isOnlyWarnings: Boolean = {
      msgs.isEmpty || !msgs.exists(_.kind > Warning)
    }
    def isIgnorable: Boolean = {
      msgs.isEmpty || !msgs.exists(_.kind >= Warning)
    }
    def hasErrors: Boolean = { msgs.nonEmpty && msgs.exists(_.kind >= Error) }
    def justMissing: Messages = msgs.filter(_.isMissing)
    def justWarnings: Messages = msgs.filter(x => x.isWarning && !x.isMissing)
    def justErrors: Messages = msgs.filter(_.isError)
  }

  val empty: Messages = List.empty[Message]

  def highestSeverity(messages: Messages): Int = {
    messages.foldLeft(0) { case (n, m) => Math.max(m.kind.severity, n) }
  }

  def logMessages(
    messages: Messages,
    log: Logger,
    options: CommonOptions
  ): Int = {
    val list = if (options.sortMessagesByLocation) messages.sorted else messages
    list.foreach { msg =>
      msg.kind match {
        case Info           => log.info(msg.format)
        case StyleWarning   => log.warn(msg.format)
        case MissingWarning => log.warn(msg.format)
        case Warning        => log.warn(msg.format)
        case Error          => log.error(msg.format)
        case SevereError    => log.severe(msg.format)
      }
    }
    highestSeverity(messages)
  }
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
      log.info(s"""Warnings: ${warns.length}""")
      if (commonOptions.showWarnings) {
        warnings.map(_.format).foreach(log.warn(_))
      }
      if (commonOptions.showMissingWarnings) {
        missing.map(_.format).foreach(log.warn(_))
      }
      if (commonOptions.showStyleWarnings) {
        style.map(_.format).foreach(log.warn(_))
      }
      log.info(s"""Errors: ${errors.length}""")
      errors.map(_.format).foreach(log.error(_))
      log.info(s"""Severe Errors: ${severe.length}""")
      severe.map(_.format).foreach(log.severe(_))
    }
  }

}
