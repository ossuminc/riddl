/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.RiddlFilesTestBase
import org.scalatest.{Assertion, TestData}

import java.io.File
import java.nio.file.Path

class RiddlFilesTestBaseTest extends RiddlFilesTestBase {

  def checkAFile(rootDir: Path, file: File): Assertion = {
    succeed
  }

  "RiddlFilesTestBase" should {
    "find riddl files" in { (td: TestData) =>
      val dir = "language/jvm/src/test/input"
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
        processAFile("language/src/test/input/not-a-file")
      }
    }
    "handle a file or directory" in { (td: TestData) =>
      processADirectory("language/jvm/src/test/input/hugo.conf")
      processADirectory("language/jvm/src/test/input/domains")
    }
  }
}
