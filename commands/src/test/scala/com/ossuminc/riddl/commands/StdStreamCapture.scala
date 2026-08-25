/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.StringBuildingPrintStream

/** Captures `System.out` for the duration of a block, under a JVM-wide lock.
  *
  * **The lock is the whole point.** `System.setOut` is process-global, and sbt runs ScalaTest
  * SUITES in parallel, so two suites capturing at once silently steal each other's output. That is
  * not hypothetical: `ProductGoesToStdoutTest`'s `help` case failed in a full run having captured
  * `InfoCommandTest`'s build information, while both suites passed when run alone. A test that
  * passes in isolation and fails in company is worse than no test, because the natural reading of
  * the failure is that the code broke.
  *
  * Any suite that redirects a standard stream must come through here, or it reintroduces the race
  * for everyone.
  */
object StdStreamCapture {

  def capturingStdOut[A](f: () => A): (A, String) = synchronized {
    val saved = System.out
    val captured = StringBuildingPrintStream()
    try
      System.setOut(captured)
      val a = f()
      captured.flush()
      (a, captured.mkString())
    finally System.setOut(saved)
  }
}
