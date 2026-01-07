/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo.utils

import com.ossuminc.riddl.commands.hugo.utils.TreeCopyFileVisitor
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

class TreeCopyFileVisitorTest extends AnyWordSpec with Matchers {

  "TreeCopyFileVisitor" must {
    val source = Path.of("commands/input")
    val target: Path = Files.createTempDirectory("TCFV-test")
    val visitor = TreeCopyFileVisitor(source, target)

    "copies input files" in {
      Files.createDirectories(target.resolve("regressions")) // To make it delete something
      val result = Files.walkFileTree(source, visitor)
      result must be(source)
      Files.isDirectory(target) must be(true)
    }
  }
}
