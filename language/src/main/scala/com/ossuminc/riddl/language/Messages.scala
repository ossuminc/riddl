/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.Logger
import org.apache.commons.lang3.exception.ExceptionUtils

import scala.collection.mutable
import scala.io.AnsiColor.*

object Messages {

  sealed trait KindOfMessage extends Ordered[KindOfMessage] {
    def isSevereError: Boolean = false

    def isError: Boolean = false

    def isWarning: Boolean = false

    def isMissing: Boolean = false

    def isStyle: Boolean = false

    def isUsage: Boolean = false

    def isInfo: Boolean = false

    def severity: Int

    def isIgnorable: Boolean = severity < Warning.severity
    def isActionable: Boolean = severity >= Warning.severity

    def compare(that: KindOfMessage): Int = { this.severity - that.severity }
  }

  case object Info extends KindOfMessage {
    override def isInfo: Boolean = true
    override def toString: String = "Info"
    def severity = 0
  }

  case object StyleWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isStyle: Boolean = true

    override def toString: String = "Style"
    def severity = 1
  }

  case object MissingWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isMissing: Boolean = true

    override def toString: String = "Missing"

    def severity = 2
  }

  case object UsageWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isUsage: Boolean = true

    override def isMissing: Boolean = false

    override def toString: String = "Usage"

    def severity = 3

  }

  case object Warning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def toString: String = "Warning"
    def severity = 4
  }

  case object Error extends KindOfMessage {
    override def isError: Boolean = true

    override def toString: String = "Error"
    def severity = 5
  }

  case object SevereError extends KindOfMessage {
    override def isError: Boolean = true

    override def isSevereError: Boolean = true

    override def toString: String = "Severe"
    def severity = 6
  }

  val nl: String = System.lineSeparator()

  case class Message(loc: At, message: String, kind: KindOfMessage = Error, context: String = "")
      extends Ordered[Message] {

    def isInfo: Boolean = kind.isInfo
    def isMissing: Boolean = kind.isMissing
    def isWarning: Boolean = kind.isWarning
    def isStyle: Boolean = kind.isStyle
    def isUsage: Boolean = kind.isUsage
    def isError: Boolean = kind.isError
    def isSevere: Boolean = kind.isSevereError

    override def compare(that: Message): Int = {
      val comparison = this.loc.compare(that.loc)
      if comparison == 0 then { this.kind.compare(that.kind) }
      else comparison
    }

    def format: String = {
      val ctxt = if context.nonEmpty then {
        s"${nl}Context: $context"
      } else ""
      val headLine = {
        val source = loc.source.from
        if source.isEmpty then {
          ""
        } else {
          s"$source${loc.toShort}"
        }
      }
      val errorLine = loc.source.annotateErrorLine(loc).dropRight(1)
      if loc.isEmpty || headLine.isEmpty || errorLine.isEmpty then {
        s"$headLine$message$ctxt"
      } else {
        s"$headLine:$nl$message:$nl$errorLine$ctxt"
      }
    }
  }

  def style(message: String, loc: At = At.empty): Message = {
    Message(loc, message, StyleWarning)
  }

  def missing(message: String, loc: At = At.empty): Message = {
    Message(loc, message, MissingWarning)
  }

  def usage(message: String, loc: At = At.empty): Message = {
    Message(loc, message, UsageWarning)
  }

  def info(message: String, loc: At = At.empty): Message = {
    Message(loc, message, Info)
  }

  def warning(message: String, loc: At = At.empty): Message = {
    Message(loc, message, Warning)
  }

  def error(
    message: String,
    loc: At = At.empty
  ): Message = { Message(loc, message) }

  private def exceptionToError(exception: Throwable, loc: At = At.empty, context: String = ""): Message = {
    val message = ExceptionUtils.getRootCauseStackTrace(exception).mkString("\n", "\n  ", "\n")
    Message(loc, s"While $context: $message")
  }

  def severe(message: String, loc: At = At.empty): Message = {
    Message(loc, message, SevereError)
  }

  def errors(message: String, loc: At = At.empty): Messages = {
    List(Message(loc, message))
  }

  def warnings(message: String, loc: At = At.empty): Messages = {
    List(Message(loc, message, Warning))
  }

  def severes(message: String, loc: At = At.empty): Messages = {
    List(Message(loc, message, Messages.SevereError))
  }

  type Messages = List[Message]

  implicit class MessagesAuxiliary(msgs: Messages) {
    def format: String = { msgs.map(_.format).mkString(System.lineSeparator()) }
    def isOnlyWarnings: Boolean = {
      msgs.isEmpty || !msgs.exists(_.kind > Warning)
    }
    def isOnlyIgnorable: Boolean = {
      msgs.isEmpty || !msgs.exists(_.kind >= Warning)
    }
    def hasErrors: Boolean = { msgs.nonEmpty && msgs.exists(_.kind >= Error) }
    def hasWarnings: Boolean = { msgs.nonEmpty && msgs.exists(_.kind < Error) }
    def justInfo: Messages = msgs.filter(_.isInfo)
    def justMissing: Messages = msgs.filter(_.isMissing)
    def justStyle: Messages = msgs.filter(_.isStyle)
    def justUsage: Messages = msgs.filter(_.isUsage)
    def justWarnings: Messages = msgs.filter(m => m.kind < Error && m.kind > Info)
    def justErrors: Messages = msgs.filter(_.kind >= Error)
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
    val list = if options.sortMessagesByLocation then messages.sorted else messages
    if options.groupMessagesByKind then { logMessagesByGroup(list, options, log) }
    else { logMessagesRetainingOrder(list, log) }
    highestSeverity(list)
  }

  private def logMessage(message: Message, log: Logger): Unit = {
    message.kind match {
      case Info           => log.info(message.format)
      case StyleWarning   => log.style(message.format)
      case MissingWarning => log.missing(message.format)
      case UsageWarning   => log.usage(message.format)
      case Warning        => log.warn(message.format)
      case Error          => log.error(message.format)
      case SevereError    => log.severe(message.format)
    }
  }

  private def logMessagesRetainingOrder(list: Messages, log: Logger): Unit = {
    list.foreach { msg => logMessage(msg, log) }
  }

  private def logMessagesByGroup(
    messages: Messages,
    commonOptions: CommonOptions,
    log: Logger
  ): Unit = {
    def logMsgs(kind: KindOfMessage, maybeMessages: Option[Seq[Message]]): Unit = {
      val messages = maybeMessages.getOrElse(Seq.empty[Message])
      if messages.nonEmpty then {
        kind match {
          case UsageWarning =>
            log.usage(s"""$kind Message Count: ${messages.length}""")
          case StyleWarning =>
            log.style(s"""$kind Message Count: ${messages.length}""")
          case MissingWarning =>
            log.missing(s"""$kind Message Count: ${messages.length}""")
          case Warning => // everything else is a warning
            log.warn(s"""$kind Message Count: ${messages.length}""")
          case Error =>
            log.error(s"""$kind Message Count: ${messages.length}""")
          case SevereError =>
            log.severe(s"""$kind Message Count: ${messages.length}""")
          case Info =>
            log.info(s"""$kind Message Count: ${messages.length}""")
        }
        messages.foreach { (msg: Message) => logMessage(msg, log) }
      }
    }
    if messages.nonEmpty then {
      val groups = messages.groupBy(_.kind)
      logMsgs(SevereError, groups.get(SevereError))
      logMsgs(Error, groups.get(Error))

      if commonOptions.showWarnings then {
        if commonOptions.showUsageWarnings then {
          logMsgs(UsageWarning, groups.get(UsageWarning))
        }
        if commonOptions.showMissingWarnings then {
          logMsgs(MissingWarning, groups.get(MissingWarning))
        }
        if commonOptions.showStyleWarnings then {
          logMsgs(StyleWarning, groups.get(StyleWarning))
        }
      }
      logMsgs(Info, groups.get(Info))
    }
  }

  case class Accumulator(commonOptions: CommonOptions) {
    private val msgs: mutable.ListBuffer[Message] = mutable.ListBuffer.empty

    def size: Int = msgs.length

    @inline def isEmpty: Boolean = msgs.isEmpty
    @inline def nonEmpty: Boolean = !isEmpty

    @inline def toMessages: Messages = msgs.toList

    def add(msg: Message): this.type = {
      msg.kind match {
        case Warning =>
          if commonOptions.showWarnings then msgs.append(msg)
        case StyleWarning =>
          if commonOptions.showStyleWarnings && commonOptions.showWarnings then msgs.append(msg)
        case MissingWarning =>
          if commonOptions.showMissingWarnings && commonOptions.showWarnings then msgs.append(msg)
        case UsageWarning =>
          if commonOptions.showUsageWarnings && commonOptions.showWarnings then msgs.append(msg)
        case Info =>
          if commonOptions.showInfoMessages then msgs.append(msg)
        case Error | SevereError => msgs.append(msg)
      }
      this
    }

    @inline def style(message: String, loc: At = At.empty): this.type = {
      add(Message(loc, message, StyleWarning))
    }

    @inline def info(message: String, loc: At = At.empty): this.type = {
      add(Message(loc, message, Info))
    }

    @inline def warning(message: String, loc: At = At.empty): this.type = {
      add(Message(loc, message, Warning))
    }

    @inline def error(message: String, loc: At = At.empty): this.type = {
      add(Message(loc, message, Error))
    }

    @inline def severe(message: String, loc: At = At.empty): this.type = {
      add(Message(loc, message, SevereError))
    }

    @inline def addStyle(loc: At, msg: String): this.type = {
      add(Message(loc, msg, StyleWarning))
    }

    @inline def addUsage(loc: At, msg: String): this.type = {
      add(Message(loc, msg, UsageWarning))
    }

    @inline def addMissing(loc: At, msg: String): this.type = {
      add(Message(loc, msg, MissingWarning))
    }

    @inline def addWarning(loc: At, msg: String): this.type = {
      add(Message(loc, msg, Warning))
    }

    @inline def addError(loc: At, msg: String): this.type = {
      add(Message(loc, msg, Error))
    }

    @inline def addSevere(loc: At, msg: String): this.type = {
      add(Message(loc, msg, SevereError))
    }
  }

  object Accumulator {
    val empty: Accumulator = new Accumulator(CommonOptions())
    def apply(commonOptions: CommonOptions): Accumulator = new Accumulator(commonOptions)
  }
}
