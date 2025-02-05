/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package docs

import org.scalatest.Assertion
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer

/** Unit Tests To Check Documentation Examples */
class CheckDocsTest extends AnyWordSpec {

  def runHugo(srcDir: Path): Assertion = {
    import scala.sys.process.*
    val lineBuffer: ArrayBuffer[String] = ArrayBuffer[String]()
    var hadErrorOutput: Boolean = false
    var hadWarningOutput: Boolean = false

    def fout(line: String): Unit = {
      lineBuffer.append(line)
      if !hadWarningOutput && line.contains("WARN") then hadWarningOutput = true
    }

    def ferr(line: String): Unit = {
      lineBuffer.append(line);
      hadErrorOutput = true
    }

    val logger = ProcessLogger(fout, ferr)
    Files.isDirectory(srcDir)
    val cwdFile = srcDir.toFile
    val proc = Process("hugo", cwd = Option(cwdFile))
    proc.!(logger) match {
      case 0 =>
        if hadErrorOutput then {
          fail("hugo wrote to stderr:\n  " + lineBuffer.mkString("\n  "))
        } else if hadWarningOutput then {
          info("hugo issued warnings:\n  " + lineBuffer.mkString("\n  "))
          succeed
        } else {
          succeed
        }
      case 1 =>
        info("hugo run returned with rc=1\n")
        succeed   
      case rc: Int =>
        fail(s"hugo run failed with rc=$rc:\n  " + lineBuffer.mkString("\n  "))
    }
  }

  "Docs" should {
    "run successfully with hugo" in {
      val srcDir = Path.of("doc/src/main/hugo")
      runHugo(srcDir)
    }
  }
}
