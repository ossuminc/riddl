package com.reactific.riddl.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.WatchEvent
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class FileWatcherTest extends AnyWordSpec with Matchers {
  "FileWatcher" should {
    "notice changes in a directory" in {
      val dir = Path.of(".").resolve("onchange").resolve("target")
        .toAbsolutePath
      def onEvents(events: Seq[WatchEvent[?]]): Boolean = {
        events.foreach { ev => info(s"Event: ${ev.kind()}: ${ev.count()}") }
        false
      }
      def notOnEvents: Boolean = {
        info("No events")
        true
      }
      // Resolve the file to
      val changeFile = dir.resolve("change.file")
      // Make sure it doesn't exist
      if (Files.exists(changeFile)) { Files.delete(changeFile) }
      // watch for changes
      val f = Future[Boolean] {
        FileWatcher.watchForChanges(dir, 2, 10)(onEvents)(notOnEvents)
      }
      Thread.sleep(900)
      Files.createFile(changeFile)
      require(Files.exists(changeFile), "File should exist")
      Thread.sleep(100)
      Files.delete(changeFile)
      val result = Await.result(f, Duration(3, "seconds"))
      result must be(true)
    }
  }
}
