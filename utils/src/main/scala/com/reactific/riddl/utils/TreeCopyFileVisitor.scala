package com.reactific.riddl.utils

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

/** Unit Tests For TreeCopyFileVisitor */
case class TreeCopyFileVisitor(log: Logger, source: Path, target: Path)
    extends SimpleFileVisitor[Path] {

  @throws[IOException]
  override def preVisitDirectory(
    dir: Path,
    attrs: BasicFileAttributes
  ): FileVisitResult = {
    val resolve = target.resolve(source.relativize(dir))
    if (Files.notExists(resolve)) { Files.createDirectories(resolve) }
    FileVisitResult.CONTINUE
  }

  @throws[IOException]
  override def visitFile(
    file: Path,
    attrs: BasicFileAttributes
  ): FileVisitResult = {
    val resolve = target.resolve(source.relativize(file))
    if (!file.getFileName.startsWith(".")) {
      if (Files.exists(resolve)) {
        Files.delete(resolve)
      }
      Files.copy(file, resolve, StandardCopyOption.REPLACE_EXISTING)
    }
    FileVisitResult.CONTINUE
  }

  override def visitFileFailed(
    file: Path,
    exc: IOException
  ): FileVisitResult = {
    log.error(s"Unable to copy: $file: $exc\n")
    FileVisitResult.CONTINUE
  }
}
