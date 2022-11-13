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
        info(s"Event: ${events.mkString(",")}")
        false
      }
      def notOnEvents: Boolean = {
        info("No events")
        true
      }
      // watch for changes
      val f = Future[Boolean] {
        FileWatcher.watchForChanges(dir, 2, 10)(onEvents)(notOnEvents)
      }
      Thread.sleep(1000)
      val changeFile = dir.resolve("change.file")
      if (Files.exists(changeFile)) { Files.delete(changeFile) }
      changeFile.toFile.createNewFile()
      Files.delete(changeFile)
      info(s"Future completed: ${f.isCompleted}")
      val result = Await.result(f, Duration(1, "seconds"))
      result must be(true)
    }
  }
}
