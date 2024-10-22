package com.ossuminc.riddl.utils

import org.scalajs.dom
import scala.concurrent.Future
import scala.annotation.unused

object Await {

  import scala.concurrent.Awaitable
  import scala.concurrent.duration.FiniteDuration
  import scala.util.{Success, Failure}

  def result[T](future: Future[T], @unused secondsToWait: Int): T = {
    // NOTE: In a scalaJS environment, there are no threads so Futures complete serially
    // NOTE: This makes Await in this context pretty much useless.
    if future.isCompleted then
      future.value match
        case Some(Success(value)) => value
        case Some(Failure(xcptn)) => throw xcptn
        case None                 => throw new IllegalStateException("No value for completed future")
    else throw new IllegalStateException("Awaitable has not yet completed")
    end if
  }
}
