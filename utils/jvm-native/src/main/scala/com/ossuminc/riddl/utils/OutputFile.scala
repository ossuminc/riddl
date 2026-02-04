/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils
import java.io.PrintWriter
import java.nio.file.Path

trait OutputFile(using PlatformContext) extends FileBuilder {

  def filePath: Path

  def write(writer: PrintWriter): Unit = {
    try {
      writer.write(sb.toString())
      writer.flush()
    } finally { writer.close() }
    sb.clear() // release memory because content written to file
  }

  private def mkDirs(): Unit = {
    val dirFile = filePath.getParent.toFile
    if !dirFile.exists then { dirFile.mkdirs() }
  }

  def write(): Unit = {
    mkDirs()
    val writer = new PrintWriter(filePath.toFile)
    write(writer)
  }

}
