/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

object Await {

  import scala.concurrent.Awaitable
  import scala.concurrent.duration.FiniteDuration
  import scala.concurrent.duration.SECONDS

  def result[T](awaitable: Awaitable[T], secondsToWait: Int): T = {
    scala.concurrent.Await.result[T](awaitable, FiniteDuration(secondsToWait, SECONDS))
  }

  def result[T](awaitable: Awaitable[T], duration: FiniteDuration): T = {
    scala.concurrent.Await.result[T](awaitable, duration)
  }
}
