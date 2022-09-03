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

package com.reactific.riddl

import java.util.concurrent.CancellationException
import scala.concurrent.*

final class Interrupt extends (() => Boolean) {

  // We need a state-machine to track the progress.
  // It can have the following states:
  // a null reference means execution has not started.
  // a Thread reference means that the execution has started but is not done.
  // a this reference means that it is already cancelled or is already too late.
  sealed trait State
  case object NotStarted
  case class Started(onThread: Thread)
  case class CancelledOrLate(that: Interrupt)
  private[this] var state: AnyRef = NotStarted

  /**
   * This is the signal to cancel the execution of the logic.
   * Returns whether the cancellation signal was successully issued or not.
   **/
  override def apply(): Boolean = this.synchronized {
    state match {
      case NotStarted =>
        state = CancelledOrLate(this)
        true
      case _: this.type => false
      case Started(t: Thread)   =>
        state = CancelledOrLate(this)
        t.interrupt()
        true
    }
  }

  // Initializes right before execution of logic and
  // allows to not run the logic at all if already cancelled.
  private[this] def enter(): Boolean =
    this.synchronized {
      state match {
        case _: this.type => false
        case NotStarted =>
          state = Started(Thread.currentThread)
          true
      }
    }

  // Cleans up after the logic has executed
  // Prevents cancellation to occur "too late"
  private[this] def exit(): Boolean =
    this.synchronized {
      state match {
        case _: this.type => false
        case Started(_: Thread) =>
          state = CancelledOrLate(this)
          true
      }
    }

  /**
   * Executes the suplied block of logic and returns the result.
   * Throws CancellationException if the block was interrupted.
   **/
  def interruptibly[T](block: =>T): T = {
    if (enter()) {
      try block catch {
        case i: InterruptedException =>
          throw new CancellationException().initCause(i)
      } finally {
        // If we were interrupted and flag was not cleared
        if(!exit() && Thread.interrupted()) { () }
      }
    } else {
      throw new CancellationException()
    }
  }
}

object Interrupt {

  def aFuture[T](block: => T)
    (implicit ec: ExecutionContext): (Future[T], () => Boolean) = {
    val interrupt = new Interrupt()
    Future(interrupt.interruptibly(block))(ec) -> interrupt
  }
}
