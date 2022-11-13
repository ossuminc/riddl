/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.commands

import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.utils.Logger
import org.eclipse.jgit.lib.ProgressMonitor

import java.io.PrintStream

case class DotWritingProgressMonitor(out: PrintStream, log: Logger,
  options: CommonOptions)
    extends ProgressMonitor {
  override def start(totalTasks: Int): Unit = {
    if (options.verbose) { log.info(s"Starting Fetch with $totalTasks tasks.") }
    else { out.print("\n.") }
  }

  override def beginTask(title: String, totalWork: Int): Unit = {
    if (options.verbose) {
      log.info(s"Starting Task '$title', $totalWork remaining.")
    } else { out.print(".") }
  }

  override def update(completed: Int): Unit = {
    if (options.verbose) { log.info(s"$completed tasks completed.") }
    else { out.print(".") }
  }

  override def endTask(): Unit = {
    if (options.verbose) { log.info(s"Task completed.") }
    else { out.println(".") }
  }

  override def isCancelled: Boolean = false
}
