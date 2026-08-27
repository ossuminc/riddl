/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** The wording and the bounds shared by the two commands that repeat a target command —
  * [[OnChangeCommand]] and [[RepeatCommand]].
  *
  * Both declare `target-command`, `refresh-rate` and `max-cycles` with identical help text and
  * identical validation limits, because they mean the same thing in both. Written out twice, a
  * bound changed in one and not the other diverges silently: the two commands would disagree about
  * how fast a refresh may be, and nothing would fail.
  *
  * Only the text and the numbers live here. Each command still wires its own scopt arguments, since
  * their `Options` types differ and a shared generic builder would cost more in ceremony than the
  * duplication it removed.
  */
private[commands] object RepeatingCommandText {

  val targetCommand: String =
    "The name of the command to select from the configuration file"

  val refreshRate: String =
    """Specifies the rate at which the <git-clone-dir> is checked
      |for updates so the process to regenerate the hugo site is
      |started""".stripMargin

  val maxCycles: String =
    """Limit the number of check cycles that will be repeated."""

  /** Below this a refresh would spin hot enough to be useless. */
  val minRefreshRate: FiniteDuration = 1.second

  /** Above this a refresh is indistinguishable from not watching at all. */
  val maxRefreshRate: FiniteDuration = 1.day

  val refreshRateTooFast: String = "<refresh-rate> is too fast, minimum is 1 seconds"

  val refreshRateTooSlow: String = "<refresh-rate> is too slow, maximum is 1 day"

  val minCycles: Int = 1

  val maxCyclesLimit: Int = 1024 * 1024

  val tooFewCycles: String = "<max-cycles> can't be less than 1"

  val tooManyCycles: String = "<max-cycles> is too big"
}
