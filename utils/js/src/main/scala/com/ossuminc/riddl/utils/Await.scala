package com.ossuminc.riddl.utils

object Await {

  import scala.concurrent.Awaitable
  import scala.concurrent.duration.FiniteDuration

  def result[T](awaitable: Awaitable[T], secondsToWait: Int): T = {
    // FIXME: should be: scala.concurrent.Await.result[T](awaitable, waitFor)
    // FIXME: but this doesn't work in JS, maybe wait for WASM?
    throw NotImplementedError("Await.result(...) is not implemented for Javascript")
  }
}
