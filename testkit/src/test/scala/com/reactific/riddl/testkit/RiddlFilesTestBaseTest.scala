package com.reactific.riddl.testkit

import org.scalatest.Assertion

import java.io.File
import java.nio.file.Path

class RiddlFilesTestBaseTest extends RiddlFilesTestBase {

  def checkAFile(rootDir: Path, file: File): Assertion = {
    succeed
  }

  "RiddlFilesTestBase" should {
    "find riddl files" in {
      val dir = "testkit/src/test/input"
      val files = findRiddlFiles(new File(dir), true)
      files.size must be > 0
    }
    "not find non-files" in {
      intercept[org.scalatest.exceptions.TestFailedException] {
        processAFile("foo.txt")
      }
    }
    "require .riddl suffix" in {
      intercept[org.scalatest.exceptions.TestFailedException] {
        processAFile("testkit/src/test/input/hugo.conf")
      }
    }
    "handle a file or directory" in {
      processADirectory("testkit/src/test/input/hugo.conf")
      processADirectory("testkit/src/test/input/domains")
    }
  }
}
