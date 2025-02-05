/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.RiddlFilesTestBase
import org.scalatest.{Assertion, TestData}

import java.io.File
import java.nio.file.Path

class RiddlFilesTestBaseTest extends RiddlFilesTestBase {

  def checkAFile(rootDir: Path, file: Path): Assertion = { succeed }

  "RiddlFilesTestBase" should {
    "find riddl files" in { (td: TestData) =>
      val dir = "language/input"
      val files = findRiddlFiles(Path.of(dir), true)
      files.size must be > 0
    }
    "not find non-files" in { (td: TestData) =>
      intercept[org.scalatest.exceptions.TestFailedException] {
        processAFile("foo.txt")
      }
    }
    "require .riddl suffix" in { (td: TestData) =>
      intercept[org.scalatest.exceptions.TestFailedException] {
        processAFile("language/input/not-a-file")
      }
    }
    "handle a file or directory" in { (td: TestData) =>
      processADirectory("language/input/hugo.conf")
      processADirectory("language/input/domains")
    }
  }
}
