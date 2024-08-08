package com.ossuminc.riddl.testkit

import org.scalatest.Assertion

import java.io.File
import java.nio.file.Path
import org.scalatest.TestData

class RiddlFilesTestBaseTest extends RiddlFilesTestBase {

  def checkAFile(rootDir: Path, file: File): Assertion = {
    succeed
  }

  "RiddlFilesTestBase" should {
    "find riddl files" in { (td: TestData) =>
      val dir = "testkit/src/test/input"
      val files = findRiddlFiles(new File(dir), true)
      files.size must be > 0
    }
    "not find non-files" in { (td: TestData) =>
      intercept[org.scalatest.exceptions.TestFailedException] {
        processAFile("foo.txt")
      }
    }
    "require .riddl suffix" in { (td: TestData) =>
      intercept[org.scalatest.exceptions.TestFailedException] {
        processAFile("testkit/src/test/input/hugo.conf")
      }
    }
    "handle a file or directory" in { (td: TestData) =>
      processADirectory("testkit/src/test/input/hugo.conf")
      processADirectory("testkit/src/test/input/domains")
    }
  }
}
