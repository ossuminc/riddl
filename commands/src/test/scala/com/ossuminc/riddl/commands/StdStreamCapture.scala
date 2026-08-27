/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.{StringBuildingPrintStream, pc}

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

  /** Locks on the **PlatformContext**, deliberately, rather than on this object.
    *
    * A private monitor here DEADLOCKED the `commands` suite. `pc.withOptions` and `pc.withLogger`
    * are `synchronized` on the PlatformContext, so a test that wraps `withOptions` around a capture
    * takes pc-then-capture while a test that captures around a command takes capture-then-pc —
    * classic opposite-order acquisition, and the run hung with threads BLOCKED on
    * `PlatformContext.withOptions`.
    *
    * Sharing the ONE monitor removes the ordering question instead of documenting it: there is no
    * second lock to order against, and Java monitors are reentrant so nesting is safe either way.
    */
  /** Redirects BOTH `System.out` and `scala.Console.out`, and the second is not redundant.
    *
    * `println` in Scala source is `Console.println`, and `Console.out` is a DynamicVariable
    * initialised from `System.out` when the class loads -- so `System.setOut` alone does NOT
    * redirect it. In production the two are the same object and nothing notices; under capture,
    * output written with a bare `println` goes to the real stdout and the test sees an empty
    * string. That reads as "the command printed nothing", which is precisely the failure this
    * whole family of tests exists to detect, arriving as a false positive from the instrument.
    *
    * Found 2026-08-25 by `ValidateJsonTest`: the JSON was visibly in the sbt log while the
    * assertion reported exhausted input.
    */
  def capturingStdOut[A](f: () => A): (A, String) = pc.synchronized {
    val saved = System.out
    val captured = StringBuildingPrintStream()
    try
      System.setOut(captured)
      val a = Console.withOut(captured) { f() }
      captured.flush()
      (a, captured.mkString())
    finally System.setOut(saved)
  }
}
