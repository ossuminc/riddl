/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import java.time.Clock
import scala.scalajs.js.annotation.*

/** Private implementation details which allow for more testability */
@JSExportTopLevel("Timer", "Utils")
object Timer {

  /** Runs a code block and returns its result, while recording its execution time, according to the passed clock.
    * Execution time is written to `out`, if `show` is set to `true`.
    *
    * e.g.
    *
    * timer(Clock.systemUTC(), System.out, "my-stage", true) { 1 + 1 } // 2
    *
    * prints: Stage 'my-stage': 0.000 seconds
    *
    * @param out
    *   the PrintStream to write execution time information to
    * @param stage
    *   The name of the stage, is included in output message
    * @param show
    *   if `true`, then message is printed, otherwise not
    * @param f
    *   the code block to execute
    * @return
    *   The result of running `f`
    */
  def time[T](
    stage: String,
    show: Boolean = true,
    out: LoggerInterface = SysLogger()
  )(f: => T): T = {
    if show then {
      val clock = Clock.systemUTC()
      val start = clock.millis()
      val result =
        try { f }
        finally {
          val stop = clock.millis()
          val delta = stop - start
          val seconds = delta / 1000
          val milliseconds = delta % 1000
          out.info(f"$seconds%3d.$milliseconds%03d sec: $stage")
        }
      result
    } else { f }
  }
}
