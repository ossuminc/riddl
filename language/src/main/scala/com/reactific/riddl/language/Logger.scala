package com.reactific.riddl.language

import java.io.{PrintWriter, StringWriter}
import scala.collection.mutable.ArrayBuffer

object Logger {
  sealed trait Lvl {
    override def toString: String = this.getClass.getSimpleName.dropRight(1).toLowerCase
  }

  case object Severe extends Lvl
  case object Error extends Lvl
  case object Warning extends Lvl
  case object Info extends Lvl

}
trait Logger {
  import Logger.*

  final def severe(s: => String): Unit = { write(Severe, s) }

  final def error(s: => String): Unit = { write(Error, s) }
  final def error(s: => String, xcptn: Throwable): Unit = {
    val sw = new StringWriter
    val pw = new PrintWriter(sw)
    pw.println(s"$s: $xcptn\n")
    xcptn.printStackTrace(pw)
    write(Error, sw.toString)
  }

  final def warn(s: => String): Unit = { write(Warning, s) }

  final def info(s: => String): Unit = { write(Info, s) }

  protected def write(level: Lvl, s: String): Unit

}

case class SysLogger() extends Logger {
  def write(level: Logger.Lvl, s: String): Unit = {
    System.out.println(s"[$level] $s")
  }
}

case class StringLogger(capacity: Int = 512 * 2) extends Logger {
  private val stringBuilder = new StringBuilder(capacity)

  def write(level: Logger.Lvl, s: String): Unit =
    stringBuilder.append("[").append(level).append("] ").append(s).append("\n")

  override def toString: String = stringBuilder.toString()
}

/** A Logger which captures logged lines into an in-memory buffer, useful for testing purposes.
  */
case class InMemoryLogger() extends Logger {
  case class Line(level: Logger.Lvl, msg: String)

  private[this] val buffer = ArrayBuffer[Line]()

  /** Returns an Iterator of all lines logged to this logger, oldest-first */
  def lines(): Iterator[Line] = buffer.iterator

  def write(level: Logger.Lvl, s: String): Unit = {
    buffer += Line(level, s)
  }
}
