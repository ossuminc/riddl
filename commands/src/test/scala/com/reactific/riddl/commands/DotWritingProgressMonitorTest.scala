/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.commands

import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.utils.{StringBuildingPrintStream, StringLogger}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DotWritingProgressMonitorTest extends AnyWordSpec with Matchers {
  def runTest(verbose: Boolean): (String, String) = {
    val log = StringLogger(1024)
    val capture = StringBuildingPrintStream()
    val monitor =
      DotWritingProgressMonitor(capture, log, CommonOptions(verbose = verbose))
    monitor.start(3)
    def runTask(name: String, work: Int): Unit = {
      monitor.beginTask(name, work)
      for (i <- 1 to work) { monitor.update(i) }
      monitor.endTask()
    }
    runTask("One", 5)
    runTask("Two", 5)
    runTask("Three", 5)
    (capture.mkString(), log.toString())

  }
  "DotWritingProgressMonitor" should {
    "product correct output for a set of tasks" in {
      val (capture, log) = runTest(true)
      capture mustBe empty
      log must be("""|[info] Starting Fetch with 3 tasks.
                     |[info] Starting Task 'One', 5 remaining.
                     |[info] 1 tasks completed.
                     |[info] 2 tasks completed.
                     |[info] 3 tasks completed.
                     |[info] 4 tasks completed.
                     |[info] 5 tasks completed.
                     |[info] Task completed.
                     |[info] Starting Task 'Two', 5 remaining.
                     |[info] 1 tasks completed.
                     |[info] 2 tasks completed.
                     |[info] 3 tasks completed.
                     |[info] 4 tasks completed.
                     |[info] 5 tasks completed.
                     |[info] Task completed.
                     |[info] Starting Task 'Three', 5 remaining.
                     |[info] 1 tasks completed.
                     |[info] 2 tasks completed.
                     |[info] 3 tasks completed.
                     |[info] 4 tasks completed.
                     |[info] 5 tasks completed.
                     |[info] Task completed.
                     |""".stripMargin)
    }
    "produce correct output in non-verbose mode" in {
      val (capture, log) = runTest(false)
      log mustBe empty
      capture must be("""
                        |........
                        |.......
                        |.......
                        |""".stripMargin)

    }
  }

}
