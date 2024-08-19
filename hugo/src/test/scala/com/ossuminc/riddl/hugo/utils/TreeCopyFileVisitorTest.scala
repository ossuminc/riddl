package com.ossuminc.riddl.hugo.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path
import java.nio.file.Files

class TreeCopyFileVisitorTest extends AnyWordSpec with Matchers {

  "TreeCopyFileVisitor" must {
    val source = Path.of("hugo/src/test/input")
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
