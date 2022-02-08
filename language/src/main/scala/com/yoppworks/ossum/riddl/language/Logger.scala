package com.yoppworks.ossum.riddl.language

import scala.collection.mutable.ArrayBuffer

trait Logger {
  def severe(s: => String): Unit

  def error(s: => String): Unit

  def warn(s: => String): Unit

  def info(s: => String): Unit
}

object Logger {}

case class SysLogger() extends Logger {

  override def severe(s: => String): Unit = { System.out.println("[severe] " + s) }

  override def error(s: => String): Unit = { System.out.println("[error] " + s) }

  override def warn(s: => String): Unit = { System.out.println("[warning] " + s) }

  override def info(s: => String): Unit = { System.out.println("[info] " + s) }
}

case class StringLogger(capacity: Int = 512 * 2) extends Logger {
  private val stringBuilder = new StringBuilder(capacity)

  override def severe(s: => String): Unit = { stringBuilder.append("[severe] " + s + "\n") }

  override def error(s: => String): Unit = { stringBuilder.append("[error] " + s + "\n") }

  override def warn(s: => String): Unit = { stringBuilder.append("[warning] " + s + "\n") }

  override def info(s: => String): Unit = { stringBuilder.append("[info] " + s + "\n") }

  override def toString: String = stringBuilder.toString()
}

/** A Logger which captures logged lines into an in-memory buffer, useful for testing purposes.
  */
object InMemoryLogger {
  sealed trait Lvl

  object Lvl {
    case object Severe extends Lvl

    case object Error extends Lvl

    case object Warn extends Lvl

    case object Info extends Lvl
  }

  case class Line(level: Lvl, msg: String)
}

case class InMemoryLogger() extends Logger {

  import InMemoryLogger.*

  private[this] val buffer = ArrayBuffer[Line]()

  private[this] def addMsg(level: Lvl, msg: String): Unit = { buffer += Line(level, msg) }

  override def severe(s: => String): Unit = addMsg(Lvl.Severe, s)

  override def error(s: => String): Unit = addMsg(Lvl.Error, s)

  override def warn(s: => String): Unit = addMsg(Lvl.Warn, s)

  override def info(s: => String): Unit = addMsg(Lvl.Info, s)

  /** Returns an Iterator of all lines logged to this logger, oldest-first */
  def lines(): Iterator[Line] = buffer.iterator
}
