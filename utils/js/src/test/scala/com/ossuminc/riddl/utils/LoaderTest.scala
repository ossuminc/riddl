/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers._

import java.time.Clock
import scala.util.{Failure, Success, Try}
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt
import java.util.concurrent.TimeoutException

private case class AwaitFuture[T](
  future: Future[T],
  duration: FiniteDuration,
  onSuccess: T => Unit,
  onFailure: Throwable => Unit = (t: Throwable) => throw t,
) {

  val start: Long = Clock.systemUTC().millis()
  var done: Boolean = false
  checkCompleteness
  if !done then
    setTimeout(0.1) { checkCompleteness }
  end if
  def checkCompleteness: Unit =
    future.value match
      case None =>
        val now: Long = Clock.systemUTC().millis()
        if duration.toMillis < now - start then
          throw new TimeoutException(s"Timeout after ${now-start}ms")
        else
          setTimeout(0.1) { checkCompleteness }
        end if
      case Some(t: Try[T]) =>
        done = true
        t match
          case Success(result) => onSuccess(result)
          case Failure(error) => onFailure(error)
        end match
    end match
  end checkCompleteness
}

class LoaderTest extends AnyWordSpec with Matchers {

  def onSuccess(result: String): Unit = {
    result must not be (empty)
    result must startWith("domain ReactiveBBQ")
    println(result.split("\n").head)
  }

  "Loader" must {
    "load" in {
      val url = URL(
        "https://raw.githubusercontent.com/ossuminc/riddl/refs/heads/main/language/input/domains/rbbq.riddl"
      )
      val io = DOMPlatformContext()
      info(s"Loading from: ${url.toExternalForm}")
      val future = io.load(url)
      AwaitFuture[String](future, 10.seconds, onSuccess)
      info(s"Done handling: ${url.toExternalForm}")
    }
    "run a fake test" in {
      pending
    }
  }
}
