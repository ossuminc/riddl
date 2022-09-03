package com.reactific.riddl.utils

import java.nio.file.{Files, Path}
import java.util.zip.ZipFile
import scala.collection.mutable
import scala.jdk.CollectionConverters.*


object Zip {

  def zip(inputFolder: Path, zipFile: Path): Unit = {
    ???
  }

  def unzip(zipPath: Path, outputPath: Path): mutable.HashSet[Path] = {
    val set = mutable.HashSet.empty[Path]
    val zipFile = new ZipFile(zipPath.toFile)
    for (entry <- zipFile.entries.asScala) {
      val path = outputPath.resolve(entry.getName)
      if (entry.isDirectory) {
        Files.createDirectories(path)
      } else {
        Files.createDirectories(path.getParent)
        Files.copy(zipFile.getInputStream(entry), path)
        set += path
      }
    }
    set
  }
}
