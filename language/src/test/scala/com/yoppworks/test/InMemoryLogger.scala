package com.yoppworks.test

import com.yoppworks.ossum.riddl.language.Riddl.Logger
import com.yoppworks.test.Logging.Lvl

import scala.collection.mutable.ArrayBuffer

object Logging {
  sealed trait Lvl

  object Lvl {
    case object Severe extends Lvl
    case object Error extends Lvl
    case object Warn extends Lvl
    case object Info extends Lvl
  }
  final case class Line(level: Lvl, msg: String)
}

/** A Logger which captures logged lines into an in-memory buffer, useful for testing purposes.*/
final class InMemoryLogger extends Logger {
  private[this] val buffer = ArrayBuffer[Logging.Line]()
  private[this] def addMsg(level: Lvl, msg: String): Unit = {
    buffer += Logging.Line(level, msg)
  }
  override def severe(s: => String): Unit = addMsg(Lvl.Severe, s)
  override def error(s: => String): Unit = addMsg(Lvl.Error, s)
  override def warn(s: => String): Unit = addMsg(Lvl.Warn, s)
  override def info(s: => String): Unit = addMsg(Lvl.Info, s)

  /** Returns an Iterator of all lines logged to this logger, oldest-first */
  def lines(): Iterator[Logging.Line] = buffer.iterator
}
