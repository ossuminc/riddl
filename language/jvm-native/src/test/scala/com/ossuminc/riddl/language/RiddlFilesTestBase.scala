/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.parsing.ParsingTest
import com.ossuminc.riddl.utils.{ec, pc}
import org.scalatest.Assertion

import scala.jdk.CollectionConverters.*

import java.io.File
import java.nio.file.{Files, LinkOption, Path}

abstract class RiddlFilesTestBase extends ParsingTest {

  def checkAFile(rootDir: Path, file: Path): Assertion

  def findRiddlFiles(dirFile: Path, recurse: Boolean = false): List[Path] = {
    val dirKids = Files.list(dirFile).toList.asScala.toList 
    val (files, dir) = dirKids.partition(Files.isRegularFile(_))
    if !recurse then 
      files.filter(_.toString.endsWith(".riddl")) 
    else
      files.filter(_.toString.endsWith(".riddl")) ++ dir.flatMap(f => findRiddlFiles(f))
    end if
  }

  def processAFile(file: String): Unit = {
    val filePath = Path.of(file)
    if !Files.isRegularFile(filePath) then {
      fail(s"Not a regular file: $filePath")
    } else if !filePath.getFileName.toString.endsWith(".riddl") then {
      fail(s"Not a .riddl file: $filePath")
    } else { checkAFile(filePath.getParent, filePath) }
  }

  def processADirectory(directory: String): Unit = {
    val dirPath = Path.of(directory)
    if Files.isDirectory(dirPath) then
      val files = findRiddlFiles(dirPath, true)
      files.foreach(file => checkAFile(dirPath, file))
    else
      checkAFile(dirPath.getParent, dirPath)
    end if  
  }
}
