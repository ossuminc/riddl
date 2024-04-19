/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo

/** Unit Tests For RunHugoTestBase */
import org.scalatest.*
import org.scalatest.matchers.must.Matchers

import java.nio.file.Files
import java.nio.file.Path
import scala.collection.mutable.ArrayBuffer

/** Tests For RunHugoTestBase */
trait RunHugoTestBase extends Matchers {

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
      lineBuffer.append(line); hadErrorOutput = true
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
          fail("hugo issued warnings:\n  " + lineBuffer.mkString("\n  "))
        } else { succeed }
      case rc: Int =>
        fail(s"hugo run failed with rc=$rc:\n  " + lineBuffer.mkString("\n  "))
    }
  }
}
