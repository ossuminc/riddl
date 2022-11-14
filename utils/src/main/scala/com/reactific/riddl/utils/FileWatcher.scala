/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.utils

import java.io.IOException
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.jdk.CollectionConverters.CollectionHasAsScala

object FileWatcher {

  @throws[IOException]
  private def registerRecursively(
    root: Path,
    watchService: WatchService
  ): Unit = {
    import java.nio.file.FileVisitResult
    import java.nio.file.Files
    import java.nio.file.SimpleFileVisitor
    import java.nio.file.attribute.BasicFileAttributes
    val sfv = new SimpleFileVisitor[Path]() {
      override def preVisitDirectory(
        dir: Path,
        attrs: BasicFileAttributes
      ): FileVisitResult = {
        dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
        FileVisitResult.CONTINUE
      }
    }
    Files.walkFileTree(root, sfv)
  }

  private def handlePolledEvents(
    key: WatchKey,
    events: Seq[WatchEvent[?]],
    interval: Int
  )(onEvents: Seq[WatchEvent[?]] => Boolean
  )(notOnEvents: => Boolean
  ): Unit = {
    events match {
      case x: Seq[WatchEvent[?]] if x.isEmpty =>
        if (notOnEvents) {
          key.reset()
          Thread.sleep(interval)
        } else {
          // they want to stop
          key.cancel()
        }
      case events =>
        if (onEvents(events)) {
          // reset the key for the next trip around
          key.reset()
        } else {
          // they want to stop
          key.cancel()
        }
    }
  }

  def watchForChanges(
    path: Path,
    periodInSeconds: Int,
    intervalInMillis: Int
  )(onEvents: Seq[WatchEvent[?]] => Boolean
  )(notOnEvents: => Boolean
  ): Boolean = {
    val deadline = Instant.now().plus(periodInSeconds, ChronoUnit.SECONDS)
      .toEpochMilli
    val watchService: WatchService = FileSystems.getDefault.newWatchService()
    try {
      registerRecursively(path, watchService)
      var saveKey: WatchKey = null
      do {
        watchService.take() match {
          case key: WatchKey if key != null =>
            saveKey = key
            val events = key.pollEvents().asScala.toSeq
            handlePolledEvents(key, events, intervalInMillis)(onEvents)(
              notOnEvents
            )
        }
      } while (saveKey.isValid && Instant.now().toEpochMilli < deadline)
      System.currentTimeMillis() < deadline
    } finally { watchService.close() }
  }
}
