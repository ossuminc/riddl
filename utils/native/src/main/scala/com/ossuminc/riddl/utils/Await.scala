package com.ossuminc.riddl.utils

object Await {

  import scala.concurrent.Awaitable
  import scala.concurrent.duration.{FiniteDuration, SECONDS, TimeUnit}

  def result[T](awaitable: Awaitable[T], secondsToWait: Int): T = {
    scala.concurrent.Await.result[T](awaitable, FiniteDuration(secondsToWait, SECONDS))
  }
}
