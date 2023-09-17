/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.utils

import java.time.Clock


/** Private implementation details which allow for more testability */
object Timer {

  /** Runs a code block and returns its result, while recording its execution
   * time, according to the passed clock. Execution time is written to `out`,
   * if `show` is set to `true`.
   *
   * e.g.
   *
   * timer(Clock.systemUTC(), System.out, "my-stage", true) { 1 + 1 } // 2
   *
   * prints: Stage 'my-stage': 0.000 seconds
   *
   * @param clock
   *   the clock that provides the start/end times to compute execution time
   * @param out
   *   the PrintStream to write execution time information to
   * @param stage
   *   The name of the stage, is included in output message
   * @param show
   *   if `true`, then message is printed, otherwise not
   * @param f
   *   the code block to execute
   *
   * @return
   *   The result of running `f`
   */
  def time[T](
    stage: String,
    show: Boolean = true,
    out: Logger = SysLogger(),
  )(f: => T
  ): T = {
    if show then {
      val clock = Clock.systemUTC()
      val start = clock.millis()
      val result = f
      val stop = clock.millis()
      val delta = stop - start
      val seconds = delta / 1000
      val milliseconds = delta % 1000
      out.info(f"Stage '$stage': $seconds.$milliseconds%03d seconds")
      result
    } else { f }
  }
}
