/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{CommonOptions, ExceptionUtils, Logger, PlatformContext}

import scala.collection.mutable
import scala.scalajs.js.annotation.*

/** This module handles everything needed to deal with the message output of the `riddlc` compiler */
@JSExportTopLevel("Messages")
object Messages {

  /** A sealed base trait for the kinds of messages that can be created each with their own existence test */
  sealed trait KindOfMessage extends Ordered[KindOfMessage] {
    def isSevereError: Boolean = false

    def isError: Boolean = false

    def isWarning: Boolean = false

    def isMissing: Boolean = false

    def isStyle: Boolean = false

    def isUsage: Boolean = false

    def isCompleteness: Boolean = false

    def isTip: Boolean = false

    def isInfo: Boolean = false

    def severity: Int

    def isIgnorable: Boolean = severity < CompletenessWarning.severity
    def isActionable: Boolean = severity >= CompletenessWarning.severity

    def compare(that: KindOfMessage): Int = { this.severity - that.severity }
  }

  /** A case object for the Tip kind of message. Retained for backward
    * compatibility; remediation guidance is now carried as the `suggestion`
    * field on every [[Message]] (gated by `CommonOptions.provideTips`) rather
    * than as a separate message kind. No pass currently produces Tip messages.
    */
  case object Tip extends KindOfMessage {
    override def isTip: Boolean = true
    override def toString: String = "Tip"
    def severity = 0
  }

  /** A case object for the Info kind of message */
  case object Info extends KindOfMessage {
    override def isInfo: Boolean = true
    override def toString: String = "Info"
    def severity = 0
  }

  /** A case object for the Style kind of warning message */
  case object StyleWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isStyle: Boolean = true

    override def toString: String = "Style"
    def severity = 1
  }

  /** A case object for the Missing kind of warning message */
  case object MissingWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isMissing: Boolean = true

    override def toString: String = "Missing"

    def severity = 2
  }

  /** A case object for the Usage kind of warning message */
  case object UsageWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isUsage: Boolean = true

    override def isMissing: Boolean = false

    override def toString: String = "Usage"

    def severity = 3

  }

  /** A case object for the Completeness kind of warning message.
    * These warnings indicate that a model is incomplete for
    * simulation, code generation, or analysis purposes.
    */
  case object CompletenessWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isCompleteness: Boolean = true

    override def toString: String = "Completeness"
    def severity = 4
  }

  /** A case object for the generic kind of warning message */
  case object Warning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def toString: String = "Warning"
    def severity = 5
  }

  /** A case object for Error messages */
  case object Error extends KindOfMessage {
    override def isError: Boolean = true

    override def toString: String = "Error"
    def severity = 6
  }

  /** A case object for Severe Error messages */
  case object SevereError extends KindOfMessage {
    override def isError: Boolean = true

    override def isSevereError: Boolean = true

    override def toString: String = "Severe"
    def severity = 7
  }

  /** The system's notion of a newline for sensible error message termination.
    * Uses "\n" directly since System.lineSeparator() returns null in Scala.js.
    */
  @JSExport val nl: String = "\n"

  /** A Message instance. There are helper functions below to help you create these.
    *
    * @param loc
    *   The location in the model that generated the message
    * @param message
    *   The message text itself (there may be multiple lines, typically indent them 2 spaces, no tabs)
    * @param kind
    *   The kind of message as one of the case objects of [[KindOfMessage]]
    * @param context
    *   Additional referent that indicates the conditions that produced the message
    * @param suggestion
    *   An optional remediation suggestion (tip) for resolving the condition that produced this message. It is only
    *   retained and rendered when [[com.ossuminc.riddl.utils.CommonOptions.provideTips]] is enabled; otherwise the
    *   [[Accumulator]] strips it. Any pass may attach one. Placeholders in the text should mirror those in `message`.
    */
  @JSExportTopLevel("Message")
  case class Message(loc: At, message: String, kind: KindOfMessage = Error, context: String = "", suggestion: String = "")
      extends Ordered[Message] {
    def isInfo: Boolean = kind.isInfo
    def isTip: Boolean = kind.isTip
    def isMissing: Boolean = kind.isMissing
    def isWarning: Boolean = kind.isWarning
    def isStyle: Boolean = kind.isStyle
    def isUsage: Boolean = kind.isUsage
    def isCompleteness: Boolean = kind.isCompleteness
    def isError: Boolean = kind.isError
    def isSevere: Boolean = kind.isSevereError

    override def compare(that: Message): Int = {
      val comparison = this.loc.compare(that.loc)
      if comparison == 0 then { this.kind.compare(that.kind) }
      else comparison
    }

    /** A standard way of formatting the message. It is common to call this just before doing I/O with it */
    def format: String = {
      val ctxt = if context.nonEmpty then {
        s"${nl}Context: $context"
      } else ""
      val sugg = if suggestion.nonEmpty then {
        s"${nl}Suggestion: $suggestion"
      } else ""
      val location = loc.format
      val errorLine = loc.source.annotateErrorLine(loc)
      if loc.isEmpty || errorLine.isEmpty then {
        s"$location:$nl$message$ctxt$sugg"
      } else {
        s"$location:$nl$message:$nl$errorLine$ctxt$sugg"
      }
    }
  }

  /** Generate a style warning */
  @JSExport def style(message: String, loc: At = At.empty, suggestion: String = ""): Message = {
    Message(loc, message, StyleWarning, suggestion = suggestion)
  }

  /** Generate a tip message */
  @JSExport def tip(message: String, loc: At = At.empty, suggestion: String = ""): Message = {
    Message(loc, message, Tip, suggestion = suggestion)
  }

  /** Generate a missing warning */
  @JSExport def missing(message: String, loc: At = At.empty, suggestion: String = ""): Message = {
    Message(loc, message, MissingWarning, suggestion = suggestion)
  }

  /** Generate a usage warning */
  @JSExport def usage(message: String, loc: At = At.empty, suggestion: String = ""): Message = {
    Message(loc, message, UsageWarning, suggestion = suggestion)
  }

  /** Generate an informative message */
  @JSExport def info(message: String, loc: At = At.empty, suggestion: String = ""): Message = {
    Message(loc, message, Info, suggestion = suggestion)
  }

  /** Generate a generic warning */
  @JSExport def warning(message: String, loc: At = At.empty, suggestion: String = ""): Message = {
    Message(loc, message, Warning, suggestion = suggestion)
  }

  /** Generate an error message */
  @JSExport
  def error(
    message: String,
    loc: At = At.empty,
    suggestion: String = ""
  ): Message = { Message(loc, message, suggestion = suggestion) }

  /** Generate an error message resulting from an exception received */
  private def exceptionToError(exception: Throwable, loc: At = At.empty, context: String = ""): Message = {
    val message = ExceptionUtils.getRootCauseStackTrace(exception).mkString("\n", "\n  ", "\n")
    Message(loc, s"While $context: $message", SevereError)
  }

  /** Generate a severe error message based on an exception received */
  @JSExport def severe(message: String, exception: Throwable, loc: At): Message = {
    exceptionToError(exception, loc, message + ": ")
  }

  /** Generate a severe error message */
  @JSExport def severe(message: String, loc: At = At.empty, suggestion: String = ""): Message = {
    Message(loc, message, SevereError, suggestion = suggestion)
  }

  /** Generate a [[scala.List]] with a single error message in it */
  @JSExport def errors(message: String, loc: At = At.empty): Messages = {
    List(Message(loc, message))
  }

  /** Generate a [[scala.List]] with a single warning message in it */
  @JSExport def warnings(message: String, loc: At = At.empty): Messages = {
    List(Message(loc, message, Warning))
  }

  /** Generate a [[scala.List]] with a single server error message in it */
  @JSExport def severes(message: String, loc: At = At.empty): Messages = {
    List(Message(loc, message, Messages.SevereError))
  }

  /** A frequently used shortcut for a [[scala.collection.immutable.List]] of [[Message]]. Note that this has an
    * <code>extension</code> that extends the capability of the list.
    */
  type Messages = List[Message]

  /** Extensions to [[scala.collection.immutable.List]] of [[Messages]] */
  extension (msgs: Messages) {

    /** Format all the messages with a newline in between them. */
    @JSExport def format: String = {
      msgs.map(_.format).mkString(nl)
    }

    /** Return true iff all the messages are only warnings (including completeness) */
    @JSExport def isOnlyWarnings: Boolean = {
      msgs.isEmpty || !msgs.exists(_.kind > Warning)
    }

    /** Return true iff all the messages are considered ignorable
      * (below CompletenessWarning severity)
      */
    @JSExport def isOnlyIgnorable: Boolean = {
      msgs.isEmpty || !msgs.exists(_.kind >= CompletenessWarning)
    }

    /** Return true iff at least one of the messages is an [[Error]] */
    @JSExport def hasErrors: Boolean = {
      msgs.nonEmpty && msgs.exists(_.kind >= Error)
    }

    /** Return true iff at least one of the messages is of a [[Warning]] type. */
    @JSExport def hasWarnings: Boolean = {
      msgs.nonEmpty && msgs.exists(_.kind < Error)
    }

    /** Return a filtered list of just the [[Info]] messages. */
    @JSExport def justInfo: Messages = msgs.filter(_.isInfo)

    /** Return a filtered list of just the [[Tip]] messages. */
    @JSExport def justTips: Messages = msgs.filter(_.isTip)

    /** Return a filtered list of just the [[MissingWarning]] messages. */
    @JSExport def justMissing: Messages = msgs.filter(_.isMissing)

    /** Return a filtered list of just the [[StyleWarning]] messages. */
    @JSExport def justStyle: Messages = msgs.filter(_.isStyle)

    /** Return a filtered list of just the [[UsageWarning]] messages. */
    @JSExport def justUsage: Messages = msgs.filter(_.isUsage)

    /** Return a filtered list of just the [[Warning]] messages. */
    @JSExport def justWarnings: Messages = msgs.filter(m => m.kind < Error && m.kind > Info)

    /** Return a filtered list of just the [[Error]] messages. */
    @JSExport def justErrors: Messages = msgs.filter(_.kind >= Error)

    /** Return a filtered list of just the [[Info]] messages. */
    @JSExport def highestSeverity: Int = msgs.foldLeft(0) { case (n, m) => Math.max(m.kind.severity, n) }
  }

  /** Canonical definition of an empty message list */
  @JSExport val empty: Messages = List.empty[Message]

  /** Format and log the <code>messages</code> to the <code>log</code> per the <code>options</code>
    *
    * @param messages
    *   The list of messages to log
    * @return
    */
  @JSExport
  def logMessages(
    messages: Messages
  )(using io: PlatformContext): Int = {
    val list = if io.options.sortMessagesByLocation then messages.sorted else messages
    if io.options.groupMessagesByKind then { logMessagesByGroup(list) }
    else { logMessagesRetainingOrder(list) }
    list.highestSeverity
  }

  private def logMessage(message: Message)(using io: PlatformContext): Unit = {
    message.kind match {
      case Tip                 => io.log.tip(message.format)
      case Info                => io.log.info(message.format)
      case StyleWarning        => io.log.style(message.format)
      case MissingWarning      => io.log.missing(message.format)
      case UsageWarning        => io.log.usage(message.format)
      case CompletenessWarning => io.log.completeness(message.format)
      case Warning             => io.log.warn(message.format)
      case Error               => io.log.error(message.format)
      case SevereError         => io.log.severe(message.format)
    }
  }

  private def logMessagesRetainingOrder(list: Messages)(using io: PlatformContext): Unit = {
    list.foreach { msg => logMessage(msg) }
  }

  private def logMessagesByGroup(
    messages: Messages
  )(using io: PlatformContext): Unit = {
    def logMsgs(kind: KindOfMessage, maybeMessages: Option[Seq[Message]]): Unit = {
      val messages = maybeMessages.getOrElse(Seq.empty[Message])
      if messages.nonEmpty then {
        kind match {
          case Tip =>
            io.log.tip(s"""$kind Message Count: ${messages.length}""")
          case UsageWarning =>
            io.log.usage(s"""$kind Message Count: ${messages.length}""")
          case StyleWarning =>
            io.log.style(s"""$kind Message Count: ${messages.length}""")
          case MissingWarning =>
            io.log.missing(s"""$kind Message Count: ${messages.length}""")
          case CompletenessWarning =>
            io.log.completeness(s"""$kind Message Count: ${messages.length}""")
          case Warning =>
            io.log.warn(s"""$kind Message Count: ${messages.length}""")
          case Error =>
            io.log.error(s"""$kind Message Count: ${messages.length}""")
          case SevereError =>
            io.log.severe(s"""$kind Message Count: ${messages.length}""")
          case Info =>
            io.log.info(s"""$kind Message Count: ${messages.length}""")
        }
        messages.foreach { (msg: Message) => logMessage(msg) }
      }
    }
    if messages.nonEmpty then {
      val groups = messages.groupBy(_.kind)
      logMsgs(SevereError, groups.get(SevereError))
      logMsgs(Error, groups.get(Error))

      if io.options.showWarnings then {
        if io.options.showCompletenessWarnings then {
          logMsgs(CompletenessWarning, groups.get(CompletenessWarning))
        }
        if io.options.showUsageWarnings then {
          logMsgs(UsageWarning, groups.get(UsageWarning))
        }
        if io.options.showMissingWarnings then {
          logMsgs(MissingWarning, groups.get(MissingWarning))
        }
        if io.options.showStyleWarnings then {
          logMsgs(StyleWarning, groups.get(StyleWarning))
        }
      }
      if io.options.showTipMessages then {
        logMsgs(Tip, groups.get(Tip))
      }
      logMsgs(Info, groups.get(Info))
    }
  }

  /** A utility to help accumulate error messages. Whether the messages are accumulated or not is governed by the
    * settings in the `options` field of [[com.ossuminc.riddl.utils.PlatformContext]]
    */
  @JSExportTopLevel("Accumulator")
  case class Accumulator() {
    private val msgs: mutable.ListBuffer[Message] = mutable.ListBuffer.empty

    def size: Int = msgs.length

    @inline def isEmpty: Boolean = msgs.isEmpty
    @inline def nonEmpty: Boolean = !isEmpty

    @inline def toMessages: Messages = msgs.toList

    /** Add an arbitrary [[Message]] to the accumulated [[Messages]]
      *
      * @param message
      *   The text of the message to add
      * @return
      *   This type, so you can chain another call to this accumulator
      */
    @JSExport
    def add(message0: Message)(using pc: PlatformContext): this.type = {
      // The provideTips option is the single chokepoint that governs whether a message's
      // remediation suggestion is retained. When disabled, strip it so output stays concise
      // and existing snapshot/`.check` tests are unaffected.
      val message = if pc.options.provideTips then message0 else message0.copy(suggestion = "")
      message.kind match {
        case Warning =>
          if pc.options.showWarnings then msgs.append(message)
        case CompletenessWarning =>
          if pc.options.showCompletenessWarnings && pc.options.showWarnings then msgs.append(message)
        case StyleWarning =>
          if pc.options.showStyleWarnings && pc.options.showWarnings then msgs.append(message)
        case MissingWarning =>
          if pc.options.showMissingWarnings && pc.options.showWarnings then msgs.append(message)
        case UsageWarning =>
          if pc.options.showUsageWarnings && pc.options.showWarnings then msgs.append(message)
        case Tip =>
          if pc.options.showTipMessages then msgs.append(message)
        case Info =>
          if pc.options.showInfoMessages then msgs.append(message)
        case Error | SevereError => msgs.append(message)
      }
      this
    }

    /** Add a [[StyleWarning]] message to the accumulated [[Messages]]
      *
      * @param message
      *   The text of the message to add
      * @param loc
      *   The location in the source related to the message.
      * @return
      *   This type, so you can chain another call to this accumulator
      */
    @inline def style(message: String, loc: At = At.empty, suggestion: String = "")(using pc: PlatformContext): this.type = {
      add(Message(loc, message, StyleWarning, suggestion = suggestion))
    }

    /** Add an [[Info]] message to the accumulated [[Messages]]
      *
      * @param message
      *   The text of the message to add
      * @param loc
      *   The location in the source related to the message.
      * @return
      *   This type, so you can chain another call to this accumulator
      */
    @inline def info(message: String, loc: At = At.empty, suggestion: String = "")(using pc: PlatformContext): this.type = {
      add(Message(loc, message, Info, suggestion = suggestion))
    }

    /** Add a [[Warning]] message to the accumulated [[Messages]]
      *
      * @param message
      *   The text of the message to add
      * @param loc
      *   The location in the source related to the message.
      * @return
      *   This type, so you can chain another call to this accumulator
      */
    @inline def warning(message: String, loc: At = At.empty, suggestion: String = "")(using pc: PlatformContext): this.type = {
      add(Message(loc, message, Warning, suggestion = suggestion))
    }

    /** Add an [[Error]] message to the accumulated [[Messages]]
      *
      * @param message
      *   The text of the message to add
      * @param loc
      *   The location in the source related to the message.
      * @return
      *   This type, so you can chain another call to this accumulator
      */
    @inline def error(message: String, loc: At = At.empty, suggestion: String = "")(using pc: PlatformContext): this.type = {
      add(Message(loc, message, Error, suggestion = suggestion))
    }

    /** Add a [[SevereError]] message to the accumulated [[Messages]]
      *
      * @param message
      *   The text of the message to add
      * @param loc
      *   The location in the source related to the message.
      * @return
      *   This type, so you can chain another call to this accumulator
      */
    @inline def severe(message: String, loc: At = At.empty, suggestion: String = "")(using pc: PlatformContext): this.type = {
      add(Message(loc, message, SevereError, suggestion = suggestion))
    }

    /** Add a [[StyleWarning]] message to the accumulated [[Messages]]
      *
      * @param loc
      *   The location in the source related to the message.
      * @param msg
      *   The text of the message to add
      * @return
      *   This type, so you can chain another call to this accumulator
      */
    @inline def addStyle(loc: At, msg: String, suggestion: String = "")(using pc: PlatformContext): this.type = {
      add(Message(loc, msg, StyleWarning, suggestion = suggestion))
    }

    /** Add a [[UsageWarning]] message to the accumulated [[Messages]]
      *
      * @param msg
      *   The text of the message to add
      * @param loc
      *   The location in the source related to the message.
      * @return
      *   This type, so you can chain another call to this accumulator
      */
    @inline def addUsage(loc: At, msg: String, suggestion: String = "")(using pc: PlatformContext): this.type = {
      add(Message(loc, msg, UsageWarning, suggestion = suggestion))
    }

    @inline def addCompleteness(loc: At, msg: String, suggestion: String = "")(using pc: PlatformContext): this.type = {
      add(Message(loc, msg, CompletenessWarning, suggestion = suggestion))
    }

    /** Add a [[Tip]] message to the accumulated [[Messages]]
      *
      * @param loc
      *   The location in the source related to the message.
      * @param msg
      *   The text of the message to add
      * @return
      *   This type, so you can chain another call to this accumulator
      */
    @inline def addTip(loc: At, msg: String, suggestion: String = "")(using pc: PlatformContext): this.type = {
      add(Message(loc, msg, Tip, suggestion = suggestion))
    }

    /** Add a [[MissingWarning]] message to the accumulated [[Messages]]
      *
      * @param msg
      *   The text of the message to add
      * @param loc
      *   The location in the source related to the message.
      * @return
      *   This type, so you can chain another call to this accumulator
      */
    @inline def addMissing(loc: At, msg: String, suggestion: String = "")(using pc: PlatformContext): this.type = {
      add(Message(loc, msg, MissingWarning, suggestion = suggestion))
    }

    /** Add a [[Warning]] message to the accumulated [[Messages]]
      *
      * @param msg
      *   The text of the message to add
      * @param loc
      *   The location in the source related to the message.
      * @return
      *   This type, so you can chain another call to this accumulator
      */
    @inline def addWarning(loc: At, msg: String, suggestion: String = "")(using pc: PlatformContext): this.type = {
      add(Message(loc, msg, Warning, suggestion = suggestion))
    }

    /** Add an [[Error]] message to the accumulated [[Messages]]
      *
      * @param msg
      *   The text of the message to add
      * @param loc
      *   The location in the source related to the message.
      * @return
      *   This type, so you can chain another call to this accumulator
      */
    @inline def addError(loc: At, msg: String, suggestion: String = "")(using pc: PlatformContext): this.type = {
      add(Message(loc, msg, Error, suggestion = suggestion))
    }

    /** Add a [[SevereError]] message to the accumulated [[Messages]]
      *
      * @param msg
      *   The text of the message to add
      * @param loc
      *   The location in the source related to the message.
      * @return
      *   This type, so you can chain another call to this accumulator
      */
    @inline def addSevere(loc: At, msg: String, suggestion: String = "")(using pc: PlatformContext): this.type = {
      add(Message(loc, msg, SevereError, suggestion = suggestion))
    }
  }

  @JSExportTopLevel("Accumulator$")
  object Accumulator {
    val empty: Accumulator = new Accumulator()
  }
}
