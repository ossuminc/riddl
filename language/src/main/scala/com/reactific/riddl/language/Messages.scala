/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.parsing.{FileParserInput, SourceParserInput, StringParserInput, URLParserInput}
import com.reactific.riddl.utils.Logger

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

    def highlight(s: String): String = {
      s"${kind match {
          case Info           => s"${BLUE}"
          case StyleWarning   => s"${YELLOW}"
          case MissingWarning => s"${YELLOW}${UNDERLINED}"
          case UsageWarning   => s"${YELLOW}${BOLD}"
          case Warning        => s"${YELLOW}${BOLD}${UNDERLINED}"
          case Error          => s"${RED}${BOLD}"
          case SevereError    => s"${RED_B}${BLACK}${BOLD}"
        }}$s${RESET}"
    }

    def format: String = {
      val ctxt =
        if context.nonEmpty then { s"${nl}Context: $context" }
        else ""
      val source = loc.source match {
        case FileParserInput(file) =>
          val path = file.getAbsolutePath
          val index = path.lastIndexOf("riddl/")
          path.substring(index + 6)
        case SourceParserInput(source, _) => source.descr
        case StringParserInput(_, origin) => origin
        case URLParserInput(url)          => url.toString
        case _                            => "unknown source"
      }
      val sourceLine = loc.toShort
      val headLine = s"${highlight(s"$kind: $source$sourceLine:")}$nl"
      val errorLine = loc.source.annotateErrorLine(loc).dropRight(1)
      if loc.isEmpty || source.isEmpty || errorLine.isEmpty then {
        s"$headLine$message$ctxt"
      } else { s"$headLine$message:$nl$errorLine$ctxt" }
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

  def logMessagesRetainingOrder(list: Messages, log: Logger): Unit = {
    list.foreach { msg =>
      msg.kind match {
        case Info           => log.info(msg.format)
        case StyleWarning   => log.warn(msg.format)
        case MissingWarning => log.warn(msg.format)
        case UsageWarning   => log.warn(msg.format)
        case Warning        => log.warn(msg.format)
        case Error          => log.error(msg.format)
        case SevereError    => log.severe(msg.format)
      }
    }
  }

  def logMessagesByGroup(
    messages: Messages,
    commonOptions: CommonOptions,
    log: Logger
  ): Unit = {
    def logMsgs(kind: KindOfMessage, maybeMessages: Option[Seq[Message]]): Unit = {
      if maybeMessages.nonEmpty then {
        val messages = maybeMessages.get
        if messages.nonEmpty then {
          log.info(s"""$kind Message Count: ${messages.length}""")

          messages.map(_.format).foreach { message =>
            kind match {
              case Info        => log.info(message)
              case SevereError => log.severe(message)
              case Error       => log.error(message)
              case _           => log.warn(message)
            }
          }
        }
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
        case StyleWarning =>
          if commonOptions.showStyleWarnings then {
            msgs.append(msg)
          }
        case MissingWarning =>
          if commonOptions.showMissingWarnings then {
            msgs.append(msg)
          }
        case UsageWarning =>
          if commonOptions.showUsageWarnings then {
            msgs.append(msg)
          }
        case _ => msgs.append(msg)
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
