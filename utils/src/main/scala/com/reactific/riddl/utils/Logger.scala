/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.utils

import org.apache.commons.lang3.exception.ExceptionUtils

import scala.annotation.unused
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object Logger {
  sealed trait Lvl {
    override def toString: String = this.getClass.getSimpleName.dropRight(1)
      .toLowerCase
  }

  case object Severe extends Lvl
  case object Error extends Lvl
  case object Warning extends Lvl
  case object Info extends Lvl
}

trait Logger {
  import Logger.*

  final def severe(s: => String): Unit = { write(Severe, s) }
  final def severe(s: => String, xcptn: Throwable): Unit = {
    val message = s"""$s: $xcptn
                     |${ExceptionUtils.getRootCauseStackTrace(xcptn)
      .mkString("\n")}
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

  protected def write(level: Lvl, @unused s: String): Unit

  protected def count(level: Lvl): Unit = {
    level match {
      case Severe => nSevere += 1
      case Error => nError += 1
      case Warning => nWarning += 1
      case Info => nInfo += 1
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

case class SysLogger() extends Logger {
  override def write(level: Logger.Lvl, s: String): Unit = {
    super.count(level)
    System.out.println(s"[$level] $s")
  }
}

case class StringLogger(capacity: Int = 512 * 2) extends Logger {
  private val stringBuilder = new mutable.StringBuilder(capacity)

  override def write(level: Logger.Lvl, s: String): Unit = {
    super.count(level)
    stringBuilder.append("[")
      .append(level).append("] ").append(s).append("\n")
  }

  override def toString: String = stringBuilder.toString()
}

/** A Logger which captures logged lines into an in-memory buffer, useful for
  * testing purposes.
  */
case class InMemoryLogger() extends Logger {
  case class Line(level: Logger.Lvl, msg: String)

  private[this] val buffer = ArrayBuffer[Line]()

  /** Returns an Iterator of all lines logged to this logger, oldest-first */
  def lines(): Iterator[Line] = buffer.iterator

  def write(level: Logger.Lvl, s: String): Unit = {
    super.count(level)
    buffer += Line(level, s)
  }
}
