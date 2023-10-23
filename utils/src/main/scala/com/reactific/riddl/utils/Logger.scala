/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.utils

import org.apache.commons.lang3.exception.ExceptionUtils

import scala.annotation.unused
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.AnsiColor.*

object Logger {
  sealed trait Lvl {
    override def toString: String = this.getClass.getSimpleName.dropRight(1).toLowerCase
  }

  case object Severe extends Lvl
  case object Error extends Lvl
  case object Warning extends Lvl
  case object Info extends Lvl

  val nl = System.getProperty("line.separator")
}

@SuppressWarnings(Array("org.wartremover.warts.Var"))
trait Logger {
  import Logger.*

  protected def withHighlighting: Boolean = true

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

  final def info(s: => String): Unit = { write(Info, s) }

  private var nSevere = 0
  private var nError = 0
  private var nWarning = 0
  private var nInfo = 0

  protected def highlight(level: Lvl, s: String): String = {
    if !withHighlighting then
      s"[$level] $s"
    else
      val prefix = level match {
        case Logger.Severe  => s"${RED_B}${BLACK}"
        case Logger.Error   => s"${RED}"
        case Logger.Warning => s"${YELLOW}"
        case Logger.Info    => s"${BLUE}"
      }
      val lines = s.split(nl)
      val head = s"$prefix$BOLD[$level] ${lines.head}$RESET"
      val tail = lines.tail.mkString(nl)
      if tail.nonEmpty then
        head + s"$nl$prefix$tail$RESET"
      else
        head
  }

  protected def write(level: Lvl, @unused s: String): Unit

  protected def count(level: Lvl): Unit = {
    level match {
      case Severe  => nSevere += 1
      case Error   => nError += 1
      case Warning => nWarning += 1
      case Info    => nInfo += 1
    }
  }

  def summary: String = {
    s"""Severe Errors: $nSevere
       |Normal Errors: $nError
       |     Warnings: $nWarning
       |         Info: $nInfo
       |""".stripMargin
  }
}

case class SysLogger(override val withHighlighting: Boolean = true) extends Logger {
  override def write(level: Logger.Lvl, s: String): Unit = {
    super.count(level)
    System.out.println(highlight(level, s))
  }
}

case class StringLogger(capacity: Int = 512 * 2, override val withHighlighting: Boolean = true) extends Logger {
  private val stringBuilder = new mutable.StringBuilder(capacity)

  override def write(level: Logger.Lvl, s: String): Unit = {
    super.count(level)
    stringBuilder.append(highlight(level, s)).append("\n")
  }

  override def toString: String = stringBuilder.toString()
}

/** A Logger which captures logged lines into an in-memory buffer, useful for testing purposes.
  */
case class InMemoryLogger(override val withHighlighting: Boolean = false) extends Logger {
  case class Line(level: Logger.Lvl, msg: String)

  private[this] val buffer = ArrayBuffer[Line]()

  /** Returns an Iterator of all lines logged to this logger, oldest-first */
  def lines(): Iterator[Line] = buffer.iterator

  def write(level: Logger.Lvl, s: String): Unit = {
    super.count(level)
    buffer += Line(level, s)
  }
}
