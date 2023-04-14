/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.testkit

import org.scalatest.Assertion

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

abstract class RiddlFilesTestBase extends ValidatingTest {

  def checkAFile(rootDir: Path, file: File): Assertion

  def findRiddlFiles(dirFile: File, recurse: Boolean = false): Seq[File] = {
    val dirKids = dirFile.listFiles().toSeq
    val (files, dir) = dirKids.partition(_.isFile())
    if !recurse then { files.filter(_.toString.endsWith(".riddl")) }
    else {
      files.filter(_.toString.endsWith(".riddl")) ++
        dir.flatMap(f => findRiddlFiles(f))
    }
  }

  def processAFile(file: String): Unit = {
    val filePath = Path.of(file)
    if !Files.isRegularFile(filePath) then {
      fail(s"Not a regular file: $filePath")
    } else if !filePath.getFileName.toString.endsWith(".riddl") then {
      fail(s"Not a .riddl file: $filePath")
    } else { checkAFile(filePath.getParent, filePath.toFile) }
  }

  def processADirectory(directory: String): Unit = {
    val dirFile = new File(directory)
    if dirFile.isDirectory then {
      val dirPath: Path = dirFile.toPath
      val files = findRiddlFiles(dirFile, true)
      files.foreach(file => checkAFile(dirPath, file))
    } else { checkAFile(dirFile.getParentFile.toPath, dirFile) }
  }
}
