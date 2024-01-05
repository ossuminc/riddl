/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.CancellationException
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.Success

/** Unit Tests For Interrupt */
class InterruptTest extends AnyWordSpec with Matchers {
  "InterruptTest" must {
    "work non-interactively" in {
      val (future, _) = Interrupt.allowCancel(false)
      future.value mustBe Some(Success(false))
    }
    "work interactively" in {
      pending
      val (future, cancel) = Interrupt.allowCancel(true)
      cancel match {
        case Some(interrupt) =>
          val f2 = future.map(_ => fail("Should have cancelled")).recover {
            case _: InterruptedException  => succeed
            case _: CancellationException => succeed
            case x: Throwable             => fail(s"Wrong exception: $x")
          }
          while !interrupt.ready do Thread.sleep(5)
          interrupt.cancel
          Await.result(f2, 2.seconds)
        case None => fail("Unexpected result")
      }
    }
  }
}
