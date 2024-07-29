/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import java.nio.file.{Files, Path}
import java.util.zip.ZipFile
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

object Zip {

  def unzip(zipPath: Path, outputPath: Path): mutable.HashSet[Path] = {
    val set = mutable.HashSet.empty[Path]
    val zipFile = new ZipFile(zipPath.toFile)
    for entry <- zipFile.entries.asScala do {
      val path = outputPath.resolve(entry.getName)
      if entry.isDirectory then { Files.createDirectories(path) }
      else {
        Files.createDirectories(path.getParent)
        Files.copy(zipFile.getInputStream(entry), path)
        set += path
      }
    }
    set
  }
}
