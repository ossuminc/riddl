/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.utils
import java.io.PrintWriter
import java.nio.file.Path
import scala.collection.mutable

trait OutputFile {
  protected val sb: mutable.StringBuilder = new mutable.StringBuilder()

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
    if (!dirFile.exists) { dirFile.mkdirs() }
  }

  def write(): Unit = {
    mkDirs()
    val writer = new PrintWriter(filePath.toFile)
    write(writer)
  }

}
