/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

object ExceptionUtils {

  import scala.scalajs.js.annotation.JSExport

  /** Describe a Throwable and its cause chain, for the pass runner's catch-all.
    *
    * Scala.js has no commons-lang3, and its stack traces are best-effort rather than guaranteed.
    * This used to return an EMPTY array on that reasoning, which meant every exception caught by
    * `Pass.runThesePasses` became a Severe message with NO TEXT and no location -- an IDE rendered
    * it as a blank squiggle on line 1 pointing at nothing, which is worse than the crash it was
    * reporting. riddl-vscode reported exactly that.
    *
    * An exception's own description is ALWAYS available in Scala.js, whatever the stack traces do,
    * so say at least that much. Frames are included when the runtime supplies them and simply
    * omitted when it does not.
    */
  @JSExport
  def getRootCauseStackTrace(exception: Throwable): Array[String] =
    describe(exception, depth = 0).toArray

  private val MaxCauseDepth = 16
  private val MaxFrames = 12

  private def describe(thrown: Throwable, depth: Int): List[String] =
    if depth > MaxCauseDepth then List("\t... cause chain truncated")
    else
      val header = if depth == 0 then thrown.toString else s"Caused by: ${thrown.toString}"
      val frames =
        try thrown.getStackTrace.take(MaxFrames).map(frame => s"\tat $frame").toList
        catch
          case _: Throwable => Nil // a describing helper must never itself throw
      // `filterNot(_ eq thrown)` because a Throwable whose cause is itself would otherwise
      // recurse to the depth guard and bury the useful first line.
      val causes = Option(thrown.getCause)
        .filterNot(_ eq thrown)
        .toList
        .flatMap(cause => describe(cause, depth + 1))
      header :: frames ::: causes
  end describe
}
