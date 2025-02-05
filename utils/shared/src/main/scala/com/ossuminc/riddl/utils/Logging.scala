/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import com.ossuminc.riddl.utils.StringHelpers.*

import scala.annotation.unused
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.AnsiColor.*
import scala.scalajs.js.annotation._

/** Companion class to Logging trait. Provides the definitions of logging levels via the Lvl trait.
  */
@JSExportTopLevel("Logger")
object Logging {
  sealed trait Lvl {
    override def toString: String =
      this.getClass.getSimpleName.toLowerCase.dropRightWhile(_ == '$')
  }

  case object Severe extends Lvl
  case object Error extends Lvl
  case object Warning extends Lvl
  case object Usage extends Lvl
  case object Style extends Lvl
  case object Missing extends Lvl
  case object Info extends Lvl

}

/** Base trait for all styles of loggers. Logger requires the "write" method be implemented which
  * provides a style-specific way to handle the logging of a message.
  */
trait Logger(using pc: PlatformContext) {
  import Logging.*

  /** Syntactic sugar for write(Severe, s) */
  final def severe(s: => String): Unit = { write(Severe, s) }

  /** Syntactic sugar for write(Severe, <message-derived-from-exception>) */
  final def severe(s: => String, xcptn: Throwable): Unit = {
    val message =
      s"""$s: $xcptn
         |${ExceptionUtils.getRootCauseStackTrace(xcptn).mkString("\n")}
         |""".stripMargin
    write(Severe, message)
  }

  /** Syntactic sugar for write(Error, s) */
  final def error(s: => String): Unit = { write(Error, s) }

  /** Syntactic sugar for write(Warning, s) */
  final def warn(s: => String): Unit = { write(Warning, s) }

  /** Syntactic sugar for write(Usage, s) */
  final def usage(s: => String): Unit = { write(Usage, s) }

  /** Syntactic sugar for write(Style, s) */
  final def style(s: => String): Unit = { write(Style, s) }

  /** Syntactic sugar for write(Missing, s) */
  final def missing(s: => String): Unit = { write(Missing, s) }

  /** Syntactic sugar for write(Info, s) */
  final def info(s: => String): Unit = { write(Info, s) }

  private var nSevere = 0
  private var nError = 0
  private var nMissing = 0
  private var nStyle = 0
  private var nUsage = 0
  private var nWarning = 0
  private var nInfo = 0

  protected def highlight(level: Lvl, s: String): String = {
    if pc.options.noANSIMessages then s"[$level] $s"
    else
      val prefix = level match {
        case Logging.Severe  => s"$RED_B$BLACK"
        case Logging.Error   => s"$RED"
        case Logging.Warning => s"$YELLOW"
        case Logging.Usage   => s"$GREEN"
        case Logging.Style   => s"$GREEN"
        case Logging.Missing => s"$GREEN"
        case Logging.Info    => s"$BLUE"
      }
      val lines = s.split(pc.newline)
      val head = s"$prefix$BOLD[$level] ${lines.head}$RESET"
      val tail = lines.tail.mkString(pc.newline)
      if tail.nonEmpty then head + s"${pc.newline}$prefix$tail$RESET"
      else head
    end if
  }

  protected def write(level: Lvl, @unused s: String): Unit

  protected def count(level: Lvl): Unit = {
    level match {
      case Severe  => nSevere += 1
      case Error   => nError += 1
      case Warning => nWarning += 1
      case Style   => nStyle += 1
      case Usage   => nUsage += 1
      case Missing => nMissing += 1
      case Info    => nInfo += 1
    }
  }

  /** Generate a summary on the number of messages logged for each severity Lvl. */
  def summary: String = {
    s"""Severe Errors: $nSevere
       |Normal Errors: $nError
       |     Warnings: $nWarning
       |        Usage: $nUsage
       |        Style: $nStyle
       |     Misasing: $nMissing
       |         Info: $nInfo
       |""".stripMargin
  }
}

/** A logger that writes to "stdout" per the PlatformContext */
case class SysLogger()(using pc: PlatformContext) extends Logger:
  override def write(level: Logging.Lvl, s: String): Unit = {
    super.count(level)
    pc.stdoutln(highlight(level, s))
  }
end SysLogger

/** A logger that doesn't do I/O but collects the messages in a buffer
  *
  * @param capacity
  *   The initial capacity of the buffer, defaults to 1K
  * @param pc
  *   The PlatformContext to use
  */
case class StringLogger(capacity: Int = 512 * 2)(using pc: PlatformContext) extends Logger:
  private val stringBuilder = new mutable.StringBuilder(capacity)

  override def write(level: Logging.Lvl, s: String): Unit = {
    super.count(level)
    stringBuilder.append(highlight(level, s)).append("\n")
  }

  override def toString: String = stringBuilder.toString()
end StringLogger

/** A logger that delegates to a callback function.
  *
  * @param callback
  *   The function to call when a messages needs logging
  */
case class CallBackLogger(callback: (Logging.Lvl, String) => Unit)(using pc: PlatformContext)
    extends Logger:
  override def write(level: Logging.Lvl, s: String): Unit = {
    super.count(level)
    callback(level, s)
  }
end CallBackLogger

/** A logger to use when logging of messages is not desired. It will still count the number of
  * messages for each severity Lvl.
  */
case class NullLogger()(using pc: PlatformContext) extends Logger:
  override def write(level: Logging.Lvl, s: String): Unit = super.count(level)
end NullLogger
