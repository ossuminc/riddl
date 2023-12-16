/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import java.util.concurrent.CancellationException
import scala.concurrent.*
import scala.concurrent.ExecutionContext.Implicits.global

@SuppressWarnings(Array("org.wartremover.warts.Var"))
final class Interrupt extends (() => Boolean) {

  // We need a state-machine to track the progress.
  // It can have the following states:
  // a null reference means execution has not started.
  // a Thread reference means that the execution has started but is not done.
  // an Interrupt reference means that it is already cancelled or is already
  // too late.
  sealed trait State
  case object NotStarted extends State
  case class Started(onThread: Thread) extends State
  case class CancelledOrLate(that: Interrupt) extends State

  private[this] var state: AnyRef = NotStarted

  /** This is the signal to cancel the execution of the logic. Returns whether
    * the cancellation signal was successully issued or not.
    */
  override def apply(): Boolean = this.synchronized {
    state match {
      case NotStarted =>
        state = CancelledOrLate(this)
        true
      case _: this.type => false
      case Started(t: Thread) =>
        state = CancelledOrLate(this)
        t.interrupt()
        true
    }
  }

  // Initializes right before execution of logic and
  // allows to not run the logic at all if already cancelled.
  private[this] def enter(): Boolean = this.synchronized {
    state match {
      case NotStarted =>
        state = Started(Thread.currentThread)
        true
      case _: this.type => false
    }
  }

  // Cleans up after the logic has executed
  // Prevents cancellation to occur "too late"
  private[this] def exit(): Boolean = this.synchronized {
    state match {
      case _: this.type => false
      case Started(_: Thread) =>
        state = CancelledOrLate(this)
        true
    }
  }

  private var thread: Option[Thread] = None

  def ready: Boolean = thread.nonEmpty

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def cancel: Unit = {
    if thread.nonEmpty then thread.map(_.interrupt())
    else {
      throw new IllegalStateException("Thread not obtained yet.")
    }
  }

  /** Executes the supplied block of code and returns the result. Throws
    * CancellationException if the block was interrupted.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def interruptibly[T](block: => T): T = {
    if enter() then {
      thread = Some(Thread.currentThread())
      try block
      catch {
        case i: InterruptedException =>
          throw new CancellationException().initCause(i)
      } finally {
        thread = None
        // If we were interrupted and flag was not cleared
        if !exit() && Thread.interrupted() then { () }
      }
    } else {
      throw new CancellationException()
    }
  }
}

object Interrupt {

  def aFuture[T](
    block: => T
  )(implicit ec: ExecutionContext
  ): (Future[T], Interrupt) = {
    val interrupt = new Interrupt()
    Future { interrupt.interruptibly(block) }(ec) -> interrupt
  }

  def allowCancel(
    isInteractive: Boolean
  ): (Future[Boolean], Option[Interrupt]) = {
    if !isInteractive then { Future.successful(false) -> None }
    else {
      val result = aFuture[Boolean] {
        while
          Option(scala.io.StdIn.readLine("Type <Ctrl-D> To Exit:\n")).nonEmpty
        do {}
        true
      }
      result._1 -> Some(result._2)
    }
  }
}
