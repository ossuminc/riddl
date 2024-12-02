/*
 * Copyright 2019 Ossum, Inc.
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

  val nl: String = "\n"
}

trait Logger(using pc: PlatformContext) {
  import Logging.*

  final def severe(s: => String): Unit = { write(Severe, s) }
  final def severe(s: => String, xcptn: Throwable): Unit = {
    val message =
      s"""$s: $xcptn
         |${ExceptionUtils.getRootCauseStackTrace(xcptn).mkString("\n")}
         |""".stripMargin
    write(Severe, message)
  }

  final def error(s: => String): Unit = { write(Error, s) }

  final def warn(s: => String): Unit = { write(Warning, s) }

  final def usage(s: => String): Unit = { write(Usage, s) }
  final def style(s: => String): Unit = { write(Style, s) }
  final def missing(s: => String): Unit = { write(Missing, s) }

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
      val lines = s.split(nl)
      val head = s"$prefix$BOLD[$level] ${lines.head}$RESET"
      val tail = lines.tail.mkString(nl)
      if tail.nonEmpty then head + s"$nl$prefix$tail$RESET"
      else head
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

case class SysLogger()(using io: PlatformContext) extends Logger {
  override def write(level: Logging.Lvl, s: String): Unit = {
    super.count(level)
    io.stdoutln(highlight(level, s))
  }
}

@JSExportTopLevel("StringLogger")
case class StringLogger(capacity: Int = 512 * 2)(using io: PlatformContext = pc) extends Logger {
  private val stringBuilder = new mutable.StringBuilder(capacity)

  override def write(level: Logging.Lvl, s: String): Unit = {
    super.count(level)
    stringBuilder.append(highlight(level, s)).append("\n")
  }

  override def toString: String = stringBuilder.toString()
}

@JSExportTopLevel("CallBackLogger")
case class CallBackLogger(callback: (Logging.Lvl, String) => Unit) extends Logger {
  override def write(level: Logging.Lvl, s: String): Unit = {
    super.count(level)
    callback(level, s)
  }
}
