/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo.utils

import com.ossuminc.riddl.utils.{Logger, SysLogger}

import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/** Unit Tests For TreeCopyFileVisitor */
case class TreeCopyFileVisitor(source: Path, target: Path, log: Logger = SysLogger())
    extends SimpleFileVisitor[Path] {

  @throws[IOException]
  override def preVisitDirectory(
    dir: Path,
    attrs: BasicFileAttributes
  ): FileVisitResult = {
    val resolve = target.resolve(source.relativize(dir))
    if Files.notExists(resolve) then { Files.createDirectories(resolve) }
    FileVisitResult.CONTINUE
  }

  @throws[IOException]
  override def visitFile(
    file: Path,
    attrs: BasicFileAttributes
  ): FileVisitResult = {
    val resolve = target.resolve(source.relativize(file))
    if !file.getFileName.startsWith(".") then {
      if Files.exists(resolve) then { Files.delete(resolve) }
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
